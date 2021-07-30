package com.bencodez.advancedcore.api.user;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import org.bukkit.configuration.file.FileConfiguration;

import com.bencodez.advancedcore.AdvancedCorePlugin;
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
	private AdvancedCoreUser user;

	public UserData(AdvancedCoreUser user) {
		this.user = user;
	}

	public boolean getBoolean(String key) {
		return Boolean.valueOf(getString(key));
	}

	public boolean getBoolean(String key, boolean cacheData, boolean waitForCache) {
		return Boolean.valueOf(getString(key, cacheData, waitForCache));
	}

	public FileConfiguration getData(String uuid) {
		return FileThread.getInstance().getThread().getData(this, uuid);
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
		return getInt(AdvancedCorePlugin.getInstance().getStorageType(), key, def, true, waitForCache);
	}

	public int getInt(String key, int def, boolean useCache, boolean waitForCache) {
		return getInt(AdvancedCorePlugin.getInstance().getStorageType(), key, def, useCache, waitForCache);
	}

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
			if (useCache) {
				UserDataCache cache = user.getCache();
				if (cache != null) {
					user.cacheIfNeeded();
					if (cache.isCached(key)) {
						if (cache.getCache().get(key).isInt()) {
							return cache.getCache().get(key).getInt();
						} else {
							return Integer.parseInt(cache.getCache().get(key).getString());
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
							Object value = row.get(i).getValue();
							if (value instanceof Integer) {
								try {
									return (int) value;
								} catch (ClassCastException | NullPointerException ex) {
									ex.printStackTrace();
								}
							} else if (value instanceof String) {
								try {
									return Integer.parseInt((String) value);
								} catch (Exception e) {
								}
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
								return Integer.parseInt(value.getString());
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

		}

		// AdvancedCorePlugin.getInstance()
		// .extraDebug("Failed to get int from '" + key + "' for '" +
		// user.getPlayerName() + "'");

		return def;

	}

	public ArrayList<String> getKeys() {
		return getKeys(true);
	}

	public ArrayList<String> getKeys(boolean waitForCache) {
		ArrayList<String> keys = new ArrayList<String>();
		if (AdvancedCorePlugin.getInstance().getStorageType().equals(UserStorage.FLAT)) {
			keys = new ArrayList<String>(getData(user.getUUID()).getConfigurationSection("").getKeys(false));
		} else if (AdvancedCorePlugin.getInstance().getStorageType().equals(UserStorage.MYSQL)) {
			List<Column> col = getMySqlRow();
			if (col != null && !col.isEmpty()) {
				for (Column c : col) {
					keys.add(c.getName());
				}
			}
		} else if (AdvancedCorePlugin.getInstance().getStorageType().equals(UserStorage.SQLITE)) {
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
		if (storage.equals(UserStorage.FLAT)) {
			keys = new ArrayList<String>(getData(user.getUUID()).getConfigurationSection("").getKeys(false));
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

	public List<Column> getMySqlRow() {
		return AdvancedCorePlugin.getInstance().getMysql().getExact(user.getUUID());
	}

	public List<Column> getSQLiteRow() {
		return AdvancedCorePlugin.getInstance().getSQLiteUserTable()
				.getExact(new Column("uuid", new DataValueString(user.getUUID())));
	}

	@Deprecated
	public String getString(String key) {
		return getString(key, true, true);
	}

	public String getString(String key, boolean waitForCache) {
		return getString(AdvancedCorePlugin.getInstance().getStorageType(), key, true, waitForCache);
	}

	public String getString(String key, boolean useCache, boolean waitForCache) {
		return getString(AdvancedCorePlugin.getInstance().getStorageType(), key, useCache, waitForCache);
	}

	public String getString(UserStorage storage, String key, boolean useCache, boolean waitForCache) {
		if (!key.equals("")) {
			if (user.isTempCache() && tempCache != null) {
				if (tempCache.get(key) != null) {
					if (tempCache.get(key).isString()) {
						return tempCache.get(key).getString();
					}
				} else {
					return "";
				}
			}
			if (useCache) {
				UserDataCache cache = user.getCache();
				if (cache != null) {
					if (cache.isCached(key)) {
						return cache.getCache().get(key).getString();
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
							if (st != null) {
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
							if (st != null) {
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
		 * if (AdvancedCorePlugin.getInstance().isExtraDebug()) {
		 * AdvancedCorePlugin.getInstance() .debug("Extra: Failed to get string from: '"
		 * + key + "' for '" + user.getPlayerName() + "'"); }
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

	public HashMap<String, DataValue> getValues() {
		return getValues(AdvancedCorePlugin.getInstance().getStorageType());
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

	public String getValue(String key) {
		if (AdvancedCorePlugin.getInstance().getStorageType().equals(UserStorage.MYSQL)) {
			if (AdvancedCorePlugin.getInstance().getMysql().isIntColumn(key)) {
				return "" + getInt(key);
			} else {
				return getString(key);
			}
		} else {
			return getString(key);
		}
	}

	public boolean hasData() {
		if (AdvancedCorePlugin.getInstance().getStorageType().equals(UserStorage.MYSQL)) {
			return AdvancedCorePlugin.getInstance().getMysql().containsKey(user.getUUID());
		} else if (AdvancedCorePlugin.getInstance().getStorageType().equals(UserStorage.FLAT)) {
			return FileThread.getInstance().getThread().hasPlayerFile(user.getUUID());
		} else if (AdvancedCorePlugin.getInstance().getStorageType().equals(UserStorage.SQLITE)) {
			return AdvancedCorePlugin.getInstance().getSQLiteUserTable().containsKey(user.getUUID());
		}
		return false;
	}

	public void remove() {
		if (AdvancedCorePlugin.getInstance().getStorageType().equals(UserStorage.MYSQL)) {
			AdvancedCorePlugin.getInstance().getMysql().deletePlayer(user.getUUID());
		} else if (AdvancedCorePlugin.getInstance().getStorageType().equals(UserStorage.FLAT)) {
			FileThread.getInstance().getThread().deletePlayerFile(user.getUUID());
		} else if (AdvancedCorePlugin.getInstance().getStorageType().equals(UserStorage.SQLITE)) {
			AdvancedCorePlugin.getInstance().getSQLiteUserTable()
					.delete(new Column("uuid", new DataValueString(user.getUUID())));
		}
	}

	public void setBoolean(String key, boolean value) {
		setString(key, "" + value);
	}

	public void setBoolean(String key, boolean value, boolean queue) {
		setString(key, "" + value, queue);
	}

	private void setData(final String uuid, final String path, final Object value) {
		FileThread.getInstance().getThread().setData(this, uuid, path, value);
	}

	public void setInt(final String key, final int value) {
		setInt(key, value, true);
	}

	public void setInt(final String key, final int value, boolean queue) {
		setInt(AdvancedCorePlugin.getInstance().getStorageType(), key, value, queue);
	}

	public void setInt(UserStorage storage, final String key, final int value, boolean queue) {
		if (key.equals("")) {
			AdvancedCorePlugin.getInstance().debug("No key: " + key + " to " + value);
			return;
		} else if (key.contains(" ")) {
			AdvancedCorePlugin.getInstance().getLogger().severe("Keys cannot contain spaces " + key);
		}

		AdvancedCorePlugin.getInstance().extraDebug("PlayerData " + storage.toString() + ": Setting " + key + " to '"
				+ value + "' for '" + user.getPlayerName() + "/" + user.getUUID() + "' Queue: " + queue);

		if (user.isCached() && queue) {
			user.getCache().addChange(new UserDataChangeInt(key, value));
			return;
		}

		// process change right away
		if (storage.equals(UserStorage.SQLITE)) {
			ArrayList<Column> columns = new ArrayList<Column>();
			Column primary = new Column("uuid", new DataValueString(user.getUUID()));
			Column column = new Column(key, new DataValueInt(value));
			columns.add(primary);
			columns.add(column);
			AdvancedCorePlugin.getInstance().getSQLiteUserTable().update(primary, columns);
		} else if (storage.equals(UserStorage.MYSQL)) {
			AdvancedCorePlugin.getInstance().getMysql().update(user.getUUID(), key, new DataValueInt(value));
		} else if (storage.equals(UserStorage.FLAT)) {
			setData(user.getUUID(), key, value);
		}

	}

	public void setString(final String key, final String value) {
		setString(key, value, true);
	}

	public void setString(final String key, final String value, boolean queue) {
		setString(AdvancedCorePlugin.getInstance().getStorageType(), key, value, queue);
	}

	public void setString(UserStorage storage, final String key, final String value, boolean queue) {
		if (key.equals("") && value != null) {
			AdvancedCorePlugin.getInstance().debug("No key/value: " + key + " to " + value);
			return;
		} else if (key.contains(" ")) {
			AdvancedCorePlugin.getInstance().getLogger().severe("Keys cannot contain spaces " + key);
		}

		AdvancedCorePlugin.getInstance().extraDebug("PlayerData " + storage.toString() + ": Setting " + key + " to '"
				+ value + "' for '" + user.getPlayerName() + "/" + user.getUUID() + "' Queue: " + queue);

		if (user.isCached() && queue) {
			user.getCache().addChange(new UserDataChangeString(key, value));
			return;
		}

		if (storage.equals(UserStorage.SQLITE)) {
			ArrayList<Column> columns = new ArrayList<Column>();
			Column primary = new Column("uuid", new DataValueString(user.getUUID()));
			Column column = new Column(key, new DataValueString(value));
			columns.add(primary);
			columns.add(column);
			AdvancedCorePlugin.getInstance().getSQLiteUserTable().update(primary, columns);
		} else if (storage.equals(UserStorage.MYSQL)) {
			AdvancedCorePlugin.getInstance().getMysql().update(user.getUUID(), key, new DataValueString(value));
		} else if (storage.equals(UserStorage.FLAT)) {
			setData(user.getUUID(), key, value);
		}
	}

	public void setStringList(final String key, final ArrayList<String> value) {
		setStringList(key, value, true);
	}

	public void setStringList(final String key, final ArrayList<String> value, boolean queue) {
		// AdvancedCorePlugin.getInstance().debug("Setting " + key + " to " +
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
		setValues(AdvancedCorePlugin.getInstance().getStorageType(), values);
	}

	public void setValues(UserStorage storage, HashMap<String, DataValue> values) {
		if (storage.equals(UserStorage.MYSQL)) {
			if (AdvancedCorePlugin.getInstance().getMysql() != null) {
				ArrayList<Column> cols = new ArrayList<Column>();
				for (Entry<String, DataValue> entry : values.entrySet()) {
					if (!entry.getKey().equals("uuid")) {
						cols.add(new Column(entry.getKey(), entry.getValue()));
					}
				}
				AdvancedCorePlugin.getInstance().getMysql().update(user.getUUID(), cols, false);
			}
		} else if (storage.equals(UserStorage.SQLITE)) {
			ArrayList<Column> cols = new ArrayList<Column>();
			for (Entry<String, DataValue> entry : values.entrySet()) {
				if (!entry.getKey().equals("uuid")) {
					cols.add(new Column(entry.getKey(), entry.getValue()));
				}
				AdvancedCorePlugin.getInstance().getSQLiteUserTable()
						.update(new Column("uuid", new DataValueString(user.getUUID())), cols);
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

	@Getter
	private HashMap<String, DataValue> tempCache;

	public void updateCacheWithTemp() {
		if (user.isCached()) {
			user.getCache().updateCache(tempCache);
		}
	}

	public void tempCache() {
		tempCache = getValues();
	}

	public void clearTempCache() {
		tempCache.clear();
		tempCache = null;
	}
}
