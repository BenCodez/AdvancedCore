package com.Ben12345rocks.AdvancedCore.UserManager;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.configuration.file.FileConfiguration;

import com.Ben12345rocks.AdvancedCore.AdvancedCorePlugin;
import com.Ben12345rocks.AdvancedCore.Thread.FileThread;
import com.Ben12345rocks.AdvancedCore.UserStorage.sql.Column;
import com.Ben12345rocks.AdvancedCore.UserStorage.sql.DataType;
import com.Ben12345rocks.AdvancedCore.Util.Misc.ArrayUtils;

public class UserData {
	private User user;

	public UserData(User user) {
		this.user = user;
	}

	public boolean getBoolean(String key) {
		return Boolean.valueOf(getString(key));
	}

	public FileConfiguration getData(String uuid) {
		return FileThread.getInstance().getThread().getData(this, uuid);
	}

	public int getInt(String key) {
		return getInt(key, 0, true);
	}

	public int getInt(String key, int def, boolean waitForCache) {
		if (!key.equals("")) {
			if (AdvancedCorePlugin.getInstance().getStorageType().equals(UserStorage.SQLITE)) {
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

			} else if (AdvancedCorePlugin.getInstance().getStorageType().equals(UserStorage.MYSQL)) {
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
			} else if (AdvancedCorePlugin.getInstance().getStorageType().equals(UserStorage.FLAT)) {
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

	public List<Column> getMySqlRow(boolean waitForCache) {
		return AdvancedCorePlugin.getInstance().getMysql().getExact(user.getUUID(),waitForCache);
	}

	public List<Column> getSQLiteRow() {
		return AdvancedCorePlugin.getInstance().getSQLiteUserTable()
				.getExact(new Column("uuid", user.getUUID(), DataType.STRING));
	}
	
	public String getString(String key) {
		return getString(key, true);
	}

	public String getString(String key, boolean waitForCache) {
		if (!key.equals("")) {
			if (AdvancedCorePlugin.getInstance().getStorageType().equals(UserStorage.SQLITE)) {
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

			} else if (AdvancedCorePlugin.getInstance().getStorageType().equals(UserStorage.MYSQL)) {
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
			} else if (AdvancedCorePlugin.getInstance().getStorageType().equals(UserStorage.FLAT)) {
				try {
					return getData(user.getUUID()).getString(key, "");
				} catch (Exception e) {

				}
			}
		}
		/*
		 * if (AdvancedCorePlugin.getInstance().isExtraDebug()) {
		 * AdvancedCorePlugin.getInstance() .debug("Extra: Failed to get string from: '"
		 * +
		 * key + "' for '" + user.getPlayerName() + "'"); }
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
		if (key.equals("")) {
			AdvancedCorePlugin.getInstance().debug("No key: " + key + " to " + value);
			return;
		} else if (key.contains(" ")) {
			AdvancedCorePlugin.getInstance().getLogger().severe("Keys cannot contain spaces " + key);
		}

		AdvancedCorePlugin.getInstance().extraDebug("Player Data: Setting " + key + " to '" + value + "' for '"
				+ user.getPlayerName() + "' Queue: " + queue);

		if (AdvancedCorePlugin.getInstance().getStorageType().equals(UserStorage.SQLITE)) {
			ArrayList<Column> columns = new ArrayList<Column>();
			Column primary = new Column("uuid", user.getUUID(), DataType.STRING);
			Column column = new Column(key, value, DataType.INTEGER);
			columns.add(primary);
			columns.add(column);
			AdvancedCorePlugin.getInstance().getSQLiteUserTable().update(primary, columns);
		} else if (AdvancedCorePlugin.getInstance().getStorageType().equals(UserStorage.MYSQL)) {
			AdvancedCorePlugin.getInstance().getMysql().update(user.getUUID(), key, value, DataType.INTEGER, queue);
		} else if (AdvancedCorePlugin.getInstance().getStorageType().equals(UserStorage.FLAT)) {
			setData(user.getUUID(), key, value);
		}

	}

	public void setString(final String key, final String value) {
		setString(key, value, true);
	}

	public void setString(final String key, final String value, boolean queue) {
		if (key.equals("") && value != null) {
			AdvancedCorePlugin.getInstance().debug("No key/value: " + key + " to " + value);
			return;
		} else if (key.contains(" ")) {
			AdvancedCorePlugin.getInstance().getLogger().severe("Keys cannot contain spaces " + key);
		}

		AdvancedCorePlugin.getInstance().extraDebug("Player Data: Setting " + key + " to '" + value + "' for '"
				+ user.getPlayerName() + "' Queue: " + queue);

		if (AdvancedCorePlugin.getInstance().getStorageType().equals(UserStorage.SQLITE)) {
			ArrayList<Column> columns = new ArrayList<Column>();
			Column primary = new Column("uuid", user.getUUID(), DataType.STRING);
			Column column = new Column(key, value, DataType.STRING);
			columns.add(primary);
			columns.add(column);
			AdvancedCorePlugin.getInstance().getSQLiteUserTable().update(primary, columns);
		} else if (AdvancedCorePlugin.getInstance().getStorageType().equals(UserStorage.MYSQL)) {
			AdvancedCorePlugin.getInstance().getMysql().update(user.getUUID(), key, value, DataType.STRING, queue);
		} else if (AdvancedCorePlugin.getInstance().getStorageType().equals(UserStorage.FLAT)) {
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
}
