package com.bencodez.advancedcore.api.user.userstorage.mysql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.configuration.ConfigurationSection;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.messages.StringParser;
import com.bencodez.advancedcore.api.misc.ArrayUtils;
import com.bencodez.advancedcore.api.misc.PlayerManager;
import com.bencodez.advancedcore.api.user.usercache.keys.UserDataKey;
import com.bencodez.advancedcore.api.user.usercache.value.DataValue;
import com.bencodez.advancedcore.api.user.usercache.value.DataValueBoolean;
import com.bencodez.advancedcore.api.user.usercache.value.DataValueInt;
import com.bencodez.advancedcore.api.user.usercache.value.DataValueString;
import com.bencodez.advancedcore.api.user.userstorage.Column;
import com.bencodez.advancedcore.api.user.userstorage.DataType;
import com.bencodez.advancedcore.api.user.userstorage.mysql.api.config.MysqlConfigSpigot;
import com.bencodez.advancedcore.api.user.userstorage.mysql.api.queries.Query;

import lombok.Getter;

public class MySQL {
	private List<String> columns = Collections.synchronizedList(new ArrayList<String>());

	// private List<String> intColumns;

	// private HashMap<String, ArrayList<Column>> table;

	@Getter
	private long lastBackgroundCheck = 0;

	// ConcurrentMap<String, ArrayList<Column>> table = new
	// ConcurrentHashMap<String, ArrayList<Column>>();

	@Getter
	private com.bencodez.advancedcore.api.user.userstorage.mysql.api.MySQL mysql;

	private String name;

	private Set<String> names = ConcurrentHashMap.newKeySet();

	private Object object2 = new Object();

	private Object object3 = new Object();

	private Object object4 = new Object();

	private AdvancedCorePlugin plugin;

	private boolean useBatchUpdates = true;

	private Set<String> uuids = ConcurrentHashMap.newKeySet();

	public MySQL(AdvancedCorePlugin plugin, String tableName, ConfigurationSection section) {
		this.plugin = plugin;

		MysqlConfigSpigot config = new MysqlConfigSpigot(section);

		if (config.hasTableNameSet()) {
			tableName = config.getTableName();
		}
		name = tableName;
		if (config.getTablePrefix() != null) {
			name = config.getTablePrefix() + tableName;
		}
		mysql = new com.bencodez.advancedcore.api.user.userstorage.mysql.api.MySQL(config.getMaxThreads()) {

			@Override
			public void debug(SQLException e) {
				plugin.debug(e);
			}

			@Override
			public void severe(String string) {
				plugin.getLogger().severe(string);
			}
		};
		if (!mysql.connect(config)) {
			plugin.getLogger().warning("Failed to connect to MySQL");
		}
		try {
			Query q = new Query(mysql, "USE `" + config.getDatabase() + "`;");
			q.executeUpdate();
		} catch (SQLException e) {
			plugin.getLogger().severe("Failed to send use database query: " + config.getDatabase() + " Error: "
					+ e.getMessage() + ", MySQL might still work");
			plugin.debug(e);
		}
		String sql = "CREATE TABLE IF NOT EXISTS " + getName() + " (";
		sql += "uuid VARCHAR(37), ";

		// add custom column types
		for (UserDataKey key : plugin.getUserManager().getDataManager().getKeys()) {
			sql += "`" + key.getKey() + "` " + key.getColumnType() + ", ";
		}
		sql += "PRIMARY KEY ( uuid ));";

		try {
			Query query = new Query(mysql, sql);

			query.executeUpdate();
		} catch (SQLException e) {
			e.printStackTrace();
		}

		loadData();

		plugin.debug("UseBatchUpdates: " + isUseBatchUpdates());
	}

