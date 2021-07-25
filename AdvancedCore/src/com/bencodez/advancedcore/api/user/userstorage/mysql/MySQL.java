package com.bencodez.advancedcore.api.user.userstorage.mysql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.configuration.ConfigurationSection;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.misc.ArrayUtils;
import com.bencodez.advancedcore.api.misc.PlayerUtils;
import com.bencodez.advancedcore.api.user.UserManager;
import com.bencodez.advancedcore.api.user.usercache.keys.UserDataKey;
import com.bencodez.advancedcore.api.user.userstorage.Column;
import com.bencodez.advancedcore.api.user.userstorage.DataType;
import com.bencodez.advancedcore.api.user.userstorage.mysql.api.queries.Query;

import lombok.Getter;

public class MySQL {
	private List<String> columns = Collections.synchronizedList(new ArrayList<String>());

	private List<String> intColumns;

	// private HashMap<String, ArrayList<Column>> table;

	@Getter
	private long lastBackgroundCheck = 0;

	// ConcurrentMap<String, ArrayList<Column>> table = new
	// ConcurrentHashMap<String, ArrayList<Column>>();

	private int maxSize = 0;

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
		intColumns = Collections.synchronizedList(plugin.getServerDataFile().getIntColumns());

		String tablePrefix = section.getString("Prefix");
		String hostName = section.getString("Host");
		int port = section.getInt("Port");
		String user = section.getString("Username");
		String pass = section.getString("Password");
		String database = section.getString("Database");
		long lifeTime = section.getLong("MaxLifeTime", -1);
		int maxThreads = section.getInt("MaxConnections", 1);
		if (maxThreads < 1) {
			maxThreads = 1;
		}
		boolean useSSL = section.getBoolean("UseSSL", false);
		boolean publicKeyRetrieval = section.getBoolean("PublicKeyRetrieval", false);
		this.maxSize = section.getInt("MaxSize", -1);
		if (!section.getString("Name", "").isEmpty()) {
			tableName = section.getString("Name", "");
		}

		String str = section.getString("Line", "");

		/*
		 * if (maxSize >= 0) { table =
		 * CompatibleCacheBuilder.newBuilder().concurrencyLevel(6).expireAfterAccess(20,
		 * TimeUnit.MINUTES) .maximumSize(maxSize).build(new CacheLoader<String,
		 * ArrayList<Column>>() {
		 * 
		 * @Override public ArrayList<Column> load(String key) { return
		 * getExactQuery(new Column("uuid", key, DataType.STRING)); } }); }
		 */

		name = tableName;
		if (tablePrefix != null) {
			name = tablePrefix + tableName;
		}
		mysql = new com.bencodez.advancedcore.api.user.userstorage.mysql.api.MySQL(maxThreads) {

			@Override
			public void debug(SQLException e) {
				plugin.debug(e);
			}

			@Override
			public void severe(String string) {
				plugin.getLogger().severe(string);
			}
		};
		if (!mysql.connect(hostName, "" + port, user, pass, database, useSSL, lifeTime, str, publicKeyRetrieval)) {
			plugin.getLogger().warning("Failed to connect to MySQL");
		}
		try {
			Query q = new Query(mysql, "USE " + database + ";");
			q.executeUpdate();
		} catch (SQLException e) {
			plugin.getLogger().severe("Failed to send use database query: " + database + " Error: " + e.getMessage()
					+ ", MySQL might still work");
			plugin.debug(e);
		}
		String sql = "CREATE TABLE IF NOT EXISTS " + getName() + " (";
		sql += "uuid VARCHAR(37), ";

