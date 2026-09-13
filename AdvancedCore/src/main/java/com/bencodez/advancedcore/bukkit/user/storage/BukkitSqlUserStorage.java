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
    private UserDataManager sharedManager() {
        // Legacy SQL can be used before user/cache runtime construction. Do not
        // make the optional shared route a prerequisite for the existing path.
        var users = owner().getUserManager();
        UserDataManager manager = users == null ? null : users.getDataManager();
        return manager != null && manager.hasSharedSqlBackend() ? manager : null;
    }

    private <T> T shared(UserDataManager manager, BiFunction<UserStorage, SqlUserStorage, T> operation) {
        return manager.withSharedSqlBackend(userId(), operation);
    }

    @Override
    public List<Column> readRow(UserStorage storage) {
        Objects.requireNonNull(storage, "storage");
        UserDataManager manager = sharedManager();
        if (manager != null) return shared(manager, (type, target) -> target.readRow(type));
        if (Objects.requireNonNull(storage, "storage") == UserStorage.MYSQL) return owner().getMysql().getExact(uuid.get());
        return owner().getSQLiteUserTable().getExact(primary());
    }

    @Override
    public boolean contains(UserStorage storage) {
        Objects.requireNonNull(storage, "storage");
        UserDataManager manager = sharedManager();
        if (manager != null) return shared(manager, (type, target) -> target.contains(type));
        if (Objects.requireNonNull(storage, "storage") == UserStorage.MYSQL) return owner().getMysql().containsKey(uuid.get());
        return owner().getSQLiteUserTable().containsKey(uuid.get());
    }

    @Override
    public void delete(UserStorage storage) {
        Objects.requireNonNull(storage, "storage");
        UserDataManager manager = sharedManager();
        if (manager != null) { shared(manager, (type, target) -> { target.delete(type); return null; }); return; }
        if (Objects.requireNonNull(storage, "storage") == UserStorage.MYSQL) owner().getMysql().deletePlayer(uuid.get());
        else owner().getSQLiteUserTable().delete(primary());
    }

    @Override
    public void write(UserStorage storage, String key, DataValue value) {
        Objects.requireNonNull(storage, "storage");
        UserDataManager manager = sharedManager();
        if (manager != null) { shared(manager, (type, target) -> { target.write(type, key, value); return null; }); return; }
        if (Objects.requireNonNull(storage, "storage") == UserStorage.SQLITE) {
            ArrayList<Column> columns = new ArrayList<>();
            Column primary = primary();
            columns.add(primary);
            columns.add(new Column(key, value));
            owner().getSQLiteUserTable().update(primary, columns);
        } else owner().getMysql().update(uuid.get(), key, value);
    }

    @Override
    public void writeValues(UserStorage storage, HashMap<String, DataValue> values) {
        Objects.requireNonNull(storage, "storage");
        UserDataManager manager = sharedManager();
        if (manager != null) { shared(manager, (type, target) -> { target.writeValues(type, values); return null; }); return; }
        if (Objects.requireNonNull(storage, "storage") == UserStorage.MYSQL) {
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
                owner().getSQLiteUserTable().update(primary(), columns);
            }
        }
    }
}
