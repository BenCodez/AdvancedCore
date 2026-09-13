package com.bencodez.advancedcore.core.user.storage.sql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.advancedcore.core.user.storage.SqlUserStorage;
import com.bencodez.simpleapi.sql.Column;
import com.bencodez.simpleapi.sql.DataType;
import com.bencodez.simpleapi.sql.data.DataValue;
import com.bencodez.simpleapi.sql.mysql.AbstractSqlTable;
import com.bencodez.simpleapi.sql.mysql.DbType;
import com.bencodez.simpleapi.sql.mysql.config.MysqlConfig;

public final class MysqlUserBackend implements SqlUserBackend {
    private static final int USER_PAGE_SIZE = 512;
    private final SqlUserSchema schema;
    private final SqlBackendLogger logger;
    private final HeadlessUserTable table;
    private final AtomicBoolean open = new AtomicBoolean(true);
    private final ReentrantReadWriteLock operations = new ReentrantReadWriteLock(true);
    private volatile boolean tableClosed;

    public MysqlUserBackend(String baseTableName, MysqlConfig config, SqlUserSchema schema, SqlBackendLogger logger) {
        this.schema = Objects.requireNonNull(schema, "schema");
        this.logger = logger == null ? SqlBackendLogger.NO_OP : logger;
        this.table = new HeadlessUserTable(baseTableName, Objects.requireNonNull(config, "config"), schema, this.logger);
        try { ensureRegisteredColumns(); }
        catch (RuntimeException | Error failure) {
            open.set(false);
            try { table.close(); tableClosed = true; }
            catch (RuntimeException | Error cleanupFailure) { failure.addSuppressed(cleanupFailure); }
            throw failure;
        }
    }

    @Override public UserStorage storageType() { return UserStorage.MYSQL; }

    @Override
    public SqlUserStorage user(UUID uuid) {
        requireOpen();
        JdbcSqlUserStorage.Dialect dialect = JdbcSqlUserStorage.Dialect.fromDbType(table.getMysql().getConnectionManager().getDbType());
        SqlUserStorage delegate = new JdbcSqlUserStorage(UserStorage.MYSQL, uuid, table.getTableName(), schema,
                () -> table.getMysql().getConnectionManager().getConnection(), dialect, logger);
        return new SqlUserStorage() {
            @Override public List<Column> readRow(UserStorage storage) { return withOperation(() -> delegate.readRow(storage)); }
            @Override public boolean contains(UserStorage storage) { return withOperation(() -> delegate.contains(storage)); }
            @Override public void delete(UserStorage storage) { withOperation(() -> { delegate.delete(storage); return null; }); }
            @Override public void write(UserStorage storage, String key, DataValue value) { withOperation(() -> { delegate.write(storage, key, value); return null; }); }
            @Override public void writeValues(UserStorage storage, HashMap<String, DataValue> values) { withOperation(() -> { delegate.writeValues(storage, values); return null; }); }
        };
    }

    @Override
    public List<UUID> enumerateUsers() {
        ArrayList<UUID> users = new ArrayList<>();
        forEachUser(uuid -> {
            if (users.size() >= MAX_MATERIALIZED_USERS) throw new IllegalStateException("User enumeration exceeds "
                    + MAX_MATERIALIZED_USERS + " entries; use forEachUser for streaming access");
            users.add(uuid);
        });
        return users;
    }

    @Override
    public void forEachUser(Consumer<UUID> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        withOperation(() -> {
            String cursor = null;
            while (true) {
                List<UserPageEntry> page = readUserPage(cursor);
                if (page.isEmpty()) return null;
                cursor = page.get(page.size() - 1).cursor();
                for (UserPageEntry entry : page) {
                    if (entry.uuid() != null) consumer.accept(entry.uuid());
                }
                if (page.size() < USER_PAGE_SIZE) return null;
            }
        });
    }