		// add custom column types
		for (UserDataKey key : plugin.getUserManager().getDataManager().getKeys()) {
			sql += key.getKey() + " " + key.getColumnType() + ", ";
			if (key.getColumnType().equalsIgnoreCase("int")) {
				if (!intColumns.contains(key.getKey())) {
					intColumns.add(key.getKey());
					plugin.getServerDataFile().setIntColumns(intColumns);
				}
			}
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
			String sql = "ALTER TABLE " + getName() + " ADD COLUMN " + column + " text" + ";";

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
		plugin.debug("Altering column " + column + " to " + newType);
		if (newType.contains("INT")) {
			try {
				Query query = new Query(mysql, "UPDATE " + getName() + " SET " + column + " = '0' where trim(coalesce("
						+ column + ", '')) = '';");
				query.executeUpdateAsync();
			} catch (SQLException e) {
				e.printStackTrace();
			}
			if (!intColumns.contains(column)) {
				intColumns.add(column);
				plugin.getServerDataFile().setIntColumns(intColumns);
			}
		}
		Query query;
		try {
			query = new Query(mysql, "ALTER TABLE " + getName() + " MODIFY " + column + " " + newType + ";");
			query.executeUpdateAsync();
		} catch (SQLException e) {
			e.printStackTrace();
		}

	}

