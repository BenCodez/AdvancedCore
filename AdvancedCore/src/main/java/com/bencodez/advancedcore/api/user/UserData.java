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
			UserDataCache cache = user.getCache();
			sharedReadCache = cache;
			if (cache != null) {
				// preserve previous behavior
				user.cacheIfNeeded();

				if (cache.isCached(key)) {
					DataValue cv = cache.getCache().get(key);
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
				}
			} else {
				user.cache();
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
			rejectUnavailableFreshRead(mode, sharedReadCache);
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
		UserDataCache sharedCache = primaryThreadSharedCache(storage);
		if (sharedCache != null) return new ArrayList<>(sharedCache.snapshot().keySet());
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
			UserDataCache cache = user.getCache();
			sharedReadCache = cache;
			if (cache != null) {
				if (cache.isCached(key)) {
					DataValue cv = cache.getCache().get(key);
					if (cv != null) {
						String str = cv.getString();
						return (str != null) ? str : "";
					}
					return "";
				}
			} else {
				user.cache();
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
			rejectUnavailableFreshRead(mode, sharedReadCache);
			return "";
		}
		return sqlData.getString(storage, key);
	}

	private void rejectUnavailableFreshRead(UserDataFetchMode mode, UserDataCache cache) {
		if (mode.allowUserCache() && cache != null && cache.hasPublishedStorageSnapshot()) return;
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
		UserDataCache sharedCache = primaryThreadSharedCache(storage);
		if (sharedCache != null) return sharedCache.snapshot();
		return convert(sqlData.readRow(storage));
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

		if (user.isCached()) {
			boolean flushImmediately = !queue && user.getPlugin().getUserManager().getDataManager()
					.usesSharedSqlStorage(storage);
			user.getCache().addChange(change, queue || flushImmediately);
			// An immediate shared flush reports the change from its persistence
			// completion callback. Keep the legacy eager callback for ordinary
			// queued/cache-only changes, but do not report this direct write twice.
			if (!flushImmediately) user.getPlugin().getUserManager().onChange(user, key);
			if (queue || flushImmediately) {
				if (flushImmediately) {
					user.getCache().processChangesImmediately(async);
				}
				return;
			}
		}

		if (async) {
			user.getPlugin().getTimer().execute(new Runnable() {

				@Override
				public void run() {
					sqlData.setInt(storage, key, value);

					if (!user.isCached()) {
						user.getPlugin().getUserManager().onChange(user, key);
					}
				}
			});
		} else {
			// process change right away
			sqlData.setInt(storage, key, value);

			if (!user.isCached()) {
				user.getPlugin().getUserManager().onChange(user, key);
			}
		}
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

		if (user.isCached()) {
			boolean flushImmediately = !queue && user.getPlugin().getUserManager().getDataManager()
					.usesSharedSqlStorage(storage);
			user.getCache().addChange(change, queue || flushImmediately);
			if (!flushImmediately) user.getPlugin().getUserManager().onChange(user, key);
			if (queue || flushImmediately) {
				if (flushImmediately) {
					user.getCache().processChangesImmediately(async);
				}
				return;
			}
		}

		if (async) {
			user.getPlugin().getTimer().execute(new Runnable() {

				@Override
				public void run() {
					sqlData.setString(storage, key, value);
					if (!user.isCached()) {
						user.getPlugin().getUserManager().onChange(user, key);
					}
				}
			});
		} else {
			sqlData.setString(storage, key, value);
			if (!user.isCached()) {
				user.getPlugin().getUserManager().onChange(user, key);
			}
		}

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
		if (manager.mustDeferSharedStorageAccess()) {
			// Publish read-after-write state synchronously, then defer only persistence.
			// Population was queued by getCache() first and its version fences preserve
			// this pending value when the older storage snapshot arrives.
			UserDataCache cache = user.getCache();
			cache.addChangeBeforeDeferredSharedFlush(change);
			if (queue) manager.dispatchSharedStorageNotification(() ->
					user.getPlugin().getUserManager().onChange(user, change.getKey()));
			else manager.deferSharedStorageWork(() -> cache.processChangesImmediately(false));
			return true;
		}
		manager.withSharedSqlStorage(uuid, storage,
				() -> applySharedMutation(manager, user.getCache(), change, queue, async));
		return true;
	}

	private void applySharedMutation(UserDataManager manager, UserDataCache cache, UserDataChange change,
			boolean queue, boolean async) {
		cache.addChange(change, true);
		if (queue) {
			manager.dispatchSharedStorageNotification(() ->
					user.getPlugin().getUserManager().onChange(user, change.getKey()));
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
			for (UserDataChange change : changes) cache.addChangeBeforeDeferredSharedFlush(change);
			return manager.deferSharedStorageWork(() -> cache.processChangesImmediately(false));
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
		UserDataCache cache = user.getCache();
		if (!cache.hasPublishedStorageSnapshot()) {
			throw new IllegalStateException(
					"Shared user data is still loading; defer this read until cache population completes");
		}
		return cache;
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
		setValues(effectiveStorageType(), values);
	}

	public void setValues(String key, DataValue value) {
		HashMap<String, DataValue> values = new HashMap<>();
		values.put(key, value);
		setValues(effectiveStorageType(), values);
	}

	public void setValues(UserStorage storage, HashMap<String, DataValue> values) {
		if (queueSharedValues(storage, values)) return;
		ensureRequestedStorageIsNotOwnedByAnotherSharedBackend(storage);
		sqlData.setValues(storage, values);
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
