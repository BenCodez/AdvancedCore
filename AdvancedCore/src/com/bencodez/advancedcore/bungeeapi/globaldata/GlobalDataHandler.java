package com.bencodez.advancedcore.bungeeapi.globaldata;

import java.util.HashMap;

import com.bencodez.advancedcore.api.user.usercache.value.DataValue;
import com.bencodez.advancedcore.api.user.usercache.value.DataValueBoolean;
import com.bencodez.advancedcore.api.user.usercache.value.DataValueInt;
import com.bencodez.advancedcore.api.user.usercache.value.DataValueString;
import com.bencodez.advancedcore.api.user.userstorage.Column;

import lombok.Getter;

public class GlobalDataHandler {
	@Getter
	private GlobalMySQL globalMysql;

	public GlobalDataHandler(GlobalMySQL globalMysql) {
		this.globalMysql = globalMysql;
	}

	public void setBoolean(String server, String key, boolean data) {
		globalMysql.update(server, key, new DataValueBoolean(data));
	}

	public void setInt(String server, String key, int data) {
		globalMysql.update(server, key, new DataValueInt(data));
	}

	public void setString(String server, String key, String data) {
		globalMysql.update(server, key, new DataValueString(data));
	}

	public HashMap<String, DataValue> getExact(String server) {
		HashMap<String, DataValue> data = new HashMap<String, DataValue>();
		for (Column entry : globalMysql.getExact(server)) {
			data.put(entry.getName(), entry.getValue());
		}
		return data;
	}

	public boolean getBoolean(String server, String key) {
		HashMap<String, DataValue> data = getExact(server);
		if (data.containsKey(key)) {
			DataValue value = data.get(key);
			if (value.isBoolean()) {
				return value.getBoolean();
			}
			return Boolean.valueOf(value.getString()).booleanValue();
		}
		return false;
	}

	public int getInt(String server, String key) {
		HashMap<String, DataValue> data = getExact(server);
		if (data.containsKey(key)) {
			DataValue value = data.get(key);
			if (value.isInt()) {
				return value.getInt();
			}
		}
		return 0;
	}

	public String getString(String server, String key) {
		HashMap<String, DataValue> data = getExact(server);
		if (data.containsKey(key)) {
			DataValue value = data.get(key);
			if (value.isString()) {
				return value.getString();
			}
		}
		return "";
	}

}
