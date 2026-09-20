package com.bencodez.advancedcore.core.user.runtime;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.advancedcore.core.user.storage.SqlUserDataAccess;
import com.bencodez.advancedcore.core.user.storage.SqlUserStorage;
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
    /** A replacement whose close failed remains here for a later safe retry. */
    private volatile SqlUserBackend pendingBackendClose;
    private volatile boolean closed;
    private CompletableFuture<Void> closeAttempt;

    public SharedUserDataRuntime(SqlUserBackend backend, UserCacheOwner cacheOwner) {
        this.backend = Objects.requireNonNull(backend, "backend");
        this.cacheOwner = Objects.requireNonNull(cacheOwner, "cacheOwner");
        Consumer<Runnable> lifecycleGate = batch -> storageAccess(() -> { batch.run(); return null; });
        BiConsumer<UUID, Runnable> perUserGate = (uuid, batch) -> userAccess(uuid, () -> { batch.run(); return null; });
        BiConsumer<UUID, Runnable> exclusiveUserGate = (uuid, batch) -> userExclusiveAccess(uuid, () -> { batch.run(); return null; });
        cacheOwner.bindLifecycle(backend, lifecycleGate, perUserGate, exclusiveUserGate);
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
		try { storageUserAccess(uuid, () -> { flushInternal(uuid); return null; }); }
		finally { cacheOwner.dispatchNotifications(uuid); }
    }

    private void flushInternal(UUID uuid) { cacheOwner.flush(uuid, backend.storageType(), backend.user(uuid)); }

    /**
     * Extend the active SQL backend's user transaction with caller-owned SQL.
     * Pending cache changes are flushed first. Only after commit is the old
     * cache generation retired, so a later load observes committed values and
     * a rolled-back callback cannot publish speculative values.
     */
    public <T> T transaction(UUID uuid, SqlUserStorage.TransactionWork<T> work) {
        return transactionInternal(uuid, null, Map.of(), work);
    }

    /**
     * Require the selected physical store. Initial values are prerequisites
     * for creating a row with required columns, not part of the caller's atomic
     * mutation. When a cache already exists, they can commit before its queued
     * changes are flushed; callers must write operation-specific values only
     * inside the transaction callback.
     */
    public <T> T transaction(UUID uuid, UserStorage expectedStorage, Map<String, DataValue> initialValues,
            SqlUserStorage.TransactionWork<T> work) {
        Objects.requireNonNull(expectedStorage, "expectedStorage");
        return transactionInternal(uuid, expectedStorage, initialValues, work);
    }

    private <T> T transactionInternal(UUID uuid, UserStorage expectedStorage, Map<String, DataValue> initialValues,
            SqlUserStorage.TransactionWork<T> work) {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(initialValues, "initialValues");
        Objects.requireNonNull(work, "work");
        cacheOwner.requireBlockingAllowed();
        try {
            return userExclusiveAccess(uuid, () -> {
                if (expectedStorage != null && backend.storageType() != expectedStorage) {
                    throw new IllegalStateException("Cannot access " + expectedStorage
                            + " user storage while the shared runtime owns " + backend.storageType());
                }
                cacheOwner.beginRemoval(uuid);
                try {
                    if (cacheOwner.hasPendingChanges(uuid) && !initialValues.isEmpty()) {
                        // Existing queued changes may need a required column on
                        // first write. Establish only row prerequisites before
                        // their ordinary, independently durable cache flush.
                        backend.user(uuid).transaction(backend.storageType(), initialValues, scope -> null);
                    }
                    flushInternal(uuid);
                    T result = backend.user(uuid).transaction(backend.storageType(), initialValues, work);
                    cacheOwner.remove(uuid);
                    return result;
                } catch (RuntimeException | Error failure) {
                    cacheOwner.cancelRemoval(uuid);
                    throw failure;
                }
            });
        } finally { cacheOwner.dispatchNotifications(uuid); }
    }

    public void flushAll() {
		try { storageAccess(() -> {
            for (UUID uuid : Set.copyOf(cacheOwner.cachedUsers())) userAccess(uuid, () -> { flushInternal(uuid); return null; });
            return null;
		}); } finally { cacheOwner.dispatchAllNotifications(); }
    }

    /**
     * Run an explicitly requested native-storage maintenance operation while no
     * shared cache read, write, replacement, or shutdown operation can overlap
     * it. The caller supplies the storage-specific work; this runtime first
     * durably flushes and retires its cache generation so a provider change
     * cannot split queued updates across the old and replacement owners.
     */
    public void runStorageMaintenance(Runnable operation) {
        Objects.requireNonNull(operation, "operation");
        rejectReentrantTransition();
        cacheOwner.requireBlockingAllowed();
        lifecycle.writeLock().lock();
        try {
            requireOpen();
			cacheOwner.beginRetirement();
			try {
				flushAllInternal();
				cacheOwner.clearAfterFlush();
				operation.run();
			} catch (RuntimeException | Error failure) {
				cacheOwner.cancelRetirement();
				throw failure;
			}
		} finally {
			lifecycle.writeLock().unlock();
			cacheOwner.dispatchAllNotifications();
		}
    }

    private void flushAllInternal() { for (UUID uuid : Set.copyOf(cacheOwner.cachedUsers())) flushInternal(uuid); }

    public void replaceBackend(SqlUserBackend replacement) {
		replaceBackend(replacement, () -> {});
	}

	/** Replace the route and publish its platform owner before releasing lifecycle admission. */
	public void replaceBackend(SqlUserBackend replacement, Runnable afterReplacement) {
        rejectReentrantTransition();
        cacheOwner.requireBlockingAllowed();
        Objects.requireNonNull(replacement, "replacement");
		Objects.requireNonNull(afterReplacement, "afterReplacement");
        lifecycle.writeLock().lock();
        try {
            requireOpen();
            retryPendingBackendClose();
            if (replacement == backend) return;
            if (!replacement.isOpen()) throw new IllegalArgumentException("replacement backend is closed");
			cacheOwner.beginRetirement();
			try {
				flushAllInternal();
				cacheOwner.clearAfterFlush();
				SqlUserBackend previous = backend;
				afterReplacement.run();
				cacheOwner.bindBackend(replacement);
				backend = replacement;
				try { previous.close(); }
				catch (RuntimeException | Error failure) {
					// The replacement is already published and owns the active route.
					// Retain the old backend for a later close retry, but never report this
					// as a failed replacement: callers must not tear down the live owner.
					pendingBackendClose = previous;
				}
			} catch (RuntimeException | Error failure) {
				cacheOwner.cancelRetirement();
				throw failure;
			}
		} finally {
			lifecycle.writeLock().unlock();
			cacheOwner.dispatchAllNotifications();
		}
    }

    private void retryPendingBackendClose() {
        SqlUserBackend pending = pendingBackendClose;
        if (pending == null) return;
        pending.close();
        pendingBackendClose = null;
    }

    public void remove(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");
        cacheOwner.requireBlockingAllowed();
		try {
			userExclusiveAccess(uuid, () -> {
				cacheOwner.beginRemoval(uuid);
				try {
					flushInternal(uuid);
					backend.user(uuid).delete(backend.storageType());
					cacheOwner.remove(uuid);
				} catch (RuntimeException | Error failure) {
					cacheOwner.cancelRemoval(uuid);
					throw failure;
				}
				return null;
			});
		} finally { cacheOwner.dispatchNotifications(uuid); }
    }

    public SqlUserBackend backend() { return backend; }
    public boolean isClosed() { return closed; }
    public boolean isRetiring() { return retiring.get(); }

	/** Admit a native bulk operation for the life of its provider access. */
	public <T> T withStorageReadAdmission(Supplier<T> operation) {
		Objects.requireNonNull(operation, "operation");
		return storageAccess(operation);
	}

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
							cacheOwner.beginRetirement();
                            flushAllInternal();
							cacheOwner.clearAfterFlush();
							cacheOwner.shutdown();
							retryPendingBackendClose();
							backend.close();
                            closed = true;
                        }
					} finally {
						lifecycle.writeLock().unlock();
						// Disable is terminal even when its flush fails. These callbacks can
						// schedule UserDataChanged work after Bukkit has unloaded, so neither
						// a successful nor a failed final retirement may dispatch them.
						cacheOwner.discardAllNotifications();
					}
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
