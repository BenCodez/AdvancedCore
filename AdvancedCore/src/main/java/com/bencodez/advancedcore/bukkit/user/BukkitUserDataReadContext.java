package com.bencodez.advancedcore.bukkit.user;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.advancedcore.api.user.UserData;
import com.bencodez.advancedcore.api.user.usercache.UserDataCache;
import com.bencodez.advancedcore.core.user.UserDataReadContext;
import com.bencodez.simpleapi.sql.Column;
import com.bencodez.simpleapi.sql.data.DataValue;

/** Bridges the existing facade, cache objects and row providers without copying their state. */
public final class BukkitUserDataReadContext implements UserDataReadContext {
    private final UserData facade;
    private final AdvancedCoreUser user;
    private final Supplier<? extends Map<String, DataValue>> temporary;

    public BukkitUserDataReadContext(UserData facade, AdvancedCoreUser user,
            Supplier<? extends Map<String, DataValue>> temporary) {
        this.facade = Objects.requireNonNull(facade, "facade");
        // UserData historically permits construction with a null user when only
        // temporary-data operations are needed. Do not eagerly dereference it.
        this.user = user;
        this.temporary = Objects.requireNonNull(temporary, "temporary");
    }

    @Override public Map<String, DataValue> tempCache() { return temporary.get(); }
    @Override public Cache userCache() {
        UserDataCache cache = user.getCache();
        if (cache == null) return null;
        // Retain this exact cache object across cacheIfNeeded, as before.
        return new Cache() {
            public boolean isCached(String key) { return cache.isCached(key); }
            public DataValue get(String key) { return cache.getCache().get(key); }
        };
    }
    @Override public void cacheIfNeeded() { user.cacheIfNeeded(); }
    @Override public void cache() { user.cache(); }
    @Override public List<Column> sqliteRow() { return facade.getSQLiteRow(); }
    @Override public List<Column> mysqlRow() { return facade.getMySqlRow(); }
    @SuppressWarnings("deprecation")
    @Override public int flatInt(String key, int fallback) {
        return facade.getData(user.getUUID()).getInt(key, fallback);
    }
    @SuppressWarnings("deprecation")
    @Override public String flatString(String key) {
        return facade.getData(user.getUUID()).getString(key, "");
    }
}
