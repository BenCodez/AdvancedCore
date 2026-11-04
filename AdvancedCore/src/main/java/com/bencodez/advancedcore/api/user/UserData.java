package com.bencodez.advancedcore.api.user;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.bencodez.advancedcore.api.user.usercache.UserDataCache;
import com.bencodez.advancedcore.api.user.usercache.UserDataManager;
import com.bencodez.advancedcore.api.user.usercache.change.UserDataChange;
import com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeBoolean;
import com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt;
import com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeString;
import com.bencodez.advancedcore.bukkit.user.storage.BukkitSqlUserStorage;
import com.bencodez.advancedcore.core.user.storage.SqlUserDataAccess;
import com.bencodez.simpleapi.array.ArrayUtils;
import com.bencodez.simpleapi.sql.Column;
import com.bencodez.simpleapi.sql.data.DataValue;
import com.bencodez.simpleapi.sql.data.DataValueInt;
import com.bencodez.simpleapi.sql.data.DataValueString;

import lombok.Getter;
import lombok.Setter;

public class UserData {
	/** Subclasses must opt in before replay transactions bypass user-data overrides. */
	public boolean supportsAtomicReplayTransactions() { return getClass() == UserData.class; }
	@Getter
	@Setter
	private HashMap<String, DataValue> tempCache;

	private AdvancedCoreUser user;

	private final BukkitSqlUserStorage sqlStorage = new BukkitSqlUserStorage(
			() -> user.getPlugin(), () -> user.getUUID());
	private final SqlUserDataAccess sqlData = new SqlUserDataAccess(sqlStorage, this::getStorageRow);

	private List<Column> getStorageRow(UserStorage storage) {
		if (storage.equals(UserStorage.MYSQL)) {
			return getMySqlRow();
		}
		return getSQLiteRow();
	}

	public UserData(AdvancedCoreUser user) {
		this.user = user;
	}

	public void clearTempCache() {
		if (tempCache != null) {
			tempCache.clear();
		}
		tempCache = null;
	}

	public HashMap<String, DataValue> convert(List<Column> cols) {
		return SqlUserDataAccess.convert(cols);
	}

	public boolean getBoolean(String key) {
		return Boolean.valueOf(getString(key));
	}

	public boolean getBoolean(String key, UserDataFetchMode mode) {
		return Boolean.valueOf(getString(key, mode));
	}

	/**
	 * @deprecated Use {@link #getBoolean(String, UserDataFetchMode)}
	 */
	@Deprecated
	public boolean getBoolean(String key, boolean useCache, boolean waitForCache) {
		return getBoolean(key, UserDataFetchMode.fromBooleans(useCache, waitForCache));
	}

	public DataValue getDataValue(String key) {
		boolean isInt = user.getPlugin().getUserManager().getDataManager().isInt(key);
		if (isInt) {
			return new DataValueInt(getInt(key));
		}
		return new DataValueString(getString(key));
	}

	public int getInt(String key) {
		return getInt(key, 0, user.getUserDataFetchMode());
	}

	public int getInt(String key, UserDataFetchMode mode) {
		return getInt(key, 0, mode);
	}

	public int getInt(String key, int def) {
		return getInt(effectiveStorageType(), key, def, user.getUserDataFetchMode());
	}

	public int getInt(String key, int def, UserDataFetchMode mode) {
		return getInt(effectiveStorageType(), key, def, mode);
	}

	/**
	 * @deprecated Use {@link #getInt(String, int, UserDataFetchMode)}
	 */
	@Deprecated
	public int getInt(String key, boolean waitForCache) {
		return getInt(key, 0, UserDataFetchMode.fromBooleans(true, waitForCache));
	}

	/**
	 * @deprecated Use {@link #getInt(String, int, UserDataFetchMode)}
	 */
	@Deprecated
	public int getInt(String key, boolean useCache, boolean waitForCache) {
		return getInt(key, 0, UserDataFetchMode.fromBooleans(useCache, waitForCache));
	}

	/**
	 * @deprecated Use {@link #getInt(String, int, UserDataFetchMode)}
	 */
	@Deprecated
	public int getInt(String key, int def, boolean waitForCache) {
		return getInt(effectiveStorageType(), key, def, UserDataFetchMode.fromBooleans(true, waitForCache));
	}

	/**
	 * @deprecated Use {@link #getInt(String, int, UserDataFetchMode)}
	 */
	@Deprecated
	public int getInt(String key, int def, boolean useCache, boolean waitForCache) {
		return getInt(effectiveStorageType(), key, def,
				UserDataFetchMode.fromBooleans(useCache, waitForCache));
	}

