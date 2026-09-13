package com.bencodez.advancedcore.api.user.usercache;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.advancedcore.api.user.usercache.change.UserDataChange;
import com.bencodez.advancedcore.api.user.usercache.keys.UserDataKey;
import com.bencodez.simpleapi.array.ArrayUtils;
import com.bencodez.simpleapi.sql.data.DataValue;

import lombok.Getter;

public class UserDataCache {
	@Getter private HashMap<String, DataValue> cache;
	private Queue<UserDataChange> cachedChanges;
	private final UserDataManager manager;
	// Version metadata only; all user values stay in the existing cache.
	private long snapshotVersion;
	private long replacementVersion;
	private final HashMap<String, Long> changedAt = new HashMap<>();
	private boolean scheduled = false;
	private int inFlightBatches = 0;
	private volatile Consumer<HashMap<String, DataValue>> sharedStorageWriter;
	private Thread sharedBatchThread;
	private volatile Consumer<Runnable> sharedFlushGate;
	@Getter private UUID uuid;

	public UserDataCache(UserDataManager manager, UUID uuid) {
		this.uuid = uuid;
		this.manager = manager;
		cachedChanges = new ConcurrentLinkedQueue<>();
		cache = new HashMap<>();
	}

	private void initializeSharedStorage() {
		synchronized (this) { if (sharedFlushGate != null || uuid == null || cachedChanges == null) return; }
		if (manager != null) manager.initializeSharedCache(this);
	}

	public void addChange(UserDataChange change, boolean queue) {
		initializeSharedStorage();
		Consumer<Runnable> gate;
		synchronized (this) {
			gate = sharedFlushGate;
			if (gate == null) { addChangeInternal(change, queue); return; }
		}
		gate.accept(() -> addChangeInternal(change, queue));
	}

	private synchronized void addChangeInternal(UserDataChange change, boolean queue) {
		if (change != null && sharedStorageWriter != null && (cache == null || cachedChanges == null)) {
			throw new IllegalStateException("Shared user cache is retired");
		}
		if (change == null || cache == null || cachedChanges == null) return;
		cache.put(change.getKey(), change.toUserDataValue());
		if (sharedStorageWriter != null) changedAt.put(change.getKey(), ++snapshotVersion);
		if (queue) {
			cachedChanges.add(change);
			if (!scheduled) scheduleChanges();
		}
	}

	public synchronized UserDataCache cache() {
		if (uuid != null && cache != null) {
			recordSnapshotReplacement();
			AdvancedCoreUser user = getUser();
			ArrayList<String> keys = user.getUserData().getKeys();
			HashMap<String, DataValue> data = user.getUserData().getValues();
			ArrayList<String> changedKeys = new ArrayList<>();
			for (UserDataKey dataKey : manager.getKeys()) {
				String key = dataKey.getKey();
				keys.remove(key);
				if (data.containsKey(key)) {
					DataValue dataValue = data.get(key);
					manager.getPlugin().devDebug("Caching " + dataValue.getTypeName() + " " + key + " for " + uuid + ", value: " + dataValue);
					try { if (cache.containsKey(key) && !cache.get(key).toString().equals(dataValue.toString())) changedKeys.add(key); }
					catch (Exception e) { manager.getPlugin().debug(e); }
					cache.put(key, dataValue);
				} else {
					manager.getPlugin().devDebug("Loading default cache value for " + key + " for " + uuid);
					cache.put(key, dataKey.getDefault());
				}
			}
			if (!changedKeys.isEmpty()) manager.getPlugin().getUserManager().onChange(user, ArrayUtils.convert(changedKeys));
			if (!keys.isEmpty()) manager.getPlugin().devDebug("Keys not cached: " + ArrayUtils.makeStringList(keys));
		}
		return this;
	}

	public void clearCache() {
		initializeSharedStorage();
		Consumer<Runnable> gate;
		synchronized (this) {
			gate = sharedFlushGate;
			if (gate == null) {
				if (hasChangesToProcess()) processChanges();
				if (cache != null) { cache.clear(); recordSnapshotReplacement(); }
				return;
			}
		}
		gate.accept(() -> { processChanges(); synchronized (this) { if (cache != null) { cache.clear(); recordSnapshotReplacement(); } } });
	}

