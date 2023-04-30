package com.bencodez.advancedcore.api.user;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import org.bukkit.configuration.file.FileConfiguration;

import com.bencodez.advancedcore.api.misc.ArrayUtils;
import com.bencodez.advancedcore.api.user.usercache.UserDataCache;
import com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt;
import com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeString;
import com.bencodez.advancedcore.api.user.usercache.value.DataValue;
import com.bencodez.advancedcore.api.user.usercache.value.DataValueInt;
import com.bencodez.advancedcore.api.user.usercache.value.DataValueString;
import com.bencodez.advancedcore.api.user.userstorage.Column;
import com.bencodez.advancedcore.thread.FileThread;

import lombok.Getter;

public class UserData {
	@Getter
	private HashMap<String, DataValue> tempCache;

	private AdvancedCoreUser user;

	public UserData(AdvancedCoreUser user) {
		this.user = user;
	}

	public void clearTempCache() {
		tempCache.clear();
		tempCache = null;
	}

	@Deprecated
	public FileConfiguration getData(String uuid) {
		return FileThread.getInstance().getThread().getData(this, uuid);
	}

	public HashMap<String, DataValue> convert(List<Column> cols) {
		HashMap<String, DataValue> data = new HashMap<String, DataValue>();
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

	public boolean getBoolean(String key, boolean useCache, boolean waitForCache) {
		return Boolean.valueOf(getString(key, useCache, waitForCache));
	}

	@Deprecated
	public int getInt(String key) {
		return getInt(key, 0, true, true);
	}

	public int getInt(String key, boolean waitForCache) {
		return getInt(key, 0, true, waitForCache);
	}

	public int getInt(String key, boolean useCache, boolean waitForCache) {
		return getInt(key, 0, useCache, waitForCache);
	}

	public int getInt(String key, int def, boolean waitForCache) {
		return getInt(user.getPlugin().getStorageType(), key, def, true, waitForCache);
	}

	public int getInt(String key, int def, boolean useCache, boolean waitForCache) {
		return getInt(user.getPlugin().getStorageType(), key, def, useCache, waitForCache);
	}

	@SuppressWarnings("deprecation")
	public int getInt(UserStorage storage, String key, int def, boolean useCache, boolean waitForCache) {
		if (!key.equals("")) {
			if (user.isTempCache() && tempCache != null) {
				if (tempCache.get(key) != null) {
					if (tempCache.get(key).isInt()) {
						return tempCache.get(key).getInt();
					}
				} else {
					return def;
				}
			}
			// user.getPlugin().debug("Pulling data: " + key + " " + useCache + " " +
			// waitForCache);
			if (useCache) {
				UserDataCache cache = user.getCache();
				if (cache != null) {
					user.cacheIfNeeded();
					if (cache.isCached(key)) {
						if (cache.getCache().get(key).isInt()) {
							// user.getPlugin().debug("Using cache: " + key + " " +
							// cache.getCache().get(key).getInt());
							return cache.getCache().get(key).getInt();
						} else {
							String str = cache.getCache().get(key).getString();
							if (str != null && !str.equals("null")) {
								try {
									return Integer.parseInt(str);
								} catch (Exception e) {
								}
							}

						}
					}
				} else {
					user.cache();
				}
			}
			if (storage.equals(UserStorage.SQLITE)) {
				List<Column> row = getSQLiteRow();
				if (row != null) {
					for (int i = 0; i < row.size(); i++) {
						if (row.get(i).getName().equals(key)) {
							DataValue value = row.get(i).getValue();
							if (value.isInt()) {
								return value.getInt();
							} else if (value.isString()) {
								String str = value.getString();
								if (str != null) {
									try {
										return Integer.parseInt(str);
									} catch (Exception e) {
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
					for (int i = 0; i < row.size(); i++) {
						if (row.get(i).getName().equals(key)) {
							DataValue value = row.get(i).getValue();
							if (value.isInt()) {
								return value.getInt();
							} else if (value.isString()) {
								String str = value.getString();
								if (str != null) {
									try {
										return Integer.parseInt(str);
									} catch (Exception e) {
									}
								}
								return def;
							}
						}
					}
				}
			}
		} else if (storage.equals(UserStorage.FLAT)) {
			try {
				return getData(user.getUUID()).getInt(key, def);
			} catch (Exception e) {

			}

		}

		// user.getPlugin()
		// .extraDebug("Failed to get int from '" + key + "' for '" +
		// user.getPlayerName() + "'");

		return def;

	}

	public ArrayList<String> getKeys() {
		return getKeys(true);
	}

	@SuppressWarnings("deprecation")
	public ArrayList<String> getKeys(boolean waitForCache) {
		ArrayList<String> keys = new ArrayList<String>();
		if (user.getPlugin().getStorageType().equals(UserStorage.FLAT)) {
			keys = new ArrayList<String>(getData(user.getUUID()).getConfigurationSection("").getKeys(false));
		} else if (user.getPlugin().getStorageType().equals(UserStorage.MYSQL)) {
			List<Column> col = getMySqlRow();
			if (col != null && !col.isEmpty()) {
				for (Column c : col) {
					keys.add(c.getName());
				}
			}
		} else if (user.getPlugin().getStorageType().equals(UserStorage.SQLITE)) {
			List<Column> col = getSQLiteRow();
			if (col != null && !col.isEmpty()) {
				for (Column c : col) {
					keys.add(c.getName());
				}
			}
		}

		return keys;
	}

	public ArrayList<String> getKeys(UserStorage storage, boolean waitForCache) {
		ArrayList<String> keys = new ArrayList<String>();
		if (storage.equals(UserStorage.MYSQL)) {
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

	public List<Column> getMySqlRow() {
		return user.getPlugin().getMysql().getExact(user.getUUID());
	}

	public List<Column> getSQLiteRow() {
		return user.getPlugin().getSQLiteUserTable().getExact(new Column("uuid", new DataValueString(user.getUUID())));
	}

	@Deprecated
	public String getString(String key) {
		return getString(key, true, true);
	}

	public String getString(String key, boolean waitForCache) {
		return getString(user.getPlugin().getStorageType(), key, true, waitForCache);
	}

	public String getString(String key, boolean useCache, boolean waitForCache) {
		return getString(user.getPlugin().getStorageType(), key, useCache, waitForCache);
	}

	@SuppressWarnings("deprecation")
	public String getString(UserStorage storage, String key, boolean useCache, boolean waitForCache) {
		if (!key.equals("")) {
			if (user.isTempCache() && tempCache != null) {
				if (tempCache.get(key) != null) {
					if (tempCache.get(key).isString()) {
						String str = tempCache.get(key).getString();
						if (str != null) {
							return str;
						}
						return "";
					}
				} else {
					return "";
				}
			}
			if (useCache) {
				UserDataCache cache = user.getCache();
				if (cache != null) {
					if (cache.isCached(key)) {
						String str = cache.getCache().get(key).getString();
						if (str != null) {
							return str;
						}
						return "";
					}
				} else {
					user.cache();
				}
			}

			if (storage.equals(UserStorage.SQLITE)) {
				List<Column> row = getSQLiteRow();
				if (row != null) {
					for (int i = 0; i < row.size(); i++) {
						if (row.get(i).getName().equals(key) && row.get(i).getValue().isString()) {
							String st = row.get(i).getValue().getString();
							if (st != null && !st.equals("null")) {
								return st;
							}
							return "";
						}
					}
				}

			} else if (storage.equals(UserStorage.MYSQL)) {
				List<Column> row = getMySqlRow();
				if (row != null) {
					for (int i = 0; i < row.size(); i++) {
						if (row.get(i).getName().equals(key) && row.get(i).getValue().isString()) {
							String st = row.get(i).getValue().getString();
							if (st != null && !st.equals("null")) {
								return st;
							}
							return "";
						}
					}
				}
			} else if (storage.equals(UserStorage.FLAT)) {
				try {
					return getData(user.getUUID()).getString(key, "");
				} catch (Exception e) {

				}
			}
		}
		/*
		 * if (user.getPlugin().isExtraDebug()) { user.getPlugin()
		 * .debug("Extra: Failed to get string from: '" + key + "' for '" +
		 * user.getPlayerName() + "'"); }
		 */
		return "";

	}

	public ArrayList<String> getStringList(String key) {
		return getStringList(key, true, true);
	}

	public ArrayList<String> getStringList(String key, boolean cache, boolean waitForCache) {
		String str = getString(key, cache, waitForCache);
		if (str == null || str.equals("")) {
			return new ArrayList<String>();
		}
		String[] list = str.split("%line%");
		return ArrayUtils.getInstance().convert(list);
	}

	public String getValue(String key) {
		boolean isInt = user.getPlugin().getUserManager().getDataManager().isInt(key);
		if (isInt) {
			return "" + getInt(key);
		}
		return getString(key);
	}

	public DataValue getDataValue(String key) {
		boolean isInt = user.getPlugin().getUserManager().getDataManager().isInt(key);
		if (isInt) {
			return new DataValueInt(getInt(key));
		}
		return new DataValueString(getString(key));
	}

	public HashMap<String, DataValue> getValues() {
		return getValues(user.getPlugin().getStorageType());
	}

	@SuppressWarnings("deprecation")
	public HashMap<String, DataValue> getValues(UserStorage storage) {
		if (storage.equals(UserStorage.MYSQL)) {
			return convert(getMySqlRow());
		} else if (storage.equals(UserStorage.SQLITE)) {
			return convert(getSQLiteRow());
		} else if (storage.equals(UserStorage.FLAT)) {
			HashMap<String, DataValue> list = new HashMap<String, DataValue>();
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
		} else if (user.getPlugin().getStorageType().equals(UserStorage.SQLITE)) {
			return user.getPlugin().getSQLiteUserTable().containsKey(user.getUUID());
		} else if (user.getPlugin().getStorageType().equals(UserStorage.FLAT)) {
			return FileThread.getInstance().getThread().hasPlayerFile(user.getUUID());
		}
		return false;
	}

	@Deprecated
	private void setData(final String uuid, final String path, final Object value) {
		FileThread.getInstance().getThread().setData(this, uuid, path, value);
	}

	public void remove() {
		if (user.getPlugin().getStorageType().equals(UserStorage.MYSQL)) {
			user.getPlugin().getMysql().deletePlayer(user.getUUID());
		} else if (user.getPlugin().getStorageType().equals(UserStorage.SQLITE)) {
			user.getPlugin().getSQLiteUserTable().delete(new Column("uuid", new DataValueString(user.getUUID())));
		}
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

	@SuppressWarnings("deprecation")
	public void setInt(UserStorage storage, final String key, final int value, boolean queue) {
		if (key.equals("")) {
			user.getPlugin().debug("No key: " + key + " to " + value);
			return;
		} else if (key.contains(" ")) {
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

		// process change right away
		if (storage.equals(UserStorage.SQLITE)) {
			ArrayList<Column> columns = new ArrayList<Column>();
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

		user.getPlugin().getUserManager().onChange(user, key);
	}

	public void setString(final String key, final String value) {
		setString(key, value, true);
	}

	public void setString(final String key, final String value, boolean queue) {
		setString(user.getPlugin().getStorageType(), key, value, queue);
	}

	@SuppressWarnings("deprecation")
	public void setString(UserStorage storage, final String key, final String value, boolean queue) {
		if (key.equals("") && value != null) {
			user.getPlugin().debug("No key/value: " + key + " to " + value);
			return;
		} else if (key.contains(" ")) {
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

		if (storage.equals(UserStorage.SQLITE)) {
			ArrayList<Column> columns = new ArrayList<Column>();
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

		user.getPlugin().getUserManager().onChange(user, key);
	}

	public void setStringList(final String key, final ArrayList<String> value) {
		setStringList(key, value, true);
	}

	public void setStringList(final String key, final ArrayList<String> value, boolean queue) {
		// user.getPlugin().debug("Setting " + key + " to " +
		// value);
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
		HashMap<String, DataValue> values = new HashMap<String, DataValue>();
		values.put(key, value);
		setValues(user.getPlugin().getStorageType(), values);
	}

	@SuppressWarnings("deprecation")
	public void setValues(UserStorage storage, HashMap<String, DataValue> values) {
		if (storage.equals(UserStorage.MYSQL)) {
			if (user.getPlugin().getMysql() != null) {
				ArrayList<Column> cols = new ArrayList<Column>();
				for (Entry<String, DataValue> entry : values.entrySet()) {
					if (!entry.getKey().equals("uuid")) {
						cols.add(new Column(entry.getKey(), entry.getValue()));
					}
				}
				user.getPlugin().getMysql().update(user.getUUID(), cols, false);
			}
		} else if (storage.equals(UserStorage.SQLITE)) {
			ArrayList<Column> cols = new ArrayList<Column>();
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
}
