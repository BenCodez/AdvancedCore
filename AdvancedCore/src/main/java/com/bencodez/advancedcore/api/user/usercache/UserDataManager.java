package com.bencodez.advancedcore.api.user.usercache;

import java.util.ArrayList;
import java.util.Objects;
import java.util.Map.Entry;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.advancedcore.api.player.UuidLookup;
import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.advancedcore.api.user.usercache.keys.UserDataKey;
import com.bencodez.advancedcore.api.user.usercache.keys.UserDataKeyBoolean;
import com.bencodez.advancedcore.api.user.usercache.keys.UserDataKeyInt;
import com.bencodez.advancedcore.api.user.usercache.keys.UserDataKeyString;
import com.bencodez.advancedcore.core.user.storage.SqlUserStorage;
import com.bencodez.advancedcore.core.user.storage.sql.SqlUserBackend;
import com.bencodez.advancedcore.core.user.runtime.SharedUserDataRuntime;
import com.bencodez.simpleapi.debug.DebugLevel;
import com.bencodez.simpleapi.array.ArrayUtils;
import com.bencodez.simpleapi.sql.data.DataValue;

import lombok.Getter;

public class UserDataManager {
	private static final AtomicInteger WORKER_SEQUENCE = new AtomicInteger();
	@Getter private ArrayList<UserDataKey> keys;
	@Getter private ArrayList<String> intColumns;
	@Getter private ArrayList<String> booleanColumns;
	@Getter private AdvancedCorePlugin plugin;
	@Getter private ScheduledExecutorService timer;
	@Getter private ConcurrentHashMap<UUID, UserDataCache> userDataCache;

	private volatile Consumer<UserDataCache> sharedCacheInitializer;
	private volatile Consumer<UUID> sharedCacheRemovalListener;
	private volatile SharedSqlRoute sharedSqlRoute;
	private volatile SharedUserDataRuntime sharedRuntime;
	/** A replacement is serialized with retirement so a native owner cannot be closed mid-flush. */
	private volatile CompletionStage<Void> sharedRuntimeReplacement;
	private volatile boolean sharedRuntimeReplacing;
	/** True from retirement admission until its native-owner callback has finished. */
	private volatile boolean sharedRuntimeRetiring;
	/** The one in-flight retirement, retained so lifecycle callers can await it safely. */
	private volatile CompletionStage<Void> sharedRuntimeRetirement;
	private final AtomicReference<Throwable> lastDeferredStorageFailure = new AtomicReference<>();
	private final Object sharedBindingAdmission = new Object();
	/** Explicit converter-only bypass for the legacy storage adapter. */
	private final ThreadLocal<Integer> storageMaintenanceDepth = ThreadLocal.withInitial(() -> 0);
	private boolean sharedBindingTransition;
	private int legacyBatches;
	private final ReentrantReadWriteLock cacheMapLifecycle = new ReentrantReadWriteLock(true);
	private final Set<UUID> sharedCachePopulations = ConcurrentHashMap.newKeySet();
	private final Set<UUID> completedSharedCachePopulations = ConcurrentHashMap.newKeySet();
	private final ConcurrentHashMap<UUID, SharedCachePopulationState> sharedCachePopulationStates = new ConcurrentHashMap<>();

	private static final class SharedCachePopulationState {
		private long generation;
		private int activePopulations;
	}

	private record SharedCachePopulation(UUID uuid, long generation, boolean deferred) {}

	/** Admit shared cache-map population while excluding whole-map clear/replace. */
	public final <T> T withCacheMapReadAdmission(Supplier<T> operation) {
		Objects.requireNonNull(operation, "operation");
		cacheMapLifecycle.readLock().lock();
		try { return operation.get(); }
		finally { cacheMapLifecycle.readLock().unlock(); }
	}

	private record SharedSqlRoute(SqlUserBackend backend, AdvancedCorePlugin.UserStorageOwner nativeOwner,
			Consumer<Runnable> lifecycleGate, BiConsumer<UUID, Runnable> gate,
			BiConsumer<UUID, Runnable> exclusiveGate) {
		SharedSqlRoute {
			Objects.requireNonNull(backend, "backend");
			Objects.requireNonNull(lifecycleGate, "lifecycleGate");
			Objects.requireNonNull(gate, "gate");
			Objects.requireNonNull(exclusiveGate, "exclusiveGate");
		}
	}

	/** One shared owner for the existing map, including caches created by legacy callers. */
	public final synchronized void bindSharedCacheInitializer(Consumer<UserDataCache> initializer) {
		Objects.requireNonNull(initializer, "initializer");
		if (sharedCacheInitializer != null && sharedCacheInitializer != initializer) {
			throw new IllegalStateException("User data manager already belongs to another shared runtime");
		}
		sharedCacheInitializer = initializer;
	}

	/** Register lifecycle cleanup for manager-driven cache eviction. */
	public final synchronized void bindSharedCacheRemovalListener(Consumer<UUID> listener) {
		Objects.requireNonNull(listener, "listener");
		if (sharedCacheRemovalListener != null && sharedCacheRemovalListener != listener) {
			throw new IllegalStateException("User data manager already belongs to another shared runtime");
		}
		sharedCacheRemovalListener = listener;
	}

	void initializeSharedCache(UserDataCache cache) {
		Consumer<UserDataCache> initializer = sharedCacheInitializer;
		if (initializer != null) {
			if (Thread.holdsLock(cache)) throw new IllegalStateException("Cannot attach shared storage while holding the cache monitor");
			initializer.accept(cache);
		}
	}

	/**
	 * Close legacy-batch admission before a shared route is published. This is a
	 * fail-fast transition: constructors never wait for an old provider write.
	 */
	public final void beginSharedBindingTransition() {
		synchronized (sharedBindingAdmission) {
			if (sharedBindingTransition) throw new IllegalStateException("Shared user binding is already in progress");
			if (sharedSqlRoute != null) throw new IllegalStateException("Shared SQL backend is already bound");
			sharedBindingTransition = true;
			if (legacyBatches != 0) {
				sharedBindingTransition = false;
				throw new IllegalStateException("Cannot attach shared storage during an active legacy batch");
			}
		}
	}

	public final void endSharedBindingTransition() {
		synchronized (sharedBindingAdmission) {
			sharedBindingTransition = false;
			sharedBindingAdmission.notifyAll();
		}
	}

