package com.bencodez.advancedcore.bukkit.user.storage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.sql.DriverManager;
import java.sql.SQLException;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL;
import com.bencodez.advancedcore.api.user.userstorage.sql.UserTable;
import com.bencodez.advancedcore.core.user.storage.SqlUserStorage;
import com.bencodez.advancedcore.core.user.storage.sql.MysqlUserBackend;
import com.bencodez.advancedcore.core.user.storage.sql.SqlBackendLogger;
import com.bencodez.advancedcore.core.user.storage.sql.SqlUserBackend;
import com.bencodez.advancedcore.core.user.storage.sql.SqlUserBackendFactory;
import com.bencodez.advancedcore.core.user.storage.sql.SqlUserSchema;
import com.bencodez.simpleapi.sql.Column;
import com.bencodez.simpleapi.sql.data.DataValue;
import com.bencodez.simpleapi.sql.data.DataValueString;

/**
 * Non-owning bridge to the Bukkit storage objects already initialized by the
 * plugin. Closing this bridge only retires the shared route; the plugin keeps
 * ownership of MySQL/UserTable resource shutdown.
 */
public final class BukkitSqlUserBackend implements SqlUserBackend {
    private final AdvancedCorePlugin plugin;
    private final UserStorage storageType;
	private final MySQL mysql;
	private final UserTable table;
    private final Object sqliteOperations = new Object();
    private final Object mysqlSchemaOperations = new Object();
    private final AtomicBoolean open = new AtomicBoolean(true);
    private volatile String reconciledMysqlSchemaSignature = "";

    public BukkitSqlUserBackend(AdvancedCorePlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.storageType = Objects.requireNonNull(plugin.getStorageType(), "storageType");
		if (storageType() == UserStorage.MYSQL) {
			this.mysql = plugin.getMysql();
			this.table = null;
			if (mysql == null) throw new IllegalStateException("Bukkit MySQL user storage is unavailable");
		} else {
			this.mysql = null;
			this.table = plugin.getSQLiteUserTable();
			if (table == null) throw new IllegalStateException("Bukkit SQLite user storage is unavailable");
		}
    }

	/**
	 * Bind a replacement runtime to native storage prepared off-thread. The
	 * backend deliberately retains these exact owners instead of resolving the
	 * plugin's mutable fields on each operation, so a configuration reload cannot
	 * redirect an old flush to a newly installed provider.
	 */
	public BukkitSqlUserBackend(AdvancedCorePlugin plugin, UserStorage storageType, MySQL mysql, UserTable table) {
		this.plugin = Objects.requireNonNull(plugin, "plugin");
		this.storageType = Objects.requireNonNull(storageType, "storageType");
		this.mysql = storageType == UserStorage.MYSQL ? Objects.requireNonNull(mysql, "mysql") : null;
		this.table = storageType == UserStorage.SQLITE ? Objects.requireNonNull(table, "table") : null;
	}

    @Override public UserStorage storageType() { return storageType; }

    @Override
    public SqlUserStorage user(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");
        requireOpen();
        return new SqlUserStorage() {
            @Override public List<Column> readRow(UserStorage storage) { return read(storage, uuid); }
            @Override public boolean contains(UserStorage storage) { return BukkitSqlUserBackend.this.contains(storage, uuid); }
            @Override public void delete(UserStorage storage) { BukkitSqlUserBackend.this.delete(storage, uuid); }
            @Override public void write(UserStorage storage, String key, DataValue value) {
                BukkitSqlUserBackend.this.write(storage, uuid, key, value);
            }
            @Override public void writeValues(UserStorage storage, HashMap<String, DataValue> values) {
                BukkitSqlUserBackend.this.writeValues(storage, uuid, values);
            }
            @Override public <T> T transaction(UserStorage storage, TransactionWork<T> work) {
                return BukkitSqlUserBackend.this.transaction(storage, uuid, java.util.Map.of(), work);
            }
            @Override public <T> T transaction(UserStorage storage, java.util.Map<String, DataValue> initialValues, TransactionWork<T> work) {
                return BukkitSqlUserBackend.this.transaction(storage, uuid, initialValues, work);
            }
        };
    }

    @Override
    public List<UUID> enumerateUsers() {
        ArrayList<UUID> users = new ArrayList<>();
        forEachUser(uuid -> {
            if (users.size() >= MAX_MATERIALIZED_USERS) {
                throw new IllegalStateException("User enumeration exceeds " + MAX_MATERIALIZED_USERS + " entries; use forEachUser");
            }
            users.add(uuid);
        });
        return users;
    }

