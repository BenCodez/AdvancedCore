package com.bencodez.advancedcore.core.user.storage.sql;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.advancedcore.core.user.storage.SqlUserStorage;
import com.bencodez.simpleapi.sql.Column;
import com.bencodez.simpleapi.sql.data.DataValue;

public final class SqliteUserBackend implements SqlUserBackend {
    private static final int USER_PAGE_SIZE = 512;
    private final Path databaseFile;
    private final String tableName;
    private final SqlUserSchema schema;
    private final SqlBackendLogger logger;
    private final AtomicBoolean open = new AtomicBoolean();
    private final ReentrantReadWriteLock operations = new ReentrantReadWriteLock(true);

    public SqliteUserBackend(Path dataDirectory, String databaseName, String tableName, SqlUserSchema schema, SqlBackendLogger logger) {
        Objects.requireNonNull(dataDirectory, "dataDirectory");
        Objects.requireNonNull(databaseName, "databaseName");
        quote(tableName);
        this.tableName = tableName;
        this.schema = Objects.requireNonNull(schema, "schema");
        this.logger = logger == null ? SqlBackendLogger.NO_OP : logger;
        if (databaseName.isBlank() || databaseName.contains("/") || databaseName.contains("\\")) throw new IllegalArgumentException("databaseName must be a simple file name");
        this.databaseFile = dataDirectory.resolve(databaseName + ".db");
        initialize();
    }

    public Path databaseFile() { return databaseFile; }
    @Override public UserStorage storageType() { return UserStorage.SQLITE; }

    @Override
    public SqlUserStorage user(UUID uuid) {
        requireOpen();
        SqlUserStorage delegate = new JdbcSqlUserStorage(UserStorage.SQLITE, uuid, tableName, schema,
                this::openConnection, JdbcSqlUserStorage.Dialect.SQLITE, logger);
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
        String uuidColumn = quote(SqlUserSchema.UUID_COLUMN);
        String sql = "SELECT " + uuidColumn + " FROM " + quote(tableName)
                + (cursor == null ? "" : " WHERE " + uuidColumn + " > ?")
                + " ORDER BY " + uuidColumn + " ASC LIMIT ?";
        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            if (cursor != null) statement.setString(index++, cursor);
            statement.setInt(index, USER_PAGE_SIZE);
            ArrayList<UserPageEntry> page = new ArrayList<>(USER_PAGE_SIZE);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    String value = result.getString(1);
                    if (value == null) continue;
                    UUID parsed = null;
                    try { parsed = UUID.fromString(value); }
                    catch (IllegalArgumentException invalid) {
                        logger.warn("Skipping invalid UUID in " + tableName + ": " + value, invalid);
                    }
                    page.add(new UserPageEntry(value, parsed));
                }
            }
            return page;
        } catch (SQLException failure) {
            throw new IllegalStateException("Failed to enumerate SQLite users", failure);
        }
    }

    private record UserPageEntry(String cursor, UUID uuid) {}

    @Override public boolean isOpen() { return open.get(); }

    @Override
    public void close() {
        if (operations.getReadHoldCount() != 0) throw new IllegalStateException("Cannot close SQLite from inside an active storage operation");
        open.set(false);
        operations.writeLock().lock();
        try { } finally { operations.writeLock().unlock(); }
    }

    private <T> T withOperation(Supplier<T> operation) {
        requireOpen();
        operations.readLock().lock();
        try { requireOpen(); return operation.get(); }
        finally { operations.readLock().unlock(); }
    }

    private void initialize() {
        try {
            Files.createDirectories(databaseFile.getParent());
            Class.forName("org.sqlite.JDBC");
            open.set(true);
            try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement(createTableSql())) { statement.executeUpdate(); }
            ensureRegisteredColumns();
        } catch (IOException | ClassNotFoundException | SQLException | RuntimeException e) {
            open.set(false);
            throw new IllegalStateException("Failed to initialize SQLite user backend at " + databaseFile, e);
        }
    }

    private void ensureRegisteredColumns() throws SQLException {
        for (SqlUserSchema.ColumnDefinition column : schema.columns()) {
            if (SqlUserSchema.UUID_COLUMN.equalsIgnoreCase(column.name()) || hasColumn(column.name())) continue;
            String sql = "ALTER TABLE " + quote(tableName) + " ADD COLUMN " + quote(column.name()) + " " + column.sqlType();
            try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.executeUpdate();
            } catch (SQLException addFailure) {
                if (!isDuplicateColumn(addFailure) || !hasColumn(column.name())) throw addFailure;
            }
        }
    }

    private boolean isDuplicateColumn(SQLException failure) {
        String message = failure.getMessage();
        return message != null && message.toLowerCase(Locale.ROOT).contains("duplicate column name");
    }

    private boolean hasColumn(String name) throws SQLException {
        String sql = "PRAGMA table_info(" + quote(tableName) + ")";
        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement(sql); ResultSet result = statement.executeQuery()) {
            while (result.next()) if (name.equalsIgnoreCase(result.getString("name"))) return true;
            return false;
        }
    }

    private String createTableSql() {
        StringBuilder sql = new StringBuilder("CREATE TABLE IF NOT EXISTS ").append(quote(tableName)).append(" (");
        boolean first = true;
        for (SqlUserSchema.ColumnDefinition column : schema.columns()) {
            if (!first) sql.append(", ");
            first = false;
            sql.append(quote(column.name())).append(' ').append(column.sqlType());
        }
        sql.append(", PRIMARY KEY (").append(quote(SqlUserSchema.UUID_COLUMN)).append("))");
        return sql.toString();
    }

    private Connection openConnection() throws SQLException { requireOpen(); return DriverManager.getConnection("jdbc:sqlite:" + databaseFile.toAbsolutePath()); }

    private void requireOpen() {
        // A close marks the backend unavailable immediately to new callers, but an
        // operation that already owns the read lock may finish nested JDBC work.
        if (!open.get() && operations.getReadHoldCount() == 0) {
            throw new IllegalStateException("SQLite user backend is closed");
        }
    }

    private static String quote(String identifier) { return JdbcSqlUserStorage.Dialect.SQLITE.quote(identifier); }
}