	/** Admission used by UserDataCache before it selects the legacy provider. */
	final void beginLegacyCacheBatch() {
		synchronized (sharedBindingAdmission) {
			if (sharedBindingTransition || sharedSqlRoute != null) {
				throw new IllegalStateException("Legacy user storage is retired by the shared runtime");
			}
			legacyBatches++;
		}
	}

	final void endLegacyCacheBatch() {
		synchronized (sharedBindingAdmission) {
			if (legacyBatches <= 0) throw new IllegalStateException("Legacy user batch admission is unbalanced");
			legacyBatches--;
			sharedBindingAdmission.notifyAll();
		}
	}

	/** Publish backend and per-user lifecycle admission as one volatile immutable route. */
	public final synchronized void bindSharedSqlBackend(SqlUserBackend backend, BiConsumer<UUID, Runnable> gate) {
		bindSharedSqlBackend(backend, gate, gate);
	}

	/** Publish the shared read and exclusive per-user lifecycle admissions together. */
	public final synchronized void bindSharedSqlBackend(SqlUserBackend backend, BiConsumer<UUID, Runnable> gate,
			BiConsumer<UUID, Runnable> exclusiveGate) {
		bindSharedSqlBackend(backend, operation -> operation.run(), gate, exclusiveGate);
	}

	/** Publish native bulk admission with the per-user route in one immutable binding. */
	public final synchronized void bindSharedSqlBackend(SqlUserBackend backend, Consumer<Runnable> lifecycleGate,
			BiConsumer<UUID, Runnable> gate, BiConsumer<UUID, Runnable> exclusiveGate) {
		Objects.requireNonNull(lifecycleGate, "lifecycleGate");
		AdvancedCorePlugin.UserStorageOwner owner = plugin == null ? null : plugin.getNativeUserStorageOwner();
		if (owner != null && owner.storageType() != backend.storageType()) {
			throw new IllegalStateException("Native user storage owner does not match the shared backend");
		}
		sharedSqlRoute = new SharedSqlRoute(backend, owner, lifecycleGate, gate, exclusiveGate);
	}

	/** Compatibility overload for adapters that only need the global lifecycle gate. */
	public final synchronized void bindSharedSqlBackend(SqlUserBackend backend, Consumer<Runnable> gate) {
		Objects.requireNonNull(gate, "gate");
		bindSharedSqlBackend(backend, gate, (uuid, operation) -> gate.accept(operation),
				(uuid, operation) -> gate.accept(operation));
	}

	public final synchronized void unbindSharedSqlBackend(SqlUserBackend expected) {
		SharedSqlRoute route = sharedSqlRoute;
		if (route != null && route.backend() == expected) sharedSqlRoute = null;
	}

	public final boolean hasSharedSqlBackend() { return sharedSqlRoute != null; }

	/** Whether the active shared writer owns the requested physical store. */
	public final boolean usesSharedSqlStorage(com.bencodez.advancedcore.api.user.UserStorage storage) {
		SharedSqlRoute route = sharedSqlRoute;
		return route != null && route.backend().storageType() == storage;
	}

	/** Prefer the immutable active shared route over a newly reloaded option. */
	public final UserStorage effectiveStorageType(UserStorage configured) {
		SharedSqlRoute route = sharedSqlRoute;
		return route == null ? Objects.requireNonNull(configured, "configured") : route.backend().storageType();
	}

	/**
	 * The native owner captured with the current shared route. Public bulk APIs
	 * use this rather than separately reading a route type and mutable plugin
	 * owner field during an asynchronous replacement.
	 */
	public final AdvancedCorePlugin.UserStorageOwner sharedNativeUserStorageOwner() {
		SharedSqlRoute route = sharedSqlRoute;
		return route == null ? null : route.nativeOwner();
	}

	/**
	 * Resolve the native owner only after lifecycle read admission. A replacement
	 * takes the matching write admission before it can publish a route or close
	 * the previous provider. Main-thread callers fail instead of waiting on SQL.
	 */
	public final <T> T withSharedNativeUserStorage(
			java.util.function.Function<AdvancedCorePlugin.UserStorageOwner, T> operation) {
		Objects.requireNonNull(operation, "operation");
		SharedSqlRoute admission = sharedSqlRoute;
		if (admission == null || isStorageMaintenanceActive()) {
			return operation.apply(admission == null
					? (plugin == null ? null : plugin.getNativeUserStorageOwner()) : admission.nativeOwner());
		}
		if (Bukkit.getServer() != null && Bukkit.isPrimaryThread()) {
			throw new IllegalStateException("Shared user storage must run on a worker thread");
		}
		AtomicReference<T> result = new AtomicReference<>();
		admission.lifecycleGate().accept(() -> {
			SharedSqlRoute current = sharedSqlRoute;
			if (current == null || current.lifecycleGate() != admission.lifecycleGate()) {
				throw new IllegalStateException("Shared SQL lifecycle changed while waiting for bulk admission");
			}
			if (current.nativeOwner() == null) throw new IllegalStateException("Shared native user storage is unavailable");
			result.set(operation.apply(current.nativeOwner()));
		});
		return result.get();
	}

	/**
	 * Run an explicit storage-maintenance operation behind the shared runtime's
	 * write barrier. Calls made by the operation may target a non-current store
	 * only while this scoped flag is active; ordinary adapters remain protected
	 * from cross-store writes.
	 */
	public final void runStorageMaintenance(Runnable operation) {
		Objects.requireNonNull(operation, "operation");
		SharedUserDataRuntime runtime = sharedRuntime;
		if (runtime == null && sharedRuntimeRetiring) {
			throw new IllegalStateException("Shared user runtime retirement is still in progress");
		}
		Runnable guarded = () -> {
			int previous = storageMaintenanceDepth.get();
			storageMaintenanceDepth.set(previous + 1);
			try { operation.run(); }
			finally {
				if (previous == 0) storageMaintenanceDepth.remove();
				else storageMaintenanceDepth.set(previous);
			}
		};
		if (runtime == null) guarded.run();
		else runtime.runStorageMaintenance(guarded);
	}

	/**
	 * Remove one user through the shared runtime when available, preserving the
	 * flush-delete-retire ordering required to avoid recreating rows.
	 */
	public final void removeUserData(UUID uuid, UserStorage storage) {
		Objects.requireNonNull(uuid, "uuid");
		Objects.requireNonNull(storage, "storage");
		if (deferSharedStorageWork(() -> removeUserDataNow(uuid, storage))) return;
		removeUserDataNow(uuid, storage);
	}

