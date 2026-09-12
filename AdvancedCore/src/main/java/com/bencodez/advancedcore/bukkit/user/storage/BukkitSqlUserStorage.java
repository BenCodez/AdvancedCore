package com.bencodez.advancedcore.bukkit.user.storage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.function.Supplier;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.advancedcore.core.user.storage.SqlUserStorage;
import com.bencodez.simpleapi.sql.Column;
import com.bencodez.simpleapi.sql.data.DataValue;
import com.bencodez.simpleapi.sql.data.DataValueString;

/**
 * Adapts the existing SQL providers. Plugin/table/UUID lookup stays live so a
 * queued operation resolves the same current ownership as the legacy facade.
 * Construction creates no user, cache, connection, executor, or schema.
 */
public final class BukkitSqlUserStorage implements SqlUserStorage {
    private final Supplier<AdvancedCorePlugin> plugin;
    private final Supplier<String> uuid;

    public BukkitSqlUserStorage(Supplier<AdvancedCorePlugin> plugin, Supplier<String> uuid) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.uuid = Objects.requireNonNull(uuid, "uuid");
    }

    private AdvancedCorePlugin owner() {
        return Objects.requireNonNull(plugin.get(), "plugin is not initialized");
    }

    private Column primary() {
        return new Column("uuid", new DataValueString(uuid.get()));
    }

    @Override
    public List<Column> readRow(UserStorage storage) {
        if (Objects.requireNonNull(storage, "storage") == UserStorage.MYSQL) {
            return owner().getMysql().getExact(uuid.get());
        }
        return owner().getSQLiteUserTable().getExact(primary());
    }

    @Override
    public boolean contains(UserStorage storage) {
        if (Objects.requireNonNull(storage, "storage") == UserStorage.MYSQL) {
            return owner().getMysql().containsKey(uuid.get());
        }
        return owner().getSQLiteUserTable().containsKey(uuid.get());
    }

    @Override
    public void delete(UserStorage storage) {
        if (Objects.requireNonNull(storage, "storage") == UserStorage.MYSQL) {
            owner().getMysql().deletePlayer(uuid.get());
        } else {
            owner().getSQLiteUserTable().delete(primary());
        }
    }

    @Override
    public void write(UserStorage storage, String key, DataValue value) {
        if (Objects.requireNonNull(storage, "storage") == UserStorage.SQLITE) {
            ArrayList<Column> columns = new ArrayList<>();
            Column primary = primary();
            columns.add(primary);
            columns.add(new Column(key, value));
            owner().getSQLiteUserTable().update(primary, columns);
        } else {
            owner().getMysql().update(uuid.get(), key, value);
        }
    }

    @Override
    public void writeValues(UserStorage storage, HashMap<String, DataValue> values) {
        if (Objects.requireNonNull(storage, "storage") == UserStorage.MYSQL) {
            // Preserve the existing bulk-write no-op while MySQL is unavailable.
            if (owner().getMysql() != null) {
                ArrayList<Column> columns = new ArrayList<>();
                for (Entry<String, DataValue> entry : values.entrySet()) {
                    if (!entry.getKey().equals("uuid")) {
                        columns.add(new Column(entry.getKey(), entry.getValue()));
                    }
                }
                owner().getMysql().update(uuid.get(), columns, false);
            }
        } else {
            ArrayList<Column> columns = new ArrayList<>();
            for (Entry<String, DataValue> entry : values.entrySet()) {
                if (!entry.getKey().equals("uuid")) {
                    columns.add(new Column(entry.getKey(), entry.getValue()));
                }
                // Deliberately preserve legacy cumulative per-entry updates.
                // Moving this call outside the loop would change write/failure ordering.
                owner().getSQLiteUserTable().update(primary(), columns);
            }
        }
    }
}
