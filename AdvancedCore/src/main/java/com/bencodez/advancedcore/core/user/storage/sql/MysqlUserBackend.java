package com.bencodez.advancedcore.core.user.storage.sql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.advancedcore.core.user.storage.SqlUserStorage;
import com.bencodez.simpleapi.sql.DataType;
import com.bencodez.simpleapi.sql.mysql.AbstractSqlTable;
import com.bencodez.simpleapi.sql.mysql.DbType;
import com.bencodez.simpleapi.sql.mysql.config.MysqlConfig;

public final class MysqlUserBackend implements SqlUserBackend {
    private final SqlUserSchema schema;
    private final SqlBackendLogger logger;
    private final HeadlessUserTable table;
    private final AtomicBoolean open = new AtomicBoolean(true);

    public MysqlUserBackend(String baseTableName, MysqlConfig config, SqlUserSchema schema, SqlBackendLogger logger) {
        this.schema = Objects.requireNonNull(schema, "schema");
        this.logger = logger == null ? SqlBackendLogger.NO_OP : logger;
        this.table = new HeadlessUserTable(baseTableName, Objects.requireNonNull(config, "config"), schema, this.logger);
        try {
            ensureRegisteredColumns();
        } catch (RuntimeException | Error failure) {
            // A failed UUID conversion must neither expose an incompatible backend
            // nor leave the newly owned connection manager running.
            open.set(false);
            try {
                table.close();
            } catch (RuntimeException | Error cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    @Override
    public UserStorage storageType() {
        return UserStorage.MYSQL;
    }

    @Override
    public SqlUserStorage user(UUID uuid) {
        requireOpen();
        JdbcSqlUserStorage.Dialect dialect = JdbcSqlUserStorage.Dialect.fromDbType(
                table.getMysql().getConnectionManager().getDbType());
        return new JdbcSqlUserStorage(UserStorage.MYSQL, uuid, table.getTableName(), schema,
                () -> table.getMysql().getConnectionManager().getConnection(), dialect, logger);
    }

    @Override
    public List<UUID> enumerateUsers() {
        requireOpen();
        String sql = "SELECT " + table.quote("uuid") + " FROM " + table.quote(table.getTableName());
        ArrayList<UUID> users = new ArrayList<>();
        try (Connection connection = table.getMysql().getConnectionManager().getConnection();
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                String value = result.getString(1);
                if (value == null || value.isBlank()) {
                    continue;
                }
                try {
                    users.add(UUID.fromString(value));
                } catch (IllegalArgumentException invalid) {
                    logger.warn("Skipping invalid UUID in " + table.getTableName() + ": " + value, invalid);
                }
            }
            return users;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to enumerate MySQL users", e);
        }
    }

    @Override
    public boolean isOpen() {
        return open.get();
    }

    @Override
    public void close() {
        if (open.compareAndSet(true, false)) {
            table.close();
        }
    }

    private void ensureRegisteredColumns() {
        table.ensureUuidType();
        for (SqlUserSchema.ColumnDefinition column : schema.columns()) {
            if (!SqlUserSchema.UUID_COLUMN.equalsIgnoreCase(column.name())) {
                table.ensureColumn(column);
            }
        }
    }

    private void requireOpen() {
        if (!open.get()) {
            throw new IllegalStateException("MySQL user backend is closed");
        }
    }

    private static final class HeadlessUserTable extends AbstractSqlTable {
        private final SqlUserSchema schema;
        private final SqlBackendLogger logger;

        HeadlessUserTable(String baseTableName, MysqlConfig config, SqlUserSchema schema, SqlBackendLogger logger) {
            super(baseTableName, config, config.isDebug(), true);
            this.schema = schema;
            this.logger = logger;
            init();
        }

        @Override
        public String getPrimaryKeyColumn() {
            return SqlUserSchema.UUID_COLUMN;
        }

        @Override
        public String buildCreateTableSql(DbType dbType) {
            StringBuilder sql = new StringBuilder("CREATE TABLE IF NOT EXISTS ").append(quote(tableName)).append(" (");
            boolean first = true;
            for (SqlUserSchema.ColumnDefinition column : schema.columns()) {
                if (!first) {
                    sql.append(", ");
                }
                first = false;
                String type = SqlUserSchema.UUID_COLUMN.equalsIgnoreCase(column.name())
                        ? bestUuidType() : normaliseTypeForDb(column.sqlType());
                sql.append(quote(column.name())).append(' ').append(type);
            }
            sql.append(", PRIMARY KEY (").append(quote(SqlUserSchema.UUID_COLUMN)).append("));");
            return sql.toString();
        }

        @Override
        public void logSevere(String message) {
            logger.warn(message, null);
        }

        @Override
        public void logInfo(String message) {
            logger.info(message);
        }

        @Override
        public void debug(Throwable error) {
            logger.warn(error == null ? "SQL debug" : error.getMessage(), error);
        }

        @Override
        public void debug(String message) {
            logger.info(message);
        }

        void ensureUuidType() {
            if (getDbType() != DbType.POSTGRESQL) {
                return;
            }
            try {
                String uuidType = bestUuidType();
                if (!columnNeedsAlter(SqlUserSchema.UUID_COLUMN, uuidType)) {
                    return;
                }
                // Match AbstractSqlTable's established VARCHAR -> UUID conversion,
                // but await it here: alterColumnType() only submits background DDL.
                // Use the same connection manager; do not create another JDBC pool.
                String uuidColumn = quote(SqlUserSchema.UUID_COLUMN);
                String sql = "ALTER TABLE " + quote(tableName) + " ALTER COLUMN " + uuidColumn
                        + " TYPE " + uuidType + " USING NULLIF(" + uuidColumn + ", '')::uuid;";
                try (Connection connection = getMysql().getConnectionManager().getConnection();
                        PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.executeUpdate();
                } catch (SQLException ddlFailure) {
                    // A competing node may have converted VARCHAR to UUID after
                    // our inspection. Only accept the failure if a fresh inspection
                    // confirms the required type; retain every genuine failure.
                    try {
                        if (columnNeedsAlter(SqlUserSchema.UUID_COLUMN, uuidType)) throw ddlFailure;
                    } catch (SQLException inspectionFailure) {
                        if (inspectionFailure != ddlFailure) ddlFailure.addSuppressed(inspectionFailure);
                        throw ddlFailure;
                    }
                }
            } catch (SQLException failure) {
                throw new IllegalStateException("Failed to initialize PostgreSQL UUID column", failure);
            }
        }

        void ensureColumn(SqlUserSchema.ColumnDefinition column) {
            synchronized (checkColumnLock) {
                try {
                    if (hasRegisteredColumn(column.name())) return;
                    String sql = "ALTER TABLE " + quote(tableName) + " ADD COLUMN " + quote(column.name())
                            + " " + normaliseTypeForDb(column.sqlType()) + ";";
                    try (Connection connection = getMysql().getConnectionManager().getConnection();
                            PreparedStatement statement = connection.prepareStatement(sql)) {
                        statement.executeUpdate();
                    } catch (SQLException ddlFailure) {
                        if (!isDuplicateColumn(ddlFailure)) throw ddlFailure;
                        // Another server may have won the ADD race. Recheck once on
                        // a new borrowed connection, after the failed DDL is cleaned up.
                        try {
                            if (!hasRegisteredColumn(column.name())) throw ddlFailure;
                        } catch (SQLException inspectionFailure) {
                            if (inspectionFailure != ddlFailure) ddlFailure.addSuppressed(inspectionFailure);
                            throw ddlFailure;
                        }
                    }
                    columns.add(column.name());
                    if (column.dataType() == DataType.INTEGER && !intColumns.contains(column.name())) {
                        intColumns.add(column.name());
                    }
                } catch (SQLException failure) {
                    throw new IllegalStateException("Failed to initialize registered SQL column: " + column.name(), failure);
                }
            }
        }

        private boolean hasRegisteredColumn(String name) throws SQLException {
            // Never turn an unavailable schema inspection into a missing-column result.
            try (Connection connection = getMysql().getConnectionManager().getConnection();
                    PreparedStatement statement = connection.prepareStatement(
                            "SELECT * FROM " + quote(tableName) + " WHERE 1=0");
                    ResultSet result = statement.executeQuery()) {
                ResultSetMetaData metadata = result.getMetaData();
                for (int i = 1; i <= metadata.getColumnCount(); i++) {
                    String storedName = metadata.getColumnName(i);
                    // PostgreSQL's quoted names are case-sensitive. Other supported
                    // backends keep their established case-insensitive lookup.
                    if (getDbType() == DbType.POSTGRESQL ? name.equals(storedName)
                            : name.equalsIgnoreCase(storedName)) return true;
                }
                return false;
            }
        }

        private boolean isDuplicateColumn(SQLException failure) {
            return getDbType() == DbType.POSTGRESQL ? "42701".equals(failure.getSQLState())
                    : failure.getErrorCode() == 1060 && "42S21".equals(failure.getSQLState());
        }

        String quote(String identifier) {
            return qi(identifier);
        }
    }
}
