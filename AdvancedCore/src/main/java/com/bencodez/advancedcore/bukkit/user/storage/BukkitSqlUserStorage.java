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
                columns.add(primary);
                columns.add(new Column(key, value));
                owner().getSQLiteUserTable().update(primary, columns);
            } else owner().getMysql().update(uuid.get(), key, value);
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
                    owner().getMysql().update(uuid.get(), columns, false);
                }
            } else {
                ArrayList<Column> columns = new ArrayList<>();
                for (Entry<String, DataValue> entry : values.entrySet()) {
                    if (!entry.getKey().equals("uuid")) columns.add(new Column(entry.getKey(), entry.getValue()));
                }
                if (!columns.isEmpty()) owner().getSQLiteUserTable().update(primary(), columns);
            }
            return null;
        });
    }
}
