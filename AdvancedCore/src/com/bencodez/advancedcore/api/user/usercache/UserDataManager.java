package com.bencodez.advancedcore.api.user.usercache;

import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.advancedcore.api.user.usercache.keys.UserDataKey;
import com.bencodez.advancedcore.api.user.usercache.keys.UserDataKeyBoolean;
import com.bencodez.advancedcore.api.user.usercache.keys.UserDataKeyInt;
import com.bencodez.advancedcore.api.user.usercache.keys.UserDataKeyString;

import lombok.Getter;

public class UserDataManager {
	@Getter
	private ArrayList<UserDataKey> keys;

	@Getter
	private ArrayList<String> intColumns;

	@Getter
	private ArrayList<String> booleanColumns;

	@Getter
	private AdvancedCorePlugin plugin;

	@Getter
	private ScheduledExecutorService timer;

	@Getter
	private ConcurrentHashMap<UUID, UserDataCache> userDataCache;

	public UserDataManager(AdvancedCorePlugin plugin) {
		this.plugin = plugin;
		userDataCache = new ConcurrentHashMap<UUID, UserDataCache>();
		keys = new ArrayList<UserDataKey>();
		intColumns = new ArrayList<String>();
		booleanColumns = new ArrayList<String>();
		timer = Executors.newScheduledThreadPool(1);
		loadKeys();

		// run every hour to clear some cache
		timer.scheduleAtFixedRate(new Runnable() {

			@Override
			public void run() {
				clearNonNeededCachedUsers();
			}
		}, 60 * 3, 60 * 60, TimeUnit.SECONDS);

		plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, new Runnable() {

			@Override
			public void run() {
				if (plugin != null && plugin.isEnabled()) {
					clearNonNeededCachedUsers();
				}
			}
		}, 20 * 60 * 3, 20 * 60 * 60);
	}

	public void addKey(UserDataKey userDataKey) {
		keys.add(userDataKey);
		if (userDataKey instanceof UserDataKeyInt) {
			intColumns.add(userDataKey.getKey());
		} else if (userDataKey instanceof UserDataKeyBoolean) {
			booleanColumns.add(userDataKey.getKey());
		}

	}

	public void cacheUser(UUID uuid) {
		plugin.devDebug("Caching " + uuid.toString());
		if (userDataCache.containsKey(uuid)) {
			UserDataCache data = userDataCache.get(uuid);
			data.cache();
		} else {
			UserDataCache data = new UserDataCache(this, uuid).cache();
			if (data.hasCache()) {
				userDataCache.put(uuid, data);
			}
		}
	}

	public void cacheUserIfNeeded(UUID uuid) {
		if (!userDataCache.containsKey(uuid)) {
			cacheUser(uuid);
		}
	}

	public void clearCache() {
		plugin.debug("Clearing cache: " + userDataCache.keySet().size());
		for (UserDataCache c : userDataCache.values()) {
			c.clearCache();
			c.dump();
		}
		userDataCache.clear();

	}

	public void clearCacheBasic() {
		if (plugin.getStorageType().equals(UserStorage.MYSQL)) {
			plugin.getMysql().clearCacheBasic();
		}
	}

	public void clearNonNeededCachedUsers() {
		plugin.devDebug("Clearing cache for non online players (if any)");
		ArrayList<UUID> onlineUUIDS = new ArrayList<UUID>();
		for (Player p : Bukkit.getOnlinePlayers()) {
			onlineUUIDS.add(p.getUniqueId());
		}
		int removed = 0;
		for (UUID uuid : userDataCache.keySet()) {
			if (!onlineUUIDS.contains(uuid)) {
				removeCache(uuid);
				removed++;
			}
		}
		if (removed > 0) {
			plugin.devDebug("Removed " + removed + " cached users who are no longer online");
		}
	}

	public boolean containsKey(UUID fromString) {
		return userDataCache.containsKey(fromString);
	}

	public UserDataCache getCache(UUID uuid) {
		cacheUserIfNeeded(uuid);
		return userDataCache.get(uuid);
	}

	public boolean isCached(UUID uuid) {
		if (userDataCache.containsKey(uuid)) {
			return userDataCache.get(uuid).hasCache();
		}
		return false;
	}

	public boolean isInt(String str) {
		return intColumns.contains(str);
	}

	public boolean isBoolean(String str) {
		return booleanColumns.contains(str);
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
	}

	public void removeCache(UUID uuid) {
		UserDataCache cache = getCache(uuid);
		if (cache != null) {
			cache.clearCache();
		}
		userDataCache.remove(uuid);
	}
}
