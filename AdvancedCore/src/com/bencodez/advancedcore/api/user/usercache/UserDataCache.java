package com.bencodez.advancedcore.api.user.usercache;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import com.bencodez.advancedcore.api.misc.ArrayUtils;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.advancedcore.api.user.usercache.change.UserDataChange;
import com.bencodez.advancedcore.api.user.usercache.keys.UserDataKey;
import com.bencodez.advancedcore.api.user.usercache.value.DataValue;

import lombok.Getter;

public class UserDataCache {
	@Getter
	private HashMap<String, DataValue> cache;

	private Queue<UserDataChange> cachedChanges;

	private UserDataManager manager;
	private boolean scheduled = false;
	@Getter
	private UUID uuid;

	public UserDataCache(UserDataManager manager, UUID uuid) {
		this.uuid = uuid;
		this.manager = manager;
		cachedChanges = new ConcurrentLinkedQueue<UserDataChange>();
		cache = new HashMap<String, DataValue>();
	}

	public synchronized void addChange(UserDataChange change, boolean queue) {
		cache.put(change.getKey(), change.toUserDataValue());
		if (queue) {
			cachedChanges.add(change);
			if (!scheduled) {
				scheduleChanges();
			}
		}

	}

	public UserDataCache cache() {
		if (uuid != null) {
			AdvancedCoreUser user = getUser();
			ArrayList<String> keys = user.getUserData().getKeys();
			HashMap<String, DataValue> data = user.getUserData().getValues();
			ArrayList<String> changedKeys = new ArrayList<String>();
			for (UserDataKey dataKey : manager.getKeys()) {
				String key = dataKey.getKey();
				keys.remove(key);
				if (data.containsKey(key)) {
					DataValue dataValue = data.get(key);
					manager.getPlugin().devDebug("Caching " + dataValue.getTypeName() + " " + key + " for "
							+ uuid.toString() + ", value: " + dataValue.toString());
					// temp try/catch to prevent plugin failures
					try {
						if (cache.containsKey(key)) {
							if (!cache.get(key).toString().equals(dataValue.toString())) {
								changedKeys.add(key);
							}
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
					cache.put(key, dataValue);
				} else {
					manager.getPlugin().devDebug("Loading default cache value for " + key + " for " + uuid.toString());
					cache.put(key, dataKey.getDefault());
				}

			}
			if (!changedKeys.isEmpty()) {
				manager.getPlugin().getUserManager().onChange(user, ArrayUtils.getInstance().convert(changedKeys));
			}
			if (keys.size() > 0) {
				manager.getPlugin().devDebug("Keys not cached: " + ArrayUtils.getInstance().makeStringList(keys));
			}
		}
		return this;
	}

	public void clearCache() {
		if (hasChangesToProcess()) {
			processChanges();
		}
		cache.clear();
	}
	
	public void clearChanges() {
		if (hasChangesToProcess()) {
			processChanges();
		}
	}

	public void dump() {
		if (hasChangesToProcess()) {
			processChanges();
		}
		cache = null;
		cachedChanges = null;
		uuid = null;
	}

	public AdvancedCoreUser getUser() {
		return manager.getPlugin().getUserManager().getUser(uuid, false);
	}

	public boolean hasCache() {
		return !cache.isEmpty();
	}

	public boolean hasChangesToProcess() {
		return !cachedChanges.isEmpty();
	}

	public boolean isCached(String key) {
		if (cache != null) {
			return cache.containsKey(key);
		}
		return false;
	}

	public void processChanges() {
		if (uuid != null) {
			if (cachedChanges.size() > 0) {
				manager.getPlugin()
						.extraDebug("Processing changes for " + uuid.toString() + ", Changes: " + cachedChanges.size());
				AdvancedCoreUser user = getUser();
				HashMap<String, DataValue> values = new HashMap<String, DataValue>();
				ArrayList<String> keys = new ArrayList<String>();
				while (!cachedChanges.isEmpty()) {
					UserDataChange change = cachedChanges.poll();
					values.put(change.getKey(), change.toUserDataValue());
					keys.add(change.getKey());
					change.dump();
					// manager.getPlugin().extraDebug("Processing change for " + change.getKey());
				}
				if (!values.isEmpty()) {
					user.getUserData().setValues(values);
				}
				manager.getPlugin().getUserManager().onChange(user, ArrayUtils.getInstance().convert(keys));
			}
		}
	}

	private void scheduleChanges() {
		manager.getPlugin().debug("Schedule changes");
		scheduled = true;

		manager.getTimer().schedule(new Runnable() {

			@Override
			public void run() {
				try {
					processChanges();
					scheduled = false;
				} catch (Exception e) {
					manager.getPlugin().debug(e);
				}
			}
		}, 3, TimeUnit.SECONDS);
	}

	public void updateCache(HashMap<String, DataValue> tempCache) {
		cache = tempCache;
	}

	public void displayCache() {
		manager.getPlugin().devDebug(displayCacheString());
	}

	public String displayCacheString() {
		String str = "Current cache for " + uuid + ": ";
		for (Entry<String, DataValue> entry : getCache().entrySet()) {
			if (entry.getValue().isBoolean()) {
				str += entry.getKey() + "=" + entry.getValue().getBoolean() + ", ";
			} else if (entry.getValue().isString()) {
				str += entry.getKey() + "=" + entry.getValue().getString() + ", ";
			} else if (entry.getValue().isInt()) {
				str += entry.getKey() + "=" + entry.getValue().getInt() + ", ";
			}
		}
		return str;
	}
}
