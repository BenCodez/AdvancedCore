package com.bencodez.advancedcore.core.user.storage.sql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLDataException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.advancedcore.core.user.storage.SqlUserStorage;
import com.bencodez.simpleapi.sql.Column;
import com.bencodez.simpleapi.sql.DataType;
import com.bencodez.simpleapi.sql.data.DataValue;
import com.bencodez.simpleapi.sql.data.DataValueBoolean;
import com.bencodez.simpleapi.sql.data.DataValueInt;
import com.bencodez.simpleapi.sql.data.DataValueString;
import com.bencodez.simpleapi.sql.mysql.DbType;

final class JdbcSqlUserStorage implements SqlUserStorage {
    enum Dialect {
        SQLITE,
        MYSQL,
        POSTGRESQL;

        static Dialect fromDbType(DbType type) {
            return switch (Objects.requireNonNull(type, "type")) {
                case MYSQL, MARIADB -> MYSQL;
                case POSTGRESQL -> POSTGRESQL;
            };
        }

        String quote(String identifier) {
            if (identifier == null || identifier.isBlank() || identifier.indexOf('\0') >= 0) {
                throw new IllegalArgumentException("SQL identifier cannot be blank or contain NUL");
            }
            String delimiter = this == POSTGRESQL ? "\"" : "`";
            return delimiter + identifier.replace(delimiter, delimiter + delimiter) + delimiter;
        }

        void bindUuid(PreparedStatement statement, int index, UUID uuid) throws SQLException {
            if (this == POSTGRESQL) {
                // Match the native UUID column used by the existing PostgreSQL provider.
                statement.setObject(index, uuid);
            } else {
                statement.setString(index, uuid.toString());
            }
        }
    }

    @FunctionalInterface
    interface ConnectionProvider {
        Connection open() throws SQLException;
    }

    private final UserStorage storage;
    private final UUID uuid;
    private final String tableName;
    private final SqlUserSchema schema;
    private final ConnectionProvider connections;
    private final Dialect dialect;
    private final SqlBackendLogger logger;

