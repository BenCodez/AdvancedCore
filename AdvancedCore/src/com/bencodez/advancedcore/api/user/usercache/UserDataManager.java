package com.bencodez.advancedcore.api.user.usercache;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Timer;
import java.util.UUID;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.user.usercache.keys.UserDataKey;
import com.bencodez.advancedcore.api.user.usercache.keys.UserDataKeyString;

import lombok.Getter;

public class UserDataManager {
	@Getter
	private ArrayList<UserDataKey> keys;

	@Getter
	private HashMap<UUID, UserDataCache> userDataCache;

	@Getter
	private AdvancedCorePlugin plugin;

	@Getter
	private Timer timer;

	public UserDataManager(AdvancedCorePlugin plugin) {
		this.plugin = plugin;
		userDataCache = new HashMap<UUID, UserDataCache>();
		keys = new ArrayList<UserDataKey>();
		timer = new Timer();
		loadKeys();
	}

	public void addKey(UserDataKey userDataKey) {
		keys.add(userDataKey);
	}

	public boolean isCached(UUID uuid) {
		if (userDataCache.containsKey(uuid)) {
			return userDataCache.get(uuid).hasCache();
		}
		return false;
	}

	private void loadKeys() {
		addKey(new UserDataKeyString("PlayerName").setColumnType("VARCHAR(30)"));
		addKey(new UserDataKeyString("OfflineRewards").setColumnType("MEDIUMTEXT"));
		addKey(new UserDataKeyString("UnClaimedChoices"));
		addKey(new UserDataKeyString("TimedRewards"));
		addKey(new UserDataKeyString("LastOnline").setColumnType("VARCHAR(20)"));
		addKey(new UserDataKeyString("InputMethod"));
		addKey(new UserDataKeyString("ChoicePreference"));
		addKey(new UserDataKeyString("CheckWorld").setColumnType("VARCHAR(5)"));
	}

	public UserDataCache getCache(UUID uuid) {
		return userDataCache.get(uuid);
	}

	public void cacheUser(UUID uuid) {
		plugin.debug("Caching " + uuid.toString());
		UserDataCache data = new UserDataCache(this, uuid).cache();
		if (data.hasCache()) {
			userDataCache.put(uuid, data);
		}
	}

	public void removeCache(UUID uuid) {
		UserDataCache cache = getCache(uuid);
		if (cache != null) {
			cache.clearCache();
		}
		userDataCache.remove(uuid);
	}

	public void clearCache() {
		plugin.debug("Clearing cache: " + userDataCache.size());
		for (UserDataCache c : userDataCache.values()) {
			c.clearCache();
		}
		userDataCache.clear();

	}

	public void cacheUserIfNeeded(UUID uuid) {
		if (!userDataCache.containsKey(uuid)) {
			cacheUser(uuid);
		}
	}
}
