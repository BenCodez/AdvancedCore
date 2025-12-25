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
import com.bencodez.simpleapi.sql.mysql.DbType;
import com.bencodez.simpleapi.sql.mysql.MySQL;
import com.bencodez.simpleapi.sql.mysql.config.MysqlConfig;
import com.bencodez.simpleapi.sql.mysql.queries.Query;

public abstract class GlobalMySQL {
	private List<String> columns = Collections.synchronizedList(new ArrayList<String>());

	private com.bencodez.simpleapi.sql.mysql.MySQL mysql;

	private String name;

	private final Object object2 = new Object();
	private final Object object3 = new Object();
	private final Object object4 = new Object();

	private List<String> intColumns = new ArrayList<>();

	private boolean useBatchUpdates = true;

	private Set<String> servers = ConcurrentHashMap.newKeySet();

	public GlobalMySQL(String tableName, MySQL mysql) {
		this.mysql = mysql;
		this.name = tableName;

		// Best practice for Postgres: keep table name lower-case unless you always quote it.
		if (dbType() == DbType.POSTGRESQL) {
			this.name = this.name.toLowerCase();
		}

		createTableIfNeeded();

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

		if (config.getDbType() == DbType.POSTGRESQL) {
			name = name.toLowerCase();
		}

		if (config.getPoolName().isEmpty()) {
			config.setPoolName("VotingPlugin" + "-" + tableName);
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
			warning("Failed to connect to database (type=" + config.getDbType() + ")");
		}

		// MySQL has a "USE db" command; Postgres does NOT.
		if (config.getDbType() != DbType.POSTGRESQL) {
			try {
				new Query(mysql, "USE " + quoteIdent(config.getDbType(), config.getDatabase()) + ";").executeUpdate();
			} catch (SQLException e) {
				logSevere("Failed to send use database query: " + config.getDatabase() + " Error: " + e.getMessage()
						+ ", DB might still work");
				debugEx(e);
			}
		}

		createTableIfNeeded();
		loadData();
	}

	// -------------------------
	// Dialect helpers
	// -------------------------

	private DbType dbType() {
		return mysql.getConnectionManager().getDbType();
	}

	private String quoteIdent(DbType dbType, String ident) {
		if (ident == null) {
			return "";
		}
		if (dbType == DbType.POSTGRESQL) {
			return "\"" + ident.replace("\"", "\"\"") + "\"";
		}
		return "`" + ident.replace("`", "``") + "`";
	}

	private String qi(String ident) {
		return quoteIdent(dbType(), ident);
	}

	private String normalizeColumnType(DbType dbType, String type) {
		if (type == null) {
			return "TEXT";
		}
		if (dbType != DbType.POSTGRESQL) {
			return type;
		}

		String t = type.trim().toUpperCase();

		// MySQL text variants -> Postgres TEXT
		if (t.equals("MEDIUMTEXT") || t.equals("LONGTEXT") || t.equals("TEXT")) {
			return "TEXT";
		}

		// INT family
		if (t.startsWith("INT")) {
			return "INTEGER";
		}

		// Leave VARCHAR(N), BOOLEAN, BIGINT, etc. alone if already compatible
		return type;
	}

