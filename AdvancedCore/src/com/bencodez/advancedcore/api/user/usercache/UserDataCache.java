package com.bencodez.advancedcore.api.user.usercache;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Queue;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.bencodez.advancedcore.api.misc.ArrayUtils;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.advancedcore.api.user.UserManager;
import com.bencodez.advancedcore.api.user.usercache.change.UserDataChange;
import com.bencodez.advancedcore.api.user.usercache.keys.UserDataKey;
import com.bencodez.advancedcore.api.user.usercache.value.UserDataValue;

import lombok.Getter;

public class UserDataCache {
	@Getter
	private UUID uuid;

	@Getter
	private HashMap<String, UserDataValue> cache;

	private Queue<UserDataChange> cachedChanges;
	private UserDataManager manager;
	private boolean scheduled = false;

	public UserDataCache(UserDataManager manager, UUID uuid) {
		this.uuid = uuid;
		this.manager = manager;
		cachedChanges = new ConcurrentLinkedQueue<UserDataChange>();
		cache = new HashMap<String, UserDataValue>();
	}

	private void scheduleChanges() {
		manager.getPlugin().debug("Schedule changes");
		scheduled = true;
		manager.getTimer().schedule(new TimerTask() {

			@Override
			public void run() {
				processChanges();
				scheduled = false;
			}
		}, 1000 * 3);
	}

	public synchronized void addChange(UserDataChange change) {
		cache.put(change.getKey(), change.toUserDataValue());
		cachedChanges.add(change);
		if (!scheduled) {
			scheduleChanges();
		}
	}

	public boolean hasChangesToProcess() {
		return !cachedChanges.isEmpty();
	}

	public AdvancedCoreUser getUser() {
		return UserManager.getInstance().getUser(uuid, false);
	}

	public void processChanges() {
		manager.getPlugin()
				.extraDebug("Processing changes for " + uuid.toString() + ", Changes: " + cachedChanges.size());
		AdvancedCoreUser user = getUser();
		HashMap<String, UserDataValue> values = new HashMap<String, UserDataValue>();
		while (!cachedChanges.isEmpty()) {
			UserDataChange change = cachedChanges.poll();
			values.put(change.getKey(), change.toUserDataValue());
			manager.getPlugin().extraDebug("Processing change for " + change.getKey());
		}
		user.getUserData().setValues(values);
	}

	public boolean isCached(String key) {
		return cache.containsKey(key);
	}

	public UserDataCache cache() {
		AdvancedCoreUser user = getUser();
		ArrayList<String> keys = user.getUserData().getKeys();
		HashMap<String, UserDataValue> data = user.getUserData().getValues();
		for (UserDataKey dataKey : manager.getKeys()) {
			String key = dataKey.getKey();
			keys.remove(key);
			if (data.containsKey(key)) {
				String type = data.get(key).getTypeName();
				String value = "";
				if (data.get(key).isInt()) {
					value = "" + data.get(key).getInt();
				} else {
					value = data.get(key).getString();
				}
				manager.getPlugin()
						.extraDebug("Caching " + type + " " + key + " for " + uuid.toString() + ", value: " + value);
				cache.put(key, data.get(key));
			}

		}
		if (keys.size() > 0) {
			manager.getPlugin().debug("Keys not cached: " + ArrayUtils.getInstance().makeStringList(keys));
		}

		return this;
	}

	public void clearCache() {
		if (hasChangesToProcess()) {
			processChanges();
		}
		cache.clear();
	}

	public boolean hasCache() {
		return !cache.isEmpty();
	}

	public void updateCache(HashMap<String, UserDataValue> tempCache) {
		cache = tempCache;
	}
}