	/**
	 * Read an integer using the requested cache and storage policy. When the
	 * policy permits storage lookup, a cold legacy cache can perform synchronous
	 * storage access on the calling thread. Callers that must not block a
	 * platform thread must use a cache-only policy or perform the read on a
	 * worker; preloading alone cannot guarantee a later cache hit.
	 */
	public int getInt(UserStorage storage, String key, int def, UserDataFetchMode mode) {
		if (key == null || key.isEmpty()) {
			return def;
		}
		// The shared cache belongs to exactly one physical store.  Check this
		// before consulting any cache layer so an explicit alternate-store read
		// cannot be answered with a value from the active shared backend.
		ensureRequestedStorageIsNotOwnedByAnotherSharedBackend(storage);

		// 1) Temp cache
		if (mode.allowTempCache() && tempCache != null) {
			DataValue v = tempCache.get(key);
			if (v != null) {
				if (v.isInt()) {
					return v.getInt();
				}
				if (v.isString()) {
					try {
						return Integer.parseInt(v.getString());
					} catch (Exception ignored) {
					}
				}
			} else {
				// If temp cache is enabled but key is absent, keep old behavior (return def)
				// ONLY when temp cache is the only allowed source.
				if (!mode.allowUserCache() && !mode.allowStorageLookup()) {
					return def;
				}
			}
		}

		// 2) UserDataCache
		UserDataCache sharedReadCache = null;
		if (mode.allowUserCache()) {
			UserDataCache cache = cacheForRead(mode);
			sharedReadCache = cache;
			if (cache != null) {
				// preserve previous behavior
				if (mode.allowStorageLookup() && !isExistingLegacyCache(cache)) user.cacheIfNeeded();

				DataValue cv = cache.getIfPresent(key);
				if (cv != null) {
					if (cv.isInt()) {
							return cv.getInt();
						}
						String str = cv.getString();
						if (str != null && !str.equalsIgnoreCase("null")) {
							try {
								return Integer.parseInt(str);
							} catch (Exception ignored) {
							}
						}
					}
			} else {
				if (mode.allowStorageLookup()) user.cache();
			}

			if (!mode.allowStorageLookup()) {
				return def;
			}
		} else {
			if (!mode.allowStorageLookup()) {
				return def;
			}
		}

		// 3) Storage lookup
		if (mustDeferSharedStorageAccess()) {
			DataValue published = requirePublishedSnapshot(mode, sharedReadCache).get(key);
			if (published != null) {
				if (published.isInt()) return published.getInt();
				String value = published.getString();
				if (value != null && !value.equalsIgnoreCase("null")) {
					try { return Integer.parseInt(value); }
					catch (NumberFormatException ignored) { }
				}
			}
			return def;
		}
		return sqlData.getInt(storage, key, def);
	}

	/**
	 * @deprecated Use {@link #getInt(UserStorage, String, int, UserDataFetchMode)}
	 */
	@Deprecated
	public int getInt(UserStorage storage, String key, int def, boolean useCache, boolean waitForCache) {
		return getInt(storage, key, def, UserDataFetchMode.fromBooleans(useCache, waitForCache));
	}

	public ArrayList<String> getKeys() {
		return getKeys(effectiveStorageType());
	}

	public ArrayList<String> getKeys(UserStorage storage) {
		ensureRequestedStorageIsNotOwnedByAnotherSharedBackend(storage);
		rejectPrimaryThreadPersistedRowRead(storage);
		return sqlData.getKeys(storage);
	}

	/**
	 * @deprecated Use {@link #getKeys()} or {@link #getKeys(UserStorage)}
	 */
	@Deprecated
	public ArrayList<String> getKeys(boolean waitForCache) {
		return getKeys(effectiveStorageType());
	}

	/**
	 * @deprecated Use {@link #getKeys(UserStorage)}
	 */
	@Deprecated
	public ArrayList<String> getKeys(UserStorage storage, boolean waitForCache) {
		return getKeys(storage);
	}

	public List<Column> getMySqlRow() {
		return sqlStorage.readRow(UserStorage.MYSQL);
	}

	public List<Column> getSQLiteRow() {
		return sqlStorage.readRow(UserStorage.SQLITE);
	}

	public String getString(String key) {
		return getString(key, user.getUserDataFetchMode());
	}

	public String getString(String key, UserDataFetchMode mode) {
		return getString(effectiveStorageType(), key, mode);
	}

	public String getString(UserStorage storage, String key, UserDataFetchMode mode) {
		if (key == null || key.isEmpty()) {
			return "";
		}
		// See getInt(UserStorage,...): cache contents are only valid for the
		// runtime-owned store.
		ensureRequestedStorageIsNotOwnedByAnotherSharedBackend(storage);

		// 1) Temp cache
		if (mode.allowTempCache() && tempCache != null) {
			DataValue v = tempCache.get(key);
			if (v != null) {
				if (v.isString() || v.isBoolean()) {
					String str = v.getString();
					return (str != null) ? str : "";
				}
			} else {
				if (!mode.allowUserCache() && !mode.allowStorageLookup()) {
					return "";
				}
			}
		}

		// 2) UserDataCache
		UserDataCache sharedReadCache = null;
		if (mode.allowUserCache()) {
			UserDataCache cache = cacheForRead(mode);
			sharedReadCache = cache;
			if (cache != null) {
				java.util.Map.Entry<String, DataValue> cached = cache.getEntryIfPresent(key);
				if (cached != null) {
					DataValue cv = cached.getValue();
					if (cv == null) return "";
					String str = cv.getString();
					return (str != null) ? str : "";
				}
			} else {
				if (mode.allowStorageLookup()) user.cache();
			}

			if (!mode.allowStorageLookup()) {
				return "";
			}
		} else {
			if (!mode.allowStorageLookup()) {
				return "";
			}
		}

		// 3) Storage lookup
		if (mustDeferSharedStorageAccess()) {
			DataValue published = requirePublishedSnapshot(mode, sharedReadCache).get(key);
			return published == null || published.getString() == null ? "" : published.getString();
		}
		return sqlData.getString(storage, key);
	}

