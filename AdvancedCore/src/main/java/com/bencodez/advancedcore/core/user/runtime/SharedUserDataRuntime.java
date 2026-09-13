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
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import com.bencodez.advancedcore.api.user.UserDataFetchMode;
import com.bencodez.advancedcore.core.user.storage.SqlUserDataAccess;
import com.bencodez.advancedcore.core.user.storage.sql.SqlUserBackend;
import com.bencodez.simpleapi.sql.Column;
import com.bencodez.simpleapi.sql.data.DataValue;

/** Coordinates one existing cache/queue and its SQL provider; storage work runs on a worker. */
public final class SharedUserDataRuntime implements AutoCloseable {
    private final UserCacheOwner cacheOwner;
    private final ReentrantLock lifecycle = new ReentrantLock(true);
    private final AtomicBoolean retiring = new AtomicBoolean();
    private final Object closeLock = new Object();
    private volatile SqlUserBackend backend;
    private volatile boolean closed;
    private CompletableFuture<Void> closeAttempt;

    public SharedUserDataRuntime(SqlUserBackend backend, UserCacheOwner cacheOwner) {
        this.backend = Objects.requireNonNull(backend, "backend");
        this.cacheOwner = Objects.requireNonNull(cacheOwner, "cacheOwner");
        cacheOwner.bindLifecycle(backend, batch -> access(() -> { batch.run(); return null; }));
    }