	private void createTableIfNeeded() {
		String sql = "CREATE TABLE IF NOT EXISTS " + getName() + " (" + "server VARCHAR(50), " + "PRIMARY KEY ( server ));";
		try {
			new Query(mysql, sql).executeUpdate();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	// -------------------------
	// Column management
	// -------------------------

	public void addColumn(String column, DataType dataType) {
		synchronized (object3) {
			// TEXT works fine for both
			String sql = "ALTER TABLE " + getName() + " ADD COLUMN " + qi(dbType() == DbType.POSTGRESQL ? column.toLowerCase() : column)
					+ " TEXT;";
			debugLog("Adding column: " + column + " Current columns: "
					+ ArrayUtils.makeStringList((ArrayList<String>) getColumns()));
			try {
				new Query(mysql, sql).executeUpdate();
				getColumns().add(column);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Decide if a column actually needs to be altered, based on information_schema.
	 * - MySQL/MariaDB: TABLE_SCHEMA = DATABASE()
	 * - PostgreSQL: table_schema = current_schema()
	 */
	private boolean columnNeedsAlter(Connection conn, DbType dbType, String column, String newType) throws SQLException {
		String normalized = normalizeColumnType(dbType, newType).trim();

		if (dbType == DbType.POSTGRESQL) {
			String sql = "SELECT data_type, character_maximum_length, column_default "
					+ "FROM information_schema.columns "
					+ "WHERE table_schema = current_schema() AND table_name = ? AND column_name = ?";

			try (PreparedStatement ps = conn.prepareStatement(sql)) {
				ps.setString(1, getName());
				ps.setString(2, column.toLowerCase());
				try (ResultSet rs = ps.executeQuery()) {
					if (!rs.next()) {
						return true;
					}

					String dataType = rs.getString("data_type"); // "text", "integer", "character varying", ...
					Object lenObj = rs.getObject("character_maximum_length");
					Long length = (lenObj instanceof Number) ? ((Number) lenObj).longValue() : null;

					String typeUpper = normalized.toUpperCase().trim();

					if (typeUpper.equals("TEXT")) {
						return !dataType.equalsIgnoreCase("text");
					}

					if (typeUpper.startsWith("VARCHAR(")) {
						boolean typeMatches = dataType.equalsIgnoreCase("character varying")
								|| dataType.equalsIgnoreCase("varchar");
						int open = typeUpper.indexOf('(');
						int close = typeUpper.indexOf(')', open + 1);
						if (open != -1 && close != -1) {
							int expectedLen = Integer.parseInt(typeUpper.substring(open + 1, close));
							boolean lengthMatches = (length != null && length == expectedLen);
							return !(typeMatches && lengthMatches);
						}
						return true;
					}

					if (typeUpper.startsWith("INTEGER") || typeUpper.startsWith("INT")) {
						return !(dataType.equalsIgnoreCase("integer") || dataType.equalsIgnoreCase("int4"));
					}

					return true;
				}
			}
		}

		// MySQL / MariaDB
		String sql = "SELECT DATA_TYPE, CHARACTER_MAXIMUM_LENGTH, COLUMN_DEFAULT "
				+ "FROM information_schema.COLUMNS "
				+ "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?";

		try (PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setString(1, getName());
			ps.setString(2, column);
			try (ResultSet rs = ps.executeQuery()) {
				if (!rs.next()) {
					return true;
				}

				String dataType = rs.getString("DATA_TYPE");
				Object lenObj = rs.getObject("CHARACTER_MAXIMUM_LENGTH");
				Long length = (lenObj instanceof Number) ? ((Number) lenObj).longValue() : null;

				String typeUpper = normalized.toUpperCase().trim();

				if (typeUpper.equals("MEDIUMTEXT")) {
					return !dataType.equalsIgnoreCase("mediumtext");
				}
				if (typeUpper.equals("TEXT")) {
					return !dataType.equalsIgnoreCase("text");
				}
				if (typeUpper.equals("LONGTEXT")) {
					return !dataType.equalsIgnoreCase("longtext");
				}

				if (typeUpper.startsWith("VARCHAR(")) {
					int open = typeUpper.indexOf('(');
					int close = typeUpper.indexOf(')', open + 1);
					if (open != -1 && close != -1) {
						int expectedLen = Integer.parseInt(typeUpper.substring(open + 1, close));
						boolean typeMatches = dataType.equalsIgnoreCase("varchar");
						boolean lengthMatches = (length != null && length == expectedLen);
						return !(typeMatches && lengthMatches);
					}
					return true;
				}

				if (typeUpper.startsWith("INT")) {
					boolean isIntFamily = dataType.equalsIgnoreCase("int") || dataType.equalsIgnoreCase("integer")
							|| dataType.equalsIgnoreCase("mediumint") || dataType.equalsIgnoreCase("smallint")
							|| dataType.equalsIgnoreCase("tinyint") || dataType.equalsIgnoreCase("bigint");
					return !isIntFamily;
				}

				return true;
			}
		}
	}

	public void alterColumnType(final String column, final String newType) {
		checkColumn(column, DataType.STRING);

		DbType dbType = dbType();
		String normalized = normalizeColumnType(dbType, newType);

		// First inspect existing type; skip ALTER if it's already correct
		try (Connection conn = mysql.getConnectionManager().getConnection()) {
			if (!columnNeedsAlter(conn, dbType, column, normalized)) {
				debugLog("GlobalDB: Column " + qi(dbType == DbType.POSTGRESQL ? column.toLowerCase() : column)
						+ " already matches " + normalized + ", skipping ALTER");
				if (normalized.toUpperCase().contains("INT") && !intColumns.contains(column)) {
					intColumns.add(column);
				}
				return;
			}
		} catch (SQLException e) {
			debugLog("GlobalDB: Failed to inspect column " + getName() + "." + column + " - running ALTER anyway");
			debugEx(e);
		}

		debugLog("Altering column `" + column + "` to " + normalized);

		// If going to INT, normalise empty strings to 0 first to avoid conversion issues.
		if (normalized.toUpperCase().contains("INT")) {
			try {
				if (dbType == DbType.POSTGRESQL) {
					// Postgres: trim(coalesce(col::text,'')) = ''
					String fix = "UPDATE " + getName() + " SET " + qi(column.toLowerCase()) + " = '0' "
							+ "WHERE btrim(coalesce(" + qi(column.toLowerCase()) + "::text, '')) = '';";
					new Query(mysql, fix).executeUpdateAsync();
				} else {
					String fix = "UPDATE " + getName() + " SET " + qi(column) + " = '0' "
							+ "WHERE TRIM(COALESCE(" + column + ", '')) = '';";
					new Query(mysql, fix).executeUpdateAsync();
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}

		try {
			String alter;
			if (dbType == DbType.POSTGRESQL) {
				alter = "ALTER TABLE " + getName() + " ALTER COLUMN " + qi(column.toLowerCase()) + " TYPE " + normalized
						+ ";";
			} else {
				alter = "ALTER TABLE " + getName() + " MODIFY " + qi(column) + " " + normalized + ";";
			}
			new Query(mysql, alter).executeUpdateAsync();
		} catch (SQLException e) {
			e.printStackTrace();
		}

		if (normalized.toUpperCase().contains("INT") && !intColumns.contains(column)) {
			intColumns.add(column);
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

	// -------------------------
	// Cache / lifecycle
	// -------------------------

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

	// -------------------------
	// Existence checks
	// -------------------------

	public boolean containsKey(String server) {
		return servers.contains(server) || containsKeyQuery(server);
	}

	public boolean containsKeyQuery(String index) {
		// Efficient: WHERE server = ?
		String sqlStr = "SELECT server FROM " + getName() + " WHERE server = ?;";
		try (Connection conn = mysql.getConnectionManager().getConnection();
				PreparedStatement ps = conn.prepareStatement(sqlStr)) {
			ps.setString(1, index);
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next();
			}
		} catch (SQLException ex) {
			ex.printStackTrace();
		}
		return false;
	}

	public boolean containsServer(String server) {
		return servers.contains(server);
	}

	public abstract void debugEx(Exception e);

	public abstract void debugLog(String text);

	public void deleteServer(String server) {
		String q = "DELETE FROM " + getName() + " WHERE server='" + server + "';";
		try {
			new Query(mysql, q).executeUpdate();
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
		if (columns == null || columns.isEmpty()) {
			loadData();
		}
		return columns;
	}

	public ArrayList<String> getColumnsQueury() {
		ArrayList<String> cols = new ArrayList<>();

		try (Connection conn = mysql.getConnectionManager().getConnection()) {
			if (dbType() == DbType.POSTGRESQL) {
				String sql = "SELECT column_name FROM information_schema.columns "
						+ "WHERE table_schema = current_schema() AND table_name = ?;";
				try (PreparedStatement ps = conn.prepareStatement(sql)) {
					ps.setString(1, getName());
					try (ResultSet rs = ps.executeQuery()) {
						while (rs.next()) {
							cols.add(rs.getString(1));
						}
					}
				}
				return cols;
			}

			// MySQL/MariaDB fallback (fast + doesn't require information_schema privileges)
			try (PreparedStatement sql = conn.prepareStatement("SELECT * FROM " + getName() + " LIMIT 1;");
					ResultSet rs = sql.executeQuery()) {
				ResultSetMetaData metadata = rs.getMetaData();
				if (metadata != null) {
					for (int i = 1; i <= metadata.getColumnCount(); i++) {
						cols.add(metadata.getColumnName(i));
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		return cols;
	}

	public ArrayList<Column> getExact(String server) {
		return getExactQuery(new Column("server", new DataValueString(server)));
	}

	public ArrayList<Column> getExactQuery(Column column) {
		ArrayList<Column> result = new ArrayList<>();

		String colName = (dbType() == DbType.POSTGRESQL) ? qi(column.getName().toLowerCase()) : qi(column.getName());
		String query = "SELECT * FROM " + getName() + " WHERE " + colName + "='" + column.getValue().getString() + "';";

		try (Connection conn = mysql.getConnectionManager().getConnection();
				PreparedStatement sql = conn.prepareStatement(query);
				ResultSet rs = sql.executeQuery()) {

			if (rs.next()) {
				for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
					String columnName = rs.getMetaData().getColumnLabel(i);
					Column rCol;
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
			return result;
		} catch (SQLException | ArrayIndexOutOfBoundsException e) {
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
				PreparedStatement sql = conn.prepareStatement(sqlStr);
				ResultSet rs = sql.executeQuery()) {

			while (rs.next()) {
				result.add(new Column("server", new DataValueString(rs.getString(1))));
			}
		} catch (SQLException e) {
			e.printStackTrace();
			return null;
		}

		return result;
	}

	public Set<String> getServers() {
		if (servers == null || servers.isEmpty()) {
			servers.clear();
			servers.addAll(getServersQuery());
		}
		return servers;
	}

	public ArrayList<String> getServersQuery() {
		ArrayList<String> out = new ArrayList<>();

		ArrayList<Column> rows = getRowsQuery();
		if (rows != null) {
			for (Column c : rows) {
				if (c.getValue() != null && c.getValue().isString()) {
					out.add(c.getValue().getString());
				}
			}
		} else {
			logSevere("Failed to fetch servers");
		}

		return out;
	}

	public abstract void info(String text);

	public void insert(String index, String column, DataValue value) {
		insertQuery(index, Arrays.asList(new Column(column, value)));
	}

	public void insertQuery(String index, List<Column> cols) {
		DbType dbType = dbType();

		// Ensure columns exist
		for (Column c : cols) {
			checkColumn(c.getName(), c.getDataType());
		}

		if (dbType == DbType.POSTGRESQL) {
			// Best-case: UPSERT
			StringBuilder sb = new StringBuilder();
			sb.append("INSERT INTO ").append(getName()).append(" (server");
			for (Column col : cols) {
				sb.append(", ").append(quoteIdent(dbType, col.getName().toLowerCase()));
			}
			sb.append(") VALUES ('").append(index).append("'");
			for (Column col : cols) {
				sb.append(", '").append(col.getValue().toString()).append("'");
			}
			sb.append(") ON CONFLICT (server) DO UPDATE SET ");
			for (int i = 0; i < cols.size(); i++) {
				Column col = cols.get(i);
				String c = quoteIdent(dbType, col.getName().toLowerCase());
				sb.append(c).append(" = EXCLUDED.").append(c);
				if (i != cols.size() - 1) {
					sb.append(", ");
				}
			}
			sb.append(";");

			String query = sb.toString();
			try {
				new Query(mysql, query).executeUpdate();
				servers.add(index);
				debugLog("Upserting " + index + " into database");
			} catch (Exception e) {
				e.printStackTrace();
				debugLog("Failed to upsert server " + index);
			}
			return;
		}

		// MySQL/MariaDB: keep original INSERT IGNORE ... SET ...
		String query = "INSERT IGNORE " + getName() + " ";
		query += "set server='" + index + "', ";

		for (int i = 0; i < cols.size(); i++) {
			Column col = cols.get(i);
			boolean last = (i == cols.size() - 1);

			if (col.getValue().isString()) {
				query += col.getName() + "='" + col.getValue().getString() + "'" + (last ? ";" : ", ");
			} else if (col.getValue().isBoolean()) {
				query += col.getName() + "='" + col.getValue().getBoolean() + "'" + (last ? ";" : ", ");
			} else if (col.getValue().isInt()) {
				query += col.getName() + "='" + col.getValue().getInt() + "'" + (last ? ";" : ", ");
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
			useBatchUpdates = con != null && con.getMetaData().supportsBatchUpdates();
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

				DbType dbType = dbType();

				StringBuilder sb = new StringBuilder();
				sb.append("UPDATE ").append(getName()).append(" SET ");

				for (int i = 0; i < cols.size(); i++) {
					Column col = cols.get(i);
					boolean last = (i == cols.size() - 1);

					String colName = (dbType == DbType.POSTGRESQL) ? quoteIdent(dbType, col.getName().toLowerCase())
							: "`" + col.getName() + "`";

					if (col.getValue().isString()) {
						sb.append(colName).append("='").append(col.getValue().getString()).append("'");
					} else if (col.getValue().isBoolean()) {
						sb.append(colName).append("='").append(col.getValue().getBoolean()).append("'");
					} else if (col.getValue().isInt()) {
						sb.append(colName).append("='").append(col.getValue().getInt()).append("'");
					}

					if (!last) {
						sb.append(", ");
					}
				}

				sb.append(" WHERE server='").append(index).append("';");

				String query = sb.toString();
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
				DbType dbType = dbType();

				String colName = (dbType == DbType.POSTGRESQL) ? quoteIdent(dbType, column.toLowerCase()) : column;

				String query = "UPDATE " + getName() + " SET ";

				if (value.isString()) {
					query += colName + "='" + value.getString() + "'";
				} else if (value.isBoolean()) {
					query += colName + "='" + value.getBoolean() + "'";
				} else if (value.isInt()) {
					query += colName + "='" + value.getInt() + "'";
				}

				query += " WHERE server='" + index + "';";

				try {
					new Query(mysql, query).executeUpdate();
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

		String colName = (dbType() == DbType.POSTGRESQL) ? quoteIdent(dbType(), columnName.toLowerCase()) : columnName;

		String sql = "UPDATE " + getName() + " SET " + colName + " = NULL;";
		try {
			new Query(mysql, sql).executeUpdate();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
}