	private void removeUserDataNow(UUID uuid, UserStorage storage) {
		SharedUserDataRuntime runtime = sharedRuntime;
		if (runtime != null && !runtime.isClosed()) {
			if (!runtime.backend().storageType().equals(storage)) {
				throw new IllegalStateException("Cannot access " + storage
						+ " user storage while the shared runtime owns " + runtime.backend().storageType());
			}
			runtime.remove(uuid);
			return;
		}
		withSharedSqlBackend(uuid, (sharedStorage, target) -> {
			if (!sharedStorage.equals(storage)) {
				throw new IllegalStateException("Cannot access " + storage
						+ " user storage while the shared runtime owns another store");
			}
			target.delete(sharedStorage);
			return null;
		});
	}

	/** True only inside a runtime-exclusive explicit storage maintenance action. */
	public final boolean isStorageMaintenanceActive() { return storageMaintenanceDepth.get() > 0; }

	/** Register the one runtime that owns the shared route for this manager. */
	public final synchronized void bindSharedRuntime(SharedUserDataRuntime runtime) {
		Objects.requireNonNull(runtime, "runtime");
		if (sharedRuntimeRetiring) {
			throw new IllegalStateException("Shared user runtime retirement is still in progress");
		}
		if (sharedRuntime != null && !sharedRuntime.isClosed()) {
			throw new IllegalStateException("Shared user runtime is already bound");
		}
		sharedRuntime = runtime;
	}

	public final boolean hasSharedRuntime() {
		SharedUserDataRuntime runtime = sharedRuntime;
		return runtime != null && !runtime.isClosed();
	}

	/**
	 * Cache-safe transaction entry for callers adding their own durable SQL
	 * record to a user mutation. Initial values are row prerequisites and may
	 * commit before queued cache work; put operation-specific mutation only in
	 * the callback. Blocking work must run off the game thread.
	 */
	public final <T> T withAtomicUserTransaction(UUID uuid, UserStorage storage,
			Map<String, DataValue> initialValues,
			SqlUserStorage.TransactionWork<T> work) {
		SharedUserDataRuntime runtime = sharedRuntime;
		if (runtime == null) throw new IllegalStateException("Shared user runtime is unavailable");
		return runtime.transaction(uuid, storage, initialValues, work);
	}

	/**
	 * Replace the active shared backend on this manager's worker. The runtime
	 * flushes and retires the old cache generation before publishing the new
	 * route, so callers may safely prepare a replacement without blocking a
	 * Bukkit thread. A concurrent shutdown wins safely: the replacement then
	 * completes exceptionally rather than touching a retiring provider.
	 */
	public final CompletionStage<Void> replaceSharedSqlBackendAsync(SqlUserBackend replacement) {
		return replaceSharedSqlBackendAsync(replacement, () -> {});
	}

	/**
	 * Asynchronously replace the backend and publish native-owner state before
	 * exposing completion to shutdown callers. The callback must be short and
	 * non-blocking; it runs on this manager's worker while the replacement route
	 * is still protected from a concurrent retirement admission.
	 */
	public final CompletionStage<Void> replaceSharedSqlBackendAsync(SqlUserBackend replacement, Runnable afterReplacement) {
		Objects.requireNonNull(replacement, "replacement");
		Objects.requireNonNull(afterReplacement, "afterReplacement");
		SharedUserDataRuntime runtime;
		CompletableFuture<Void> completion = new CompletableFuture<>();
		synchronized (this) {
			runtime = sharedRuntime;
			if (runtime == null || sharedRuntimeRetiring) {
				completion.completeExceptionally(new IllegalStateException(
						"Shared user storage is retiring and cannot be reloaded"));
				return completion;
			}
			if (sharedRuntimeReplacement != null && !sharedRuntimeReplacement.toCompletableFuture().isDone()) {
				completion.completeExceptionally(new IllegalStateException("Shared user storage reload is already in progress"));
				return completion;
			}
			sharedRuntimeReplacement = completion;
			sharedRuntimeReplacing = true;
		}
		try {
			timer.execute(() -> {
				Throwable replacementFailure = null;
				try {
					runtime.replaceBackend(replacement, afterReplacement);
				} catch (Throwable failure) {
					replacementFailure = failure;
				} finally {
					synchronized (UserDataManager.this) {
						if (sharedRuntimeReplacement == completion) sharedRuntimeReplacement = null;
						sharedRuntimeReplacing = false;
					}
				}
				if (replacementFailure == null) completion.complete(null);
				else completion.completeExceptionally(replacementFailure);
			});
		} catch (RuntimeException | Error failure) {
			completion.completeExceptionally(failure);
			synchronized (this) {
				if (sharedRuntimeReplacement == completion) sharedRuntimeReplacement = null;
				sharedRuntimeReplacing = false;
			}
		}
		return completion;
	}

	/**
	 * A native storage replacement must not race the shared runtime's final flush
	 * and owner cleanup. This remains true while asynchronous retirement is in
	 * progress even though the runtime is no longer available for new work.
	 */
	public final synchronized boolean hasSharedRuntimeLifecycle() {
		return sharedRuntimeRetiring || hasSharedRuntime();
	}

	/**
	 * Start shared cache retirement on the manager worker. The callback runs only
	 * after a successful flush/retirement, so the native owner never closes a
	 * database while a failed shared write remains queued for recovery.
	 */
	public final boolean closeSharedRuntimeAsync(Runnable afterRetirement) {
		return closeSharedRuntimeAsyncCompletion(afterRetirement) != null;
	}