	private UserDataCache cacheForRead(UserDataFetchMode mode) {
		if (mode.allowUserCache()) {
			com.bencodez.advancedcore.api.user.UserManager users = user.getPlugin().getUserManager();
			com.bencodez.advancedcore.api.user.usercache.UserDataManager manager = users == null ? null : users.getDataManager();
			if (manager != null && !mode.allowStorageLookup()) {
				if (user.getUUID() == null) return null;
				try {
					java.util.concurrent.ConcurrentHashMap<java.util.UUID, UserDataCache> caches = manager.getUserDataCache();
					return caches == null ? null : caches.get(java.util.UUID.fromString(user.getUUID()));
				} catch (IllegalArgumentException invalidUuid) { return null; }
			}
			if (manager != null && !manager.hasSharedSqlBackend() && user.getUUID() != null) {
				try {
					java.util.UUID uuid = java.util.UUID.fromString(user.getUUID());
					java.util.concurrent.ConcurrentHashMap<java.util.UUID, UserDataCache> caches = manager.getUserDataCache();
					UserDataCache current = caches == null ? null : caches.get(uuid);
					if (current != null && !current.hasPublishedStorageSnapshot()) {
						if (mode.waitForCache() && !current.hasEverPublishedStorageSnapshot()) return user.getCache();
						return manager.getCacheWithoutStorage(uuid);
					}
				} catch (IllegalArgumentException invalidUuid) { /* Preserve the normal user cache contract. */ }
			}
		}
		return user.getCache();
	}

	private boolean isExistingLegacyCache(UserDataCache cache) {
		com.bencodez.advancedcore.api.user.UserManager users = user.getPlugin().getUserManager();
		com.bencodez.advancedcore.api.user.usercache.UserDataManager manager = users == null ? null : users.getDataManager();
		if (manager == null || manager.hasSharedSqlBackend() || user.getUUID() == null) return false;
		try {
			java.util.concurrent.ConcurrentHashMap<java.util.UUID, UserDataCache> caches = manager.getUserDataCache();
			return caches != null && caches.get(java.util.UUID.fromString(user.getUUID())) == cache;
		} catch (IllegalArgumentException invalidUuid) { return false; }
	}

	private HashMap<String, DataValue> requirePublishedSnapshot(UserDataFetchMode mode, UserDataCache cache) {
		HashMap<String, DataValue> published = mode.allowUserCache() && cache != null
				? cache.snapshotIfPublished() : null;
		if (published != null) return published;
		throw new IllegalStateException(
				"Shared user data is still loading; defer this read until cache population completes");
	}

	private boolean mustDeferSharedStorageAccess() {
		UserDataManager dataManager = sharedDataManager();
		return dataManager != null && dataManager.mustDeferSharedStorageAccess();
	}

	/**
	 * @deprecated Use {@link #getString(String, UserDataFetchMode)}
	 */
	@Deprecated
	public String getString(String key, boolean waitForCache) {
		return getString(key, UserDataFetchMode.fromBooleans(true, waitForCache));
	}

	/**
	 * @deprecated Use {@link #getString(String, UserDataFetchMode)}
	 */
	@Deprecated
	public String getString(String key, boolean useCache, boolean waitForCache) {
		return getString(key, UserDataFetchMode.fromBooleans(useCache, waitForCache));
	}

	/**
	 * @deprecated Use {@link #getString(UserStorage, String, UserDataFetchMode)}
	 */
	@Deprecated
	public String getString(UserStorage storage, String key, boolean useCache, boolean waitForCache) {
		return getString(storage, key, UserDataFetchMode.fromBooleans(useCache, waitForCache));
	}

	public ArrayList<String> getStringList(String key) {
		return getStringList(key, user.getUserDataFetchMode());
	}

	public ArrayList<String> getStringList(String key, UserDataFetchMode mode) {
		String str = getString(key, mode);
		if (str == null || str.isEmpty()) {
			return new ArrayList<>();
		}
		return ArrayUtils.convert(str.split("%line%"));
	}

	/**
	 * @deprecated Use {@link #getStringList(String, UserDataFetchMode)}
	 */
	@Deprecated
	public ArrayList<String> getStringList(String key, boolean cache, boolean waitForCache) {
		return getStringList(key, UserDataFetchMode.fromBooleans(cache, waitForCache));
	}

	public String getValue(String key) {
		boolean isInt = user.getPlugin().getUserManager().getDataManager().isInt(key);
		if (isInt) {
			return "" + getInt(key);
		}
		return getString(key);
	}

