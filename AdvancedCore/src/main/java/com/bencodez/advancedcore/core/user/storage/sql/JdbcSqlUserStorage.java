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
        try (Connection connection = connections.open()) {
            return readRow(connection);
        } catch (SQLException e) { throw failure("read user row", e); }
    }

    private List<Column> readRow(Connection connection) throws SQLException {
        String sql = "SELECT * FROM " + quote(tableName) + " WHERE " + quote(SqlUserSchema.UUID_COLUMN) + "=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            dialect.bindUuid(statement, 1, uuid);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return new ArrayList<>();
                ResultSetMetaData metadata = result.getMetaData();
                ArrayList<Column> columns = new ArrayList<>(metadata.getColumnCount());
                for (int i = 1; i <= metadata.getColumnCount(); i++) {
                    String name = metadata.getColumnLabel(i);
                    SqlUserSchema.ColumnDefinition definition = schema.column(name);
                    DataType type = definition == null ? DataType.STRING : definition.dataType();
                    Column column = new Column(definition == null ? name : definition.name(), type);
                    column.setValue(readValue(result, i, type));
                    columns.add(column);
                }
                return columns;
            }
        }
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
        inTransaction("write user values", connection -> {
            writeValues(connection, updates);
            return null;
        });
    }

    @Override public <T> T transaction(UserStorage requestedStorage, Map<String, DataValue> initialValues, TransactionWork<T> work) {
        requireStorage(requestedStorage);
        Objects.requireNonNull(work, "work");
        Map<String, DataValue> seed = canonicalize(Objects.requireNonNull(initialValues, "initialValues"));
        return inTransaction("run user transaction", connection -> {
            boolean createdUserRow = !ensureRow(connection, seed);
            Scope scope = new Scope(connection, createdUserRow);
            try { return work.run(scope); }
            finally { scope.active = false; }
        });
    }

    private final class Scope implements TransactionScope {
        private final Connection connection;
        private final boolean createdUserRow;
        private boolean active = true;
        private Scope(Connection connection, boolean createdUserRow) {
            this.connection = connection;
            this.createdUserRow = createdUserRow;
        }
        private void requireActive() { if (!active) throw new IllegalStateException("SQL user transaction scope has ended"); }
        @Override public boolean createdUserRow() { requireActive(); return createdUserRow; }
        @Override public Connection connection() { requireActive(); return connection; }
        @Override public List<Column> readRow() throws SQLException { requireActive(); return JdbcSqlUserStorage.this.readRow(connection); }
        @Override public void writeValues(Map<String, DataValue> values) throws SQLException {
            requireActive();
            JdbcSqlUserStorage.this.writeValues(connection, canonicalize(Objects.requireNonNull(values, "values")));
        }
    }

    private void writeValues(Connection connection, Map<String, DataValue> updates) throws SQLException {
        if (updates.isEmpty()) return;
        boolean updateExistingRow = ensureRow(connection, updates);
        if (updateExistingRow) updateValues(connection, updates);
    }

    private <T> T inTransaction(String operation, TransactionWorkOnConnection<T> work) {
        boolean committed = false;
        T result = null;
        try (Connection connection = connections.open()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            boolean transactionEnded = false;
            Throwable transactionFailure = null;
            try {
                result = work.run(connection);
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
            if (committed) committedCleanupFailure("close SQL connection", e); else throw failure(operation, e);
        } catch (RuntimeException e) {
            if (committed) committedCleanupFailure("close SQL connection", e); else throw e;
        }
        return result;
    }

    @FunctionalInterface private interface TransactionWorkOnConnection<T> { T run(Connection connection) throws SQLException; }

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
        Map<String, SqlUserSchema.ColumnDefinition> definitions = retainedDefinitions(connection, updates);
        StringBuilder names = new StringBuilder(quote(SqlUserSchema.UUID_COLUMN));
        StringBuilder parameters = new StringBuilder("?");
        for (String key : updates.keySet()) {
            names.append(", ").append(quote(key));
            parameters.append(", ").append(parameterExpression(definitions.get(key)));
        }
        String prefix = dialect == Dialect.SQLITE ? "INSERT OR IGNORE INTO " : "INSERT INTO ";
        String sql = prefix + quote(tableName) + " (" + names + ") VALUES (" + parameters + ")";
        if (dialect == Dialect.POSTGRESQL) sql += " ON CONFLICT (" + quote(SqlUserSchema.UUID_COLUMN) + ") DO NOTHING";
        int inserted;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            dialect.bindUuid(statement, 1, uuid);
            int index = 2;
            for (Map.Entry<String, DataValue> entry : updates.entrySet()) bind(statement, index++, entry.getValue(), definitions.get(entry.getKey()));
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
        Map<String, SqlUserSchema.ColumnDefinition> definitions = retainedDefinitions(connection, updates);
        StringBuilder sql = new StringBuilder("UPDATE ").append(quote(tableName)).append(" SET ");
        boolean first = true;
        for (String key : updates.keySet()) {
            if (!first) sql.append(", ");
            first = false;
            sql.append(quote(key)).append('=').append(parameterExpression(definitions.get(key)));
        }
        sql.append(" WHERE ").append(quote(SqlUserSchema.UUID_COLUMN)).append("=?");
        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            int index = 1;
            for (Map.Entry<String, DataValue> entry : updates.entrySet()) bind(statement, index++, entry.getValue(), definitions.get(entry.getKey()));
            dialect.bindUuid(statement, index, uuid); statement.executeUpdate();
        }
    }

    private void requireStorage(UserStorage requestedStorage) { if (requestedStorage != storage) throw new IllegalArgumentException("Storage mismatch: backend=" + storage + ", requested=" + requestedStorage); }

    private DataValue readValue(ResultSet result, int index, DataType type) throws SQLException {
        if (type == DataType.INTEGER) {
            try { int value = result.getInt(index); return new DataValueInt(result.wasNull() ? 0 : value); }
            catch (SQLException invalidInteger) {
                String state = invalidInteger.getSQLState();
                if (invalidInteger instanceof SQLDataException || (state != null && state.startsWith("22"))) return new DataValueInt(0);
                throw invalidInteger;
            }
        }
        if (type == DataType.BOOLEAN) {
            String value = result.getString(index);
            if (value == null) return new DataValueBoolean(false);
            String normalized = value.strip();
            if ("true".equalsIgnoreCase(normalized) || "t".equalsIgnoreCase(normalized)
                    || "yes".equalsIgnoreCase(normalized) || "y".equalsIgnoreCase(normalized)
                    || "on".equalsIgnoreCase(normalized)) return new DataValueBoolean(true);
            if ("false".equalsIgnoreCase(normalized) || "f".equalsIgnoreCase(normalized)
                    || "no".equalsIgnoreCase(normalized) || "n".equalsIgnoreCase(normalized)
                    || "off".equalsIgnoreCase(normalized) || normalized.isEmpty()) return new DataValueBoolean(false);
            try { return new DataValueBoolean(new java.math.BigDecimal(normalized).signum() != 0); }
            catch (NumberFormatException ignored) { return new DataValueBoolean(false); }
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
            // PostgreSQL accepts an unspecified parameter using the retained
            // target column's input function.  This matters when an existing
            // installation still has a legacy VARCHAR boolean column even
            // though the current schema declaration is BOOLEAN: setBoolean
            // sends a typed boolean parameter which PostgreSQL will not assign
            // to VARCHAR.  Keep BIT explicit below because its width is part of
            // the value contract.
            if (booleanStorage == BooleanStorage.NATIVE && dialect == Dialect.POSTGRESQL)
                statement.setObject(index, Boolean.toString(value.getBoolean()), Types.OTHER);
            else if (booleanStorage == BooleanStorage.NATIVE) statement.setBoolean(index, value.getBoolean());
            else if (booleanStorage == BooleanStorage.NUMERIC) statement.setInt(index, value.getBoolean() ? 1 : 0);
            else if (booleanStorage == BooleanStorage.POSTGRES_BIT) statement.setString(index, value.getBoolean() ? "1" : "0");
            else statement.setString(index, Boolean.toString(value.getBoolean()));
        } else statement.setObject(index, value.toString());
    }

    private String parameterExpression(SqlUserSchema.ColumnDefinition definition) {
        BooleanStorage storage = booleanStorage(definition);
        if (storage != BooleanStorage.POSTGRES_BIT) return "?";
        return "CAST(? AS " + postgresBitType(definition) + ")";
    }

    private Map<String, SqlUserSchema.ColumnDefinition> retainedDefinitions(Connection connection, Map<String, DataValue> updates) throws SQLException {
        Map<String, SqlUserSchema.ColumnDefinition> definitions = new HashMap<>();
        for (String key : updates.keySet()) {
            SqlUserSchema.ColumnDefinition definition = schema.column(key);
            definitions.put(key, retainedDefinition(connection, definition));
        }
        return definitions;
    }

    private SqlUserSchema.ColumnDefinition retainedDefinition(Connection connection, SqlUserSchema.ColumnDefinition definition) throws SQLException {
        if (definition == null || definition.dataType() != DataType.BOOLEAN) return definition;
        java.sql.DatabaseMetaData metadata = connection.getMetaData();
        if (metadata == null) return definition;
        String catalog = dialect == Dialect.POSTGRESQL ? null : connection.getCatalog();
        try (ResultSet columns = metadata.getColumns(catalog, metadataPattern(metadata, metadataSchema(connection)),
                metadataPattern(metadata, tableName), metadataPattern(metadata, definition.name()))) {
            if (columns.next()) {
                String type = columns.getString("TYPE_NAME");
                if (type != null && !type.isBlank()) {
                    String normalized = type.strip().toUpperCase(Locale.ROOT);
                    if (dialect == Dialect.POSTGRESQL
                            && (startsType(normalized, "BIT") || startsType(normalized, "VARBIT"))) {
                        int width = columns.getInt("COLUMN_SIZE");
                        if (columns.wasNull() || width <= 0) width = 1;
                        // PgJDBC exposes this as VARBIT on supported versions, but
                        // accept the SQL spelling too so preserving a legacy column
                        // does not accidentally turn BIT VARYING(n) into BIT(n).
                        String retainedType = postgresVaryingBit(normalized)
                                ? "BIT VARYING(" + width + ")" : "BIT(" + width + ")";
                        return new SqlUserSchema.ColumnDefinition(definition.name(), retainedType, DataType.BOOLEAN);
                    }
                    // The current logical schema may say BOOLEAN while an existing
                    // server still has a VARCHAR or numeric column. Bind according
                    // to the retained physical type instead of sending a typed
                    // boolean that PostgreSQL cannot assign to that column.
                    return new SqlUserSchema.ColumnDefinition(definition.name(), type, DataType.BOOLEAN);
                }
            }
        }
        return definition;
    }

    /**
     * JDBC metadata accepts SQL LIKE patterns rather than exact identifiers.
     * Treat these user-table identifiers literally so an underscore or percent
     * sign cannot select a similarly named table/column's physical type.
     */
    private String metadataPattern(java.sql.DatabaseMetaData metadata, String identifier) throws SQLException {
        if (identifier == null) return null;
        String escape = metadata.getSearchStringEscape();
        if (escape == null || escape.isEmpty()) return identifier;
        return identifier.replace(escape, escape + escape)
                .replace("_", escape + "_")
                .replace("%", escape + "%");
    }

    private boolean postgresVaryingBit(String normalizedType) {
        return startsType(normalizedType, "VARBIT") || normalizedType.matches("^BIT\\s+VARYING(?:\\s*\\(\\s*\\d+\\s*\\))?(?:\\s+.*)?$");
    }

    private String metadataSchema(Connection connection) throws SQLException {
        if (dialect != Dialect.POSTGRESQL) return null;
        String regclass = '"' + tableName.replace("\"", "\"\"") + '"';
        String sql = "SELECT n.nspname FROM pg_catalog.pg_class c JOIN pg_catalog.pg_namespace n "
                + "ON n.oid=c.relnamespace WHERE c.oid=pg_catalog.to_regclass(?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, regclass);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getString(1) : null;
            }
        }
    }

    private String postgresBitType(SqlUserSchema.ColumnDefinition definition) {
        String sqlType = definition.sqlType().strip();
        java.util.regex.Matcher varbit = java.util.regex.Pattern
                .compile("(?i)^VARBIT(?:\\s*\\(\\s*(\\d+)\\s*\\))?(?:\\s+.*)?$").matcher(sqlType);
        if (varbit.matches()) return "BIT VARYING" + (varbit.group(1) == null ? "" : "(" + varbit.group(1) + ")");
        java.util.regex.Matcher type = java.util.regex.Pattern
                .compile("(?i)^BIT(\\s+VARYING)?(?:\\s*\\(\\s*(\\d+)\\s*\\))?(?:\\s+.*)?$")
                .matcher(sqlType);
        if (!type.matches()) {
            throw new IllegalArgumentException("Unsupported PostgreSQL bit type: " + definition.sqlType());
        }
        return (type.group(1) == null ? "BIT" : "BIT VARYING")
                + (type.group(2) == null ? "" : "(" + type.group(2) + ")");
    }

    private BooleanStorage booleanStorage(SqlUserSchema.ColumnDefinition definition) {
        if (definition == null || definition.dataType() != DataType.BOOLEAN) return BooleanStorage.TEXT;
        String sqlType = definition.sqlType().strip().toUpperCase(Locale.ROOT);
        if (startsType(sqlType, "BOOLEAN") || startsType(sqlType, "BOOL")) return BooleanStorage.NATIVE;
        if (startsType(sqlType, "BIT") || startsType(sqlType, "VARBIT")) return dialect == Dialect.POSTGRESQL ? BooleanStorage.POSTGRES_BIT : BooleanStorage.NUMERIC;
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