	/**
	 * Start shared runtime retirement and expose its completion to the platform
	 * lifecycle. The database work remains on this manager's worker; callers must
	 * not replace or close its native owner before this stage completes.
	 *
	 * @return the active retirement stage, or {@code null} when no shared runtime
	 *         owns storage
	 */
	public final CompletionStage<Void> closeSharedRuntimeAsyncCompletion(Runnable afterRetirement) {
		Objects.requireNonNull(afterRetirement, "afterRetirement");
		SharedUserDataRuntime runtime;
		CompletableFuture<Void> completion;
		synchronized (this) {
			if (sharedRuntimeReplacing) {
				CompletionStage<Void> replacement = sharedRuntimeReplacement;
				// A failed replacement leaves the old runtime and native owner alive.
				// Shutdown must still retire that old owner; thenCompose would skip the
				// recursive retirement entirely on the exceptional path.
				return replacement.handle((ignored, replacementFailure) -> replacementFailure)
						.thenCompose(replacementFailure -> continueRetirementAfterReplacement(afterRetirement,
								replacementFailure));
			}
			runtime = sharedRuntime;
			// A second shutdown caller must not interpret an in-flight retirement as
			// "no runtime" and close the native provider underneath its final flush.
			if (runtime == null) return sharedRuntimeRetiring ? sharedRuntimeRetirement : null;
			sharedRuntime = null;
			sharedRuntimeRetiring = true;
			completion = new CompletableFuture<>();
			sharedRuntimeRetirement = completion;
		}
		runtime.closeAsync(timer).whenComplete((ignored, failure) -> {
			if (failure != null) {
				synchronized (this) {
					if (sharedRuntime == null) sharedRuntime = runtime;
					sharedRuntimeRetiring = false;
				}
				reportDeferredStorageFailure(failure);
				completion.completeExceptionally(failure);
				return;
			}
			Throwable cleanupFailure = null;
			try { afterRetirement.run(); }
			catch (RuntimeException | Error failureAfterRetirement) {
				cleanupFailure = failureAfterRetirement;
				reportDeferredStorageFailure(cleanupFailure);
			} finally { synchronized (this) { sharedRuntimeRetiring = false; } }
			if (cleanupFailure == null) completion.complete(null);
			else completion.completeExceptionally(cleanupFailure);
		});
		return completion;
	}

	private CompletionStage<Void> continueRetirementAfterReplacement(Runnable afterRetirement,
			Throwable replacementFailure) {
		if (replacementFailure != null) reportDeferredStorageFailure(replacementFailure);
		CompletionStage<Void> retirement = closeSharedRuntimeAsyncCompletion(afterRetirement);
		if (retirement == null) {
			if (replacementFailure == null) return CompletableFuture.completedFuture(null);
			return CompletableFuture.failedFuture(replacementFailure);
		}
		return retirement.handle((ignored, retirementFailure) -> {
			if (replacementFailure != null) {
				if (retirementFailure != null) replacementFailure.addSuppressed(retirementFailure);
				throw new java.util.concurrent.CompletionException(replacementFailure);
			}
			if (retirementFailure != null) throw new java.util.concurrent.CompletionException(retirementFailure);
			return null;
		});
	}

	/**
	 * Route one complete legacy SQL operation through lifecycle admission. The gate
	 * is captured only to enter the current runtime; the backend is resolved after
	 * admission so a concurrent replacement cannot leave this call using the closed
	 * provider that was current before it waited.
	 */
	public final <T> T withSharedSqlBackend(UUID uuid,
			BiFunction<UserStorage, SqlUserStorage, T> operation) {
		Objects.requireNonNull(uuid, "uuid");
		Objects.requireNonNull(operation, "operation");
		SharedSqlRoute admission = sharedSqlRoute;
		if (admission == null) throw new IllegalStateException("Shared SQL backend is not bound");
		if (Bukkit.getServer() != null && Bukkit.isPrimaryThread()) {
			throw new IllegalStateException("Shared user storage must run on a worker thread");
		}
		AtomicReference<T> result = new AtomicReference<>();
		admission.gate().accept(uuid, () -> {
			SharedSqlRoute current = sharedSqlRoute;
			if (current == null) throw new IllegalStateException("Shared SQL backend is not bound");
			if (current.gate() != admission.gate()) {
				throw new IllegalStateException("Shared SQL lifecycle changed while waiting for admission");
			}
			SqlUserBackend selected = current.backend();
			if (!selected.isOpen()) throw new IllegalStateException("Shared SQL backend is unavailable");
			result.set(operation.apply(selected.storageType(), selected.user(uuid)));
		});
		return result.get();
	}

	/** Run cache-facing work only while the requested physical store is still current. */
	public final void withSharedSqlStorage(UUID uuid, UserStorage expectedStorage, Runnable operation) {
		Objects.requireNonNull(expectedStorage, "expectedStorage");
		Objects.requireNonNull(operation, "operation");
		withSharedSqlBackend(uuid, (currentStorage, ignored) -> {
			if (currentStorage != expectedStorage) {
				throw new IllegalStateException("Cannot access " + expectedStorage
						+ " user storage while the shared runtime owns " + currentStorage);
			}
			operation.run();
			return null;
		});
	}

	/**
	 * Execute one complete legacy cache transition with exclusive per-user lifecycle
	 * admission. This prevents a writer that observed the old map entry from
	 * queuing work between its flush and removal.
	 */
	private void withSharedSqlBackendExclusive(UUID uuid, Runnable operation) {
		SharedSqlRoute admission = sharedSqlRoute;
		if (admission == null) {
			operation.run();
			return;
		}
		admission.exclusiveGate().accept(uuid, () -> {
			SharedSqlRoute current = sharedSqlRoute;
			if (current == null) throw new IllegalStateException("Shared SQL backend is not bound");
			if (current.exclusiveGate() != admission.exclusiveGate()) {
				throw new IllegalStateException("Shared SQL lifecycle changed while waiting for exclusive admission");
			}
			operation.run();
		});
	}

	/** Admit cache population through the same route that guards backend replacement and close. */
	private void withSharedCacheAdmission(UUID uuid, Runnable operation) {
		SharedSqlRoute admission = sharedSqlRoute;
		if (admission == null) {
			beginLegacyCacheBatch();
			try { operation.run(); }
			finally { endLegacyCacheBatch(); }
			return;
		}
		admission.gate().accept(uuid, () -> {
			SharedSqlRoute current = sharedSqlRoute;
			if (current == null) throw new IllegalStateException("Shared SQL backend is not bound");
			if (current.gate() != admission.gate()) {
				throw new IllegalStateException("Shared SQL lifecycle changed while waiting for cache admission");
			}
			operation.run();
		});
	}

