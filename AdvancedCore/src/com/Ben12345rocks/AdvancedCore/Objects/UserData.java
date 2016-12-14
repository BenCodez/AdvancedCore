package com.Ben12345rocks.AdvancedCore.Objects;

import java.util.HashMap;

import org.bukkit.configuration.file.FileConfiguration;

import com.Ben12345rocks.AdvancedCore.AdvancedCoreHook;
import com.Ben12345rocks.AdvancedCore.Data.Data;

public class UserData {
	private User user;
	private UserStorage storage;
	private HashMap<String, String> data;

	public UserData(User user, UserStorage storage) {
		this.user = user;
		this.storage = storage;
	}

	public void loadData() {
		data = new HashMap<String, String>();
		if (AdvancedCoreHook.getInstance().isPreloadData()) {
			if (storage.equals(UserStorage.SQLITE)) {
				data.putAll(AdvancedCoreHook.getInstance().getSql().getSet(user.getUUID()));
			} else if (storage.equals(UserStorage.FLAT)) {
				FileConfiguration file = Data.getInstance().getData(user.getUUID());
				for (String key : file.getConfigurationSection("").getKeys(false)) {
					data.put(key, file.getString(key, ""));
				}
			}
			if (data.size() == 0) {
				data.put("uuid", user.getUUID().replace("-", "_"));
				data.put("playername", user.getPlayerName());
			}
		}
	}

	public void saveData() {
		if (storage.equals(UserStorage.SQLITE)) {
			AdvancedCoreHook.getInstance().getSql().setData(user, data);
		} else if (storage.equals(UserStorage.FLAT)) {
			for (String d : data.keySet()) {
				Data.getInstance().set(user.getUUID(), d, data.get(d));
			}
		}
	}

	public synchronized String getData(String key) {
		if (AdvancedCoreHook.getInstance().isPreloadData()) {
			if (data.containsKey(key)) {
				return data.get(key);
			} else {
				return "";
			}
		} else {
			if (storage.equals(UserStorage.SQLITE)) {
				return AdvancedCoreHook.getInstance().getSql().getString(user.getUUID(), key);
			} else if (storage.equals(UserStorage.FLAT)) {
				return Data.getInstance().getData(user.getUUID()).getString(key);
			}
		}
		return "";
	}

	public synchronized void setData(String key, String value) {
		if (AdvancedCoreHook.getInstance().isPreloadData()) {
			data.put(key, value);
		} else {
			if (storage.equals(UserStorage.SQLITE)) {
				data = new HashMap<String, String>();
				data.put("uuid", user.getUUID().replace("-", "_"));
				data.put("playername", user.getPlayerName());
				data.put(key, value);
				AdvancedCoreHook.getInstance().getSql().setData(user, data);
			} else if (storage.equals(UserStorage.FLAT)) {
				Data.getInstance().getData(user.getUUID()).set(key, value);
			}
		}
	}
}