	public void addColumn(String column, DataType dataType) {
		synchronized (object3) {
			String sql = "ALTER TABLE " + getName() + " ADD COLUMN `" + column + "` text" + ";";

			plugin.debug("Adding column: " + column + " Current columns: "
					+ ArrayUtils.getInstance().makeStringList((ArrayList<String>) getColumns()));
			try {
				Query query = new Query(mysql, sql);
				query.executeUpdate();

				getColumns().add(column);
			} catch (Exception e) {
				e.printStackTrace();
			}

		}
	}

	public void alterColumnType(final String column, final String newType) {
		checkColumn(column, DataType.STRING);
		plugin.debug("MYSQL QUERY: Altering column `" + column + "` to " + newType);
		if (newType.contains("INT")) {
			try {
				Query query = new Query(mysql, "UPDATE " + getName() + " SET `" + column
						+ "` = '0' where trim(coalesce(" + column + ", '')) = '';");
				query.executeUpdateAsync();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		} else {
			Query query;
			try {
				query = new Query(mysql, "ALTER TABLE " + getName() + " MODIFY `" + column + "` " + newType + ";");
				query.executeUpdateAsync();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}

	}

	public void checkColumn(String column, DataType dataType) {
		synchronized (object4) {
			if (!ArrayUtils.getInstance().containsIgnoreCase((ArrayList<String>) getColumns(), column)) {
				if (!ArrayUtils.getInstance().containsIgnoreCase(getColumnsQueury(), column)) {
					addColumn(column, dataType);
				}
			}
		}
	}

	public void clearCacheBasic() {
		plugin.debug("Clearing cache basic");
		columns.clear();
		columns.addAll(getColumnsQueury());
		uuids.clear();
		uuids.addAll(getUuidsQuery());
		names.clear();
		names.addAll(getNamesQuery());
	}

	public void close() {
		mysql.disconnect();
	}

	public boolean containsKey(String uuid) {
		if (getUuids().contains(uuid) || containsKeyQuery(uuid)) {
			return true;
		}
		return false;
	}

	public boolean containsKeyQuery(String index) {
		String sqlStr = "SELECT uuid FROM " + getName() + ";";
		plugin.devDebug("MYSQL QUERY: " + sqlStr);
		try (Connection conn = mysql.getConnectionManager().getConnection();
				PreparedStatement sql = conn.prepareStatement(sqlStr)) {
			ResultSet rs = sql.executeQuery();
			/*
			 * Query query = new Query(mysql, sql); ResultSet rs = query.executeQuery();
			 */
			while (rs.next()) {
				if (rs.getString("uuid").equals(index)) {
					rs.close();
					return true;
				}
			}
			rs.close();

		} catch (SQLException ex) {
			ex.printStackTrace();
		}
		return false;
	}

	public boolean containsUUID(String uuid) {
		if (uuids.contains(uuid)) {
			return true;
		}
		return false;
	}

	public void deletePlayer(String uuid) {
		String q = "DELETE FROM " + getName() + " WHERE uuid='" + uuid + "';";
		plugin.devDebug("MYSQL QUERY: " + q);
		try {
			Query query = new Query(mysql, q);
			query.executeUpdate();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		uuids.remove(uuid);
		names.remove(PlayerManager.getInstance()
				.getPlayerName(plugin.getUserManager().getUser(java.util.UUID.fromString(uuid)), uuid));
		clearCacheBasic();

	}

	public List<String> getColumns() {
		if (columns == null || columns.size() == 0) {
			loadData();
		}
		return columns;
	}

	public ArrayList<String> getColumnsQueury() {
		ArrayList<String> columns = new ArrayList<String>();
		try (Connection conn = mysql.getConnectionManager().getConnection();
				PreparedStatement sql = conn.prepareStatement("SHOW COLUMNS FROM `" + getName() + "`;")) {
			plugin.devDebug("MYSQL QUERY: " + "SHOW COLUMNS FROM `" + getName() + "`;");
			ResultSet rs = sql.executeQuery();

			while (rs.next()) {
				String columnName = rs.getString(1);
				columns.add(columnName);

			}

			// plugin.devDebug("MYSQL QUERY COLUMNS: " +
			// ArrayUtils.getInstance().makeStringList(columns));
			rs.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return columns;
	}

	public ArrayList<Column> getExact(String uuid) {
		return getExactQuery(new Column("uuid", new DataValueString(uuid)));
	}

	public ArrayList<Column> getExactQuery(Column column) {
		ArrayList<Column> result = new ArrayList<>();
		String query = "SELECT * FROM " + getName() + " WHERE `" + column.getName() + "`='"
				+ column.getValue().getString() + "';";
		plugin.devDebug("MYSQL QUERY: " + query);

		try (Connection conn = mysql.getConnectionManager().getConnection();
				PreparedStatement sql = conn.prepareStatement(query)) {
			ResultSet rs = sql.executeQuery();

			if (rs.next()) {
				for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
					String columnName = rs.getMetaData().getColumnLabel(i);
					Column rCol = null;
					if (plugin.getUserManager().getDataManager().isInt(columnName)) {
						try {
							rCol = new Column(columnName, DataType.INTEGER);
							rCol.setValue(new DataValueInt(rs.getInt(i)));
						} catch (Exception e) {
							rCol = new Column(columnName, DataType.INTEGER);
							String data = rs.getString(i);
							if (data != null) {
								try {
									rCol.setValue(new DataValueInt(Integer.parseInt(data)));
								} catch (NumberFormatException ex) {
									rCol.setValue(new DataValueInt(0));
								}
							} else {
								rCol.setValue(new DataValueInt(0));
							}
						}
					} else if (plugin.getUserManager().getDataManager().isBoolean(columnName)) {
						rCol = new Column(columnName, DataType.BOOLEAN);
						rCol.setValue(new DataValueBoolean(Boolean.valueOf(rs.getString(i))));
					} else {
						rCol = new Column(columnName, DataType.STRING);
						rCol.setValue(new DataValueString(rs.getString(i)));
					}
					result.add(rCol);
				}
			}
			rs.close();
			return result;
		} catch (SQLException e) {
			e.printStackTrace();
		} catch (ArrayIndexOutOfBoundsException e) {
			e.printStackTrace();
		}

		for (String col : getColumns()) {
			result.add(new Column(col, DataType.STRING));
		}
		return result;
	}

	public HashMap<UUID, ArrayList<Column>> getAllQuery() {
		HashMap<UUID, ArrayList<Column>> result = new HashMap<UUID, ArrayList<Column>>();
		String query = "SELECT * FROM " + getName() + ";";
		plugin.devDebug("MYSQL QUERY: " + query);

		try (Connection conn = mysql.getConnectionManager().getConnection();
				PreparedStatement sql = conn.prepareStatement(query)) {
			ResultSet rs = sql.executeQuery();

			while (rs.next()) {
				ArrayList<Column> cols = new ArrayList<Column>();
				UUID uuid = null;
				for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
					String columnName = rs.getMetaData().getColumnLabel(i);
					Column rCol = null;

					if (plugin.getUserManager().getDataManager().isInt(columnName)) {
						try {
							rCol = new Column(columnName, DataType.INTEGER);
							rCol.setValue(new DataValueInt(rs.getInt(i)));
						} catch (Exception e) {
							rCol = new Column(columnName, DataType.INTEGER);
							String data = rs.getString(i);
							if (data != null) {
								try {
									rCol.setValue(new DataValueInt(Integer.parseInt(data)));
								} catch (NumberFormatException ex) {
									rCol.setValue(new DataValueInt(0));
								}
							} else {
								rCol.setValue(new DataValueInt(0));
							}
						}
					} else if (plugin.getUserManager().getDataManager().isBoolean(columnName)) {
						rCol = new Column(columnName, DataType.BOOLEAN);
						rCol.setValue(new DataValueBoolean(Boolean.valueOf(rs.getString(i))));
					} else {
						rCol = new Column(columnName, DataType.STRING);
						rCol.setValue(new DataValueString(rs.getString(i)));
						if (columnName.equals("uuid")) {
							uuid = UUID.fromString(rs.getString(i));
						}
					}
					cols.add(rCol);
				}
				result.put(uuid, cols);
			}
			rs.close();
			return result;
		} catch (SQLException e) {
			e.printStackTrace();
		} catch (ArrayIndexOutOfBoundsException e) {
			e.printStackTrace();
		}

		return result;
	}

	public String getName() {
		return name;
	}

	public Set<String> getNames() {
		if (names == null || names.size() == 0) {
			names.clear();
			names.addAll(getNamesQuery());
			return names;
		}
		return names;
	}

	public String getUUID(String playerName) {
		String query = "SELECT uuid FROM " + getName() + " WHERE " + "PlayerName" + "='" + playerName + "';";
		plugin.devDebug("MYSQL QUERY: " + query);
		try (Connection conn = mysql.getConnectionManager().getConnection();
				PreparedStatement sql = conn.prepareStatement(query)) {
			ResultSet rs = sql.executeQuery();
			/*
			 * Query sql = new Query(mysql, query); ResultSet rs = sql.executeQuery();
			 */
			if (rs.next()) {
				String uuid = rs.getString("uuid");
				if (uuid != null && !uuid.isEmpty()) {
					rs.close();
					return uuid;
				}
			}
			rs.close();
		} catch (SQLException e) {
			e.printStackTrace();
		} catch (ArrayIndexOutOfBoundsException e) {
		}
		return null;
	}

	public ConcurrentHashMap<UUID, String> getRowsUUIDNameQuery() {
		ConcurrentHashMap<UUID, String> uuidNames = new ConcurrentHashMap<UUID, String>();
		String sqlStr = "SELECT UUID, PlayerName FROM " + getName() + ";";
		plugin.devDebug("MYSQL QUERY: " + sqlStr);
		try (Connection conn = mysql.getConnectionManager().getConnection();
				PreparedStatement sql = conn.prepareStatement(sqlStr)) {
			ResultSet rs = sql.executeQuery();
			/*
			 * Query query = new Query(mysql, sql); ResultSet rs = query.executeQuery();
			 */

			while (rs.next()) {
				String uuid = rs.getString("uuid");
				String playerName = rs.getString("PlayerName");
				if (uuid != null && !uuid.isEmpty() && !uuid.equals("null") && playerName != null
						&& !playerName.isEmpty()) {
					uuidNames.put(UUID.fromString(uuid), playerName);
				}
			}
			sql.close();
			conn.close();
		} catch (SQLException e) {
		}

		return uuidNames;
	}

	public ArrayList<String> getNamesQuery() {
		ArrayList<String> uuids = new ArrayList<String>();

		checkColumn("PlayerName", DataType.STRING);
		ArrayList<Column> rows = getRowsNameQuery();
		if (rows != null) {
			for (Column c : rows) {
				if (c.getValue() != null && c.getValue().isString()) {
					String value = c.getValue().getString();
					if (value != null) {
						uuids.add(value);
					}
				}
			}
		}

		return uuids;
	}

	public ArrayList<Column> getRowsNameQuery() {
		ArrayList<Column> result = new ArrayList<Column>();
		String sqlStr = "SELECT PlayerName FROM " + getName() + ";";
		plugin.devDebug("MYSQL QUERY: " + sqlStr);

		try (Connection conn = mysql.getConnectionManager().getConnection();
				PreparedStatement sql = conn.prepareStatement(sqlStr)) {
			ResultSet rs = sql.executeQuery();

			while (rs.next()) {
				Column rCol = new Column("PlayerName", new DataValueString(rs.getString("PlayerName")));
				result.add(rCol);
			}
			rs.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}

		return result;
	}

	public ArrayList<Integer> getNumbersInColumn(String column) {
		ArrayList<Integer> result = new ArrayList<Integer>();
		String sqlStr = "SELECT " + column + " FROM " + getName() + ";";
		plugin.devDebug("MYSQL QUERY: " + sqlStr);

		try (Connection conn = mysql.getConnectionManager().getConnection();
				PreparedStatement sql = conn.prepareStatement(sqlStr)) {
			ResultSet rs = sql.executeQuery();

			while (rs.next()) {
				result.add(rs.getInt(column));
			}
			rs.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}

		return result;
	}

	public ArrayList<Column> getRowsQuery() {
		ArrayList<Column> result = new ArrayList<Column>();
		String sqlStr = "SELECT uuid FROM " + getName() + ";";
		plugin.devDebug("MYSQL QUERY: " + sqlStr);

		try (Connection conn = mysql.getConnectionManager().getConnection();
				PreparedStatement sql = conn.prepareStatement(sqlStr)) {

			ResultSet rs = sql.executeQuery();

			while (rs.next()) {
				Column rCol = new Column("uuid", new DataValueString(rs.getString("uuid")));
				result.add(rCol);
			}
			rs.close();
		} catch (SQLException e) {
			e.printStackTrace();
			return null;
		}

		return result;
	}

	public Set<String> getUuids() {
		if (uuids == null || uuids.size() == 0) {
			uuids.clear();
			uuids.addAll(getUuidsQuery());
			return uuids;
		}
		return uuids;
	}

	public ArrayList<String> getUuidsQuery() {
		ArrayList<String> uuids = new ArrayList<String>();

		ArrayList<Column> rows = getRowsQuery();
		if (rows != null) {
			for (Column c : rows) {
				if (c.getValue() != null && c.getValue().isString()) {
					uuids.add(c.getValue().getString());
				}
			}
		} else {
			plugin.getLogger().severe("Failed to fetch uuids");
		}

		return uuids;
	}

	public void insert(String index, String column, DataValue value) {
		insertQuery(index, Arrays.asList(new Column(column, value)));

	}

	public void insertQuery(String index, List<Column> cols) {
		String query = "INSERT IGNORE " + getName() + " ";

		query += "set uuid='" + index + "', ";

		for (int i = 0; i < cols.size(); i++) {
			Column col = cols.get(i);
			if (i == cols.size() - 1) {
				if (col.getValue().isString()) {
					query += col.getName() + "='" + col.getValue().getString() + "';";
				} else if (col.getValue().isBoolean()) {
					query += col.getName() + "='" + col.getValue().getBoolean() + "';";
				} else if (col.getValue().isInt()) {
					query += col.getName() + "='" + col.getValue().getInt() + "';";
				}
			} else {
				if (col.getValue().isString()) {
					query += col.getName() + "='" + col.getValue().getString() + "', ";
				} else if (col.getValue().isBoolean()) {
					query += col.getName() + "='" + col.getValue().getBoolean() + "', ";
				} else if (col.getValue().isInt()) {
					query += col.getName() + "='" + col.getValue().getInt() + "', ";
				}
			}
		}

		plugin.devDebug("MYSQL QUERY: " + query);

		try {
			new Query(mysql, query).executeUpdate();
			String playerName = "";
			for (Column col : cols) {
				if (col.getName().equalsIgnoreCase("playername")) {
					playerName = col.getValue().toString();
				}
			}
			if (playerName == null || playerName.isEmpty()) {
				names.add(PlayerManager.getInstance().getPlayerName(
						plugin.getUserManager().getUser(java.util.UUID.fromString(index), false), index));
			} else {
				names.add(playerName);
			}
			uuids.add(index);
			plugin.devDebug("Inserting " + index + " into database");
		} catch (Exception e) {
			e.printStackTrace();
			plugin.debug("Failed to insert player " + index);
		}

	}

	public boolean isUseBatchUpdates() {
		return useBatchUpdates;
	}

	public void loadData() {
		columns = getColumnsQueury();

		try (Connection con = mysql.getConnectionManager().getConnection()) {
			useBatchUpdates = con.getMetaData().supportsBatchUpdates();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public void update(String index, List<Column> cols, boolean runAsync) {
		for (Column col : cols) {
			checkColumn(col.getName(), col.getDataType());
		}
		synchronized (object2) {
			if (getUuids().contains(index) || containsKeyQuery(index)) {

				String query = "UPDATE " + getName() + " SET ";

				for (int i = 0; i < cols.size(); i++) {
					Column col = cols.get(i);
					if (i == cols.size() - 1) {
						if (col.getValue().isString()) {
							query += "`" + col.getName() + "`='" + col.getValue().getString() + "'";
						} else if (col.getValue().isBoolean()) {
							query += "`" + col.getName() + "`='" + col.getValue().getBoolean() + "'";
						} else if (col.getValue().isInt()) {
							query += "`" + col.getName() + "`='" + col.getValue().getInt() + "'";
						}
					} else {
						if (col.getValue().isString()) {
							query += "`" + col.getName() + "`='" + col.getValue().getString() + "', ";
						} else if (col.getValue().isBoolean()) {
							query += "`" + col.getName() + "`='" + col.getValue().getBoolean() + "', ";
						} else if (col.getValue().isInt()) {
							query += "`" + col.getName() + "`='" + col.getValue().getInt() + "', ";
						}
					}
				}
				query += " WHERE uuid=";
				query += "'" + index + "';";

				plugin.devDebug("MYSQL QUERY: " + query);

				try {
					Query q = new Query(mysql, query);
					if (runAsync) {
						q.executeUpdateAsync();
					} else {
						q.executeUpdate();
					}
				} catch (SQLException e) {
					e.printStackTrace();
				}
			} else {
				insertQuery(index, cols);
			}
		}
	}

	public void update(String index, String column, DataValue value) {
		if (value == null) {
			plugin.extraDebug("Mysql value null: " + column);
			return;
		}
		checkColumn(column, value.getType());
		synchronized (object2) {
			if (getUuids().contains(index) || containsKeyQuery(index)) {
				String query = "UPDATE " + getName() + " SET ";

				if (value.isString()) {
					query += column + "='" + value.getString() + "'";
				} else if (value.isBoolean()) {
					query += column + "='" + value.getBoolean() + "'";
				} else if (value.isInt()) {
					query += column + "='" + value.getInt() + "'";
				}
				query += " WHERE uuid=";
				query += "'" + index + "';";

				plugin.devDebug("MYSQL QUERY: " + query);
				try {
					Query q = new Query(mysql, query);
					q.executeUpdate();
				} catch (SQLException e) {
					e.printStackTrace();
				}

			} else {
				insert(index, column, value);
			}
		}

	}

	public void wipeColumnData(String columnName) {
		checkColumn(columnName, DataType.STRING);
		String sql = "UPDATE " + getName() + " SET " + columnName + " = NULL;";
		plugin.devDebug("MYSQL QUERY: " + sql);
		try {
			Query query = new Query(mysql, sql);
			query.executeUpdate();
		} catch (SQLException e) {
			e.printStackTrace();
		}

	}

	public void copyColumnData(String columnFromName, String columnToName) {
		checkColumn(columnFromName, DataType.STRING);
		checkColumn(columnToName, DataType.STRING);
		String sql = "UPDATE `" + getName() + "` SET `" + columnToName + "` = `" + columnFromName + "`;";
		plugin.devDebug("MYSQL QUERY: " + sql);
		try {
			Query query = new Query(mysql, sql);
			query.executeUpdate();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public void executeQuery(String str) {
		try {
			Query q = new Query(mysql, StringParser.getInstance().replacePlaceHolder(str, "tablename", getName()));
			plugin.devDebug("MYSQL QUERY: " + str);
			q.executeUpdate();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public void executeQueryReturn(String str) {
		try (Connection conn = mysql.getConnectionManager().getConnection();
				PreparedStatement sql = conn.prepareStatement(str)) {
			plugin.devDebug("MYSQL QUERY: " + str);
			ResultSet rs = sql.executeQuery();

			rs.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
}