	/**
	 * Atomically chooses the shared route or admits one complete legacy provider
	 * call. A binding transition cannot publish a replacement while the legacy
	 * call is active.
	 */
	public final <T> T withSharedSqlBackendOrLegacy(UUID uuid,
			BiFunction<UserStorage, SqlUserStorage, T> sharedOperation, Supplier<T> legacyOperation) {
		Objects.requireNonNull(uuid, "uuid");
		return withSharedSqlBackendOrLegacy(() -> uuid, sharedOperation, legacyOperation);
	}

	/**
	 * Supplier overload keeps legacy string identifiers opaque until a shared route
	 * is actually selected.
	 */
	public final <T> T withSharedSqlBackendOrLegacy(Supplier<UUID> uuid,
			BiFunction<UserStorage, SqlUserStorage, T> sharedOperation, Supplier<T> legacyOperation) {
		Objects.requireNonNull(uuid, "uuid");
		Objects.requireNonNull(sharedOperation, "sharedOperation");
		Objects.requireNonNull(legacyOperation, "legacyOperation");
		boolean legacy;
		synchronized (sharedBindingAdmission) {
			legacy = sharedSqlRoute == null;
			if (legacy) {
				if (sharedBindingTransition) {
					throw new IllegalStateException("Legacy user storage is retired by the shared runtime");
				}
				legacyBatches++;
			}
		}
		if (!legacy) return withSharedSqlBackend(Objects.requireNonNull(uuid.get(), "uuid"), sharedOperation);
		try {
			return legacyOperation.get();
		} finally {
			endLegacyCacheBatch();
		}
	}

	public UserDataManager(AdvancedCorePlugin plugin) {
		this.plugin = plugin;
		userDataCache = new ConcurrentHashMap<>();
		keys = new ArrayList<>();
		intColumns = new ArrayList<>();
		booleanColumns = new ArrayList<>();
		timer = Executors.newScheduledThreadPool(1, task -> {
			Thread worker = new Thread(task, "AdvancedCore-UserStorage-" + WORKER_SEQUENCE.incrementAndGet());
			// A JDBC driver may ignore interruption during final retirement. Keeping
			// this component-owned worker daemon prevents that hung call from retaining
			// the server JVM after the bounded lifecycle watchdog has reported it.
			worker.setDaemon(true);
			return worker;
		});
		loadKeys();
		timer.scheduleAtFixedRate(() -> {
			if (plugin != null && plugin.isEnabled()) clearNonNeededCachedUsers();
		}, 60 * 3, 60 * 60, TimeUnit.SECONDS);
	}

	/**
	 * Registers a user data key with the manager. This method is synchronized to ensure
	 * thread-safe registration when keys are added concurrently with SQL schema snapshot operations.
	 *
	 * @param userDataKey the key to register
	 */
	public synchronized void addKey(UserDataKey userDataKey) {
		keys.add(userDataKey);
		if (userDataKey instanceof UserDataKeyInt) intColumns.add(userDataKey.getKey());
		else if (userDataKey instanceof UserDataKeyBoolean) booleanColumns.add(userDataKey.getKey());
	}

	@Deprecated
	public void cacheUser(UUID uuid) {
		cacheUser(uuid, true);
	}

	private void cacheUser(UUID uuid, boolean traceDevelopmentCall) {
		if (deferSharedCachePopulation(uuid, traceDevelopmentCall)) return;
		cacheUserSynchronously(uuid, traceDevelopmentCall);
	}

	private void cacheUserSynchronously(UUID uuid, boolean traceDevelopmentCall) {
		cacheUserSynchronously(uuid, traceDevelopmentCall, true);
	}

	private void cacheUserSynchronously(UUID uuid, boolean traceDevelopmentCall, boolean retryAfterRetirement) {
		SharedCachePopulation population = hasSharedSqlBackend() ? beginSharedCachePopulation(uuid, false) : null;
		CacheRefresh refreshed = new CacheRefresh(uuid);
		RuntimeException runtimeFailure = null;
		Error errorFailure = null;
		boolean[] populated = { false };
		cacheMapLifecycle.readLock().lock();
		try {
			withSharedCacheAdmission(uuid, () -> {
				if (population == null || isCurrentSharedCachePopulation(population)) {
					cacheUserNow(uuid, traceDevelopmentCall, refreshed);
					populated[0] = true;
				}
			});
		}
		catch (RuntimeException failure) { runtimeFailure = failure; }
		catch (Error failure) { errorFailure = failure; }
		finally { cacheMapLifecycle.readLock().unlock(); }
		boolean current = population == null || finishSharedCachePopulation(population, runtimeFailure == null && errorFailure == null);
		if (runtimeFailure != null) {
			if (current) notifyCacheChangesAfterFailure(refreshed, runtimeFailure);
			throw runtimeFailure;
		}
		if (errorFailure != null) {
			if (current) notifyCacheChangesAfterFailure(refreshed, errorFailure);
			throw errorFailure;
		}
		if (!current || !populated[0]) {
			// A synchronous caller whose read was fenced before it began still expects
			// one attempt in the newly published generation. Do not retry a read that
			// already populated and was then retired by backend replacement: that would
			// republish a cache after the replacement's clear phase completed.
			if (retryAfterRetirement && population != null && !populated[0]) {
				cacheUserSynchronously(uuid, traceDevelopmentCall, false);
			}
			return;
		}
		notifyCacheChanges(refreshed);
	}

	private void cacheUserNow(UUID uuid, boolean traceDevelopmentCall, CacheRefresh refresh) {
		plugin.devDebug("Caching " + uuid.toString());
		if (traceDevelopmentCall && plugin.getOptions().getDebug().isDebug(DebugLevel.DEV)) {
			try { throw new Exception("caching here: " + uuid.toString()); }
			catch (Exception e) { e.printStackTrace(); }
		}
		if (userDataCache.containsKey(uuid)) {
			UserDataCache data = userDataCache.get(uuid);
			refresh.flushNotification = data.clearChangesForRefresh();
			refresh.changed = data.refreshInternal(false);
		} else {
			UserDataCache data = new UserDataCache(this, uuid);
			refresh.changed = data.refreshInternal(false);
			if (data.hasCache()) userDataCache.put(uuid, data);
		}
	}

	public void cacheUser(UUID uuid, String playerName) {
		if (playerName != null && !playerName.isEmpty() && !plugin.getOptions().isOnlineMode()) {
			uuid = UUID.fromString(UuidLookup.getInstance().getUUID(playerName));
		}
		cacheUser(uuid, false);
	}

