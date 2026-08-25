package com.bencodez.advancedcore.api.user.usercache;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.advancedcore.api.user.usercache.change.UserDataChange;
import com.bencodez.advancedcore.api.user.usercache.keys.UserDataKey;
import com.bencodez.simpleapi.array.ArrayUtils;
import com.bencodez.simpleapi.sql.data.DataValue;

import lombok.Getter;

public class UserDataCache {
	@Getter
	private HashMap<String, DataValue> cache;

	private Queue<UserDataChange> cachedChanges;

	private final UserDataManager manager;
	private boolean scheduled = false;
	private int inFlightBatches = 0;
	@Getter
	private UUID uuid;

	public UserDataCache(UserDataManager manager, UUID uuid) {
		this.uuid = uuid;
		this.manager = manager;
		cachedChanges = new ConcurrentLinkedQueue<>();
		cache = new HashMap<>();
	}

	public synchronized void addChange(UserDataChange change, boolean queue) {
		if (change == null || cache == null || cachedChanges == null) {
			return;
		}
		cache.put(change.getKey(), change.toUserDataValue());
		if (queue) {
			cachedChanges.add(change);
			if (!scheduled) {
				scheduleChanges();
			}
		}
	}

	public synchronized UserDataCache cache() {
		if (uuid != null && cache != null) {
			AdvancedCoreUser user = getUser();
			ArrayList<String> keys = user.getUserData().getKeys();
			HashMap<String, DataValue> data = user.getUserData().getValues();
			ArrayList<String> changedKeys = new ArrayList<>();
			for (UserDataKey dataKey : manager.getKeys()) {
				String key = dataKey.getKey();
				keys.remove(key);
				if (data.containsKey(key)) {
					DataValue dataValue = data.get(key);
					manager.getPlugin().devDebug("Caching " + dataValue.getTypeName() + " " + key + " for "
							+ uuid.toString() + ", value: " + dataValue.toString());
					try {
						if (cache.containsKey(key) && !cache.get(key).toString().equals(dataValue.toString())) {
							changedKeys.add(key);
						}
					} catch (Exception e) {
						manager.getPlugin().debug(e);
					}
					cache.put(key, dataValue);
				} else {
					manager.getPlugin().devDebug("Loading default cache value for " + key + " for " + uuid.toString());
					cache.put(key, dataKey.getDefault());
				}
			}
			if (!changedKeys.isEmpty()) {
				manager.getPlugin().getUserManager().onChange(user, ArrayUtils.convert(changedKeys));
			}
			if (keys.size() > 0) {
				manager.getPlugin().devDebug("Keys not cached: " + ArrayUtils.makeStringList(keys));
			}
		}
		return this;
	}

	public synchronized void clearCache() {
		if (hasChangesToProcess()) {
			processChanges();
		}
		if (cache != null) {
			cache.clear();
		}
	}

	public synchronized void clearChanges() {
		if (hasChangesToProcess()) {
			processChanges();
		}
	}

	public void displayCache() {
		manager.getPlugin().devDebug(displayCacheStringList().toString());
	}

	public synchronized ArrayList<String> displayCacheStringList() {
		ArrayList<String> list = new ArrayList<>();
		list.add("Current cache for " + uuid + ": ");
		if (cache == null) {
			return list;
		}
		for (Entry<String, DataValue> entry : getCache().entrySet()) {
			if (entry.getValue().isBoolean()) {
				list.add(entry.getKey() + "=" + entry.getValue().getBoolean());
			} else if (entry.getValue().isString()) {
				list.add(entry.getKey() + "=" + entry.getValue().getString());
			} else if (entry.getValue().isInt()) {
				list.add(entry.getKey() + "=" + entry.getValue().getInt());
			}
		}
		return list;
	}

	public void dump() {
		while (true) {
			processChanges();
			synchronized (this) {
				while (inFlightBatches > 0) {
					try {
						wait();
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						return;
					}
				}
				if (cachedChanges != null && !cachedChanges.isEmpty()) {
					continue;
				}
				cache = null;
				cachedChanges = null;
				uuid = null;
				scheduled = false;
				return;
			}
		}
	}

	public AdvancedCoreUser getUser() {
		return manager.getPlugin().getUserManager().getUser(uuid, false);
	}

	public synchronized boolean hasCache() {
		return cache != null && !cache.isEmpty();
	}

	public synchronized boolean hasChangesToProcess() {
		return cachedChanges != null && !cachedChanges.isEmpty();
	}

	public synchronized boolean isCached(String key) {
		return cache != null && cache.containsKey(key);
	}

	public void processChanges() {
		UUID currentUuid;
		ArrayList<UserDataChange> changes = new ArrayList<>();
		synchronized (this) {
			currentUuid = uuid;
			if (currentUuid == null || cachedChanges == null || cachedChanges.isEmpty()) {
				return;
			}
			UserDataChange change;
			while ((change = cachedChanges.poll()) != null) {
				changes.add(change);
			}
			inFlightBatches++;
		}

		boolean persisted = false;
		try {
			manager.getPlugin().extraDebug("Processing changes for " + currentUuid + ", Changes: " + changes.size());
			AdvancedCoreUser user = manager.getPlugin().getUserManager().getUser(currentUuid, false);
			HashMap<String, DataValue> values = new HashMap<>();
			ArrayList<String> keys = new ArrayList<>();
			for (UserDataChange change : changes) {
				values.put(change.getKey(), change.toUserDataValue());
				keys.add(change.getKey());
			}
			if (!values.isEmpty()) {
				user.getUserData().setValues(values);
			}
			persisted = true;
			manager.getPlugin().getUserManager().onChange(user, ArrayUtils.convert(keys));
			for (UserDataChange change : changes) {
				change.dump();
			}
		} catch (RuntimeException | Error e) {
			if (!persisted) {
				requeueChanges(changes);
			}
			throw e;
		} finally {
			finishInFlightBatch();
		}
	}

	private synchronized void requeueChanges(ArrayList<UserDataChange> changes) {
		if (changes == null || changes.isEmpty() || cachedChanges == null) {
			return;
		}
		Queue<UserDataChange> restored = new ConcurrentLinkedQueue<>();
		restored.addAll(changes);
		restored.addAll(cachedChanges);
		cachedChanges = restored;
	}

	private synchronized void finishInFlightBatch() {
		if (inFlightBatches > 0) {
			inFlightBatches--;
		}
		notifyAll();
	}

	public void processChangesAsync() {
		if (uuid != null && hasChangesToProcess()) {
			manager.getPlugin().getTimer().execute(this::processChanges);
		}
	}

	private synchronized void scheduleChanges() {
		if (scheduled || cachedChanges == null || cachedChanges.isEmpty()) {
			return;
		}
		manager.getPlugin().debug("Schedule changes");
		scheduled = true;
		try {
			manager.getTimer().schedule(() -> {
				try {
					processChanges();
				} catch (Exception e) {
					manager.getPlugin().debug(e);
				} finally {
					onScheduledFlushComplete();
				}
			}, 3, TimeUnit.SECONDS);
		} catch (RejectedExecutionException e) {
			scheduled = false;
			manager.getPlugin().debug(e);
		}
	}

	private synchronized void onScheduledFlushComplete() {
		scheduled = false;
		if (cachedChanges != null && !cachedChanges.isEmpty()) {
			scheduleChanges();
		}
	}

	public synchronized void updateCache(HashMap<String, DataValue> tempCache) {
		cache = tempCache == null ? new HashMap<>() : new HashMap<>(tempCache);
	}
}
