package com.bencodez.advancedcore.core.user.storage.sql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLDataException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
        SQLITE, MYSQL, POSTGRESQL;
        static Dialect fromDbType(DbType type) {
            return switch (Objects.requireNonNull(type, "type")) {
                case MYSQL, MARIADB -> MYSQL;
                case POSTGRESQL -> POSTGRESQL;
            };
        }
        String quote(String identifier) {
            if (identifier == null || identifier.isBlank() || identifier.indexOf('\0') >= 0) throw new IllegalArgumentException("SQL identifier cannot be blank or contain NUL");
            String delimiter = this == POSTGRESQL ? "\"" : "`";
            return delimiter + identifier.replace(delimiter, delimiter + delimiter) + delimiter;
        }
        void bindUuid(PreparedStatement statement, int index, UUID uuid) throws SQLException {
            if (this == POSTGRESQL) statement.setObject(index, uuid); else statement.setString(index, uuid.toString());
        }
    }

    private enum BooleanStorage { TEXT, NATIVE, NUMERIC, POSTGRES_BIT }
    @FunctionalInterface interface ConnectionProvider { Connection open() throws SQLException; }

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

    @Override public List<Column> readRow(UserStorage requestedStorage) {
        requireStorage(requestedStorage);
        String sql = "SELECT * FROM " + quote(tableName) + " WHERE " + quote(SqlUserSchema.UUID_COLUMN) + "=?";
        try (Connection connection = connections.open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            dialect.bindUuid(statement, 1, uuid);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return new ArrayList<>();
                ResultSetMetaData metadata = result.getMetaData();
                ArrayList<Column> columns = new ArrayList<>(metadata.getColumnCount());
                for (int i = 1; i <= metadata.getColumnCount(); i++) {
                    String name = metadata.getColumnLabel(i);
                    SqlUserSchema.ColumnDefinition definition = schema.column(name);
                    DataType type = definition == null ? DataType.STRING : definition.dataType();
                    Column column = new Column(name, type);
                    column.setValue(readValue(result, i, definition, type));
                    columns.add(column);
                }
                return columns;
            }
        } catch (SQLException e) { throw failure("read user row", e); }
    }

    @Override public boolean contains(UserStorage requestedStorage) {
        requireStorage(requestedStorage);
        String sql = "SELECT 1 FROM " + quote(tableName) + " WHERE " + quote(SqlUserSchema.UUID_COLUMN) + "=? LIMIT 1";
        try (Connection connection = connections.open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            dialect.bindUuid(statement, 1, uuid);
            try (ResultSet result = statement.executeQuery()) { return result.next(); }
        } catch (SQLException e) { throw failure("check user row", e); }
    }

    @Override public void delete(UserStorage requestedStorage) {
        requireStorage(requestedStorage);
        String sql = "DELETE FROM " + quote(tableName) + " WHERE " + quote(SqlUserSchema.UUID_COLUMN) + "=?";
        try (Connection connection = connections.open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            dialect.bindUuid(statement, 1, uuid); statement.executeUpdate();
        } catch (SQLException e) { throw failure("delete user row", e); }
    }

    @Override public void write(UserStorage requestedStorage, String key, DataValue value) {
        requireStorage(requestedStorage);
        if (SqlUserSchema.UUID_COLUMN.equalsIgnoreCase(key)) throw new IllegalArgumentException("uuid is immutable through SqlUserStorage");
        HashMap<String, DataValue> values = new HashMap<>(); values.put(key, value); writeValues(requestedStorage, values);
    }

    @Override public void writeValues(UserStorage requestedStorage, HashMap<String, DataValue> values) {
        requireStorage(requestedStorage);
        Objects.requireNonNull(values, "values");
        Map<String, DataValue> updates = canonicalize(values);
        if (updates.isEmpty()) return;
        boolean committed = false;
        try (Connection connection = connections.open()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            boolean transactionEnded = false;
            Throwable transactionFailure = null;
            try {
                boolean updateExistingRow = ensureRow(connection, updates);
                if (updateExistingRow) updateValues(connection, updates);
                connection.commit(); committed = true; transactionEnded = true;
            } catch (SQLException | RuntimeException | Error e) {
                transactionFailure = e;
                try { connection.rollback(); transactionEnded = true; }
                catch (SQLException | RuntimeException rollbackFailure) { suppress(e, rollbackFailure); }
                throw e;
            } finally {
                if (transactionEnded) {
                    try { connection.setAutoCommit(autoCommit); }
                    catch (SQLException | RuntimeException restoreFailure) {
                        if (transactionFailure != null) suppress(transactionFailure, restoreFailure); else committedCleanupFailure("restore auto-commit", restoreFailure);
                    }
                }
            }
        } catch (SQLException e) {
            if (committed) committedCleanupFailure("close SQL connection", e); else throw failure("write user values", e);
        } catch (RuntimeException e) {
            if (committed) committedCleanupFailure("close SQL connection", e); else throw e;
        }
    }

    private Map<String, DataValue> canonicalize(Map<String, DataValue> values) {
        Map<String, DataValue> updates = new LinkedHashMap<>();
        for (Map.Entry<String, DataValue> entry : values.entrySet()) {
            String key = Objects.requireNonNull(entry.getKey(), "key");
            if (SqlUserSchema.UUID_COLUMN.equalsIgnoreCase(key)) continue;
            SqlUserSchema.ColumnDefinition definition = schema.column(key);
            if (definition == null) throw new IllegalArgumentException("Column is not registered in the SQL schema: " + key);
            if (updates.containsKey(definition.name())) throw new IllegalArgumentException("Duplicate SQL column in write batch: " + definition.name());
            updates.put(definition.name(), entry.getValue());
        }
        return updates;
    }

    private void committedCleanupFailure(String operation, Exception error) {
        try { logger.warn("User values committed, but failed to " + operation + " for " + uuid, error); }
        catch (RuntimeException loggingFailure) { suppress(error, loggingFailure); }
    }
    private static void suppress(Throwable primary, Throwable secondary) { if (primary != secondary) primary.addSuppressed(secondary); }

    /** @return true when the row already existed and still needs the batch UPDATE. */
    private boolean ensureRow(Connection connection, Map<String, DataValue> updates) throws SQLException {
        if (dialect != Dialect.SQLITE && rowExists(connection)) return true;
        StringBuilder names = new StringBuilder(quote(SqlUserSchema.UUID_COLUMN));
        StringBuilder parameters = new StringBuilder("?");
        for (String key : updates.keySet()) {
            names.append(", ").append(quote(key));
            parameters.append(", ").append(parameterExpression(schema.column(key)));
        }
        String prefix = dialect == Dialect.SQLITE ? "INSERT OR IGNORE INTO " : "INSERT INTO ";
        String sql = prefix + quote(tableName) + " (" + names + ") VALUES (" + parameters + ")";
        if (dialect == Dialect.POSTGRESQL) sql += " ON CONFLICT (" + quote(SqlUserSchema.UUID_COLUMN) + ") DO NOTHING";
        int inserted;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            dialect.bindUuid(statement, 1, uuid);
            int index = 2;
            for (Map.Entry<String, DataValue> entry : updates.entrySet()) bind(statement, index++, entry.getValue(), schema.column(entry.getKey()));
            inserted = statement.executeUpdate();
        } catch (SQLException insertFailure) {
            if (dialect == Dialect.MYSQL && isDuplicateKey(insertFailure) && rowExists(connection)) return true;
            throw insertFailure;
        }
        if (inserted > 0) return false;
        if (!rowExists(connection)) throw new SQLException("SQL user row was not created");
        return true;
    }

    private boolean isDuplicateKey(SQLException failure) { return failure.getErrorCode() == 1062 || "23000".equals(failure.getSQLState()); }

    private boolean rowExists(Connection connection) throws SQLException {
        String sql = "SELECT 1 FROM " + quote(tableName) + " WHERE " + quote(SqlUserSchema.UUID_COLUMN) + "=? LIMIT 1" + (dialect == Dialect.SQLITE ? "" : " FOR UPDATE");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            dialect.bindUuid(statement, 1, uuid);
            try (ResultSet result = statement.executeQuery()) { return result.next(); }
        }
    }

    private void updateValues(Connection connection, Map<String, DataValue> updates) throws SQLException {
        StringBuilder sql = new StringBuilder("UPDATE ").append(quote(tableName)).append(" SET ");
        boolean first = true;
        for (String key : updates.keySet()) {
            if (!first) sql.append(", ");
            first = false;
            SqlUserSchema.ColumnDefinition definition = schema.column(key);
            sql.append(quote(key)).append('=').append(parameterExpression(definition));
        }
        sql.append(" WHERE ").append(quote(SqlUserSchema.UUID_COLUMN)).append("=?");
        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            int index = 1;
            for (Map.Entry<String, DataValue> entry : updates.entrySet()) bind(statement, index++, entry.getValue(), schema.column(entry.getKey()));
            dialect.bindUuid(statement, index, uuid); statement.executeUpdate();
        }
    }

    private void requireStorage(UserStorage requestedStorage) { if (requestedStorage != storage) throw new IllegalArgumentException("Storage mismatch: backend=" + storage + ", requested=" + requestedStorage); }

    private DataValue readValue(ResultSet result, int index, SqlUserSchema.ColumnDefinition definition, DataType type) throws SQLException {
        if (type == DataType.INTEGER) {
            try { int value = result.getInt(index); return new DataValueInt(result.wasNull() ? 0 : value); }
            catch (SQLException invalidInteger) {
                String state = invalidInteger.getSQLState();
                if (invalidInteger instanceof SQLDataException || (state != null && state.startsWith("22"))) return new DataValueInt(0);
                throw invalidInteger;
            }
        }
        if (type == DataType.BOOLEAN) {
            BooleanStorage booleanStorage = booleanStorage(definition);
            if (booleanStorage == BooleanStorage.NATIVE) { boolean value = result.getBoolean(index); return new DataValueBoolean(!result.wasNull() && value); }
            if (booleanStorage == BooleanStorage.NUMERIC) { int value = result.getInt(index); return new DataValueBoolean(!result.wasNull() && value != 0); }
            String value = result.getString(index);
            if (booleanStorage == BooleanStorage.POSTGRES_BIT) return new DataValueBoolean(value != null && value.indexOf('1') >= 0);
            return new DataValueBoolean("1".equals(value) || "t".equalsIgnoreCase(value) || "true".equalsIgnoreCase(value));
        }
        return new DataValueString(result.getString(index));
    }

    private void bind(PreparedStatement statement, int index, DataValue value, SqlUserSchema.ColumnDefinition definition) throws SQLException {
        if (value == null) statement.setObject(index, null);
        else if (value.isString()) statement.setString(index, value.getString());
        else if (value.isInt()) {
            // PostgreSQL's unspecified parameter type is inferred from the target
            // column. This preserves writes to both current integer columns and
            // legacy text columns retained by schema discovery.
            if (dialect == Dialect.POSTGRESQL) statement.setObject(index, Integer.toString(value.getInt()), Types.OTHER);
            else statement.setInt(index, value.getInt());
        }
        else if (value.isBoolean()) {
            BooleanStorage booleanStorage = booleanStorage(definition);
            if (booleanStorage == BooleanStorage.NATIVE) statement.setBoolean(index, value.getBoolean());
            else if (booleanStorage == BooleanStorage.NUMERIC) statement.setInt(index, value.getBoolean() ? 1 : 0);
            else if (booleanStorage == BooleanStorage.POSTGRES_BIT) statement.setString(index, value.getBoolean() ? "1" : "0");
            else statement.setString(index, Boolean.toString(value.getBoolean()));
        } else statement.setObject(index, value.toString());
    }

    private String parameterExpression(SqlUserSchema.ColumnDefinition definition) {
        if (booleanStorage(definition) != BooleanStorage.POSTGRES_BIT) return "?";
        return "CAST(? AS " + postgresBitType(definition) + ")";
    }

    private String postgresBitType(SqlUserSchema.ColumnDefinition definition) {
        String sqlType = definition.sqlType().strip();
        int separator = sqlType.indexOf(' ');
        String token = separator < 0 ? sqlType : sqlType.substring(0, separator);
        if (!token.matches("(?i)BIT(?:\\(\\d+\\))?")) {
            throw new IllegalArgumentException("Unsupported PostgreSQL bit type: " + definition.sqlType());
        }
        return token.toUpperCase(Locale.ROOT);
    }

    private BooleanStorage booleanStorage(SqlUserSchema.ColumnDefinition definition) {
        if (definition == null || definition.dataType() != DataType.BOOLEAN) return BooleanStorage.TEXT;
        String sqlType = definition.sqlType().strip().toUpperCase(Locale.ROOT);
        if (startsType(sqlType, "BOOLEAN") || startsType(sqlType, "BOOL")) return BooleanStorage.NATIVE;
        if (startsType(sqlType, "BIT")) return dialect == Dialect.POSTGRESQL ? BooleanStorage.POSTGRES_BIT : BooleanStorage.NUMERIC;
        if (startsType(sqlType, "TINYINT") || startsType(sqlType, "SMALLINT") || startsType(sqlType, "MEDIUMINT") || startsType(sqlType, "INT") || startsType(sqlType, "INTEGER") || startsType(sqlType, "BIGINT")) return BooleanStorage.NUMERIC;
        return BooleanStorage.TEXT;
    }
    private boolean startsType(String sqlType, String type) { if (!sqlType.startsWith(type)) return false; if (sqlType.length() == type.length()) return true; char next = sqlType.charAt(type.length()); return Character.isWhitespace(next) || next == '('; }
    private String quote(String identifier) { return dialect.quote(identifier); }
    private IllegalStateException failure(String operation, SQLException error) {
        try { logger.warn("Failed to " + operation + " for " + uuid, error); }
        catch (RuntimeException loggingFailure) { suppress(error, loggingFailure); }
        return new IllegalStateException("Failed to " + operation + " for " + uuid, error);
    }
}
