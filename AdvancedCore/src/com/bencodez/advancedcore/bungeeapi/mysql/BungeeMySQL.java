package com.bencodez.advancedcore.bungeeapi.mysql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.bencodez.advancedcore.api.user.usercache.value.DataValue;
import com.bencodez.advancedcore.api.user.usercache.value.DataValueInt;
import com.bencodez.advancedcore.api.user.usercache.value.DataValueString;
import com.bencodez.advancedcore.api.user.userstorage.Column;
import com.bencodez.advancedcore.api.user.userstorage.DataType;
import com.bencodez.advancedcore.api.user.userstorage.mysql.api.queries.Query;

import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;

public abstract class BungeeMySQL {
	private List<String> columns = Collections.synchronizedList(new ArrayList<String>());

	private List<String> intColumns = new ArrayList<String>();

	// private HashMap<String, ArrayList<Column>> table;

	// ConcurrentMap<String, ArrayList<Column>> table = new
	// ConcurrentHashMap<String, ArrayList<Column>>();

	private com.bencodez.advancedcore.api.user.userstorage.mysql.api.MySQL mysql;

	private String name;

	private Object object2 = new Object();

	private Object object3 = new Object();

	private Object object4 = new Object();

	private Set<String> uuids = Collections.synchronizedSet(new HashSet<String>());

	public BungeeMySQL(Plugin bungee, String tableName, Configuration section) {
		String tablePrefix = section.getString("Prefix");
		String hostName = section.getString("Host");
		int port = section.getInt("Port");
		String user = section.getString("Username");
		String pass = section.getString("Password");
		String database = section.getString("Database");
		long lifeTime = section.getLong("MaxLifeTime", -1);
		int maxThreads = section.getInt("MaxConnections", 1);
		String str = section.getString("Line", "");
		if (maxThreads < 1) {
			maxThreads = 1;
		}
		boolean useSSL = section.getBoolean("UseSSL", false);
		boolean publicKeyRetrieval = section.getBoolean("PublicKeyRetrieval", false);
		boolean useMariaDB = section.getBoolean("UseMariaDB", false);
		if (!section.getString("Name", "").isEmpty()) {
			tableName = section.getString("Name", "");
		}

		name = tableName;
		if (tablePrefix != null) {
			name = tablePrefix + tableName;
		}
		mysql = new com.bencodez.advancedcore.api.user.userstorage.mysql.api.MySQL(maxThreads) {

			@Override
			public void debug(SQLException e) {
				debug(e);
			}

			@Override
			public void severe(String string) {
				bungee.getLogger().severe(string);
			}
		};
		if (!mysql.connect(hostName, "" + port, user, pass, database, useSSL, lifeTime, str, publicKeyRetrieval,
				useMariaDB)) {

		}
		try {
			Query q = new Query(mysql, "USE " + database + ";");
			q.executeUpdateAsync();
		} catch (SQLException e) {
			bungee.getLogger().severe("Failed to send use database query: " + database + " Error: " + e.getMessage());
			debug(e);
		}
		String sql = "CREATE TABLE IF NOT EXISTS " + getName() + " (";
		sql += "uuid VARCHAR(37),";
		sql += "PRIMARY KEY ( uuid )";
		sql += ");";
		Query query;
		try {
			query = new Query(mysql, sql);

			query.executeUpdateAsync();
		} catch (SQLException e) {
			e.printStackTrace();
		}

		loadData();

		// tempoary to improve performance from old tables
		// addToQue("ALTER TABLE " + getName() + " MODIFY uuid VARCHAR(37);");
		alterColumnType("uuid", "VARCHAR(37)");
	}

	public void addColumn(String column, DataType dataType) {
		synchronized (object3) {
			String sql = "ALTER TABLE " + getName() + " ADD COLUMN " + column + " text" + ";";
			try {
				Query query = new Query(mysql, sql);
				query.executeUpdate();

				getColumns().add(column);
			} catch (SQLException e) {
				e.printStackTrace();
			}

		}
	}

