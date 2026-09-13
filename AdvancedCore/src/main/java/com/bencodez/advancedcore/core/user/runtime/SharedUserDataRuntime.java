package com.bencodez.advancedcore.core.user.runtime;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.bencodez.advancedcore.api.user.UserDataFetchMode;
import com.bencodez.advancedcore.core.user.storage.SqlUserDataAccess;
import com.bencodez.advancedcore.core.user.storage.sql.SqlUserBackend;
import com.bencodez.simpleapi.sql.Column;
import com.bencodez.simpleapi.sql.data.DataValue;

/** Coordinates one existing cache/queue and its SQL provider; storage work runs on a worker. */
public final class SharedUserDataRuntime implements AutoCloseable {
    private static final int USER_LOCK_STRIPES = 64;
    private final UserCacheOwner cacheOwner;
    private final ReentrantReadWriteLock lifecycle = new ReentrantReadWriteLock(true);
    private final ReentrantReadWriteLock[] userLocks = createUserLocks();
    private final AtomicBoolean retiring = new AtomicBoolean();
    private final Object closeLock = new Object();
    private volatile SqlUserBackend backend;
    private volatile boolean closed;
    private CompletableFuture<Void> closeAttempt;

    public SharedUserDataRuntime(SqlUserBackend backend, UserCacheOwner cacheOwner) {
        this.backend = Objects.requireNonNull(backend, "backend");
        this.cacheOwner = Objects.requireNonNull(cacheOwner, "cacheOwner");
        Consumer<Runnable> lifecycleGate = batch -> access(() -> { batch.run(); return null; });
        BiConsumer<UUID, Runnable> perUserGate = (uuid, batch) -> userAccess(uuid, () -> { batch.run(); return null; });
        cacheOwner.bindLifecycle(backend, lifecycleGate, perUserGate);
    }

    public DataValue read(UUID uuid, String key, UserDataFetchMode mode, HashMap<String, DataValue> temporaryCache, DataValue defaultValue) {
        Objects.requireNonNull(uuid, "uuid");
        return userAccess(uuid, () -> {
            Objects.requireNonNull(mode, "mode");
            if (key == null || key.isEmpty()) return defaultValue;
            if (mode.allowTempCache() && temporaryCache != null) {
                DataValue temporary = temporaryCache.get(key);
                if (temporary != null) return temporary;
                if (!mode.allowUserCache() && !mode.allowStorageLookup()) return defaultValue;
            }
            if (mode.allowUserCache()) {
                DataValue cached = cacheOwner.getIfPresent(uuid, key);
                if (cached != null) return cached;
                if (mode.allowStorageLookup() && mode.waitForCache() && !cacheOwner.isCached(uuid)) {
                    cacheOwner.requireBlockingAllowed();
                    populateInternal(uuid);
                    cached = cacheOwner.getIfPresent(uuid, key);
                    if (cached != null) return cached;
                }
                if (!mode.allowStorageLookup()) return defaultValue;
            } else if (!mode.allowStorageLookup()) return defaultValue;
            cacheOwner.requireBlockingAllowed();
            return find(readStorageRow(uuid), key, defaultValue);
        });
    }

    public HashMap<String, DataValue> populate(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");
        return storageUserAccess(uuid, () -> populateInternal(uuid));
    }

    private HashMap<String, DataValue> populateInternal(UUID uuid) {
        UserCacheOwner.PopulationToken token = cacheOwner.beginPopulation(uuid);
        if (cacheOwner.isCached(uuid)) flushInternal(uuid);
        HashMap<String, DataValue> values = SqlUserDataAccess.convert(readStorageRow(uuid));
        return cacheOwner.completePopulation(uuid, values, token);
    }

    public int startupForEach(BiConsumer<UUID, HashMap<String, DataValue>> consumer, boolean populateCache) {
        return storageAccess(() -> {
            Objects.requireNonNull(consumer, "consumer");
            int[] count = { 0 };
            backend.forEachUser(uuid -> {
                HashMap<String, DataValue> values = userAccess(uuid, () -> populateCache ? populateInternal(uuid) : SqlUserDataAccess.convert(readStorageRow(uuid)));
                // External callbacks run after releasing the per-user read lock so they
                // may safely remove or otherwise exclusively mutate this user.
                consumer.accept(uuid, values);
                count[0]++;
            });
            return count[0];
        });
    }

    public void queueChange(UUID uuid, String key, DataValue value) {
        Objects.requireNonNull(uuid, "uuid");
        userAccess(uuid, () -> {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(value, "value");
            if (!cacheOwner.isCached(uuid)) {
                cacheOwner.requireBlockingAllowed();
                populateInternal(uuid);
            }
            cacheOwner.queueChange(uuid, key, value);
            return null;
        });
    }

    public void flush(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");
        storageUserAccess(uuid, () -> { flushInternal(uuid); return null; });
    }

    private void flushInternal(UUID uuid) { cacheOwner.flush(uuid, backend.storageType(), backend.user(uuid)); }

    public void flushAll() {
        storageAccess(() -> {
            for (UUID uuid : Set.copyOf(cacheOwner.cachedUsers())) userAccess(uuid, () -> { flushInternal(uuid); return null; });
            return null;
        });
    }

