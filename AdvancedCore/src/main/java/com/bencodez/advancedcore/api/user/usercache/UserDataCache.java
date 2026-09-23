package com.bencodez.advancedcore.api.user.usercache;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
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
	private boolean storedDataPresent;
	private boolean storageSnapshotPublished;
	private final HashMap<String, Long> changedAt = new HashMap<>();
	private final HashMap<String, Long> persistedAt = new HashMap<>();
	private final HashMap<String, DataValue> inFlightValues = new HashMap<>();
	private boolean scheduled = false;
	private boolean removing;
	private int inFlightBatches = 0;
	private volatile Consumer<HashMap<String, DataValue>> sharedStorageWriter;
	private Thread sharedBatchThread;
	private volatile Consumer<Runnable> sharedFlushGate;
	private volatile Consumer<Runnable> sharedExclusiveFlushGate;
	private int exclusiveFlushesPending;
	private final Queue<UserDataChange> changesAfterExclusiveFlush = new ConcurrentLinkedQueue<>();
	private final Queue<Runnable> notificationsAfterExclusiveFlush = new ConcurrentLinkedQueue<>();
	private boolean flushChangesAfterExclusive;
	private final ReentrantReadWriteLock legacyMutationOrder = new ReentrantReadWriteLock(true);
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
		}
		if (gate != null) {
			gate.accept(() -> addChangeInternal(change, queue));
			return;
		}
		legacyMutationOrder.readLock().lock();
		try { addChangeInternal(change, queue); }
		finally { legacyMutationOrder.readLock().unlock(); }
	}

	/**
	 * Publish into an already bound cache without waiting for its user gate. The
	 * cache monitor serializes this with retirement markers: a change accepted
	 * first is included in the following flush, while a transition that marks the
	 * cache first makes the caller defer the complete mutation instead.
	 */
	public boolean tryAddChangeBeforeDeferredSharedFlush(UserDataChange change) {
		return tryAddChangeBeforeDeferredSharedFlush(change, null);
	}

	/**
	 * Publish read-after-write state without crossing a pending durable checkpoint.
	 * Notifications accepted during the checkpoint are released with the mutation,
	 * after every already-admitted exclusive operation has completed.
	 */
	public boolean tryAddChangeBeforeDeferredSharedFlush(UserDataChange change, Runnable notification) {
		return tryAddChangeBeforeDeferredSharedFlush(change, notification, false);
	}

	public boolean tryAddChangeBeforeDeferredSharedFlush(UserDataChange change, Runnable notification,
			boolean flushImmediately) {
		return tryAddChangesBeforeDeferredSharedFlush(java.util.List.of(change), notification, flushImmediately);
	}

	/** Atomically publish one complete caller batch against cache retirement. */
	public boolean tryAddChangesBeforeDeferredSharedFlush(Iterable<UserDataChange> changes) {
		return tryAddChangesBeforeDeferredSharedFlush(changes, null, false);
	}

	public boolean tryAddChangesBeforeDeferredSharedFlush(Iterable<UserDataChange> changes,
			boolean flushImmediately) {
		return tryAddChangesBeforeDeferredSharedFlush(changes, null, flushImmediately);
	}

	private boolean tryAddChangesBeforeDeferredSharedFlush(Iterable<UserDataChange> changes,
			Runnable notification, boolean flushImmediately) {
		Runnable notificationToDispatch = null;
		boolean flushNow = false;
		synchronized (this) {
			if (sharedFlushGate == null || removing || uuid == null || cache == null || cachedChanges == null) {
				return false;
			}
			if (exclusiveFlushesPending != 0) {
				for (UserDataChange change : changes) {
					publishChangeInternal(change);
					if (change != null) changesAfterExclusiveFlush.add(change);
				}
				if (notification != null) {
					Runnable captured = manager.captureSharedUserDataNotification(notification);
					notificationsAfterExclusiveFlush.add(captured == null ? notification : captured);
				}
				flushChangesAfterExclusive |= flushImmediately;
				return true;
			}
			for (UserDataChange change : changes) addChangeInternal(change, true);
			if (notification != null) {
				Runnable captured = manager.captureSharedUserDataNotification(notification);
				notificationToDispatch = captured == null ? notification : captured;
			}
			flushNow = flushImmediately;
		}
		if (notificationToDispatch != null) manager.dispatchSharedUserDataNotification(notificationToDispatch);
		if (flushNow) scheduleImmediateSharedFlush();
		return true;
	}

	private synchronized void addChangeInternal(UserDataChange change, boolean queue) {
		if (change != null && sharedStorageWriter != null && (cache == null || cachedChanges == null)) {
			throw new IllegalStateException("Shared user cache is retired");
		}
		// A manager-wide retirement must never make a caller believe that its
		// queued mutation was accepted when it was not.  The manager normally
		// admits writers before this point; this guard also covers a re-entrant
		// listener running as the cache is being retired.
		if (removing) throw new IllegalStateException("Shared user cache is retiring");
		if (change == null || cache == null || cachedChanges == null) return;
		publishChangeInternal(change);
		if (queue) {
			cachedChanges.add(change);
			if (!scheduled) scheduleChanges();
		}
	}

	private void publishChangeInternal(UserDataChange change) {
		if (change == null || cache == null) return;
		cache.put(change.getKey(), change.toUserDataValue());
		changedAt.put(change.getKey(), ++snapshotVersion);
	}

	public UserDataCache cache() {
		cacheInternal(true);
		return this;
	}

	UserDataCache cacheInternal(boolean notify) {
		refreshInternal(notify);
		return this;
	}

	ArrayList<String> refreshInternal(boolean notify) {
		initializeSharedStorage();
		UUID currentUuid;
		long expectedVersion;
		HashMap<String, DataValue> before;
		synchronized (this) {
			if (uuid == null || cache == null) return new ArrayList<>();
			currentUuid = uuid;
			expectedVersion = snapshotVersion;
			before = new HashMap<>(cache);
		}
		AdvancedCoreUser user = manager.getPlugin().getUserManager().getUser(currentUuid, false);
		ArrayList<String> changedKeys = new ArrayList<>();
		Runnable refreshNotification = null;
		if (notify) {
			Runnable rawNotification = () -> manager.getPlugin().getUserManager()
					.onChange(user, ArrayUtils.convert(changedKeys));
			Runnable captured = manager.captureSharedUserDataNotification(rawNotification);
			refreshNotification = captured == null ? rawNotification : captured;
		}
		ArrayList<String> keys = user.getUserData().getKeys();
		HashMap<String, DataValue> data = user.getUserData().getValues();
		boolean refreshedStoredDataPresent = !keys.isEmpty() || !data.isEmpty();
		// Primary-thread public reads cannot fall back to SQL once the shared
		// runtime is bound. Retain arbitrary persisted columns (for example
		// VotingPlugin's dynamic VoteShopLimit keys) and layer registered defaults
		// only where storage omitted a known key.
		HashMap<String, DataValue> refreshed = new HashMap<>(data);
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
			if (uuid == null || cache == null) return new ArrayList<>();
			published = updateSharedSnapshot(refreshed, expectedVersion, currentUuid,
					refreshedStoredDataPresent);
		}
		for (Entry<String, DataValue> entry : published.entrySet()) {
			DataValue prior = before.get(entry.getKey());
			if (prior != null && entry.getValue() != null && !prior.toString().equals(entry.getValue().toString())) changedKeys.add(entry.getKey());
		}
		if (refreshNotification != null && !changedKeys.isEmpty()) deliverNotification(refreshNotification);
		if (!keys.isEmpty()) manager.getPlugin().devDebug("Caching additional keys: " + ArrayUtils.makeStringList(keys));
		return changedKeys;
	}

	public void clearCache() {
		if (manager != null && manager.deferSharedStorageWork(this::clearCacheNow)) return;
		clearCacheNow();
	}

	/**
	 * Drains every queued or in-flight cache batch before running a synchronous
	 * write. Shared storage keeps the complete drain and replacement under the
	 * per-user lifecycle gate, preventing a newer cache operation from interleaving.
	 */
	public void flushChangesAndRun(Runnable action) {
		if (action == null) return;
		initializeSharedStorage();
		Consumer<Runnable> gate;
		synchronized (this) {
			gate = sharedExclusiveFlushGate;
		}
		if (gate != null) {
			ArrayList<Runnable> notifications = new ArrayList<>();
			java.util.concurrent.atomic.AtomicBoolean flushNow = new java.util.concurrent.atomic.AtomicBoolean();
			try {
				gate.accept(() -> {
					synchronized (this) {
						if (removing || uuid == null || cache == null || cachedChanges == null) {
							throw new IllegalStateException("Shared user cache is retiring");
						}
						exclusiveFlushesPending++;
					}
					try {
						// Admission is the durable boundary for this checkpoint. Mutations
						// accepted before it remain in the normal queue and are flushed first;
						// only later mutations use the staging queue.
						drainChangesStagedBeforeExclusiveAdmission(notifications);
						while (true) {
							Runnable notification = processChangesInternal(true);
							if (notification != null) notifications.add(notification);
							synchronized (this) {
								if (cachedChanges != null && !cachedChanges.isEmpty()) continue;
							}
							action.run();
							break;
						}
					} finally {
						synchronized (this) {
							exclusiveFlushesPending--;
							if (exclusiveFlushesPending == 0) {
								flushNow.set(flushChangesAfterExclusive);
								flushChangesAfterExclusive = false;
								drainChangesStagedBeforeExclusiveAdmission(notifications);
								if (!flushNow.get() && cachedChanges != null && !cachedChanges.isEmpty()
										&& !scheduled) scheduleChanges();
							}
						}
						// Submission belongs to this checkpoint's ordered handoff. The manager
						// queues callback execution, so listeners still run only after this
						// per-user exclusive admission has been released.
						for (Runnable notification : notifications) {
							manager.dispatchSharedUserDataNotification(notification);
						}
					}
				});
			} finally {
				if (flushNow.get()) scheduleImmediateSharedFlush();
			}
			return;
		}
		legacyMutationOrder.writeLock().lock();
		try {
			while (true) {
				processChanges();
				synchronized (this) {
					while (inFlightBatches > 0) {
						try {
							wait();
						} catch (InterruptedException e) {
							Thread.currentThread().interrupt();
							throw new IllegalStateException("Interrupted while flushing cached user changes", e);
						}
					}
					if (cachedChanges != null && !cachedChanges.isEmpty()) continue;
				}
				action.run();
				return;
			}
		} finally { legacyMutationOrder.writeLock().unlock(); }
	}

	/** Caller holds either this monitor or the exclusive per-user admission. */
	private void drainChangesStagedBeforeExclusiveAdmission(ArrayList<Runnable> notifications) {
		synchronized (this) {
			UserDataChange deferred;
			while ((deferred = changesAfterExclusiveFlush.poll()) != null) {
				// A checkpoint action may replace the visible cache snapshot. Re-publish
				// mutations ordered after that checkpoint as they cross back into the
				// normal queue so read-after-write visibility survives the replacement.
				publishChangeInternal(deferred);
				cachedChanges.add(deferred);
			}
			Runnable deferredNotification;
			while ((deferredNotification = notificationsAfterExclusiveFlush.poll()) != null) {
				notifications.add(deferredNotification);
			}
			// The admitted checkpoint will flush every change it just claimed.
			flushChangesAfterExclusive = false;
		}
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
		java.util.concurrent.atomic.AtomicReference<Runnable> notification = new java.util.concurrent.atomic.AtomicReference<>();
		gate.accept(() -> {
			notification.set(processChangesInternal(true));
			synchronized (this) { if (cache != null) { cache.clear(); recordSnapshotReplacement(); } }
		});
		Runnable callback = notification.get();
		deliverNotification(callback);
	}

	public void clearChanges() {
		Runnable callback = clearChangesForRefresh();
		deliverNotification(callback);
	}

	/**
	 * Flush queued changes for a cache refresh, but leave the user-data callback to
	 * the caller. Cache refreshes run under shared per-user admission, while a
	 * callback is permitted to remove that user and therefore needs exclusive
	 * admission. Running it here would attempt an unsupported lock upgrade.
	 */
	Runnable clearChangesForRefresh() {
		if (!hasChangesToProcess()) return null;
		if (manager != null && manager.deferSharedStorageWork(this::clearChangesAndNotify)) return null;
		return clearChangesNow();
	}

	private void clearChangesAndNotify() {
		Runnable callback = clearChangesNow();
		deliverNotification(callback);
	}

	private Runnable clearChangesNow() {
		if (!hasChangesToProcess()) return null;
		Consumer<Runnable> gate;
		synchronized (this) { gate = sharedFlushGate; }
		if (gate == null) return processChangesInternal(false);
		java.util.concurrent.atomic.AtomicReference<Runnable> notification = new java.util.concurrent.atomic.AtomicReference<>();
		gate.accept(() -> notification.set(processChangesInternal(true)));
		return notification.get();
	}

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
	public synchronized boolean hasStoredData() { return storedDataPresent; }
	public synchronized boolean hasPublishedStorageSnapshot() { return storageSnapshotPublished; }
	/** Observe publication and values under the same cache monitor. */
	public synchronized HashMap<String, DataValue> snapshotIfPublished() {
		return storageSnapshotPublished ? (cache == null ? new HashMap<>() : new HashMap<>(cache)) : null;
	}
	public synchronized HashMap<String, DataValue> snapshot() {
		return cache == null ? new HashMap<>() : new HashMap<>(cache);
	}
	public synchronized boolean hasChangesToProcess() { return cachedChanges != null && !cachedChanges.isEmpty(); }
	public synchronized boolean isCached(String key) { return cache != null && cache.containsKey(key); }

	public synchronized void ensureNoLegacyBatchForSharedBinding() {
		if (sharedStorageWriter == null && inFlightBatches != 0) throw new IllegalStateException("Cannot attach shared storage during an active legacy batch");
	}

	/** Retained for binary compatibility with integrations that provide one admission gate. */
	public void configureSharedStorage(Consumer<HashMap<String, DataValue>> writer, Consumer<Runnable> gate) {
		configureSharedStorage(writer, gate, gate);
	}

	public synchronized void configureSharedStorage(Consumer<HashMap<String, DataValue>> writer, Consumer<Runnable> gate,
			Consumer<Runnable> exclusiveGate) {
		if (sharedFlushGate != null && sharedFlushGate != gate) throw new IllegalStateException("Shared user cache already belongs to another runtime");
		if (sharedExclusiveFlushGate != null && sharedExclusiveFlushGate != exclusiveGate) throw new IllegalStateException("Shared user cache already belongs to another runtime");
		if (sharedFlushGate == null && gate != null && inFlightBatches != 0) throw new IllegalStateException("Cannot attach shared storage during an active legacy batch");
		setSharedStorageWriter(writer);
		sharedFlushGate = gate;
		sharedExclusiveFlushGate = exclusiveGate;
	}

	public synchronized void setSharedStorageWriter(Consumer<HashMap<String, DataValue>> writer) {
		if (uuid == null || cachedChanges == null) throw new IllegalStateException("Shared user cache is retired");
		sharedStorageWriter = java.util.Objects.requireNonNull(writer, "writer");
	}

	public synchronized void retireAfterSharedFlush() {
		if (inFlightBatches != 0 || (cachedChanges != null && !cachedChanges.isEmpty())) throw new IllegalStateException("Shared user cache has unflushed work");
		recordSnapshotReplacement(); cache = null; cachedChanges = null; uuid = null; scheduled = false;
	}

	/** Prevent reentrant change listeners from resurrecting a cache being removed. */
	public synchronized void beginRemoval() { removing = true; }

	/** Reopen a cache when a manager-wide removal could not flush this cache. */
	public synchronized void cancelRemoval() {
		if (cache != null && cachedChanges != null) removing = false;
	}

	public void processChanges() {
		initializeSharedStorage();
		Runnable notification = processChangesInternal(false);
		deliverNotification(notification);
	}

	private void deliverNotification(Runnable notification) {
		if (notification == null) return;
		if (manager != null && manager.hasSharedSqlBackend()) manager.dispatchSharedUserDataNotification(notification);
		else notification.run();
	}

	/**
	 * Persist one shared-runtime batch while returning its change notification to
	 * the runtime. The runtime delivers it only after releasing lifecycle and
	 * per-user admission, so listeners may safely request exclusive user work.
	 */
	public Runnable processChangesForSharedRuntime() {
		initializeSharedStorage();
		return processChangesInternal(false);
	}

	/** Flush now when blocking is allowed, otherwise preserve ordering on the cache worker. */
	public void processChangesImmediately(boolean async) {
		if (async) {
			processChangesAsync();
			return;
		}
		if (manager != null && manager.hasSharedSqlBackend()) {
			if (manager.deferSharedStorageWork(() -> processChangesImmediately(false))) return;
			initializeSharedStorage();
			Runnable notification = processChangesInternal(false);
			if (notification != null) manager.dispatchSharedUserDataNotification(notification);
			return;
		}
		processChanges();
	}

	private Runnable processChangesInternal(boolean admitted) {
		UUID currentUuid = null;
		Consumer<HashMap<String, DataValue>> writer = null;
		Consumer<Runnable> gate;
		ArrayList<UserDataChange> changes = new ArrayList<>();
		HashMap<String, Long> persistedMutationVersions = new HashMap<>();
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
				for (UserDataChange queuedChange : changes) {
					Long version = changedAt.get(queuedChange.getKey());
					if (version != null) persistedMutationVersions.put(queuedChange.getKey(), version);
				}
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
			synchronized (this) {
				if (!values.isEmpty()) storedDataPresent = true;
				if (cache != null) {
					for (UserDataChange persistedChange : changes) {
						Long persistedVersion = persistedMutationVersions.get(persistedChange.getKey());
						Long queuedVersion = changedAt.get(persistedChange.getKey());
						// A setter may have queued a newer value while this batch was in
						// storage. Keep that visible value until its own batch succeeds.
						// A public updateCache() replacement, however, is a storage snapshot
						// rather than another mutation. It clears changedAt(), so reconcile
						// that stale snapshot with this completed write instead of leaving a
						// value visible which will never be persisted.
						if (queuedVersion == null || Objects.equals(persistedVersion, queuedVersion)) {
							cache.put(persistedChange.getKey(), persistedChange.toUserDataValue());
						}
					}
				}
				// The in-memory values now represent the successful write. Mark that
				// replacement so an older in-flight read cannot overwrite them.
				persistedMutationVersions.forEach((key, version) -> persistedAt.put(key, version));
				persistedMutationVersions.forEach((key, version) -> {
					if (version.equals(changedAt.get(key))) changedAt.remove(key);
				});
			}
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
		Runnable notification = () -> {
			manager.getPlugin().getUserManager().onChange(notifyUser, notifyKeys);
			for (UserDataChange change : changes) change.dump();
		};
		if (manager != null && manager.hasSharedSqlBackend()) {
			Runnable captured = manager.captureSharedUserDataNotification(notification);
			if (captured != null) return captured;
		}
		return notification;
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

	private void scheduleImmediateSharedFlush() {
		if (manager != null && manager.deferSharedStorageWork(() -> processChangesImmediately(false))) return;
		processChangesAsync();
	}

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
		storageSnapshotPublished = true;
		recordSnapshotReplacement();
	}

	public synchronized void updateCachePreservingPending(HashMap<String, DataValue> storageValues) {
		HashMap<String, DataValue> refreshed = storageValues == null ? new HashMap<>() : new HashMap<>(storageValues);
		storedDataPresent = !refreshed.isEmpty();
		refreshed.putAll(inFlightValues);
		if (cachedChanges != null) for (UserDataChange change : cachedChanges) refreshed.put(change.getKey(), change.toUserDataValue());
		cache = refreshed;
		storageSnapshotPublished = true;
		recordSnapshotReplacement();
	}

	public synchronized long getSharedSnapshotVersion() { return snapshotVersion; }

	public synchronized HashMap<String, DataValue> updateSharedSnapshot(HashMap<String, DataValue> values,
			long expectedVersion) {
		return updateSharedSnapshot(values, expectedVersion, uuid, values != null && !values.isEmpty());
	}

	private synchronized HashMap<String, DataValue> updateSharedSnapshot(HashMap<String, DataValue> values,
			long expectedVersion, UUID expectedUuid, boolean snapshotStoredDataPresent) {
		if (cache == null || uuid == null || !uuid.equals(expectedUuid)) throw new IllegalStateException("Shared user cache changed while loading");
		if (expectedVersion < 0 || expectedVersion > snapshotVersion) throw new IllegalArgumentException("Invalid cache snapshot version");
		if (replacementVersion > expectedVersion) return new HashMap<>(cache);
		storedDataPresent = snapshotStoredDataPresent;
		HashMap<String, DataValue> merged = values == null ? new HashMap<>() : new HashMap<>(values);
		changedAt.forEach((key, version) -> { if (version >= expectedVersion && cache.containsKey(key)) merged.put(key, cache.get(key)); });
		persistedAt.forEach((key, version) -> { if (version >= expectedVersion && cache.containsKey(key)) merged.put(key, cache.get(key)); });
		cache = merged;
		storageSnapshotPublished = true;
		recordSnapshotReplacement();
		persistedAt.clear();
		return new HashMap<>(cache);
	}

	private void recordSnapshotReplacement() {
		replacementVersion = ++snapshotVersion;
		// A storage/read snapshot replaces unqueued local observations, but it must
		// not erase the version fence for a mutation that is still waiting in the
		// write queue. Otherwise an older in-flight batch can overwrite that newer
		// visible value when it completes.
		changedAt.keySet().removeIf(key -> cachedChanges == null
				|| cachedChanges.stream().noneMatch(change -> key.equals(change.getKey())));
		if (cache != null && cachedChanges != null) {
			for (UserDataChange change : cachedChanges) {
				cache.put(change.getKey(), change.toUserDataValue());
			}
		}
	}
}