    @Override
    public void forEachUser(Consumer<UUID> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        requireOpen();
		UserStorage storage = storageType();
		if (storage == UserStorage.MYSQL) {
			mysql().forEachUser((uuid, ignored) -> consumer.accept(uuid), ignored -> {},
					failure -> { throw enumerationFailure("MySQL", failure); });
			return;
		}
		synchronized (sqliteOperations) {
			table().forEachUser((uuid, ignored) -> consumer.accept(uuid), ignored -> {},
					failure -> { throw enumerationFailure("SQLite", failure); });
		}
	}

	private IllegalStateException enumerationFailure(String storage, Throwable failure) {
		return new IllegalStateException("Failed to enumerate " + storage + " users", failure);
	}

    @Override public boolean isOpen() { return open.get(); }

    /** The Bukkit plugin owns and closes the underlying connections. */
    @Override public void close() { open.set(false); }

    private List<Column> read(UserStorage storage, UUID uuid) {
        requireOpen();
        requireStorage(storage);
        if (storage == UserStorage.MYSQL) return mysql().getExact(uuid.toString());
        synchronized (sqliteOperations) { return table().getExact(primary(uuid)); }
    }

    private boolean contains(UserStorage storage, UUID uuid) {
        requireOpen();
        requireStorage(storage);
        if (storage == UserStorage.MYSQL) return mysql().containsKey(uuid.toString());
        synchronized (sqliteOperations) { return table().containsKey(uuid.toString()); }
    }

    private void delete(UserStorage storage, UUID uuid) {
        requireOpen();
        requireStorage(storage);
        if (storage == UserStorage.MYSQL) mysql().deletePlayer(uuid.toString());
        else synchronized (sqliteOperations) { table().delete(primary(uuid)); }
    }

    private void write(UserStorage storage, UUID uuid, String key, DataValue value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        requireOpen();
        requireStorage(storage);
        if (storage == UserStorage.MYSQL) {
            reconcileMysqlSchema(registeredSchema(), sqlLogger());
            mysql().update(uuid.toString(), key, value);
        } else synchronized (sqliteOperations) { table().update(primary(uuid), new ArrayList<>(List.of(new Column(key, value)))); }
    }

    private void writeValues(UserStorage storage, UUID uuid, HashMap<String, DataValue> values) {
        Objects.requireNonNull(values, "values");
        requireOpen();
        requireStorage(storage);
        java.util.Map<String, DataValue> updates = new HashMap<>();
        values.forEach((key, value) -> {
            if (!"uuid".equalsIgnoreCase(key) && value != null) updates.put(key, value);
        });
        if (updates.isEmpty()) return;
        // Shared cache flushes must surface SQL failures. The legacy update
        // methods log and swallow them, which could acknowledge a later caller
        // transaction while its prerequisite queued values were never stored.
        withSqlUser(storage, uuid, updates, user -> {
            user.writeValues(storage, new HashMap<>(updates));
            return null;
        });
        if (storage == UserStorage.MYSQL) mysql().recordCommittedUser(uuid, containsPlayerName(updates));
    }

    private <T> T transaction(UserStorage storage, UUID uuid, java.util.Map<String, DataValue> initialValues, SqlUserStorage.TransactionWork<T> work) {
        requireOpen();
        requireStorage(storage);
        Objects.requireNonNull(work, "work");
        return withSqlUser(storage, uuid, initialValues, user -> {
            if (storage != UserStorage.MYSQL) return user.transaction(storage, initialValues, work);
            boolean[] nameTouched = {false};
            T result = user.transaction(storage, initialValues, scope -> {
                nameTouched[0] = scope.createdUserRow() && containsPlayerName(initialValues);
                return work.run(new SqlUserStorage.TransactionScope() {
                    @Override public boolean createdUserRow() { return scope.createdUserRow(); }
                    @Override public java.sql.Connection connection() { return scope.connection(); }
                    @Override public List<Column> readRow() throws SQLException { return scope.readRow(); }
                    @Override public void writeValues(java.util.Map<String, DataValue> values) throws SQLException {
                        scope.writeValues(values);
                        if (containsPlayerName(values)) nameTouched[0] = true;
                    }
                });
            });
            mysql().recordCommittedUser(uuid, nameTouched[0]);
            return result;
        });
    }

    private static boolean containsPlayerName(java.util.Map<String, DataValue> values) {
        return values.keySet().stream().anyMatch("PlayerName"::equalsIgnoreCase);
    }