	public HashMap<String, DataValue> getValues() {
		return getValues(effectiveStorageType());
	}

	public HashMap<String, DataValue> getValues(UserStorage storage) {
		ensureRequestedStorageIsNotOwnedByAnotherSharedBackend(storage);
		rejectPrimaryThreadPersistedRowRead(storage);
		return convert(sqlData.readRow(storage));
	}

	/** A null row is ambiguous and must not authorize defaults for absent keys. */
	public HashMap<String, DataValue> getValuesIfComplete() {
		UserStorage storage = effectiveStorageType();
		ensureRequestedStorageIsNotOwnedByAnotherSharedBackend(storage);
		rejectPrimaryThreadPersistedRowRead(storage);
		java.util.List<com.bencodez.simpleapi.sql.Column> row = sqlData.readRow(storage);
		return row == null ? null : convert(row);
	}

	/**
	 * Whether this reader can provide strict complete legacy rows without a storage
	 * query. A custom reader may opt in by overriding this method and the strict
	 * getter; it must distinguish a successful empty row from a failed read.
	 * This capability does not imply atomic write ordering.
	 */
	public boolean supportsStrictCompleteValues() {
		try {
			Class<?> type = getClass();
			if (type.getMethod("getValues").getDeclaringClass() != UserData.class
					|| type.getMethod("getValues", UserStorage.class).getDeclaringClass() != UserData.class
					|| type.getMethod("getMySqlRow").getDeclaringClass() != UserData.class
					|| type.getMethod("getSQLiteRow").getDeclaringClass() != UserData.class
					|| type.getMethod("convert", java.util.List.class).getDeclaringClass() != UserData.class
					|| type.getMethod("getValuesIfStrictComplete").getDeclaringClass() != UserData.class) return false;
		} catch (NoSuchMethodException | SecurityException unavailable) { return false; }
		return sqlStorage.supportsStrictLegacyRowReads(effectiveStorageType());
	}

	/** Strict legacy row read for atomic operations; custom row readers remain authoritative. */
	public HashMap<String, DataValue> getValuesIfStrictComplete() {
		try {
			Class<?> type = getClass();
			if (type.getMethod("getValues").getDeclaringClass() != UserData.class
					|| type.getMethod("getValues", UserStorage.class).getDeclaringClass() != UserData.class
					|| type.getMethod("getMySqlRow").getDeclaringClass() != UserData.class
					|| type.getMethod("getSQLiteRow").getDeclaringClass() != UserData.class
					|| type.getMethod("convert", java.util.List.class).getDeclaringClass() != UserData.class) return null;
		} catch (NoSuchMethodException unavailable) { return null; }
		UserStorage storage = effectiveStorageType();
		ensureRequestedStorageIsNotOwnedByAnotherSharedBackend(storage);
		rejectPrimaryThreadPersistedRowRead(storage);
		java.util.List<com.bencodez.simpleapi.sql.Column> row = sqlStorage.readRowStrict(storage);
		return row == null ? null : convert(row);
	}

	public boolean hasData() {
		UserStorage storage = effectiveStorageType();
		ensureRequestedStorageIsNotOwnedByAnotherSharedBackend(storage);
		UserDataCache sharedCache = primaryThreadSharedCache(storage);
		return sharedCache == null ? sqlData.hasData(storage) : sharedCache.hasStoredData();
	}

	public void remove() {
		UserStorage storage = effectiveStorageType();
		UserDataManager manager = sharedDataManager();
		if (manager != null && manager.hasSharedRuntime()) {
			// The runtime serializes flush, delete, and cache retirement under its
			// exclusive per-user gate.  Deleting through the adapter and then
			// clearing the cache separately can otherwise let queued work recreate
			// the row after its deletion.
			manager.removeUserData(java.util.UUID.fromString(user.getUUID()), storage);
			return;
		}
		sqlData.remove(storage);
		user.clearCache();
	}

	public void setBoolean(String key, boolean value) {
		setString(key, "" + value);
	}

	public void setBoolean(String key, boolean value, boolean queue) {
		setString(key, "" + value, queue);
	}

	public void setInt(final String key, final int value) {
		setInt(key, value, true);
	}

	public void setInt(final String key, final int value, boolean queue) {
		setInt(effectiveStorageType(), key, value, queue);
	}

	public void setInt(final String key, final int value, boolean queue, boolean async) {
		setInt(effectiveStorageType(), key, value, queue, async);
	}

	public void setInt(UserStorage storage, final String key, final int value, boolean queue) {
		setInt(storage, key, value, queue, false);
	}

