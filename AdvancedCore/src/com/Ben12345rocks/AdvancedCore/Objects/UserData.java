package com.Ben12345rocks.AdvancedCore.Objects;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import com.Ben12345rocks.AdvancedCore.AdvancedCoreHook;
import com.Ben12345rocks.AdvancedCore.sql.Column;
import com.Ben12345rocks.AdvancedCore.sql.DataType;

public class UserData {
	private User user;
	private UserStorage storage;

	public UserData(User user, UserStorage storage) {
		this.user = user;
		this.storage = storage;
	}

	public FileConfiguration getData(String uuid) {
		File dFile = AdvancedCoreHook.getInstance().getPlayerFile(uuid);
		FileConfiguration data = YamlConfiguration.loadConfiguration(dFile);
		notify();
		return data;
	}

	public List<Column> getSQLiteRow() {
		if (storage.equals(UserStorage.SQLITE)) {
			return AdvancedCoreHook.getInstance().getSQLiteUserTable()
					.getExact(new Column("uuid", DataType.STRING, user.getUUID()));
		}
		return null;
	}

	public String getString(String key) {
		if (storage.equals(UserStorage.SQLITE)) {
			List<Column> row = getSQLiteRow();
			if (row != null) {
				for (int i = 0; i < row.size(); i++) {
					AdvancedCoreHook.getInstance().debug("Column: " + row.get(i).getName());
					if (row.get(i).getName().equals(key) && row.get(i).getDataType().equals(DataType.STRING)) {
						return (String) row.get(i).getValue();
					}
				}
			}

		} else if (storage.equals(UserStorage.FLAT)) {
			return getData(user.getUUID()).getString(key, "");
		}
		return "";
	}

	public void setData(String uuid, String path, Object value) {
		File dFile = AdvancedCoreHook.getInstance().getPlayerFile(uuid);
		FileConfiguration data = YamlConfiguration.loadConfiguration(dFile);
		data.set(path, value);
		try {
			data.save(dFile);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void setString(String key, String value) {
		if (storage.equals(UserStorage.SQLITE)) {
			ArrayList<Column> columns = new ArrayList<Column>();
			Column primary = new Column("uuid", DataType.STRING, user.getUUID());
			Column column = new Column(key, DataType.STRING, value);
			columns.add(primary);
			columns.add(column);
			AdvancedCoreHook.getInstance().getSQLiteUserTable().insert(columns);
		} else if (storage.equals(UserStorage.FLAT)) {
			setData(user.getUUID(), key, value);
		}
	}

}
