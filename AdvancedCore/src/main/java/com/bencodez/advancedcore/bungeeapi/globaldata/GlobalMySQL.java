package com.bencodez.advancedcore.bungeeapi.globaldata;

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

import com.bencodez.advancedcore.api.messages.PlaceholderUtils;
import com.bencodez.simpleapi.array.ArrayUtils;
import com.bencodez.simpleapi.sql.Column;
import com.bencodez.simpleapi.sql.DataType;
import com.bencodez.simpleapi.sql.data.DataValue;
import com.bencodez.simpleapi.sql.data.DataValueInt;
import com.bencodez.simpleapi.sql.data.DataValueString;
import com.bencodez.simpleapi.sql.mysql.MySQL;
import com.bencodez.simpleapi.sql.mysql.config.MysqlConfig;
import com.bencodez.simpleapi.sql.mysql.queries.Query;

public abstract class GlobalMySQL {
	private List<String> columns = Collections.synchronizedList(new ArrayList<String>());

	private com.bencodez.simpleapi.sql.mysql.MySQL mysql;

	private String name;

	private Object object2 = new Object();

	private Object object3 = new Object();

	private Object object4 = new Object();

	private List<String> intColumns = new ArrayList<>();

	private boolean useBatchUpdates = true;

	private Set<String> servers = ConcurrentHashMap.newKeySet();

	public GlobalMySQL(String tableName, MySQL mysql) {
		this.mysql = mysql;
		this.name = tableName;
		String sql = "CREATE TABLE IF NOT EXISTS " + getName() + " (";
		sql += "server VARCHAR(50), ";

		sql += "PRIMARY KEY ( server ));";

		try {
			Query query = new Query(mysql, sql);

			query.executeUpdate();
		} catch (SQLException e) {
			e.printStackTrace();
		}

		loadData();
	}

	public GlobalMySQL(String tableName, MysqlConfig config) {

		if (config.hasTableNameSet()) {
			tableName = config.getTableName();
		}
		name = tableName;
		if (config.getTablePrefix() != null) {
			name = config.getTablePrefix() + tableName;
		}
		mysql = new com.bencodez.simpleapi.sql.mysql.MySQL(config.getMaxThreads()) {

			@Override
			public void debug(SQLException e) {
				debugEx(e);
			}

			@Override
			public void severe(String string) {
				logSevere(string);
			}

			@Override
			public void debug(String msg) {
				debugLog(msg);
			}
		};
		if (!mysql.connect(config)) {
			warning("Failed to connect to MySQL");
		}
		try {
			Query q = new Query(mysql, "USE `" + config.getDatabase() + "`;");
			q.executeUpdate();
		} catch (SQLException e) {
			logSevere("Failed to send use database query: " + config.getDatabase() + " Error: " + e.getMessage()
					+ ", MySQL might still work");
			debugEx(e);
		}
		String sql = "CREATE TABLE IF NOT EXISTS " + getName() + " (";
		sql += "server VARCHAR(50), ";

		sql += "PRIMARY KEY ( server ));";

		try {
			Query query = new Query(mysql, sql);

			query.executeUpdate();
		} catch (SQLException e) {
			e.printStackTrace();
		}

		loadData();
	}

	public void addColumn(String column, DataType dataType) {
		synchronized (object3) {
			String sql = "ALTER TABLE " + getName() + " ADD COLUMN `" + column + "` text" + ";";

			debugLog("Adding column: " + column + " Current columns: "
					+ ArrayUtils.makeStringList((ArrayList<String>) getColumns()));
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
		debugLog("Altering column `" + column + "` to " + newType);
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
			if (!ArrayUtils.containsIgnoreCase((ArrayList<String>) getColumns(), column)) {
				if (!ArrayUtils.containsIgnoreCase(getColumnsQueury(), column)) {
					addColumn(column, dataType);
				}
			}
		}
	}

	public void clearCacheBasic() {
		debugLog("Clearing cache basic");
		columns.clear();
		columns.addAll(getColumnsQueury());
		servers.clear();
		servers.addAll(getServersQuery());
	}

	public void close() {
		mysql.disconnect();
	}

	public boolean containsKey(String server) {
		if (servers.contains(server) || containsKeyQuery(server)) {
			return true;
		}
		return false;
	}