	/**
	 * Compatibility path for synchronous Bukkit APIs: cache population still
	 * happens, but its SQL read and lifecycle admission run on the manager worker.
	 */
	private boolean deferSharedCachePopulation(UUID uuid, boolean traceDevelopmentCall) {
		if (!hasSharedSqlBackend() || Bukkit.getServer() == null || !Bukkit.isPrimaryThread()) return false;
		ensureSharedCachePlaceholder(uuid);
		SharedCachePopulation population = beginSharedCachePopulation(uuid, true);
		if (population == null) return true;
		try {
			timer.execute(() -> {
				CacheRefresh refreshed = new CacheRefresh(uuid);
				Throwable failure = null;
				boolean[] populated = { false };
				cacheMapLifecycle.readLock().lock();
				try {
					withSharedCacheAdmission(uuid, () -> {
						if (isCurrentSharedCachePopulation(population)) {
							cacheUserNow(uuid, traceDevelopmentCall, refreshed);
							populated[0] = true;
						}
					});
				}
				catch (RuntimeException | Error caught) { failure = caught; }
				finally { cacheMapLifecycle.readLock().unlock(); }
				Throwable deferredFailure = failure;
				boolean current = finishSharedCachePopulation(population, deferredFailure == null);
				if (!current) {
					if (deferredFailure != null) reportDeferredStorageFailure(deferredFailure);
					return;
				}
				if (deferredFailure != null) {
					reportDeferredStorageFailure(deferredFailure);
					dispatchSharedStorageNotification(() -> {
						try { notifyCacheChangesAfterFailure(refreshed, deferredFailure); }
						catch (RuntimeException | Error notificationFailure) {
							deferredFailure.addSuppressed(notificationFailure);
							reportDeferredStorageFailure(deferredFailure);
						}
					});
					return;
				}
				if (!populated[0]) return;
				dispatchSharedStorageNotification(() -> {
					try { notifyCacheChanges(refreshed); }
					catch (RuntimeException | Error notificationFailure) {
						reportDeferredStorageFailure(notificationFailure);
					}
				});
			});
		} catch (RejectedExecutionException rejected) {
			finishSharedCachePopulation(population, false);
			reportDeferredStorageFailure(rejected);
			throw rejected;
		}
		return true;
	}

	private SharedCachePopulation beginSharedCachePopulation(UUID uuid, boolean deferred) {
		SharedCachePopulationState state = sharedCachePopulationStates.computeIfAbsent(uuid,
				ignored -> new SharedCachePopulationState());
		synchronized (state) {
			if (deferred && !sharedCachePopulations.add(uuid)) return null;
			state.activePopulations++;
			return new SharedCachePopulation(uuid, state.generation, deferred);
		}
	}

	private boolean isCurrentSharedCachePopulation(SharedCachePopulation population) {
		SharedCachePopulationState state = sharedCachePopulationStates.get(population.uuid());
		if (state == null) return false;
		synchronized (state) { return state.generation == population.generation(); }
	}

	/**
	 * Complete a cache population only if it still belongs to the current cache
	 * generation. Retirement clears both externally visible marker sets before it
	 * advances that generation, so a queued old read cannot republish completion.
	 */
	private boolean finishSharedCachePopulation(SharedCachePopulation population, boolean success) {
		SharedCachePopulationState state = sharedCachePopulationStates.get(population.uuid());
		if (state == null) return false;
		boolean current;
		synchronized (state) {
			current = state.generation == population.generation();
			if (population.deferred() && current) sharedCachePopulations.remove(population.uuid());
			if (current) {
				if (success) completedSharedCachePopulations.add(population.uuid());
				else completedSharedCachePopulations.remove(population.uuid());
			}
			state.activePopulations--;
			if (state.activePopulations == 0 && !completedSharedCachePopulations.contains(population.uuid())) {
				sharedCachePopulationStates.remove(population.uuid(), state);
			}
		}
		return current;
	}

	/**
	 * Retire one manager-owned cache generation. Callers that flush or replace a
	 * runtime must use this instead of removing the public map entry directly.
	 * The expected instance prevents an old owner from retiring a replacement.
	 */
	public final boolean retireSharedCache(UUID uuid, UserDataCache expected) {
		Objects.requireNonNull(uuid, "uuid");
		SharedCachePopulationState state = sharedCachePopulationStates.computeIfAbsent(uuid,
				ignored -> new SharedCachePopulationState());
		synchronized (state) {
			if (userDataCache.get(uuid) != expected) return false;
			if (expected != null) userDataCache.remove(uuid, expected);
			sharedCachePopulations.remove(uuid);
			completedSharedCachePopulations.remove(uuid);
			state.generation++;
			if (state.activePopulations == 0) sharedCachePopulationStates.remove(uuid, state);
			return true;
		}
	}

	/** Publish an empty cache generation without storage access for synchronous callers. */
	private UserDataCache ensureSharedCachePlaceholder(UUID uuid) {
		cacheMapLifecycle.readLock().lock();
		try { return userDataCache.computeIfAbsent(uuid, ignored -> new UserDataCache(this, uuid)); }
		finally { cacheMapLifecycle.readLock().unlock(); }
	}

	private void notifyCacheChangesAfterFailure(CacheRefresh refresh, Throwable originalFailure) {
		try { notifyCacheChanges(refresh); }
		catch (RuntimeException | Error notificationFailure) { originalFailure.addSuppressed(notificationFailure); }
	}

	private void notifyCacheChanges(CacheRefresh refresh) {
		if (refresh == null) return;
		if (refresh.flushNotification != null) refresh.flushNotification.run();
		ArrayList<String> changed = refresh.changed;
		if (!changed.isEmpty()) {
			// The notification intentionally runs after lifecycle admission so listeners
			// can remove or replace this user's cache. Capture the identity with the
			// refresh result; the old cache may already have been retired here.
			AdvancedCoreUser user = plugin.getUserManager().getUser(refresh.uuid, false);
			plugin.getUserManager().onChange(user, ArrayUtils.convert(changed));
		}
	}

	private static final class CacheRefresh {
		private final UUID uuid;
		private ArrayList<String> changed = new ArrayList<>();
		private Runnable flushNotification;
		private CacheRefresh(UUID uuid) { this.uuid = uuid; }
	}

	public void cacheUserIfNeeded(UUID uuid) { if (!userDataCache.containsKey(uuid)) cacheUser(uuid); }

