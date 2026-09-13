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
	private long snapshotVersion;
	private long replacementVersion;
	private final HashMap<String, Long> changedAt = new HashMap<>();
	private final HashMap<String, DataValue> inFlightValues = new HashMap<>();
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
		changedAt.put(change.getKey(), ++snapshotVersion);
		if (queue) {
			cachedChanges.add(change);
			if (!scheduled) scheduleChanges();
		}
	}

	public UserDataCache cache() {
		initializeSharedStorage();
		UUID currentUuid;
		long expectedVersion;
		HashMap<String, DataValue> before;
		synchronized (this) {
			if (uuid == null || cache == null) return this;
			currentUuid = uuid;
			expectedVersion = snapshotVersion;
			before = new HashMap<>(cache);
		}
		AdvancedCoreUser user = manager.getPlugin().getUserManager().getUser(currentUuid, false);
		ArrayList<String> keys = user.getUserData().getKeys();
		HashMap<String, DataValue> data = user.getUserData().getValues();
		HashMap<String, DataValue> refreshed = new HashMap<>();
		for (UserDataKey dataKey : manager.getKeys()) {
			String key = dataKey.getKey();
			keys.remove(key);
			DataValue dataValue = data.containsKey(key) ? data.get(key) : dataKey.getDefault();
			if (data.containsKey(key)) manager.getPlugin().devDebug("Caching " + dataValue.getTypeName() + " " + key + " for " + currentUuid + ", value: " + dataValue);
			else manager.getPlugin().devDebug("Loading default cache value for " + key + " for " + currentUuid);
			refreshed.put(key, dataValue);
		}
		HashMap<String, DataValue> published;
		synchronized (this) {
			// A concurrent cache eviction is an expected legacy lifecycle outcome.
			// It must not turn a completed storage read into a failed cache request.
			if (uuid == null || cache == null) return this;
			published = updateSharedSnapshot(refreshed, expectedVersion, currentUuid);
		}
		ArrayList<String> changedKeys = new ArrayList<>();
		for (Entry<String, DataValue> entry : published.entrySet()) {
			DataValue prior = before.get(entry.getKey());
			if (prior != null && entry.getValue() != null && !prior.toString().equals(entry.getValue().toString())) changedKeys.add(entry.getKey());
		}
		if (!changedKeys.isEmpty()) manager.getPlugin().getUserManager().onChange(user, ArrayUtils.convert(changedKeys));
		if (!keys.isEmpty()) manager.getPlugin().devDebug("Keys not cached: " + ArrayUtils.makeStringList(keys));
		return this;
	}

	public void clearCache() {
		if (manager != null && manager.deferSharedStorageWork(this::clearCacheNow)) return;
		clearCacheNow();
	}

	private void clearCacheNow() {
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

	public void clearChanges() {
		if (!hasChangesToProcess()) return;
		if (manager != null && manager.deferSharedStorageWork(this::clearChangesNow)) return;
		clearChangesNow();
	}

	private void clearChangesNow() { if (hasChangesToProcess()) processChanges(); }

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
		if (manager != null && manager.deferSharedStorageWork(this::dumpNow)) return;
		dumpNow();
	}

	private void dumpNow() {
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

	public synchronized void ensureNoLegacyBatchForSharedBinding() {
		if (sharedStorageWriter == null && inFlightBatches != 0) throw new IllegalStateException("Cannot attach shared storage during an active legacy batch");
	}

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

	public void processChanges() {
		initializeSharedStorage();
		Runnable notification = processChangesInternal(false);
		if (notification != null) notification.run();
	}

	private Runnable processChangesInternal(boolean admitted) {
		UUID currentUuid = null;
		Consumer<HashMap<String, DataValue>> writer = null;
		Consumer<Runnable> gate;
		ArrayList<UserDataChange> changes = new ArrayList<>();
		boolean legacyAdmission = false;
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
				if (currentUuid == null || cachedChanges == null || cachedChanges.isEmpty()) return null;
				if (writer == null && manager != null) {
					manager.beginLegacyCacheBatch();
					legacyAdmission = true;
				}
				UserDataChange change;
				while ((change = cachedChanges.poll()) != null) changes.add(change);
				inFlightValues.clear();
				try {
					for (UserDataChange changeEntry : changes) inFlightValues.put(changeEntry.getKey(), changeEntry.toUserDataValue());
				} catch (RuntimeException | Error preparationFailure) {
					inFlightValues.clear();
					requeueChanges(changes);
					if (legacyAdmission) manager.endLegacyCacheBatch();
					throw preparationFailure;
				}
				inFlightBatches++;
				if (writer != null) sharedBatchThread = Thread.currentThread();
			}
		}
		if (gate != null) {
			java.util.concurrent.atomic.AtomicReference<Runnable> notification = new java.util.concurrent.atomic.AtomicReference<>();
			gate.accept(() -> notification.set(processChangesInternal(true)));
			return notification.get();
		}
		boolean persisted = false;
		AdvancedCoreUser changedUser = null;
		String[] changedKeys = null;
		try {
			manager.getPlugin().extraDebug("Processing changes for " + currentUuid + ", Changes: " + changes.size());
			AdvancedCoreUser user = manager.getPlugin().getUserManager().getUser(currentUuid, false);
			HashMap<String, DataValue> values = new HashMap<>();
			ArrayList<String> keys = new ArrayList<>();
			for (UserDataChange change : changes) { values.put(change.getKey(), change.toUserDataValue()); keys.add(change.getKey()); }
			if (!values.isEmpty()) { if (writer == null) user.getUserData().setValues(values); else writer.accept(values); }
			persisted = true;
			changedUser = user;
			changedKeys = ArrayUtils.convert(keys);
		} catch (RuntimeException | Error e) {
			if (!persisted) requeueChanges(changes);
			throw e;
		} finally {
			finishInFlightBatch();
			if (legacyAdmission) manager.endLegacyCacheBatch();
		}
		// UserDataChanged callbacks may clear this cache. Do not expose the cache
		// while its active shared batch marker is still set.
		if (!persisted) return null;
		AdvancedCoreUser notifyUser = changedUser;
		String[] notifyKeys = changedKeys;
		return () -> {
			manager.getPlugin().getUserManager().onChange(notifyUser, notifyKeys);
			for (UserDataChange change : changes) change.dump();
		};
	}

	private synchronized void requeueChanges(ArrayList<UserDataChange> changes) {
		if (changes == null || changes.isEmpty() || cachedChanges == null) return;
		Queue<UserDataChange> restored = new ConcurrentLinkedQueue<>();
		restored.addAll(changes); restored.addAll(cachedChanges); cachedChanges = restored;
	}

	private synchronized void finishInFlightBatch() {
		if (inFlightBatches > 0) {
			inFlightBatches--;
			if (inFlightBatches == 0) inFlightValues.clear();
			if (sharedBatchThread == Thread.currentThread()) sharedBatchThread = null;
		}
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

	public synchronized void updateCachePreservingPending(HashMap<String, DataValue> storageValues) {
		HashMap<String, DataValue> refreshed = storageValues == null ? new HashMap<>() : new HashMap<>(storageValues);
		refreshed.putAll(inFlightValues);
		if (cachedChanges != null) for (UserDataChange change : cachedChanges) refreshed.put(change.getKey(), change.toUserDataValue());
		cache = refreshed;
		recordSnapshotReplacement();
	}

	public synchronized long getSharedSnapshotVersion() { return snapshotVersion; }

	public synchronized HashMap<String, DataValue> updateSharedSnapshot(HashMap<String, DataValue> values,
			long expectedVersion) { return updateSharedSnapshot(values, expectedVersion, uuid); }

	private synchronized HashMap<String, DataValue> updateSharedSnapshot(HashMap<String, DataValue> values,
			long expectedVersion, UUID expectedUuid) {
		if (cache == null || uuid == null || !uuid.equals(expectedUuid)) throw new IllegalStateException("Shared user cache changed while loading");
		if (expectedVersion < 0 || expectedVersion > snapshotVersion) throw new IllegalArgumentException("Invalid cache snapshot version");
		if (replacementVersion > expectedVersion) return new HashMap<>(cache);
		HashMap<String, DataValue> merged = values == null ? new HashMap<>() : new HashMap<>(values);
		changedAt.forEach((key, version) -> { if (version >= expectedVersion && cache.containsKey(key)) merged.put(key, cache.get(key)); });
		cache = merged;
		recordSnapshotReplacement();
		return new HashMap<>(cache);
	}

	private void recordSnapshotReplacement() {
		replacementVersion = ++snapshotVersion;
		changedAt.clear();
	}
}