    private List<UserPageEntry> readUserPage(String cursor) {
        String uuidColumn = table.quote(SqlUserSchema.UUID_COLUMN);
        String sql = "SELECT " + uuidColumn + " FROM " + table.quote(table.getTableName())
                + (cursor == null ? "" : " WHERE " + uuidColumn + " > ?")
                + " ORDER BY " + uuidColumn + " ASC LIMIT ?";
        JdbcSqlUserStorage.Dialect dialect = JdbcSqlUserStorage.Dialect.fromDbType(table.getDbType());
        try (Connection connection = table.getMysql().getConnectionManager().getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            if (cursor != null) {
                if (dialect == JdbcSqlUserStorage.Dialect.POSTGRESQL) dialect.bindUuid(statement, index++, UUID.fromString(cursor));
                else statement.setString(index++, cursor);
            }
            statement.setInt(index, USER_PAGE_SIZE);
            ArrayList<UserPageEntry> page = new ArrayList<>(USER_PAGE_SIZE);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    String value = result.getString(1);
                    if (value == null) continue;
                    UUID parsed = null;
                    try { parsed = UUID.fromString(value); }
                    catch (IllegalArgumentException invalid) { logger.warn("Skipping invalid UUID in " + table.getTableName() + ": " + value, invalid); }
                    page.add(new UserPageEntry(value, parsed));
                }
            }
            return page;
        } catch (IllegalArgumentException invalidCursor) {
            throw new IllegalStateException("Failed to advance SQL user enumeration cursor", invalidCursor);
        } catch (SQLException failure) {
            throw new IllegalStateException("Failed to enumerate MySQL users", failure);
        }
    }

    private record UserPageEntry(String cursor, UUID uuid) {}

    @Override public boolean isOpen() { return open.get(); }

    @Override
    public void close() {
        if (operations.getReadHoldCount() != 0) throw new IllegalStateException("Cannot close MySQL from inside an active storage operation");
        open.set(false);
        operations.writeLock().lock();
        try {
            if (!tableClosed) {
                table.close();
                tableClosed = true;
            }
        } finally {
            operations.writeLock().unlock();
        }
    }

    private <T> T withOperation(Supplier<T> operation) {
        requireOpen();
        operations.readLock().lock();
        try { requireOpen(); return operation.get(); }
        finally { operations.readLock().unlock(); }
    }

    private void ensureRegisteredColumns() {
        table.ensureUuidType();
        for (SqlUserSchema.ColumnDefinition column : schema.columns()) {
            if (!SqlUserSchema.UUID_COLUMN.equalsIgnoreCase(column.name())) table.ensureColumn(column);
        }
    }

    private void requireOpen() {
        if (!open.get() && operations.getReadHoldCount() == 0) throw new IllegalStateException("MySQL user backend is closed");
    }

    private static final class HeadlessUserTable extends AbstractSqlTable {
        private final SqlUserSchema schema;
        private final SqlBackendLogger logger;

        HeadlessUserTable(String baseTableName, MysqlConfig config, SqlUserSchema schema, SqlBackendLogger logger) {
            super(baseTableName, config, config.isDebug(), true);
            this.schema = schema;
            this.logger = logger;
            try { init(); }
            catch (RuntimeException | Error failure) {
                try { if (getMysql() != null) getMysql().disconnect(); }
                catch (RuntimeException | Error cleanupFailure) { failure.addSuppressed(cleanupFailure); }
                throw failure;
            }
        }

        @Override public String getPrimaryKeyColumn() { return SqlUserSchema.UUID_COLUMN; }

        @Override
        public String buildCreateTableSql(DbType dbType) {
            StringBuilder sql = new StringBuilder("CREATE TABLE IF NOT EXISTS ").append(quote(tableName)).append(" (");
            boolean first = true;
            for (SqlUserSchema.ColumnDefinition column : schema.columns()) {
                if (!first) sql.append(", ");
                first = false;
                String type = SqlUserSchema.UUID_COLUMN.equalsIgnoreCase(column.name()) ? bestUuidType() : normaliseTypeForDb(column.sqlType());
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
                        PreparedStatement statement = connection.prepareStatement(sql)) { statement.executeUpdate(); }
                catch (SQLException ddlFailure) {
                    try { if (columnNeedsAlter(SqlUserSchema.UUID_COLUMN, uuidType)) throw ddlFailure; }
                    catch (SQLException inspectionFailure) {
                        if (inspectionFailure != ddlFailure) ddlFailure.addSuppressed(inspectionFailure);
                        throw ddlFailure;
                    }
                }
            } catch (SQLException failure) { throw new IllegalStateException("Failed to initialize PostgreSQL UUID column", failure); }
        }

        void ensureColumn(SqlUserSchema.ColumnDefinition column) {
            synchronized (checkColumnLock) {
                try {
                    String storedName = findRegisteredColumn(column.name());
                    if (storedName != null) {
                        if (getDbType() == DbType.POSTGRESQL && !storedName.equals(column.name())) renamePostgresColumn(storedName, column.name());
                        rememberColumn(column);
                        return;
                    }
                    String sql = "ALTER TABLE " + quote(tableName) + " ADD COLUMN " + quote(column.name())
                            + " " + normaliseTypeForDb(column.sqlType()) + ";";
                    try (Connection connection = getMysql().getConnectionManager().getConnection();
                            PreparedStatement statement = connection.prepareStatement(sql)) { statement.executeUpdate(); }
                    catch (SQLException ddlFailure) {
                        if (!isDuplicateColumn(ddlFailure)) throw ddlFailure;
                        try {
                            String raced = findRegisteredColumn(column.name());
                            if (raced == null) throw ddlFailure;
                            if (getDbType() == DbType.POSTGRESQL && !raced.equals(column.name())) renamePostgresColumn(raced, column.name());
                        } catch (SQLException inspectionFailure) {
                            if (inspectionFailure != ddlFailure) ddlFailure.addSuppressed(inspectionFailure);
                            throw ddlFailure;
                        }
                    }
                    rememberColumn(column);
                } catch (SQLException failure) { throw new IllegalStateException("Failed to initialize registered SQL column: " + column.name(), failure); }
            }
        }

        private void renamePostgresColumn(String storedName, String requestedName) throws SQLException {
            String sql = "ALTER TABLE " + quote(tableName) + " RENAME COLUMN " + quote(storedName) + " TO " + quote(requestedName) + ";";
            try (Connection connection = getMysql().getConnectionManager().getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.executeUpdate();
            } catch (SQLException renameFailure) {
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
                    PreparedStatement statement = connection.prepareStatement("SELECT * FROM " + quote(tableName) + " WHERE 1=0");
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
