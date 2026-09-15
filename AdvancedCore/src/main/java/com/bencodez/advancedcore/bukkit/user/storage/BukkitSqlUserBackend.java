package com.bencodez.advancedcore.bukkit.user.storage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL;
import com.bencodez.advancedcore.api.user.userstorage.sql.UserTable;
import com.bencodez.advancedcore.core.user.storage.SqlUserStorage;
import com.bencodez.advancedcore.core.user.storage.sql.SqlUserBackend;
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
    private final Object sqliteOperations = new Object();
    private final AtomicBoolean open = new AtomicBoolean(true);

    public BukkitSqlUserBackend(AdvancedCorePlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.storageType = Objects.requireNonNull(plugin.getStorageType(), "storageType");
        if (storageType() == UserStorage.MYSQL) requireMysql();
        else requireTable();
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
        if (storage == UserStorage.MYSQL) mysql().update(uuid.toString(), key, value);
        else synchronized (sqliteOperations) { table().update(primary(uuid), new ArrayList<>(List.of(new Column(key, value)))); }
    }

    private void writeValues(UserStorage storage, UUID uuid, HashMap<String, DataValue> values) {
        Objects.requireNonNull(values, "values");
        requireOpen();
        requireStorage(storage);
        ArrayList<Column> columns = new ArrayList<>();
        values.forEach((key, value) -> {
            if (!"uuid".equalsIgnoreCase(key) && value != null) columns.add(new Column(key, value));
        });
        if (columns.isEmpty()) return;
        if (storage == UserStorage.MYSQL) mysql().update(uuid.toString(), columns, false);
        else synchronized (sqliteOperations) { table().update(primary(uuid), columns); }
    }

    private Column primary(UUID uuid) { return new Column("uuid", new DataValueString(uuid.toString())); }
    private MySQL mysql() { return requireMysql(); }
    private UserTable table() { return requireTable(); }

    private MySQL requireMysql() {
        MySQL mysql = plugin.getMysql();
        if (mysql == null) throw new IllegalStateException("Bukkit MySQL user storage is unavailable");
        return mysql;
    }

    private UserTable requireTable() {
        UserTable table = plugin.getSQLiteUserTable();
        if (table == null) throw new IllegalStateException("Bukkit SQLite user storage is unavailable");
        return table;
    }

    private void requireOpen() { if (!open.get()) throw new IllegalStateException("Bukkit SQL user backend is retired"); }
    private void requireStorage(UserStorage storage) {
        if (storage != storageType()) throw new IllegalArgumentException(
                "Storage mismatch: backend=" + storageType() + ", requested=" + storage);
    }
}