    private void flushAllInternal() { for (UUID uuid : Set.copyOf(cacheOwner.cachedUsers())) flushInternal(uuid); }

    public void replaceBackend(SqlUserBackend replacement) {
        rejectReentrantTransition();
        cacheOwner.requireBlockingAllowed();
        Objects.requireNonNull(replacement, "replacement");
        lifecycle.writeLock().lock();
        try {
            requireOpen();
            if (replacement == backend) return;
            if (!replacement.isOpen()) throw new IllegalArgumentException("replacement backend is closed");
            flushAllInternal();
            cacheOwner.clearAfterFlush();
            SqlUserBackend previous = backend;
            cacheOwner.bindBackend(replacement);
            backend = replacement;
            previous.close();
        } finally { lifecycle.writeLock().unlock(); }
    }

    public void remove(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");
        cacheOwner.requireBlockingAllowed();
        userExclusiveAccess(uuid, () -> {
            flushInternal(uuid);
            backend.user(uuid).delete(backend.storageType());
            cacheOwner.remove(uuid);
            return null;
        });
    }

    public SqlUserBackend backend() { return backend; }
    public boolean isClosed() { return closed; }
    public boolean isRetiring() { return retiring.get(); }

    public CompletionStage<Void> closeAsync(Executor executor) {
        Objects.requireNonNull(executor, "executor");
        rejectReentrantTransition();
        CompletableFuture<Void> result;
        synchronized (closeLock) {
            if (closeAttempt != null && !closeAttempt.isCompletedExceptionally()) return closeAttempt.minimalCompletionStage();
            retiring.set(true);
            result = new CompletableFuture<>();
            closeAttempt = result;
        }
        try {
            executor.execute(() -> {
                try {
                    cacheOwner.requireBlockingAllowed();
                    lifecycle.writeLock().lock();
                    try {
                        if (!closed) {
                            flushAllInternal();
                            cacheOwner.clearAfterFlush();
                            cacheOwner.shutdown();
                            backend.close();
                            closed = true;
                        }
                    } finally { lifecycle.writeLock().unlock(); }
                    result.complete(null);
                } catch (Throwable failure) { result.completeExceptionally(failure); }
            });
        } catch (RuntimeException | Error failure) { result.completeExceptionally(failure); }
        return result.minimalCompletionStage();
    }

    @Override
    public void close() {
        if (closed) return;
        cacheOwner.requireBlockingAllowed();
        try { closeAsync(Runnable::run).toCompletableFuture().join(); }
        catch (CompletionException failure) {
            if (failure.getCause() instanceof RuntimeException runtime) throw runtime;
            if (failure.getCause() instanceof Error error) throw error;
            throw failure;
        }
    }

    private <T> T access(Supplier<T> operation) {
        boolean admitted = lifecycle.getReadHoldCount() > 0 || lifecycle.isWriteLockedByCurrentThread();
        if (!admitted) requireOpen();
        lifecycle.readLock().lock();
        try {
            if (!admitted) requireOpen();
            return operation.get();
        } finally { lifecycle.readLock().unlock(); }
    }

    private <T> T storageAccess(Supplier<T> operation) { cacheOwner.requireBlockingAllowed(); return access(operation); }

    private <T> T userAccess(UUID uuid, Supplier<T> operation) {
        return access(() -> {
            ReentrantReadWriteLock.ReadLock lock = userLock(uuid).readLock();
            lock.lock();
            try { return operation.get(); } finally { lock.unlock(); }
        });
    }

    private <T> T storageUserAccess(UUID uuid, Supplier<T> operation) { cacheOwner.requireBlockingAllowed(); return userAccess(uuid, operation); }

    private <T> T userExclusiveAccess(UUID uuid, Supplier<T> operation) {
        return access(() -> {
            ReentrantReadWriteLock.WriteLock lock = userLock(uuid).writeLock();
            lock.lock();
            try { return operation.get(); } finally { lock.unlock(); }
        });
    }

    private ReentrantReadWriteLock userLock(UUID uuid) {
        int index = (uuid.hashCode() & Integer.MAX_VALUE) % USER_LOCK_STRIPES;
        return userLocks[index];
    }

    private static ReentrantReadWriteLock[] createUserLocks() {
        ReentrantReadWriteLock[] locks = new ReentrantReadWriteLock[USER_LOCK_STRIPES];
        for (int i = 0; i < locks.length; i++) locks[i] = new ReentrantReadWriteLock(true);
        return locks;
    }

    private void rejectReentrantTransition() {
        if (lifecycle.getReadHoldCount() > 0 || lifecycle.isWriteLockedByCurrentThread()) throw new IllegalStateException("Cannot replace or close the user runtime from an active user callback");
    }

    private List<Column> readStorageRow(UUID uuid) { return backend.user(uuid).readRow(backend.storageType()); }

    private DataValue find(List<Column> row, String key, DataValue defaultValue) {
        if (row != null) for (Column column : row) if (column.getName().equals(key)) return column.getValue() == null ? defaultValue : column.getValue();
        return defaultValue;
    }

    private void requireOpen() {
        if (retiring.get()) throw new IllegalStateException("Shared user data runtime is retiring or closed");
        if (!backend.isOpen()) throw new IllegalStateException("SQL user backend is closed");
    }
}
