package com.bencodez.advancedcore.bukkit.user.runtime;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.bencodez.advancedcore.api.user.usercache.UserDataCache;
import com.bencodez.advancedcore.api.user.usercache.UserDataManager;
import com.bencodez.advancedcore.api.user.usercache.change.UserDataChange;
import com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeBoolean;
import com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt;
import com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeString;
import com.bencodez.advancedcore.core.user.runtime.UserCacheOwner;
import com.bencodez.advancedcore.core.user.storage.SqlUserStorage;
import com.bencodez.simpleapi.sql.data.DataValue;

/**
 * Reuses the existing UserDataManager map, UserDataCache instances and their
 * queued-change ordering. No parallel Bukkit cache is introduced.
 */
public final class BukkitUserCacheOwner implements UserCacheOwner {
    private final UserDataManager manager;

    public BukkitUserCacheOwner(UserDataManager manager) {
        this.manager = Objects.requireNonNull(manager, "manager");
    }

    @Override
    public boolean isCached(UUID uuid) {
        return manager.isCached(uuid);
    }

    @Override
    public DataValue getIfPresent(UUID uuid, String key) {
        UserDataCache cache = manager.getUserDataCache().get(uuid);
        if (cache == null || !cache.isCached(key) || cache.getCache() == null) {
            return null;
        }
        return cache.getCache().get(key);
    }

    @Override
    public void populate(UUID uuid, HashMap<String, DataValue> values) {
        UserDataCache cache = manager.getUserDataCache().computeIfAbsent(uuid,
                ignored -> new UserDataCache(manager, uuid));
        cache.updateCache(values);
    }

    @Override
    public void queueChange(UUID uuid, String key, DataValue value) {
        Objects.requireNonNull(value, "value");
        UserDataCache cache = manager.getUserDataCache().get(uuid);
        if (cache == null) {
            throw new IllegalStateException("User must be populated before queuing a change: " + uuid);
        }
        cache.addChange(change(key, value), true);
    }

    @Override
    public void flush(UUID uuid, SqlUserStorage storage) {
        UserDataCache cache = manager.getUserDataCache().get(uuid);
        if (cache != null) {
            // UserDataCache remains the queue owner and preserves existing notification,
            // batching and write-order behavior. The storage argument is for headless owners.
            cache.processChanges();
        }
    }

    @Override
    public Set<UUID> cachedUsers() {
        return new HashSet<>(manager.getUserDataCache().keySet());
    }

    @Override
    public void remove(UUID uuid) {
        UserDataCache cache = manager.getUserDataCache().remove(uuid);
        if (cache != null) {
            cache.clearCache();
            cache.dump();
        }
    }

    @Override
    public void clearAfterFlush() {
        manager.clearCache();
    }

    @Override
    public void shutdown() {
        manager.getTimer().shutdown();
        try {
            if (!manager.getTimer().awaitTermination(10, TimeUnit.SECONDS)) {
                manager.getTimer().shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            manager.getTimer().shutdownNow();
        }
    }

    private UserDataChange change(String key, DataValue value) {
        if (value.isInt()) {
            return new UserDataChangeInt(key, value.getInt());
        }
        if (value.isBoolean()) {
            return new UserDataChangeBoolean(key, value.getBoolean());
        }
        return new UserDataChangeString(key, value.getString());
    }
}