	public boolean checkBackgroundTask() {
		if ((System.currentTimeMillis() - lastBackgroundCheck) > 50000) {
			plugin.getLogger().severe("MySQL background task not working, fixing");
			return false;
		}
		return true;
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

	public boolean containsKeyQuery(String index) {
		String sqlStr = "SELECT uuid FROM " + getName() + ";";
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
		try {
			Query query = new Query(mysql, q);
			query.executeUpdate();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		uuids.remove(uuid);
		names.remove(PlayerUtils.getInstance()
				.getPlayerName(UserManager.getInstance().getUser(java.util.UUID.fromString(uuid)), uuid));
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
				PreparedStatement sql = conn.prepareStatement("SELECT * FROM " + getName() + ";")) {
			ResultSet rs = sql.executeQuery();
			/*
			 * Query query = new Query(mysql, "SELECT * FROM " + getName() + ";"); ResultSet
			 * rs = query.executeQuery();
			 */

			ResultSetMetaData metadata = rs.getMetaData();
			int columnCount = 0;
			if (metadata != null) {
				columnCount = metadata.getColumnCount();

				for (int i = 1; i <= columnCount; i++) {
					String columnName = metadata.getColumnName(i);
					columns.add(columnName);
				}
				sql.close();
				rs.close();
				conn.close();
				return columns;
			}
			rs.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return columns;
	}

	public ArrayList<Column> getExact(String uuid) {
		return getExactQuery(new Column("uuid", uuid, DataType.STRING));
	}

	public ArrayList<Column> getExactQuery(Column column) {
		ArrayList<Column> result = new ArrayList<>();
		String query = "SELECT * FROM " + getName() + " WHERE `" + column.getName() + "`='"
				+ column.getValue().toString() + "';";

		try (Connection conn = mysql.getConnectionManager().getConnection();
				PreparedStatement sql = conn.prepareStatement(query)) {
			ResultSet rs = sql.executeQuery();

			if (rs.next()) {
				for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
					String columnName = rs.getMetaData().getColumnLabel(i);
					Column rCol = null;
					if (intColumns.contains(columnName)) {
						rCol = new Column(columnName, DataType.INTEGER);
					} else {
						rCol = new Column(columnName, DataType.STRING);
					}
					// System.out.println(i + " " +
					// rs.getMetaData().getColumnLabel(i));
					rCol.setValue(rs.getString(i));
					// System.out.println(rCol.getValue());
					result.add(rCol);
				}
			}
			rs.close();
			return result;
		} catch (SQLException e) {
			e.printStackTrace();
		} catch (ArrayIndexOutOfBoundsException e) {
		}

		for (String col : getColumns()) {
			result.add(new Column(col, DataType.STRING));
		}
		return result;
	}

	public int getMaxSize() {
		return maxSize;
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

	public ArrayList<String> getNamesQuery() {
		ArrayList<String> uuids = new ArrayList<String>();

		checkColumn("PlayerName", DataType.STRING);
		ArrayList<Column> rows = getRowsNameQuery();
		if (rows != null) {
			for (Column c : rows) {
				if (c.getValue() != null) {
					uuids.add((String) c.getValue());
				}
			}
		}

		return uuids;
	}

	public ArrayList<Column> getRowsNameQuery() {
		ArrayList<Column> result = new ArrayList<Column>();
		String sqlStr = "SELECT PlayerName FROM " + getName() + ";";

		try (Connection conn = mysql.getConnectionManager().getConnection();
				PreparedStatement sql = conn.prepareStatement(sqlStr)) {
			ResultSet rs = sql.executeQuery();

			while (rs.next()) {
				Column rCol = new Column("PlayerName", rs.getString("PlayerName"), DataType.STRING);
				result.add(rCol);
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

		try (Connection conn = mysql.getConnectionManager().getConnection();
				PreparedStatement sql = conn.prepareStatement(sqlStr)) {

			ResultSet rs = sql.executeQuery();

			while (rs.next()) {
				Column rCol = new Column("uuid", rs.getString("uuid"), DataType.STRING);
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
				if (c.getValue() != null) {
					uuids.add((String) c.getValue());
				}
			}
		} else {
			plugin.getLogger().severe("Failed to fetch uuids");
		}

		return uuids;
	}

	public void insert(String index, String column, Object value, DataType dataType) {
		insertQuery(index, Arrays.asList(new Column(column, value, dataType)));

	}

	public void insertQuery(String index, List<Column> cols) {
		String query = "INSERT IGNORE " + getName() + " ";

		query += "set uuid='" + index + "', ";

		for (int i = 0; i < cols.size(); i++) {
			Column col = cols.get(i);
			if (i == cols.size() - 1) {
				query += col.getName() + "='" + col.getValue().toString() + "';";
			} else {
				query += col.getName() + "='" + col.getValue().toString() + "', ";
			}

		}

		try {
			new Query(mysql, query).executeUpdate();
			String playerName = "";
			for (Column col : cols) {
				if (col.getName().equalsIgnoreCase("playername")) {
					playerName = col.getValue().toString();
				}
			}
			if (playerName.isEmpty()) {
				names.add(PlayerUtils.getInstance().getPlayerName(
						UserManager.getInstance().getUser(java.util.UUID.fromString(index), false), index));
			} else {
				names.add(playerName);
			}
			uuids.add(index);
			plugin.debug("Inserting " + index + " into database");
		} catch (Exception e) {
			e.printStackTrace();
			plugin.debug("Failed to insert player " + index);
		}

	}

	public boolean isIntColumn(String key) {
		return intColumns.contains(key);
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
						if (col.getDataType().equals(DataType.STRING)) {
							query += col.getName() + "='" + col.getValue().toString() + "'";
						} else {
							query += col.getName() + "=" + col.getValue().toString();
						}
					} else {
						if (col.getDataType().equals(DataType.STRING)) {
							query += col.getName() + "='" + col.getValue().toString() + "', ";
						} else {
							query += col.getName() + "=" + col.getValue().toString() + ", ";
						}

					}
				}
				query += " WHERE 'uuid'=";
				query += "'" + index + "';";

				plugin.extraDebug("Batch query: " + query);

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

	public void update(String index, String column, Object value, DataType dataType) {
		if (value == null) {
			plugin.extraDebug("Mysql value null: " + column);
			return;
		}
		checkColumn(column, dataType);
		synchronized (object2) {
			if (getUuids().contains(index) || containsKeyQuery(index)) {
				String query = "UPDATE " + getName() + " SET ";

				if (dataType == DataType.STRING) {
					query += column + "='" + value.toString() + "'";
				} else {
					query += column + "=" + value;

				}
				query += " WHERE `uuid`=";
				query += "'" + index + "';";

				try {
					Query q = new Query(mysql, query);
					q.executeUpdateAsync();
				} catch (SQLException e) {
					e.printStackTrace();
				}

			} else {
				insert(index, column, value, dataType);
			}
		}

	}

	public void wipeColumnData(String columnName) {
		String sql = "UPDATE " + getName() + " SET " + columnName + " = NULL;";
		try {
			Query query = new Query(mysql, sql);
			query.executeUpdate();
		} catch (SQLException e) {
			e.printStackTrace();
		}

	}

	public boolean containsKey(String uuid) {
		if (uuids.contains(uuid) || containsKeyQuery(uuid)) {
			return true;
		}
		return false;
	}
}
