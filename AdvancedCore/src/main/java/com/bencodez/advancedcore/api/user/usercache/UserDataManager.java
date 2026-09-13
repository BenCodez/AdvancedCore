package com.bencodez.advancedcore.api.user.usercache;

import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Consumer;

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
	private volatile SqlUserBackend sharedSqlBackend;
	private volatile Consumer<Runnable> sharedSqlGate;

	/** One shared owner for the existing map, including caches created by legacy callers. */
	public final synchronized void bindSharedCacheInitializer(Consumer<UserDataCache> initializer) {
		Objects.requireNonNull(initializer, "initializer");
		if (sharedCacheInitializer != null && sharedCacheInitializer != initializer) {
			throw new IllegalStateException("User data manager already belongs to another shared runtime");
		}
		sharedCacheInitializer = initializer;
	}

	void initializeSharedCache(UserDataCache cache) {
		Consumer<UserDataCache> initializer = sharedCacheInitializer;
		if (initializer != null) {
			if (Thread.holdsLock(cache)) {
				throw new IllegalStateException("Cannot attach shared storage while holding the cache monitor");
			}
			initializer.accept(cache);
		}
	}

	/** Publish the backend and the runtime admission gate as one legacy-facade route. */
	public final synchronized void bindSharedSqlBackend(SqlUserBackend backend, Consumer<Runnable> gate) {
		sharedSqlBackend = Objects.requireNonNull(backend, "backend");
		sharedSqlGate = Objects.requireNonNull(gate, "gate");
	}

	public final synchronized void unbindSharedSqlBackend(SqlUserBackend expected) {
		if (sharedSqlBackend == expected) {
			sharedSqlBackend = null;
			sharedSqlGate = null;
		}
	}

	public final boolean hasSharedSqlBackend() {
		return sharedSqlBackend != null && sharedSqlGate != null;
	}

	/**
	 * Route legacy UserData/BukkitSqlUserStorage access through the currently selected
	 * shared backend while holding the same lifecycle admission used by runtime calls.
	 */
	public final <T> T withSharedSqlBackend(UUID uuid,
			BiFunction<UserStorage, SqlUserStorage, T> operation) {
		Objects.requireNonNull(uuid, "uuid");
		Objects.requireNonNull(operation, "operation");
		Consumer<Runnable> gate = sharedSqlGate;
		if (gate == null) throw new IllegalStateException("Shared SQL backend is not bound");
		AtomicReference<T> result = new AtomicReference<>();
		gate.accept(() -> {
			SqlUserBackend selected = sharedSqlBackend;
			if (selected == null || !selected.isOpen()) {
				throw new IllegalStateException("Shared SQL backend is unavailable");
			}
			result.set(operation.apply(selected.storageType(), selected.user(uuid)));
		});
		return result.get();
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
		plugin.devDebug("Caching " + uuid.toString());
		if (plugin.getOptions().getDebug().isDebug(DebugLevel.DEV)) {
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
		plugin.devDebug("Caching " + uuid.toString());
		if (userDataCache.containsKey(uuid)) {
			UserDataCache data = userDataCache.get(uuid);
			data.clearChanges();
			data.cache();
		} else {
			UserDataCache data = new UserDataCache(this, uuid).cache();
			if (data.hasCache()) userDataCache.put(uuid, data);
		}
	}

	public void cacheUserIfNeeded(UUID uuid) { if (!userDataCache.containsKey(uuid)) cacheUser(uuid); }

	public void clearCache() {
		plugin.debug("Clearing cache: " + userDataCache.keySet().size());
		for (UserDataCache c : userDataCache.values()) { c.clearCache(); c.dump(); }
		userDataCache.clear();
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
		if (playerName != null && !playerName.isEmpty() && !plugin.getOptions().isOnlineMode()) {
			uuid = UUID.fromString(UuidLookup.getInstance().getUUID(playerName));
		}
		UserDataCache cache = getCache(uuid);
		if (cache != null) cache.clearCache();
		userDataCache.remove(uuid);
	}

	public void updateCacheOnline() {
		for (Player p : Bukkit.getOnlinePlayers()) if (isCached(p.getUniqueId())) cacheUser(p.getUniqueId());
	}
}
