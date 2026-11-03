package com.bencodez.advancedcore.bukkit.user.storage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.advancedcore.api.user.usercache.UserDataManager;
import com.bencodez.advancedcore.core.user.storage.SqlUserStorage;
import com.bencodez.simpleapi.sql.Column;
import com.bencodez.simpleapi.sql.data.DataValue;
import com.bencodez.simpleapi.sql.data.DataValueString;

/** Adapts the existing SQL providers and the shared-runtime replacement route. */
public final class BukkitSqlUserStorage implements SqlUserStorage {
    private record StrictOwnerScope(AdvancedCorePlugin plugin, AdvancedCorePlugin.UserStorageOwner owner) { }
    private static final ThreadLocal<StrictOwnerScope> STRICT_OWNER = new ThreadLocal<>();

    /** Bind a strict legacy read to the already captured native provider for this call. */
    public static <T> T withStrictOwner(AdvancedCorePlugin plugin, AdvancedCorePlugin.UserStorageOwner owner, Supplier<T> operation) {
        Objects.requireNonNull(operation, "operation");
        StrictOwnerScope previous = STRICT_OWNER.get();
        STRICT_OWNER.set(new StrictOwnerScope(plugin, owner));
        try { return operation.get(); }
        finally { if (previous == null) STRICT_OWNER.remove(); else STRICT_OWNER.set(previous); }
    }
    private final Supplier<AdvancedCorePlugin> plugin;
    private final Supplier<String> uuid;

