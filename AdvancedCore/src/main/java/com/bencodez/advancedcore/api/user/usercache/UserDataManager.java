package com.bencodez.advancedcore.api.user.usercache;

import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.player.UuidLookup;
import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.advancedcore.api.user.usercache.keys.UserDataKey;
import com.bencodez.advancedcore.api.user.usercache.keys.UserDataKeyBoolean;
import com.bencodez.advancedcore.api.user.usercache.keys.UserDataKeyInt;
import com.bencodez.advancedcore.api.user.usercache.keys.UserDataKeyString;
import com.bencodez.advancedcore.core.user.storage.SqlUserStorage;
import com.bencodez.advancedcore.core.user.storage.sql.SqlUserBackend;
import com.bencodez.simpleapi.debug.DebugLevel;

import lombok.Getter;

public class UserDataManager {
	@Getter private ArrayList<UserDataKey> keys;
	@Getter private ArrayList<String> intColumns;
	@Getter private ArrayList<String> booleanColumns;
	@Getter private AdvancedCorePlugin plugin;
	@Getter private ScheduledExecutorService timer;
	@Getter private ConcurrentHashMap<UUID, UserDataCache> userDataCache;

	private volatile Consumer<UserDataCache> sharedCacheInitializer;
	private volatile Consumer<UUID> sharedCacheRemovalListener;
	private volatile SharedSqlRoute sharedSqlRoute;
	private final AtomicReference<Throwable> lastDeferredStorageFailure = new AtomicReference<>();
	private final Object sharedBindingAdmission = new Object();
	private boolean sharedBindingTransition;
	private int legacyBatches;

	private record SharedSqlRoute(SqlUserBackend backend, BiConsumer<UUID, Runnable> gate,
			BiConsumer<UUID, Runnable> exclusiveGate) {
		SharedSqlRoute {
			Objects.requireNonNull(backend, "backend");
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
		sharedSqlRoute = new SharedSqlRoute(backend, gate, exclusiveGate);
	}

	/** Compatibility overload for adapters that only need the global lifecycle gate. */
	public final synchronized void bindSharedSqlBackend(SqlUserBackend backend, Consumer<Runnable> gate) {
		Objects.requireNonNull(gate, "gate");
		bindSharedSqlBackend(backend, (uuid, operation) -> gate.accept(operation));
	}

	public final synchronized void unbindSharedSqlBackend(SqlUserBackend expected) {
		SharedSqlRoute route = sharedSqlRoute;
		if (route != null && route.backend() == expected) sharedSqlRoute = null;
	}

	public final boolean hasSharedSqlBackend() { return sharedSqlRoute != null; }

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
		timer = Executors.newScheduledThreadPool(1);
		loadKeys();
		timer.scheduleAtFixedRate(() -> {
			if (plugin != null && plugin.isEnabled()) clearNonNeededCachedUsers();
		}, 60 * 3, 60 * 60, TimeUnit.SECONDS);
	}

	public void addKey(UserDataKey userDataKey) {
		keys.add(userDataKey);
		if (userDataKey instanceof UserDataKeyInt) intColumns.add(userDataKey.getKey());
		else if (userDataKey instanceof UserDataKeyBoolean) booleanColumns.add(userDataKey.getKey());
	}

	@Deprecated
	public void cacheUser(UUID uuid) {
		withSharedCacheAdmission(uuid, () -> cacheUserNow(uuid, true));
	}

	private void cacheUserNow(UUID uuid, boolean traceDevelopmentCall) {
		plugin.devDebug("Caching " + uuid.toString());
		if (traceDevelopmentCall && plugin.getOptions().getDebug().isDebug(DebugLevel.DEV)) {
			try { throw new Exception("caching here: " + uuid.toString()); }
			catch (Exception e) { e.printStackTrace(); }
		}
		if (userDataCache.containsKey(uuid)) {
			UserDataCache data = userDataCache.get(uuid);
			data.clearChanges();
			data.cache();
		} else {
			UserDataCache data = new UserDataCache(this, uuid).cache();
			if (data.hasCache()) userDataCache.put(uuid, data);
		}
	}

	public void cacheUser(UUID uuid, String playerName) {
		if (playerName != null && !playerName.isEmpty() && !plugin.getOptions().isOnlineMode()) {
			uuid = UUID.fromString(UuidLookup.getInstance().getUUID(playerName));
		}
		UUID target = uuid;
		withSharedCacheAdmission(target, () -> cacheUserNow(target, false));
	}

	public void cacheUserIfNeeded(UUID uuid) { if (!userDataCache.containsKey(uuid)) cacheUser(uuid); }

	/**
	 * Shared SQL writes are worker-only. Existing synchronous Bukkit clear/remove
	 * entry points hand the complete flush-and-remove sequence to the cache worker.
	 */
	boolean deferSharedStorageWork(Runnable task) {
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

	private void reportDeferredStorageFailure(Throwable failure) {
		lastDeferredStorageFailure.set(failure);
		if (plugin != null && plugin.getLogger() != null) {
			plugin.getLogger().log(java.util.logging.Level.SEVERE, "Deferred user-cache cleanup failed", failure);
		}
	}

	/** Last asynchronous cache-cleanup failure, retained for diagnosis and recovery. */
	public Throwable getLastDeferredStorageFailure() { return lastDeferredStorageFailure.get(); }

	public void clearCache() {
		if (deferSharedStorageWork(this::clearCacheNow)) return;
		clearCacheNow();
	}

	private void clearCacheNow() {
		plugin.debug("Clearing cache: " + userDataCache.keySet().size());
		Set<UUID> removed = new java.util.HashSet<>(userDataCache.keySet());
		for (UserDataCache c : userDataCache.values()) { c.clearCache(); c.dump(); }
		userDataCache.clear();
		Consumer<UUID> listener = sharedCacheRemovalListener;
		if (listener != null) removed.forEach(listener);
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
	public UserDataCache getCache(UUID uuid) { cacheUserIfNeeded(uuid); return userDataCache.get(uuid); }
	public boolean isBoolean(String str) { return booleanColumns.contains(str); }
	public boolean isCached(UUID uuid) { return userDataCache.containsKey(uuid) && userDataCache.get(uuid).hasCache(); }
	public boolean isInt(String str) { return intColumns.contains(str); }

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
		boolean shared = sharedSqlRoute != null;
		withSharedSqlBackendExclusive(uuid, () -> removeCacheExclusively(uuid, shared));
	}

	private void removeCacheExclusively(UUID uuid, boolean shared) {
		UserDataCache cache = getCache(uuid);
		if (cache != null) {
			cache.clearCache();
			if (shared) cache.retireAfterSharedFlush();
		}
		boolean removed = cache == null ? userDataCache.remove(uuid) != null : userDataCache.remove(uuid, cache);
		Consumer<UUID> listener = sharedCacheRemovalListener;
		if (removed && listener != null) listener.accept(uuid);
	}

	public void updateCacheOnline() {
		for (Player p : Bukkit.getOnlinePlayers()) if (isCached(p.getUniqueId())) cacheUser(p.getUniqueId());
	}
}
