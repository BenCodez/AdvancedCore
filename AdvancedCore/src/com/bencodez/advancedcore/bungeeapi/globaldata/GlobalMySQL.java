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

import com.bencodez.advancedcore.api.messages.StringParser;
import com.bencodez.advancedcore.api.misc.ArrayUtils;
import com.bencodez.advancedcore.api.user.usercache.value.DataValue;
import com.bencodez.advancedcore.api.user.usercache.value.DataValueInt;
import com.bencodez.advancedcore.api.user.usercache.value.DataValueString;
import com.bencodez.advancedcore.api.user.userstorage.Column;
import com.bencodez.advancedcore.api.user.userstorage.DataType;
import com.bencodez.advancedcore.api.user.userstorage.mysql.api.MySQL;
import com.bencodez.advancedcore.api.user.userstorage.mysql.api.config.MysqlConfig;
import com.bencodez.advancedcore.api.user.userstorage.mysql.api.queries.Query;

public abstract class GlobalMySQL {
	private List<String> columns = Collections.synchronizedList(new ArrayList<String>());

	private com.bencodez.advancedcore.api.user.userstorage.mysql.api.MySQL mysql;

	private String name;

	private Object object2 = new Object();

	private Object object3 = new Object();

	private Object object4 = new Object();

	private List<String> intColumns = new ArrayList<String>();

	private boolean useBatchUpdates = true;

	private Set<String> servers = ConcurrentHashMap.newKeySet();

	public abstract void debug(String text);

	public abstract void debug(Exception e);

	public abstract void severe(String text);

	public abstract void warning(String text);

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
		mysql = new com.bencodez.advancedcore.api.user.userstorage.mysql.api.MySQL(config.getMaxThreads()) {

			@Override
			public void debug(SQLException e) {
				debug(e);
			}

			@Override
			public void severe(String string) {
				severe(string);
			}
		};
		if (!mysql.connect(config)) {
			warning("Failed to connect to MySQL");
		}
		try {
			Query q = new Query(mysql, "USE `" + config.getDatabase() + "`;");
			q.executeUpdate();
		} catch (SQLException e) {
			severe("Failed to send use database query: " + config.getDatabase() + " Error: " + e.getMessage()
					+ ", MySQL might still work");
			debug(e);
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

			debug("Adding column: " + column + " Current columns: "
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
		debug("Altering column `" + column + "` to " + newType);
		if (newType.contains("INT")) {
			try {
				Query query = new Query(mysql, "UPDATE " + getName() + " SET `" + column
						+ "` = '0' where trim(coalesce(" + column + ", '')) = '';");
				query.executeUpdateAsync();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		Query query;
		try {
			query = new Query(mysql, "ALTER TABLE " + getName() + " MODIFY `" + column + "` " + newType + ";");
			query.executeUpdateAsync();
		} catch (SQLException e) {
			e.printStackTrace();
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
		debug("Clearing cache basic");
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
		ArrayList<Column> result = new ArrayList<Column>();
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
		ArrayList<String> uuids = new ArrayList<String>();

		ArrayList<Column> rows = getRowsQuery();
		if (rows != null) {
			for (Column c : rows) {
				if (c.getValue() != null && c.getValue().isString()) {
					uuids.add(c.getValue().getString());
				}
			}
		} else {
			severe("Failed to fetch servers");
		}

		return uuids;
	}

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
			debug("Inserting " + index + " into database");
		} catch (Exception e) {
			e.printStackTrace();
			debug("Failed to insert server " + index);
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

				debug("Batch query: " + query);

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
			debug("Mysql value null: " + column);
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

	public void executeQuery(String str) {
		try {
			Query q = new Query(mysql, StringParser.getInstance().replacePlaceHolder(str, "tablename", getName()));
			q.executeUpdate();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
}
