package com.bencodez.advancedcore.bukkit.user.runtime;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import org.bukkit.Bukkit;

import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.advancedcore.api.user.usercache.UserDataCache;
import com.bencodez.advancedcore.api.user.usercache.UserDataManager;
import com.bencodez.advancedcore.api.user.usercache.change.UserDataChange;
import com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeBoolean;
import com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt;
import com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeString;
import com.bencodez.advancedcore.core.user.runtime.UserCacheOwner;
import com.bencodez.advancedcore.core.user.storage.SqlUserStorage;
import com.bencodez.advancedcore.core.user.storage.sql.SqlUserBackend;
import com.bencodez.simpleapi.sql.data.DataValue;

/** Reuses the existing manager, cache instances, queue and notification ordering. */
public final class BukkitUserCacheOwner implements UserCacheOwner {
    private final UserDataManager manager;
    private volatile SqlUserBackend backend;
    private volatile Consumer<Runnable> flushGate;
    private final Consumer<UserDataCache> cacheInitializer;

    public BukkitUserCacheOwner(UserDataManager manager) {
        this.manager = Objects.requireNonNull(manager, "manager");
        cacheInitializer = cache -> {
            Consumer<Runnable> gate;
            synchronized (this) { gate = flushGate; }
            if (gate == null) bind(cache, cache.getUuid());
            else gate.accept(() -> bind(cache, cache.getUuid()));
        };
    }

    @Override
    public synchronized void bindFlushGate(Consumer<Runnable> gate) {
        Objects.requireNonNull(gate, "gate");
        if (flushGate != null && flushGate != gate) {
            throw new IllegalStateException("Cache owner already belongs to another runtime");
        }
        flushGate = gate;
    }

    @Override
    public synchronized void bindLifecycle(SqlUserBackend backend, Consumer<Runnable> gate) {
        Objects.requireNonNull(backend, "backend");
        Objects.requireNonNull(gate, "gate");
        if (flushGate != null && flushGate != gate) {
            throw new IllegalStateException("Cache owner already belongs to another runtime");
        }
        // Installation publishes no cache changes and runs no callbacks. A failed
        // registration leaves this owner reusable; the callback takes this monitor
        // before using the gate, so it cannot observe a half-published binding.
        manager.bindSharedCacheInitializer(cacheInitializer);
        this.backend = backend;
        flushGate = gate;
    }

    @Override
    public synchronized void bindBackend(SqlUserBackend backend) {
        Objects.requireNonNull(backend, "backend");
        manager.bindSharedCacheInitializer(cacheInitializer);
        this.backend = backend;
        // Existing legacy batches finish with their original writer. Binding is
        // lazy and refuses an active legacy batch, leaving a reachable runtime
        // whose flush/close can be retried instead of orphaning half-bound caches.
    }

    private void bind(UserDataCache cache, UUID uuid) {
        SqlUserBackend selected = backend;
        if (selected != null) {
            // Capture this lifecycle, not plugin.getStorageType() or a later provider.
            cache.configureSharedStorage(values ->
                    selected.user(uuid).writeValues(selected.storageType(), values), flushGate);
        }
    }

    @Override
    public void requireBlockingAllowed() {
        if (Bukkit.getServer() != null && Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Shared user storage must run on a worker; use closeAsync for shutdown");
        }
    }

    @Override
    public boolean isCached(UUID uuid) { return manager.isCached(uuid); }

    @Override
    public DataValue getIfPresent(UUID uuid, String key) {
        UserDataCache cache = manager.getUserDataCache().get(uuid);
        if (cache == null) return null;
        synchronized (cache) {
            return cache.getCache() == null ? null : cache.getCache().get(key);
        }
    }

    @Override
    public void populate(UUID uuid, HashMap<String, DataValue> values) {
        UserDataCache cache = manager.getUserDataCache().computeIfAbsent(uuid,
                ignored -> new UserDataCache(manager, uuid));
        bind(cache, uuid);
        cache.updateCache(values);
    }

    @Override
    public void queueChange(UUID uuid, String key, DataValue value) {
        Objects.requireNonNull(value, "value");
        UserDataCache cache = manager.getUserDataCache().get(uuid);
        if (cache == null) throw new IllegalStateException("User must be populated before queuing: " + uuid);
        bind(cache, uuid);
        cache.addChange(change(key, value), true);
    }

    @Override
    public void flush(UUID uuid, SqlUserStorage storage) {
        UserStorage type = backend == null ? manager.getPlugin().getStorageType() : backend.storageType();
        flush(uuid, type, storage);
    }

    @Override
    public void flush(UUID uuid, UserStorage type, SqlUserStorage storage) {
        UserDataCache cache = manager.getUserDataCache().get(uuid);
        if (cache != null) {
            bind(cache, uuid);
            cache.setSharedStorageWriter(values -> storage.writeValues(type, values));
            do {
                // Includes the existing in-flight batch and its notifications before returning.
                cache.processChanges();
            } while (cache.hasChangesToProcess());
        }
    }

    @Override
    public Set<UUID> cachedUsers() { return new HashSet<>(manager.getUserDataCache().keySet()); }

    @Override
    public void remove(UUID uuid) {
        UserDataCache cache = manager.getUserDataCache().get(uuid);
        if (cache != null) {
            // Never call the legacy clear/dump path, which can resolve the plugin's provider.
            cache.retireAfterSharedFlush();
            manager.getUserDataCache().remove(uuid, cache);
        }
    }

    @Override
    public void clearAfterFlush() {
        for (UUID uuid : cachedUsers()) remove(uuid);
    }

    @Override
    public void shutdown() {
        // User data has already drained. Cancel redundant delayed/periodic tasks
        // without awaiting the timer on a server thread or interrupting active JDBC.
        if (manager.getTimer() instanceof ScheduledThreadPoolExecutor timer) {
            timer.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
            timer.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        }
        manager.getTimer().shutdown();
    }

    private UserDataChange change(String key, DataValue value) {
        if (value.isInt()) return new UserDataChangeInt(key, value.getInt());
        if (value.isBoolean()) return new UserDataChangeBoolean(key, value.getBoolean());
        return new UserDataChangeString(key, value.getString());
    }
}
