package com.bencodez.advancedcore.api.user;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.bencodez.advancedcore.api.user.usercache.UserDataCache;
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
		return getInt(user.getPlugin().getStorageType(), key, def, user.getUserDataFetchMode());
	}

	public int getInt(String key, int def, UserDataFetchMode mode) {
		return getInt(user.getPlugin().getStorageType(), key, def, mode);
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
		return getInt(user.getPlugin().getStorageType(), key, def, UserDataFetchMode.fromBooleans(true, waitForCache));
	}

	/**
	 * @deprecated Use {@link #getInt(String, int, UserDataFetchMode)}
	 */
	@Deprecated
	public int getInt(String key, int def, boolean useCache, boolean waitForCache) {
		return getInt(user.getPlugin().getStorageType(), key, def,
				UserDataFetchMode.fromBooleans(useCache, waitForCache));
	}

	public int getInt(UserStorage storage, String key, int def, UserDataFetchMode mode) {
		if (key == null || key.isEmpty()) {
			return def;
		}

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
		if (mode.allowUserCache()) {
			UserDataCache cache = user.getCache();
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
		return getKeys(user.getPlugin().getStorageType());
	}

	public ArrayList<String> getKeys(UserStorage storage) {
		return sqlData.getKeys(storage);
	}

	/**
	 * @deprecated Use {@link #getKeys()} or {@link #getKeys(UserStorage)}
	 */
	@Deprecated
	public ArrayList<String> getKeys(boolean waitForCache) {
		return getKeys(user.getPlugin().getStorageType());
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
		return getString(user.getPlugin().getStorageType(), key, mode);
	}

	public String getString(UserStorage storage, String key, UserDataFetchMode mode) {
		if (key == null || key.isEmpty()) {
			return "";
		}

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
		if (mode.allowUserCache()) {
			UserDataCache cache = user.getCache();
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
		return sqlData.getString(storage, key);
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
		return getValues(user.getPlugin().getStorageType());
	}

	public HashMap<String, DataValue> getValues(UserStorage storage) {
		return convert(sqlData.readRow(storage));
	}

	public boolean hasData() {
		return sqlData.hasData(user.getPlugin().getStorageType());
	}

	public void remove() {
		sqlData.remove(user.getPlugin().getStorageType());
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
		setInt(user.getPlugin().getStorageType(), key, value, queue);
	}

	public void setInt(final String key, final int value, boolean queue, boolean async) {
		setInt(user.getPlugin().getStorageType(), key, value, queue, async);
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

		if (user.isCached()) {
			user.getCache().addChange(new UserDataChangeInt(key, value), queue);
			user.getPlugin().getUserManager().onChange(user, key);
			if (queue) {
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
		setString(user.getPlugin().getStorageType(), key, value, queue);
	}

	public void setString(final String key, final String value, boolean queue, boolean async) {
		setString(user.getPlugin().getStorageType(), key, value, queue, async);
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

		if (user.isCached()) {
			user.getCache().addChange(new UserDataChangeString(key, value), queue);
			user.getPlugin().getUserManager().onChange(user, key);
			if (queue) {
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
		setValues(user.getPlugin().getStorageType(), values);
	}

	public void setValues(String key, DataValue value) {
		HashMap<String, DataValue> values = new HashMap<>();
		values.put(key, value);
		setValues(user.getPlugin().getStorageType(), values);
	}

	public void setValues(UserStorage storage, HashMap<String, DataValue> values) {
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