	public void setInt(final UserStorage storage, final String key, final int value, boolean queue, boolean async) {
		if (key.equals("")) {
			user.getPlugin().debug("No key: " + key + " to " + value);
			return;
		}
		if (key.contains(" ")) {
			user.getPlugin().getLogger().severe("Keys cannot contain spaces " + key);
		}

		user.getPlugin().extraDebug("PlayerData " + storage.toString() + ": Setting " + key + " to '" + value
				+ "' for '" + user.getPlayerName() + "/" + user.getUUID() + "' Queue: " + queue);

		UserDataChangeInt change = new UserDataChangeInt(key, value);
		if (queueSharedMutation(storage, change, queue, async)) return;
		ensureRequestedStorageIsNotOwnedByAnotherSharedBackend(storage);
		UserDataManager directManager = sharedDataManager();
		UserDataManager.LegacyDirectWrite early = (!queue || async) ? beginDirectAdmission(directManager, storage, change) : null;
		java.util.concurrent.atomic.AtomicBoolean released = new java.util.concurrent.atomic.AtomicBoolean();
		UserDataManager.LegacyDirectWrite captured = early;
		Runnable release = () -> { if (captured != null && released.compareAndSet(false, true)) directManager.endLegacyDirectWrite(captured); };
		boolean[] handedOff = { false };
		try {

		boolean initiallyCached = user.isCached();
		if (initiallyCached) {
			boolean flushImmediately = !queue && user.getPlugin().getUserManager().getDataManager()
					.usesSharedSqlStorage(storage);
			if (queue || storage == effectiveStorageType()) {
				UserDataCache target = queue ? cacheForRead(UserDataFetchMode.CACHE_ONLY) : user.getCache();
				if (target == null) throw new IllegalStateException("User cache was removed before the queued change was admitted");
				if (queue || flushImmediately || target.matchesLegacyStorage(storage)) {
					if (!queue && !flushImmediately) UserDataCache.runWithLegacyDirectPublication(directManager, captured, target, () -> target.addChange(change, false));
					else target.addChange(change, true);
				}
			}
			// An immediate shared flush reports the change from its persistence
			// completion callback. Keep the legacy eager callback for ordinary
			// queued/cache-only changes, but do not report this direct write twice.
			if (!flushImmediately) {
				if (queue) release.run();
				UserDataCache.runWithLegacyDirectCallback(directManager, captured, () -> user.getPlugin().getUserManager().onChange(user, key));
			}
			if (queue || flushImmediately) {
				if (flushImmediately) {
					user.getCache().processChangesImmediately(async);
				}
				return;
			}
		}

		if (async) {
			handedOff[0] = true;
			try { user.getPlugin().getTimer().execute(new Runnable() {

				@Override
				public void run() {
					try {
					writeDirectInt(storage, key, value, captured);
			if (captured != null) directManager.markLegacyDirectWriteSucceeded(captured);

					if (!initiallyCached) {
				release.run();
						user.getPlugin().getUserManager().onChange(user, key);
					}
					} catch (RuntimeException | Error failure) { if (captured != null) directManager.failLegacyDirectWrite(captured, change, failure); throw failure; } finally { release.run(); }
				}
			}); } catch (RuntimeException | Error failure) { if (captured != null) directManager.failLegacyDirectWrite(captured, change, failure); release.run(); throw failure; }
		} else {
			// process change right away
			writeDirectInt(storage, key, value, captured);
			if (captured != null) directManager.markLegacyDirectWriteSucceeded(captured);

			if (!initiallyCached) {
				release.run();
				user.getPlugin().getUserManager().onChange(user, key);
			}
		}
		} catch (RuntimeException | Error failure) { if (captured != null) directManager.failLegacyDirectWrite(captured, change, failure); throw failure; } finally { if (!handedOff[0]) release.run(); }
	}

	private static String directStorageKey(UserStorage storage, String key) {
		return storage == UserStorage.SQLITE ? UserDataCache.sqliteDirectKey(key) : storage.name() + ":" + key;
	}

	private UserDataManager.LegacyDirectWrite beginDirectAdmission(UserDataManager manager, UserStorage storage, UserDataChange change) {
		if (manager == null || manager.hasSharedSqlBackend() || user.getUUID() == null) return null;
		try { String row = user.getUUID(); return manager.beginLegacyDirectWrite(java.util.UUID.fromString(row), java.util.Set.of(directStorageKey(storage, change.getKey())), storage == UserStorage.SQLITE ? row : null); }
		catch (IllegalArgumentException invalidUuid) { return null; }
	}

	private void writeDirectInt(UserStorage storage, String key, int value, UserDataManager.LegacyDirectWrite alreadyAdmitted) {
		writeDirect(() -> sqlData.setInt(storage, key, value), null, alreadyAdmitted, storage, new UserDataChangeInt(key, value));
	}