	public void clearChanges() { if (hasChangesToProcess()) processChanges(); }
	public void displayCache() { manager.getPlugin().devDebug(displayCacheStringList().toString()); }
	public synchronized ArrayList<String> displayCacheStringList() {
		ArrayList<String> list = new ArrayList<>();
		list.add("Current cache for " + uuid + ": ");
		if (cache == null) return list;
		for (Entry<String, DataValue> entry : cache.entrySet()) {
			if (entry.getValue().isBoolean()) list.add(entry.getKey() + "=" + entry.getValue().getBoolean());
			else if (entry.getValue().isString()) list.add(entry.getKey() + "=" + entry.getValue().getString());
			else if (entry.getValue().isInt()) list.add(entry.getKey() + "=" + entry.getValue().getInt());
		}
		return list;
	}

	public void dump() {
		while (true) {
			processChanges();
			synchronized (this) {
				while (inFlightBatches > 0) {
					try { wait(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
				}
				if (cachedChanges != null && !cachedChanges.isEmpty()) continue;
				recordSnapshotReplacement(); cache = null; cachedChanges = null; uuid = null; scheduled = false; return;
			}
		}
	}

	public AdvancedCoreUser getUser() { return manager.getPlugin().getUserManager().getUser(uuid, false); }
	public synchronized boolean hasCache() { return cache != null && !cache.isEmpty(); }
	public synchronized boolean hasChangesToProcess() { return cachedChanges != null && !cachedChanges.isEmpty(); }
	public synchronized boolean isCached(String key) { return cache != null && cache.containsKey(key); }

	public synchronized void configureSharedStorage(Consumer<HashMap<String, DataValue>> writer, Consumer<Runnable> gate) {
		if (sharedFlushGate != null && sharedFlushGate != gate) throw new IllegalStateException("Shared user cache already belongs to another runtime");
		if (sharedFlushGate == null && gate != null && inFlightBatches != 0) throw new IllegalStateException("Cannot attach shared storage during an active legacy batch");
		setSharedStorageWriter(writer);
		sharedFlushGate = gate;
	}

	public synchronized void setSharedStorageWriter(Consumer<HashMap<String, DataValue>> writer) {
		if (uuid == null || cachedChanges == null) throw new IllegalStateException("Shared user cache is retired");
		sharedStorageWriter = java.util.Objects.requireNonNull(writer, "writer");
	}

	public synchronized void retireAfterSharedFlush() {
		if (inFlightBatches != 0 || (cachedChanges != null && !cachedChanges.isEmpty())) throw new IllegalStateException("Shared user cache has unflushed work");
		recordSnapshotReplacement(); cache = null; cachedChanges = null; uuid = null; scheduled = false;
	}

	public void processChanges() { initializeSharedStorage(); processChangesInternal(false); }

	private void processChangesInternal(boolean admitted) {
		UUID currentUuid = null;
		Consumer<HashMap<String, DataValue>> writer = null;
		Consumer<Runnable> gate;
		ArrayList<UserDataChange> changes = new ArrayList<>();
		synchronized (this) {
			gate = admitted ? null : sharedFlushGate;
			if (gate == null) {
				writer = sharedStorageWriter;
				if (writer != null) {
					if (sharedBatchThread == Thread.currentThread()) throw new IllegalStateException("Cannot flush a shared cache from its own change notification");
					while (inFlightBatches > 0) {
						try { wait(); }
						catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException("Interrupted while draining user changes", interrupted); }
					}
				}
				currentUuid = uuid;
				if (currentUuid == null || cachedChanges == null || cachedChanges.isEmpty()) return;
				UserDataChange change;
				while ((change = cachedChanges.poll()) != null) changes.add(change);
				inFlightBatches++;
				if (writer != null) sharedBatchThread = Thread.currentThread();
			}
		}
		if (gate != null) { gate.accept(() -> processChangesInternal(true)); return; }
		boolean persisted = false;
		try {
			manager.getPlugin().extraDebug("Processing changes for " + currentUuid + ", Changes: " + changes.size());
			AdvancedCoreUser user = manager.getPlugin().getUserManager().getUser(currentUuid, false);
			HashMap<String, DataValue> values = new HashMap<>();
			ArrayList<String> keys = new ArrayList<>();
			for (UserDataChange change : changes) { values.put(change.getKey(), change.toUserDataValue()); keys.add(change.getKey()); }
			if (!values.isEmpty()) { if (writer == null) user.getUserData().setValues(values); else writer.accept(values); }
			persisted = true;
			manager.getPlugin().getUserManager().onChange(user, ArrayUtils.convert(keys));
			for (UserDataChange change : changes) change.dump();
		} catch (RuntimeException | Error e) {
			if (!persisted) requeueChanges(changes);
			throw e;
		} finally { finishInFlightBatch(); }
	}

	private synchronized void requeueChanges(ArrayList<UserDataChange> changes) {
		if (changes == null || changes.isEmpty() || cachedChanges == null) return;
		Queue<UserDataChange> restored = new ConcurrentLinkedQueue<>();
		restored.addAll(changes); restored.addAll(cachedChanges); cachedChanges = restored;
	}

	private synchronized void finishInFlightBatch() {
		if (inFlightBatches > 0) { inFlightBatches--; if (sharedBatchThread == Thread.currentThread()) sharedBatchThread = null; }
		notifyAll();
	}

	public void processChangesAsync() { if (uuid != null && hasChangesToProcess()) manager.getPlugin().getTimer().execute(this::processChanges); }

	private synchronized void scheduleChanges() {
		if (scheduled || cachedChanges == null || cachedChanges.isEmpty()) return;
		manager.getPlugin().debug("Schedule changes");
		scheduled = true;
		try {
			manager.getTimer().schedule(() -> {
				try { processChanges(); } catch (Exception e) { manager.getPlugin().debug(e); }
				finally { onScheduledFlushComplete(); }
			}, 3, TimeUnit.SECONDS);
		} catch (RejectedExecutionException e) { scheduled = false; manager.getPlugin().debug(e); }
	}

	private synchronized void onScheduledFlushComplete() {
		scheduled = false;
		if (cachedChanges != null && !cachedChanges.isEmpty()) scheduleChanges();
	}

	public synchronized void updateCache(HashMap<String, DataValue> tempCache) {
		cache = tempCache == null ? new HashMap<>() : new HashMap<>(tempCache);
		recordSnapshotReplacement();
	}

	/** Merge a storage refresh without overwriting values that are queued or currently being persisted. */
	public synchronized void updateCachePreservingPending(HashMap<String, DataValue> storageValues) {
		HashMap<String, DataValue> refreshed = storageValues == null ? new HashMap<>() : new HashMap<>(storageValues);
		if (inFlightBatches > 0 && cache != null) refreshed.putAll(cache);
		if (cachedChanges != null) {
			for (UserDataChange change : cachedChanges) refreshed.put(change.getKey(), change.toUserDataValue());
		}
		cache = refreshed;
		recordSnapshotReplacement();
	}

	public synchronized long getSharedSnapshotVersion() { return snapshotVersion; }

	/** Merge only mutations newer than the load token, including already-flushed ones. */
	public synchronized HashMap<String, DataValue> updateSharedSnapshot(HashMap<String, DataValue> values,
			long expectedVersion) {
		if (cache == null || uuid == null) throw new IllegalStateException("Shared user cache is retired");
		if (expectedVersion < 0 || expectedVersion > snapshotVersion) {
			throw new IllegalArgumentException("Invalid cache snapshot version");
		}
		// A later completed population or explicit full-cache refresh already won.
		if (replacementVersion > expectedVersion) return new HashMap<>(cache);
		HashMap<String, DataValue> merged = values == null ? new HashMap<>() : new HashMap<>(values);
		changedAt.forEach((key, version) -> {
			if (version > expectedVersion) merged.put(key, cache.get(key));
		});
		cache = merged;
		recordSnapshotReplacement();
		return new HashMap<>(cache);
	}

	private void recordSnapshotReplacement() {
		replacementVersion = ++snapshotVersion;
		changedAt.clear();
	}
}
