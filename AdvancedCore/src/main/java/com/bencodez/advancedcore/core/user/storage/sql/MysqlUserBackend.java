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
import java.util.function.Consumer;

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
        ArrayList<UUID> users = new ArrayList<>();
        forEachUser(uuid -> {
            if (users.size() >= MAX_MATERIALIZED_USERS) {
                throw new IllegalStateException("User enumeration exceeds " + MAX_MATERIALIZED_USERS
                        + " entries; use forEachUser for streaming access");
            }
            users.add(uuid);
        });
        return users;
    }

    @Override
    public void forEachUser(Consumer<UUID> consumer) {
        requireOpen();
        Objects.requireNonNull(consumer, "consumer");
        String sql = "SELECT " + table.quote(SqlUserSchema.UUID_COLUMN) + " FROM " + table.quote(table.getTableName());
        try (Connection connection = table.getMysql().getConnectionManager().getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setFetchSize(512);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    String value = result.getString(1);
                    if (value == null || value.isBlank()) continue;
                    try {
                        consumer.accept(UUID.fromString(value));
                    } catch (IllegalArgumentException invalid) {
                        logger.warn("Skipping invalid UUID in " + table.getTableName() + ": " + value, invalid);
                    }
                }
            }
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
            try {
                init();
            } catch (RuntimeException | Error failure) {
                // AbstractSqlTable owns the connection manager before init() begins.
                // Clean it here because the outer backend cannot receive this table
                // instance when construction itself fails.
                try {
                    if (getMysql() != null) getMysql().disconnect();
                } catch (RuntimeException | Error cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
                throw failure;
            }
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
                if (!first) sql.append(", ");
                first = false;
                String type = SqlUserSchema.UUID_COLUMN.equalsIgnoreCase(column.name())
                        ? bestUuidType() : normaliseTypeForDb(column.sqlType());
                sql.append(quote(column.name())).append(' ').append(type);
            }
            sql.append(", PRIMARY KEY (").append(quote(SqlUserSchema.UUID_COLUMN)).append("));");
            return sql.toString();
        }

        @Override public void logSevere(String message) { logger.warn(message, null); }
        @Override public void logInfo(String message) { logger.info(message); }
        @Override public void debug(Throwable error) { logger.warn(error == null ? "SQL debug" : error.getMessage(), error); }
        @Override public void debug(String message) { logger.info(message); }

        void ensureUuidType() {
            if (getDbType() != DbType.POSTGRESQL) return;
            try {
                String uuidType = bestUuidType();
                if (!columnNeedsAlter(SqlUserSchema.UUID_COLUMN, uuidType)) return;
                String uuidColumn = quote(SqlUserSchema.UUID_COLUMN);
                String sql = "ALTER TABLE " + quote(tableName) + " ALTER COLUMN " + uuidColumn
                        + " TYPE " + uuidType + " USING NULLIF(" + uuidColumn + ", '')::uuid;";
                try (Connection connection = getMysql().getConnectionManager().getConnection();
                        PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.executeUpdate();
                } catch (SQLException ddlFailure) {
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
                    String storedName = findRegisteredColumn(column.name());
                    if (storedName != null) {
                        if (getDbType() == DbType.POSTGRESQL && !storedName.equals(column.name())) {
                            renamePostgresColumn(storedName, column.name());
                        }
                        rememberColumn(column);
                        return;
                    }
                    String sql = "ALTER TABLE " + quote(tableName) + " ADD COLUMN " + quote(column.name())
                            + " " + normaliseTypeForDb(column.sqlType()) + ";";
                    try (Connection connection = getMysql().getConnectionManager().getConnection();
                            PreparedStatement statement = connection.prepareStatement(sql)) {
                        statement.executeUpdate();
                    } catch (SQLException ddlFailure) {
                        if (!isDuplicateColumn(ddlFailure)) throw ddlFailure;
                        try {
                            String raced = findRegisteredColumn(column.name());
                            if (raced == null) throw ddlFailure;
                            if (getDbType() == DbType.POSTGRESQL && !raced.equals(column.name())) {
                                renamePostgresColumn(raced, column.name());
                            }
                        } catch (SQLException inspectionFailure) {
                            if (inspectionFailure != ddlFailure) ddlFailure.addSuppressed(inspectionFailure);
                            throw ddlFailure;
                        }
                    }
                    rememberColumn(column);
                } catch (SQLException failure) {
                    throw new IllegalStateException("Failed to initialize registered SQL column: " + column.name(), failure);
                }
            }
        }

        private void renamePostgresColumn(String storedName, String requestedName) throws SQLException {
            String sql = "ALTER TABLE " + quote(tableName) + " RENAME COLUMN " + quote(storedName)
                    + " TO " + quote(requestedName) + ";";
            try (Connection connection = getMysql().getConnectionManager().getConnection();
                    PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.executeUpdate();
            } catch (SQLException renameFailure) {
                // A peer may have completed the same rename after our inspection.
                String current = findRegisteredColumn(requestedName);
                if (!requestedName.equals(current)) throw renameFailure;
            }
        }

        private void rememberColumn(SqlUserSchema.ColumnDefinition column) {
            columns.removeIf(existing -> existing.equalsIgnoreCase(column.name()));
            columns.add(column.name());
            intColumns.removeIf(existing -> existing.equalsIgnoreCase(column.name()));
            if (column.dataType() == DataType.INTEGER) intColumns.add(column.name());
        }

        private String findRegisteredColumn(String name) throws SQLException {
            try (Connection connection = getMysql().getConnectionManager().getConnection();
                    PreparedStatement statement = connection.prepareStatement(
                            "SELECT * FROM " + quote(tableName) + " WHERE 1=0");
                    ResultSet result = statement.executeQuery()) {
                ResultSetMetaData metadata = result.getMetaData();
                String foldedMatch = null;
                for (int i = 1; i <= metadata.getColumnCount(); i++) {
                    String storedName = metadata.getColumnName(i);
                    if (name.equals(storedName)) return storedName;
                    if (foldedMatch == null && name.equalsIgnoreCase(storedName)) foldedMatch = storedName;
                }
                return foldedMatch;
            }
        }

        private boolean isDuplicateColumn(SQLException failure) {
            return getDbType() == DbType.POSTGRESQL ? "42701".equals(failure.getSQLState())
                    : failure.getErrorCode() == 1060 && "42S21".equals(failure.getSQLState());
        }

        String quote(String identifier) { return qi(identifier); }
    }
}