    public DataValue read(UUID uuid, String key, UserDataFetchMode mode,
            HashMap<String, DataValue> temporaryCache, DataValue defaultValue) {
        return access(() -> {
            Objects.requireNonNull(uuid, "uuid");
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
                    populateInternal(uuid);
                    cached = cacheOwner.getIfPresent(uuid, key);
                    if (cached != null) return cached;
                }
                if (!mode.allowStorageLookup()) return defaultValue;
            } else if (!mode.allowStorageLookup()) {
                return defaultValue;
            }
            return find(readStorageRow(uuid), key, defaultValue);
        });
    }

    public HashMap<String, DataValue> populate(UUID uuid) {
        return access(() -> populateInternal(Objects.requireNonNull(uuid, "uuid")));
    }

    private HashMap<String, DataValue> populateInternal(UUID uuid) {
        if (cacheOwner.isCached(uuid)) flushInternal(uuid);
        HashMap<String, DataValue> values = SqlUserDataAccess.convert(readStorageRow(uuid));
        cacheOwner.populate(uuid, values);
        return values;
    }

    public int startupForEach(BiConsumer<UUID, HashMap<String, DataValue>> consumer, boolean populateCache) {
        return access(() -> {
            Objects.requireNonNull(consumer, "consumer");
            int count = 0;
            for (UUID uuid : backend.enumerateUsers()) {
                HashMap<String, DataValue> values = populateCache ? populateInternal(uuid)
                        : SqlUserDataAccess.convert(readStorageRow(uuid));
                consumer.accept(uuid, values);
                count++;
            }
            return count;
        });
    }

    public void queueChange(UUID uuid, String key, DataValue value) {
        access(() -> {
            Objects.requireNonNull(uuid, "uuid");
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(value, "value");
            if (!cacheOwner.isCached(uuid)) populateInternal(uuid);
            cacheOwner.queueChange(uuid, key, value);
            return null;
        });
    }

    public void flush(UUID uuid) {
        access(() -> { flushInternal(Objects.requireNonNull(uuid, "uuid")); return null; });
    }

    private void flushInternal(UUID uuid) {
        cacheOwner.flush(uuid, backend.storageType(), backend.user(uuid));
    }

    public void flushAll() {
        access(() -> { flushAllInternal(); return null; });
    }

    private void flushAllInternal() {
        for (UUID uuid : Set.copyOf(cacheOwner.cachedUsers())) flushInternal(uuid);
    }

    public void replaceBackend(SqlUserBackend replacement) {
        rejectReentrantTransition();
        access(() -> {
            Objects.requireNonNull(replacement, "replacement");
            if (replacement == backend) return null;
            if (!replacement.isOpen()) throw new IllegalArgumentException("replacement backend is closed");
            flushAllInternal();
            cacheOwner.clearAfterFlush();
            SqlUserBackend previous = backend;
            cacheOwner.bindBackend(replacement);
            backend = replacement;
            previous.close();
            return null;
        });
    }

    public void remove(UUID uuid) {
        access(() -> {
            Objects.requireNonNull(uuid, "uuid");
            flushInternal(uuid);
            backend.user(uuid).delete(backend.storageType());
            cacheOwner.remove(uuid);
            return null;
        });
    }

    public SqlUserBackend backend() { return backend; }
    public boolean isClosed() { return closed; }
    public boolean isRetiring() { return retiring.get(); }

    /**
     * Stop admission now and drain on the supplied worker. Completion represents
     * actual flush/cleanup, not task submission. A failed attempt retains the
     * unflushed queue/provider and can be retried; it never reopens admission.
     */
    public CompletionStage<Void> closeAsync(Executor executor) {
        Objects.requireNonNull(executor, "executor");
        rejectReentrantTransition();
        CompletableFuture<Void> result;
        synchronized (closeLock) {
            if (closeAttempt != null && !closeAttempt.isCompletedExceptionally()) {
                return closeAttempt.minimalCompletionStage();
            }
            retiring.set(true);
            result = new CompletableFuture<>();
            closeAttempt = result;
        }
        try {
            executor.execute(() -> {
                try {
                    cacheOwner.requireBlockingAllowed();
                    lifecycle.lock();
                    try {
                        if (!closed) {
                            flushAllInternal();
                            cacheOwner.clearAfterFlush();
                            cacheOwner.shutdown();
                            backend.close();
                            closed = true;
                        }
                    } finally {
                        lifecycle.unlock();
                    }
                    result.complete(null);
                } catch (Throwable failure) {
                    result.completeExceptionally(failure);
                }
            });
        } catch (RuntimeException | Error failure) {
            result.completeExceptionally(failure);
        }
        return result.minimalCompletionStage();
    }

    /** Blocking worker-only form. Server callbacks must use closeAsync and observe its stage. */
    @Override
    public void close() {
        if (closed) return;
        cacheOwner.requireBlockingAllowed();
        try {
            closeAsync(Runnable::run).toCompletableFuture().join();
        } catch (CompletionException failure) {
            if (failure.getCause() instanceof RuntimeException runtime) throw runtime;
            if (failure.getCause() instanceof Error error) throw error;
            throw failure;
        }
    }

    private <T> T access(Supplier<T> operation) {
        cacheOwner.requireBlockingAllowed();
        boolean admitted = lifecycle.isHeldByCurrentThread();
        if (!admitted) requireOpen();
        lifecycle.lock();
        try {
            // Nested notification work belongs to the already-admitted batch.
            if (!admitted) requireOpen();
            return operation.get();
        } finally {
            lifecycle.unlock();
        }
    }

    private void rejectReentrantTransition() {
        if (lifecycle.isHeldByCurrentThread()) {
            throw new IllegalStateException("Cannot replace or close the user runtime from an active user callback");
        }
    }

    private List<Column> readStorageRow(UUID uuid) {
        return backend.user(uuid).readRow(backend.storageType());
    }

    private DataValue find(List<Column> row, String key, DataValue defaultValue) {
        if (row != null) {
            for (Column column : row) {
                if (column.getName().equals(key)) {
                    return column.getValue() == null ? defaultValue : column.getValue();
                }
            }
        }
        return defaultValue;
    }

    private void requireOpen() {
        if (retiring.get()) throw new IllegalStateException("Shared user data runtime is retiring or closed");
        if (!backend.isOpen()) throw new IllegalStateException("SQL user backend is closed");
    }
}
