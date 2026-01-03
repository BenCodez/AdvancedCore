package com.bencodez.advancedcore.api.user;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import org.bukkit.configuration.file.FileConfiguration;

import com.bencodez.advancedcore.api.user.usercache.UserDataCache;
import com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt;
import com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeString;
import com.bencodez.advancedcore.thread.FileThread;
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
		HashMap<String, DataValue> data = new HashMap<>();
		if (cols != null) {
			for (Column col : cols) {
				data.put(col.getName(), col.getValue());
			}
		}

		return data;
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

	@Deprecated
	public FileConfiguration getData(String uuid) {
		return FileThread.getInstance().getThread().getData(this, uuid);
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

	@SuppressWarnings("deprecation")
	public int getInt(UserStorage storage, String key, int def, UserDataFetchMode mode) {
		if (key == null || key.isEmpty()) {
			if (storage.equals(UserStorage.FLAT)) {
				try {
					return getData(user.getUUID()).getInt(key, def);
				} catch (Exception ignored) {
				}
			}
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
		if (storage.equals(UserStorage.SQLITE)) {
			List<Column> row = getSQLiteRow();
			if (row != null) {
				for (Column element : row) {
					if (element.getName().equals(key)) {
						DataValue value = element.getValue();
						if (value.isInt()) {
							return value.getInt();
						}
						if (value.isString()) {
							String str = value.getString();
							if (str != null) {
								try {
									return Integer.parseInt(str);
								} catch (Exception ignored) {
								}
							}
							return def;
						}
					}
				}
			}
		} else if (storage.equals(UserStorage.MYSQL)) {
			List<Column> row = getMySqlRow();
			if (row != null) {
				for (Column element : row) {
					if (element.getName().equals(key)) {
						DataValue value = element.getValue();
						if (value.isInt()) {
							return value.getInt();
						}
						if (value.isString()) {
							String str = value.getString();
							if (str != null) {
								try {
									return Integer.parseInt(str);
								} catch (Exception ignored) {
								}
							}
							return def;
						}
					}
				}
			}
		} else if (storage.equals(UserStorage.FLAT)) {
			try {
				return getData(user.getUUID()).getInt(key, def);
			} catch (Exception ignored) {
			}
		}

		return def;
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

	@SuppressWarnings("deprecation")
	public ArrayList<String> getKeys(UserStorage storage) {
		ArrayList<String> keys = new ArrayList<>();
		if (storage.equals(UserStorage.FLAT)) {
			keys = new ArrayList<>(getData(user.getUUID()).getConfigurationSection("").getKeys(false));
		} else if (storage.equals(UserStorage.MYSQL)) {
			List<Column> col = getMySqlRow();
			if (col != null && !col.isEmpty()) {
				for (Column c : col) {
					keys.add(c.getName());
				}
			}
		} else if (storage.equals(UserStorage.SQLITE)) {
			List<Column> col = getSQLiteRow();
			if (col != null && !col.isEmpty()) {
				for (Column c : col) {
					keys.add(c.getName());
				}
			}
		}

		return keys;
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
		return user.getPlugin().getMysql().getExact(user.getUUID());
	}

	public List<Column> getSQLiteRow() {
		return user.getPlugin().getSQLiteUserTable().getExact(new Column("uuid", new DataValueString(user.getUUID())));
	}

	public String getString(String key) {
		return getString(key, user.getUserDataFetchMode());
	}

	public String getString(String key, UserDataFetchMode mode) {
		return getString(user.getPlugin().getStorageType(), key, mode);
	}

	@SuppressWarnings("deprecation")
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
		if (storage.equals(UserStorage.SQLITE)) {
			List<Column> row = getSQLiteRow();
			if (row != null) {
				for (Column element : row) {
					if (element.getName().equals(key)
							&& (element.getValue().isString() || element.getValue().isBoolean())) {
						String st = element.getValue().getString();
						return (st != null && !st.equalsIgnoreCase("null")) ? st : "";
					}
				}
			}
		} else if (storage.equals(UserStorage.MYSQL)) {
			List<Column> row = getMySqlRow();
			if (row != null) {
				for (Column element : row) {
					if (element.getName().equals(key)
							&& (element.getValue().isString() || element.getValue().isBoolean())) {
						String st = element.getValue().getString();
						return (st != null && !st.equalsIgnoreCase("null")) ? st : "";
					}
				}
			}
		} else if (storage.equals(UserStorage.FLAT)) {
			try {
				return getData(user.getUUID()).getString(key, "");
			} catch (Exception ignored) {
			}
		}

		return "";
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

	@SuppressWarnings("deprecation")
	public HashMap<String, DataValue> getValues(UserStorage storage) {
		if (storage.equals(UserStorage.MYSQL)) {
			return convert(getMySqlRow());
		}
		if (storage.equals(UserStorage.SQLITE)) {
			return convert(getSQLiteRow());
		} else if (storage.equals(UserStorage.FLAT)) {
			HashMap<String, DataValue> list = new HashMap<>();
			FileConfiguration data = getData(user.getUUID());
			for (String str : data.getKeys(false)) {
				if (data.isInt(str)) {
					list.put(str, new DataValueInt(data.getInt(str)));
				} else {
					list.put(str, new DataValueString(data.getString(str, "")));
				}
			}
			return list;
		}
		return null;
	}

	@SuppressWarnings("deprecation")
	public boolean hasData() {
		if (user.getPlugin().getStorageType().equals(UserStorage.MYSQL)) {
			return user.getPlugin().getMysql().containsKey(user.getUUID());
		}
		if (user.getPlugin().getStorageType().equals(UserStorage.SQLITE)) {
			return user.getPlugin().getSQLiteUserTable().containsKey(user.getUUID());
		} else if (user.getPlugin().getStorageType().equals(UserStorage.FLAT)) {
			return FileThread.getInstance().getThread().hasPlayerFile(user.getUUID());
		}
		return false;
	}

	@SuppressWarnings("deprecation")
	public void remove() {
		if (user.getPlugin().getStorageType().equals(UserStorage.MYSQL)) {
			user.getPlugin().getMysql().deletePlayer(user.getUUID());
		} else if (user.getPlugin().getStorageType().equals(UserStorage.SQLITE)) {
			user.getPlugin().getSQLiteUserTable().delete(new Column("uuid", new DataValueString(user.getUUID())));
		} else if (user.getPlugin().getStorageType().equals(UserStorage.FLAT)) {
			FileThread.getInstance().getThread().deletePlayerFile(user.getUUID());
		}
		user.clearCache();
	}

	public void setBoolean(String key, boolean value) {
		setString(key, "" + value);
	}

	public void setBoolean(String key, boolean value, boolean queue) {
		setString(key, "" + value, queue);
	}

	@Deprecated
	private void setData(final String uuid, final String path, final Object value) {
		FileThread.getInstance().getThread().setData(this, uuid, path, value);
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

	@SuppressWarnings("deprecation")
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
					if (storage.equals(UserStorage.SQLITE)) {
						ArrayList<Column> columns = new ArrayList<>();
						Column primary = new Column("uuid", new DataValueString(user.getUUID()));
						Column column = new Column(key, new DataValueInt(value));
						columns.add(primary);
						columns.add(column);
						user.getPlugin().getSQLiteUserTable().update(primary, columns);
					} else if (storage.equals(UserStorage.MYSQL)) {
						user.getPlugin().getMysql().update(user.getUUID(), key, new DataValueInt(value));
					} else if (storage.equals(UserStorage.FLAT)) {
						setData(user.getUUID(), key, value);
					}

					if (!user.isCached()) {
						user.getPlugin().getUserManager().onChange(user, key);
					}
				}
			});
		} else {
			// process change right away
			if (storage.equals(UserStorage.SQLITE)) {
				ArrayList<Column> columns = new ArrayList<>();
				Column primary = new Column("uuid", new DataValueString(user.getUUID()));
				Column column = new Column(key, new DataValueInt(value));
				columns.add(primary);
				columns.add(column);
				user.getPlugin().getSQLiteUserTable().update(primary, columns);
			} else if (storage.equals(UserStorage.MYSQL)) {
				user.getPlugin().getMysql().update(user.getUUID(), key, new DataValueInt(value));
			} else if (storage.equals(UserStorage.FLAT)) {
				setData(user.getUUID(), key, value);
			}

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

	@SuppressWarnings("deprecation")
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
					if (storage.equals(UserStorage.SQLITE)) {
						ArrayList<Column> columns = new ArrayList<>();
						Column primary = new Column("uuid", new DataValueString(user.getUUID()));
						Column column = new Column(key, new DataValueString(value));
						columns.add(primary);
						columns.add(column);
						user.getPlugin().getSQLiteUserTable().update(primary, columns);
					} else if (storage.equals(UserStorage.MYSQL)) {
						user.getPlugin().getMysql().update(user.getUUID(), key, new DataValueString(value));
					} else if (storage.equals(UserStorage.FLAT)) {
						setData(user.getUUID(), key, value);
					}
					if (!user.isCached()) {
						user.getPlugin().getUserManager().onChange(user, key);
					}
				}
			});
		} else {
			if (storage.equals(UserStorage.SQLITE)) {
				ArrayList<Column> columns = new ArrayList<>();
				Column primary = new Column("uuid", new DataValueString(user.getUUID()));
				Column column = new Column(key, new DataValueString(value));
				columns.add(primary);
				columns.add(column);
				user.getPlugin().getSQLiteUserTable().update(primary, columns);
			} else if (storage.equals(UserStorage.MYSQL)) {
				user.getPlugin().getMysql().update(user.getUUID(), key, new DataValueString(value));
			} else if (storage.equals(UserStorage.FLAT)) {
				setData(user.getUUID(), key, value);
			}
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

	@SuppressWarnings("deprecation")
	public void setValues(UserStorage storage, HashMap<String, DataValue> values) {
		if (storage.equals(UserStorage.MYSQL)) {
			if (user.getPlugin().getMysql() != null) {
				ArrayList<Column> cols = new ArrayList<>();
				for (Entry<String, DataValue> entry : values.entrySet()) {
					if (!entry.getKey().equals("uuid")) {
						cols.add(new Column(entry.getKey(), entry.getValue()));
					}
				}
				user.getPlugin().getMysql().update(user.getUUID(), cols, false);
			}
		} else if (storage.equals(UserStorage.SQLITE)) {
			ArrayList<Column> cols = new ArrayList<>();
			for (Entry<String, DataValue> entry : values.entrySet()) {
				if (!entry.getKey().equals("uuid")) {
					cols.add(new Column(entry.getKey(), entry.getValue()));
				}
				user.getPlugin().getSQLiteUserTable().update(new Column("uuid", new DataValueString(user.getUUID())),
						cols);
			}
		} else if (storage.equals(UserStorage.FLAT)) {
			for (Entry<String, DataValue> entry : values.entrySet()) {
				if (entry.getValue() instanceof DataValueString) {
					setData(user.getUUID(), entry.getKey(), entry.getValue().getString());
				} else if (entry.getValue() instanceof DataValueInt) {
					setData(user.getUUID(), entry.getKey(), entry.getValue().getInt());
				}
			}
		}
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