	public void alterColumnType(String column, String newType) {
		checkColumn(column, DataType.STRING);
		if (newType.contains("INT")) {
			try {
				new Query(mysql, "UPDATE " + getName() + " SET " + column + " = '0' where trim(coalesce(" + column
						+ ", '')) = '';").executeUpdateAsync();
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			if (!intColumns.contains(column)) {
				intColumns.add(column);
			}
		}
		try {
			new Query(mysql, "ALTER TABLE " + getName() + " MODIFY " + column + " " + newType + ";")
					.executeUpdateAsync();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void checkColumn(String column, DataType dataType) {
		synchronized (object4) {
			if (!getColumns().contains(column)) {
				if (!getColumnsQueury().contains(column)) {
					addColumn(column, dataType);
				}
			}
		}
	}

	public void clearCache() {
		clearCacheBasic();
	}

	public void clearCacheBasic() {
		columns.clear();
		columns.addAll(getColumnsQueury());
		uuids.clear();
		uuids.addAll(getUuidsQuery());
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

	public abstract void debug(SQLException e);

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
				rs.close();
				return columns;
			}
			rs.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return columns;

	}

	public ArrayList<Column> getExactQuery(Column column) {
		ArrayList<Column> result = new ArrayList<>();
		String query = "SELECT * FROM " + getName() + " WHERE `" + column.getName() + "`='"
				+ column.getValue().getString() + "';";

		try (Connection conn = mysql.getConnectionManager().getConnection();
				PreparedStatement sql = conn.prepareStatement(query)) {
			ResultSet rs = sql.executeQuery();

			/*
			 * Query sql = new Query(mysql, query); sql.setParameter(1,
			 * column.getValue().toString()); ResultSet rs = sql.executeQuery();
			 */
			if (rs.next()) {
				for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
					String columnName = rs.getMetaData().getColumnLabel(i);
					Column rCol = null;
					if (intColumns.contains(columnName)) {
						rCol = new Column(columnName, DataType.INTEGER);
						rCol.setValue(new DataValueInt(rs.getInt(i)));
					} else {
						rCol = new Column(columnName, DataType.STRING);
						rCol.setValue(new DataValueString(rs.getString(i)));
					}
					// System.out.println(i + " " +
					// rs.getMetaData().getColumnLabel(i));

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

	public String getName() {
		return name;
	}

	public ArrayList<String> getNamesQuery() {
		ArrayList<String> uuids = new ArrayList<String>();

		checkColumn("PlayerName", DataType.STRING);
		ArrayList<Column> rows = getRowsNameQuery();
		if (rows != null) {
			for (Column c : rows) {
				if (c.getValue().isString()) {
					uuids.add(c.getValue().getString());
				}
			}
		}

		return uuids;
	}

	public ConcurrentHashMap<UUID, String> getRowsUUIDNameQuery() {
		ConcurrentHashMap<UUID, String> uuidNames = new ConcurrentHashMap<UUID, String>();
		String sqlStr = "SELECT UUID, PlayerName FROM " + getName() + ";";

		try (Connection conn = mysql.getConnectionManager().getConnection();
				PreparedStatement sql = conn.prepareStatement(sqlStr)) {
			ResultSet rs = sql.executeQuery();
			/*
			 * Query query = new Query(mysql, sql); ResultSet rs = query.executeQuery();
			 */

			while (rs.next()) {
				String uuid = rs.getString("uuid");
				String playerName = rs.getString("PlayerName");
				if (uuid != null && !uuid.isEmpty() && !uuid.equals("null") && playerName != null) {
					uuidNames.put(UUID.fromString(uuid), playerName);
				}
			}
			sql.close();
			conn.close();
		} catch (SQLException e) {
		}

		return uuidNames;
	}

	public ArrayList<Column> getRowsNameQuery() {
		ArrayList<Column> result = new ArrayList<Column>();
		String sqlStr = "SELECT PlayerName FROM " + getName() + ";";

		try (Connection conn = mysql.getConnectionManager().getConnection();
				PreparedStatement sql = conn.prepareStatement(sqlStr)) {
			ResultSet rs = sql.executeQuery();
			/*
			 * Query query = new Query(mysql, sql); ResultSet rs = query.executeQuery();
			 */

			while (rs.next()) {
				Column rCol = new Column("PlayerName", new DataValueString(rs.getString("PlayerName")));
				result.add(rCol);
			}
			sql.close();
			conn.close();
		} catch (SQLException e) {
		}

		return result;
	}

	public ArrayList<Column> getRowsQuery() {
		ArrayList<Column> result = new ArrayList<Column>();
		String sqlStr = "SELECT uuid FROM " + getName() + ";";

		try (Connection conn = mysql.getConnectionManager().getConnection();
				PreparedStatement sql = conn.prepareStatement(sqlStr)) {
			ResultSet rs = sql.executeQuery();
			/*
			 * Query query = new Query(mysql, sql); ResultSet rs = query.executeQuery();
			 */

			while (rs.next()) {
				Column rCol = new Column("uuid", new DataValueString(rs.getString("uuid")));
				result.add(rCol);
			}
			rs.close();
		} catch (SQLException e) {
			return null;
		}

		return result;
	}

	public String getUUID(String playerName) {
		String query = "SELECT uuid FROM " + getName() + " WHERE " + "PlayerName" + "='" + playerName + "';";
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
		for (Column c : rows) {
			if (c.getValue().isString()) {
				uuids.add(c.getValue().getString());
			}
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
				query += col.getName() + "='" + col.getValue().toString() + "';";
			} else {
				query += col.getName() + "='" + col.getValue().toString() + "', ";
			}

		}

		try {
			uuids.add(index);
			new Query(mysql, query).executeUpdateAsync();
		} catch (SQLException e) {
			e.printStackTrace();
		}

	}

	public boolean isIntColumn(String key) {
		return intColumns.contains(key);
	}

	public void loadData() {
		columns = getColumnsQueury();
	}

	public void update(String index, List<Column> cols) {
		for (Column col : cols) {
			checkColumn(col.getName(), col.getDataType());
		}
		if (getUuids().contains(index)) {
			synchronized (object2) {

				String query = "UPDATE " + getName() + " SET ";

				for (int i = 0; i < cols.size(); i++) {
					Column col = cols.get(i);
					if (i == cols.size() - 1) {
						if (col.getValue().isString()) {
							query += col.getName() + "='" + col.getValue().getString() + "'";
						} else if (col.getValue().isBoolean()) {
							query += col.getName() + "='" + col.getValue().getBoolean() + "'";
						} else if (col.getValue().isInt()) {
							query += col.getName() + "='" + col.getValue().getInt() + "'";
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
				query += " WHERE `uuid`=";
				query += "'" + index + "';";

				try {
					Query q = new Query(mysql, query);
					q.executeUpdateAsync();
				} catch (SQLException e) {
					e.printStackTrace();
				}
			}
		} else {
			insertQuery(index, cols);
		}
	}

	public void update(String index, String column, DataValue value) {
		checkColumn(column, value.getType());
		if (getUuids().contains(index)) {
			synchronized (object2) {
				String query = "UPDATE " + getName() + " SET ";

				if (value.isString()) {
					query += column + "='" + value.getString() + "'";
				} else if (value.isBoolean()) {
					query += column + "='" + value.getBoolean() + "'";
				} else if (value.isInt()) {
					query += column + "='" + value.getInt() + "'";
				}
				query += " WHERE `uuid`=";
				query += "'" + index + "';";

				try {
					Query q = new Query(mysql, query);
					q.executeUpdate();
				} catch (SQLException e) {
					e.printStackTrace();
				}
			}

		} else {
			insert(index, column, value);
		}

	}

	public void shutdown() {
		mysql.disconnect();
	}
}