	private void writeDirect(Runnable operation, java.util.Map<String, DataValue> batchValues, UserDataManager.LegacyDirectWrite alreadyAdmitted, UserStorage storage, UserDataChange change) {
		com.bencodez.advancedcore.api.user.UserManager users = user.getPlugin().getUserManager();
		UserDataManager manager = users == null ? null : users.getDataManager();
		UserDataManager.LegacyDirectWrite admission = null;
		boolean internalBatch = false;
		if (manager != null && !manager.hasSharedSqlBackend() && user.getUUID() != null) {
			try {
				java.lang.String rowIdentity = user.getUUID();
				java.util.UUID uuid = java.util.UUID.fromString(rowIdentity);
				internalBatch = batchValues != null && UserDataCache.isLegacyBatchPersistence(manager, uuid, batchValues);
				if (!internalBatch && alreadyAdmitted == null) {
					java.util.Set<String> keys = new java.util.HashSet<>();
					if (change != null) keys.add(directStorageKey(storage, change.getKey()));
					else if (batchValues != null) for (String name : batchValues.keySet()) {
						if (name == null || name.equals("uuid")) continue;
						DataValue value = batchValues.get(name);
						if (storage == UserStorage.SQLITE && UserDataCache.sqliteDirectKey(name).equals(UserDataCache.sqliteDirectKey("uuid"))
								&& value != null && value.isString() && user.getUUID().equals(value.getString())) continue;
						keys.add(directStorageKey(storage, name));
					}
					admission = manager.beginLegacyDirectWrite(uuid, keys, storage == UserStorage.SQLITE ? rowIdentity : null);
					if (admission != null && storage == UserStorage.SQLITE && batchValues != null) {
						DataValue first = null, last = null; boolean found = false;
						for (java.util.Map.Entry<java.lang.String,com.bencodez.simpleapi.sql.data.DataValue> entry : batchValues.entrySet()) {
							String name = entry.getKey();
							if (name == null || name.equals("uuid") || !UserDataCache.sqliteDirectKey(name).equals(UserDataCache.sqliteDirectKey("uuid"))) continue;
							if (!found) first = entry.getValue();
							last = entry.getValue(); found = true;
						}
						if (found) manager.configureSqliteBulkPrimary(admission,
								first == null || !first.isString() || !user.getUUID().equals(first.getString()),
								last == null || !last.isString() || !user.getUUID().equals(last.getString()));
					}
				}
			}
			catch (IllegalArgumentException invalidUuid) { /* Preserve legacy non-UUID identities. */ }
		}
		UserDataManager.LegacyDirectWrite effectiveAdmission = alreadyAdmitted == null ? admission : alreadyAdmitted;
		if (!internalBatch && batchValues != null && manager != null) manager.invalidateLegacyBulkSnapshot(effectiveAdmission);
		try {
			if (!internalBatch && manager != null && storage == UserStorage.SQLITE) {
				if (change != null && UserDataCache.sqliteDirectKey(change.getKey()).equals(UserDataCache.sqliteDirectKey("uuid"))) manager.guardLegacySqliteDestination(effectiveAdmission, change.toUserDataValue());
				if (batchValues != null) for (java.util.Map.Entry<java.lang.String,com.bencodez.simpleapi.sql.data.DataValue> entry : batchValues.entrySet()) if (entry.getKey() != null && UserDataCache.sqliteDirectKey(entry.getKey()).equals(UserDataCache.sqliteDirectKey("uuid"))) manager.guardLegacySqliteDestination(effectiveAdmission, entry.getValue());
			}
			if (internalBatch) operation.run(); else UserDataCache.runWithLegacyDirectWrite(manager, effectiveAdmission, change, () -> UserDataCache.runOutsideLegacyBatch(operation));
			if (!internalBatch && batchValues != null && manager != null) manager.completeLegacyBulkValues(effectiveAdmission, batchValues, key -> directStorageKey(storage, key));
			if (!internalBatch && change != null && manager != null) manager.completeLegacyBulkValues(effectiveAdmission, java.util.Collections.singletonMap(change.getKey(), change.toUserDataValue()), key -> directStorageKey(storage, key));
			if (admission != null) manager.markLegacyDirectWriteSucceeded(admission);
		}
		catch (RuntimeException | Error failure) { if (admission != null) manager.failLegacyDirectWrite(admission, change, failure); throw failure; }
		finally { if (admission != null) manager.endLegacyDirectWrite(admission); }
	}

	public void setString(final String key, final String value) {
		setString(key, value, true);
	}

	public void setString(final String key, final String value, boolean queue) {
		setString(effectiveStorageType(), key, value, queue);
	}

	public void setString(final String key, final String value, boolean queue, boolean async) {
		setString(effectiveStorageType(), key, value, queue, async);
	}

	public void setString(UserStorage storage, final String key, final String value, boolean queue) {
		setString(storage, key, value, queue, false);
	}