	/**
	 * Shared SQL writes are worker-only. Existing synchronous Bukkit clear/remove
	 * entry points hand the complete flush-and-remove sequence to the cache worker.
	 */
	public final boolean deferSharedStorageWork(Runnable task) {
		Objects.requireNonNull(task, "task");
		if (!hasSharedSqlBackend() || Bukkit.getServer() == null || !Bukkit.isPrimaryThread()) return false;
		try {
			timer.execute(() -> {
				lastDeferredStorageFailure.set(null);
				try {
					task.run();
				} catch (RuntimeException | Error failure) {
					reportDeferredStorageFailure(failure);
					throw failure;
				}
			});
		}
		catch (RejectedExecutionException rejected) {
			reportDeferredStorageFailure(rejected);
			throw rejected;
		}
		return true;
	}

	/**
	 * Move a legacy synchronous SQL read off the Bukkit thread and return its
	 * result on the platform thread.  The boolean tells callers whether their
	 * synchronous path was deferred; a value-returning API must never substitute
	 * an empty or stale value while its shared-store read is pending.
	 */
	public final <T> boolean deferSharedStorageResult(Supplier<T> storageWork, Consumer<T> success,
			Consumer<Throwable> failure) {
		return deferSharedStorageResult(storageWork, success, failure, null);
	}

	/** Return player-facing completions through that entity's owning scheduler. */
	public final <T> boolean deferSharedStorageResult(Supplier<T> storageWork, Consumer<T> success,
			Consumer<Throwable> failure, org.bukkit.entity.Entity callbackOwner) {
		Objects.requireNonNull(storageWork, "storageWork");
		Objects.requireNonNull(success, "success");
		Objects.requireNonNull(failure, "failure");
		if (!mustDeferSharedStorageAccess()) return false;
		return submitSharedStorageResult(storageWork, success, failure, callbackOwner);
	}

	/**
	 * Submit a shared-storage read after a platform task has captured any Bukkit
	 * identity evidence. Folia's global scheduler is not Bukkit's primary thread,
	 * so it must not synchronously enter the shared store there.
	 */
	public final <T> boolean deferSharedStorageResultFromPlatform(Supplier<T> storageWork, Consumer<T> success,
			Consumer<Throwable> failure) {
		Objects.requireNonNull(storageWork, "storageWork");
		Objects.requireNonNull(success, "success");
		Objects.requireNonNull(failure, "failure");
		if (!hasSharedSqlBackend()) return false;
		return submitSharedStorageResult(storageWork, success, failure, null);
	}

	private <T> boolean submitSharedStorageResult(Supplier<T> storageWork, Consumer<T> success,
			Consumer<Throwable> failure, org.bukkit.entity.Entity callbackOwner) {
		try {
			timer.execute(() -> {
				T result = null;
				Throwable problem = null;
				try { result = storageWork.get(); }
				catch (RuntimeException | Error caught) {
					problem = caught;
					reportDeferredStorageFailure(caught);
				}
				T completed = result;
				Throwable completedFailure = problem;
				java.util.concurrent.atomic.AtomicBoolean completionClaimed = new java.util.concurrent.atomic.AtomicBoolean();
				try {
					dispatchSharedStorageNotification(() -> {
						if (!completionClaimed.compareAndSet(false, true)) return;
						if (completedFailure == null) success.accept(completed);
						else failure.accept(completedFailure);
					}, callbackOwner);
				} catch (RuntimeException rejected) {
					if (completionClaimed.compareAndSet(false, true)) {
						reportDeferredStorageFailure(rejected);
					} else throw rejected;
				}
			});
		} catch (RejectedExecutionException rejected) {
			reportDeferredStorageFailure(rejected);
			throw rejected;
		}
		return true;
	}

	/**
	 * Return a deferred storage completion to Bukkit/Folia's safe scheduler. If the
	 * scheduler has stopped, the manager records the terminal delivery failure;
	 * it never invokes a callback from the storage worker as a fallback.
	 */
	public final void dispatchSharedStorageNotification(Runnable notification) {
		dispatchSharedStorageNotification(notification, null);
	}

	private void dispatchSharedStorageNotification(Runnable notification, org.bukkit.entity.Entity callbackOwner) {
		Objects.requireNonNull(notification, "notification");
		if (plugin == null || !plugin.isEnabled()) {
			RejectedExecutionException rejected = new RejectedExecutionException(
					"Shared storage notification rejected because the plugin is disabled");
			reportDeferredStorageFailure(rejected);
			throw rejected;
		}
		if (Bukkit.getServer() == null || Bukkit.isPrimaryThread()) notification.run();
		else if (callbackOwner != null) plugin.getBukkitScheduler().runTask(plugin, notification, callbackOwner);
		else plugin.getBukkitScheduler().runTask(plugin, notification);
	}

	private void reportDeferredStorageFailure(Throwable failure) {
		lastDeferredStorageFailure.set(failure);
		if (plugin != null && plugin.getLogger() != null) {
			plugin.getLogger().log(java.util.logging.Level.SEVERE, "Deferred user-cache cleanup failed", failure);
		}
	}

	/** Retain a cache reconciliation failure after its SQL transaction committed. */
	public final void recordSharedStorageFailure(Throwable failure) {
		reportDeferredStorageFailure(Objects.requireNonNull(failure, "failure"));
	}

	/** Last asynchronous cache-cleanup failure, retained for diagnosis and recovery. */
	public Throwable getLastDeferredStorageFailure() { return lastDeferredStorageFailure.get(); }

	public void clearCache() {
		if (deferSharedStorageWork(this::clearCacheNow)) return;
		clearCacheNow();
	}

	/** Complete only after all cache entries have been flushed and retired. */
	public CompletionStage<Void> clearCacheAsyncCompletion() {
		CompletableFuture<Void> completion = new CompletableFuture<>();
		if (!mustDeferSharedStorageAccess()) {
			try {
				clearCacheNow();
				completion.complete(null);
			} catch (RuntimeException | Error failure) {
				completion.completeExceptionally(failure);
			}
			return completion;
		}
		try {
			timer.execute(() -> {
				lastDeferredStorageFailure.set(null);
				try {
					clearCacheNow();
					completion.complete(null);
				} catch (RuntimeException | Error failure) {
					reportDeferredStorageFailure(failure);
					completion.completeExceptionally(failure);
				}
			});
		} catch (RejectedExecutionException rejected) {
			reportDeferredStorageFailure(rejected);
			completion.completeExceptionally(rejected);
		}
		return completion;
	}

