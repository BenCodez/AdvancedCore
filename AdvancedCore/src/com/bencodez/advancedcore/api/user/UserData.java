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
import com.bencodez.advancedcore.api.user.usercache.value.UserDataValue;
import com.bencodez.advancedcore.api.user.usercache.value.UserDataValueInt;
import com.bencodez.advancedcore.api.user.usercache.value.UserDataValueString;
import com.bencodez.advancedcore.api.user.userstorage.Column;
import com.bencodez.advancedcore.api.user.userstorage.DataType;
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
							Object value = row.get(i).getValue();
							if (value instanceof Integer) {
								try {
									return (int) value;
								} catch (ClassCastException | NullPointerException ex) {
								}
							} else if (value instanceof String) {
								try {
									return Integer.parseInt((String) row.get(i).getValue());
								} catch (Exception e) {
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
				.getExact(new Column("uuid", user.getUUID(), DataType.STRING));
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
						if (row.get(i).getName().equals(key) && row.get(i).getDataType().equals(DataType.STRING)) {
							String st = (String) row.get(i).getValue();
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
						if (row.get(i).getName().equals(key) && row.get(i).getDataType().equals(DataType.STRING)) {
							// AdvancedCorePlugin.getInstance().debug(key);
							Object value = row.get(i).getValue();

							if (value != null) {
								return value.toString();
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
		if (str.equals("")) {
			return new ArrayList<String>();
		}
		String[] list = str.split("%line%");
		return ArrayUtils.getInstance().convert(list);
	}

	public String getValue(UserStorage storage, String key, boolean useCache) {
		if (storage.equals(UserStorage.MYSQL)) {
			if (AdvancedCorePlugin.getInstance().getMysql().isIntColumn(key)) {
				return "" + getInt(storage, key, 0, useCache, true);
			} else {
				return getString(storage, key, useCache, true);
			}
		} else {
			return getString(storage, key, useCache, true);
		}
	}

	public HashMap<String, UserDataValue> getValues() {
		return getValues(AdvancedCorePlugin.getInstance().getStorageType());
	}

	public HashMap<String, UserDataValue> convert(List<Column> cols) {
		HashMap<String, UserDataValue> data = new HashMap<String, UserDataValue>();
		if (cols != null) {
			for (Column col : cols) {
				data.put(col.getName(), col.toUserData());
			}
		}

		return data;
	}

	public HashMap<String, UserDataValue> getValues(UserStorage storage) {
		if (storage.equals(UserStorage.MYSQL)) {
			return convert(getMySqlRow());
		} else if (storage.equals(UserStorage.SQLITE)) {
			return convert(getSQLiteRow());
		} else if (storage.equals(UserStorage.FLAT)) {
			HashMap<String, UserDataValue> list = new HashMap<String, UserDataValue>();
			FileConfiguration data = getData(user.getUUID());
			for (String str : data.getKeys(false)) {
				if (data.isInt(str)) {
					list.put(str, new UserDataValueInt(data.getInt(str)));
				} else {
					list.put(str, new UserDataValueString(data.getString(str, "")));
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
					.delete(new Column("uuid", user.getUUID(), DataType.STRING));
		}
	}

	public void setBoolean(String key, boolean value) {
		setString(key, "" + value);
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
			Column primary = new Column("uuid", user.getUUID(), DataType.STRING);
			Column column = new Column(key, value, DataType.INTEGER);
			columns.add(primary);
			columns.add(column);
			AdvancedCorePlugin.getInstance().getSQLiteUserTable().update(primary, columns);
		} else if (storage.equals(UserStorage.MYSQL)) {
			AdvancedCorePlugin.getInstance().getMysql().update(user.getUUID(), key, value, DataType.INTEGER);
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
			Column primary = new Column("uuid", user.getUUID(), DataType.STRING);
			Column column = new Column(key, value, DataType.STRING);
			columns.add(primary);
			columns.add(column);
			AdvancedCorePlugin.getInstance().getSQLiteUserTable().update(primary, columns);
		} else if (storage.equals(UserStorage.MYSQL)) {
			AdvancedCorePlugin.getInstance().getMysql().update(user.getUUID(), key, value, DataType.STRING);
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

	public void setValues(HashMap<String, UserDataValue> values) {
		setValues(AdvancedCorePlugin.getInstance().getStorageType(), values);
	}

	public void setValues(UserStorage storage, HashMap<String, UserDataValue> values) {
		if (storage.equals(UserStorage.MYSQL)) {
			if (AdvancedCorePlugin.getInstance().getMysql() != null) {
				ArrayList<Column> cols = new ArrayList<Column>();
				for (Entry<String, UserDataValue> entry : values.entrySet()) {
					if (!entry.getKey().equals("uuid")) {
						if (entry.getValue() instanceof UserDataValueInt) {
							cols.add(new Column(entry.getKey(), entry.getValue().getInt(), DataType.INTEGER));
						} else if (entry.getValue() instanceof UserDataValueString) {
							cols.add(new Column(entry.getKey(), entry.getValue().getString(), DataType.STRING));
						}
					}
				}
				AdvancedCorePlugin.getInstance().getMysql().update(user.getUUID(), cols, false);
			}
		} else if (storage.equals(UserStorage.SQLITE)) {
			ArrayList<Column> cols = new ArrayList<Column>();
			for (Entry<String, UserDataValue> entry : values.entrySet()) {
				if (!entry.getKey().equals("uuid")) {
					if (entry.getValue().isInt()) {
						cols.add(new Column(entry.getKey(), entry.getValue().getInt(), DataType.INTEGER));
					} else if (entry.getValue().isString()) {
						cols.add(new Column(entry.getKey(), entry.getValue().getString(), DataType.STRING));
					}
				}
				AdvancedCorePlugin.getInstance().getSQLiteUserTable()
						.update(new Column("uuid", user.getUUID(), DataType.STRING), cols);
			}
		} else if (storage.equals(UserStorage.FLAT)) {
			for (Entry<String, UserDataValue> entry : values.entrySet()) {
				if (entry.getValue() instanceof UserDataValueString) {
					setData(user.getUUID(), entry.getKey(), entry.getValue().getString());
				} else if (entry.getValue() instanceof UserDataValueInt) {
					setData(user.getUUID(), entry.getKey(), entry.getValue().getInt());
				}
			}
		}
	}

	@Getter
	private HashMap<String, UserDataValue> tempCache;

	public void tempCache() {
		tempCache = getValues();
	}

	public void clearTempCache() {
		tempCache.clear();
		tempCache = null;
	}
}
