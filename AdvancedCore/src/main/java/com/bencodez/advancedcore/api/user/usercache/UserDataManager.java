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
import java.util.concurrent.atomic.AtomicLong;
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
	/** Exact private executor identity; Spigot reports every thread as primary after stop. */
	private volatile Thread storageWorker;
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
	private int sharedIntegerAdmissions;
	private boolean sharedIntegerClosing;
	private boolean sharedIntegerOwnerReplacement;
	private CompletableFuture<Void> sharedIntegerDrain = CompletableFuture.completedFuture(null);
	private CompletableFuture<Void> sharedIntegerRetirement;
	private final ThreadLocal<Boolean> sharedIntegerCompletion = new ThreadLocal<>();
	private final ThreadLocal<Boolean> sharedNotificationCallback = new ThreadLocal<>();
	private final ThreadLocal<Integer> sharedIntegerSynchronousDepth = ThreadLocal.withInitial(() -> 0);
	private final ThreadLocal<UUID> sharedIntegerWorker = new ThreadLocal<>();
	private final Map<UUID, Integer> sharedIntegerQueuedUsers = new java.util.HashMap<>();
	private final Map<UUID, Integer> sharedIntegerActiveUsers = new java.util.HashMap<>();
	private record SharedReplayLease(UUID uuid, String identity) { }
	private static final class SharedReplayLeaseState {
		final SharedUserDataRuntime runtime; int attempts; int checkpointReservations; boolean committed; boolean checkpointed;
		SharedReplayLeaseState(SharedUserDataRuntime runtime) { this.runtime = runtime; }
	}
	private final Map<SharedReplayLease, SharedReplayLeaseState> sharedReplayLeases = new java.util.HashMap<>();
	/** An observation of uncertain work, never a transaction settlement. */
	public record IndeterminateReplay(UUID user, String identity, boolean checkpoint) { }
	private final Map<MutationReceipt<?>, IndeterminateReplay> indeterminateReplays = new java.util.HashMap<>();
	/** Snapshot only: these operations and their owner admissions remain pending. */
	public synchronized java.util.List<IndeterminateReplay> indeterminateSharedReplays() {
		return java.util.List.copyOf(indeterminateReplays.values());
	}
	private final Set<UUID> sharedIntegerCompletingUsers = new java.util.HashSet<>();
	private final Map<UUID, Set<MutationReceipt<?>>> sharedCallbackReceipts = new java.util.HashMap<>();
	private record DeferredSharedInteger(Runnable action, Consumer<Throwable> reject,
			MutationReceipt<?> receipt) { }
	private final Map<UUID, java.util.ArrayDeque<DeferredSharedInteger>> sharedIntegerDeferred = new java.util.HashMap<>();
	private final AtomicReference<Throwable> lastDeferredStorageFailure = new AtomicReference<>();
	private final AtomicLong sharedNotificationGeneration = new AtomicLong();
	private volatile boolean sharedNotificationsClosed;
	private final Object sharedBindingAdmission = new Object();
	/** Explicit converter-only bypass for the legacy storage adapter. */
	private final ThreadLocal<Integer> storageMaintenanceDepth = ThreadLocal.withInitial(() -> 0);
	private boolean sharedBindingTransition;
	private int legacyBatches;
	private final ReentrantReadWriteLock cacheMapLifecycle = new ReentrantReadWriteLock(true);
	private final Set<UUID> sharedCachePopulations = ConcurrentHashMap.newKeySet();
	private final Set<UUID> completedSharedCachePopulations = ConcurrentHashMap.newKeySet();
	private final ConcurrentHashMap<UUID, SharedCachePopulationState> sharedCachePopulationStates = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<UUID, LegacyWriteState> legacyWriteStates = new ConcurrentHashMap<>();
	private final Object legacyMutationAdmission = new Object();
	private final java.util.concurrent.locks.ReentrantLock[] legacyMutationStripes =
			java.util.stream.IntStream.range(0, 64).mapToObj(ignored -> new java.util.concurrent.locks.ReentrantLock())
					.toArray(java.util.concurrent.locks.ReentrantLock[]::new);
	private final Map<UUID, java.util.ArrayDeque<PendingLegacyIntegerMutation>> pendingLegacyIntegers = new java.util.HashMap<>();
	private final Map<UUID, Set<PendingLegacyIntegerMutation>> legacyCallbackReceipts = new java.util.HashMap<>();
	private final Set<UUID> legacyIntegerCacheRetirements = new java.util.HashSet<>();
	private final ThreadLocal<Boolean> legacyIntegerNotification = new ThreadLocal<>();
	private final ThreadLocal<Boolean> legacyIntegerCompletion = new ThreadLocal<>();
	private int pendingLegacyIntegerCount;
	private static final int MAX_PENDING_LEGACY_INTEGERS = 256;
	/* Admissions bound the number of outstanding callback deliveries. Never run
	 * rejected callbacks on the SQL worker: retain their admissions instead. */
	private final java.util.concurrent.ThreadPoolExecutor mutationCallbacks = new java.util.concurrent.ThreadPoolExecutor(
			4, 4, 1, TimeUnit.SECONDS, new java.util.concurrent.ArrayBlockingQueue<>(768), task -> {
				Thread thread = new Thread(task, "AdvancedCore-mutation-completion-" + WORKER_SEQUENCE.incrementAndGet());
				thread.setDaemon(true); return thread;
			});
	private static final int MAX_LEGACY_MUTATION_ATTEMPTS = 200;
	private java.util.concurrent.locks.ReentrantLock legacyMutationStripe(UUID uuid) {
		return legacyMutationStripes[(uuid.hashCode() & Integer.MAX_VALUE) % legacyMutationStripes.length];
	}
	/** The accepted mutation owns its outcome; observers cannot cancel or settle it. */
	private static class GuardedMutationFuture<T> extends CompletableFuture<T> {
		private final UserDataManager owner;
		GuardedMutationFuture(UserDataManager owner) { this.owner = owner; }
		private void rejectCompletionThreadWait() {
			if (!isDone() && owner != null && (Boolean.TRUE.equals(owner.sharedIntegerCompletion.get())
					|| Boolean.TRUE.equals(owner.legacyIntegerCompletion.get())))
				throw new IllegalStateException("A completion callback cannot await its own lifecycle drain");
		}
		@Override public T join() { rejectCompletionThreadWait(); return super.join(); }
		@Override public T get() throws InterruptedException, java.util.concurrent.ExecutionException { rejectCompletionThreadWait(); return super.get(); }
		@Override public T get(long timeout, TimeUnit unit) throws InterruptedException, java.util.concurrent.ExecutionException, java.util.concurrent.TimeoutException {
			if (timeout > 0) rejectCompletionThreadWait(); return super.get(timeout, unit);
		}
		@Override public <U> CompletableFuture<U> newIncompleteFuture() { return new GuardedMutationFuture<>(owner); }
		@Override public CompletionStage<T> minimalCompletionStage() {
			GuardedMutationFuture<T> view = new GuardedMutationFuture<>(owner);
			super.whenComplete((value, failure) -> { if (failure == null) view.complete(value); else view.completeExceptionally(failure); });
			return view;
		}
	}
	private static final class MutationReceipt<T> extends GuardedMutationFuture<T> {
		private final GuardedMutationFuture<Void> finalized;
		private final UserDataManager sharedOwner;
		private MutationReceipt(UserDataManager owner, boolean shared) { super(null); this.sharedOwner = shared ? owner : null; this.finalized = new GuardedMutationFuture<>(owner); }
		private MutationReceipt(UserDataManager sharedOwner) { this(sharedOwner, true); }
		@Override public boolean cancel(boolean mayInterruptIfRunning) { return false; }
		@Override public boolean complete(T value) { return false; }
		@Override public boolean completeExceptionally(Throwable failure) { return false; }
		@Override public void obtrudeValue(T value) { throw new UnsupportedOperationException("read-only receipt"); }
		@Override public void obtrudeException(Throwable failure) { throw new UnsupportedOperationException("read-only receipt"); }
		@Override public CompletableFuture<T> completeAsync(java.util.function.Supplier<? extends T> supplier) { throw new UnsupportedOperationException("read-only receipt"); }
		@Override public CompletableFuture<T> completeAsync(java.util.function.Supplier<? extends T> supplier, java.util.concurrent.Executor executor) { throw new UnsupportedOperationException("read-only receipt"); }
		@Override public CompletableFuture<T> orTimeout(long timeout, TimeUnit unit) { throw new UnsupportedOperationException("read-only receipt"); }
		@Override public CompletableFuture<T> completeOnTimeout(T value, long timeout, TimeUnit unit) { throw new UnsupportedOperationException("read-only receipt"); }
		private void succeed(T value) { super.complete(value); }
		private void fail(Throwable failure) { super.completeExceptionally(failure); }
	}
	private static final class PendingLegacyIntegerMutation {
		final UUID uuid;
		final AdvancedCorePlugin.UserStorageOwner owner;
		final Map<String, Integer> values;
		final UserDataCache.IntegerMutation operation;
		final Consumer<Set<String>> notification;
		final MutationReceipt<UserDataCache.IntegerMutationResult> completion;
		int attempts;
		PendingLegacyIntegerMutation(UserDataManager manager, UUID uuid, AdvancedCorePlugin.UserStorageOwner owner, Map<String, Integer> values,
				UserDataCache.IntegerMutation operation, Consumer<Set<String>> notification) {
			this.uuid = uuid; this.owner = owner; this.values = Map.copyOf(values);
			this.operation = operation; this.notification = notification;
			this.completion = new MutationReceipt<>(manager, false);
		}
	}
	private final Object legacyRetirementAdmission = new Object();
	private int legacyDirectAdmissions;
	private boolean legacyRetirementActive;
	private boolean legacyRetirementRequested;
	private boolean legacyIntegerOwnerReplacement;
	private boolean legacyIntegerShutdown;
	private int activeLegacyIntegerApplications;
	private CompletableFuture<Void> legacyIntegerApplicationDrain = CompletableFuture.completedFuture(null);
	private final ThreadLocal<Boolean> legacyIntegerOwnerReplacementThread = new ThreadLocal<>();
	private CompletableFuture<Void> legacyDirectDrain = CompletableFuture.completedFuture(null);
	private CompletableFuture<Void> legacyRetirementWait;
	private Throwable legacyDirectFailure;
	private long legacyDirectSequence;
	private final java.util.Map<String, java.util.Map<String, Long>> legacyDirectSuccesses = new java.util.HashMap<>();
	private final java.util.Map<String, Integer> legacyDirectAdmissionsByUser = new java.util.HashMap<>();
	private final java.util.Map<String, java.util.Map<String, Long>> legacyUnrecoveredWrites = new java.util.HashMap<>();
	private static final class LegacyWriteState {
		private long version; private int readers; private int writers;
		private final java.util.IdentityHashMap<Object, long[]> confirmedTransitions = new java.util.IdentityHashMap<>();
		private final java.util.IdentityHashMap<Object, Map<String, Long>> confirmedKeyVersions = new java.util.IdentityHashMap<>();
	}
	static final class LegacyRead {
		private final UUID uuid;
		private final LegacyWriteState state;
		private long version;
		private int nestedWriters;
		private int pendingOverlapWriters;
		private boolean pendingOverlapAuthorized;
		private boolean active = true;
		private final Set<String> confirmedOverlap = new java.util.HashSet<>();
		private final Set<String> filteredOverlap = new java.util.HashSet<>();
		private long confirmedOverlapVersion = -1;
		private long confirmedClaimSerial;
		private long lastConfirmedClaimVersion = -1;
		private Object historyProvider;
		private Object historySource;
		private long historyVersion = -1;
		private long historyTransitions;
		private int historyMode;
		private final boolean writing;
		private final LegacyDirectWrite callback;
		private LegacyRead(UUID uuid, LegacyWriteState state, long version, boolean writing, LegacyDirectWrite callback) {
			this.uuid = uuid; this.state = state; this.version = version; this.writing = writing; this.callback = callback;
		}
	}
	public static final class LegacyDirectWrite {
		private final UUID uuid;
		private final String sqliteRow;
		private final UserDataCache cache;
		private final LegacyWriteState state;
		private final Set<String> keys;
		private final long sequence;
		private volatile long cacheVersion;
		private volatile UserDataCache.DirectCachePublication priorPublication;
		private final java.util.concurrent.atomic.AtomicBoolean ended = new java.util.concurrent.atomic.AtomicBoolean();
		private final java.util.concurrent.atomic.AtomicBoolean failureRecorded = new java.util.concurrent.atomic.AtomicBoolean();
		private final java.util.concurrent.atomic.AtomicBoolean successful = new java.util.concurrent.atomic.AtomicBoolean();
		private final java.util.Map<String, Long> sqliteCommitPrevious = new java.util.HashMap<>();
		private Boolean sqliteInsertStatement;
		private Boolean sqliteInsertChangesPrimary;
		private Boolean sqliteUpdateChangesPrimary;
		private LegacyDirectWrite redirected;
		private final java.util.Map<String, LegacyDirectWrite> destinationGuards = new java.util.HashMap<>();
		private final java.util.Set<String> possibleDestinations = new java.util.HashSet<>();
		private boolean borrowedBatch;
		private LegacyRead nestedRead;
		private LegacyRead overlapRead;
		private final Set<String> confirmedKeys = new java.util.HashSet<>();
		private Object confirmedProvider;
		private boolean confirmedMysqlFold;
		private com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL capturedNestedMysql;
		private com.bencodez.advancedcore.api.user.userstorage.sql.UserTable capturedNestedSqlite;
		private LegacyDirectWrite(UUID uuid, String sqliteRow, UserDataCache cache, LegacyWriteState state, Set<String> keys, long sequence, long cacheVersion) {
			this.uuid = uuid; this.sqliteRow = sqliteRow; this.cache = cache; this.state = state; this.keys = new java.util.HashSet<>(keys); this.sequence = sequence; this.cacheVersion = cacheVersion;
		}
		private String identity() { return sqliteRow == null ? "UUID:" + uuid : "SQLITE:" + sqliteRow; }
	}
	/** Live storage identities, maintained from platform join/quit events. */
	// Generation-stamped offline tombstones prevent an older queued platform
	// snapshot from erasing a newer quit and letting a later stale snapshot
	// resurrect that session.
	private final ConcurrentHashMap<UUID, OnlineSessionState> onlineUserSessions = new ConcurrentHashMap<>();
	private final AtomicLong onlineSessionGeneration = new AtomicLong();
	private final Object[] onlineSessionLocks = createOnlineSessionLocks();
	private record OnlineSessionState(boolean online, long generation) {}

	private static Object[] createOnlineSessionLocks() {
		Object[] locks = new Object[64];
		java.util.Arrays.setAll(locks, ignored -> new Object());
		return locks;
	}

	private Object onlineSessionLock(UUID uuid) {
		return onlineSessionLocks[(uuid.hashCode() & Integer.MAX_VALUE) % onlineSessionLocks.length];
	}

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
		synchronized (sharedBindingAdmission) {
			if (legacyBatches != 0) throw new IllegalStateException("Cannot attach shared storage during an active legacy batch");
			sharedSqlRoute = new SharedSqlRoute(backend, owner, lifecycleGate, gate, exclusiveGate);
		}
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
	public final boolean hasAuthoritativeSharedRowReads() {
		SharedSqlRoute route = sharedSqlRoute;
		return route != null && route.backend().hasAuthoritativeRowReads();
	}

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
		if (isPlatformOwnedThread()) {
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
		if (sharedIntegerAdmissions != 0) throw new IllegalStateException("Shared integer mutations have not settled");
		sharedIntegerClosing = false;
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
			if (sharedIntegerClosing || sharedIntegerOwnerReplacement || sharedIntegerAdmissions != 0) {
				completion.completeExceptionally(new IllegalStateException("Shared integer mutations have not settled"));
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
		if (Boolean.TRUE.equals(sharedIntegerCompletion.get()) || Boolean.TRUE.equals(sharedNotificationCallback.get()) || sharedIntegerSynchronousDepth.get() != 0)
			return CompletableFuture.failedFuture(new IllegalStateException("Shared retirement cannot await its own mutation completion"));
		SharedUserDataRuntime runtime;
		CompletableFuture<Void> completion;
		synchronized (this) {
			if (sharedIntegerOwnerReplacement)
				return CompletableFuture.failedFuture(new IllegalStateException("Shared storage owner replacement is active"));
			if (sharedIntegerRetirement != null) return sharedIntegerRetirement.copy();
			if (sharedIntegerAdmissions != 0) {
				sharedIntegerClosing = true;
				CompletableFuture<Void> waiting = new CompletableFuture<Void>();
				sharedIntegerRetirement = waiting;
				sharedIntegerDrain.whenComplete((ignored, failure) -> {
					try {
						timer.execute(() -> {
							try {
								CompletionStage<Void> retirement;
								synchronized (UserDataManager.this) {
									sharedIntegerRetirement = null;
									retirement = closeSharedRuntimeAsyncCompletion(afterRetirement);
									}
								if (retirement == null) waiting.completeExceptionally(new IllegalStateException("Shared runtime disappeared during retirement"));
								else retirement.whenComplete((done, error) -> {
									if (error == null) waiting.complete(null); else waiting.completeExceptionally(error);
								});
							} catch (RuntimeException | Error error) {
								synchronized (UserDataManager.this) {
									if (!sharedRuntimeRetiring && sharedRuntime != null) sharedIntegerClosing = false;
								}
								waiting.completeExceptionally(error);
							}
						});
					} catch (RuntimeException | Error error) {
						synchronized (UserDataManager.this) {
							if (sharedIntegerRetirement == waiting) sharedIntegerRetirement = null;
							sharedIntegerClosing = false;
						}
						waiting.completeExceptionally(error);
					}
				});
				return waiting.copy();
			}
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
			sharedIntegerClosing = true;
            // Fence queued notifications before closeAsync marks the runtime retiring.
            // An already-running callback retains its lifecycle read admission and
            // finishes before the runtime's final writer can close storage.
            closeSharedUserDataNotifications();
			sharedRuntime = null;
			sharedRuntimeRetiring = true;
			completion = new CompletableFuture<>();
			sharedRuntimeRetirement = completion;
		}
		CompletionStage<Void> runtimeClose;
		try { runtimeClose = Objects.requireNonNull(runtime.closeAsync(timer), "runtime close stage"); }
		catch (RuntimeException | Error failure) {
			synchronized (this) {
				if (sharedRuntime == null) sharedRuntime = runtime;
				sharedRuntimeRetiring = false;
				sharedIntegerClosing = false;
			}
			completion.completeExceptionally(failure);
			return completion;
		}
		runtimeClose.whenComplete((ignored, failure) -> {
			if (failure != null) {
				synchronized (this) {
					if (sharedRuntime == null) sharedRuntime = runtime;
					sharedRuntimeRetiring = false;
					sharedIntegerClosing = false;
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
		if (isPlatformOwnedThread()) {
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
		mutationCallbacks.allowCoreThreadTimeOut(true);
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
			storageWorker = worker;
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
			if (current) dispatchCacheRefreshNotification(refreshed, runtimeFailure);
			throw runtimeFailure;
		}
		if (errorFailure != null) {
			if (current) dispatchCacheRefreshNotification(refreshed, errorFailure);
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
		dispatchCacheRefreshNotification(refreshed, null);
	}

	private void cacheUserNow(UUID uuid, boolean traceDevelopmentCall, CacheRefresh refresh) {
		refresh.notificationGeneration = sharedNotificationGeneration.get();
		plugin.devDebug("Caching " + uuid.toString());
		if (traceDevelopmentCall && plugin.getOptions().getDebug().isDebug(DebugLevel.DEV)) {
			try { throw new Exception("caching here: " + uuid.toString()); }
			catch (Exception e) { e.printStackTrace(); }
		}
		if (hasSharedSqlBackend()) {
			UserDataCache existing = userDataCache.get(uuid);
			if (existing != null) {
				refresh.flushNotification = existing.clearChangesForRefresh();
				refresh.changed = existing.refreshInternal(false, true);
			} else {
				UserDataCache data = new UserDataCache(this, uuid);
				refresh.changed = data.refreshInternal(false, true);
				if (data.hasCache()) {
					UserDataCache winner = userDataCache.putIfAbsent(uuid, data);
					if (winner != null) {
						refresh.flushNotification = winner.clearChangesForRefresh();
						refresh.changed = winner.refreshInternal(false, true);
					}
				}
			}
			return;
		}
		UserDataCache candidate = new UserDataCache(this, uuid);
		UserDataCache existing = userDataCache.putIfAbsent(uuid, candidate);
		UserDataCache data = existing == null ? candidate : existing;
		LegacyRead read = beginLegacyRead(uuid);
		try {
			if (read != null && read.writing) return;
			if (existing != null) refresh.flushNotification = data.clearChangesForRefresh();
			refresh.changed = data.refreshInternal(false, false, read);
		} finally { if (read != null) endLegacyRead(read); }
	}

	LegacyRead beginLegacyRead(UUID uuid) {
		LegacyRead[] result = new LegacyRead[1];
		LegacyDirectWrite callback = UserDataCache.currentLegacyCallback(this);
		legacyWriteStates.compute(uuid, (id, previous) -> {
			LegacyWriteState current = previous == null ? new LegacyWriteState() : previous;
			current.readers++;
			boolean allowed = callback != null && callback.uuid.equals(uuid) && callback.state == current && !callback.ended.get() && current.writers == 1;
			result[0] = new LegacyRead(uuid, current, current.version, current.writers != 0 && !allowed, allowed ? callback : null);
			return current;
		});
		return result[0];
	}

	void endLegacyRead(LegacyRead read) {
		legacyWriteStates.compute(read.uuid, (id, current) -> {
			if (current != read.state || current.readers <= 0) throw new IllegalStateException("Legacy read state changed");
			read.active = false;
			current.readers--;
			return current.writers == 0 && current.readers == 0 ? null : current;
		});
	}

	<T> T publishLegacyRead(LegacyRead read, Supplier<T> publication) {
		AtomicReference<T> result = new AtomicReference<>();
		legacyWriteStates.computeIfPresent(read.uuid, (id, current) -> {
			if (current == read.state && legacyReadWritersAllowed(read, current) && current.version == read.version)
				result.set(publication.get());
			return current;
		});
		return result.get();
	}

	boolean isCurrentLegacyRead(LegacyRead read) {
		boolean[] current = new boolean[1];
		legacyWriteStates.computeIfPresent(read.uuid, (id, state) -> {
			current[0] = state == read.state && legacyReadWritersAllowed(read, state) && state.version == read.version;
			return state;
		});
		return current[0];
	}
	private boolean legacyReadWritersAllowed(LegacyRead read, LegacyWriteState state) {
		return state.writers == 0 || (!read.writing && read.callback == null && read.nestedWriters > 0 && state.writers == read.nestedWriters)
				|| (read.pendingOverlapAuthorized && read.pendingOverlapWriters > 0 && state.writers == read.pendingOverlapWriters)
				|| (state.writers == 1 && read.callback != null && !read.callback.ended.get()
				&& UserDataCache.currentLegacyCallback(this) == read.callback);
	}

	/** A completed batch inside a direct-write callback is ordered after that direct admission. */
	void recordCompletedLegacyCallbackBatch(LegacyRead read, String row, Set<String> keys, UserStorage storage, boolean foldColumns) {
		if (read == null || read.callback == null || storage == null || keys.isEmpty()) return;
		LegacyDirectWrite callback = read.callback;
		if (row == null || (storage == UserStorage.SQLITE ? !row.equals(callback.sqliteRow)
				: callback.sqliteRow != null || !callback.uuid.toString().equalsIgnoreCase(row))) return;
		synchronized (legacyRetirementAdmission) {
			if (callback.ended.get()) return;
			long sequence = ++legacyDirectSequence;
			java.util.Map<java.lang.String,java.lang.Long> successes = legacyDirectSuccesses.computeIfAbsent(callback.identity(), ignored -> new java.util.HashMap<>());
			for (String key : keys) {
				if (key == null) continue;
				String storageKey = storage == UserStorage.SQLITE ? UserDataCache.sqliteDirectKey(key) : storage.name() + ":" + key;
				successes.merge(storageKey, sequence, Math::max);
				if (foldColumns && storage == UserStorage.MYSQL) successes.merge(mysqlFoldedKey(storageKey), sequence, Math::max);
			}
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
		if (!hasSharedSqlBackend() || !isPlatformOwnedThread()) return false;
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
					dispatchSharedUserDataNotification(() -> {
						try { notifyCacheChangesAfterFailure(refreshed, deferredFailure); }
						catch (RuntimeException | Error notificationFailure) {
							deferredFailure.addSuppressed(notificationFailure);
							reportDeferredStorageFailure(deferredFailure);
						}
					}, refreshed.notificationGeneration);
					return;
				}
				if (!populated[0]) return;
				dispatchSharedUserDataNotification(() -> {
					try { notifyCacheChanges(refreshed); }
					catch (RuntimeException | Error notificationFailure) {
						reportDeferredStorageFailure(notificationFailure);
					}
				}, refreshed.notificationGeneration);
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
		return retireSharedCache(uuid, expected, false);
	}
	/** Atomically verify retirement admission before invalidating the flushed cache. */
	public final boolean retireSharedCacheAfterFlush(UUID uuid, UserDataCache expected) {
		return retireSharedCache(uuid, expected, true);
	}
	private boolean retireSharedCache(UUID uuid, UserDataCache expected, boolean retireContents) {
		Objects.requireNonNull(uuid, "uuid");
		synchronized (legacyMutationAdmission) {
			if (pendingLegacyIntegers.containsKey(uuid)) return false;
		SharedCachePopulationState state = sharedCachePopulationStates.computeIfAbsent(uuid,
				ignored -> new SharedCachePopulationState());
		synchronized (state) {
			if (userDataCache.get(uuid) != expected) return false;
			if (retireContents && expected != null) expected.retireAfterSharedFlush();
			if (expected != null) userDataCache.remove(uuid, expected);
			sharedCachePopulations.remove(uuid);
			completedSharedCachePopulations.remove(uuid);
			state.generation++;
			if (state.activePopulations == 0) sharedCachePopulationStates.remove(uuid, state);
			return true;
		}
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

	private void dispatchCacheRefreshNotification(CacheRefresh refresh, Throwable originalFailure) {
		if (refresh == null || (refresh.flushNotification == null && refresh.changed.isEmpty())) return;
		Runnable notification = () -> {
			if (originalFailure == null) notifyCacheChanges(refresh);
			else notifyCacheChangesAfterFailure(refresh, originalFailure);
		};
		if (hasSharedSqlBackend()) dispatchSharedUserDataNotification(notification, refresh.notificationGeneration);
		else notification.run();
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
		private long notificationGeneration;
		private CacheRefresh(UUID uuid) { this.uuid = uuid; }
	}

	private final java.util.concurrent.ConcurrentHashMap<UUID, UserDataCache> deferredLegacyRefreshes = new java.util.concurrent.ConcurrentHashMap<>();
	public void cacheUserIfNeeded(UUID uuid) {
		UserDataCache current = userDataCache.get(uuid);
		if (current == null) cacheUser(uuid);
		else if (!hasSharedSqlBackend() && !current.hasPublishedStorageSnapshot()) {
			if (current.hasEverPublishedStorageSnapshot()) getCacheWithoutStorage(uuid);
			else cacheUser(uuid);
		}
	}
	/** Return the current cache without doing storage work on the caller. */
	public UserDataCache getCacheWithoutStorage(UUID uuid) {
		UserDataCache current = userDataCache.get(uuid);
		if (current == null) return null;
		if (!hasSharedSqlBackend() && !current.hasPublishedStorageSnapshot() && deferredLegacyRefreshes.putIfAbsent(uuid, current) == null) {
			try {
				timer.execute(() -> {
					try { if (userDataCache.get(uuid) == current && !current.hasPublishedStorageSnapshot()) populateCompleteLegacyCache(uuid, current); }
					catch (RuntimeException | Error failure) { reportDeferredStorageFailure(failure); }
					finally { deferredLegacyRefreshes.remove(uuid, current); }
				});
			} catch (RejectedExecutionException failure) { deferredLegacyRefreshes.remove(uuid, current); reportDeferredStorageFailure(failure); }
		}
		return current;
	}

	/**
	 * Shared SQL writes are worker-only. Existing synchronous Bukkit clear/remove
	 * entry points hand the complete flush-and-remove sequence to the cache worker.
	 */
	public final boolean deferSharedStorageWork(Runnable task) {
		Objects.requireNonNull(task, "task");
		if (!hasSharedSqlBackend() || !isPlatformOwnedThread()) return false;
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
					dispatchPlatformCallback(() -> {
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
		dispatchPlatformCallback(notification, null);
	}

	private void dispatchPlatformCallback(Runnable notification, org.bukkit.entity.Entity callbackOwner) {
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

	/**
	 * Deliver an internal shared cache/storage notification on the manager's
	 * storage worker. Unlike platform completions, this lane never hands the
	 * notification to Bukkit/Folia. Queueing also ensures callers have released
	 * cache and per-user admission before a listener can re-enter storage APIs.
	 */
	public final void dispatchSharedUserDataNotification(Runnable notification) {
		dispatchSharedUserDataNotification(notification, sharedNotificationGeneration.get(), timer);
	}

	/** Mutation listeners use the completion lane so they cannot stall storage work. */
	public final void dispatchSharedMutationNotification(Runnable notification) {
		long generation = sharedNotificationGeneration.get();
		// The bridge runs after the current storage operation has left admission.
		// It queues only delivery, leaving the storage worker free for successors.
		timer.execute(() -> {
			try { dispatchSharedUserDataNotification(notification, generation, mutationCallbacks); }
			catch (RuntimeException | Error failure) { try { reportDeferredStorageFailure(failure); } catch (RuntimeException | Error ignored) { } }
		});
	}
	private void dispatchSharedUserDataNotification(Runnable notification, long generation) {
		dispatchSharedUserDataNotification(notification, generation, timer);
	}

	private void dispatchSharedUserDataNotification(Runnable notification, long generation, java.util.concurrent.Executor executor) {
		Objects.requireNonNull(notification, "notification");
		if (sharedNotificationsClosed) return;
		SharedSqlRoute admission = sharedSqlRoute;
		try {
			executor.execute(() -> {
				if (sharedNotificationsClosed || generation != sharedNotificationGeneration.get()) return;
				try {
					Runnable admittedNotification = () -> {
						if (sharedNotificationsClosed || generation != sharedNotificationGeneration.get()) return;
						try { sharedNotificationCallback.set(true); notification.run(); }
						catch (RuntimeException | Error failure) { reportDeferredStorageFailure(failure); }
						finally { sharedNotificationCallback.remove(); }
					};
					// Keep the producing runtime alive for the complete callback. A backend
					// replacement or shutdown takes the matching write admission and must
					// therefore wait for an already-started notification to finish. The
					// callback remains outside per-user admission so it may re-enter cache APIs.
					if (admission == null) admittedNotification.run();
					else admission.lifecycleGate().accept(admittedNotification);
				} catch (RuntimeException | Error failure) {
					// A queued callback may be rejected by its old runtime after replacement.
					// Its generation is stale by then, so dropping it is the intended fence.
					if (!sharedNotificationsClosed && generation == sharedNotificationGeneration.get()) {
						reportDeferredStorageFailure(failure);
					}
				}
			});
		} catch (RejectedExecutionException rejected) {
			reportDeferredStorageFailure(rejected);
			throw rejected;
		}
	}

	/** Capture the producing cache generation before lifecycle admission is released. */
	public final Runnable captureSharedUserDataNotification(Runnable notification) {
		Objects.requireNonNull(notification, "notification");
		long generation = sharedNotificationGeneration.get();
		return () -> {
			if (!sharedNotificationsClosed && generation == sharedNotificationGeneration.get()) notification.run();
		};
	}

	/** Fence callbacks captured from a cache generation that has been replaced. */
	public final void advanceSharedUserDataNotificationGeneration() {
		sharedNotificationGeneration.incrementAndGet();
	}

	/** Final shutdown discards queued notifications and prevents future publication. */
	public final void closeSharedUserDataNotifications() {
		sharedNotificationsClosed = true;
		sharedNotificationGeneration.incrementAndGet();
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

	/** Complete only after all cache entries have been flushed and retired.
	 * A request from a direct-write callback that must finish before its own write
	 * completes fails exceptionally; the caller may retry after the callback returns.
	 */
	public CompletionStage<Void> clearCacheAsyncCompletion() {
		if (UserDataCache.hasLiveLegacyCallback(this) || Boolean.TRUE.equals(legacyIntegerNotification.get())
				|| Boolean.TRUE.equals(legacyIntegerCompletion.get()))
			return CompletableFuture.failedFuture(new IllegalStateException("Cache retirement cannot await its own direct-write callback"));
		CompletableFuture<Void> pending;
		CompletableFuture<Void> waiting;
		synchronized (legacyRetirementAdmission) {
			if (legacyRetirementWait != null) return legacyRetirementWait.copy();
			java.util.ArrayList<java.util.concurrent.CompletableFuture<?>> dependencies = new java.util.ArrayList<CompletableFuture<?>>();
			if (legacyDirectAdmissions != 0) dependencies.add(legacyDirectDrain);
			if (activeLegacyIntegerApplications != 0) dependencies.add(legacyIntegerApplicationDrain);
			synchronized (legacyMutationAdmission) {
				for (java.util.ArrayDeque<com.bencodez.advancedcore.api.user.usercache.UserDataManager.PendingLegacyIntegerMutation> queue : pendingLegacyIntegers.values())
					for (com.bencodez.advancedcore.api.user.usercache.UserDataManager.PendingLegacyIntegerMutation mutation : queue) dependencies.add(mutation.completion.finalized);
				for (Set<PendingLegacyIntegerMutation> callbacks : legacyCallbackReceipts.values())
					for (PendingLegacyIntegerMutation mutation : callbacks) dependencies.add(mutation.completion.finalized);
			}
			pending = dependencies.isEmpty() ? null : CompletableFuture.allOf(dependencies.toArray(CompletableFuture[]::new));
			if (pending != null) {
				legacyRetirementRequested = true;
				legacyRetirementWait = new CompletableFuture<>();
			}
			waiting = legacyRetirementWait;
		}
		if (pending != null) {
			pending.whenComplete((ignored, failure) -> {
				if (failure != null) {
					synchronized (legacyRetirementAdmission) { legacyRetirementRequested = false; legacyRetirementWait = null; }
					reportDeferredStorageFailure(failure);
					waiting.completeExceptionally(failure);
					return;
				}
				try {
					timer.execute(() -> {
						Throwable error = null;
						try { clearCacheNow(); }
						catch (RuntimeException | Error failureDuringRetirement) { error = failureDuringRetirement; reportDeferredStorageFailure(error); }
						finally { synchronized (legacyRetirementAdmission) { if (legacyRetirementWait == waiting) { legacyRetirementWait = null; legacyRetirementRequested = false; } } }
						if (error == null) waiting.complete(null); else waiting.completeExceptionally(error);
					});
				} catch (RejectedExecutionException rejected) {
					synchronized (legacyRetirementAdmission) { legacyRetirementRequested = false; legacyRetirementWait = null; }
					reportDeferredStorageFailure(rejected);
					waiting.completeExceptionally(rejected);
				}
			});
			return waiting.copy();
		}
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
					try { reportDeferredStorageFailure(failure); }
					finally { completion.completeExceptionally(failure); }
				}
			});
		} catch (RejectedExecutionException rejected) {
			reportDeferredStorageFailure(rejected);
			completion.completeExceptionally(rejected);
		}
		return completion;
	}

	private void clearCacheNow() {
		synchronized (legacyRetirementAdmission) {
			if (legacyDirectAdmissions != 0) throw new IllegalStateException("Legacy direct writes are still pending");
			if (activeLegacyIntegerApplications != 0) throw new IllegalStateException("Legacy integer mutations are active");
			synchronized (legacyMutationAdmission) {
				if (pendingLegacyIntegerCount != 0) throw new IllegalStateException("Legacy integer mutations are still pending");
			}
			if (!legacyUnrecoveredWrites.isEmpty()) throw new IllegalStateException("A failed uncached legacy write requires an explicit successful retry");
			if (legacyRetirementActive) throw new IllegalStateException("Legacy cache retirement is already active");
			legacyRetirementActive = true;
		}
		try { clearCacheNowAdmitted(); }
		finally { synchronized (legacyRetirementAdmission) { legacyRetirementActive = false; if (legacyRetirementWait == null) legacyRetirementRequested = false; } }
	}

	private void clearCacheNowAdmitted() {
		// Do not retain this map lock while flushing through a shared runtime gate.
		// A population already admitted by that runtime needs the map read lock to
		// publish, while a queued lifecycle writer would otherwise block this flush.
		// Capture the current generations under the map lock. Shared generations
		// are marked only after their per-user admission drains, so already-admitted
		// setters can complete before retirement starts.
		java.util.HashMap<UUID, UserDataCache> retiring = new java.util.HashMap<>();
		boolean shared = sharedSqlRoute != null;
		cacheMapLifecycle.writeLock().lock();
		try {
			plugin.debug("Clearing cache: " + userDataCache.keySet().size());
			retiring.putAll(userDataCache);
			if (!shared) for (UserDataCache cache : retiring.values()) cache.beginRemoval();
		} finally { cacheMapLifecycle.writeLock().unlock(); }
		try {
			for (Entry<UUID, UserDataCache> entry : retiring.entrySet()) {
				UUID uuid = entry.getKey();
				UserDataCache cache = entry.getValue();
				// The map write lock only protects publication.  Retiring each cache
				// must also exclude the normal per-user read admission used by
				// addChange(), otherwise a queued setter can observe this mapped cache
				// after beginRemoval() and have its mutation silently discarded.
				withSharedSqlBackendExclusive(uuid, () -> {
					if (shared) {
						if (userDataCache.get(uuid) != cache) return;
						cache.beginRemoval();
					}
					try { clearCacheExclusively(uuid, cache); }
					catch (RuntimeException | Error failure) {
						if (shared && userDataCache.get(uuid) == cache) cache.cancelRemoval();
						throw failure;
					}
				});
			}
		} catch (RuntimeException | Error failure) {
			// All not-yet-detached caches were marked before flushing began. Reopen
			// each surviving cache so one user's failed write cannot discard later
			// updates for another cache that has not been flushed yet.
			if (!shared) for (UserDataCache cache : retiring.values()) cache.cancelRemoval();
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

	/**
	 * Capture Bukkit-owned online-player state on the platform scheduler, then do
	 * cache/storage eviction on the user-storage worker.
	 */
	public void clearNonNeededCachedUsers() {
		Runnable capture = () -> {
			java.util.HashSet<UUID> platformOnline = new java.util.HashSet<>();
			if (Bukkit.getServer() != null) {
				for (Player player : Bukkit.getOnlinePlayers()) {
					UUID storageUuid = onlineStorageUuid(player);
					if (storageUuid != null) platformOnline.add(storageUuid);
				}
			}
			long snapshotGeneration = onlineSessionGeneration.get();
			try {
				timer.execute(() -> {
					try { clearNonNeededCachedUsers(reconcileOnlineSnapshot(platformOnline, snapshotGeneration)); }
					catch (RuntimeException | Error failure) {
						reportDeferredStorageFailure(failure);
						throw failure;
					}
				});
			} catch (RejectedExecutionException rejected) {
				if (plugin != null && plugin.isEnabled()) reportDeferredStorageFailure(rejected);
			}
		};
		if (Bukkit.getServer() == null) capture.run();
		else {
			try { plugin.getBukkitScheduler().runTask(plugin, capture); }
			catch (RuntimeException failure) {
				if (plugin != null && plugin.isEnabled()) reportDeferredStorageFailure(failure);
			}
		}
	}

	private Set<UUID> reconcileOnlineSnapshot(Set<UUID> platformOnline, long snapshotGeneration) {
		java.util.HashSet<UUID> online = new java.util.HashSet<>();
		for (UUID uuid : platformOnline) {
			synchronized (onlineSessionLock(uuid)) {
				OnlineSessionState state = onlineUserSessions.compute(uuid,
						(ignored, current) -> current == null
								? new OnlineSessionState(true, snapshotGeneration) : current);
				if (state.online()) online.add(uuid);
			}
		}
		for (UUID uuid : Set.copyOf(onlineUserSessions.keySet())) {
			synchronized (onlineSessionLock(uuid)) {
				OnlineSessionState state = onlineUserSessions.get(uuid);
				if (state != null && !state.online() && state.generation() <= snapshotGeneration
						&& !platformOnline.contains(uuid)) {
					onlineUserSessions.remove(uuid, state);
				}
			}
		}
		return online;
	}

	private UUID onlineStorageUuid(Player player) {
		if (player == null) return null;
		if (plugin.getOptions().isOnlineMode()) return player.getUniqueId();
		try {
			String resolved = UuidLookup.getInstance().getUUID(player.getName());
			return resolved == null || resolved.isEmpty() ? null : UUID.fromString(resolved);
		} catch (RuntimeException failure) {
			plugin.debug(failure);
			return null;
		}
	}

	public void markUserOnline(UUID uuid) {
		if (uuid != null) synchronized (onlineSessionLock(uuid)) {
			onlineUserSessions.put(uuid, new OnlineSessionState(true, onlineSessionGeneration.incrementAndGet()));
		}
	}

	/** Capture an online session from a Bukkit/Folia-owned player event. */
	public void markUserOnline(Player player) {
		markUserOnline(onlineStorageUuid(player));
	}

	public void markUserOffline(UUID uuid) {
		if (uuid != null) synchronized (onlineSessionLock(uuid)) {
			onlineUserSessions.put(uuid, new OnlineSessionState(false, onlineSessionGeneration.incrementAndGet()));
		}
	}

	/** Remove an online session from a Bukkit/Folia-owned player event. */
	public void markUserOffline(Player player) {
		markUserOffline(onlineStorageUuid(player));
	}

	private void clearNonNeededCachedUsers(Set<UUID> onlineSnapshot) {
		plugin.devDebug("Clearing cache for non online players (if any)");
		int removed = 0;
		for (UUID uuid : Set.copyOf(userDataCache.keySet())) {
			if (onlineSnapshot.contains(uuid) || isUserOnline(uuid)) continue;
			UserDataCache expected = userDataCache.get(uuid);
			if (expected == null) continue;
			long expectedVersion = expected.getSharedSnapshotVersion();
			java.util.concurrent.atomic.AtomicBoolean retired = new java.util.concurrent.atomic.AtomicBoolean();
			java.util.concurrent.atomic.AtomicBoolean invalidatedForJoin = new java.util.concurrent.atomic.AtomicBoolean();
			withSharedSqlBackendExclusive(uuid, () -> {
				if (isUserOnline(uuid)) return;
				UserDataCache current = userDataCache.get(uuid);
				if (current != expected || current.getSharedSnapshotVersion() != expectedVersion) return;
				synchronized (legacyMutationAdmission) {
					if (pendingLegacyIntegers.containsKey(uuid)) return;
					legacyIntegerCacheRetirements.add(uuid);
				}
				try {
				current.beginRemoval();
				if (isUserOnline(uuid)) {
					current.cancelRemoval();
					return;
				}
				try {
					current.clearCache();
				} catch (RuntimeException | Error failure) {
					current.cancelRemoval();
					throw failure;
				}
				synchronized (onlineSessionLock(uuid)) {
					if (isUserOnline(uuid)) {
						if (sharedSqlRoute != null) {
							try { current.retireAfterSharedFlush(); }
							catch (RuntimeException | Error failure) {
								current.cancelRemoval();
								throw failure;
							}
						}
						boolean cacheRemoved = retireSharedCache(uuid, current);
						retired.set(cacheRemoved);
						invalidatedForJoin.set(cacheRemoved);
						return;
					}
					if (sharedSqlRoute != null) {
						try { current.retireAfterSharedFlush(); }
						catch (RuntimeException | Error failure) {
							current.cancelRemoval();
							throw failure;
						}
					}
					boolean cacheRemoved = retireSharedCache(uuid, current);
					retired.set(cacheRemoved);
				}
				} finally { synchronized (legacyMutationAdmission) { legacyIntegerCacheRetirements.remove(uuid); } }
			});
			Consumer<UUID> listener = sharedCacheRemovalListener;
			if (retired.get() && listener != null) listener.accept(uuid);
			if (retired.get() && !invalidatedForJoin.get()) removed++;
		}
		if (removed > 0) plugin.devDebug("Removed " + removed + " cached users who are no longer online");
	}

	private boolean isUserOnline(UUID uuid) {
		OnlineSessionState state = onlineUserSessions.get(uuid);
		return state != null && state.online();
	}

	public UserDataCache getPublishedCache(UUID uuid) {
		UserDataCache cache = userDataCache.get(uuid);
		return cache != null && cache.hasPublishedStorageSnapshot() ? cache : null;
	}

	/** Resolve a legacy generation without waiting for storage or lifecycle work. */
	public UserDataCache.IntegerMutationResult tryMutateLegacyIntegers(UUID uuid, Map<String, Integer> values,
			UserDataCache.IntegerMutation operation, Consumer<Set<String>> notification) {
		if (hasSharedSqlBackend()) return null;
		Objects.requireNonNull(uuid, "uuid");
		UserDataCache.IntegerMutationResult mutation;
		java.util.concurrent.locks.ReentrantLock stripe = legacyMutationStripe(uuid);
		boolean locked = stripe.tryLock();
		if (!locked && !isPlatformOwnedThread()) {
			try { locked = stripe.tryLock(100, TimeUnit.MILLISECONDS); }
			catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return null; }
		}
		if (!locked) return null;
		boolean admitted = false;
		try {
			try {
			synchronized (legacyRetirementAdmission) {
				if (legacyRetirementActive || legacyRetirementRequested || legacyIntegerOwnerReplacement || legacyIntegerShutdown) return null;
				if (activeLegacyIntegerApplications++ == 0) legacyIntegerApplicationDrain = new CompletableFuture<>();
				admitted = true;
			}
			synchronized (legacyMutationAdmission) {
				if (pendingLegacyIntegers.containsKey(uuid) || legacyIntegerCacheRetirements.contains(uuid)) return null;
			}
			mutation = tryMutateLegacyIntegersAdmitted(uuid, values, operation);
			} finally { stripe.unlock(); }
			boolean nestedNotification = Boolean.TRUE.equals(legacyIntegerNotification.get());
			legacyIntegerNotification.set(true);
			try { notifyLegacyIntegerMutation(mutation, operation, notification); }
			catch (RuntimeException | Error notificationFailure) {
				try { reportDeferredStorageFailure(notificationFailure); } catch (RuntimeException | Error ignored) { }
			}
			finally { if (nestedNotification) legacyIntegerNotification.set(true); else legacyIntegerNotification.remove(); }
			return mutation;
		} finally {
			CompletableFuture<Void> drained = null;
			if (admitted) synchronized (legacyRetirementAdmission) {
				if (--activeLegacyIntegerApplications == 0) drained = legacyIntegerApplicationDrain;
			}
			if (drained != null) drained.complete(null);
		}
	}

	private UserDataCache.IntegerMutationResult tryMutateLegacyIntegersAdmitted(UUID uuid, Map<String, Integer> values,
			UserDataCache.IntegerMutation operation) {
		AtomicReference<UserDataCache.IntegerMutationResult> result = new AtomicReference<>();
		legacyWriteStates.compute(uuid, (id, state) -> {
			if (state == null || state.writers == 0) {
				UserDataCache cache = userDataCache.get(id);
				if (cache != null) result.set(cache.tryMutateLegacyIntegers(values, operation, null));
			}
			return state;
		});
		return result.get();
	}

	private void notifyLegacyIntegerMutation(UserDataCache.IntegerMutationResult mutation,
			UserDataCache.IntegerMutation operation, Consumer<Set<String>> notification) {
		if (mutation != null && mutation.applied() && notification != null) {
			java.util.LinkedHashSet<String> changed = new java.util.LinkedHashSet<String>();
			for (java.util.Map.Entry<java.lang.String,java.lang.Integer> entry : mutation.previous().entrySet())
				if (operation != UserDataCache.IntegerMutation.DEBIT_POSITIVE || entry.getValue() > 0) changed.add(entry.getKey());
			notification.accept(Set.copyOf(changed));
		}
	}

	/**
	 * Retain one original arithmetic operation for a legacy storage worker.
	 * A terminal exceptional result means this operation was not applied and will
	 * not be applied later. The returned receipt cannot be cancelled or externally
	 * completed; callers must retain and observe it for the actual operation outcome.
	 * Cancelling a dependent stage only cancels that observation, not this receipt
	 * or the mutation, and must not be treated as a reason to retry the mutation.
	 * Completion callbacks registered before settlement run on a bounded lane,
	 * independently of the serialized storage worker; callbacks registered after
	 * settlement may run on the registering thread. Delivery can be out of order.
	 * Blocking callbacks can exhaust that lane and delay completion and shutdown.
	 */
	public CompletionStage<UserDataCache.IntegerMutationResult> submitLegacyIntegerMutation(UUID uuid,
			Map<String, Integer> values, UserDataCache.IntegerMutation operation, Consumer<Set<String>> notification) {
		Objects.requireNonNull(uuid, "uuid"); Objects.requireNonNull(values, "values"); Objects.requireNonNull(operation, "operation");
		if (values.isEmpty() || values.size() > 64) throw new IllegalArgumentException("Invalid integer mutation size");
		com.bencodez.advancedcore.AdvancedCorePlugin.UserStorageOwner owner = Objects.requireNonNull(plugin.getNativeUserStorageOwner(), "Native user storage owner is unavailable");
		PendingLegacyIntegerMutation pending = new PendingLegacyIntegerMutation(this, uuid, owner, values, operation, notification);
		boolean start;
		java.util.concurrent.locks.ReentrantLock stripe = legacyMutationStripe(uuid);
		if (!stripe.tryLock()) throw new RejectedExecutionException("Legacy integer mutation admission is busy");
		try {
		synchronized (legacyRetirementAdmission) {
			if (legacyRetirementActive || legacyRetirementRequested || legacyIntegerOwnerReplacement || legacyIntegerShutdown)
				throw new IllegalStateException("Legacy cache retirement or owner replacement is active");
			synchronized (legacyMutationAdmission) {
				if (pendingLegacyIntegerCount >= MAX_PENDING_LEGACY_INTEGERS) throw new RejectedExecutionException("Legacy integer mutation queue is full");
				if (legacyIntegerCacheRetirements.contains(uuid)) throw new IllegalStateException("Legacy user cache is retiring");
				beginLegacyCacheBatch();
				java.util.ArrayDeque<com.bencodez.advancedcore.api.user.usercache.UserDataManager.PendingLegacyIntegerMutation> queue = pendingLegacyIntegers.computeIfAbsent(uuid, ignored -> new java.util.ArrayDeque<>());
				start = queue.isEmpty(); queue.addLast(pending); pendingLegacyIntegerCount++;
			}
		}
		} finally { stripe.unlock(); }
		if (start) scheduleLegacyIntegerMutation(pending, 0);
		return pending.completion;
	}

	/** Submit one shared-cache mutation and retain its receipt through lifecycle retirement.
	 * Completion callbacks registered before settlement run on a bounded lane and can
	 * arrive out of order; callbacks attached afterward may run on the attaching thread.
	 * Blocking callbacks can exhaust the lane and delay completion and shutdown. */
	public CompletionStage<UserDataCache.IntegerMutationResult> submitSharedIntegerMutation(UUID uuid,
			Map<String, Integer> values, UserDataCache.IntegerMutation operation, Consumer<Set<String>> notification) {
		Objects.requireNonNull(uuid, "uuid"); Objects.requireNonNull(values, "values"); Objects.requireNonNull(operation, "operation");
		if (values.isEmpty() || values.size() > 64) throw new IllegalArgumentException("Invalid integer mutation size");
		java.util.Map<java.lang.String,java.lang.Integer> operands = Map.copyOf(values);
		MutationReceipt<UserDataCache.IntegerMutationResult> receipt = new MutationReceipt<UserDataCache.IntegerMutationResult>(this);
		SharedUserDataRuntime runtime;
		Throwable schedulingFailure = null;
		synchronized (this) {
			runtime = sharedRuntime;
			if (runtime == null || sharedRuntimeRetiring || sharedRuntimeReplacing || sharedIntegerClosing || sharedIntegerOwnerReplacement)
				throw new RejectedExecutionException("Shared user storage is unavailable or retiring");
			if (sharedIntegerAdmissions >= MAX_PENDING_LEGACY_INTEGERS) throw new RejectedExecutionException("Shared integer mutation queue is full");
			if (sharedIntegerAdmissions++ == 0) sharedIntegerDrain = new CompletableFuture<>();
			sharedIntegerQueuedUsers.merge(uuid, 1, Integer::sum);
			DeferredSharedInteger deferred = new DeferredSharedInteger(
					() -> runSharedIntegerMutation(runtime, uuid, operands, operation, notification, receipt),
					failure -> finishSharedIntegerMutation(uuid, receipt, null, failure), receipt);
			java.util.ArrayDeque<com.bencodez.advancedcore.api.user.usercache.UserDataManager.DeferredSharedInteger> queue = sharedIntegerDeferred.computeIfAbsent(uuid, ignored -> new java.util.ArrayDeque<>());
			queue.addLast(deferred);
			if (queue.size() == 1 && !sharedIntegerActiveUsers.containsKey(uuid) && !sharedIntegerCompletingUsers.contains(uuid)) try { timer.execute(deferred.action()); }
			catch (RuntimeException | Error failure) { schedulingFailure = failure; }
		}
		if (schedulingFailure != null) finishSharedIntegerMutation(uuid, receipt, null, schedulingFailure);
		return receipt;
	}

	private void runSharedIntegerMutation(SharedUserDataRuntime runtime, UUID uuid, Map<String, Integer> values,
			UserDataCache.IntegerMutation operation, Consumer<Set<String>> notification,
			MutationReceipt<UserDataCache.IntegerMutationResult> receipt) {
		try {
			sharedIntegerWorker.set(uuid);
			com.bencodez.advancedcore.api.user.usercache.UserDataCache.IntegerMutationResult result = runtime.withStorageReadAdmission(() -> {
				if (sharedRuntime != runtime) throw new IllegalStateException("Shared storage owner changed");
				com.bencodez.advancedcore.api.user.usercache.UserDataCache cache = getPublishedCache(uuid);
				if (cache == null) { cacheUser(uuid); cache = getPublishedCache(uuid); }
				if (cache == null) throw new IllegalStateException("Shared user cache is unavailable");
				for (String key : values.keySet()) if (cache.getIfPresent(key) == null) cache.seedMissingIntegerFromCompleteSnapshot(key);
				com.bencodez.advancedcore.api.user.usercache.UserDataCache.IntegerMutationResult applied = cache.tryMutateIntegers(values, operation, notification);
				if (applied == null) throw new IllegalStateException("Shared integer mutation was not applied");
				return applied;
			});
			finishSharedIntegerMutation(uuid, receipt, result, null);
		} catch (UserDataCache.SharedIntegerMutationUncertainException uncertain) {
			try { reportDeferredStorageFailure(uncertain); } catch (RuntimeException | Error ignored) { }
		} catch (RuntimeException | Error failure) { finishSharedIntegerMutation(uuid, receipt, null, failure); }
		finally { sharedIntegerWorker.remove(); }
	}

	private <T> void finishSharedIntegerMutation(UUID uuid, MutationReceipt<T> receipt, T result, Throwable failure) {
		DeferredSharedInteger failedTask = null; Throwable schedulingFailure = null;
		synchronized (this) {
			java.util.ArrayDeque<com.bencodez.advancedcore.api.user.usercache.UserDataManager.DeferredSharedInteger> queue = sharedIntegerDeferred.get(uuid);
			if (queue == null || queue.isEmpty() || queue.peekFirst().receipt() != receipt)
				throw new IllegalStateException("Shared integer completion is out of order");
			queue.removeFirst();
			if (queue.isEmpty()) sharedIntegerDeferred.remove(uuid);
			sharedCallbackReceipts.computeIfAbsent(uuid, ignored -> new java.util.HashSet<>()).add(receipt);
			sharedIntegerQueuedUsers.computeIfPresent(uuid, (key, count) -> count == 1 ? null : count - 1);
			queue = sharedIntegerDeferred.get(uuid);
			if (queue != null && !queue.isEmpty() && !sharedIntegerActiveUsers.containsKey(uuid)) {
				DeferredSharedInteger next = queue.peekFirst();
				try { timer.execute(next.action()); }
				catch (RuntimeException | Error error) { failedTask = next; schedulingFailure = error; }
			}
		}
		if (failedTask != null) failedTask.reject().accept(schedulingFailure);
		try {
			mutationCallbacks.execute(() -> {
				sharedIntegerCompletion.set(true);
				try { if (failure == null) receipt.succeed(result); else receipt.fail(failure); }
				finally {
					sharedIntegerCompletion.remove();
					synchronized (this) {
						Set<MutationReceipt<?>> callbacks = sharedCallbackReceipts.get(uuid);
						callbacks.remove(receipt); if (callbacks.isEmpty()) sharedCallbackReceipts.remove(uuid);
						if (--sharedIntegerAdmissions == 0) sharedIntegerDrain.complete(null);
					}
					sharedIntegerCompletion.set(true);
					try { receipt.finalized.complete(null); } finally { sharedIntegerCompletion.remove(); }
				}
			});
		} catch (RuntimeException | Error rejected) {
			try { reportDeferredStorageFailure(rejected); } catch (RuntimeException | Error ignored) { }
		}
	}

	/** Observe when a shared mutation's completion callbacks and admission have settled. */
	public CompletionStage<Void> afterSharedMutationFinalization(CompletionStage<?> completion) {
		if (!(completion instanceof MutationReceipt<?> receipt) || receipt.sharedOwner != this)
			throw new IllegalArgumentException("Not a shared mutation receipt");
		return receipt.finalized.minimalCompletionStage();
	}

	/**
	 * Submit an idempotent SQL replay transaction. The work must atomically
	 * record and check the same durable operation identity on every attempt, and
	 * must not perform external side effects. An uncertain commit is retried with
	 * the same work; the original non-cancellable receipt remains pending until
	 * the transaction can establish its outcome. A callback may throw
	 * {@link SqlUserStorage.ReplayPreconditionException} only before any write or
	 * external effect to report a definite rejection without retry.
	 */
	/** Submit an award with its durable identity for indeterminate diagnostics. */
	public <T> CompletionStage<T> submitSharedReplayTransaction(UUID uuid, UserStorage storage, String identity,
			Map<String, DataValue> initialValues, SqlUserStorage.TransactionWork<T> work) {
		return submitSharedReplayTransaction(uuid, storage, initialValues, work, null, Objects.requireNonNull(identity, "identity"));
	}

	/** Submit checkpoint cleanup for an existing lease even while shutdown is draining it. */
	public <T> CompletionStage<T> submitSharedReplayCheckpoint(UUID uuid, UserStorage storage, String identity,
			Map<String, DataValue> initialValues, SqlUserStorage.TransactionWork<T> work) {
		return submitSharedReplayTransaction(uuid, storage, initialValues, work, Objects.requireNonNull(identity, "identity"), identity);
	}

	private <T> CompletionStage<T> submitSharedReplayTransaction(UUID uuid, UserStorage storage,
			Map<String, DataValue> initialValues, SqlUserStorage.TransactionWork<T> work, String checkpointIdentity, String diagnosticIdentity) {
		Objects.requireNonNull(uuid, "uuid"); Objects.requireNonNull(storage, "storage");
		Objects.requireNonNull(work, "work"); java.util.Map<java.lang.String,com.bencodez.simpleapi.sql.data.DataValue> initial = Map.copyOf(initialValues);
		MutationReceipt<T> receipt = new MutationReceipt<T>(this); SharedUserDataRuntime runtime; Throwable schedulingFailure = null;
		synchronized (this) {
			runtime = sharedRuntime;
			com.bencodez.advancedcore.api.user.usercache.UserDataManager.SharedReplayLeaseState checkpointLease = checkpointIdentity == null ? null : sharedReplayLeases.get(new SharedReplayLease(uuid, checkpointIdentity));
			boolean drainingCheckpoint = checkpointLease != null && runtime != null && checkpointLease.runtime == runtime;
			if (checkpointIdentity != null && (!drainingCheckpoint || checkpointLease.checkpointReservations == 0))
				throw new IllegalStateException("Replay checkpoint requires an active lease for its storage owner");
			if (runtime == null || sharedRuntimeRetiring || sharedRuntimeReplacing || (sharedIntegerClosing && !drainingCheckpoint) || sharedIntegerOwnerReplacement)
				throw new RejectedExecutionException("Shared user storage is unavailable or retiring");
			if (!runtime.backend().supportsAtomicUserTransactions())
				throw new UnsupportedOperationException("Shared provider does not support atomic replay transactions");
			// Reserve bounded cleanup capacity so a full set of retained replay
			// leases cannot prevent the checkpoints that release those leases.
			int limit = drainingCheckpoint ? MAX_PENDING_LEGACY_INTEGERS * 2 : MAX_PENDING_LEGACY_INTEGERS;
			if (sharedIntegerAdmissions >= limit) throw new RejectedExecutionException("Shared mutation queue is full");
			if (checkpointIdentity != null) checkpointLease.checkpointReservations--;
			if (sharedIntegerAdmissions++ == 0) sharedIntegerDrain = new CompletableFuture<>();
			sharedIntegerQueuedUsers.merge(uuid, 1, Integer::sum);
			DeferredSharedInteger deferred = new DeferredSharedInteger(
					() -> runSharedReplayTransaction(runtime, uuid, storage, initial, work, receipt, 0, diagnosticIdentity, checkpointIdentity != null),
					failure -> finishSharedIntegerMutation(uuid, receipt, null, failure), receipt);
			java.util.ArrayDeque<com.bencodez.advancedcore.api.user.usercache.UserDataManager.DeferredSharedInteger> queue = sharedIntegerDeferred.computeIfAbsent(uuid, ignored -> new java.util.ArrayDeque<>());
			queue.addLast(deferred);
			if (queue.size() == 1 && !sharedIntegerActiveUsers.containsKey(uuid) && !sharedIntegerCompletingUsers.contains(uuid))
				try { timer.execute(deferred.action()); } catch (RuntimeException | Error failure) { schedulingFailure = failure; }
		}
		if (schedulingFailure != null) finishSharedIntegerMutation(uuid, receipt, null, schedulingFailure);
		return receipt;
	}

	/** Retain the current owner from award admission through durable checkpoint cleanup. */
	public synchronized void acquireSharedReplayLease(UUID uuid, String identity) { acquireSharedReplayLease(uuid, identity, false); }
	public synchronized void acquireSharedReplayCheckpointLease(UUID uuid, String identity) { acquireSharedReplayLease(uuid, identity, true); }
	/** Reject an admitted award overtaken by durable checkpoint cleanup before it writes. */
	public synchronized void requireSharedReplayAwardActive(UUID uuid, String identity) {
		com.bencodez.advancedcore.api.user.usercache.UserDataManager.SharedReplayLeaseState state = sharedReplayLeases.get(new SharedReplayLease(Objects.requireNonNull(uuid), Objects.requireNonNull(identity)));
		if (state == null || state.runtime != sharedRuntime || state.checkpointed)
			throw new SqlUserStorage.ReplayPreconditionException("Replay award has already been checkpointed");
	}
	private void acquireSharedReplayLease(UUID uuid, String identity, boolean checkpoint) {
		Objects.requireNonNull(uuid, "uuid"); Objects.requireNonNull(identity, "identity");
		if (identity.isEmpty() || identity.length() > 4096) throw new IllegalArgumentException("Invalid replay identity");
		SharedReplayLease key = new SharedReplayLease(uuid, identity); com.bencodez.advancedcore.api.user.usercache.UserDataManager.SharedReplayLeaseState existing = sharedReplayLeases.get(key);
		if (existing != null) {
			if (existing.runtime != sharedRuntime) throw new IllegalStateException("Replay owner changed");
			if (existing.checkpointed && !checkpoint) throw new IllegalStateException("Replay award is already checkpointed");
			existing.attempts++; if (checkpoint) existing.checkpointReservations++;
			return;
		}
		if (sharedRuntime == null || sharedRuntimeRetiring || sharedRuntimeReplacing || sharedIntegerClosing || sharedIntegerOwnerReplacement)
			throw new RejectedExecutionException("Shared user storage is unavailable or retiring");
		if (sharedIntegerAdmissions >= MAX_PENDING_LEGACY_INTEGERS) throw new RejectedExecutionException("Shared mutation queue is full");
		if (sharedIntegerAdmissions++ == 0) sharedIntegerDrain = new CompletableFuture<>();
		SharedReplayLeaseState state = new SharedReplayLeaseState(sharedRuntime); state.attempts = 1;
		if (checkpoint) state.checkpointReservations = 1; sharedReplayLeases.put(key, state);
	}

	/** Release a checkpoint reservation only when submission did not admit work. */
	public synchronized void cancelSharedReplayCheckpointReservation(UUID uuid, String identity) {
		SharedReplayLease key = new SharedReplayLease(Objects.requireNonNull(uuid), Objects.requireNonNull(identity));
		SharedReplayLeaseState state = sharedReplayLeases.get(key);
		if (state == null || state.checkpointReservations == 0 || state.attempts == 0)
			throw new IllegalStateException("Replay checkpoint reservation is not active");
		state.checkpointReservations--; state.attempts--;
		if (state.attempts == 0 && (!state.committed || state.checkpointed)) {
			sharedReplayLeases.remove(key);
			if (--sharedIntegerAdmissions == 0) sharedIntegerDrain.complete(null);
		}
	}

	/** Settle one lease attempt; a committed award retains the owner until checkpoint cleanup. */
	public void settleSharedReplayLease(UUID uuid, String identity, boolean awardCommitted, boolean checkpointCommitted) {
		Objects.requireNonNull(uuid, "uuid"); Objects.requireNonNull(identity, "identity");
		synchronized (this) {
			SharedReplayLease key = new SharedReplayLease(uuid, identity); com.bencodez.advancedcore.api.user.usercache.UserDataManager.SharedReplayLeaseState state = sharedReplayLeases.get(key);
			if (state == null || state.attempts <= 0) throw new IllegalStateException("Replay lease attempt is not active");
			state.attempts--; state.committed |= awardCommitted; state.checkpointed |= checkpointCommitted;
			if (state.attempts == 0 && (!state.committed || state.checkpointed)) {
				sharedReplayLeases.remove(key);
				if (--sharedIntegerAdmissions == 0) sharedIntegerDrain.complete(null);
			}
		}
	}

	private <T> void runSharedReplayTransaction(SharedUserDataRuntime runtime, UUID uuid, UserStorage storage,
			Map<String, DataValue> initial, SqlUserStorage.TransactionWork<T> work, MutationReceipt<T> receipt, int attempts,
			String identity, boolean checkpoint) {
		T result;
		try {
			if (sharedRuntime != runtime) throw new IllegalStateException("Shared storage owner changed");
			result = runtime.transaction(uuid, storage, initial, work);
			if (checkpoint) {
				// The durable deletion is confirmed. Fence previously admitted awards
				// before releasing the per-user queue, independent of user callbacks.
				synchronized (this) {
					SharedReplayLeaseState lease = sharedReplayLeases.get(new SharedReplayLease(uuid, identity));
					if (lease == null || lease.runtime != runtime)
						throw new IllegalStateException("Confirmed checkpoint lost its storage lease");
					lease.checkpointed = true;
				}
			}
		} catch (SqlUserStorage.ReplayPreconditionException rejected) {
			finishSharedIntegerMutation(uuid, receipt, null, rejected);
			return;
		} catch (RuntimeException failure) {
			if (attempts == 0 || (attempts & (attempts - 1)) == 0)
				try { reportDeferredStorageFailure(failure); } catch (RuntimeException | Error ignored) { }
			int next = attempts == Integer.MAX_VALUE ? attempts : attempts + 1;
			long delay = Math.min(30000L, 250L << Math.min(attempts, 7));
			try { timer.schedule(() -> runSharedReplayTransaction(runtime, uuid, storage, initial, work, receipt, next, identity, checkpoint), delay, TimeUnit.MILLISECONDS); }
			catch (RuntimeException | Error rejected) {
				synchronized (this) { indeterminateReplays.put(receipt, new IndeterminateReplay(uuid, identity, checkpoint)); }
				try { reportDeferredStorageFailure(rejected); } catch (RuntimeException | Error ignored) { }
			}
			return;
		} catch (Error uncertain) {
			synchronized (this) { indeterminateReplays.put(receipt, new IndeterminateReplay(uuid, identity, checkpoint)); }
			try { reportDeferredStorageFailure(uncertain); } catch (RuntimeException | Error ignored) { } return;
		}
		finishSharedIntegerMutation(uuid, receipt, result, null);
	}

	/** Nonblocking admission prevents a synchronous writer overtaking a queued award. */
	synchronized boolean beginSharedSynchronousIntegerMutation(UUID uuid) {
		boolean worker = uuid.equals(sharedIntegerWorker.get()) && sharedIntegerSynchronousDepth.get() == 0
				&& !Boolean.TRUE.equals(sharedIntegerCompletion.get());
		if (!worker && (sharedIntegerClosing || sharedIntegerOwnerReplacement || sharedIntegerQueuedUsers.containsKey(uuid))) return false;
		if (sharedIntegerAdmissions++ == 0) sharedIntegerDrain = new CompletableFuture<>();
		sharedIntegerSynchronousDepth.set(sharedIntegerSynchronousDepth.get() + 1);
		sharedIntegerActiveUsers.merge(uuid, 1, Integer::sum);
		return true;
	}
	void endSharedSynchronousIntegerMutation(UUID uuid) {
		java.util.ArrayList<Runnable> failures = new java.util.ArrayList<Runnable>();
		synchronized (this) {
		int depth = sharedIntegerSynchronousDepth.get() - 1;
		if (depth == 0) sharedIntegerSynchronousDepth.remove(); else sharedIntegerSynchronousDepth.set(depth);
		sharedIntegerActiveUsers.computeIfPresent(uuid, (key, count) -> count == 1 ? null : count - 1);
		if (!sharedIntegerActiveUsers.containsKey(uuid) && !sharedIntegerCompletingUsers.contains(uuid)
				&& !uuid.equals(sharedIntegerWorker.get())) {
			java.util.ArrayDeque<com.bencodez.advancedcore.api.user.usercache.UserDataManager.DeferredSharedInteger> queue = sharedIntegerDeferred.get(uuid);
			if (queue != null && !queue.isEmpty()) {
				com.bencodez.advancedcore.api.user.usercache.UserDataManager.DeferredSharedInteger mutation = queue.peekFirst();
				try { timer.execute(mutation.action()); }
				catch (RuntimeException | Error failure) { failures.add(() -> mutation.reject().accept(failure)); }
			}
		}
		if (--sharedIntegerAdmissions == 0) sharedIntegerDrain.complete(null);
		}
		for (java.lang.Runnable failure : failures) failure.run();
	}

	/** Fail fast rather than close an owner that still has retained integer operations. */
	public void withLegacyIntegerOwnerReplacement(Runnable replacement) {
		Objects.requireNonNull(replacement, "replacement");
		if (Boolean.TRUE.equals(legacyIntegerOwnerReplacementThread.get())) { replacement.run(); return; }
		synchronized (this) {
			if (sharedIntegerOwnerReplacement || sharedIntegerClosing || sharedIntegerAdmissions != 0)
				throw new IllegalStateException("Shared integer mutations are active");
			sharedIntegerOwnerReplacement = true;
		}
		try {
		synchronized (legacyRetirementAdmission) {
			if (legacyIntegerOwnerReplacement || legacyRetirementActive || legacyRetirementRequested || legacyIntegerShutdown)
				throw new IllegalStateException("Legacy owner lifecycle is already active");
			if (activeLegacyIntegerApplications != 0) throw new IllegalStateException("Legacy integer mutations are active");
			synchronized (legacyMutationAdmission) {
				if (pendingLegacyIntegerCount != 0) throw new IllegalStateException("Legacy integer mutations are still pending");
			}
			legacyIntegerOwnerReplacement = true;
		}
		legacyIntegerOwnerReplacementThread.set(true);
		try { replacement.run(); }
		finally {
			legacyIntegerOwnerReplacementThread.remove();
			synchronized (legacyRetirementAdmission) { legacyIntegerOwnerReplacement = false; }
		}
		} finally { synchronized (this) { sharedIntegerOwnerReplacement = false; } }
	}

	/** Observe the currently admitted logical mutations for this user. */
	public CompletionStage<Void> pendingLegacyIntegerMutations(UUID uuid) {
		Objects.requireNonNull(uuid, "uuid");
		synchronized (legacyMutationAdmission) {
			java.util.ArrayDeque<com.bencodez.advancedcore.api.user.usercache.UserDataManager.PendingLegacyIntegerMutation> queue = pendingLegacyIntegers.get(uuid);
			java.util.ArrayList<CompletableFuture<?>> pending = new java.util.ArrayList<>();
			if (queue != null) for (PendingLegacyIntegerMutation item : queue) pending.add(item.completion.finalized);
			Set<PendingLegacyIntegerMutation> callbacks = legacyCallbackReceipts.get(uuid);
			if (callbacks != null) for (PendingLegacyIntegerMutation item : callbacks) pending.add(item.completion.finalized);
			return guardedObservation(CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new)));
		}
	}

	/** Observe shared operations already admitted for this user without cancelling them. */
	public CompletionStage<Void> pendingSharedIntegerMutations(UUID uuid) {
		Objects.requireNonNull(uuid, "uuid");
		synchronized (this) {
			java.util.ArrayDeque<com.bencodez.advancedcore.api.user.usercache.UserDataManager.DeferredSharedInteger> queue = sharedIntegerDeferred.get(uuid);
			java.util.ArrayList<CompletableFuture<?>> pending = new java.util.ArrayList<>();
			if (queue != null) for (DeferredSharedInteger item : queue) {
				pending.add(item.receipt()); pending.add(item.receipt().finalized);
			}
			Set<MutationReceipt<?>> callbacks = sharedCallbackReceipts.get(uuid);
			if (callbacks != null) for (MutationReceipt<?> item : callbacks) { pending.add(item); pending.add(item.finalized); }
			return guardedObservation(CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new)));
		}
	}
	private CompletionStage<Void> guardedObservation(CompletableFuture<Void> source) {
		GuardedMutationFuture<Void> view = new GuardedMutationFuture<>(this);
		source.whenComplete((value, failure) -> { if (failure == null) view.complete(value); else view.completeExceptionally(failure); });
		return view;
	}

	/** Close new legacy arithmetic admission before the storage owner is shut down. */
	public CompletionStage<Void> shutdownLegacyIntegerMutations() {
		if (UserDataCache.hasLiveLegacyCallback(this) || Boolean.TRUE.equals(legacyIntegerNotification.get())
				|| Boolean.TRUE.equals(legacyIntegerCompletion.get()))
			return CompletableFuture.failedFuture(new IllegalStateException("Cache retirement cannot await its own direct-write callback"));
		closeLegacyIntegerAdmissionForShutdown();
		CompletableFuture<Void> completion = new CompletableFuture<>();
		try {
			timer.execute(() -> {
				try {
					clearCacheAsyncCompletion().whenComplete((ignored, failure) -> {
						if (failure == null) completion.complete(null); else completion.completeExceptionally(failure);
					});
				} catch (RuntimeException | Error failure) {
					reportDeferredStorageFailure(failure);
					completion.completeExceptionally(failure);
				}
			});
		} catch (RejectedExecutionException failure) {
			reportDeferredStorageFailure(failure);
			completion.completeExceptionally(failure);
		}
		return completion.minimalCompletionStage();
	}
	public void closeLegacyIntegerAdmissionForShutdown() {
		synchronized (legacyRetirementAdmission) { legacyIntegerShutdown = true; }
	}

	/** A forced owner close cannot safely pass a retained or active arithmetic operation. */
	public boolean hasUnsettledLegacyIntegerMutations() {
		synchronized (this) { if (sharedIntegerAdmissions != 0 || sharedIntegerOwnerReplacement) return true; }
		synchronized (legacyRetirementAdmission) {
			if (activeLegacyIntegerApplications != 0) return true;
			synchronized (legacyMutationAdmission) { return pendingLegacyIntegerCount != 0; }
		}
	}

	private void scheduleLegacyIntegerMutation(PendingLegacyIntegerMutation pending, long delayMillis) {
		try { timer.schedule(() -> runLegacyIntegerMutation(pending), delayMillis, TimeUnit.MILLISECONDS); }
		catch (RejectedExecutionException failure) { finishLegacyIntegerMutation(pending, null, failure); }
	}
	private void retryLegacyIntegerMutation(PendingLegacyIntegerMutation pending) {
		if (++pending.attempts >= MAX_LEGACY_MUTATION_ATTEMPTS) {
			finishLegacyIntegerMutation(pending, null, new IllegalStateException("Legacy integer mutation could not acquire an authoritative cache"));
			return;
		}
		scheduleLegacyIntegerMutation(pending, 25);
	}

	private void runLegacyIntegerMutation(PendingLegacyIntegerMutation pending) {
		try {
			requirePendingLegacyOwner(pending);
			UserDataCache.IntegerMutationResult result;
			java.util.concurrent.locks.ReentrantLock stripe = legacyMutationStripe(pending.uuid);
			if (!stripe.tryLock()) { retryLegacyIntegerMutation(pending); return; }
			try {
			synchronized (legacyMutationAdmission) {
				java.util.ArrayDeque<com.bencodez.advancedcore.api.user.usercache.UserDataManager.PendingLegacyIntegerMutation> queue = pendingLegacyIntegers.get(pending.uuid);
				if (queue == null || queue.peekFirst() != pending) return;
			}
			requirePendingLegacyOwner(pending);
			result = tryMutateLegacyIntegersAdmitted(pending.uuid, pending.values, pending.operation);
			} finally { stripe.unlock(); }
			if (result == null) {
				com.bencodez.advancedcore.api.user.usercache.UserDataManager.LegacyPopulationResult population = populateCompleteLegacyCacheIfSupported(pending.uuid);
				if (population == LegacyPopulationResult.UNSUPPORTED) throw new IllegalStateException("Legacy reader does not support strict integer mutation");
				if (population == LegacyPopulationResult.PUBLISHED) {
					if (!stripe.tryLock()) { retryLegacyIntegerMutation(pending); return; }
					try {
						requirePendingLegacyOwner(pending);
						result = tryMutateLegacyIntegersAdmitted(pending.uuid, pending.values, pending.operation);
					} finally { stripe.unlock(); }
				}
			}
			if (result != null) { finishLegacyIntegerMutation(pending, result, null); return; }
			retryLegacyIntegerMutation(pending);
		} catch (UserDataCache.LegacyIntegerMutationUncertainException uncertain) {
			// Some cache changes may already exist. Keep the original ticket and
			// retirement admission unresolved; a blind retry could award twice.
			try { reportDeferredStorageFailure(uncertain); } catch (RuntimeException | Error ignored) { }
		} catch (RuntimeException | Error failure) { finishLegacyIntegerMutation(pending, null, failure); }
	}
	private void requirePendingLegacyOwner(PendingLegacyIntegerMutation pending) {
		if (!Objects.equals(plugin.getNativeUserStorageOwner(), pending.owner) || !legacyOwnerMatchesProvider(pending.owner))
			throw new IllegalStateException("Legacy integer mutation storage owner changed");
	}

	private void finishLegacyIntegerMutation(PendingLegacyIntegerMutation pending,
			UserDataCache.IntegerMutationResult result, Throwable failure) {
		PendingLegacyIntegerMutation next;
		synchronized (legacyMutationAdmission) {
			java.util.ArrayDeque<com.bencodez.advancedcore.api.user.usercache.UserDataManager.PendingLegacyIntegerMutation> queue = pendingLegacyIntegers.get(pending.uuid);
			if (queue == null || queue.peekFirst() != pending) return;
			queue.removeFirst();
			legacyCallbackReceipts.computeIfAbsent(pending.uuid, ignored -> new java.util.HashSet<>()).add(pending);
			next = queue.peekFirst(); if (next == null) pendingLegacyIntegers.remove(pending.uuid);
		}
		if (next != null) scheduleLegacyIntegerMutation(next, 0);
		try {
			mutationCallbacks.execute(() -> {
				legacyIntegerCompletion.set(true);
				try {
					if (failure == null) {
						legacyIntegerNotification.set(true);
						try { notifyLegacyIntegerMutation(result, pending.operation, pending.notification); }
						catch (RuntimeException | Error notificationFailure) {
							try { reportDeferredStorageFailure(notificationFailure); } catch (RuntimeException | Error ignored) { }
						} finally { legacyIntegerNotification.remove(); }
						pending.completion.succeed(result);
					} else pending.completion.fail(failure);
				}
				finally {
					legacyIntegerCompletion.remove();
					endLegacyCacheBatch();
					synchronized (legacyMutationAdmission) {
						Set<PendingLegacyIntegerMutation> callbacks = legacyCallbackReceipts.get(pending.uuid);
						callbacks.remove(pending); if (callbacks.isEmpty()) legacyCallbackReceipts.remove(pending.uuid);
						pendingLegacyIntegerCount--;
					}
					if (failure == null) pending.completion.finalized.complete(null);
					else pending.completion.finalized.completeExceptionally(failure);
				}
			});
		} catch (RuntimeException | Error rejected) {
			try { reportDeferredStorageFailure(rejected); } catch (RuntimeException | Error ignored) { }
		}
	}

	/** Fence a direct legacy writer against stale row publication. */
	public LegacyDirectWrite beginLegacyDirectWrite(UUID uuid) { return beginLegacyDirectWrite(uuid, Set.of()); }

	public LegacyDirectWrite beginLegacyDirectWrite(UUID uuid, String key) { return beginLegacyDirectWrite(uuid, key == null ? Set.of() : Set.of(key)); }

	public LegacyDirectWrite beginLegacyDirectWrite(UUID uuid, Set<String> keys) {
		return beginLegacyDirectWrite(uuid, keys, keys.stream().anyMatch(key -> key.startsWith("SQLITE:")) ? uuid.toString() : null);
	}
	public LegacyDirectWrite beginLegacyDirectWrite(UUID uuid, Set<String> keys, String sqliteRow) {
		return beginLegacyDirectWrite(uuid, keys, sqliteRow, null);
	}
	private LegacyDirectWrite beginLegacyDirectWrite(UUID uuid, Set<String> keys, String sqliteRow, LegacyDirectWrite parent) {
		Objects.requireNonNull(uuid, "uuid");
		boolean borrowed = parent != null || UserDataCache.hasActiveLegacyBatch(this, uuid);
		String identity = sqliteRow == null ? "UUID:" + uuid : "SQLITE:" + sqliteRow;
		long sequence;
		synchronized (legacyRetirementAdmission) {
			if (!borrowed && (legacyRetirementActive || legacyRetirementRequested)) throw new IllegalStateException("Legacy cache retirement is active");
			if (parent != null && parent.ended.get()) throw new IllegalStateException("Legacy direct admission already ended");
			if (legacyDirectAdmissions == 0) { legacyDirectDrain = new CompletableFuture<>(); legacyDirectFailure = null; }
			legacyDirectAdmissions++;
			legacyDirectAdmissionsByUser.merge(identity, 1, Integer::sum);
			sequence = ++legacyDirectSequence;
		}
		try { if (parent == null) beginLegacyCacheBatch(); }
		catch (IllegalStateException transitioning) {
			endLegacyDirectAdmission(identity);
			return null;
		}
		boolean admitted = false;
		LegacyWriteState state = null;
		try {
			LegacyRead nested = parent == null && (sqliteRow == null || sqliteRow.equals(uuid.toString()))
					? UserDataCache.disjointNestedLegacyRead(this, uuid, keys) : null;
			LegacyRead overlap = parent == null && (sqliteRow == null || sqliteRow.equals(uuid.toString()))
					? UserDataCache.overlappingNestedRead(this, uuid, keys) : null;
			boolean[] nestedAdmitted = { false };
			boolean[] overlapAdmitted = { false };
			state = legacyWriteStates.compute(uuid, (id, previous) -> {
				LegacyWriteState current = previous == null ? new LegacyWriteState() : previous;
				overlapAdmitted[0] = overlap != null && overlap.active && !overlap.writing && overlap.callback == null
						&& overlap.state == current && overlap.version == current.version && current.writers == 0;
				boolean ownNested = nested != null && nested.active && !nested.writing && nested.callback == null
						&& nested.state == current && nested.version == current.version && current.writers == nested.nestedWriters;
				current.writers++; current.version++;
				if (overlapAdmitted[0] && overlap.historySource != null && overlap.historyProvider != null
						&& overlap.historyVersion == overlap.version) overlap.pendingOverlapWriters++;
				if (ownNested) { nested.nestedWriters++; nested.version = current.version; nestedAdmitted[0] = true; }
				return current;
			});
			UserStorage requested = sqliteRow == null ? null : UserStorage.SQLITE;
			if (requested == null) for (String key : keys) {
				int separator = key.indexOf(':');
				if (separator > 0) try { requested = UserStorage.valueOf(key.substring(0, separator)); } catch (IllegalArgumentException ignored) { }
				if (requested != null) break;
			}
			UserStorage configured = plugin == null ? null : plugin.getStorageType();
			boolean sameStorage = requested == null || configured == null || requested == configured;
			UserDataCache cache = sameStorage && (sqliteRow == null || sqliteRow.equals(uuid.toString())) ? userDataCache.get(uuid) : null;
			if (cache != null && !cache.matchesLegacyStorage(requested)) cache = null;
			long cacheVersion = cache == null ? -1 : cache.beginLegacyDirectWrite();
			if (cacheVersion < 0) cache = null;
			admitted = true;
			LegacyDirectWrite result = new LegacyDirectWrite(uuid, sqliteRow, cache, state, keys, sequence, cacheVersion);
			result.borrowedBatch = parent != null;
			if (nestedAdmitted[0]) result.nestedRead = nested;
			if (overlapAdmitted[0]) {
				result.overlapRead = overlap;
				if (overlap.pendingOverlapWriters > 0 && overlap.historyProvider instanceof com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL mysql)
					result.capturedNestedMysql = mysql;
				if (overlap.pendingOverlapWriters > 0 && overlap.historyMode == 0
						&& overlap.historySource instanceof com.bencodez.advancedcore.api.user.userstorage.sql.UserTable sqlite)
					result.capturedNestedSqlite = sqlite;
			}
			return result;
		} finally {
			if (!admitted) {
				try { if (state != null) finishLegacyDirectState(uuid, state); }
				finally {
					try { if (parent == null) endLegacyCacheBatch(); }
					finally { endLegacyDirectAdmission(identity); }
				}
			}
		}
	}

	boolean isLiveLegacyDirectWrite(LegacyDirectWrite admission) {
		return admission != null && !admission.ended.get();
	}

	public void endLegacyDirectWrite(LegacyDirectWrite admission) {
		if (admission == null) return;
		if (admission.redirected != null) endLegacyDirectWrite(admission.redirected);
		for (LegacyDirectWrite guard : admission.destinationGuards.values()) endLegacyDirectWrite(guard);
		if (!admission.ended.compareAndSet(false, true)) throw new IllegalStateException("Legacy direct write already ended");
		if (admission.successful.get() && !admission.failureRecorded.get()) synchronized (legacyRetirementAdmission) {
			java.util.Map<java.lang.String,java.lang.Long> failed = legacyUnrecoveredWrites.get(admission.identity());
			for (String key : admission.keys) {
				legacyDirectSuccesses.computeIfAbsent(admission.identity(), ignored -> new java.util.HashMap<>()).merge(key, admission.sequence, Math::max);
				if (failed != null && failed.getOrDefault(key, Long.MAX_VALUE) < admission.sequence) failed.remove(key);
			}
			if (failed != null && failed.isEmpty()) legacyUnrecoveredWrites.remove(admission.identity());
		}
		try { if (admission.cache != null) admission.cache.endLegacyDirectWrite(); }
		finally {
			try { finishLegacyDirectState(admission.uuid, admission.state, admission); }
			finally {
				try { if (!admission.borrowedBatch) endLegacyCacheBatch(); }
				finally { endLegacyDirectAdmission(admission.identity()); }
			}
		}
	}
	/** Admit a possible SQLite primary destination before the physical write. */
	public void guardLegacySqliteDestination(LegacyDirectWrite admission, com.bencodez.simpleapi.sql.data.DataValue value) {
		if (admission == null || value == null || !value.isString() || admission.sqliteRow == null) return;
		String row = value.getString();
		if (row == null) return;
		admission.possibleDestinations.add(row);
		if (row.equals(admission.sqliteRow) || admission.destinationGuards.containsKey(row)) return;
		try {
			UUID target = UUID.fromString(row);
			admission.destinationGuards.put(row, beginLegacyDirectWrite(target, Set.of(UserDataCache.sqliteDirectKey("uuid")), row, admission));
		} catch (IllegalArgumentException invalidUuid) { /* Preserve non-UUID storage identities. */ }
	}

	/** Check a direct write against later completed writes without waiting for storage. */
	LegacyDirectWrite bindLegacyDirectRow(LegacyDirectWrite original, String row, boolean sqlite) {
		if (original == null || isLegacyDirectIdentity(original, row)) return original;
		if (original.redirected != null) {
			if (isLegacyDirectIdentity(original.redirected, row)) return original.redirected;
			throw new IllegalStateException("Legacy write identity changed during persistence");
		}
		UUID actual = UUID.fromString(row);
		LegacyDirectWrite redirected = beginLegacyDirectWrite(actual, original.keys, sqlite ? row : null, original);
		if (original.cache != null) original.cache.restoreRedirectedPublication(original.priorPublication, original.cacheVersion);
		redirected.destinationGuards.putAll(original.destinationGuards);
		original.destinationGuards.clear();
		original.redirected = redirected;
		for (String destination : original.possibleDestinations)
			guardLegacySqliteDestination(redirected, new com.bencodez.simpleapi.sql.data.DataValueString(destination));
		return redirected;
	}
	private LegacyDirectWrite effectiveLegacyDirectWrite(LegacyDirectWrite admission) {
		while (admission != null && admission.redirected != null) admission = admission.redirected;
		return admission;
	}
	boolean isLegacyDirectIdentity(LegacyDirectWrite admission, String row) { return admission != null && (admission.sqliteRow != null ? admission.sqliteRow.equals(row) : admission.uuid.equals(UUID.fromString(row))); }
	boolean isRelatedLegacyBatchFailure(LegacyDirectWrite admission, UUID uuid, UserStorage storage, Object values) {
		if (admission == null || uuid == null || storage == null || !(values instanceof Map<?, ?> map)
				|| !admission.uuid.equals(uuid)) return false;
		if (storage == UserStorage.SQLITE ? !uuid.toString().equals(admission.sqliteRow) : admission.sqliteRow != null) return false;
		for (Object key : map.keySet()) if (key instanceof String name) {
			String identity = storage == UserStorage.SQLITE ? UserDataCache.sqliteDirectKey(name) : storage.name() + ":" + name;
			if (admission.keys.contains(identity)) return true;
		}
		return false;
	}
	public void configureSqliteBulkPrimary(LegacyDirectWrite admission, boolean insertChanges, boolean updateChanges) {
		if (admission != null) { admission.sqliteInsertChangesPrimary = insertChanges; admission.sqliteUpdateChangesPrimary = updateChanges; }
	}
	void recordSqliteDirectStatement(LegacyDirectWrite admission, boolean insertStatement) {
		if (admission != null) {
			admission.sqliteInsertStatement = insertStatement;
			Boolean changes = insertStatement ? admission.sqliteInsertChangesPrimary : admission.sqliteUpdateChangesPrimary;
			if (changes != null) synchronized (legacyRetirementAdmission) {
				String key = UserDataCache.sqliteDirectKey("uuid");
				if (changes) admission.keys.add(key); else admission.keys.remove(key);
			}
		}
	}
	boolean hasLegacyDirectKey(LegacyDirectWrite admission, String key) { return admission != null && admission.keys.contains(key); }

	boolean isCurrentLegacyDirectKey(LegacyDirectWrite admission, String row, String storageKey) {
		if (admission == null || !isLegacyDirectIdentity(admission, row)
				|| (!admission.keys.contains(storageKey) && !storageKey.equals(UserDataCache.sqliteDirectKey("uuid")))) return true;
		synchronized (legacyRetirementAdmission) {
			return legacyDirectSuccesses.getOrDefault(admission.identity(), java.util.Map.of()).getOrDefault(storageKey, 0L) <= admission.sequence;
		}
	}
	boolean isCurrentMysqlDirectKey(LegacyDirectWrite admission, String row, String key, boolean foldColumns) {
		String identity = UserStorage.MYSQL.name() + ":" + key;
		if (!foldColumns) return isCurrentLegacyDirectKey(admission, row, identity);
		if (admission == null || !isLegacyDirectIdentity(admission, row) || !admission.keys.contains(identity)) return true;
		synchronized (legacyRetirementAdmission) {
			return legacyDirectSuccesses.getOrDefault(admission.identity(), java.util.Map.of())
					.getOrDefault(mysqlFoldedKey(identity), 0L) <= admission.sequence;
		}
	}
	private static String mysqlFoldedKey(String identity) {
		StringBuilder folded = new StringBuilder("MYSQL_FOLDED:");
		for (int i = 0; i < identity.length(); i++) folded.append(Character.toLowerCase(Character.toUpperCase(identity.charAt(i))));
		return folded.toString();
	}

	public void markLegacyDirectWriteSucceeded(LegacyDirectWrite admission) {
		admission = effectiveLegacyDirectWrite(admission);
		if (admission != null && !admission.ended.get()) admission.successful.set(true);
	}
	void recordLegacyDirectCachePublication(LegacyDirectWrite admission, UserDataCache cache, long version) {
		if (admission != null && !admission.ended.get() && admission.cache == cache) admission.cacheVersion = version;
	}
	void rememberLegacyDirectCachePublication(LegacyDirectWrite admission, UserDataCache cache, UserDataCache.DirectCachePublication prior) {
		if (admission != null && !admission.ended.get() && admission.cache == cache) admission.priorPublication = prior;
	}
	boolean matchesLegacyDirectCacheRow(LegacyDirectWrite admission, UserDataCache cache) {
		return admission == null || admission.sqliteRow == null || (cache.getUuid() != null && admission.sqliteRow.equals(cache.getUuid().toString()));
	}
	long legacyDirectSequence(LegacyDirectWrite admission) { return admission.sequence; }

	/** Publish guarded keys while SQLite still holds its committing writer lock. */
	void recordSqliteDirectCommit(LegacyDirectWrite admission, Set<String> keys, Object provider) {
		if (admission == null || admission.ended.get()) return;
		synchronized (legacyRetirementAdmission) {
			if (provider != null) admission.confirmedProvider = provider;
			java.util.Map<java.lang.String,java.lang.Long> successes = legacyDirectSuccesses.computeIfAbsent(admission.identity(), ignored -> new java.util.HashMap<>());
			for (String key : keys) if (admission.keys.contains(key)) {
				admission.confirmedKeys.add(key);
				long previous = successes.getOrDefault(key, 0L);
				admission.sqliteCommitPrevious.putIfAbsent(key, previous);
				successes.merge(key, admission.sequence, Math::max);
			}
		}
	}
	void recordSqliteDestinationCommit(LegacyDirectWrite admission, String row) {
		if (admission == null || row == null || !admission.keys.contains(UserDataCache.sqliteDirectKey("uuid"))) return;
		LegacyDirectWrite guard = admission.destinationGuards.get(row);
		if (guard != null) recordSqliteDirectCommit(guard, Set.of(UserDataCache.sqliteDirectKey("uuid")), null);
	}
	/** Publish completed native-provider keys before releasing its writer lock. */
	void recordLegacyProviderCommit(LegacyDirectWrite admission, Set<String> keys, boolean foldColumns, Object provider) {
		if (admission == null || admission.ended.get() || admission.failureRecorded.get()) return;
		synchronized (legacyRetirementAdmission) {
			if (provider != null) { admission.confirmedProvider = provider; admission.confirmedMysqlFold = foldColumns; }
			java.util.Map<java.lang.String,java.lang.Long> successes = legacyDirectSuccesses.computeIfAbsent(admission.identity(), ignored -> new java.util.HashMap<>());
			java.util.Map<java.lang.String,java.lang.Long> failed = legacyUnrecoveredWrites.get(admission.identity());
			for (String key : keys) if (admission.keys.contains(key)) {
				admission.confirmedKeys.add(key);
				successes.merge(key, admission.sequence, Math::max);
				if (foldColumns) successes.merge(mysqlFoldedKey(key), admission.sequence, Math::max);
				if (foldColumns && failed != null && failed.size() <= 2048)
					failed.entrySet().removeIf(entry -> entry.getKey().equalsIgnoreCase(key) && entry.getValue() < admission.sequence);
			}
			if (failed != null && failed.isEmpty()) legacyUnrecoveredWrites.remove(admission.identity());
		}
	}

	/** Undo a commit-hook observation when SQLite reports a rollback. */
	void rollbackSqliteDirectCommit(LegacyDirectWrite admission) {
		if (admission == null) return;
		for (LegacyDirectWrite guard : admission.destinationGuards.values()) rollbackSqliteDirectCommit(guard);
		synchronized (legacyRetirementAdmission) {
			java.util.Map<java.lang.String,java.lang.Long> successes = legacyDirectSuccesses.get(admission.identity());
			if (successes != null) for (java.util.Map.Entry<java.lang.String,java.lang.Long> entry : admission.sqliteCommitPrevious.entrySet()) {
				if (successes.getOrDefault(entry.getKey(), 0L) == admission.sequence) {
					if (entry.getValue() == 0) successes.remove(entry.getKey());
					else successes.put(entry.getKey(), entry.getValue());
				}
			}
			admission.sqliteCommitPrevious.clear();
			admission.confirmedKeys.clear();
			admission.confirmedProvider = null;
		}
	}

	/** Fence arithmetic on the previous row while an external bulk write is admitted. */
	public void invalidateLegacyBulkSnapshot(LegacyDirectWrite admission) {
		if (admission != null && admission.cache != null && !admission.ended.get())
			admission.cache.invalidateLegacyBulkSnapshot();
	}

	/** Reconcile only the keys not superseded by a completed newer direct write. */
	public void completeLegacyBulkValues(LegacyDirectWrite admission, java.util.Map<String, com.bencodez.simpleapi.sql.data.DataValue> values,
			java.util.function.Function<String, String> identity) {
		admission = effectiveLegacyDirectWrite(admission);
		if (admission == null || admission.ended.get() || admission.failureRecorded.get()
				|| (admission.cache == null && admission.destinationGuards.isEmpty())) return;
		java.util.Map<String, com.bencodez.simpleapi.sql.data.DataValue> accepted;
		synchronized (legacyRetirementAdmission) {
			java.util.Map<java.lang.String,java.lang.Long> successes = legacyDirectSuccesses.computeIfAbsent(admission.identity(), ignored -> new java.util.HashMap<>());
			java.util.LinkedHashMap<java.lang.String,java.util.Map.Entry<java.lang.String,com.bencodez.simpleapi.sql.data.DataValue>> selected = new java.util.LinkedHashMap<String, java.util.Map.Entry<String, com.bencodez.simpleapi.sql.data.DataValue>>();
			boolean mysqlFold = plugin != null && plugin.getMysql() != null && Boolean.TRUE.equals(plugin.getMysql().caseInsensitiveColumnNames());
			for (java.util.Map.Entry<java.lang.String,com.bencodez.simpleapi.sql.data.DataValue> entry : values.entrySet()) {
				if (entry.getKey() == null) continue;
				String key = identity.apply(entry.getKey());
				if (admission.cache == null && !key.equals(UserDataCache.sqliteDirectKey("uuid"))) continue;
				long latest = successes.getOrDefault(key, 0L);
				if (key.startsWith("MYSQL:") && mysqlFold)
					latest = Math.max(latest, successes.getOrDefault(mysqlFoldedKey(key), 0L));
				if (admission.keys.contains(key) && latest <= admission.sequence) {
					String selectedKey = key.startsWith("MYSQL:") && mysqlFold ? mysqlFoldedKey(key) : key;
					java.util.Map.Entry<java.lang.String,com.bencodez.simpleapi.sql.data.DataValue> effective = entry;
					if (Boolean.TRUE.equals(admission.sqliteInsertStatement) && admission.sqliteInsertChangesPrimary == null
							&& key.equals(UserDataCache.sqliteDirectKey("uuid")) && admission.sqliteRow != null)
						effective = new java.util.AbstractMap.SimpleImmutableEntry<>(entry.getKey(), new com.bencodez.simpleapi.sql.data.DataValueString(admission.sqliteRow));
					if (Boolean.TRUE.equals(admission.sqliteInsertStatement)) selected.putIfAbsent(selectedKey, effective);
					else selected.put(selectedKey, effective);
					successes.merge(key, admission.sequence, Math::max);
				}
			}
			accepted = new java.util.LinkedHashMap<>();
			for (java.util.Map.Entry<java.lang.String,com.bencodez.simpleapi.sql.data.DataValue> entry : selected.values()) accepted.put(entry.getKey(), entry.getValue());
			if (admission.cache != null) admission.cache.completeLegacyBulkValues(accepted, admission.cacheVersion, identity);
		}
		for (java.util.Map.Entry<java.lang.String,com.bencodez.simpleapi.sql.data.DataValue> entry : accepted.entrySet()) if (identity.apply(entry.getKey()).equals(UserDataCache.sqliteDirectKey("uuid"))
				&& entry.getValue() != null && entry.getValue().isString()) {
			LegacyDirectWrite guard = admission.destinationGuards.get(entry.getValue().getString());
			if (guard != null) {
				UserDataCache target = guard.sqliteRow.equals(guard.uuid.toString()) ? userDataCache.get(guard.uuid) : null;
				if (target != null) target.fenceIncomingSqliteMove(guard.cacheVersion);
			}
		}
	}

	public void failLegacyDirectWrite(LegacyDirectWrite admission,
			com.bencodez.advancedcore.api.user.usercache.change.UserDataChange change, Throwable failure) {
		admission = effectiveLegacyDirectWrite(admission);
		if (admission == null || admission.ended.get() || !admission.failureRecorded.compareAndSet(false, true)) return;
		boolean retained = false;
		// Bulk persistence intentionally excludes the exact primary-key name, so
		// queuing it would falsely acknowledge a failed scalar write as retried.
		boolean primaryAlias = change != null && change.getKey() != null && admission.sqliteRow != null
				&& UserDataCache.sqliteDirectKey(change.getKey()).equals(UserDataCache.sqliteDirectKey("uuid"));
		if (!(failure instanceof com.bencodez.advancedcore.api.user.userstorage.sql.UserTable.StaleRowWriteException)
				&& admission.cache != null && (change == null || (!"uuid".equals(change.getKey()) && !primaryAlias))) {
			try { admission.cache.retainFailedLegacyDirectChange(change, admission.cacheVersion,
					change != null && change.getKey() != null && admission.keys.contains(UserDataCache.sqliteDirectKey(change.getKey()))); retained = true; }
			catch (RuntimeException | Error retryFailure) { failure.addSuppressed(retryFailure); }
		}
		synchronized (legacyRetirementAdmission) {
			if (legacyDirectFailure == null) legacyDirectFailure = failure;
			if (!retained) for (String key : admission.keys) {
				java.util.Map<java.lang.String,java.lang.Long> successes = legacyDirectSuccesses.getOrDefault(admission.identity(), java.util.Map.of());
				long latest = successes.getOrDefault(key, 0L);
				if (key.startsWith("MYSQL:") && plugin != null && plugin.getMysql() != null && Boolean.TRUE.equals(plugin.getMysql().caseInsensitiveColumnNames()))
					latest = Math.max(latest, successes.getOrDefault(mysqlFoldedKey(key), 0L));
				if (latest < admission.sequence)
					legacyUnrecoveredWrites.computeIfAbsent(admission.identity(), ignored -> new java.util.HashMap<>()).merge(key, admission.sequence, Math::max);
			}
		}
	}

	private void endLegacyDirectAdmission(String identity) {
		CompletableFuture<Void> drained = null;
		Throwable failure = null;
		synchronized (legacyRetirementAdmission) {
			int remaining = legacyDirectAdmissionsByUser.getOrDefault(identity, 0) - 1;
			if (remaining < 0) throw new IllegalStateException("Legacy direct user admission is unbalanced");
			if (remaining == 0) { legacyDirectAdmissionsByUser.remove(identity); legacyDirectSuccesses.remove(identity); }
			else legacyDirectAdmissionsByUser.put(identity, remaining);
			if (--legacyDirectAdmissions == 0) { drained = legacyDirectDrain; failure = legacyDirectFailure; legacyDirectSuccesses.clear(); }
		}
		if (drained != null) {
			if (failure == null) drained.complete(null); else drained.completeExceptionally(failure);
		}
	}


	private void finishLegacyDirectState(UUID uuid, LegacyWriteState state) {
		finishLegacyDirectState(uuid, state, null);
	}
	private void finishLegacyDirectState(UUID uuid, LegacyWriteState state, LegacyDirectWrite admission) {
		legacyWriteStates.compute(uuid, (id, current) -> {
			if (current != state || current.writers <= 0) throw new IllegalStateException("Legacy write state changed");
			LegacyRead nested = admission == null ? null : admission.nestedRead;
			LegacyRead overlap = admission == null ? null : admission.overlapRead;
			boolean confirmedOverlap = overlap != null && overlap.active && admission.successful.get()
					&& !admission.failureRecorded.get() && admission.redirected == null && !admission.confirmedKeys.isEmpty()
					&& current.writers == 1 && current.version == overlap.version + 1;
			boolean ownDisjointCompletion = nested != null && nested.active && admission.successful.get()
					&& !admission.failureRecorded.get() && admission.redirected == null && admission.destinationGuards.isEmpty()
					&& current.version == nested.version && current.writers == nested.nestedWriters;
			if (nested != null) nested.nestedWriters--;
			if (overlap != null && (admission.capturedNestedMysql != null || admission.capturedNestedSqlite != null)) overlap.pendingOverlapWriters--;
			current.writers--; current.version++;
			boolean sqliteHistory = admission != null && admission.sqliteRow != null && admission.sqliteRow.equals(uuid.toString())
					&& !admission.keys.contains(UserDataCache.sqliteDirectKey("uuid"));
			boolean mysqlHistory = admission != null && admission.sqliteRow == null && !admission.keys.isEmpty()
					&& admission.keys.stream().allMatch(key -> key.startsWith("MYSQL:") && !key.substring(6).equalsIgnoreCase("uuid") && key.chars().allMatch(c -> c < 128));
			if (admission != null && admission.successful.get() && !admission.failureRecorded.get()
					&& admission.confirmedProvider != null && admission.redirected == null && admission.destinationGuards.isEmpty()
					&& (sqliteHistory || mysqlHistory) && !admission.keys.isEmpty() && admission.confirmedKeys.containsAll(admission.keys)) {
				int mode = sqliteHistory ? 0 : admission.confirmedMysqlFold ? 2 : 1;
				current.confirmedTransitions.computeIfAbsent(admission.confirmedProvider, ignored -> new long[3])[mode] += 2;
				java.util.Map<java.lang.String,java.lang.Long> versions = current.confirmedKeyVersions.computeIfAbsent(admission.confirmedProvider, ignored -> new java.util.HashMap<>());
				for (String key : admission.keys) versions.put(mode == 2 ? "MYSQLF:" + key.substring(6).toLowerCase(java.util.Locale.ROOT) : key, current.version);
			}
			if (confirmedOverlap) {
				overlap.confirmedOverlap.addAll(admission.confirmedKeys);
				overlap.confirmedOverlapVersion = current.version;
			}
			if (ownDisjointCompletion) nested.version = current.version;
			return current.writers == 0 && current.readers == 0 ? null : current;
		});
	}
	void beginConfirmedBatchHistory(LegacyRead read, Object provider, int mode, Object source) {
		if (read == null || provider == null || source == null) return;
		legacyWriteStates.computeIfPresent(read.uuid, (id, current) -> {
			if (read.active && !read.writing && current == read.state && current.writers == 0 && current.version == read.version) {
				read.historyProvider = provider;
				read.historySource = source;
				read.historyVersion = current.version;
				read.historyMode = mode;
				read.historyTransitions = current.confirmedTransitions.getOrDefault(provider, new long[3])[mode];
			}
			return current;
		});
	}
	Set<String> claimConfirmedNestedKeys(LegacyRead read, Object provider) {
		if (read == null) return Set.of();
		AtomicReference<Set<String>> result = new AtomicReference<>(Set.of());
		legacyWriteStates.computeIfPresent(read.uuid, (id, current) -> {
			if (provider != null && provider == read.historyProvider && read.historyVersion >= 0
					&& read.active && current == read.state && read.pendingOverlapWriters > 0
					&& current.writers == read.pendingOverlapWriters && current.version == read.historyVersion + read.pendingOverlapWriters
					&& ((read.historyMode == 0 && read.historySource instanceof com.bencodez.advancedcore.api.user.userstorage.sql.UserTable sqlite
							&& sqlite.legacyDirectProviderIdentity() == provider)
						|| (read.historyMode != 0 && provider instanceof com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL mysql
								&& java.util.Objects.equals(mysql.caseInsensitiveColumnNames(), read.historyMode == 2)))) {
				read.version = current.version;
				read.pendingOverlapAuthorized = true;
				return current;
			}
			if (provider != null && provider == read.historyProvider && read.historyVersion >= 0 && read.active
					&& current == read.state && current.writers == 0 && current.version > read.historyVersion
					&& current.version - read.historyVersion == current.confirmedTransitions.getOrDefault(provider, new long[3])[read.historyMode] - read.historyTransitions) {
				java.util.HashSet<String> confirmed = new java.util.HashSet<String>();
				current.confirmedKeyVersions.getOrDefault(provider, Map.of()).forEach((key, version) -> { if (version > read.historyVersion) confirmed.add(key); });
				read.version = current.version;
				read.filteredOverlap.addAll(confirmed);
				if (current.version > read.lastConfirmedClaimVersion) {
					read.lastConfirmedClaimVersion = current.version;
					read.confirmedClaimSerial++;
				}
				result.set(Set.copyOf(confirmed));
				return current;
			}
			if (read.active && current == read.state && current.writers == 0 && current.version == read.confirmedOverlapVersion
					&& !read.confirmedOverlap.isEmpty()) {
				read.version = current.version;
				read.filteredOverlap.addAll(read.confirmedOverlap);
				if (current.version > read.lastConfirmedClaimVersion) {
					read.lastConfirmedClaimVersion = current.version;
					read.confirmedClaimSerial++;
				}
				result.set(Set.copyOf(read.confirmedOverlap));
			}
			return current;
		});
		return result.get();
	}
	long confirmedNestedClaimSerial(LegacyRead read) {
		return read == null ? 0 : read.confirmedClaimSerial;
	}
	boolean capturedMysqlFoldsColumnNames(LegacyRead read) {
		return read != null && read.historyProvider != null && read.historyVersion >= 0 && read.historyMode == 2;
	}
	boolean wasConfirmedNestedKeyFiltered(LegacyRead read, String key, UserStorage storage) {
		return read != null && key != null && storage != null && (read.filteredOverlap.contains(storage == UserStorage.SQLITE
				? UserDataCache.sqliteDirectKey(key) : storage.name() + ":" + key)
				|| (storage == UserStorage.MYSQL && key.chars().allMatch(c -> c < 128) && read.filteredOverlap.contains("MYSQLF:" + key.toLowerCase(java.util.Locale.ROOT))));
	}
	com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL capturedNestedMysql(LegacyDirectWrite admission) {
		return admission == null ? null : admission.capturedNestedMysql;
	}
	com.bencodez.advancedcore.api.user.userstorage.sql.UserTable capturedNestedSqlite(LegacyDirectWrite admission) {
		return admission == null ? null : admission.capturedNestedSqlite;
	}


	/** Load a strict legacy row on a worker; false means its generation changed. */
	public boolean populateCompleteLegacyCache(UUID uuid) {
		return populateCompleteLegacyCache(uuid, null, false) == LegacyPopulationResult.PUBLISHED;
	}
	private boolean populateCompleteLegacyCache(UUID uuid, UserDataCache expectedCache) {
		return populateCompleteLegacyCache(uuid, expectedCache, false) == LegacyPopulationResult.PUBLISHED;
	}
	/** Distinguishes a custom reader without strict authority from a transient generation change. */
	public enum LegacyPopulationResult { PUBLISHED, RETRY, UNSUPPORTED }
	/** Load strict legacy data on a worker, preserving unsupported custom-reader behavior. */
	public LegacyPopulationResult populateCompleteLegacyCacheIfSupported(UUID uuid) {
		return populateCompleteLegacyCache(uuid, null, true);
	}
	private LegacyPopulationResult populateCompleteLegacyCache(UUID uuid, UserDataCache expectedCache, boolean checkCapability) {
		Objects.requireNonNull(uuid, "uuid");
		if (hasSharedSqlBackend() || isPlatformOwnedThread()) throw new IllegalStateException("Legacy row loading requires a storage worker");
		beginLegacyCacheBatch();
		try {
			LegacyWriteState state = legacyWriteStates.compute(uuid, (id, previous) -> {
				LegacyWriteState current = previous == null ? new LegacyWriteState() : previous;
				current.readers++; return current;
			});
			long[] expected = new long[1]; boolean[] writing = new boolean[1];
			legacyWriteStates.computeIfPresent(uuid, (id, current) -> { expected[0] = current.version; writing[0] = current.writers != 0; return current; });
			try {
				if (writing[0]) return LegacyPopulationResult.RETRY;
				UserStorage storage = plugin.getStorageType();
				AdvancedCorePlugin.UserStorageOwner owner = plugin.getNativeUserStorageOwner();
				Object provider = owner == null ? null : storage == UserStorage.MYSQL ? owner.mysql()
						: storage == UserStorage.SQLITE ? owner.table() : null;
				if (!legacyOwnerMatchesProvider(owner) || (storage != null && owner != null && owner.storageType() != storage))
					return LegacyPopulationResult.RETRY;
				com.bencodez.advancedcore.api.user.UserData capabilityData = checkCapability ? plugin.getUserManager().getUser(uuid, false).getUserData() : null;
				if (checkCapability && !com.bencodez.advancedcore.bukkit.user.storage.BukkitSqlUserStorage.withStrictOwner(plugin, owner,
						capabilityData::supportsStrictCompleteValues)) {
					if (plugin.getStorageType() != storage || !Objects.equals(plugin.getNativeUserStorageOwner(), owner)
							|| (owner != null && storage == UserStorage.MYSQL && plugin.getMysql() != provider)
							|| (owner != null && storage == UserStorage.SQLITE && plugin.getSQLiteUserTable() != provider)
							|| !legacyOwnerMatchesProvider(owner)) return LegacyPopulationResult.RETRY;
					return LegacyPopulationResult.UNSUPPORTED;
				}
				UserDataCache cache = expectedCache == null ? ensureSharedCachePlaceholder(uuid) : userDataCache.get(uuid);
				if (expectedCache != null && cache != expectedCache) return LegacyPopulationResult.RETRY;
				if (!cache.matchesLegacyStorage(storage) || !cache.matchesLegacyOwner(owner) || !legacyOwnerMatchesProvider(owner)
						|| (storage != null && owner != null && owner.storageType() != storage)) return LegacyPopulationResult.RETRY;
				long version = cache.getSharedSnapshotVersion();
				com.bencodez.advancedcore.api.user.UserData data = checkCapability ? capabilityData : plugin.getUserManager().getUser(uuid, false).getUserData();
				java.util.HashMap<java.lang.String,com.bencodez.simpleapi.sql.data.DataValue> row = com.bencodez.advancedcore.bukkit.user.storage.BukkitSqlUserStorage.withStrictOwner(plugin, owner,
						data::getValuesIfStrictComplete);
				if (row == null) throw new IllegalStateException("Complete legacy user row is unavailable");
				boolean sqlite = storage == UserStorage.SQLITE;
				com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL mysqlProvider = storage == UserStorage.MYSQL ? (owner == null ? plugin.getMysql() : owner.mysql()) : null;
				boolean mysqlFold = storage == UserStorage.MYSQL && mysqlProvider != null
						&& Boolean.TRUE.equals(mysqlProvider.caseInsensitiveColumnNames());
				java.util.HashSet<String> sqliteColumns = new java.util.HashSet<String>();
				java.util.TreeSet<String> mysqlColumns = new java.util.TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
				if (sqlite) for (String name : row.keySet()) if (name != null) sqliteColumns.add(UserDataCache.sqliteDirectKey(name));
				if (mysqlFold) for (String name : row.keySet()) if (name != null) mysqlColumns.add(name);
				for (UserDataKey key : getKeys()) {
					if ((!sqlite || sqliteColumns.add(UserDataCache.sqliteDirectKey(key.getKey()))) && (!mysqlFold || mysqlColumns.add(key.getKey()))) row.putIfAbsent(key.getKey(), key.getDefault());
				}
				boolean[] published = new boolean[1];
				legacyWriteStates.computeIfPresent(uuid, (id, current) -> {
					if (current == state && current.writers == 0 && current.version == expected[0] && userDataCache.get(uuid) == cache
							&& plugin.getStorageType() == storage && Objects.equals(plugin.getNativeUserStorageOwner(), owner)
							&& (owner == null || storage != UserStorage.MYSQL || plugin.getMysql() == provider)
							&& (owner == null || storage != UserStorage.SQLITE || plugin.getSQLiteUserTable() == provider)
							&& legacyOwnerMatchesProvider(owner))
						published[0] = cache.tryPublishCompleteLegacySnapshot(row, version);
					return current;
				});
				return published[0] ? LegacyPopulationResult.PUBLISHED : LegacyPopulationResult.RETRY;
			} finally {
				legacyWriteStates.compute(uuid, (id, current) -> {
					if (current != state || current.readers <= 0) throw new IllegalStateException("Legacy read state changed");
					current.readers--;
					return current.writers == 0 && current.readers == 0 ? null : current;
				});
			}
		} finally { endLegacyCacheBatch(); }
	}
	private boolean legacyOwnerMatchesProvider(AdvancedCorePlugin.UserStorageOwner owner) {
		if (owner == null) return true;
		if (owner.storageType() == UserStorage.SQLITE) return plugin.getSQLiteUserTable() == owner.table();
		if (owner.storageType() == UserStorage.MYSQL) return plugin.getMysql() == owner.mysql();
		return false;
	}
	public boolean containsKey(UUID uuid) { return userDataCache.containsKey(uuid); }
	public UserDataCache getCache(UUID uuid) {
		if (hasSharedSqlBackend() && isPlatformOwnedThread()) {
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
		return hasSharedSqlBackend() && isPlatformOwnedThread();
	}

	/**
	 * True for Bukkit's primary thread and Folia/Paper tick threads. These are
	 * platform-owned execution lanes and must never wait on JDBC/shared-runtime admission.
	 */
	public boolean isPlatformOwnedThread() {
		// This thread is created by our single-threaded executor, never by Bukkit.
		// Do not infer ownership from its name, plugin enabled state, or shutdown state.
		if (Thread.currentThread() == storageWorker) return false;
		Object server = Bukkit.getServer();
		if (server == null) return false;
		try { if (Bukkit.isPrimaryThread()) return true; }
		catch (RuntimeException ignored) { }
		return isFoliaTickThread(server);
	}

	/** Reflection-only Folia/Paper probe kept separate so it can be tested headlessly. */
	static boolean isFoliaTickThread(Object server) {
		if (server == null) return false;
		try {
			java.lang.reflect.Method global = server.getClass().getMethod("isGlobalTickThread");
			if (Boolean.TRUE.equals(global.invoke(server))) return true;
		} catch (ReflectiveOperationException | RuntimeException ignored) { }
		if (reflectiveStaticBoolean("ca.spottedleaf.moonrise.common.util.TickThread", "isTickThread")) return true;
		if (reflectiveStaticBoolean("io.papermc.paper.threadedregions.RegionizedServer", "isGlobalTickThread")) return true;
		try {
			Class<?> scheduler = Class.forName("io.papermc.paper.threadedregions.TickRegionScheduler");
			java.lang.reflect.Method current = scheduler.getMethod("getCurrentRegion");
			if (current.invoke(null) != null) return true;
		} catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) { }
		return false;
	}

	private static boolean reflectiveStaticBoolean(String className, String methodName) {
		try {
			Class<?> type = Class.forName(className);
			return Boolean.TRUE.equals(type.getMethod(methodName).invoke(null));
		} catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
			return false;
		}
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
		// Eviction must only retire an already-published cache. Loading a missing
		// user from storage just to remove it adds I/O and extends exclusive admission.
		UserDataCache cache = userDataCache.get(uuid);
		synchronized (legacyMutationAdmission) {
			if (pendingLegacyIntegers.containsKey(uuid)) throw new IllegalStateException("Legacy integer mutation is pending");
			legacyIntegerCacheRetirements.add(uuid);
		}
		try {
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
		} finally { synchronized (legacyMutationAdmission) { legacyIntegerCacheRetirements.remove(uuid); } }
	}

	public void updateCacheOnline() {
		for (Player p : Bukkit.getOnlinePlayers()) if (isCached(p.getUniqueId())) cacheUser(p.getUniqueId());
	}
}
