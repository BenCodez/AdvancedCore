package com.bencodez.advancedcore.bungeeapi.mysql;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.bencodez.advancedcore.api.user.usercache.value.DataValue;
import com.bencodez.advancedcore.api.user.userstorage.Column;
import com.bencodez.advancedcore.api.user.userstorage.DataType;

public interface ProxyMySQL {
	void addColumn(String column, DataType dataType);

	void alterColumnType(String column, String newType);

	void checkColumn(String column, DataType dataType);

	void clearCache();

	void close();
	
	com.bencodez.advancedcore.api.user.userstorage.mysql.api.MySQL getMysql();

	boolean containsKeyQuery(String index);

	void copyColumnData(String columnFromName, String columnToName);

	void debug(SQLException e);

	List<String> getColumns();

	ArrayList<String> getColumnsQueury();

	ArrayList<Column> getExactQuery(Column column);

	String getName();

	ArrayList<String> getNamesQuery();

	ArrayList<Column> getRowsNameQuery();

	ArrayList<Column> getRowsQuery();

	ConcurrentHashMap<UUID, String> getRowsUUIDNameQuery();

	String getUUID(String playerName);

	Set<String> getUuids();

	ArrayList<String> getUuidsQuery();

	void insert(String index, String column, DataValue value);

	void insertQuery(String index, List<Column> cols);

	boolean isIntColumn(String key);

	void loadData();

	void update(String index, List<Column> cols);

	void update(String index, String column, DataValue value);

	void wipeColumnData(String columnName, DataType dataType);

	void shutdown();
}
