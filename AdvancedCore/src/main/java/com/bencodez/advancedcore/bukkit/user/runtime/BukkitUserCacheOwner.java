package com.bencodez.advancedcore.bukkit.user.runtime;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

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
    private volatile BiConsumer<UUID, Runnable> userGate;
    private volatile BiConsumer<UUID, Runnable> exclusiveUserGate;
    private final ConcurrentHashMap<UUID, Consumer<Runnable>> cacheGates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Consumer<Runnable>> exclusiveCacheGates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ConcurrentLinkedQueue<Runnable>> pendingNotifications =
			new ConcurrentHashMap<>();
    private final Consumer<UUID> cacheRemovalListener = uuid -> {
        cacheGates.remove(uuid);
        exclusiveCacheGates.remove(uuid);
    };
    private final Consumer<UserDataCache> cacheInitializer;

    public BukkitUserCacheOwner(UserDataManager manager) {
        this.manager = Objects.requireNonNull(manager, "manager");
        cacheInitializer = cache -> {
            Consumer<Runnable> gate = cacheGate(cache.getUuid());
            if (gate == null) bind(cache, cache.getUuid());
            else gate.accept(() -> bind(cache, cache.getUuid()));
        };
    }

    @Override public synchronized void bindFlushGate(Consumer<Runnable> gate) {
        Objects.requireNonNull(gate, "gate");
        if (flushGate != null && flushGate != gate) throw new IllegalStateException("Cache owner already belongs to another runtime");
        flushGate = gate;
    }

    @Override public synchronized void bindUserGate(BiConsumer<UUID, Runnable> gate) {
        Objects.requireNonNull(gate, "gate");
        if (userGate != null && userGate != gate) throw new IllegalStateException("Cache owner already belongs to another runtime");
        userGate = gate;
    }

    @Override
    public synchronized void bindLifecycle(SqlUserBackend backend, Consumer<Runnable> gate,
            BiConsumer<UUID, Runnable> perUserGate) {
        bindLifecycle(backend, gate, perUserGate, perUserGate);
    }

    @Override
    public synchronized void bindLifecycle(SqlUserBackend backend, Consumer<Runnable> gate,
            BiConsumer<UUID, Runnable> perUserGate, BiConsumer<UUID, Runnable> perUserExclusiveGate) {
        Objects.requireNonNull(backend, "backend");
        Objects.requireNonNull(gate, "gate");
        Objects.requireNonNull(perUserGate, "perUserGate");
        Objects.requireNonNull(perUserExclusiveGate, "perUserExclusiveGate");
        if (flushGate != null && flushGate != gate) throw new IllegalStateException("Cache owner already belongs to another runtime");
        if (userGate != null && userGate != perUserGate) throw new IllegalStateException("Cache owner already belongs to another runtime");
        if (exclusiveUserGate != null && exclusiveUserGate != perUserExclusiveGate) throw new IllegalStateException("Cache owner already belongs to another runtime");

        manager.beginSharedBindingTransition();
        try {
            manager.bindSharedCacheInitializer(cacheInitializer);
            manager.bindSharedCacheRemovalListener(cacheRemovalListener);
            manager.bindSharedSqlBackend(backend, gate, perUserGate, perUserExclusiveGate);
            this.backend = backend;
            flushGate = gate;
            userGate = perUserGate;
            exclusiveUserGate = perUserExclusiveGate;
            cacheGates.clear();
            exclusiveCacheGates.clear();
        } finally {
            manager.endSharedBindingTransition();
        }
    }

    @Override public synchronized void bindLifecycle(SqlUserBackend backend, Consumer<Runnable> gate) {
        BiConsumer<UUID, Runnable> existing = userGate;
        if (existing == null) existing = (uuid, operation) -> gate.accept(operation);
        BiConsumer<UUID, Runnable> exclusive = exclusiveUserGate;
        if (exclusive == null) exclusive = existing;
        bindLifecycle(backend, gate, existing, exclusive);
    }

    @Override public synchronized void bindBackend(SqlUserBackend backend) {
        Objects.requireNonNull(backend, "backend");
        manager.bindSharedCacheInitializer(cacheInitializer);
        manager.bindSharedCacheRemovalListener(cacheRemovalListener);
        this.backend = backend;
        BiConsumer<UUID, Runnable> perUser = userGate;
        if (perUser != null) manager.bindSharedSqlBackend(backend, flushGate, perUser,
                exclusiveUserGate == null ? perUser : exclusiveUserGate);
        else if (flushGate != null) manager.bindSharedSqlBackend(backend, flushGate);
        manager.advanceSharedUserDataNotificationGeneration();
    }

    private Consumer<Runnable> cacheGate(UUID uuid) {
        BiConsumer<UUID, Runnable> perUser = userGate;
        if (perUser != null && uuid != null) {
            return cacheGates.computeIfAbsent(uuid, id -> operation -> {
                BiConsumer<UUID, Runnable> current = userGate;
                if (current == null) throw new IllegalStateException("Shared user lifecycle is not bound");
                current.accept(id, operation);
            });
        }
        return flushGate;
    }

    private Consumer<Runnable> exclusiveCacheGate(UUID uuid) {
        BiConsumer<UUID, Runnable> perUser = exclusiveUserGate;
        if (perUser == null || uuid == null) return flushGate;
        return exclusiveCacheGates.computeIfAbsent(uuid, id -> operation -> {
            BiConsumer<UUID, Runnable> current = exclusiveUserGate;
            if (current == null) throw new IllegalStateException("Shared user lifecycle is not bound");
            current.accept(id, operation);
        });
    }

    private void bind(UserDataCache cache, UUID uuid) {
        if (backend == null) return;
        // Do not capture the provider. Detached caches can outlive a replacement
        // boundary before they are inserted into the manager map. Their writer is
        // always invoked under the stable per-user lifecycle gate, then resolves
        // whichever backend is current after that admission.
        cache.configureSharedStorage(values -> {
            requireBlockingAllowed();
            SqlUserBackend selected = backend;
            if (selected == null || !selected.isOpen()) {
                throw new IllegalStateException("Shared SQL backend is unavailable");
            }
            selected.user(uuid).writeValues(selected.storageType(), values);
        }, cacheGate(uuid), exclusiveCacheGate(uuid));
    }

    @Override public void requireBlockingAllowed() {
        if ((Bukkit.getServer() != null && Bukkit.isPrimaryThread()) || manager.isPlatformOwnedThread()) {
            throw new IllegalStateException("Shared user storage must run on a worker; use closeAsync for shutdown");
        }
    }

    @Override public boolean isCached(UUID uuid) { return manager.isCached(uuid); }

    @Override public boolean hasPendingChanges(UUID uuid) {
        UserDataCache cache = manager.getUserDataCache().get(uuid);
        return cache != null && cache.hasChangesToProcess();
    }

    @Override public DataValue getIfPresent(UUID uuid, String key) {
        UserDataCache cache = manager.getUserDataCache().get(uuid);
        if (cache == null) return null;
        synchronized (cache) { return cache.getCache() == null ? null : cache.getCache().get(key); }
    }

    @Override public void populate(UUID uuid, HashMap<String, DataValue> values) {
        Boolean admitted = manager.withCacheMapReadAdmission(() -> {
            UserDataCache cache = manager.getUserDataCache().computeIfAbsent(uuid, ignored -> new UserDataCache(manager, uuid));
            bind(cache, uuid);
            cache.updateCachePreservingPending(values);
            return Boolean.TRUE;
        });
        // Test doubles and legacy adapters may not implement the optional admission hook.
        if (admitted == null) populateWithoutMapAdmission(uuid, values);
    }

    private void populateWithoutMapAdmission(UUID uuid, HashMap<String, DataValue> values) {
        UserDataCache cache = manager.getUserDataCache().computeIfAbsent(uuid, ignored -> new UserDataCache(manager, uuid));
        bind(cache, uuid);
        cache.updateCachePreservingPending(values);
    }

    private record CachePopulation(UUID uuid, UserDataCache cache, long version) implements PopulationToken {}

    @Override public PopulationToken beginPopulation(UUID uuid) {
        CachePopulation admitted = manager.withCacheMapReadAdmission(() -> {
            UserDataCache cache = manager.getUserDataCache().computeIfAbsent(uuid, ignored -> new UserDataCache(manager, uuid));
            bind(cache, uuid);
            return new CachePopulation(uuid, cache, cache.getSharedSnapshotVersion());
        });
        if (admitted != null) return admitted;
        UserDataCache cache = manager.getUserDataCache().computeIfAbsent(uuid, ignored -> new UserDataCache(manager, uuid));
        bind(cache, uuid);
        return new CachePopulation(uuid, cache, cache.getSharedSnapshotVersion());
    }

    @Override public HashMap<String, DataValue> completePopulation(UUID uuid, HashMap<String, DataValue> values, PopulationToken token) {
        if (!(token instanceof CachePopulation expected) || !uuid.equals(expected.uuid())) throw new IllegalArgumentException("Population token does not belong to this user");
        AtomicReference<HashMap<String, DataValue>> populated = new AtomicReference<>();
        HashMap<String, DataValue> admitted = manager.withCacheMapReadAdmission(() -> {
            manager.getUserDataCache().compute(uuid, (ignored, current) -> {
                if (current != expected.cache()) throw new IllegalStateException("User cache changed while loading its database snapshot");
                populated.set(current.updateSharedSnapshot(values, expected.version()));
                return current;
            });
            return populated.get();
        });
        if (admitted == null) {
            manager.getUserDataCache().compute(uuid, (ignored, current) -> {
                if (current != expected.cache()) throw new IllegalStateException("User cache changed while loading its database snapshot");
                populated.set(current.updateSharedSnapshot(values, expected.version()));
                return current;
            });
        }
        return populated.get();
    }

    @Override public void queueChange(UUID uuid, String key, DataValue value) {
        Objects.requireNonNull(value, "value");
        UserDataCache cache = manager.getUserDataCache().get(uuid);
        if (cache == null) throw new IllegalStateException("User must be populated before queuing: " + uuid);
        bind(cache, uuid);
        cache.addChange(change(key, value), true);
    }

    @Override public void flush(UUID uuid, SqlUserStorage storage) {
        UserStorage type = backend == null ? manager.getPlugin().getStorageType() : backend.storageType();
        flush(uuid, type, storage);
    }

    @Override public void flush(UUID uuid, UserStorage type, SqlUserStorage storage) {
        UserDataCache cache = manager.getUserDataCache().get(uuid);
        if (cache != null) {
            bind(cache, uuid);
            cache.setSharedStorageWriter(values -> {
                requireBlockingAllowed();
                storage.writeValues(type, values);
            });
			do {
				Runnable notification = cache.processChangesForSharedRuntime();
				if (notification != null) pendingNotifications.compute(uuid, (ignored, notifications) -> {
					ConcurrentLinkedQueue<Runnable> queue = notifications == null
							? new ConcurrentLinkedQueue<>() : notifications;
					queue.add(notification);
					return queue;
				});
			} while (cache.hasChangesToProcess());
        }
    }

    @Override public synchronized void dispatchNotifications(UUID uuid) {
        ConcurrentLinkedQueue<Runnable> notifications = pendingNotifications.remove(uuid);
        if (notifications == null) return;
        // Preserve the established storage-worker callback contract. The runtime has
        // released per-user admission here, so callbacks may perform exclusive storage
        // work; moving them to Bukkit's primary thread would make that work illegal.
        Runnable notification;
        while ((notification = notifications.poll()) != null) {
            manager.dispatchSharedUserDataNotification(notification);
        }
    }

    @Override public void dispatchAllNotifications() {
        for (UUID uuid : Set.copyOf(pendingNotifications.keySet())) dispatchNotifications(uuid);
    }

    @Override public void reportCommittedFailure(UUID uuid, Throwable failure) {
        manager.recordSharedStorageFailure(failure);
    }

    @Override public synchronized void discardAllNotifications() {
		pendingNotifications.clear();
		manager.advanceSharedUserDataNotificationGeneration();
	}

    @Override public Set<UUID> cachedUsers() { return new HashSet<>(manager.getUserDataCache().keySet()); }

	@Override public void beginRemoval(UUID uuid) {
		UserDataCache cache = manager.getUserDataCache().get(uuid);
		if (cache != null) cache.beginRemoval();
	}

	@Override public void cancelRemoval(UUID uuid) {
		UserDataCache cache = manager.getUserDataCache().get(uuid);
		if (cache != null) cache.cancelRemoval();
	}

	@Override public void beginRetirement() {
		for (UserDataCache cache : manager.getUserDataCache().values()) cache.beginRemoval();
	}

	@Override public void cancelRetirement() {
		for (UserDataCache cache : manager.getUserDataCache().values()) cache.cancelRemoval();
	}

    @Override public void remove(UUID uuid) {
        UserDataCache cache = manager.getUserDataCache().get(uuid);
        if (cache != null) {
            cache.retireAfterSharedFlush();
        }
        if (manager.retireSharedCache(uuid, cache)) {
            cacheGates.remove(uuid);
            exclusiveCacheGates.remove(uuid);
        }
    }

    @Override public void clearAfterFlush() { for (UUID uuid : cachedUsers()) remove(uuid); }

    @Override public void shutdown() {
        manager.closeSharedUserDataNotifications();
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