	public void setString(final UserStorage storage, final String key, final String value, boolean queue,
			boolean async) {
		if (key.equals("") && value != null) {
			user.getPlugin().debug("No key/value: " + key + " to " + value);
			return;
		}
		if (key.contains(" ")) {
			user.getPlugin().getLogger().severe("Keys cannot contain spaces " + key);
		}

		user.getPlugin().extraDebug("PlayerData " + storage.toString() + ": Setting " + key + " to '" + value
				+ "' for '" + user.getPlayerName() + "/" + user.getUUID() + "' Queue: " + queue);

		UserDataChangeString change = new UserDataChangeString(key, value);
		if (queueSharedMutation(storage, change, queue, async)) return;
		ensureRequestedStorageIsNotOwnedByAnotherSharedBackend(storage);
		UserDataManager directManager = sharedDataManager();
		UserDataManager.LegacyDirectWrite early = (!queue || async) ? beginDirectAdmission(directManager, storage, change) : null;
		java.util.concurrent.atomic.AtomicBoolean released = new java.util.concurrent.atomic.AtomicBoolean();
		UserDataManager.LegacyDirectWrite captured = early;
		Runnable release = () -> { if (captured != null && released.compareAndSet(false, true)) directManager.endLegacyDirectWrite(captured); };
		boolean[] handedOff = { false };
		try {

		boolean initiallyCached = user.isCached();
		if (initiallyCached) {
			boolean flushImmediately = !queue && user.getPlugin().getUserManager().getDataManager()
					.usesSharedSqlStorage(storage);
			if (queue || storage == effectiveStorageType()) {
				UserDataCache target = queue ? cacheForRead(UserDataFetchMode.CACHE_ONLY) : user.getCache();
				if (target == null) throw new IllegalStateException("User cache was removed before the queued change was admitted");
				if (queue || flushImmediately || target.matchesLegacyStorage(storage)) {
					if (!queue && !flushImmediately) UserDataCache.runWithLegacyDirectPublication(directManager, captured, target, () -> target.addChange(change, false));
					else target.addChange(change, true);
				}
			}
			if (!flushImmediately) {
				if (queue) release.run();
				UserDataCache.runWithLegacyDirectCallback(directManager, captured, () -> user.getPlugin().getUserManager().onChange(user, key));
			}
			if (queue || flushImmediately) {
				if (flushImmediately) {
					user.getCache().processChangesImmediately(async);
				}
				return;
			}
		}

		if (async) {
			handedOff[0] = true;
			try { user.getPlugin().getTimer().execute(new Runnable() {

				@Override
				public void run() {
					try {
					writeDirect(() -> sqlData.setString(storage, key, value), null, captured, storage, change);
			if (captured != null) directManager.markLegacyDirectWriteSucceeded(captured);
					if (!initiallyCached) {
				release.run();
						user.getPlugin().getUserManager().onChange(user, key);
					}
					} catch (RuntimeException | Error failure) { if (captured != null) directManager.failLegacyDirectWrite(captured, change, failure); throw failure; } finally { release.run(); }
				}
			}); } catch (RuntimeException | Error failure) { if (captured != null) directManager.failLegacyDirectWrite(captured, change, failure); release.run(); throw failure; }
		} else {
			writeDirect(() -> sqlData.setString(storage, key, value), null, captured, storage, change);
			if (captured != null) directManager.markLegacyDirectWriteSucceeded(captured);
			if (!initiallyCached) {
				release.run();
				user.getPlugin().getUserManager().onChange(user, key);
			}
		}
		} catch (RuntimeException | Error failure) { if (captured != null) directManager.failLegacyDirectWrite(captured, change, failure); throw failure; } finally { if (!handedOff[0]) release.run(); }

	}

	/**
	 * A primary-thread cache miss is represented by a placeholder whose database
	 * snapshot is already queued on the manager worker.  Mutations must join that
	 * generation instead of falling through to synchronous SQL.  The cache's
	 * version fences retain this value when the delayed read publishes.
	 */
	private boolean queueSharedMutation(UserStorage storage, UserDataChange change, boolean queue, boolean async) {
		UserDataManager manager = sharedDataManager();
		if (manager == null || !manager.usesSharedSqlStorage(storage) || manager.isStorageMaintenanceActive()) {
			return false;
		}
		java.util.UUID uuid = java.util.UUID.fromString(user.getUUID());
		Runnable mutation = () -> manager.withSharedSqlStorage(uuid, storage,
				() -> applySharedMutation(manager, user.getCache(), change, queue, async));
		if (manager.mustDeferSharedStorageAccess()) {
			// Publish read-after-write state synchronously, then defer only persistence.
			// An unbound placeholder or retiring cache defers the entire mutation so it
			// can join the correct lifecycle generation on the storage worker.
			UserDataCache cache = user.getCache();
			Runnable notification = queue
					? () -> user.getPlugin().getUserManager().onChange(user, change.getKey()) : null;
			if (!cache.tryAddChangeBeforeDeferredSharedFlush(change, notification, !queue)) {
				return manager.deferSharedStorageWork(mutation);
			}
			return true;
		}
		mutation.run();
		return true;
	}

	private void applySharedMutation(UserDataManager manager, UserDataCache cache, UserDataChange change,
			boolean queue, boolean async) {
		Runnable notification = queue ? manager.captureSharedUserDataNotification(() ->
				user.getPlugin().getUserManager().onChange(user, change.getKey())) : null;
		if (queue && notification == null) {
			notification = () -> user.getPlugin().getUserManager().onChange(user, change.getKey());
		}
		cache.addChange(change, true);
		if (queue) {
			manager.dispatchSharedUserDataNotification(notification);
		} else cache.processChangesImmediately(async);
	}

