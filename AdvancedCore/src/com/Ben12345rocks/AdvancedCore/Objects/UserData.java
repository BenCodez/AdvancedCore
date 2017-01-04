package com.Ben12345rocks.AdvancedCore.Objects;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import com.Ben12345rocks.AdvancedCore.AdvancedCoreHook;
import com.Ben12345rocks.AdvancedCore.Util.Misc.ArrayUtils;
import com.Ben12345rocks.AdvancedCore.sql.Column;
import com.Ben12345rocks.AdvancedCore.sql.DataType;

public class UserData {
	private User user;

	public UserData(User user) {
		this.user = user;
	}

	public FileConfiguration getData(String uuid) {
		File dFile = AdvancedCoreHook.getInstance().getPlayerFile(uuid);
		FileConfiguration data = YamlConfiguration.loadConfiguration(dFile);
		return data;
	}

	public int getInt(String key) {
		if (AdvancedCoreHook.getInstance().getStorageType().equals(UserStorage.SQLITE)) {
			List<Column> row = getSQLiteRow();
			if (row != null) {
				for (int i = 0; i < row.size(); i++) {
					if (row.get(i).getName().equals(key)) {
						try {
							return (int) row.get(i).getValue();
						} catch (ClassCastException ex) {
							try {
								return Integer.parseInt((String) row.get(i).getValue());
							} catch (Exception e) {
								AdvancedCoreHook.getInstance().debug(e);
							}
						}
					}
				}
			}

		} else if (AdvancedCoreHook.getInstance().getStorageType().equals(UserStorage.FLAT)) {
			return getData(user.getUUID()).getInt(key, 0);
		}
		AdvancedCoreHook.getInstance().debug("Failed to get value: " + key);
		return 0;
	}

	public List<Column> getSQLiteRow() {
		if (AdvancedCoreHook.getInstance().getStorageType().equals(UserStorage.SQLITE)) {
			return AdvancedCoreHook.getInstance().getSQLiteUserTable()
					.getExact(new Column("uuid", user.getUUID(), DataType.STRING));
		}
		return null;
	}

	public String getString(String key) {
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

		} else if (AdvancedCoreHook.getInstance().getStorageType().equals(UserStorage.FLAT)) {
			return getData(user.getUUID()).getString(key, "");
		}
		AdvancedCoreHook.getInstance().debug("Failed to get value: " + key);
		return "";
	}

	public ArrayList<String> getStringList(String key) {
		String str = getString(key);
		if (str.equals("")) {
			return new ArrayList<String>();
		}
		String[] list = str.split(",");
		return ArrayUtils.getInstance().convert(list);
	}

	public void setData(final String uuid, final String path, final Object value) {
		File dFile = AdvancedCoreHook.getInstance().getPlayerFile(uuid);
		FileConfiguration data = YamlConfiguration.loadConfiguration(dFile);
		data.set(path, value);
		try {
			data.save(dFile);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void setInt(final String key, final int value) {

		AdvancedCoreHook.getInstance().debug("Setting " + key + " to " + value);
		if (AdvancedCoreHook.getInstance().getStorageType().equals(UserStorage.SQLITE)) {
			ArrayList<Column> columns = new ArrayList<Column>();
			Column primary = new Column("uuid", user.getUUID(), DataType.STRING);
			Column column = new Column(key, value, DataType.INTEGER);
			columns.add(primary);
			columns.add(column);
			AdvancedCoreHook.getInstance().getSQLiteUserTable().update(primary, columns);
		} else if (AdvancedCoreHook.getInstance().getStorageType().equals(UserStorage.FLAT)) {
			setData(user.getUUID(), key, value);
		}

	}

	public void setString(final String key, final String value) {

		AdvancedCoreHook.getInstance().debug("Setting " + key + " to " + value);
		if (AdvancedCoreHook.getInstance().getStorageType().equals(UserStorage.SQLITE)) {
			ArrayList<Column> columns = new ArrayList<Column>();
			Column primary = new Column("uuid", user.getUUID(), DataType.STRING);
			Column column = new Column(key, value, DataType.STRING);
			columns.add(primary);
			columns.add(column);
			AdvancedCoreHook.getInstance().getSQLiteUserTable().update(primary, columns);
		} else if (AdvancedCoreHook.getInstance().getStorageType().equals(UserStorage.FLAT)) {
			setData(user.getUUID(), key, value);
		}

	}

	public void setStringList(final String key, final ArrayList<String> value) {

		AdvancedCoreHook.getInstance().debug("Setting " + key + " to " + value);
		String str = "";
		for (int i = 0; i < value.size(); i++) {
			if (i != 0) {
				str += ",";
			}
			str += value.get(i);
		}
		setString(key, str);

	}
}