	public boolean containsKeyQuery(String index) {
		String sqlStr = "SELECT server FROM " + getName() + ";";
		try (Connection conn = mysql.getConnectionManager().getConnection();
				PreparedStatement sql = conn.prepareStatement(sqlStr)) {
			ResultSet rs = sql.executeQuery();
			/*
			 * Query query = new Query(mysql, sql); ResultSet rs = query.executeQuery();
			 */
			while (rs.next()) {
				if (rs.getString("server").equals(index)) {
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

	public boolean containsServer(String server) {
		if (servers.contains(server)) {
			return true;
		}
		return false;
	}

	public abstract void debugEx(Exception e);

	public abstract void debugLog(String text);

	public void deleteServer(String server) {
		String q = "DELETE FROM " + getName() + " WHERE server='" + server + "';";
		try {
			Query query = new Query(mysql, q);
			query.executeUpdate();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		servers.remove(server);
		clearCacheBasic();

	}

	public void executeQuery(String str) {
		try {
			Query q = new Query(mysql, PlaceholderUtils.replacePlaceHolder(str, "tablename", getName()));
			q.executeUpdate();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public List<String> getColumns() {
		if (columns == null || columns.size() == 0) {
			loadData();
		}
		return columns;
	}

	public ArrayList<String> getColumnsQueury() {
		ArrayList<String> columns = new ArrayList<>();
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

	public ArrayList<Column> getExact(String server) {
		return getExactQuery(new Column("server", new DataValueString(server)));
	}

	public ArrayList<Column> getExactQuery(Column column) {
		ArrayList<Column> result = new ArrayList<>();
		String query = "SELECT * FROM " + getName() + " WHERE `" + column.getName() + "`='"
				+ column.getValue().getString() + "';";

		try (Connection conn = mysql.getConnectionManager().getConnection();
				PreparedStatement sql = conn.prepareStatement(query)) {
			ResultSet rs = sql.executeQuery();

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

	public String getName() {
		return name;
	}

	public ArrayList<Column> getRowsQuery() {
		ArrayList<Column> result = new ArrayList<>();
		String sqlStr = "SELECT server FROM " + getName() + ";";

		try (Connection conn = mysql.getConnectionManager().getConnection();
				PreparedStatement sql = conn.prepareStatement(sqlStr)) {

			ResultSet rs = sql.executeQuery();

			while (rs.next()) {
				Column rCol = new Column("server", new DataValueString(rs.getString("server")));
				result.add(rCol);
			}
			rs.close();
		} catch (SQLException e) {
			e.printStackTrace();
			return null;
		}

		return result;
	}

	public Set<String> getServers() {
		if (servers == null || servers.size() == 0) {
			servers.clear();
			servers.addAll(getServersQuery());
			return servers;
		}
		return servers;
	}

	public ArrayList<String> getServersQuery() {
		ArrayList<String> uuids = new ArrayList<>();

		ArrayList<Column> rows = getRowsQuery();
		if (rows != null) {
			for (Column c : rows) {
				if (c.getValue() != null && c.getValue().isString()) {
					uuids.add(c.getValue().getString());
				}
			}
		} else {
			logSevere("Failed to fetch servers");
		}

		return uuids;
	}

	public abstract void info(String text);

	public void insert(String index, String column, DataValue value) {
		insertQuery(index, Arrays.asList(new Column(column, value)));

	}

	public void insertQuery(String index, List<Column> cols) {
		String query = "INSERT IGNORE " + getName() + " ";

		query += "set server='" + index + "', ";

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

		try {
			new Query(mysql, query).executeUpdate();
			servers.add(index);
			debugLog("Inserting " + index + " into database");
		} catch (Exception e) {
			e.printStackTrace();
			debugLog("Failed to insert server " + index);
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

	public abstract void logSevere(String text);

	public void update(String index, List<Column> cols, boolean runAsync) {
		for (Column col : cols) {
			checkColumn(col.getName(), col.getDataType());
		}
		synchronized (object2) {
			if (getServers().contains(index) || containsKeyQuery(index)) {

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
				query += " WHERE server=";
				query += "'" + index + "';";

				debugLog("Batch query: " + query);

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
			debugLog("Mysql value null: " + column);
			return;
		}
		checkColumn(column, value.getType());
		synchronized (object2) {
			if (getServers().contains(index) || containsKeyQuery(index)) {
				String query = "UPDATE " + getName() + " SET ";

				if (value.isString()) {
					query += column + "='" + value.getString() + "'";
				} else if (value.isBoolean()) {
					query += column + "='" + value.getBoolean() + "'";
				} else if (value.isInt()) {
					query += column + "='" + value.getInt() + "'";
				}
				query += " WHERE server=";
				query += "'" + index + "';";

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

	public abstract void warning(String text);

	public void wipeColumnData(String columnName) {
		checkColumn(columnName, DataType.STRING);
		String sql = "UPDATE " + getName() + " SET " + columnName + " = NULL;";
		try {
			Query query = new Query(mysql, sql);
			query.executeUpdate();
		} catch (SQLException e) {
			e.printStackTrace();
		}

	}
}
