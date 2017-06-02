package com.Ben12345rocks.AdvancedCore.Objects;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.configuration.file.FileConfiguration;

import com.Ben12345rocks.AdvancedCore.AdvancedCoreHook;
import com.Ben12345rocks.AdvancedCore.Thread.FileThread;
import com.Ben12345rocks.AdvancedCore.Util.Misc.ArrayUtils;
import com.Ben12345rocks.AdvancedCore.sql.Column;
import com.Ben12345rocks.AdvancedCore.sql.DataType;

public class UserData {
	private User user;

	public UserData(User user) {
		this.user = user;
	}

	public FileConfiguration getData(String uuid) {
		return FileThread.getInstance().getThread().getData(this, uuid);
	}

	public int getInt(String key) {
		return getInt(key, 0);
	}

	public int getInt(String key, int def) {
		if (!key.equals("")) {
			if (AdvancedCoreHook.getInstance().getStorageType().equals(UserStorage.SQLITE)) {
				List<Column> row = getSQLiteRow();
				if (row != null) {
					for (int i = 0; i < row.size(); i++) {
						if (row.get(i).getName().equals(key)) {
							try {
								return (int) row.get(i).getValue();
							} catch (ClassCastException | NullPointerException ex) {
								try {
									return Integer.parseInt((String) row.get(i).getValue());
								} catch (Exception e) {
									// AdvancedCoreHook.getInstance().debug(e);
								}
							}
						}
					}
				}

			} else if (AdvancedCoreHook.getInstance().getStorageType().equals(UserStorage.MYSQL)) {
				List<Column> row = getMySqlRow();
				if (row != null) {
					for (int i = 0; i < row.size(); i++) {
						if (row.get(i).getName().equals(key)) {
							try {
								return (int) row.get(i).getValue();
							} catch (ClassCastException | NullPointerException ex) {
								try {
									return Integer.parseInt((String) row.get(i).getValue());
								} catch (Exception e) {
									// AdvancedCoreHook.getInstance().debug(e);
								}
							}
						}
					}
				}
			} else if (AdvancedCoreHook.getInstance().getStorageType().equals(UserStorage.FLAT)) {
				try {
					return getData(user.getUUID()).getInt(key, def);
				} catch (Exception e) {

				}

			}

		}

		if (AdvancedCoreHook.getInstance().isExtraDebug()) {
			AdvancedCoreHook.getInstance()
					.debug("Extra: Failed to get int from '" + key + "' for '" + user.getPlayerName() + "'");
		}
		return def;
	}

	public List<Column> getMySqlRow() {
		return AdvancedCoreHook.getInstance().getMysql().getExact(user.getUUID());
	}

	public List<Column> getSQLiteRow() {
		return AdvancedCoreHook.getInstance().getSQLiteUserTable()
				.getExact(new Column("uuid", user.getUUID(), DataType.STRING));
	}

	public String getString(String key) {
		if (!key.equals("")) {
			if (AdvancedCoreHook.getInstance().getStorageType().equals(UserStorage.SQLITE)) {
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

			} else if (AdvancedCoreHook.getInstance().getStorageType().equals(UserStorage.MYSQL)) {
				List<Column> row = getMySqlRow();
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
			} else if (AdvancedCoreHook.getInstance().getStorageType().equals(UserStorage.FLAT)) {
				try {
					return getData(user.getUUID()).getString(key, "");
				} catch (Exception e) {

				}
			}
		}
		if (AdvancedCoreHook.getInstance().isExtraDebug()) {
			AdvancedCoreHook.getInstance()
					.debug("Extra: Failed to get string from: '" + key + "' for '" + user.getPlayerName() + "'");
		}
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

	public void setData(final String uuid, final String path, final Object value) {
		FileThread.getInstance().getThread().setData(this, uuid, path, value);
	}

	public void setInt(final String key, final int value) {
		if (key.equals("")) {
			AdvancedCoreHook.getInstance().debug("No key: " + key + " to " + value);
			return;
		}
		if (AdvancedCoreHook.getInstance().isExtraDebug()) {
			AdvancedCoreHook.getInstance()
					.debug("Extra: Setting " + key + " to '" + value + "' for '" + user.getPlayerName() + "'");
		}
		if (AdvancedCoreHook.getInstance().getStorageType().equals(UserStorage.SQLITE)) {
			ArrayList<Column> columns = new ArrayList<Column>();
			Column primary = new Column("uuid", user.getUUID(), DataType.STRING);
			Column column = new Column(key, value, DataType.INTEGER);
			columns.add(primary);
			columns.add(column);
			AdvancedCoreHook.getInstance().getSQLiteUserTable().update(primary, columns);
		} else if (AdvancedCoreHook.getInstance().getStorageType().equals(UserStorage.MYSQL)) {
			AdvancedCoreHook.getInstance().getMysql().update(user.getUUID(), key, value, DataType.INTEGER);
		} else if (AdvancedCoreHook.getInstance().getStorageType().equals(UserStorage.FLAT)) {
			setData(user.getUUID(), key, value);
		}

	}

	public void setString(final String key, final String value) {
		if (key.equals("")) {
			AdvancedCoreHook.getInstance().debug("No key: " + key + " to " + value);
			return;
		}
		if (AdvancedCoreHook.getInstance().isExtraDebug()) {
			AdvancedCoreHook.getInstance()
					.debug("Extra: Setting " + key + " to '" + value + "' for '" + user.getPlayerName() + "'");
		}
		if (AdvancedCoreHook.getInstance().getStorageType().equals(UserStorage.SQLITE)) {
			ArrayList<Column> columns = new ArrayList<Column>();
			Column primary = new Column("uuid", user.getUUID(), DataType.STRING);
			Column column = new Column(key, value, DataType.STRING);
			columns.add(primary);
			columns.add(column);
			AdvancedCoreHook.getInstance().getSQLiteUserTable().update(primary, columns);
		} else if (AdvancedCoreHook.getInstance().getStorageType().equals(UserStorage.MYSQL)) {
			AdvancedCoreHook.getInstance().getMysql().update(user.getUUID(), key, value, DataType.STRING);
		} else if (AdvancedCoreHook.getInstance().getStorageType().equals(UserStorage.FLAT)) {
			setData(user.getUUID(), key, value);
		}
	}

	public void setStringList(final String key, final ArrayList<String> value) {
		// AdvancedCoreHook.getInstance().debug("Setting " + key + " to " +
		// value);
		String str = "";
		for (int i = 0; i < value.size(); i++) {
			if (i != 0) {
				str += "%line%";
			}
			str += value.get(i);
		}
		setString(key, str);

	}
}
