package com.bencodez.advancedcore.api.user;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import org.bukkit.configuration.file.FileConfiguration;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.misc.ArrayUtils;
import com.bencodez.advancedcore.api.user.userstorage.sql.Column;
import com.bencodez.advancedcore.api.user.userstorage.sql.DataType;
import com.bencodez.advancedcore.thread.FileThread;

public class UserData {
	private AdvancedCoreUser user;

	public UserData(AdvancedCoreUser user) {
		this.user = user;
	}

	public boolean getBoolean(String key) {
		return Boolean.valueOf(getString(key));
	}

	public FileConfiguration getData(String uuid) {
		return FileThread.getInstance().getThread().getData(this, uuid);
	}

	@Deprecated
	public int getInt(String key) {
		return getInt(key, 0, true);
	}

	public int getInt(String key, boolean waitForCache) {
		return getInt(key, 0, waitForCache);
	}

	public int getInt(String key, int def, boolean waitForCache) {
		return getInt(AdvancedCorePlugin.getInstance().getStorageType(), key, def, waitForCache);
	}

	public int getInt(UserStorage storage, String key, int def, boolean waitForCache) {
		if (!key.equals("")) {
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

			} else if (storage.equals(UserStorage.MYSQL)) {
				List<Column> row = getMySqlRow(waitForCache);
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
			List<Column> col = getMySqlRow(waitForCache);
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
			List<Column> col = getMySqlRow(waitForCache);
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

	public List<Column> getMySqlRow(boolean waitForCache) {
		return AdvancedCorePlugin.getInstance().getMysql().getExact(user.getUUID(), waitForCache);
	}

	public List<Column> getSQLiteRow() {
		return AdvancedCorePlugin.getInstance().getSQLiteUserTable()
				.getExact(new Column("uuid", user.getUUID(), DataType.STRING));
	}

	@Deprecated
	public String getString(String key) {
		return getString(key, true);
	}

	public String getString(String key, boolean waitForCache) {
		return getString(AdvancedCorePlugin.getInstance().getStorageType(), key, waitForCache);
	}

	public String getString(UserStorage storage, String key, boolean waitForCache) {
		if (!key.equals("")) {
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
				List<Column> row = getMySqlRow(waitForCache);
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
		String str = getString(key);
		if (str.equals("")) {
			return new ArrayList<String>();
		}
		String[] list = str.split("%line%");
		return ArrayUtils.getInstance().convert(list);
	}

	public String getValue(UserStorage storage, String key) {
		if (storage.equals(UserStorage.MYSQL)) {
			if (AdvancedCorePlugin.getInstance().getMysql().isIntColumn(key)) {
				return "" + getInt(storage, key, 0, true);
			} else {
				return getString(storage, key, true);
			}
		} else {
			return getString(storage, key, true);
		}
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
			return AdvancedCorePlugin.getInstance().getSQLiteUserTable()
					.containsKey(new Column("uuid", user.getUUID(), DataType.STRING));
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

		if (storage.equals(UserStorage.SQLITE)) {
			ArrayList<Column> columns = new ArrayList<Column>();
			Column primary = new Column("uuid", user.getUUID(), DataType.STRING);
			Column column = new Column(key, value, DataType.INTEGER);
			columns.add(primary);
			columns.add(column);
			AdvancedCorePlugin.getInstance().getSQLiteUserTable().update(primary, columns);
		} else if (storage.equals(UserStorage.MYSQL)) {
			AdvancedCorePlugin.getInstance().getMysql().update(user.getUUID(), key, value, DataType.INTEGER, queue);
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

		if (storage.equals(UserStorage.SQLITE)) {
			ArrayList<Column> columns = new ArrayList<Column>();
			Column primary = new Column("uuid", user.getUUID(), DataType.STRING);
			Column column = new Column(key, value, DataType.STRING);
			columns.add(primary);
			columns.add(column);
			AdvancedCorePlugin.getInstance().getSQLiteUserTable().update(primary, columns);
		} else if (storage.equals(UserStorage.MYSQL)) {
			AdvancedCorePlugin.getInstance().getMysql().update(user.getUUID(), key, value, DataType.STRING, queue);
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

	public void setValues(UserStorage storage, HashMap<String, String> values) {
		if (storage.equals(UserStorage.MYSQL)) {
			if (AdvancedCorePlugin.getInstance().getMysql() != null) {
				ArrayList<Column> cols = new ArrayList<Column>();
				for (Entry<String, String> entry : values.entrySet()) {
					if (!entry.getKey().equals("uuid")) {
						if (AdvancedCorePlugin.getInstance().getMysql().isIntColumn(entry.getKey())) {
							cols.add(new Column(entry.getKey(), entry.getValue(), DataType.INTEGER));
						} else {
							cols.add(new Column(entry.getKey(), entry.getValue(), DataType.STRING));
						}
					}
				}
				AdvancedCorePlugin.getInstance().getMysql().update(user.getUUID(), cols, false, false);
			}
		} else if (storage.equals(UserStorage.SQLITE)) {
			ArrayList<Column> cols = new ArrayList<Column>();
			for (Entry<String, String> entry : values.entrySet()) {
				if (!entry.getKey().equals("uuid")) {
					if (AdvancedCorePlugin.getInstance().getMysql().isIntColumn(entry.getKey())) {
						cols.add(new Column(entry.getKey(), entry.getValue(), DataType.INTEGER));
					} else {
						cols.add(new Column(entry.getKey(), entry.getValue(), DataType.STRING));
					}
				}
			}
			AdvancedCorePlugin.getInstance().getSQLiteUserTable()
					.update(new Column("uuid", user.getUUID(), DataType.STRING), cols);
		} else if (storage.equals(UserStorage.FLAT)) {
			for (Entry<String, String> entry : values.entrySet()) {
				setData(user.getUUID(), entry.getKey(), entry.getValue());
			}
		}
	}
}
