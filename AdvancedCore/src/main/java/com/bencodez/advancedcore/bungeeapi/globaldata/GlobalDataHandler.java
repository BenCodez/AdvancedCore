package com.bencodez.advancedcore.bungeeapi.globaldata;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;

import com.bencodez.simpleapi.sql.Column;
import com.bencodez.simpleapi.sql.data.DataValue;
import com.bencodez.simpleapi.sql.data.DataValueBoolean;
import com.bencodez.simpleapi.sql.data.DataValueInt;
import com.bencodez.simpleapi.sql.data.DataValueString;

import lombok.Getter;

/**
 * Handler for global data across multiple servers.
 */
public class GlobalDataHandler {
	/**
	 * @return the global MySQL instance
	 */
	@Getter
	private GlobalMySQL globalMysql;

	/**
	 * Constructor for GlobalDataHandler.
	 *
	 * @param globalMysql the global MySQL instance
	 */
	public GlobalDataHandler(GlobalMySQL globalMysql) {
		this.globalMysql = globalMysql;
	}

	/**
	 * Gets a boolean value from global data.
	 *
	 * @param server the server name
	 * @param key the key
	 * @return the boolean value
	 */
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

	/**
	 * Gets exact data for a server.
	 *
	 * @param server the server name
	 * @return the data map
	 */
	public HashMap<String, DataValue> getExact(String server) {
		HashMap<String, DataValue> data = new HashMap<>();
		for (Column entry : globalMysql.getExact(server)) {
			data.put(entry.getName(), entry.getValue());
		}
		return data;
	}

	/**
	 * Gets an integer value from global data.
	 *
	 * @param server the server name
	 * @param key the key
	 * @return the integer value
	 */
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

	/**
	 * Gets a string value from global data.
	 *
	 * @param server the server name
	 * @param key the key
	 * @return the string value
	 */
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

	/**
	 * Sets a boolean value in global data.
	 *
	 * @param server the server name
	 * @param key the key
	 * @param data the boolean value
	 */
	public void setBoolean(String server, String key, boolean data) {
		globalMysql.update(server, key, new DataValueBoolean(data));
	}

	/**
	 * Sets multiple data values in global data.
	 *
	 * @param server the server name
	 * @param data the data map
	 */
	public void setData(String server, HashMap<String, DataValue> data) {
		ArrayList<Column> cols = new ArrayList<>();
		for (Entry<String, DataValue> entry : data.entrySet()) {
			cols.add(new Column(entry.getKey(), entry.getValue()));
		}
		globalMysql.update(server, cols, false);
	}

	/**
	 * Sets an integer value in global data.
	 *
	 * @param server the server name
	 * @param key the key
	 * @param data the integer value
	 */
	public void setInt(String server, String key, int data) {
		globalMysql.update(server, key, new DataValueInt(data));
	}

	/**
	 * Sets a string value in global data.
	 *
	 * @param server the server name
	 * @param key the key
	 * @param data the string value
	 */
	public void setString(String server, String key, String data) {
		globalMysql.update(server, key, new DataValueString(data));
	}

}
