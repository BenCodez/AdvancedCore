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
	private com.bencodez.advancedcore.api.user.UserStorage legacyStorageType;
	private com.bencodez.advancedcore.AdvancedCorePlugin.UserStorageOwner legacyStorageOwner;
	private long snapshotVersion;
	private long replacementVersion;
	private boolean storedDataPresent;
	private boolean storageSnapshotPublished;
	private boolean hasEverPublishedStorageSnapshot;
	private boolean hasPublishedStorageSnapshot;
	private boolean completeStorageSnapshot;
	private final HashMap<String, Long> changedAt = new HashMap<>();
	private final HashMap<String, Long> persistedAt = new HashMap<>();
	private final HashMap<String, DataValue> persistedValues = new HashMap<>();
	private final HashMap<String, DataValue> inFlightValues = new HashMap<>();
	private record LegacyBulkOverride(DataValue value, long version, boolean sqlite) { }
	private final HashMap<String, LegacyBulkOverride> legacySqliteBulkOverrides = new HashMap<>();
	private final HashMap<String, LegacyBulkOverride> legacyExactBulkOverrides = new HashMap<>();
	private final HashMap<String, Long> sqliteChangedAt = new HashMap<>();
	private final HashMap<String, DataValue> sqliteChangedValues = new HashMap<>();
	private final HashMap<String, String> sqliteCacheKeys = new HashMap<>();
	private boolean scheduled = false;
	private boolean removing;
	private int inFlightBatches = 0;
	private volatile Consumer<HashMap<String, DataValue>> sharedStorageWriter;
	private com.bencodez.advancedcore.api.user.UserStorage sharedStorageType;
	private Thread sharedBatchThread;
	private volatile Consumer<Runnable> sharedFlushGate;
	private volatile Consumer<Runnable> sharedExclusiveFlushGate;
	private int exclusiveFlushesPending;
	private final Queue<UserDataChange> changesAfterExclusiveFlush = new ConcurrentLinkedQueue<>();
	private final Queue<Runnable> notificationsAfterExclusiveFlush = new ConcurrentLinkedQueue<>();
	private boolean flushChangesAfterExclusive;
	private final ReentrantReadWriteLock legacyMutationOrder = new ReentrantReadWriteLock(true);
	private int legacyCheckpointsPending;
	private record LegacyBatchContext(UserDataManager manager, UUID uuid, Object values, UserDataManager.LegacyRead read,
			com.bencodez.advancedcore.api.user.UserStorage storage, java.util.concurrent.atomic.AtomicReference<Throwable> delegatedFailure) { }
	private static final ThreadLocal<LegacyBatchContext> LEGACY_BATCH_PERSISTENCE = new ThreadLocal<>();
	private static final ThreadLocal<LegacyBatchContext> LEGACY_DELEGATED_BATCH = new ThreadLocal<>();
	/** Opaque identity for a currently executing legacy batch. It is not transferable between threads. */
	public static final class LegacyBatchToken {
		private final LegacyBatchContext context;
		private final Thread owner;
		private LegacyBatchToken(LegacyBatchContext context) { this.context = context; this.owner = Thread.currentThread(); }
	}

	/**
	 * Capture the active batch identity when an override receives its original map.
	 * Returns null for ordinary independent calls. Overrides which copy a batch map
	 * may pass the returned token to {@link #withLegacyBatchCopy} while delegating.
	 */
	public static LegacyBatchToken captureLegacyBatchToken(HashMap<String, DataValue> originalValues) {
		LegacyBatchContext current = LEGACY_BATCH_PERSISTENCE.get();
		return current != null && current.values() == originalValues ? new LegacyBatchToken(current) : null;
	}

	/**
	 * Explicitly delegate a copy within the same synchronous invocation. The token
	 * carries the original destination and generation; independent nested writes
	 * must not use it. An expired or cross-thread token is rejected.
	 */
	public static void withLegacyBatchCopy(LegacyBatchToken token, HashMap<String, DataValue> copiedValues, Runnable delegate) {
		java.util.Objects.requireNonNull(token, "token");
		java.util.Objects.requireNonNull(copiedValues, "copiedValues");
		java.util.Objects.requireNonNull(delegate, "delegate");
		LegacyBatchContext prior = LEGACY_BATCH_PERSISTENCE.get();
		if (token.owner != Thread.currentThread() || prior != token.context) throw new IllegalStateException("Legacy batch token is not active on this thread");
		LEGACY_BATCH_PERSISTENCE.set(new LegacyBatchContext(prior.manager(), prior.uuid(), copiedValues, prior.read(), prior.storage(), prior.delegatedFailure()));
		try { delegate.run(); }
		finally { LEGACY_BATCH_PERSISTENCE.set(prior); }
	}
	private static final class LegacyDirectContext {
		final UserDataManager manager;
		UserDataManager.LegacyDirectWrite admission;
		final UserDataChange change;
		final java.util.Set<String> sqliteObserved = new java.util.HashSet<>();
		boolean sqliteExecuting;
		Object sqliteProvider;
		String sqliteMoveDestination;
		LegacyDirectContext(UserDataManager manager, UserDataManager.LegacyDirectWrite admission, UserDataChange change) {
			this.manager = manager; this.admission = admission; this.change = change;
		}
	}
	private static final ThreadLocal<LegacyDirectContext> LEGACY_DIRECT_PERSISTENCE = new ThreadLocal<>();
	private record LegacyCallback(UserDataManager manager, UserDataManager.LegacyDirectWrite admission, LegacyCallback previous) { }
	private static final ThreadLocal<LegacyCallback> LEGACY_DIRECT_CALLBACK = new ThreadLocal<>();
	private record LegacyPublication(UserDataManager manager, UserDataManager.LegacyDirectWrite admission, UserDataCache cache) { }
	static final class DirectCachePublication {
		final HashMap<String, DataValue> previousValues = new HashMap<>();
		final HashMap<String, Long> previousVersions = new HashMap<>();
		final java.util.Set<String> keys = new java.util.HashSet<>();
		String sqliteKey, previousSqliteAlias;
		Long previousSqliteVersion;
		DataValue previousSqliteValue;
	}
	private DirectCachePublication captureDirectCachePublication(String key, String mysqlAlias) {
		DirectCachePublication previous = new DirectCachePublication();
		previous.keys.add(key);
		for (String candidate : cache.keySet()) if (candidate != null && (candidate.equals(key)
				|| (mysqlAlias != null && candidate.equalsIgnoreCase(key))
				|| (legacyStorageType == com.bencodez.advancedcore.api.user.UserStorage.SQLITE && sqliteDirectKey(candidate).equals(sqliteDirectKey(key))))) previous.keys.add(candidate);
		for (String candidate : previous.keys) {
			if (cache.containsKey(candidate)) previous.previousValues.put(candidate, cache.get(candidate));
			if (changedAt.containsKey(candidate)) previous.previousVersions.put(candidate, changedAt.get(candidate));
		}
		previous.sqliteKey = sqliteDirectKey(key);
		previous.previousSqliteVersion = sqliteChangedAt.get(previous.sqliteKey);
		previous.previousSqliteValue = sqliteChangedValues.get(previous.sqliteKey);
		previous.previousSqliteAlias = sqliteCacheKeys.get(previous.sqliteKey);
		return previous;
	}
	synchronized void restoreRedirectedPublication(DirectCachePublication previous, long version) {
		if (previous == null || cache == null) return;
		for (String key : previous.keys) if (java.util.Objects.equals(changedAt.get(key), version)) {
			if (previous.previousValues.containsKey(key)) cache.put(key, previous.previousValues.get(key)); else cache.remove(key);
			if (previous.previousVersions.containsKey(key)) changedAt.put(key, previous.previousVersions.get(key)); else changedAt.remove(key);
		}
		if (java.util.Objects.equals(sqliteChangedAt.get(previous.sqliteKey), version)) {
			if (previous.previousSqliteVersion == null) sqliteChangedAt.remove(previous.sqliteKey); else sqliteChangedAt.put(previous.sqliteKey, previous.previousSqliteVersion);
			if (previous.previousSqliteValue == null) sqliteChangedValues.remove(previous.sqliteKey); else sqliteChangedValues.put(previous.sqliteKey, previous.previousSqliteValue);
			if (previous.previousSqliteAlias == null) sqliteCacheKeys.remove(previous.sqliteKey); else sqliteCacheKeys.put(previous.sqliteKey, previous.previousSqliteAlias);
		}
	}
	private static final ThreadLocal<LegacyPublication> LEGACY_DIRECT_PUBLICATION = new ThreadLocal<>();
	public static void runWithLegacyDirectPublication(UserDataManager manager, UserDataManager.LegacyDirectWrite admission, UserDataCache cache, Runnable publication) {
		if (manager == null || admission == null || cache == null) { publication.run(); return; }
		if (!manager.matchesLegacyDirectCacheRow(admission, cache)) return;
		LegacyPublication previous = LEGACY_DIRECT_PUBLICATION.get();
		LEGACY_DIRECT_PUBLICATION.set(new LegacyPublication(manager, admission, cache));
		try { publication.run(); }
		finally { if (previous == null) LEGACY_DIRECT_PUBLICATION.remove(); else LEGACY_DIRECT_PUBLICATION.set(previous); }
	}
	public static void runWithLegacyDirectCallback(UserDataManager manager, UserDataManager.LegacyDirectWrite admission, Runnable notification) {
		if (manager == null || admission == null) { notification.run(); return; }
		LegacyCallback previous = LEGACY_DIRECT_CALLBACK.get();
		LEGACY_DIRECT_CALLBACK.set(new LegacyCallback(manager, admission, previous));
		try { notification.run(); }
		finally { if (previous == null) LEGACY_DIRECT_CALLBACK.remove(); else LEGACY_DIRECT_CALLBACK.set(previous); }
	}
	static UserDataManager.LegacyDirectWrite currentLegacyCallback(UserDataManager manager) {
		LegacyCallback callback = LEGACY_DIRECT_CALLBACK.get();
		return callback != null && callback.manager() == manager ? callback.admission() : null;
	}
	static boolean hasLiveLegacyCallback(UserDataManager manager) {
		for (LegacyCallback callback = LEGACY_DIRECT_CALLBACK.get(); callback != null; callback = callback.previous()) {
			if (callback.manager() == manager && manager.isLiveLegacyDirectWrite(callback.admission())) return true;
		}
		return false;
	}
	public static void runWithLegacyDirectWrite(UserDataManager manager, UserDataManager.LegacyDirectWrite admission, UserDataChange change, Runnable operation) {
		LegacyDirectContext previous = LEGACY_DIRECT_PERSISTENCE.get();
		if (manager != null && admission != null) LEGACY_DIRECT_PERSISTENCE.set(new LegacyDirectContext(manager, admission, change));
		else LEGACY_DIRECT_PERSISTENCE.remove();
		try { operation.run(); }
		finally { if (previous == null) LEGACY_DIRECT_PERSISTENCE.remove(); else LEGACY_DIRECT_PERSISTENCE.set(previous); }
	}
	public static boolean hasLegacyDirectPersistence() { return LEGACY_DIRECT_PERSISTENCE.get() != null; }
	public static com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL capturedNestedMysql() {
		LegacyDirectContext current = LEGACY_DIRECT_PERSISTENCE.get();
		return current == null ? null : current.manager.capturedNestedMysql(current.admission);
	}
	public static com.bencodez.advancedcore.api.user.userstorage.sql.UserTable capturedNestedSqlite() {
		LegacyDirectContext current = LEGACY_DIRECT_PERSISTENCE.get();
		return current == null ? null : current.manager.capturedNestedSqlite(current.admission);
	}
	public static boolean hasLegacyDirectPersistence(String row) {
		LegacyDirectContext context = LEGACY_DIRECT_PERSISTENCE.get();
		if (context == null || row == null) return false;
		try { return context.manager.isLegacyDirectIdentity(context.admission, row); }
		catch (IllegalArgumentException invalidUuid) { return false; }
	}
	/** Bind a previously admitted direct operation to the row actually selected by its provider. */
	public static void bindLegacyDirectRow(String row, boolean sqlite) {
		LegacyDirectContext context = LEGACY_DIRECT_PERSISTENCE.get();
		if (context == null || row == null) return;
		try { context.admission = context.manager.bindLegacyDirectRow(context.admission, row, sqlite); }
		catch (IllegalArgumentException invalidUuid) { /* Preserve legacy non-UUID identities. */ }
	}
	/** SQLite folds ASCII identifier case, but does not fold non-ASCII identifiers. */
	public static String sqliteDirectKey(String key) {
		StringBuilder result = new StringBuilder("SQLITE:");
		for (int i = 0; i < key.length(); i++) { char c = key.charAt(i); result.append(c >= 'A' && c <= 'Z' ? (char) (c + ('a' - 'A')) : c); }
		return result.toString();
	}
	/** Retain a provider-reported failure without changing the legacy caller's exception contract. */
	public static void recordLegacyDirectWriteFailure(String uuid, Throwable failure) {
		LegacyDirectContext context = LEGACY_DIRECT_PERSISTENCE.get();
		if (context == null || uuid == null) return;
		try { if (context.manager.isLegacyDirectIdentity(context.admission, uuid)) {
			LegacyBatchContext delegated = LEGACY_DELEGATED_BATCH.get();
			if (delegated != null && context.change == null && delegated.manager() == context.manager
					&& context.manager.isRelatedLegacyBatchFailure(context.admission, delegated.uuid(), delegated.storage(), delegated.values()))
				delegated.delegatedFailure().compareAndSet(null, failure);
			context.manager.rollbackSqliteDirectCommit(context.admission);
			context.manager.failLegacyDirectWrite(context.admission, context.change, failure);
		} }
		catch (IllegalArgumentException invalidUuid) { /* Preserve legacy non-UUID identities. */ }
	}
	public static boolean isCurrentLegacyDirectKey(String uuid, String key) {
		LegacyDirectContext context = LEGACY_DIRECT_PERSISTENCE.get();
		if (context == null) return true;
		try { return context.manager.isCurrentLegacyDirectKey(context.admission, uuid, sqliteDirectKey(key)); }
		catch (IllegalArgumentException invalidUuid) { return true; }
	}
	public static boolean isCurrentLegacyProviderKey(String uuid, String key, com.bencodez.advancedcore.api.user.UserStorage storage, boolean foldColumns) {
		LegacyDirectContext context = LEGACY_DIRECT_PERSISTENCE.get();
		if (context == null || uuid == null || key == null || storage == null) return true;
		try { return storage == com.bencodez.advancedcore.api.user.UserStorage.MYSQL
				? context.manager.isCurrentMysqlDirectKey(context.admission, uuid, key, foldColumns)
				: context.manager.isCurrentLegacyDirectKey(context.admission, uuid, storage.name() + ":" + key); }
		catch (IllegalArgumentException invalidUuid) { return true; }
	}
	public static void recordLegacyProviderCommit(String uuid, java.util.Collection<String> keys, com.bencodez.advancedcore.api.user.UserStorage storage, boolean foldColumns) {
		recordLegacyProviderCommit(uuid, keys, storage, foldColumns, null);
	}
	public static void recordLegacyProviderCommit(String uuid, java.util.Collection<String> keys, com.bencodez.advancedcore.api.user.UserStorage storage, boolean foldColumns, Object provider) {
		LegacyDirectContext context = LEGACY_DIRECT_PERSISTENCE.get();
		if (context == null || uuid == null || storage == null) return;
		try {
			if (!context.manager.isLegacyDirectIdentity(context.admission, uuid)) return;
			java.util.HashSet<String> identities = new java.util.HashSet<String>();
			for (String key : keys) if (key != null) identities.add(storage.name() + ":" + key);
			context.manager.recordLegacyProviderCommit(context.admission, identities, foldColumns, provider);
		} catch (IllegalArgumentException invalidUuid) { /* Preserve non-UUID behavior. */ }
	}
	/** Record only keys actually admitted by SQLite during the guarded statement. */
	public static boolean observeSqliteDirectKey(String uuid, String key) {
		boolean current = isCurrentLegacyDirectKey(uuid, key);
		LegacyDirectContext context = LEGACY_DIRECT_PERSISTENCE.get();
		if (current && context != null && context.sqliteExecuting) context.sqliteObserved.add(sqliteDirectKey(key));
		return current;
	}
	public static void beginSqliteDirectExecution(boolean insertStatement) {
		beginSqliteDirectExecution(insertStatement, null);
	}
	public static void beginSqliteDirectExecution(boolean insertStatement, Object provider) {
		LegacyDirectContext context = LEGACY_DIRECT_PERSISTENCE.get();
		if (context != null) { context.sqliteObserved.clear(); context.sqliteExecuting = true; context.sqliteProvider = provider; context.manager.recordSqliteDirectStatement(context.admission, insertStatement); }
	}
	public static void setSqliteMoveDestination(String source, String destination) {
		LegacyDirectContext context = LEGACY_DIRECT_PERSISTENCE.get();
		if (context != null) context.sqliteMoveDestination = destination != null && !destination.equals(source) ? destination : null;
	}
	public static void classifySqliteDirectStatement(boolean insertStatement) {
		LegacyDirectContext context = LEGACY_DIRECT_PERSISTENCE.get();
		if (context != null) context.manager.recordSqliteDirectStatement(context.admission, insertStatement);
	}
	public static boolean hasAdmittedSqliteDirectKey(String key) {
		LegacyDirectContext context = LEGACY_DIRECT_PERSISTENCE.get();
		return context != null && context.manager.hasLegacyDirectKey(context.admission, sqliteDirectKey(key));
	}
	public static void endSqliteDirectExecution() {
		LegacyDirectContext context = LEGACY_DIRECT_PERSISTENCE.get();
		if (context != null) context.sqliteExecuting = false;
	}
	public static void sqliteDirectCommit() {
		sqliteDirectCommit(null);
	}
	public static void sqliteDirectCommit(Object provider) {
		LegacyDirectContext context = LEGACY_DIRECT_PERSISTENCE.get();
		if (context != null && context.sqliteExecuting && (context.sqliteProvider == null || context.sqliteProvider == provider)) {
			context.manager.recordSqliteDirectCommit(context.admission, context.sqliteObserved, provider);
			if (context.sqliteObserved.contains(sqliteDirectKey("uuid"))) context.manager.recordSqliteDestinationCommit(context.admission, context.sqliteMoveDestination);
		}
	}
	public static void sqliteDirectRollback() {
		sqliteDirectRollback(null);
	}
	public static void sqliteDirectRollback(Object provider) {
		LegacyDirectContext context = LEGACY_DIRECT_PERSISTENCE.get();
		if (context != null && context.sqliteExecuting && (context.sqliteProvider == null || context.sqliteProvider == provider)) context.manager.rollbackSqliteDirectCommit(context.admission);
	}
	public static final class StaleLegacyBatchException extends IllegalStateException {
		private StaleLegacyBatchException() { super("Legacy batch was superseded by a direct write"); }
	}
	/**
	 * Participating providers call this under the same storage admission used by
	 * direct writes, immediately before the physical batch write. A check without
	 * that shared admission cannot prevent a later concurrent stale write. Opaque
	 * legacy overrides retain their existing behavior and do not gain this guarantee.
	 */
	public static void rejectStaleLegacyBatch(String uuid) {
		LegacyBatchContext current = LEGACY_BATCH_PERSISTENCE.get();
		if (current != null && current.uuid().toString().equalsIgnoreCase(uuid)
				&& !current.manager().isCurrentLegacyRead(current.read())) throw new StaleLegacyBatchException();
	}
	public static boolean hasLegacyBatchPersistence(String uuid) {
		LegacyBatchContext current = LEGACY_BATCH_PERSISTENCE.get();
		return current != null && current.uuid().toString().equalsIgnoreCase(uuid);
	}
	public static long confirmedLegacyBatchClaimSerial(String uuid) {
		LegacyBatchContext current = LEGACY_BATCH_PERSISTENCE.get();
		return current != null && uuid != null && current.uuid().toString().equals(uuid)
				? current.manager().confirmedNestedClaimSerial(current.read()) : 0;
	}
	public static void recordLegacyBatchProviderCommit(String uuid, java.util.Collection<String> keys, com.bencodez.advancedcore.api.user.UserStorage storage, boolean foldColumns) {
		LegacyBatchContext current = LEGACY_BATCH_PERSISTENCE.get();
		if (current == null || storage == null || current.storage() != storage || uuid == null || !current.uuid().toString().equalsIgnoreCase(uuid)) return;
		current.manager().recordCompletedLegacyCallbackBatch(current.read(), uuid, new java.util.HashSet<>(keys), storage, foldColumns);
	}
	public static void rejectLegacyBatchInsertConflict(String uuid) {
		if (hasLegacyBatchPersistence(uuid)) throw new StaleLegacyBatchException();
	}
	public static void propagateLegacyBatchWriteFailure(String uuid, Throwable failure) {
		if (hasLegacyBatchPersistence(uuid)) {
			if (failure instanceof StaleLegacyBatchException stale) throw stale;
			throw new IllegalStateException("Legacy cache batch storage failed", failure);
		}
	}
	public static boolean isLegacyBatchPersistence(UserDataManager manager, UUID uuid, Object values) {
		LegacyBatchContext current = LEGACY_BATCH_PERSISTENCE.get();
		return current != null && current.manager() == manager && current.uuid().equals(uuid) && current.values() == values;
	}
	static boolean hasActiveLegacyBatch(UserDataManager manager, UUID uuid) {
		LegacyBatchContext current = LEGACY_BATCH_PERSISTENCE.get();
		return current != null && current.manager() == manager && current.uuid().equals(uuid);
	}
	static UserDataManager.LegacyRead overlappingNestedRead(UserDataManager manager, UUID uuid, java.util.Set<String> keys) {
		LegacyBatchContext current = LEGACY_BATCH_PERSISTENCE.get();
		if (current == null || current.manager() != manager || !current.uuid().equals(uuid)
				|| current.storage() == null || keys.size() != 1
				|| !(current.values() instanceof java.util.Map<?, ?> values)) return null;
		String key = keys.iterator().next();
		if (key == null || !key.startsWith(current.storage().name() + ":")) return null;
		String name = key.substring(current.storage().name().length() + 1);
		if (name.equalsIgnoreCase("uuid")) return null;
		for (Object raw : values.keySet()) if (raw instanceof String valueKey && (current.storage() == com.bencodez.advancedcore.api.user.UserStorage.SQLITE
				? sqliteDirectKey(valueKey).equals(key) : valueKey.equals(name)
						|| (manager.capturedMysqlFoldsColumnNames(current.read()) && valueKey.chars().allMatch(c -> c < 128)
								&& name.chars().allMatch(c -> c < 128) && valueKey.equalsIgnoreCase(name)))) return current.read();
		return null;
	}
	/** Participating MySQL providers call this while holding their physical write admission. */
	public static java.util.List<com.bencodez.simpleapi.sql.Column> filterConfirmedLegacyBatchColumns(String uuid,
			java.util.List<com.bencodez.simpleapi.sql.Column> columns) {
		return filterConfirmedLegacyBatchColumns(uuid, columns, com.bencodez.advancedcore.api.user.UserStorage.MYSQL);
	}
	public static java.util.List<com.bencodez.simpleapi.sql.Column> filterConfirmedLegacyBatchColumns(String uuid,
			java.util.List<com.bencodez.simpleapi.sql.Column> columns, Object provider) {
		return filterConfirmedLegacyBatchColumns(uuid, columns, com.bencodez.advancedcore.api.user.UserStorage.MYSQL, provider);
	}
	/** SQLite calls this only under its existing-row provider admission. */
	public static java.util.List<com.bencodez.simpleapi.sql.Column> filterConfirmedLegacyBatchColumns(String uuid,
			java.util.List<com.bencodez.simpleapi.sql.Column> columns, com.bencodez.advancedcore.api.user.UserStorage storage) {
		return filterConfirmedLegacyBatchColumns(uuid, columns, storage, null);
	}
	public static java.util.List<com.bencodez.simpleapi.sql.Column> filterConfirmedLegacyBatchColumns(String uuid,
			java.util.List<com.bencodez.simpleapi.sql.Column> columns, com.bencodez.advancedcore.api.user.UserStorage storage, Object provider) {
		LegacyBatchContext current = LEGACY_BATCH_PERSISTENCE.get();
		if (current == null || uuid == null || current.storage() != storage
				|| (storage == com.bencodez.advancedcore.api.user.UserStorage.SQLITE ? !current.uuid().toString().equals(uuid) : !current.uuid().toString().equalsIgnoreCase(uuid))) return columns;
		java.util.Set<String> excluded = current.manager().claimConfirmedNestedKeys(current.read(), provider);
		if (excluded.isEmpty()) return columns;
		java.util.ArrayList<com.bencodez.simpleapi.sql.Column> result = new java.util.ArrayList<com.bencodez.simpleapi.sql.Column>();
		for (com.bencodez.simpleapi.sql.Column column : columns) if (!excluded.contains(storage == com.bencodez.advancedcore.api.user.UserStorage.SQLITE
				? sqliteDirectKey(column.getName()) : "MYSQL:" + column.getName())
				&& !(storage == com.bencodez.advancedcore.api.user.UserStorage.MYSQL && column.getName().chars().allMatch(c -> c < 128)
						&& excluded.contains("MYSQLF:" + column.getName().toLowerCase(java.util.Locale.ROOT)))) result.add(column);
		return result;
	}
	/** Return the enclosing batch only for clearly disjoint ASCII column identities. */
	static UserDataManager.LegacyRead disjointNestedLegacyRead(UserDataManager manager, UUID uuid, java.util.Set<String> keys) {
		LegacyBatchContext current = LEGACY_BATCH_PERSISTENCE.get();
		if (current == null || current.manager() != manager || !current.uuid().equals(uuid)
				|| current.storage() == null || keys.isEmpty() || !(current.values() instanceof java.util.Map<?, ?> values)) return null;
		for (String key : keys) {
			if (key == null || !key.startsWith(current.storage().name() + ":")) return null;
			String column = key.substring(current.storage().name().length() + 1);
			if (column.isEmpty() || column.equalsIgnoreCase("uuid") || !column.chars().allMatch(c -> c < 128)) return null;
			for (Object valueKey : values.keySet()) {
				if (!(valueKey instanceof String name) || !name.chars().allMatch(c -> c < 128) || name.equalsIgnoreCase(column)) return null;
			}
		}
		return current.read();
	}
	public static com.bencodez.advancedcore.api.user.UserStorage legacyBatchStorage(UserDataManager manager, UUID uuid, Object values) {
		LegacyBatchContext current = LEGACY_BATCH_PERSISTENCE.get();
		return isLegacyBatchPersistence(manager, uuid, values) ? current.storage() : null;
	}
	public static void runOutsideLegacyBatch(Runnable operation) {
		LegacyBatchContext previous = LEGACY_BATCH_PERSISTENCE.get();
		if (previous == null) { operation.run(); return; }
		LegacyBatchContext previousDelegated = LEGACY_DELEGATED_BATCH.get();
		LEGACY_DELEGATED_BATCH.set(previous);
		LEGACY_BATCH_PERSISTENCE.remove();
		try { operation.run(); }
		finally {
			LEGACY_BATCH_PERSISTENCE.set(previous);
			if (previousDelegated == null) LEGACY_DELEGATED_BATCH.remove(); else LEGACY_DELEGATED_BATCH.set(previousDelegated);
		}
	}
	private int legacyDirectWrites;
	private final Queue<UserDataChange> legacyChangesAfterCheckpoint = new ConcurrentLinkedQueue<>();
	private final java.util.IdentityHashMap<UserDataChange, Long> legacyChangeVersions = new java.util.IdentityHashMap<>();
	private long legacySqliteRowMoveVersion;
	private final HashMap<String, Long> legacyDirectPublicationSequences = new HashMap<>();
	private final java.util.Map<String, DataValue> legacyCheckpointLatestValues = new java.util.HashMap<>();
	@Getter private UUID uuid;

	public UserDataCache(UserDataManager manager, UUID uuid) {
		this.uuid = uuid;
		this.manager = manager;
		captureLegacyStorageType();
		cachedChanges = new ConcurrentLinkedQueue<>();
		cache = new HashMap<>();
	}
	private void captureLegacyStorageType() {
		if (legacyStorageType == null && sharedFlushGate == null && manager != null && manager.getPlugin() != null) {
			legacyStorageType = manager.getPlugin().getStorageType();
			com.bencodez.advancedcore.AdvancedCorePlugin.UserStorageOwner owner = manager.getPlugin().getNativeUserStorageOwner();
			if (owner != null && owner.storageType() == legacyStorageType) legacyStorageOwner = owner;
		}
	}
	public synchronized boolean matchesLegacyOwner(com.bencodez.advancedcore.AdvancedCorePlugin.UserStorageOwner owner) {
		return legacyStorageOwner == null || legacyStorageOwner.equals(owner);
	}
	public synchronized boolean matchesLegacyStorage(com.bencodez.advancedcore.api.user.UserStorage storage) {
		return storage == null || (legacyStorageType != null && legacyStorageType == storage);
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
		synchronized (this) {
			if (legacyCheckpointsPending != 0 || !legacyMutationOrder.readLock().tryLock()) {
				if (removing || uuid == null || cache == null || cachedChanges == null)
					throw new IllegalStateException("User cache is retiring");
				if (change != null) {
					publishChangeInternal(change);
					if (legacyCheckpointsPending != 0) legacyCheckpointLatestValues.put(change.getKey(), change.toUserDataValue());
					if (queue) { legacyChangeVersions.put(change, snapshotVersion); legacyChangesAfterCheckpoint.add(change); }
				}
				return;
			}
		}
		try { addChangeInternal(change, queue); }
		finally { legacyMutationOrder.readLock().unlock(); }
	}

	/** Keep a failed admitted direct write retryable without republishing its possibly stale value. */
	synchronized void retainFailedLegacyDirectChange(UserDataChange change, long admittedVersion, boolean sqlite) {
		if (change == null || removing || uuid == null || cache == null || cachedChanges == null)
			throw new IllegalStateException("Failed legacy direct write has no retryable cache");
		String key = change.getKey();
		boolean mysqlFold = !sqlite && key != null && legacyMysqlFoldsColumns();
		String mysqlAlias = mysqlFold ? resolveSharedMysqlKey(key) : null;
		if (mysqlFold && mysqlAlias == null) throw new IllegalStateException("MySQL column identity is ambiguous during retry");
		long latest = key == null ? 0 : sqlite ? sqliteChangedAt.getOrDefault(sqliteDirectKey(key), 0L) : changedAt.getOrDefault(key, 0L);
		if (mysqlFold) latest = Math.max(latest, changedAt.getOrDefault(mysqlAlias, 0L));
		if (latest <= admittedVersion) publishChangeInternal(change);
		UserDataChange retry = change;
		if ((sqlite || mysqlFold) && key != null && latest > admittedVersion) {
			DataValue newer = sqlite ? sqliteChangedValues.get(sqliteDirectKey(key)) : cache.get(mysqlAlias);
			if (newer == null) throw new IllegalStateException("Newer legacy value unavailable for retry");
			if (newer != null) {
				cache.put(key, newer);
				changedAt.put(key, latest);
				if (sqlite) sqliteCacheKeys.put(sqliteDirectKey(key), key);
				retry = new UserDataChange(key) {
					@Override public void dump() { setKey(null); }
					@Override public DataValue toUserDataValue() { return newer; }
				};
			}
		}
		legacyChangeVersions.put(retry, snapshotVersion); cachedChanges.add(retry);
		if (!scheduled) scheduleChanges();
	}

	private synchronized void beginLegacyCheckpoint() { legacyCheckpointsPending++; }

	/** Fence legacy row reads while a caller performs its existing direct write. */
	synchronized long beginLegacyDirectWrite() {
		if (sharedFlushGate != null || removing || uuid == null || cache == null || cachedChanges == null) return -1;
		legacyDirectWrites++;
		replacementVersion = ++snapshotVersion;
		return snapshotVersion;
	}

	synchronized void endLegacyDirectWrite() {
		if (legacyDirectWrites <= 0) throw new IllegalStateException("No legacy direct write is active");
		legacyDirectWrites--;
		if (legacyDirectWrites == 0) legacyDirectPublicationSequences.clear();
		replacementVersion = ++snapshotVersion;
	}

	/** Require a fresh authoritative read after an external bulk write. */
	synchronized void invalidateLegacyBulkSnapshot() {
		if (cache == null) return;
		storageSnapshotPublished = false;
		completeStorageSnapshot = false;
		replacementVersion = ++snapshotVersion;
	}
	synchronized void fenceIncomingSqliteMove(long admittedVersion) {
		if (cache == null) return;
		if (admittedVersion >= 0) legacySqliteRowMoveVersion = Math.max(legacySqliteRowMoveVersion, admittedVersion);
		storageSnapshotPublished = false; completeStorageSnapshot = false;
		replacementVersion = ++snapshotVersion;
	}

	/** Reconcile a successful external bulk SET against changes admitted before it. */
	synchronized void completeLegacyBulkValues(java.util.Map<String, DataValue> values, long admittedVersion,
			java.util.function.Function<String, String> identity) {
		if (cache == null || cachedChanges == null) return;
		for (java.util.Map.Entry<java.lang.String,com.bencodez.simpleapi.sql.data.DataValue> entry : values.entrySet()) {
			String normalized = identity.apply(entry.getKey());
			boolean sqlite = normalized.startsWith("SQLITE:");
			boolean mysqlFold = normalized.startsWith("MYSQL:") && legacyMysqlFoldsColumns();
			String mysqlAlias = mysqlFold ? resolveSharedMysqlKey(entry.getKey()) : null;
			if (mysqlFold && mysqlAlias == null) throw new IllegalStateException("MySQL column identity is ambiguous");
			long latest = sqlite ? sqliteChangedAt.getOrDefault(normalized, 0L) : changedAt.getOrDefault(entry.getKey(), 0L);
			if (mysqlAlias != null) latest = Math.max(latest, changedAt.getOrDefault(mysqlAlias, 0L));
			boolean newer = latest > admittedVersion;
			if (newer) continue;
			DataValue value = entry.getValue() == null ? new com.bencodez.simpleapi.sql.data.DataValueString("") : entry.getValue();
			if (sqlite && normalized.equals(sqliteDirectKey("uuid")) && value.isString() && uuid != null && !uuid.toString().equals(value.getString())) {
				legacySqliteRowMoveVersion = Math.max(legacySqliteRowMoveVersion, admittedVersion);
				storageSnapshotPublished = false;
				completeStorageSnapshot = false;
			}
			if (legacyCheckpointsPending != 0) legacyCheckpointLatestValues.put(entry.getKey(), value);
			cache.put(entry.getKey(), value);
			long version = ++snapshotVersion;
			changedAt.put(entry.getKey(), version);
			if (mysqlAlias != null) for (String alias : cache.keySet()) if (alias != null && !alias.equals(entry.getKey()) && alias.equalsIgnoreCase(entry.getKey())) {
				cache.put(alias, value); changedAt.put(alias, version);
				legacyExactBulkOverrides.put(alias, new LegacyBulkOverride(value, version, false));
			}
			if (sqlite) {
				for (String alias : cache.keySet()) if (alias != null && !alias.equals(entry.getKey()) && sqliteDirectKey(alias).equals(normalized)) {
					cache.put(alias, value); changedAt.put(alias, version);
				}
				sqliteChangedAt.put(normalized, version); sqliteChangedValues.put(normalized, value); sqliteCacheKeys.put(normalized, entry.getKey());
			}
			(sqlite ? legacySqliteBulkOverrides : legacyExactBulkOverrides).put(sqlite ? normalized : entry.getKey(), new LegacyBulkOverride(value, version, sqlite));
		}
	}

	/** Reconcile older queued values lazily, without walking the queue on a direct writer. */
	private DataValue resolveLegacyBulkValue(String key, DataValue fallback) {
		if (key == null) return fallback;
		LegacyBulkOverride sqlite = legacySqliteBulkOverrides.get(sqliteDirectKey(key));
		LegacyBulkOverride exact = legacyExactBulkOverrides.get(key);
		LegacyBulkOverride selected = sqlite == null ? exact : exact == null || sqlite.version() >= exact.version() ? sqlite : exact;
		if (selected == null) return fallback;
		long latest = selected.sqlite() ? sqliteChangedAt.getOrDefault(sqliteDirectKey(key), 0L) : changedAt.getOrDefault(key, 0L);
		if (latest <= selected.version()) return selected.value();
		DataValue newer = selected.sqlite() ? sqliteChangedValues.get(sqliteDirectKey(key)) : cache.get(key);
		return newer == null ? fallback : newer;
	}
	private DataValue resolveLegacyBulkValue(String key, DataValue fallback, Long queuedVersion) {
		if (key == null || queuedVersion == null) return resolveLegacyBulkValue(key, fallback);
		LegacyBulkOverride sqlite = legacySqliteBulkOverrides.get(sqliteDirectKey(key));
		LegacyBulkOverride exact = legacyExactBulkOverrides.get(key);
		LegacyBulkOverride selected = sqlite == null ? exact : exact == null || sqlite.version() >= exact.version() ? sqlite : exact;
		return selected != null && selected.version() > queuedVersion ? selected.value() : fallback;
	}
	private long effectiveLegacyQueuedVersion(String key, Long queuedVersion) {
		long version = queuedVersion == null ? 0 : queuedVersion;
		if (key == null) return version;
		LegacyBulkOverride sqlite = legacySqliteBulkOverrides.get(sqliteDirectKey(key));
		LegacyBulkOverride exact = legacyExactBulkOverrides.get(key);
		if (sqlite != null) version = Math.max(version, sqlite.version());
		if (exact != null) version = Math.max(version, exact.version());
		return version;
	}

	private synchronized boolean finishLegacyCheckpoint() {
		legacyCheckpointsPending--;
		if (legacyCheckpointsPending == 0) {
			if (!legacyChangesAfterCheckpoint.isEmpty() && (cache == null || cachedChanges == null))
				throw new IllegalStateException("Legacy cache retired with staged changes");
			UserDataChange change;
			while ((change = legacyChangesAfterCheckpoint.poll()) != null) cachedChanges.add(change);
			legacyCheckpointLatestValues.clear();
		}
		return cachedChanges != null && !cachedChanges.isEmpty() && !scheduled;
	}

	private void finishLegacyCheckpointAndUnlock() {
		boolean schedule = false;
		synchronized (this) {
			try { schedule = finishLegacyCheckpoint(); }
			finally { legacyMutationOrder.writeLock().unlock(); }
		}
		if (schedule) scheduleChanges();
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

	/** Supported arithmetic for a bounded, conditional cached integer mutation. */
	public enum IntegerMutation { SET, ADD, ADD_FLOOR_ZERO, DEBIT_POSITIVE }
	/** Cache mutation may have started; callers must not retry it as a new operation. */
	public static final class LegacyIntegerMutationUncertainException extends IllegalStateException {
		public LegacyIntegerMutationUncertainException(Throwable cause) { super("Legacy integer mutation outcome is uncertain", cause); }
	}
	/** Publication began but its full outcome is unknown; retrying may duplicate an award. */
	public static final class SharedIntegerMutationUncertainException extends IllegalStateException {
		public SharedIntegerMutationUncertainException(Throwable cause) { super("Shared integer mutation outcome is uncertain", cause); }
	}

	/** Previous values are returned so a caller can compensate precisely. */
	public record IntegerMutationResult(boolean applied, java.util.Map<String, Integer> previous) { }

	/**
	 * Atomically compute and enqueue a bounded set of integer changes on a published
	 * shared cache. Returns null when the cache is cold, retiring, or not bound; a
	 * DEBIT_POSITIVE result with applied=false means no requested balance was positive.
	 * No user callback or storage operation is run while the cache monitor is held.
	 * Returns null while an exclusive checkpoint can change the baseline. This is
	 * local cache atomicity, not a cross-process transaction.
	 * If notification submission is rejected after acceptance, the manager records
	 * the failure and the result still reports the already accepted mutation.
	 */
	public IntegerMutationResult tryMutateIntegers(java.util.Map<String, Integer> operands,
			IntegerMutation operation, java.util.function.Consumer<java.util.Set<String>> notification) {
		UUID identity = uuid;
		boolean admission = manager != null && identity != null && manager.hasSharedRuntimeLifecycle();
		if (admission && !manager.beginSharedSynchronousIntegerMutation(identity)) return null;
		try { return tryMutateIntegersAdmitted(operands, operation, notification); }
		finally { if (admission) manager.endSharedSynchronousIntegerMutation(identity); }
	}

	private IntegerMutationResult tryMutateIntegersAdmitted(java.util.Map<String, Integer> operands,
			IntegerMutation operation, java.util.function.Consumer<java.util.Set<String>> notification) {
		Objects.requireNonNull(operands, "operands"); Objects.requireNonNull(operation, "operation");
		if (operands.isEmpty() || operands.size() > 64) throw new IllegalArgumentException("Invalid integer mutation size");
		java.util.LinkedHashMap<String, Integer> ordered = new java.util.LinkedHashMap<String, Integer>();
		int copied = 0;
		for (java.util.Map.Entry<java.lang.String,java.lang.Integer> entry : operands.entrySet()) {
			if (++copied > 64) throw new IllegalArgumentException("Invalid integer mutation size");
			String key = entry.getKey(); Integer value = entry.getValue();
			if (key == null || key.isEmpty() || value == null) throw new IllegalArgumentException("Invalid integer mutation");
			if (operation == IntegerMutation.DEBIT_POSITIVE && value != -1)
				throw new IllegalArgumentException("Positive debit must decrement by one");
			ordered.put(key, value);
		}
		if (ordered.isEmpty()) throw new IllegalArgumentException("Invalid integer mutation size");
		Runnable notify = null;
		IntegerMutationResult result;
		synchronized (this) {
			if (sharedFlushGate == null || removing || uuid == null || cache == null || cachedChanges == null
					|| !storageSnapshotPublished || exclusiveFlushesPending != 0) return null;
			boolean sqlite = sharedStorageType == com.bencodez.advancedcore.api.user.UserStorage.SQLITE;
			java.util.LinkedHashMap<String, String> actualKeys = new java.util.LinkedHashMap<String, String>();
			java.util.HashSet<String> identities = new java.util.HashSet<String>();
			for (String key : ordered.keySet()) {
				if (sqlite && !identities.add(sqliteDirectKey(key))) throw new IllegalArgumentException("Duplicate SQLite column identity");
				if (sharedStorageType == com.bencodez.advancedcore.api.user.UserStorage.MYSQL && hasAmbiguousMysqlKey(key)) return null;
				String actual = sqlite ? resolveSharedSqliteKey(key) : key;
				if (actual == null) return null;
				actualKeys.put(key, actual);
			}
			var previous = new java.util.LinkedHashMap<String, Integer>();
			for (String key : ordered.keySet()) {
				String actual = actualKeys.get(key);
				if (!cache.containsKey(actual) || cache.get(actual) == null) return null;
				DataValue value = cache.get(actual);
				// Direct snapshot replacement intentionally remains visible to legacy
				// readers. It must not let arithmetic forget an accepted write whose
				// batch is still in flight. A newer local mutation retains changedAt.
				if (!changedAt.containsKey(actual) && inFlightValues.containsKey(actual))
					value = inFlightValues.get(actual);
				int number;
				if (value.isInt()) number = value.getInt();
				else if (value.isString()) {
					try { number = Integer.parseInt(value.getString()); }
					catch (NumberFormatException invalid) { number = 0; }
				} else number = 0;
				previous.put(key, number);
			}
			var changes = new java.util.ArrayList<UserDataChange>();
			var changed = new java.util.LinkedHashSet<String>();
			ordered.forEach((key, operand) -> {
				int old = previous.get(key);
				if (operation == IntegerMutation.DEBIT_POSITIVE && old <= 0) return;
				int updated = switch (operation) {
					case SET -> operand;
					case ADD, DEBIT_POSITIVE -> Math.addExact(old, operand);
					case ADD_FLOOR_ZERO -> Math.max(0, Math.addExact(old, operand));
				};
				changes.add(new com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt(actualKeys.get(key), updated));
				changed.add(key);
			});
			result = new IntegerMutationResult(!changes.isEmpty(), java.util.Map.copyOf(previous));
			if (changes.isEmpty()) return result;
			try {
				for (UserDataChange change : changes) {
					publishChangeInternal(change);
					cachedChanges.add(change);
				}
			} catch (RuntimeException | Error failure) { throw new SharedIntegerMutationUncertainException(failure); }
			if (!scheduled) {
				try { scheduleChanges(); }
				catch (RuntimeException | Error failure) {
					try { if (manager != null) manager.recordSharedStorageFailure(failure); }
					catch (RuntimeException | Error ignored) { }
				}
			}
			if (notification != null) {
				try {
					var snapshot = java.util.Set.copyOf(changed);
					Runnable raw = () -> notification.accept(snapshot);
					Runnable captured = manager.captureSharedUserDataNotification(raw);
					notify = captured == null ? raw : captured;
				} catch (RuntimeException | Error failure) {
					try { if (manager != null) manager.recordSharedStorageFailure(failure); }
					catch (RuntimeException | Error ignored) { }
				}
			}
		}
		if (notify != null) {
			try { manager.dispatchSharedUserDataNotification(notify); }
			catch (RuntimeException | Error failure) {
				// The mutation is already accepted; a failed notification cannot
				// turn it into a retryable failure.
				try { manager.recordSharedStorageFailure(failure); }
				catch (RuntimeException | Error ignored) { }
			}
		}
		return result;
	}

	/** Nonblocking cached arithmetic for a legacy cache; null means retry on a worker. */
	public IntegerMutationResult tryMutateLegacyIntegers(java.util.Map<String, Integer> operands,
			IntegerMutation operation, java.util.function.Consumer<java.util.Set<String>> notification) {
		Objects.requireNonNull(operands, "operands"); Objects.requireNonNull(operation, "operation");
		if (operands.isEmpty() || operands.size() > 64) throw new IllegalArgumentException("Invalid integer mutation size");
		java.util.LinkedHashMap<String, Integer> ordered = new java.util.LinkedHashMap<String, Integer>();
		int copied = 0;
		for (java.util.Map.Entry<java.lang.String,java.lang.Integer> entry : operands.entrySet()) {
			if (++copied > 64) throw new IllegalArgumentException("Invalid integer mutation size");
			String key = entry.getKey(); Integer value = entry.getValue();
			if (key == null || key.isEmpty() || value == null || (operation == IntegerMutation.DEBIT_POSITIVE && value != -1))
				throw new IllegalArgumentException("Invalid integer mutation");
			if (ordered.size() == 64) throw new IllegalArgumentException("Invalid integer mutation size");
			ordered.put(key, value);
		}
		if (ordered.isEmpty()) throw new IllegalArgumentException("Invalid integer mutation size");
		boolean sqlite = manager != null && manager.getPlugin().getStorageType() == com.bencodez.advancedcore.api.user.UserStorage.SQLITE;
		boolean mysql = manager != null && manager.getPlugin().getStorageType() == com.bencodez.advancedcore.api.user.UserStorage.MYSQL;
		Boolean mysqlFold = mysql && manager.getPlugin().getMysql() != null ? manager.getPlugin().getMysql().caseInsensitiveColumnNames() : null;
		if (sqlite) {
			java.util.HashSet<String> identities = new java.util.HashSet<String>();
			for (String key : ordered.keySet()) if (!identities.add(sqliteDirectKey(key))) throw new IllegalArgumentException("Duplicate SQLite column identity");
		}
		if (mysql && !Boolean.FALSE.equals(mysqlFold)) {
			java.util.HashSet<String> identities = new java.util.HashSet<String>();
			for (String key : ordered.keySet()) if (!identities.add(key.toLowerCase(java.util.Locale.ROOT))) return null;
		}
		if (!legacyMutationOrder.readLock().tryLock()) return null;
		IntegerMutationResult result;
		java.util.LinkedHashSet<String> changed = new java.util.LinkedHashSet<String>();
		try {
			synchronized (this) {
				if (sharedFlushGate != null || removing || legacyDirectWrites != 0 || legacyCheckpointsPending != 0 || uuid == null || cache == null || cachedChanges == null
						|| !storageSnapshotPublished) return null;
				if (manager != null && manager.getPlugin() != null && (!matchesLegacyStorage(manager.getPlugin().getStorageType())
						|| !matchesLegacyOwner(manager.getPlugin().getNativeUserStorageOwner()))) return null;
				java.util.LinkedHashMap<String, Integer> previous = new java.util.LinkedHashMap<String, Integer>();
				for (String key : ordered.keySet()) {
					String actual = key;
					if (sqlite) {
						String identity = sqliteDirectKey(key);
						if (sqliteCacheKeys.containsKey(identity)) {
							actual = sqliteCacheKeys.get(identity);
							if (actual == null) return null;
						}
					}
					if (mysql && Boolean.TRUE.equals(mysqlFold)) {
						actual = resolveSharedMysqlKey(key);
						if (actual == null) return null;
					} else if (mysql && mysqlFold == null && hasAmbiguousMysqlKey(key)) return null;
					DataValue value = cache.get(actual);
					if (!changedAt.containsKey(actual) && inFlightValues.containsKey(actual)) value = inFlightValues.get(actual);
					if (!completeStorageSnapshot && !changedAt.containsKey(actual) && !inFlightValues.containsKey(actual)) return null;
					if (value == null) {
						if (!completeStorageSnapshot || cache.containsKey(actual)) return null;
						previous.put(key, 0); continue;
					}
					int number;
					if (value.isInt()) number = value.getInt();
					else if (value.isString()) { try { number = Integer.parseInt(value.getString()); } catch (NumberFormatException invalid) { number = 0; } }
					else number = 0;
					previous.put(key, number);
				}
				java.util.ArrayList<UserDataChange> changes = new java.util.ArrayList<UserDataChange>();
				for (java.util.Map.Entry<java.lang.String,java.lang.Integer> entry : ordered.entrySet()) {
					int old = previous.get(entry.getKey());
					if (operation == IntegerMutation.DEBIT_POSITIVE && old <= 0) continue;
					int updated = switch (operation) {
						case SET -> entry.getValue();
						case ADD, DEBIT_POSITIVE -> Math.addExact(old, entry.getValue());
						case ADD_FLOOR_ZERO -> Math.max(0, Math.addExact(old, entry.getValue()));
					};
					String actual = entry.getKey();
					if (sqlite) actual = sqliteCacheKeys.getOrDefault(sqliteDirectKey(actual), actual);
					if (mysql && Boolean.TRUE.equals(mysqlFold)) actual = resolveSharedMysqlKey(actual);
					changes.add(new com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt(actual, updated));
					changed.add(entry.getKey());
				}
				try {
				for (UserDataChange change : changes) {
					publishChangeInternal(change);
					legacyChangeVersions.put(change, snapshotVersion);
					cachedChanges.add(change);
					if (sqlite) {
						String identity = sqliteDirectKey(change.getKey());
						for (String alias : cache.keySet()) if (alias != null && !alias.equals(change.getKey()) && sqliteDirectKey(alias).equals(identity)) {
							cache.put(alias, change.toUserDataValue()); changedAt.put(alias, snapshotVersion);
						}
					}
				}
				} catch (RuntimeException | Error failure) { throw new LegacyIntegerMutationUncertainException(failure); }
				if (!changes.isEmpty() && !scheduled) {
					try { scheduleChanges(); }
					catch (RuntimeException | Error failure) {
						try { if (manager != null) manager.recordSharedStorageFailure(failure); }
						catch (RuntimeException | Error ignored) { }
					}
				}
				result = new IntegerMutationResult(!changed.isEmpty(), java.util.Map.copyOf(previous));
			}
		} finally { legacyMutationOrder.readLock().unlock(); }
		if (!changed.isEmpty() && notification != null) notification.accept(java.util.Set.copyOf(changed));
		return result;
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
			if (sharedFlushGate == null) legacyChangeVersions.put(change, snapshotVersion);
			cachedChanges.add(change);
			if (!scheduled) scheduleChanges();
		}
	}

	private void publishChangeInternal(UserDataChange change) {
		if (change == null || cache == null) return;
		captureLegacyStorageType();
		LegacyPublication publication = LEGACY_DIRECT_PUBLICATION.get();
		if (publication != null && publication.cache() == this && change.getKey() != null) {
			String identity = legacyStorageType == com.bencodez.advancedcore.api.user.UserStorage.SQLITE
					? sqliteDirectKey(change.getKey()) : legacyMysqlFoldsColumns()
							? change.getKey().toLowerCase(java.util.Locale.ROOT) : change.getKey();
			long sequence = publication.manager().legacyDirectSequence(publication.admission());
			if (legacyDirectPublicationSequences.getOrDefault(identity, Long.MIN_VALUE) > sequence) return;
			legacyDirectPublicationSequences.put(identity, sequence);
		}
		String mysqlAlias = null;
		if (change.getKey() != null && sharedFlushGate == null && legacyMysqlFoldsColumns()) {
			mysqlAlias = resolveSharedMysqlKey(change.getKey());
			if (mysqlAlias == null) throw new IllegalStateException("MySQL column identity is ambiguous");
		}
		if (publication != null && publication.cache() == this && change.getKey() != null)
			publication.manager().rememberLegacyDirectCachePublication(publication.admission(), this, captureDirectCachePublication(change.getKey(), mysqlAlias));
		DataValue value = change.toUserDataValue();
		cache.put(change.getKey(), value);
		long version = ++snapshotVersion;
		changedAt.put(change.getKey(), version);
		if (publication != null && publication.cache() == this) publication.manager().recordLegacyDirectCachePublication(publication.admission(), this, version);
		if (mysqlAlias != null) for (String alias : cache.keySet()) if (alias != null && !alias.equals(change.getKey()) && alias.equalsIgnoreCase(change.getKey())) {
			cache.put(alias, value); changedAt.put(alias, version);
		}
		if (change.getKey() != null) {
			String sqliteKey = sqliteDirectKey(change.getKey());
			if (sharedFlushGate == null && legacyStorageType == com.bencodez.advancedcore.api.user.UserStorage.SQLITE) {
				for (String alias : cache.keySet()) if (alias != null && !alias.equals(change.getKey()) && sqliteDirectKey(alias).equals(sqliteKey)) {
					cache.put(alias, value); changedAt.put(alias, version);
				}
			}
			sqliteChangedAt.put(sqliteKey, version);
			sqliteChangedValues.put(sqliteKey, value);
			sqliteCacheKeys.put(sqliteKey, change.getKey());
		}
	}
	private boolean legacyMysqlFoldsColumns() {
		return legacyStorageType == com.bencodez.advancedcore.api.user.UserStorage.MYSQL
				&& manager != null && manager.getPlugin() != null && manager.getPlugin().getMysql() != null
				&& Boolean.TRUE.equals(manager.getPlugin().getMysql().caseInsensitiveColumnNames());
	}

	public UserDataCache cache() {
		cacheInternal(true);
		return this;
	}

	UserDataCache cacheInternal(boolean notify) {
		refreshInternal(notify);
		return this;
	}

	ArrayList<String> refreshInternal(boolean notify) { return refreshInternal(notify, false); }

	ArrayList<String> refreshInternal(boolean notify, boolean completeSharedRow) {
		return refreshInternal(notify, completeSharedRow, null);
	}

	ArrayList<String> refreshInternal(boolean notify, boolean completeSharedRow, UserDataManager.LegacyRead legacyRead) {
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
		com.bencodez.advancedcore.api.user.UserData userData = user.getUserData();
		boolean canonicalRowRead = completeSharedRow && usesCanonicalValuesReader(userData);
		HashMap<String, DataValue> data = canonicalRowRead ? userData.getValuesIfComplete() : userData.getValues();
		boolean completeRowRead = canonicalRowRead && data != null && manager.hasAuthoritativeSharedRowReads();
		if (data == null) data = new HashMap<>();
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
		final boolean complete = completeRowRead;
		java.util.function.Supplier<HashMap<String, DataValue>> publication = () -> {
			synchronized (this) {
			// A concurrent cache eviction is an expected legacy lifecycle outcome.
			// It must not turn a completed storage read into a failed cache request.
				if (uuid == null || cache == null || removing) return null;
				return updateSharedSnapshot(refreshed, expectedVersion, currentUuid, refreshedStoredDataPresent, complete);
			}
		};
		HashMap<String, DataValue> published = legacyRead == null ? publication.get() : manager.publishLegacyRead(legacyRead, publication);
		if (published == null) return new ArrayList<>();
		for (Entry<String, DataValue> entry : published.entrySet()) {
			DataValue prior = before.get(entry.getKey());
			if (prior != null && entry.getValue() != null && !prior.toString().equals(entry.getValue().toString())) changedKeys.add(entry.getKey());
		}
		if (refreshNotification != null && !changedKeys.isEmpty()) deliverNotification(refreshNotification);
		if (!keys.isEmpty()) manager.getPlugin().devDebug("Caching additional keys: " + ArrayUtils.makeStringList(keys));
		return changedKeys;
	}

	/** Preserve custom getValues overrides without claiming that their maps are complete rows. */
	private static boolean usesCanonicalValuesReader(com.bencodez.advancedcore.api.user.UserData data) {
		if (data == null) return false;
		try { return data.getClass().getMethod("getValues").getDeclaringClass()
				== com.bencodez.advancedcore.api.user.UserData.class
				&& data.getClass().getMethod("getValues", com.bencodez.advancedcore.api.user.UserStorage.class)
						.getDeclaringClass() == com.bencodez.advancedcore.api.user.UserData.class
				&& data.getClass().getMethod("convert", java.util.List.class).getDeclaringClass()
						== com.bencodez.advancedcore.api.user.UserData.class
				&& data.getClass().getMethod("getSQLiteRow").getDeclaringClass() == com.bencodez.advancedcore.api.user.UserData.class
				&& data.getClass().getMethod("getMySqlRow").getDeclaringClass() == com.bencodez.advancedcore.api.user.UserData.class; }
		catch (ReflectiveOperationException | SecurityException unavailable) { return false; }
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
		beginLegacyCheckpoint();
		legacyMutationOrder.writeLock().lock();
		try {
			{
				synchronized (this) {
					if (!legacyChangesAfterCheckpoint.isEmpty() && (cache == null || cachedChanges == null))
						throw new IllegalStateException("Legacy cache retired with staged changes");
					UserDataChange staged;
					while ((staged = legacyChangesAfterCheckpoint.poll()) != null) cachedChanges.add(staged);
				}
			}
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
		} finally { finishLegacyCheckpointAndUnlock(); }
	}

	/** Caller holds either this monitor or the exclusive per-user admission. */
	private void drainChangesStagedBeforeExclusiveAdmission(ArrayList<Runnable> notifications) {
		synchronized (this) {
			UserDataChange deferred;
			while ((deferred = changesAfterExclusiveFlush.poll()) != null) {
				// A checkpoint action may replace the visible cache snapshot. Re-publish
				// mutations ordered after that checkpoint as they cross back into the
				// normal queue so read-after-write visibility survives the replacement.
				boolean unavailable = !storageSnapshotPublished && cache != null && !cache.containsKey(deferred.getKey());
				publishChangeInternal(deferred);
				if (unavailable && cache != null) cache.remove(deferred.getKey());
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
		synchronized (this) { gate = sharedFlushGate; }
		if (gate == null) {
			if (legacyMutationOrder.isWriteLockedByCurrentThread())
				throw new IllegalStateException("Cannot clear a legacy cache inside its active checkpoint");
			beginLegacyCheckpoint();
			legacyMutationOrder.writeLock().lock();
			try {
				synchronized (this) {
					gate = sharedFlushGate;
					if (gate == null) {
						if (inFlightBatches != 0) throw new IllegalStateException("Legacy cache has an in-flight batch");
						if (!legacyChangesAfterCheckpoint.isEmpty() && (cache == null || cachedChanges == null))
							throw new IllegalStateException("Legacy cache retired with staged changes");
						UserDataChange staged;
						while ((staged = legacyChangesAfterCheckpoint.poll()) != null) cachedChanges.add(staged);
					}
				}
				if (gate == null) {
					if (hasChangesToProcess()) processChanges();
					synchronized (this) {
						if (inFlightBatches != 0 || (cachedChanges != null && !cachedChanges.isEmpty()))
							throw new IllegalStateException("Legacy cache still has unflushed work");
						if (cache != null) { cache.clear(); storageSnapshotPublished = false; completeStorageSnapshot = false; recordSnapshotReplacement(); }
					}
					return;
				}
			} finally { finishLegacyCheckpointAndUnlock(); }
		}
		java.util.concurrent.atomic.AtomicReference<Runnable> notification = new java.util.concurrent.atomic.AtomicReference<>();
		gate.accept(() -> {
			notification.set(processChangesInternal(true));
			synchronized (this) { if (cache != null) { cache.clear(); storageSnapshotPublished = false; completeStorageSnapshot = false; recordSnapshotReplacement(); } }
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
				if (sharedFlushGate == null && (legacyCheckpointsPending != 0 || !legacyChangesAfterCheckpoint.isEmpty()))
					throw new IllegalStateException("Cannot retire a legacy cache with an active checkpoint");
				if (cachedChanges != null && !cachedChanges.isEmpty()) continue;
				recordSnapshotReplacement(); legacyChangeVersions.clear(); cache = null; cachedChanges = null; uuid = null; scheduled = false; return;
			}
		}
	}

	public AdvancedCoreUser getUser() { return manager.getPlugin().getUserManager().getUser(uuid, false); }
	public synchronized boolean hasCache() { return cache != null && !cache.isEmpty(); }
	public synchronized boolean hasStoredData() { return storedDataPresent; }
	public synchronized boolean hasPublishedStorageSnapshot() { return storageSnapshotPublished; }
	public synchronized boolean hasEverPublishedStorageSnapshot() { return hasEverPublishedStorageSnapshot; }
	/**
	 * Retire only externally changed values without flushing or deleting unrelated
	 * pending writes. Readers must not mistake these missing values for defaults.
	 * Fence in-flight storage reads so an older load cannot republish stale data.
	 * The caller must arrange an authoritative worker-side refresh afterwards.
	 */
	public synchronized void invalidateStorageSnapshot(String... keys) {
		if (cache == null || keys == null || keys.length == 0) return;
		boolean invalidated = false;
		for (String key : keys) {
			if (key == null) continue;
			cache.remove(key);
			invalidated = true;
		}
		if (!invalidated) return;
		storageSnapshotPublished = false;
		completeStorageSnapshot = false;
		recordSnapshotReplacement();
	}

	/** Seed an absent integer only after a complete, successful storage-row read. */
	public synchronized boolean seedMissingIntegerFromCompleteSnapshot(String key) {
		Objects.requireNonNull(key, "key");
		if (key.isEmpty() || !completeStorageSnapshot || !storageSnapshotPublished || removing
				|| uuid == null || cache == null || cachedChanges == null || sharedFlushGate == null) return false;
		boolean sqlite = sharedStorageType == com.bencodez.advancedcore.api.user.UserStorage.SQLITE;
		if (sharedStorageType == com.bencodez.advancedcore.api.user.UserStorage.MYSQL && hasAmbiguousMysqlKey(key)) return false;
		String actual = sqlite ? resolveSharedSqliteKey(key) : key;
		if (actual == null) return false;
		if (!cache.containsKey(actual)) {
			if (sharedStorageType == com.bencodez.advancedcore.api.user.UserStorage.MYSQL) {
				// An arbitrary SQL provider may not share the native schema's folding
				// rules. Refuse to seed a zero when another spelling could name the
				// same stored column; do not guess or merge custom-provider keys.
				if (!inFlightValues.isEmpty() || !cachedChanges.isEmpty()) return false;
			}
			if (sqlite && (!inFlightValues.isEmpty() || !cachedChanges.isEmpty())) return false;
			DataValue inFlight = inFlightValues.get(actual);
			cache.put(actual, inFlight != null ? inFlight : new com.bencodez.simpleapi.sql.data.DataValueInt(0));
			if (sqlite) sqliteCacheKeys.put(sqliteDirectKey(actual), actual);
		}
		return cache.get(actual) != null;
	}

	private boolean hasAmbiguousMysqlKey(String key) {
		if (cache.size() > 2048 || inFlightValues.size() > 2048) return true;
		for (String candidate : cache.keySet()) if (candidate != null && !candidate.equals(key) && candidate.equalsIgnoreCase(key)) return true;
		for (String candidate : inFlightValues.keySet()) if (candidate != null && !candidate.equals(key) && candidate.equalsIgnoreCase(key)) return true;
		return false;
	}
	private String resolveSharedMysqlKey(String key) {
		if (cache.size() > 2048 || inFlightValues.size() > 2048) return null;
		String found = null;
		for (String candidate : cache.keySet()) if (candidate != null && candidate.equalsIgnoreCase(key)) {
			if (found != null && !found.equals(candidate) && !samePublishedMysqlAlias(found, candidate)) return null;
			found = candidate;
		}
		for (String candidate : inFlightValues.keySet()) if (candidate != null && candidate.equalsIgnoreCase(key)) {
			if (found != null && !found.equals(candidate) && !samePublishedMysqlAlias(found, candidate)) return null;
			found = candidate;
		}
		return found == null ? key : found;
	}
	private boolean samePublishedMysqlAlias(String first, String second) {
		if (!cache.containsKey(first) || !cache.containsKey(second) || cache.get(first) != cache.get(second)) return false;
		Long firstVersion = changedAt.get(first), secondVersion = changedAt.get(second);
		if (firstVersion != null && firstVersion.equals(secondVersion)) return true;
		if (firstVersion == null && secondVersion != null) return persistedAt.getOrDefault(first, 0L) > secondVersion;
		if (secondVersion == null && firstVersion != null) return persistedAt.getOrDefault(second, 0L) > firstVersion;
		return false;
	}

	/** Resolve SQLite's ASCII identifier folding without guessing among aliases. */
	private String resolveSharedSqliteKey(String key) {
		if (cache.size() > 2048 || inFlightValues.size() > 2048) return null;
		String identity = sqliteDirectKey(key), found = null;
		for (String candidate : cache.keySet()) if (candidate != null && sqliteDirectKey(candidate).equals(identity)) {
			if (found != null && !found.equals(candidate)) return null;
			found = candidate;
		}
		for (String candidate : inFlightValues.keySet()) if (candidate != null && sqliteDirectKey(candidate).equals(identity)) {
			if (found != null && !found.equals(candidate)) return null;
			found = candidate;
		}
		return found == null ? key : found;
	}

	/** Observe publication and values under the same cache monitor. */
	public synchronized HashMap<String, DataValue> snapshotIfPublished() {
		return storageSnapshotPublished ? (cache == null ? new HashMap<>() : new HashMap<>(cache)) : null;
	}
	public synchronized HashMap<String, DataValue> snapshot() {
		return cache == null ? new HashMap<>() : new HashMap<>(cache);
	}
	public synchronized boolean hasChangesToProcess() { return cachedChanges != null && !cachedChanges.isEmpty(); }
	public synchronized boolean isCached(String key) { return cache != null && cache.containsKey(key); }
	/** Return one cache value while holding the same monitor used by retirement/replacement. */
	public synchronized DataValue getIfPresent(String key) { return cache == null ? null : cache.get(key); }
	/** Return a stable entry when the key is present, including a present null value. */
	public synchronized java.util.Map.Entry<String, DataValue> getEntryIfPresent(String key) {
		if (cache == null || !cache.containsKey(key)) return null;
		return new java.util.AbstractMap.SimpleImmutableEntry<>(key, cache.get(key));
	}

	public synchronized void ensureNoLegacyBatchForSharedBinding() {
		if (sharedStorageWriter == null && inFlightBatches != 0) throw new IllegalStateException("Cannot attach shared storage during an active legacy batch");
	}

	/** Retained for binary compatibility with integrations that provide one admission gate. */
	public void configureSharedStorage(Consumer<HashMap<String, DataValue>> writer, Consumer<Runnable> gate) {
		configureSharedStorage(writer, gate, gate);
	}

	public synchronized void configureSharedStorage(Consumer<HashMap<String, DataValue>> writer, Consumer<Runnable> gate,
			Consumer<Runnable> exclusiveGate) {
		configureSharedStorage(writer, gate, exclusiveGate, null);
	}
	public synchronized void configureSharedStorage(Consumer<HashMap<String, DataValue>> writer, Consumer<Runnable> gate,
			Consumer<Runnable> exclusiveGate, com.bencodez.advancedcore.api.user.UserStorage storageType) {
		if (sharedFlushGate != null && sharedFlushGate != gate) throw new IllegalStateException("Shared user cache already belongs to another runtime");
		if (sharedExclusiveFlushGate != null && sharedExclusiveFlushGate != exclusiveGate) throw new IllegalStateException("Shared user cache already belongs to another runtime");
		if (sharedStorageType != null && storageType != null && sharedStorageType != storageType) throw new IllegalStateException("Shared user cache belongs to another storage generation");
		if (sharedFlushGate == null && gate != null && inFlightBatches != 0) throw new IllegalStateException("Cannot attach shared storage during an active legacy batch");
		setSharedStorageWriter(writer);
		if (storageType != null) sharedStorageType = storageType;
		sharedFlushGate = gate;
		sharedExclusiveFlushGate = exclusiveGate;
	}

	public synchronized void setSharedStorageWriter(Consumer<HashMap<String, DataValue>> writer) {
		if (uuid == null || cachedChanges == null) throw new IllegalStateException("Shared user cache is retired");
		sharedStorageWriter = java.util.Objects.requireNonNull(writer, "writer");
	}

	public synchronized void retireAfterSharedFlush() {
		if (inFlightBatches != 0 || (cachedChanges != null && !cachedChanges.isEmpty()) || legacyCheckpointsPending != 0 || !legacyChangesAfterCheckpoint.isEmpty()) throw new IllegalStateException("Shared user cache has unflushed work");
		recordSnapshotReplacement(); legacyChangeVersions.clear(); cache = null; cachedChanges = null; uuid = null; scheduled = false;
	}

	/** Prevent reentrant change listeners from resurrecting a cache being removed. */
	public synchronized void beginRemoval() { removing = true; }

	/** Reopen a cache when a manager-wide removal could not flush this cache. */
	public synchronized void cancelRemoval() {
		if (cache != null && cachedChanges != null) removing = false;
	}

	public void processChanges() {
		initializeSharedStorage();
		Runnable notification;
		try { notification = processChangesInternal(false); }
		catch (StaleLegacyBatchException stale) { notification = processChangesInternal(false); }
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
		UUID identity;
		synchronized (this) { identity = uuid; }
		UserDataManager.LegacyRead read = manager != null && identity != null && !manager.hasSharedSqlBackend()
				? manager.beginLegacyRead(identity) : null;
		try { return processChangesInternal(admitted, read); }
		finally { if (read != null) manager.endLegacyRead(read); }
	}

	private Runnable processChangesInternal(boolean admitted, UserDataManager.LegacyRead legacyRead) {
		UUID currentUuid = null;
		Consumer<HashMap<String, DataValue>> writer = null;
		Consumer<Runnable> gate;
		ArrayList<UserDataChange> changes = new ArrayList<>();
		HashMap<String, Long> persistedMutationVersions = new HashMap<>();
		HashMap<String, Long> physicalCandidateVersions = new HashMap<>();
		java.util.Set<UserDataChange> legacyBatchChanges = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
		boolean legacyAdmission = false;
		synchronized (this) {
			gate = admitted ? null : sharedFlushGate;
			if (gate == null) {
				writer = sharedStorageWriter;
				if (sharedBatchThread == Thread.currentThread()) throw new IllegalStateException("Cannot reenter an active cache flush");
				while (inFlightBatches > 0) {
					try { wait(); }
					catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException("Interrupted while draining user changes", interrupted); }
				}
				currentUuid = uuid;
				if (currentUuid == null || cachedChanges == null || cachedChanges.isEmpty()) return null;
				if (writer == null && legacySqliteRowMoveVersion != 0 && cachedChanges.stream().anyMatch(pending -> {
					Long version = legacyChangeVersions.get(pending);
					return version == null || version <= legacySqliteRowMoveVersion;
				})) throw new com.bencodez.advancedcore.api.user.userstorage.sql.UserTable.StaleRowWriteException("Queued SQLite mutation targets a moved row");
				if (writer != null && !legacyChangeVersions.isEmpty()
						&& (!legacyExactBulkOverrides.isEmpty() || !legacySqliteBulkOverrides.isEmpty())
						&& (legacyStorageType == null || sharedStorageType == null || legacyStorageType != sharedStorageType))
					throw new IllegalStateException("Cannot route pending legacy writes to a different or unknown shared storage");
				if (writer == null && manager != null && manager.getPlugin() != null
						&& (!matchesLegacyStorage(manager.getPlugin().getStorageType())
								|| !matchesLegacyOwner(manager.getPlugin().getNativeUserStorageOwner())))
					throw new IllegalStateException("Cannot flush legacy cache to a different configured storage");
				if (writer == null && manager != null) {
					manager.beginLegacyCacheBatch();
					legacyAdmission = true;
				}
				UserDataChange change;
				while ((change = cachedChanges.poll()) != null) changes.add(change);
				for (UserDataChange queuedChange : changes) {
					Long version = legacyChangeVersions.get(queuedChange);
					if (version != null) legacyBatchChanges.add(queuedChange);
					if (version == null) version = changedAt.get(queuedChange.getKey());
					if (version != null) persistedMutationVersions.merge(queuedChange.getKey(), version, Math::max);
				}
				inFlightValues.clear();
				try {
					for (UserDataChange changeEntry : changes) {
						String key = changeEntry.getKey();
						DataValue value = changeEntry.toUserDataValue();
						boolean legacyChange = writer == null || legacyBatchChanges.contains(changeEntry);
						boolean stagedKey = key != null && legacyChangesAfterCheckpoint.stream().anyMatch(staged -> staged.getKey() != null
								&& (key.equals(staged.getKey()) || (legacyStorageType == com.bencodez.advancedcore.api.user.UserStorage.SQLITE && sqliteDirectKey(key).equals(sqliteDirectKey(staged.getKey())))));
						// A later direct legacy SET may already have superseded this queued
						// value. Keep staged post-checkpoint changes behind their checkpoint.
						if (legacyChange && changedAt.containsKey(key) && cache != null && cache.get(key) != null
								&& !stagedKey)
							value = cache.get(key);
						if (legacyChange && !stagedKey && key != null && legacyStorageType == com.bencodez.advancedcore.api.user.UserStorage.SQLITE) {
							String identity = sqliteDirectKey(key);
							if (sqliteChangedAt.getOrDefault(identity, 0L) > changedAt.getOrDefault(key, 0L) && sqliteChangedValues.containsKey(identity))
								value = sqliteChangedValues.get(identity);
						}
						if (legacyChange) {
							Long queuedVersion = legacyChangeVersions.get(changeEntry);
							value = resolveLegacyBulkValue(key, value, queuedVersion);
							physicalCandidateVersions.merge(key, effectiveLegacyQueuedVersion(key, queuedVersion), Math::max);
						}
						inFlightValues.put(key, value);
					}
				} catch (RuntimeException | Error preparationFailure) {
					inFlightValues.clear();
					requeueChanges(changes);
					if (legacyAdmission) manager.endLegacyCacheBatch();
					throw preparationFailure;
				}
				inFlightBatches++;
				sharedBatchThread = Thread.currentThread();
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
			HashMap<String, String> physicalKeys = new HashMap<>();
			HashMap<String, Long> physicalVersions = new HashMap<>();
			for (UserDataChange change : changes) {
				String key = change.getKey();
				boolean legacyChange = writer == null || legacyBatchChanges.contains(change);
				String physical = null;
				if (legacyChange && key != null) {
					if (legacyStorageType == com.bencodez.advancedcore.api.user.UserStorage.SQLITE) physical = sqliteDirectKey(key);
					else if (legacyMysqlFoldsColumns()) physical = key.toLowerCase(java.util.Locale.ROOT);
				}
				if (physical == null) values.put(key, legacyChange ? inFlightValues.get(key) : change.toUserDataValue());
				else {
					long version = physicalCandidateVersions.getOrDefault(key, 0L);
					if (version >= physicalVersions.getOrDefault(physical, Long.MIN_VALUE)) {
						String previous = physicalKeys.put(physical, key);
						if (previous != null && !previous.equals(key)) values.remove(previous);
						values.put(key, inFlightValues.get(key)); physicalVersions.put(physical, version);
					}
				}
				keys.add(change.getKey());
			}
			if (!values.isEmpty()) {
				if (writer == null) {
					if (!matchesLegacyStorage(manager.getPlugin().getStorageType())
							|| !matchesLegacyOwner(manager.getPlugin().getNativeUserStorageOwner()))
						throw new IllegalStateException("Cannot flush legacy cache to a different configured storage");
					LegacyBatchContext previous = LEGACY_BATCH_PERSISTENCE.get();
					if (legacyStorageType == com.bencodez.advancedcore.api.user.UserStorage.SQLITE) {
						com.bencodez.advancedcore.api.user.userstorage.sql.UserTable table = manager.getPlugin().getSQLiteUserTable();
						if (table != null) manager.beginConfirmedBatchHistory(legacyRead, table.legacyDirectProviderIdentity(), 0, table);
					} else if (legacyStorageType == com.bencodez.advancedcore.api.user.UserStorage.MYSQL) {
						com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL table = manager.getPlugin().getMysql();
						if (table != null) {
							Boolean fold = table.caseInsensitiveColumnNames();
							if (fold != null) manager.beginConfirmedBatchHistory(legacyRead, table, fold ? 2 : 1, table);
						}
					}
					LegacyBatchContext context = new LegacyBatchContext(manager, currentUuid, values, legacyRead, legacyStorageType, new java.util.concurrent.atomic.AtomicReference<>());
					LEGACY_BATCH_PERSISTENCE.set(context);
					try {
						user.getUserData().setValues(values);
						if (context.delegatedFailure().get() != null) throw new IllegalStateException("Delegated legacy batch storage failed", context.delegatedFailure().get());
					}
					finally { if (previous == null) LEGACY_BATCH_PERSISTENCE.remove(); else LEGACY_BATCH_PERSISTENCE.set(previous); }
				} else writer.accept(values);
			}
			persisted = true;
			synchronized (this) {
				for (UserDataChange change : changes) if (writer == null || legacyBatchChanges.contains(change)) legacyChangeVersions.remove(change);
				if (!values.isEmpty()) storedDataPresent = true;
				if (cache != null) {
					for (UserDataChange persistedChange : changes) {
						if (writer == null && manager.wasConfirmedNestedKeyFiltered(legacyRead, persistedChange.getKey(), legacyStorageType)) continue;
						Long persistedVersion = persistedMutationVersions.get(persistedChange.getKey());
						Long queuedVersion = changedAt.get(persistedChange.getKey());
						// A setter may have queued a newer value while this batch was in
						// storage. Keep that visible value until its own batch succeeds.
						// A public updateCache() replacement, however, is a storage snapshot
						// rather than another mutation. It clears changedAt(), so reconcile
						// that stale snapshot with this completed write instead of leaving a
						// value visible which will never be persisted.
						if ((storageSnapshotPublished || cache.containsKey(persistedChange.getKey()))
								&& (queuedVersion == null || Objects.equals(persistedVersion, queuedVersion))) {
							String key = persistedChange.getKey();
							String physical = null;
							if ((writer == null || legacyBatchChanges.contains(persistedChange)) && key != null) {
								if (legacyStorageType == com.bencodez.advancedcore.api.user.UserStorage.SQLITE) physical = sqliteDirectKey(key);
								else if (legacyMysqlFoldsColumns()) physical = key.toLowerCase(java.util.Locale.ROOT);
							}
							String winningKey = physical == null ? key : physicalKeys.getOrDefault(physical, key);
							cache.put(key, values.get(winningKey));
						}
					}
				}
				// The in-memory values now represent the successful write. Mark that
				// replacement so an older in-flight read cannot overwrite them.
				// Fence reads that began before this write completed, even if the
				// key's original mutation predates a later mutation of another key.
				if (!values.isEmpty()) {
					long completedVersion = ++snapshotVersion;
					for (java.util.Map.Entry<java.lang.String,com.bencodez.simpleapi.sql.data.DataValue> entry : values.entrySet()) {
						if (writer == null && manager.wasConfirmedNestedKeyFiltered(legacyRead, entry.getKey(), legacyStorageType)) continue;
						persistedAt.put(entry.getKey(), completedVersion);
						persistedValues.put(entry.getKey(), entry.getValue());
					}
				}
				persistedMutationVersions.forEach((key, version) -> {
					if (version.equals(changedAt.get(key))) changedAt.remove(key);
				});
			}
			changedUser = user;
			changedKeys = ArrayUtils.convert(keys);
		} catch (RuntimeException | Error e) {
			if (!persisted) {
				try {
					requeueChanges(changes, writer == null ? persistedMutationVersions : null);
				}
				catch (RuntimeException | Error recoveryFailure) {
					if (recoveryFailure != e) e.addSuppressed(recoveryFailure);
				}
			}
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
		requeueChanges(changes, null);
	}

	private synchronized void requeueChanges(ArrayList<UserDataChange> changes, HashMap<String, Long> legacyVersions) {
		if (changes == null || changes.isEmpty() || cachedChanges == null) return;
		HashMap<String, DataValue> newerLegacyValues = new HashMap<>();
		if (legacyVersions != null && cache != null) legacyVersions.forEach((key, version) -> {
			Long latest = changedAt.get(key);
			if (latest != null && latest > version && cache.containsKey(key)) newerLegacyValues.put(key, cache.get(key));
		});
		Queue<UserDataChange> restored = new ConcurrentLinkedQueue<>();
		restored.addAll(changes); restored.addAll(cachedChanges); cachedChanges = restored;
		// A direct replacement may have temporarily exposed an older value while
		// this batch was in flight. Once requeued, restore the ordered pending view
		// before another arithmetic operation can observe that stale snapshot.
		if (!inFlightValues.isEmpty()) {
			try { recordSnapshotReplacement(); }
			catch (RuntimeException | Error recoveryFailure) {
				// A malformed change must not leave a partly rebuilt visible snapshot.
				if (cache != null) cache.clear();
				storageSnapshotPublished = false;
				replacementVersion = ++snapshotVersion;
				throw recoveryFailure;
			}
		}
		if (legacyVersions != null && cache != null) legacyVersions.forEach((key, version) -> {
			if (newerLegacyValues.containsKey(key)) cache.put(key, newerLegacyValues.get(key));
			else if (Objects.equals(version, changedAt.get(key)) && inFlightValues.containsKey(key)) cache.put(key, inFlightValues.get(key));
		});
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
		scheduled = true;
		try {
			manager.getPlugin().debug("Schedule changes");
			manager.getTimer().schedule(() -> {
				try { processChanges(); } catch (Exception e) { manager.getPlugin().debug(e); }
				finally { onScheduledFlushComplete(); }
			}, 3, TimeUnit.SECONDS);
		} catch (RejectedExecutionException e) { scheduled = false; manager.getPlugin().debug(e); }
		catch (RuntimeException | Error failure) { scheduled = false; throw failure; }
	}

	private synchronized void onScheduledFlushComplete() {
		scheduled = false;
		if (cachedChanges != null && !cachedChanges.isEmpty()) scheduleChanges();
	}

	public synchronized void updateCache(HashMap<String, DataValue> tempCache) {
		cache = tempCache == null ? new HashMap<>() : new HashMap<>(tempCache);
		completeStorageSnapshot = false;
		storageSnapshotPublished = true; hasEverPublishedStorageSnapshot = true;
		hasPublishedStorageSnapshot = true;
		recordSnapshotReplacement();
	}

	public synchronized void updateCachePreservingPending(HashMap<String, DataValue> storageValues) {
		updateCachePreservingPending(storageValues, false);
	}

	/** Called only after reading the complete persisted row. */
	public synchronized void updateCompleteCachePreservingPending(HashMap<String, DataValue> storageValues) {
		updateCachePreservingPending(Objects.requireNonNull(storageValues, "storageValues"), true);
	}

	private void updateCachePreservingPending(HashMap<String, DataValue> storageValues, boolean complete) {
		HashMap<String, DataValue> refreshed = storageValues == null ? new HashMap<>() : new HashMap<>(storageValues);
		storedDataPresent = !refreshed.isEmpty();
		refreshed.putAll(inFlightValues);
		if (cachedChanges != null) for (UserDataChange change : cachedChanges) refreshed.put(change.getKey(), change.toUserDataValue());
		cache = refreshed;
		completeStorageSnapshot = complete;
		storageSnapshotPublished = true; hasEverPublishedStorageSnapshot = true;
		hasPublishedStorageSnapshot = true;
		recordSnapshotReplacement();
	}

	public synchronized long getSharedSnapshotVersion() { return snapshotVersion; }

	public synchronized HashMap<String, DataValue> updateSharedSnapshot(HashMap<String, DataValue> values,
			long expectedVersion) {
		return updateSharedSnapshot(values, expectedVersion, uuid, values != null && !values.isEmpty(), false);
	}

	/** Publish a complete row read, retaining concurrent queued changes. */
	public synchronized HashMap<String, DataValue> updateCompleteSharedSnapshot(HashMap<String, DataValue> values, long expectedVersion) {
		Objects.requireNonNull(values, "values");
		return updateSharedSnapshot(values, expectedVersion, uuid, !values.isEmpty(), true);
	}

	/** Publish a strict legacy row only while its generation is still active. */
	public synchronized boolean tryPublishCompleteLegacySnapshot(HashMap<String, DataValue> values, long expectedVersion) {
		Objects.requireNonNull(values, "values");
		if (sharedFlushGate != null || removing || legacyDirectWrites != 0 || uuid == null || cache == null || cachedChanges == null
				|| expectedVersion < 0 || expectedVersion > snapshotVersion || replacementVersion > expectedVersion) return false;
		updateSharedSnapshot(values, expectedVersion, uuid, !values.isEmpty(), true);
		return true;
	}

	private synchronized HashMap<String, DataValue> updateSharedSnapshot(HashMap<String, DataValue> values,
			long expectedVersion, UUID expectedUuid, boolean snapshotStoredDataPresent, boolean complete) {
		if (cache == null || uuid == null || !uuid.equals(expectedUuid)) throw new IllegalStateException("Shared user cache changed while loading");
		if (expectedVersion < 0 || expectedVersion > snapshotVersion) throw new IllegalArgumentException("Invalid cache snapshot version");
		if (legacyDirectWrites != 0 || replacementVersion > expectedVersion) return new HashMap<>(cache);
		storedDataPresent = snapshotStoredDataPresent;
		HashMap<String, DataValue> merged = values == null ? new HashMap<>() : new HashMap<>(values);
		// A flush may have claimed pending writes after the read started, removing
		// them from the queue before the storage snapshot can observe them.
		boolean sqliteAliases = sharedStorageType == com.bencodez.advancedcore.api.user.UserStorage.SQLITE
				|| (sharedStorageType == null && legacyStorageType == com.bencodez.advancedcore.api.user.UserStorage.SQLITE);
		boolean mysqlAliases = sharedFlushGate == null && legacyMysqlFoldsColumns();
		persistedAt.entrySet().stream().sorted(java.util.Map.Entry.comparingByValue()).forEach(entry -> {
			String key = entry.getKey(); long version = entry.getValue();
			if ((version > expectedVersion || (!hasPublishedStorageSnapshot && version == expectedVersion && cache.containsKey(key)))
					&& persistedValues.containsKey(key)) {
				DataValue persisted = persistedValues.get(key);
				if (key != null && (sqliteAliases || mysqlAliases)) for (String alias : merged.keySet())
					if (alias != null && (sqliteAliases ? sqliteDirectKey(alias).equals(sqliteDirectKey(key)) : alias.equalsIgnoreCase(key))) merged.put(alias, persisted);
				merged.put(key, persisted);
			}
		});
		inFlightValues.forEach((key, value) -> merged.put(key, resolveLegacyBulkValue(key, value)));
		if (cachedChanges != null) for (UserDataChange pending : cachedChanges) merged.put(pending.getKey(), resolveLegacyBulkValue(pending.getKey(), pending.toUserDataValue(), legacyChangeVersions.get(pending)));
		changedAt.forEach((key, version) -> { if (version >= expectedVersion && cache.containsKey(key)) merged.put(key, cache.get(key)); });
		cache = merged;
		completeStorageSnapshot = complete;
		storageSnapshotPublished = true; hasEverPublishedStorageSnapshot = true;
		hasPublishedStorageSnapshot = true;
		recordSnapshotReplacement();
		persistedAt.clear();
		persistedValues.clear();
		return new HashMap<>(cache);
	}

	private void recordSnapshotReplacement() {
		captureLegacyStorageType();
		replacementVersion = ++snapshotVersion;
		// A storage/read snapshot replaces unqueued local observations, but it must
		// not erase the version fence for a mutation that is still waiting in the
		// write queue. Otherwise an older in-flight batch can overwrite that newer
		// visible value when it completes.
		changedAt.keySet().removeIf(key -> (cachedChanges == null || cachedChanges.stream().noneMatch(change -> key.equals(change.getKey())))
				&& changesAfterExclusiveFlush.stream().noneMatch(change -> key.equals(change.getKey()))
				&& legacyChangesAfterCheckpoint.stream().noneMatch(change -> key.equals(change.getKey())));
		if (cache != null) {
			// Queued writes precede changes accepted during an exclusive checkpoint.
			// Keep that ordering when a storage snapshot replaces the visible cache.
			if (cachedChanges != null) for (UserDataChange change : cachedChanges)
				if (storageSnapshotPublished || cache.containsKey(change.getKey()))
					overlayLegacyPendingChange(change);
			for (UserDataChange change : changesAfterExclusiveFlush)
				if (storageSnapshotPublished || cache.containsKey(change.getKey()))
					cache.put(change.getKey(), resolveLegacyBulkValue(change.getKey(), change.toUserDataValue()));
			for (UserDataChange change : legacyChangesAfterCheckpoint)
				if (storageSnapshotPublished || cache.containsKey(change.getKey()))
					overlayLegacyPendingChange(change);
			// A later direct write supersedes earlier staged entries even if the
			// checkpoint replaces the visible snapshot before it completes.
			cache.putAll(legacyCheckpointLatestValues);
			sqliteCacheKeys.clear();
			HashMap<String, Long> sqliteCacheVersions = new HashMap<String, Long>();
			for (String key : cache.keySet()) {
				if (key == null) continue;
				String identity = sqliteDirectKey(key);
				long version = changedAt.getOrDefault(key, 0L);
				Long priorVersion = sqliteCacheVersions.get(identity);
				if (priorVersion == null || version > priorVersion) { sqliteCacheKeys.put(identity, key); sqliteCacheVersions.put(identity, version); }
				else if (version == priorVersion) {
					String previous = sqliteCacheKeys.get(identity);
					if (previous == null || cache.get(previous) != cache.get(key)) sqliteCacheKeys.put(identity, null);
				}
			}
		}
	}
	private void overlayLegacyPendingChange(UserDataChange change) {
		String key = change.getKey();
		DataValue value = resolveLegacyBulkValue(key, change.toUserDataValue(), legacyChangeVersions.get(change));
		cache.put(key, value);
		if (key == null || sharedFlushGate != null) return;
		boolean sqlite = legacyStorageType == com.bencodez.advancedcore.api.user.UserStorage.SQLITE;
		boolean mysqlFold = legacyMysqlFoldsColumns();
		if (!sqlite && !mysqlFold) return;
		if (cache.size() > 2048) throw new IllegalStateException("Column identity is too large to reconcile safely");
		Long version = changedAt.get(key);
		if (version == null) version = legacyChangeVersions.get(change);
		for (String alias : cache.keySet()) if (alias != null && !alias.equals(key)
				&& (sqlite ? sqliteDirectKey(alias).equals(sqliteDirectKey(key)) : alias.equalsIgnoreCase(key))) {
			cache.put(alias, value);
			if (version != null) changedAt.put(alias, version);
		}
	}
}
