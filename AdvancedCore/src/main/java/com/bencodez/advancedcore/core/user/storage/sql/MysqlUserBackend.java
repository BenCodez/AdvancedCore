package com.bencodez.advancedcore.core.user.storage.sql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
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
    private static final long PEER_MIGRATION_GRACE_NANOS = TimeUnit.SECONDS.toNanos(5);
    private static final long PEER_MIGRATION_RECHECK_MILLIS = 50;
    private final SqlUserSchema schema;
    private final SqlBackendLogger logger;
    private final HeadlessUserTable table;
    private final AtomicBoolean open = new AtomicBoolean(true);
    private final AtomicBoolean invalidUuidWarningLogged = new AtomicBoolean();
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
        requireAdmissionOpen();
        JdbcSqlUserStorage.Dialect dialect = JdbcSqlUserStorage.Dialect.fromDbType(table.getMysql().getConnectionManager().getDbType());
        SqlUserStorage delegate = new JdbcSqlUserStorage(UserStorage.MYSQL, uuid, table.getTableName(), schema,
                () -> table.getMysql().getConnectionManager().getConnection(), dialect, logger);
        return new SqlUserStorage() {
            @Override public List<Column> readRow(UserStorage storage) { return withOperation(() -> delegate.readRow(storage)); }
            @Override public boolean contains(UserStorage storage) { return withOperation(() -> delegate.contains(storage)); }
            @Override public void delete(UserStorage storage) { withOperation(() -> { delegate.delete(storage); return null; }); }
            @Override public void write(UserStorage storage, String key, DataValue value) { withOperation(() -> { delegate.write(storage, key, value); return null; }); }
            @Override public void writeValues(UserStorage storage, HashMap<String, DataValue> values) { withOperation(() -> { delegate.writeValues(storage, values); return null; }); }
            @Override public <T> T transaction(UserStorage storage, TransactionWork<T> work) { return withOperation(() -> delegate.transaction(storage, work)); }
            @Override public <T> T transaction(UserStorage storage, java.util.Map<String, DataValue> initialValues, TransactionWork<T> work) {
                return withOperation(() -> delegate.transaction(storage, initialValues, work));
            }
        };
    }

    @Override public List<UUID> enumerateUsers() {
        ArrayList<UUID> users = new ArrayList<>();
        forEachUser(uuid -> {
            if (users.size() >= MAX_MATERIALIZED_USERS) throw new IllegalStateException("User enumeration exceeds " + MAX_MATERIALIZED_USERS + " entries; use forEachUser for streaming access");
            users.add(uuid);
        });
        return users;
    }

    @Override public void forEachUser(Consumer<UUID> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        withOperation(() -> {
            String cursor = null;
            while (true) {
                List<UserPageEntry> page = readUserPage(cursor);
                if (page.isEmpty()) return null;
                cursor = page.get(page.size() - 1).cursor();
                for (UserPageEntry entry : page) if (entry.uuid() != null) consumer.accept(entry.uuid());
                if (page.size() < USER_PAGE_SIZE) return null;
            }
        });
    }

    private List<UserPageEntry> readUserPage(String cursor) {
        String uuidColumn = table.quote(SqlUserSchema.UUID_COLUMN);
        String sql = "SELECT " + uuidColumn + " FROM " + table.quote(table.getTableName())
                + " WHERE " + uuidColumn + " IS NOT NULL"
                + (cursor == null ? "" : " AND " + uuidColumn + " > ?")
                + " ORDER BY " + uuidColumn + " ASC LIMIT ?";
        JdbcSqlUserStorage.Dialect dialect = JdbcSqlUserStorage.Dialect.fromDbType(table.getDbType());
        try (Connection connection = table.getMysql().getConnectionManager().getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            if (cursor != null) {
                if (dialect == JdbcSqlUserStorage.Dialect.POSTGRESQL) dialect.bindUuid(statement, index++, UUID.fromString(cursor)); else statement.setString(index++, cursor);
            }
            statement.setInt(index, USER_PAGE_SIZE);
            ArrayList<UserPageEntry> page = new ArrayList<>(USER_PAGE_SIZE);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    String value = result.getString(1);
                    if (value == null) continue;
                    UUID parsed = null;
                    try {
                        parsed = UUID.fromString(value);
                        if (!parsed.toString().equals(value)) throw new IllegalArgumentException("Non-canonical UUID");
                    }
                    catch (IllegalArgumentException invalid) {
                        parsed = null;
                        if (invalidUuidWarningLogged.compareAndSet(false, true)) {
                            logger.warn("Skipping malformed UUID entries while enumerating SQL users; further diagnostics suppressed",
                                    new IllegalArgumentException("Malformed SQL UUID value"));
                        }
                    }
                    page.add(new UserPageEntry(value, parsed));
                }
            }
            return page;
        } catch (IllegalArgumentException invalidCursor) { throw new IllegalStateException("Failed to advance SQL user enumeration cursor", invalidCursor); }
        catch (SQLException failure) { throw new IllegalStateException("Failed to enumerate MySQL users", failure); }
    }

    private record UserPageEntry(String cursor, UUID uuid) {}

    @Override public boolean isOpen() { return open.get(); }

    @Override public void close() {
        if (operations.getReadHoldCount() != 0) throw new IllegalStateException("Cannot close MySQL from inside an active storage operation");
        open.set(false);
        operations.writeLock().lock();
        try {
            if (!tableClosed) { table.close(); tableClosed = true; }
        } finally { operations.writeLock().unlock(); }
    }

    private <T> T withOperation(Supplier<T> operation) {
        requireAdmissionOpen();
        operations.readLock().lock();
        try {
            requireAdmissionOpen();
            return operation.get();
        } finally { operations.readLock().unlock(); }
    }

    /**
     * Reconcile a platform-owned MySQL/MariaDB/PostgreSQL user table without
     * taking ownership of its connection pool.
     */
    public static void reconcileExistingTable(String tableName,
            com.bencodez.simpleapi.sql.mysql.MySQL mysql, SqlUserSchema schema, SqlBackendLogger logger) {
        Objects.requireNonNull(tableName, "tableName");
        Objects.requireNonNull(mysql, "mysql");
        Objects.requireNonNull(schema, "schema");
        HeadlessUserTable table = new HeadlessUserTable(tableName, mysql, schema,
                logger == null ? SqlBackendLogger.NO_OP : logger);
        ensureRegisteredColumns(table, schema);
    }

    private void ensureRegisteredColumns() {
        ensureRegisteredColumns(table, schema);
    }

    private static void ensureRegisteredColumns(HeadlessUserTable table, SqlUserSchema schema) {
        table.ensureUuidType();
        table.ensureUuidUnique();
        for (SqlUserSchema.ColumnDefinition column : schema.columns()) if (!SqlUserSchema.UUID_COLUMN.equalsIgnoreCase(column.name())) table.ensureColumn(column);
    }

    private void requireAdmissionOpen() {
        if (!open.get()) throw new IllegalStateException("MySQL user backend is closed");
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

        /** Borrow an existing pool; reconciliation must never close or replace it. */
        HeadlessUserTable(String tableName, com.bencodez.simpleapi.sql.mysql.MySQL mysql,
                SqlUserSchema schema, SqlBackendLogger logger) {
            super(tableName, mysql, true);
            this.schema = schema;
            this.logger = logger;
            // Reconciliation needs table DDL and live schema inspection only. Calling
            // init() here would also load the borrowed table's complete primary-key
            // cache, needlessly enumerating every user during a schema check.
            ensureTable();
        }

        @Override public String getPrimaryKeyColumn() { return SqlUserSchema.UUID_COLUMN; }
        @Override public String buildCreateTableSql(DbType dbType) {
            StringBuilder sql = new StringBuilder("CREATE TABLE IF NOT EXISTS ").append(quote(tableName)).append(" (");
            boolean first = true;
            for (SqlUserSchema.ColumnDefinition column : schema.columns()) {
                if (!first) sql.append(", "); first = false;
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
            try {
                String uuidType = bestUuidType();
                if (!columnNeedsAlter(SqlUserSchema.UUID_COLUMN, uuidType)) return;
                String uuidColumn = quote(SqlUserSchema.UUID_COLUMN);
                String sql = getDbType() == DbType.POSTGRESQL
                        ? "ALTER TABLE " + quote(tableName) + " ALTER COLUMN " + uuidColumn + " TYPE "
                                + uuidType + " USING NULLIF(" + uuidColumn + ", '')::uuid;"
                        : "ALTER TABLE " + quote(tableName) + " MODIFY " + uuidColumn + " "
                                + normaliseTypeForDb(uuidType) + ";";
                try (Connection connection = getMysql().getConnectionManager().getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) { statement.executeUpdate(); }
                catch (SQLException ddlFailure) {
                    try { if (columnNeedsAlter(SqlUserSchema.UUID_COLUMN, uuidType)) throw ddlFailure; }
                    catch (SQLException inspectionFailure) {
                        if (inspectionFailure != ddlFailure) ddlFailure.addSuppressed(inspectionFailure);
                        throw ddlFailure;
                    }
                }
            } catch (SQLException failure) { throw new IllegalStateException("Failed to initialize SQL UUID column", failure); }
        }

        void ensureUuidUnique() {
            try (Connection connection = getMysql().getConnectionManager().getConnection()) {
                java.sql.DatabaseMetaData metadata = connection.getMetaData();
                if (metadata == null || hasUniqueUuidConstraint(connection, metadata)) return;
                String indexName = tableName + "_uuid_unique";
                String sql = "ALTER TABLE " + quote(tableName) + " ADD CONSTRAINT " + quote(indexName)
                        + " UNIQUE (" + quote(SqlUserSchema.UUID_COLUMN) + ")";
                try (PreparedStatement statement = connection.prepareStatement(sql)) { statement.executeUpdate(); }
                catch (SQLException ddlFailure) {
                    // Another opener may have created the constraint after our metadata
                    // check.  Re-inspect the database before treating that race as fatal.
                    if (!hasUniqueUuidConstraint(connection, connection.getMetaData())) throw ddlFailure;
                }
            } catch (SQLException failure) {
                throw new IllegalStateException("Failed to initialize SQL UUID uniqueness", failure);
            }
        }

        private boolean hasUniqueUuidConstraint(Connection connection, java.sql.DatabaseMetaData metadata) throws SQLException {
            String catalog = metadata.getConnection() == null ? null : metadata.getConnection().getCatalog();
            String schema = resolvedMetadataSchema(connection, metadata);
            try (ResultSet keys = metadata.getPrimaryKeys(catalog, schema, tableName)) {
                String keyName = null; int count = 0; boolean uuid = false;
                while (keys.next()) { keyName = keys.getString("PK_NAME"); count++; uuid |= SqlUserSchema.UUID_COLUMN.equalsIgnoreCase(keys.getString("COLUMN_NAME")); }
                if (count == 1 && uuid) return true;
            }
            try (ResultSet indexes = metadata.getIndexInfo(catalog, schema, tableName, true, false)) {
                Map<String, Integer> counts = new HashMap<>(); Map<String, Boolean> uuids = new HashMap<>();
                while (indexes.next()) {
                    String name = indexes.getString("INDEX_NAME");
                    if (name == null) continue;
                    // PostgreSQL exposes partial indexes through FILTER_CONDITION. They
                    // only constrain a subset of rows and cannot protect user identity.
                    if (indexes.getString("FILTER_CONDITION") != null) continue;
                    String column = indexes.getString("COLUMN_NAME");
                    counts.merge(name, 1, Integer::sum);
                    uuids.merge(name, column != null && SqlUserSchema.UUID_COLUMN.equalsIgnoreCase(column), Boolean::logicalOr);
                }
                for (String name : counts.keySet()) if (counts.get(name) == 1 && uuids.getOrDefault(name, false)) return true;
            }
            return false;
        }

        /**
         * PostgreSQL treats a null schema in DatabaseMetaData calls as a wildcard.
         * Resolve the table selected by search_path first, otherwise a same-named
         * table in another schema can make us accept the wrong uniqueness metadata.
         */
        private String resolvedMetadataSchema(Connection connection, java.sql.DatabaseMetaData metadata) throws SQLException {
            if (getDbType() != DbType.POSTGRESQL) return null;
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT table_schema FROM information_schema.tables "
                            + "WHERE table_name=? AND table_schema=ANY(current_schemas(false)) "
                            + "ORDER BY array_position(current_schemas(false), table_schema) LIMIT 1")) {
                statement.setString(1, tableName);
                try (ResultSet result = statement.executeQuery()) {
                    if (result.next()) return result.getString(1);
                }
            }
            // Views and unusual metadata implementations may not appear in
            // information_schema.tables; getSchema is still narrower than null.
            String current = connection.getSchema();
            return current == null || current.isBlank() ? metadata.getUserName() : current;
        }

        void ensureColumn(SqlUserSchema.ColumnDefinition column) {
            synchronized (checkColumnLock) {
                try {
                    String storedName = findRegisteredColumn(column.name());
                    if (storedName != null) {
                        if (column.dataType() == DataType.STRING)
                            migrateRetainedColumnToString(storedName, column);
                        if (getDbType() == DbType.POSTGRESQL && !storedName.equals(column.name())) renamePostgresColumn(storedName, column.name());
                        rememberColumn(column); return;
                    }
                    String sql = "ALTER TABLE " + quote(tableName) + " ADD COLUMN " + quote(column.name()) + " " + normaliseTypeForDb(column.sqlType()) + ";";
                    try (Connection connection = getMysql().getConnectionManager().getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) { statement.executeUpdate(); }
                    catch (SQLException ddlFailure) {
                        if (!isDuplicateColumn(ddlFailure)) throw ddlFailure;
                        try {
                            String raced = findRegisteredColumn(column.name());
                            if (raced == null) throw ddlFailure;
                            if (column.dataType() == DataType.STRING)
                                migrateRetainedColumnToString(raced, column);
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
            try (Connection connection = getMysql().getConnectionManager().getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) { statement.executeUpdate(); }
            catch (SQLException renameFailure) { String current = findRegisteredColumn(requestedName); if (!requestedName.equals(current)) throw renameFailure; }
        }

        private void migrateRetainedColumnToString(String storedName,
                SqlUserSchema.ColumnDefinition definition) throws SQLException {
            // DataType.STRING describes the Java value API, but plugins may deliberately
            // retain a numeric SQL representation (for example an epoch millisecond).
            // In that case the existing numeric column already matches the requested
            // schema and must not be reconciled as a legacy numeric-to-text column.
            RegisteredColumnType registeredType = registeredColumnType(storedName);
            boolean declaredStorageType = declaredTypeIsNumericOrBoolean(definition.sqlType());
            if (declaredStorageType && getDbType() == DbType.POSTGRESQL
                    && declaredTypeMatches(definition.sqlType(), registeredType)) return;
            if (!isNumericOrBoolean(registeredType.jdbcType())) return;
            String column = quote(storedName);
            if (getDbType() != DbType.POSTGRESQL) {
                MysqlColumnAttributes attributes;
                try {
					attributes = mysqlColumnAttributes(storedName);
					if (declaredStorageType
							&& mysqlDeclaredTypeMatches(definition.sqlType(), registeredType, attributes)) return;
					validateMysqlMigrationAttributes(storedName, attributes);
				}
                catch (SQLException inspectionFailure) {
                    if (migrationCompletedByPeer(storedName, definition, inspectionFailure)) return;
                    throw inspectionFailure;
                }
                // Database metadata and information_schema are read through separate
                // connections. A peer can finish the ALTER between those reads.
                if (!retainedColumnNeedsMigration(storedName, definition)) return;
                if (attributes.extra() != null && !attributes.extra().isBlank()) {
                    throw new SQLException("Cannot safely migrate SQL column with generated or automatic attributes: "
                            + storedName);
                }
                String sql = "ALTER TABLE " + quote(tableName) + " MODIFY COLUMN " + column + " "
                        + normaliseTypeForDb(declaredPhysicalType(definition.sqlType()))
                        + (attributes.nullable() ? " NULL" : " NOT NULL")
                        + (attributes.defaultValue() == null ? ""
                                : " DEFAULT '" + quoteMysqlLiteral(attributes.defaultValue()) + "'")
                        + (attributes.comment() == null || attributes.comment().isEmpty() ? ""
                                : " COMMENT '" + quoteMysqlLiteral(attributes.comment()) + "'")
                        + ";";
                try (Connection connection = getMysql().getConnectionManager().getConnection();
                        PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.executeUpdate();
                } catch (SQLException ddlFailure) {
                    if (!migrationCompletedByPeer(storedName, definition, ddlFailure)) throw ddlFailure;
                }
                return;
            }
            String defaultExpression = postgresColumnDefault(storedName);
			String targetType = normaliseTypeForDb(declaredPhysicalType(definition.sqlType()));
            StringBuilder sql = new StringBuilder("ALTER TABLE ").append(quote(tableName));
            // PostgreSQL does not apply TYPE ... USING to a column default. Drop and
            // recreate it in the same transactional ALTER TABLE so a legacy numeric
            // DEFAULT does not make an otherwise-safe value conversion fail.
            if (defaultExpression != null) sql.append(" ALTER COLUMN ").append(column).append(" DROP DEFAULT,");
            sql.append(" ALTER COLUMN ").append(column).append(" TYPE ")
                    .append(targetType).append(" USING ").append(column).append("::text")
                    .append(declaredStorageType ? "::" + targetType : "");
            if (defaultExpression != null) sql.append(", ALTER COLUMN ").append(column)
                    .append(" SET DEFAULT (").append(defaultExpression).append(")::text")
                    .append(declaredStorageType ? "::" + targetType : "");
            sql.append(';');
            try (Connection connection = getMysql().getConnectionManager().getConnection();
                    PreparedStatement statement = connection.prepareStatement(sql.toString())) {
                statement.executeUpdate();
            } catch (SQLException ddlFailure) {
                if (!migrationCompletedByPeer(storedName, definition, ddlFailure)) throw ddlFailure;
            }
        }

        private boolean migrationCompletedByPeer(String storedName, SqlUserSchema.ColumnDefinition definition,
                SQLException failure) throws SQLException {
            long deadline = System.nanoTime() + PEER_MIGRATION_GRACE_NANOS;
            do {
                try {
                    if (!retainedColumnNeedsMigration(storedName, definition)) return true;
                } catch (SQLException inspectionFailure) {
                    if (inspectionFailure != failure) failure.addSuppressed(inspectionFailure);
                }
                if (System.nanoTime() >= deadline) return false;
                try { Thread.sleep(PEER_MIGRATION_RECHECK_MILLIS); }
                catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    failure.addSuppressed(new SQLException(
                            "Interrupted while waiting for concurrent SQL column migration: " + storedName,
                            interrupted));
                    return false;
                }
            } while (true);
        }

        private boolean retainedColumnNeedsMigration(String storedName,
                SqlUserSchema.ColumnDefinition definition) throws SQLException {
            RegisteredColumnType registered = registeredColumnType(storedName);
            if (declaredTypeIsNumericOrBoolean(definition.sqlType())) {
				if (getDbType() != DbType.POSTGRESQL) {
					return !mysqlDeclaredTypeMatches(definition.sqlType(), registered,
							mysqlColumnAttributes(storedName));
				}
                return !declaredTypeMatches(definition.sqlType(), registered);
            }
            return isNumericOrBoolean(registered.jdbcType());
        }

        private static boolean isNumericOrBoolean(int jdbcType) {
            return jdbcType == java.sql.Types.TINYINT || jdbcType == java.sql.Types.SMALLINT
                    || jdbcType == java.sql.Types.INTEGER || jdbcType == java.sql.Types.BIGINT
                    || jdbcType == java.sql.Types.REAL || jdbcType == java.sql.Types.FLOAT
                    || jdbcType == java.sql.Types.DOUBLE || jdbcType == java.sql.Types.NUMERIC
                    || jdbcType == java.sql.Types.DECIMAL || jdbcType == java.sql.Types.BOOLEAN
                    || jdbcType == java.sql.Types.BIT;
        }

        private static boolean declaredTypeIsNumericOrBoolean(String sqlType) {
            String normalized = sqlType == null ? "" : sqlType.stripLeading().toUpperCase(java.util.Locale.ROOT);
            int separator = normalized.indexOf(' ');
            int parameters = normalized.indexOf('(');
            int end = separator < 0 ? normalized.length() : separator;
            if (parameters >= 0 && parameters < end) end = parameters;
            String baseType = normalized.substring(0, end);
            return baseType.equals("TINYINT") || baseType.equals("SMALLINT") || baseType.equals("MEDIUMINT")
                    || baseType.equals("INT") || baseType.equals("INTEGER") || baseType.equals("BIGINT")
                    || baseType.equals("REAL") || baseType.equals("FLOAT") || baseType.equals("DOUBLE")
					|| baseType.equals("NUMERIC") || baseType.equals("DECIMAL") || baseType.equals("DEC")
					|| baseType.equals("BOOLEAN")
                    || baseType.equals("BOOL") || baseType.equals("BIT");
        }

        private static boolean declaredTypeMatches(String sqlType, RegisteredColumnType registered) {
            String declared = normalizedBaseType(sqlType);
            String actual = normalizedBaseType(registered.typeName());
            if (actual.isEmpty()) actual = switch (registered.jdbcType()) {
                case java.sql.Types.TINYINT -> "TINYINT";
                case java.sql.Types.SMALLINT -> "SMALLINT";
                case java.sql.Types.INTEGER -> "INTEGER";
                case java.sql.Types.BIGINT -> "BIGINT";
                case java.sql.Types.REAL -> "REAL";
                case java.sql.Types.FLOAT -> "FLOAT";
                case java.sql.Types.DOUBLE -> "DOUBLE";
                case java.sql.Types.NUMERIC -> "NUMERIC";
                case java.sql.Types.DECIMAL -> "DECIMAL";
                case java.sql.Types.BOOLEAN -> "BOOLEAN";
                case java.sql.Types.BIT -> "BIT";
                default -> "";
            };
            declared = canonicalType(declared);
            actual = canonicalType(actual);
            if (!declared.equals(actual)) return false;
            int[] parameters = declaredTypeParameters(sqlType);
            if (parameters.length > 0 && registered.precision() > 0 && parameters[0] != registered.precision()) return false;
            return parameters.length < 2 || registered.scale() < 0 || parameters[1] == registered.scale();
        }

		private static boolean mysqlDeclaredTypeMatches(String sqlType, RegisteredColumnType registered,
				MysqlColumnAttributes attributes) {
			String declared = declaredPhysicalType(sqlType).toUpperCase(java.util.Locale.ROOT);
			String actual = attributes.columnType() == null ? ""
					: attributes.columnType().toUpperCase(java.util.Locale.ROOT);
			RegisteredColumnType actualType = new RegisteredColumnType(registered.jdbcType(),
					attributes.columnType(), registered.precision(), registered.scale());
			boolean mysqlBooleanAlias = "BOOLEAN".equals(canonicalType(normalizedBaseType(declared)))
					&& "TINYINT".equals(normalizedBaseType(actual))
					&& java.util.Arrays.equals(declaredTypeParameters(actual), new int[] { 1 });
			String declaredBase = canonicalType(normalizedBaseType(declared));
			if (!mysqlBooleanAlias && !declaredTypeMatches(declaredBase, actualType)) return false;
			int[] declaredParameters = comparableDeclaredMysqlTypeParameters(declared);
			if (!mysqlBooleanAlias && declaredParameters.length > 0 && !mysqlIntegerType(declaredBase)) {
				int[] actualParameters = declaredTypeParameters(actual);
				if (actualParameters.length > 0) {
					if (!java.util.Arrays.equals(declaredParameters, actualParameters)) return false;
				} else if (declaredParameters[0] != registered.precision()
						|| declaredParameters.length > 1 && declaredParameters[1] != registered.scale()) return false;
			}
			return declared.matches(".*\\bUNSIGNED\\b.*") == actual.matches(".*\\bUNSIGNED\\b.*")
					&& declared.matches(".*\\bZEROFILL\\b.*") == actual.matches(".*\\bZEROFILL\\b.*");
		}

		private static boolean mysqlIntegerType(String type) {
			return type.equals("TINYINT") || type.equals("SMALLINT") || type.equals("MEDIUMINT")
					|| type.equals("INTEGER") || type.equals("BIGINT");
		}

		private static int[] comparableDeclaredMysqlTypeParameters(String sqlType) {
			int[] parameters = declaredTypeParameters(sqlType);
			if (!"DECIMAL".equals(canonicalType(normalizedBaseType(sqlType)))) return parameters;
			return new int[] { parameters.length > 0 ? parameters[0] : 10,
					parameters.length > 1 ? parameters[1] : 0 };
		}

		private static String declaredPhysicalType(String sqlType) {
			if (sqlType == null) return "";
			return sqlType.strip().replaceFirst(
					"(?i)\\s+(?=DEFAULT\\b|NOT\\s+NULL\\b|NULL\\b|PRIMARY\\s+KEY\\b|UNIQUE\\b|COMMENT\\b|REFERENCES\\b|CHECK\\b).*$",
					"");
		}

        private static String normalizedBaseType(String sqlType) {
            String normalized = sqlType == null ? "" : sqlType.stripLeading().toUpperCase(java.util.Locale.ROOT);
            int space = normalized.indexOf(' ');
            int parenthesis = normalized.indexOf('(');
            int end = space < 0 ? normalized.length() : space;
            if (parenthesis >= 0 && parenthesis < end) end = parenthesis;
            return normalized.substring(0, end);
        }

        private static String canonicalType(String type) {
            return switch (type) {
                case "INT", "INT4" -> "INTEGER";
                case "INT2" -> "SMALLINT";
                case "INT8" -> "BIGINT";
                case "BOOL" -> "BOOLEAN";
                case "DEC", "NUMERIC" -> "DECIMAL";
                case "FLOAT4" -> "REAL";
                case "FLOAT8" -> "DOUBLE";
                default -> type;
            };
        }

        private static int[] declaredTypeParameters(String sqlType) {
            if (sqlType == null) return new int[0];
			String physicalType = declaredPhysicalType(sqlType);
			int open = physicalType.indexOf('(');
			int close = open < 0 ? -1 : physicalType.indexOf(')', open + 1);
            if (open < 0 || close < 0) return new int[0];
			String[] values = physicalType.substring(open + 1, close).split(",");
            try {
                int[] parsed = new int[values.length];
                for (int i = 0; i < values.length; i++) parsed[i] = Integer.parseInt(values[i].trim());
                return parsed;
            } catch (NumberFormatException ignored) { return new int[0]; }
        }

        private MysqlColumnAttributes mysqlColumnAttributes(String name) throws SQLException {
            String sql = "SELECT IS_NULLABLE, COLUMN_DEFAULT, EXTRA, COLUMN_COMMENT, COLUMN_TYPE "
                    + "FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND COLUMN_NAME=?";
            try (Connection connection = getMysql().getConnectionManager().getConnection();
                    PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, tableName);
                statement.setString(2, name);
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) throw new SQLException(
                            "Registered SQL column disappeared during attribute inspection: " + name);
                    String nullable = result.getString(1);
                    String defaultValue = normalizeMysqlDefault(result.getString(2));
                    String comment = result.getString(4);
                    return new MysqlColumnAttributes(nullable,
							defaultValue, result.getString(3), comment, result.getString(5));
                }
            }
        }

		private static String normalizeMysqlDefault(String raw) {
			if (raw == null) return null;
			String value = raw.strip();
			if ("NULL".equalsIgnoreCase(value)) return null;
			if (value.length() >= 2 && value.charAt(0) == '\'' && value.charAt(value.length() - 1) == '\'') {
				return value.substring(1, value.length() - 1).replace("''", "'");
			}
			return value;
		}

		private static void validateMysqlMigrationAttributes(String name, MysqlColumnAttributes attributes)
				throws SQLException {
			if (!"YES".equalsIgnoreCase(attributes.nullableValue())
					&& !"NO".equalsIgnoreCase(attributes.nullableValue())) {
				throw new SQLException("Cannot determine SQL column nullability during migration: " + name);
			}
			if (attributes.defaultValue() != null && !attributes.defaultValue().matches(
					"(?i)(?:true|false|[-+]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:e[-+]?\\d+)?)")) {
				throw new SQLException("Cannot safely preserve SQL column default during migration: " + name);
			}
			if (attributes.comment() != null && attributes.comment().indexOf('\\') >= 0) {
				throw new SQLException("Cannot safely preserve SQL column comment during migration: " + name);
			}
		}

        private static String quoteMysqlLiteral(String value) {
            return value.replace("'", "''");
        }

        private record MysqlColumnAttributes(String nullableValue, String defaultValue, String extra, String comment,
				String columnType) {
			boolean nullable() { return "YES".equalsIgnoreCase(nullableValue); }
		}

        private String postgresColumnDefault(String name) throws SQLException {
            String regclass = '"' + tableName.replace("\"", "\"\"") + '"';
            String sql = "SELECT pg_catalog.pg_get_expr(default_value.adbin, default_value.adrelid) "
                    + "FROM pg_catalog.pg_attribute attribute "
                    + "LEFT JOIN pg_catalog.pg_attrdef default_value ON default_value.adrelid=attribute.attrelid "
                    + "AND default_value.adnum=attribute.attnum "
                    + "WHERE attribute.attrelid=pg_catalog.to_regclass(?) AND attribute.attname=? "
                    + "AND attribute.attnum>0 AND NOT attribute.attisdropped";
            try (Connection connection = getMysql().getConnectionManager().getConnection();
                    PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, regclass);
                statement.setString(2, name);
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? result.getString(1) : null;
                }
            }
        }

        private RegisteredColumnType registeredColumnType(String name) throws SQLException {
            try (Connection connection = getMysql().getConnectionManager().getConnection();
                    PreparedStatement statement = connection.prepareStatement(
                            "SELECT * FROM " + quote(tableName) + " WHERE 1=0");
                    ResultSet result = statement.executeQuery()) {
                ResultSetMetaData metadata = result.getMetaData();
                for (int i = 1; i <= metadata.getColumnCount(); i++) {
                    if (name.equals(metadata.getColumnName(i))) return new RegisteredColumnType(metadata.getColumnType(i),
                            metadata.getColumnTypeName(i), metadata.getPrecision(i), metadata.getScale(i));
                }
                throw new SQLException("Registered SQL column disappeared during type inspection: " + name);
            }
        }

        private record RegisteredColumnType(int jdbcType, String typeName, int precision, int scale) { }

        private void rememberColumn(SqlUserSchema.ColumnDefinition column) {
            columns.removeIf(existing -> existing.equalsIgnoreCase(column.name())); columns.add(column.name());
            intColumns.removeIf(existing -> existing.equalsIgnoreCase(column.name())); if (column.dataType() == DataType.INTEGER) intColumns.add(column.name());
        }

        private String findRegisteredColumn(String name) throws SQLException {
            try (Connection connection = getMysql().getConnectionManager().getConnection(); PreparedStatement statement = connection.prepareStatement("SELECT * FROM " + quote(tableName) + " WHERE 1=0"); ResultSet result = statement.executeQuery()) {
                ResultSetMetaData metadata = result.getMetaData(); String exactMatch = null; String foldedMatch = null; int foldedMatches = 0;
                for (int i = 1; i <= metadata.getColumnCount(); i++) {
                    String storedName = metadata.getColumnName(i);
                    if (name.equals(storedName)) exactMatch = storedName;
                    if (name.equalsIgnoreCase(storedName)) { foldedMatches++; if (foldedMatch == null) foldedMatch = storedName; }
                }
                if (getDbType() == DbType.POSTGRESQL && foldedMatches > 1)
                    throw new SQLException("Ambiguous case-folded SQL columns for registered name: " + name);
                return exactMatch == null ? foldedMatch : exactMatch;
            }
        }

        private boolean isDuplicateColumn(SQLException failure) { return getDbType() == DbType.POSTGRESQL ? "42701".equals(failure.getSQLState()) : failure.getErrorCode() == 1060 && "42S21".equals(failure.getSQLState()); }
        String quote(String identifier) { return qi(identifier); }
    }
}