	/** Preserve batched setter semantics through the same cache/storage generation. */
	private boolean queueSharedValues(UserStorage storage, HashMap<String, DataValue> values) {
		UserDataManager manager = sharedDataManager();
		if (manager == null || !manager.usesSharedSqlStorage(storage) || manager.isStorageMaintenanceActive()) {
			return false;
		}
		if (values == null) return false;
		if (values.isEmpty()) return true;
		ArrayList<UserDataChange> changes = new ArrayList<>();
		for (java.util.Map.Entry<String, DataValue> entry : values.entrySet()) {
			if (entry.getKey() == null || "uuid".equalsIgnoreCase(entry.getKey()) || entry.getValue() == null) continue;
			changes.add(change(entry.getKey(), entry.getValue()));
		}
		if (changes.isEmpty()) return true;
		java.util.UUID uuid = java.util.UUID.fromString(user.getUUID());
		Runnable mutation = () -> manager.withSharedSqlStorage(uuid, storage, () -> {
			UserDataCache cache = user.getCache();
			for (UserDataChange change : changes) cache.addChange(change, true);
			cache.processChangesImmediately(false);
		});
		if (manager.mustDeferSharedStorageAccess()) {
			UserDataCache cache = user.getCache();
			if (!cache.tryAddChangesBeforeDeferredSharedFlush(changes, true)) {
				return manager.deferSharedStorageWork(mutation);
			}
			return true;
		}
		mutation.run();
		return true;
	}

	private UserDataChange change(String key, DataValue value) {
		if (value.isInt()) return new UserDataChangeInt(key, value.getInt());
		if (value.isBoolean()) return new UserDataChangeBoolean(key, value.getBoolean());
		return new UserDataChangeString(key, value.getString());
	}

	/** Reject a cross-store request before it can alter the shared cache generation. */
	private void ensureRequestedStorageIsNotOwnedByAnotherSharedBackend(UserStorage storage) {
		UserDataManager manager = sharedDataManager();
		if (manager != null && manager.hasSharedSqlBackend() && !manager.usesSharedSqlStorage(storage)
				&& !manager.isStorageMaintenanceActive()) {
			throw new IllegalStateException("Cannot access " + storage
					+ " user storage while the shared runtime owns another store");
		}
	}

	private UserDataManager sharedDataManager() {
		if (user.getPlugin() == null) return null;
		UserManager userManager = user.getPlugin().getUserManager();
		return userManager == null ? null : userManager.getDataManager();
	}

	private UserStorage effectiveStorageType() {
		UserStorage configured = user.getPlugin().getStorageType();
		UserDataManager manager = sharedDataManager();
		return manager == null ? configured : manager.effectiveStorageType(configured);
	}

	private UserDataCache primaryThreadSharedCache(UserStorage storage) {
		UserDataManager manager = sharedDataManager();
		if (manager == null || !manager.usesSharedSqlStorage(storage) || !manager.mustDeferSharedStorageAccess()) {
			return null;
		}
		UserDataCache cache = manager.getPublishedCache(java.util.UUID.fromString(user.getUUID()));
		if (cache == null) {
			throw new IllegalStateException(
					"Shared user data is still loading; defer this read until cache population completes");
		}
		return cache;
	}

	/** Bulk row APIs expose persisted storage, not defaults or pending cache values. */
	private void rejectPrimaryThreadPersistedRowRead(UserStorage storage) {
		UserDataManager manager = sharedDataManager();
		if (manager != null && manager.usesSharedSqlStorage(storage) && manager.mustDeferSharedStorageAccess()) {
			throw new IllegalStateException(
					"Shared persisted user data must be read on a worker; use deferSharedStorageResult");
		}
	}

	public void setStringList(final String key, final ArrayList<String> value) {
		setStringList(key, value, true);
	}

	public void setStringList(final String key, final ArrayList<String> value, boolean queue) {
		String str = "";
		for (int i = 0; i < value.size(); i++) {
			if (i != 0) {
				str += "%line%";
			}
			str += value.get(i);
		}
		setString(key, str, queue);
	}

	public void setValues(HashMap<String, DataValue> values) {
		com.bencodez.advancedcore.api.user.UserManager users = user.getPlugin().getUserManager();
		UserDataManager manager = users == null ? null : users.getDataManager();
		UserStorage captured = null;
		if (manager != null && user.getUUID() != null) {
			try { captured = UserDataCache.legacyBatchStorage(manager, java.util.UUID.fromString(user.getUUID()), values); }
			catch (IllegalArgumentException ignored) { /* Preserve ordinary non-UUID user behavior. */ }
		}
		setValues(captured == null ? effectiveStorageType() : captured, values);
	}

	public void setValues(String key, DataValue value) {
		HashMap<String, DataValue> values = new HashMap<>();
		values.put(key, value);
		setValues(effectiveStorageType(), values);
	}

	public void setValues(UserStorage storage, HashMap<String, DataValue> values) {
		if (queueSharedValues(storage, values)) return;
		ensureRequestedStorageIsNotOwnedByAnotherSharedBackend(storage);
		writeDirect(() -> sqlData.setValues(storage, values), values, null, storage, null);
	}

	public void tempCache() {
		tempCache = getValues();
	}

	public void updateCacheWithTemp() {
		if (user.isCached()) {
			user.getCache().updateCache(tempCache);
		}
	}

	public void updateTempCacheWithColumns(ArrayList<Column> cols) {
		tempCache = convert(cols);
	}
}