	private void clearCacheNow() {
		// Do not retain this map lock while flushing through a shared runtime gate.
		// A population already admitted by that runtime needs the map read lock to
		// publish, while a queued lifecycle writer would otherwise block this flush.
		// Mark the current generation as retiring under the map lock, then perform
		// its storage work without that lock and finally detach only those instances.
		java.util.HashMap<UUID, UserDataCache> retiring = new java.util.HashMap<>();
		cacheMapLifecycle.writeLock().lock();
		try {
			plugin.debug("Clearing cache: " + userDataCache.keySet().size());
			retiring.putAll(userDataCache);
			for (UserDataCache cache : retiring.values()) cache.beginRemoval();
		} finally { cacheMapLifecycle.writeLock().unlock(); }
		try {
			for (Entry<UUID, UserDataCache> entry : retiring.entrySet()) {
				UUID uuid = entry.getKey();
				UserDataCache cache = entry.getValue();
				// The map write lock only protects publication.  Retiring each cache
				// must also exclude the normal per-user read admission used by
				// addChange(), otherwise a queued setter can observe this mapped cache
				// after beginRemoval() and have its mutation silently discarded.
				withSharedSqlBackendExclusive(uuid, () -> clearCacheExclusively(uuid, cache));
			}
		} catch (RuntimeException | Error failure) {
			// All not-yet-detached caches were marked before flushing began. Reopen
			// each surviving cache so one user's failed write cannot discard later
			// updates for another cache that has not been flushed yet.
			for (UserDataCache cache : retiring.values()) cache.cancelRemoval();
			throw failure;
		}
	}

	private void clearCacheExclusively(UUID uuid, UserDataCache cache) {
		cache.clearCache();
		cache.dump();
		boolean removed = retireSharedCache(uuid, cache);
		Consumer<UUID> listener = sharedCacheRemovalListener;
		if (removed && listener != null) listener.accept(uuid);
	}

	public void clearCacheBasic() {
		if (plugin.getStorageType().equals(UserStorage.MYSQL)) plugin.getMysql().clearCacheBasic();
	}

	public void clearNonNeededCachedUsers() {
		plugin.devDebug("Clearing cache for non online players (if any)");
		ArrayList<UUID> onlineUUIDS = new ArrayList<>();
		for (Player p : Bukkit.getOnlinePlayers()) onlineUUIDS.add(p.getUniqueId());
		int removed = 0;
		for (UUID uuid : userDataCache.keySet()) {
			if (!onlineUUIDS.contains(uuid)) { removeCache(uuid, null); removed++; }
		}
		if (removed > 0) plugin.devDebug("Removed " + removed + " cached users who are no longer online");
	}

	public boolean containsKey(UUID fromString) { return userDataCache.containsKey(fromString); }
	/** Return an already-published cache snapshot without creating or populating one. */
	public UserDataCache getPublishedCache(UUID uuid) {
		UserDataCache cache = userDataCache.get(uuid);
		return cache != null && cache.hasPublishedStorageSnapshot() ? cache : null;
	}
	public UserDataCache getCache(UUID uuid) {
		if (hasSharedSqlBackend() && Bukkit.getServer() != null && Bukkit.isPrimaryThread()) {
			UserDataCache cache = userDataCache.get(uuid);
			if (cache == null) cache = ensureSharedCachePlaceholder(uuid);
			if (!completedSharedCachePopulations.contains(uuid)) cacheUser(uuid, false);
			return cache;
		}
		cacheUserIfNeeded(uuid);
		return userDataCache.get(uuid);
	}
	public boolean isBoolean(String str) { return booleanColumns.contains(str); }
	public boolean isCached(UUID uuid) { return userDataCache.containsKey(uuid) && userDataCache.get(uuid).hasCache(); }
	public boolean isInt(String str) { return intColumns.contains(str); }
	public boolean mustDeferSharedStorageAccess() {
		return hasSharedSqlBackend() && Bukkit.getServer() != null && Bukkit.isPrimaryThread();
	}

	private void loadKeys() {
		addKey(new UserDataKeyString("PlayerName").setColumnType("VARCHAR(30)"));
		addKey(new UserDataKeyString("OfflineRewards").setColumnType("MEDIUMTEXT"));
		addKey(new UserDataKeyString("UnClaimedChoices"));
		addKey(new UserDataKeyString("TimedRewards"));
		addKey(new UserDataKeyString("LastOnline").setColumnType("VARCHAR(20)"));
		addKey(new UserDataKeyString("InputMethod"));
		addKey(new UserDataKeyString("ChoicePreference"));
		addKey(new UserDataKeyBoolean("CheckWorld"));
		addKey(new UserDataKeyBoolean("isBedrock"));
	}

	public void removeCache(UUID uuid, String playerName) {
		UUID resolved = uuid;
		if (playerName != null && !playerName.isEmpty() && !plugin.getOptions().isOnlineMode()) {
			resolved = UUID.fromString(UuidLookup.getInstance().getUUID(playerName));
		}
		UUID target = resolved;
		if (deferSharedStorageWork(() -> removeCacheNow(target))) return;
		removeCacheNow(target);
	}

	private void removeCacheNow(UUID uuid) {
		withSharedSqlBackendExclusive(uuid, () -> removeCacheExclusively(uuid, sharedSqlRoute != null));
	}

	private void removeCacheExclusively(UUID uuid, boolean shared) {
		UserDataCache cache = getCache(uuid);
		if (cache != null) {
			cache.beginRemoval();
			try {
				cache.clearCache();
				if (shared) cache.retireAfterSharedFlush();
			} catch (RuntimeException | Error failure) {
				// The failed cache is still mapped with its queued write retained. It
				// must resume accepting mutations so a later cleanup can retry safely.
				cache.cancelRemoval();
				throw failure;
			}
		}
		boolean removed = retireSharedCache(uuid, cache);
		Consumer<UUID> listener = sharedCacheRemovalListener;
		if (removed && listener != null) listener.accept(uuid);
	}

	public void updateCacheOnline() {
		for (Player p : Bukkit.getOnlinePlayers()) if (isCached(p.getUniqueId())) cacheUser(p.getUniqueId());
	}
}