    JdbcSqlUserStorage(UserStorage storage, UUID uuid, String tableName, SqlUserSchema schema,
            ConnectionProvider connections, Dialect dialect, SqlBackendLogger logger) {
        this.storage = Objects.requireNonNull(storage, "storage");
        this.uuid = Objects.requireNonNull(uuid, "uuid");
        this.tableName = Objects.requireNonNull(tableName, "tableName");
        this.schema = Objects.requireNonNull(schema, "schema");
        this.connections = Objects.requireNonNull(connections, "connections");
        this.dialect = Objects.requireNonNull(dialect, "dialect");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public List<Column> readRow(UserStorage requestedStorage) {
        requireStorage(requestedStorage);
        String sql = "SELECT * FROM " + quote(tableName) + " WHERE " + quote(SqlUserSchema.UUID_COLUMN) + "=?";
        try (Connection connection = connections.open();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            dialect.bindUuid(statement, 1, uuid);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return new ArrayList<>();
                }
                ResultSetMetaData metadata = result.getMetaData();
                ArrayList<Column> columns = new ArrayList<>(metadata.getColumnCount());
                for (int i = 1; i <= metadata.getColumnCount(); i++) {
                    String name = metadata.getColumnLabel(i);
                    SqlUserSchema.ColumnDefinition definition = schema.column(name);
                    DataType type = definition == null ? DataType.STRING : definition.dataType();
                    Column column = new Column(name, type);
                    column.setValue(readValue(result, i, type));
                    columns.add(column);
                }
                return columns;
            }
        } catch (SQLException e) {
            throw failure("read user row", e);
        }
    }

    @Override
    public boolean contains(UserStorage requestedStorage) {
        requireStorage(requestedStorage);
        String sql = "SELECT 1 FROM " + quote(tableName) + " WHERE " + quote(SqlUserSchema.UUID_COLUMN)
                + "=? LIMIT 1";
        try (Connection connection = connections.open();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            dialect.bindUuid(statement, 1, uuid);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        } catch (SQLException e) {
            throw failure("check user row", e);
        }
    }

    @Override
    public void delete(UserStorage requestedStorage) {
        requireStorage(requestedStorage);
        String sql = "DELETE FROM " + quote(tableName) + " WHERE " + quote(SqlUserSchema.UUID_COLUMN) + "=?";
        try (Connection connection = connections.open();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            dialect.bindUuid(statement, 1, uuid);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw failure("delete user row", e);
        }
    }

    @Override
    public void write(UserStorage requestedStorage, String key, DataValue value) {
        requireStorage(requestedStorage);
        if (SqlUserSchema.UUID_COLUMN.equalsIgnoreCase(key)) {
            throw new IllegalArgumentException("uuid is immutable through SqlUserStorage");
        }
        HashMap<String, DataValue> values = new HashMap<>();
        values.put(key, value);
        writeValues(requestedStorage, values);
    }

    @Override
    public void writeValues(UserStorage requestedStorage, HashMap<String, DataValue> values) {
        requireStorage(requestedStorage);
        Objects.requireNonNull(values, "values");
        // SELECT * copies include identity metadata. Do not mutate the caller's map
        // or let its UUID replace this storage object's bound identity.
        Map<String, DataValue> updates = new LinkedHashMap<>(values);
        updates.keySet().removeIf(SqlUserSchema.UUID_COLUMN::equalsIgnoreCase);
        if (updates.isEmpty()) {
            return;
        }
        boolean committed = false;
        try (Connection connection = connections.open()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            boolean transactionEnded = false;
            Throwable transactionFailure = null;
            try {
                ensureRow(connection, updates);
                for (Map.Entry<String, DataValue> entry : updates.entrySet()) {
                    updateValue(connection, entry.getKey(), entry.getValue());
                }
                connection.commit();
                committed = true;
                transactionEnded = true;
            } catch (SQLException | RuntimeException | Error e) {
                transactionFailure = e;
                try {
                    connection.rollback();
                    transactionEnded = true;
                } catch (SQLException | RuntimeException rollbackFailure) {
                    suppress(e, rollbackFailure);
                }
                throw e;
            } finally {
                // Switching to auto-commit after a failed rollback can commit a
                // partial batch. Close that connection without restoring its mode.
                if (transactionEnded) {
                    try {
                        connection.setAutoCommit(autoCommit);
                    } catch (SQLException | RuntimeException restoreFailure) {
                        if (transactionFailure != null) {
                            suppress(transactionFailure, restoreFailure);
                        } else {
                            committedCleanupFailure("restore auto-commit", restoreFailure);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            if (committed) {
                // The resource-close path must not make a durable batch retryable.
                committedCleanupFailure("close SQL connection", e);
            } else {
                throw failure("write user values", e);
            }
        } catch (RuntimeException e) {
            if (committed) {
                committedCleanupFailure("close SQL connection", e);
            } else {
                throw e;
            }
        }
    }

    private void committedCleanupFailure(String operation, Exception error) {
        try {
            logger.warn("User values committed, but failed to " + operation + " for " + uuid, error);
        } catch (RuntimeException loggingFailure) {
            // An injected logger must not turn post-commit cleanup into a retry.
            suppress(error, loggingFailure);
        }
    }

    private static void suppress(Throwable primary, Throwable secondary) {
        if (primary != secondary) {
            primary.addSuppressed(secondary);
        }
    }

    private void ensureRow(Connection connection, Map<String, DataValue> updates) throws SQLException {
        Map<String, DataValue> initial = new LinkedHashMap<>();
        for (Map.Entry<String, DataValue> entry : updates.entrySet()) {
            String key = Objects.requireNonNull(entry.getKey(), "key");
            SqlUserSchema.ColumnDefinition definition = schema.column(key);
            if (definition == null) {
                throw new IllegalArgumentException("Column is not registered in the SQL schema: " + key);
            }
            // Case aliases refer to one physical column, with the same last-wins
            // order used by the following updates. Never add UUID metadata here.
            initial.put(definition.name(), entry.getValue());
        }
        // PostgreSQL checks NOT NULL before ON CONFLICT. An existing row can be
        // updated without re-supplying all of its required columns.
        if (dialect == Dialect.POSTGRESQL && rowExists(connection)) return;
        StringBuilder names = new StringBuilder(quote(SqlUserSchema.UUID_COLUMN));
        StringBuilder parameters = new StringBuilder("?");
        for (String key : initial.keySet()) {
            names.append(", ").append(quote(key));
            parameters.append(", ?");
        }
        String prefix = dialect == Dialect.SQLITE ? "INSERT OR IGNORE INTO "
                : dialect == Dialect.POSTGRESQL ? "INSERT INTO " : "INSERT IGNORE INTO ";
        String sql = prefix + quote(tableName) + " (" + names + ") VALUES (" + parameters + ")";
        if (dialect == Dialect.POSTGRESQL) {
            sql += " ON CONFLICT (" + quote(SqlUserSchema.UUID_COLUMN) + ") DO NOTHING";
        }
        int inserted;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            dialect.bindUuid(statement, 1, uuid);
            int index = 2;
            for (DataValue value : initial.values()) bind(statement, index++, value);
            inserted = statement.executeUpdate();
        }
        // OR IGNORE may reject a missing required field, not just a duplicate UUID.
        // Do not acknowledge a batch when no row exists for its bound identity.
        if (inserted == 0 && !rowExists(connection)) {
            throw new SQLException("SQL user row was not created");
        }
    }

    private boolean rowExists(Connection connection) throws SQLException {
        // Only write transactions call this helper. Keep PostgreSQL's existing
        // row locked through UPDATE/commit so a concurrent delete cannot make a
        // successful batch silently update zero rows. contains() stays read-only.
        String sql = "SELECT 1 FROM " + quote(tableName) + " WHERE " + quote(SqlUserSchema.UUID_COLUMN) + "=? LIMIT 1"
                + (dialect == Dialect.POSTGRESQL ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            dialect.bindUuid(statement, 1, uuid);
            try (ResultSet result = statement.executeQuery()) { return result.next(); }
        }
    }

    private void updateValue(Connection connection, String key, DataValue value) throws SQLException {
        Objects.requireNonNull(key, "key");
        if (SqlUserSchema.UUID_COLUMN.equalsIgnoreCase(key)) {
            throw new IllegalArgumentException("uuid is immutable through SqlUserStorage");
        }
        if (!schema.contains(key)) {
            throw new IllegalArgumentException("Column is not registered in the SQL schema: " + key);
        }
        String sql = "UPDATE " + quote(tableName) + " SET " + quote(schema.column(key).name()) + "=? WHERE "
                + quote(SqlUserSchema.UUID_COLUMN) + "=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, 1, value);
            dialect.bindUuid(statement, 2, uuid);
            statement.executeUpdate();
        }
    }

    private void requireStorage(UserStorage requestedStorage) {
        if (requestedStorage != storage) {
            throw new IllegalArgumentException("Storage mismatch: backend=" + storage + ", requested=" + requestedStorage);
        }
    }

    private DataValue readValue(ResultSet result, int index, DataType type) throws SQLException {
        if (type == DataType.INTEGER) {
            try {
                int value = result.getInt(index);
                return new DataValueInt(result.wasNull() ? 0 : value);
            } catch (SQLException invalidInteger) {
                // Legacy string columns can contain invalid/out-of-range integer
                // text. Preserve the zero fallback, but never hide an I/O failure.
                String state = invalidInteger.getSQLState();
                if (invalidInteger instanceof SQLDataException || (state != null && state.startsWith("22"))) {
                    return new DataValueInt(0);
                }
                throw invalidInteger;
            }
        }
        if (type == DataType.BOOLEAN) {
            String value = result.getString(index);
            // Also read numeric values written by the old SQLite setBoolean path.
            return new DataValueBoolean("1".equals(value) || Boolean.parseBoolean(value));
        }
        return new DataValueString(result.getString(index));
    }

    private void bind(PreparedStatement statement, int index, DataValue value) throws SQLException {
        if (value == null) {
            statement.setObject(index, null);
        } else if (value.isString()) {
            statement.setString(index, value.getString());
        } else if (value.isInt()) {
            statement.setInt(index, value.getInt());
        } else if (value.isBoolean()) {
            // UserDataKeyBoolean uses VARCHAR(5), not a native SQL boolean column.
            statement.setString(index, Boolean.toString(value.getBoolean()));
        } else {
            statement.setObject(index, value.toString());
        }
    }

    private String quote(String identifier) {
        return dialect.quote(identifier);
    }

    private IllegalStateException failure(String operation, SQLException error) {
        logger.warn("Failed to " + operation + " for " + uuid, error);
        return new IllegalStateException("Failed to " + operation + " for " + uuid, error);
    }
}