    private <T> T withSqlUser(UserStorage storage, UUID uuid, java.util.Map<String, DataValue> updates,
            java.util.function.Function<SqlUserStorage, T> work) {
        SqlUserSchema registered = registeredSchema();
        SqlUserSchema.Builder builder = SqlUserSchema.builder();
        for (SqlUserSchema.ColumnDefinition column : registered.columns()) {
            if (!"uuid".equalsIgnoreCase(column.name())) builder.column(column.name(), column.sqlType(), column.dataType());
        }
        ArrayList<Column> dynamic = new ArrayList<>();
        for (java.util.Map.Entry<String, DataValue> entry : updates.entrySet()) {
            if (registered.contains(entry.getKey())) continue;
            com.bencodez.simpleapi.sql.DataType type = entry.getValue().getType();
            builder.column(entry.getKey(), type == com.bencodez.simpleapi.sql.DataType.STRING ? "TEXT" :
                    type == com.bencodez.simpleapi.sql.DataType.INTEGER ? "INTEGER" : "BOOLEAN", type);
            dynamic.add(new Column(entry.getKey(), type));
        }
        SqlUserSchema schema = builder.build();
        SqlBackendLogger logger = sqlLogger();
        if (storage == UserStorage.MYSQL) {
            reconcileMysqlSchema(registered, logger);
            for (Column column : dynamic) mysql().checkColumn(column.getName(), column.getDataType());
            var manager = mysql().getMysql().getConnectionManager();
            return work.apply(SqlUserBackendFactory.existingUser(storage, uuid, mysql().getTableName(), schema,
                    manager::getConnection, manager.getDbType(), logger));
        }
        synchronized (sqliteOperations) {
            for (Column column : dynamic) table().checkColumn(column);
            // The legacy SQLite provider retains one shared connection. Obtain
            // its actual database URL, then open a separate transaction-owned
            // connection so AdvancedCore can close it after commit/rollback.
            String url;
            try { url = table().getSqLite().getSQLConnection().getMetaData().getURL(); }
            catch (SQLException failure) { throw new IllegalStateException("Failed to locate Bukkit SQLite user database", failure); }
            return work.apply(SqlUserBackendFactory.existingUser(storage, uuid, table().getName(), schema,
                    () -> DriverManager.getConnection(url), null, logger));
        }
    }

    private SqlUserSchema registeredSchema() {
        return SqlUserSchema.fromKeys(plugin.getUserManager().getDataManager().getKeys());
    }

    private SqlBackendLogger sqlLogger() {
        return new SqlBackendLogger() {
            @Override public void info(String message) { plugin.getLogger().info(message); }
            @Override public void warn(String message, Throwable error) {
                plugin.getLogger().warning(message + (error == null ? "" : ": " + error.getMessage()));
            }
        };
    }

    private void reconcileMysqlSchema(SqlUserSchema schema, SqlBackendLogger logger) {
        String signature = schemaSignature(schema);
        if (signature.equals(reconciledMysqlSchemaSignature)) return;
        synchronized (mysqlSchemaOperations) {
            if (signature.equals(reconciledMysqlSchemaSignature)) return;
            MysqlUserBackend.reconcileExistingTable(mysql().getTableName(), mysql().getMysql(), schema, logger);
            reconciledMysqlSchemaSignature = signature;
        }
    }

    private static String schemaSignature(SqlUserSchema schema) {
        StringBuilder signature = new StringBuilder();
        for (SqlUserSchema.ColumnDefinition column : schema.columns()) {
            signature.append(column.name().toLowerCase(java.util.Locale.ROOT)).append('\u0000')
                    .append(column.sqlType()).append('\u0000').append(column.dataType().name()).append('\u0001');
        }
        return signature.toString();
    }

    private Column primary(UUID uuid) { return new Column("uuid", new DataValueString(uuid.toString())); }
    private MySQL mysql() { return requireMysql(); }
    private UserTable table() { return requireTable(); }

    private MySQL requireMysql() {
		if (mysql == null) throw new IllegalStateException("Bukkit MySQL user storage is unavailable");
		return mysql;
    }

    private UserTable requireTable() {
		if (table == null) throw new IllegalStateException("Bukkit SQLite user storage is unavailable");
		return table;
    }

    private void requireOpen() { if (!open.get()) throw new IllegalStateException("Bukkit SQL user backend is retired"); }
    private void requireStorage(UserStorage storage) {
        if (storage != storageType()) throw new IllegalArgumentException(
                "Storage mismatch: backend=" + storageType() + ", requested=" + storage);
    }
}