    public BukkitSqlUserStorage(Supplier<AdvancedCorePlugin> plugin, Supplier<String> uuid) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.uuid = Objects.requireNonNull(uuid, "uuid");
    }

    private AdvancedCorePlugin owner() { return Objects.requireNonNull(plugin.get(), "plugin is not initialized"); }
    private Column primary() { return new Column("uuid", new DataValueString(uuid.get())); }
    private UUID userId() { return UUID.fromString(uuid.get()); }
    private UserDataManager dataManager() {
        // Legacy SQL can be used before user/cache runtime construction. Do not
        // make the optional shared route a prerequisite for the existing path.
        var users = owner().getUserManager();
        UserDataManager manager = users == null ? null : users.getDataManager();
        return manager;
    }

	private <T> T routed(UserStorage requestedStorage, BiFunction<UserStorage, SqlUserStorage, T> sharedOperation,
			Supplier<T> legacyOperation) {
		UserDataManager manager = dataManager();
		// Cross-store access is valid only for the converter's runtime-exclusive
		// maintenance action. Outside that narrow scope, route every operation
		// through the shared backend so a request cannot silently hit another store.
		return manager == null || manager.isStorageMaintenanceActive() ? legacyOperation.get()
				: manager.withSharedSqlBackendOrLegacy(this::userId, (sharedStorage, target) -> {
					if (sharedStorage != requestedStorage) {
						throw new IllegalStateException("Cannot access " + requestedStorage
								+ " user storage while the shared runtime owns " + sharedStorage);
					}
					return sharedOperation.apply(requestedStorage, target);
				}, legacyOperation);
	}

    @Override
    public List<Column> readRow(UserStorage storage) {
        Objects.requireNonNull(storage, "storage");
		return routed(storage, (type, target) -> target.readRow(type), () -> storage == UserStorage.MYSQL
                ? owner().getMysql().getExact(uuid.get()) : owner().getSQLiteUserTable().getExact(primary()));
    }

    /** Read a complete row without treating a failed legacy query as defaults. */
    public List<Column> readRowStrict(UserStorage storage) {
        Objects.requireNonNull(storage, "storage");
        return routed(storage, (type, target) -> target.readRow(type), () -> {
            try {
                StrictOwnerScope scope = STRICT_OWNER.get();
                AdvancedCorePlugin pluginOwner = owner();
                AdvancedCorePlugin.UserStorageOwner captured = scope != null && scope.plugin() == pluginOwner ? scope.owner() : null;
                if (captured != null && captured.storageType() != storage) throw new IllegalStateException("Strict legacy storage changed while reading");
                if (storage == UserStorage.MYSQL) {
                    com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL table = captured == null ? pluginOwner.getMysql() : captured.mysql();
                    if (table.getClass().getMethod("getExact", String.class).getDeclaringClass()
                            != com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL.class
                            || table.getClass().getMethod("getExactQuery", Column.class).getDeclaringClass()
                            != com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL.class
                            || table.getClass().getMethod("getExactStrict", String.class).getDeclaringClass()
                            != com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL.class) return null;
                    return table.getExactStrict(uuid.get());
                }
                com.bencodez.advancedcore.api.user.userstorage.sql.UserTable table = captured == null ? pluginOwner.getSQLiteUserTable() : captured.table();
                if (table.getClass().getMethod("getExact", Column.class).getDeclaringClass()
                        != com.bencodez.advancedcore.api.user.userstorage.sql.UserTable.class
                        || table.getClass().getMethod("getExactStrict", Column.class).getDeclaringClass()
                        != com.bencodez.advancedcore.api.user.userstorage.sql.UserTable.class) return null;
                return table.getExactStrict(primary());
            } catch (NoSuchMethodException unavailable) { return null; }
        });
    }

    /** Inspect legacy reader capability without issuing a query. Missing providers are failures. */
    public boolean supportsStrictLegacyRowReads(UserStorage storage) {
        Objects.requireNonNull(storage, "storage");
        StrictOwnerScope scope = STRICT_OWNER.get();
        AdvancedCorePlugin pluginOwner = owner();
        AdvancedCorePlugin.UserStorageOwner captured = scope != null && scope.plugin() == pluginOwner
                ? scope.owner() : pluginOwner.getNativeUserStorageOwner();
        if (captured == null) throw new IllegalStateException("Native user storage owner is unavailable");
        if (captured.storageType() != storage)
            throw new IllegalStateException("Strict legacy storage changed while inspecting capability");
        try {
            if (storage == UserStorage.MYSQL) {
                com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL table = Objects.requireNonNull(captured.mysql(), "MySQL user storage is unavailable");
                Class<?> type = table.getClass();
                return type.getMethod("getExact", String.class).getDeclaringClass() == com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL.class
                        && type.getMethod("getExactQuery", Column.class).getDeclaringClass() == com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL.class
                        && type.getMethod("getExactStrict", String.class).getDeclaringClass() == com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL.class;
            }
            com.bencodez.advancedcore.api.user.userstorage.sql.UserTable table = Objects.requireNonNull(captured.table(), "SQLite user storage is unavailable");
            Class<?> type = table.getClass();
            return type.getMethod("getExact", Column.class).getDeclaringClass() == com.bencodez.advancedcore.api.user.userstorage.sql.UserTable.class
                    && type.getMethod("getExactStrict", Column.class).getDeclaringClass() == com.bencodez.advancedcore.api.user.userstorage.sql.UserTable.class;
        } catch (NoSuchMethodException | SecurityException unavailable) { return false; }
    }

    @Override
    public boolean contains(UserStorage storage) {
        Objects.requireNonNull(storage, "storage");
		return routed(storage, (type, target) -> target.contains(type), () -> storage == UserStorage.MYSQL
                ? owner().getMysql().containsKey(uuid.get()) : owner().getSQLiteUserTable().containsKey(uuid.get()));
    }

    @Override
    public void delete(UserStorage storage) {
        Objects.requireNonNull(storage, "storage");
		routed(storage, (type, target) -> { target.delete(type); return null; }, () -> {
            if (storage == UserStorage.MYSQL) owner().getMysql().deletePlayer(uuid.get());
            else owner().getSQLiteUserTable().delete(primary());
            return null;
        });
    }

    @Override
    public void write(UserStorage storage, String key, DataValue value) {
        Objects.requireNonNull(storage, "storage");
		routed(storage, (type, target) -> { target.write(type, key, value); return null; }, () -> {
            if (storage == UserStorage.SQLITE) {
                ArrayList<Column> columns = new ArrayList<>();
                Column primary = primary();
				com.bencodez.advancedcore.api.user.usercache.UserDataCache.bindLegacyDirectRow(primary.getValue().getString(), true);
                columns.add(primary);
                columns.add(new Column(key, value));
                com.bencodez.advancedcore.api.user.userstorage.sql.UserTable captured = com.bencodez.advancedcore.api.user.usercache.UserDataCache.capturedNestedSqlite();
                (captured == null ? owner().getSQLiteUserTable() : captured).update(primary, columns);
            } else {
				String row = uuid.get();
				com.bencodez.advancedcore.api.user.usercache.UserDataCache.bindLegacyDirectRow(row, false);
				com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL captured = com.bencodez.advancedcore.api.user.usercache.UserDataCache.capturedNestedMysql();
				(captured == null ? owner().getMysql() : captured).update(row, key, value);
			}
            return null;
        });
    }

    @Override
    public void writeValues(UserStorage storage, HashMap<String, DataValue> values) {
        Objects.requireNonNull(storage, "storage");
		routed(storage, (type, target) -> { target.writeValues(type, values); return null; }, () -> {
            if (storage == UserStorage.MYSQL) {
                if (owner().getMysql() != null) {
                    ArrayList<Column> columns = new ArrayList<>();
                    for (Entry<String, DataValue> entry : values.entrySet()) {
                        if (!entry.getKey().equals("uuid")) columns.add(new Column(entry.getKey(), entry.getValue()));
                    }
					String row = uuid.get();
					com.bencodez.advancedcore.api.user.usercache.UserDataCache.bindLegacyDirectRow(row, false);
                    owner().getMysql().update(row, columns, false);
                }
            } else {
                ArrayList<Column> columns = new ArrayList<>();
                for (Entry<String, DataValue> entry : values.entrySet()) {
                    if (!entry.getKey().equals("uuid")) columns.add(new Column(entry.getKey(), entry.getValue()));
                }
				if (!columns.isEmpty()) {
					Column primary = primary();
					com.bencodez.advancedcore.api.user.usercache.UserDataCache.bindLegacyDirectRow(primary.getValue().getString(), true);
					owner().getSQLiteUserTable().update(primary, columns);
				}
            }
            return null;
        });
    }
}
