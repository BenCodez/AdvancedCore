package com.Ben12345rocks.AdvancedCore.mysql;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import org.bukkit.configuration.ConfigurationSection;

import com.Ben12345rocks.AdvancedCore.AdvancedCoreHook;
import com.Ben12345rocks.AdvancedCore.Objects.UUID;
import com.Ben12345rocks.AdvancedCore.UserManager.UserManager;
import com.Ben12345rocks.AdvancedCore.Util.Misc.CompatibleCacheBuilder;
import com.Ben12345rocks.AdvancedCore.Util.Misc.PlayerUtils;
import com.Ben12345rocks.AdvancedCore.mysql.api.queries.Query;
import com.Ben12345rocks.AdvancedCore.sql.Column;
import com.Ben12345rocks.AdvancedCore.sql.DataType;
import com.google.common.cache.CacheLoader;

public class MySQL {
	private com.Ben12345rocks.AdvancedCore.mysql.api.MySQL mysql;

	private List<String> columns = Collections.synchronizedList(new ArrayList<String>());

	// private HashMap<String, ArrayList<Column>> table;

	ConcurrentMap<String, ArrayList<Column>> table = CompatibleCacheBuilder.newBuilder().concurrencyLevel(6)
			.build(new CacheLoader<String, ArrayList<Column>>() {

				@Override
				public ArrayList<Column> load(String key) {
					return getExactQuery(new Column("uuid", key, DataType.STRING));
				}
			});

	// ConcurrentMap<String, ArrayList<Column>> table = new
	// ConcurrentHashMap<String, ArrayList<Column>>();

	private ConcurrentLinkedQueue<String> query = new ConcurrentLinkedQueue<String>();

	private String name;

	private Set<String> uuids = Collections.synchronizedSet(new HashSet<String>());

	private Set<String> names = Collections.synchronizedSet(new HashSet<String>());

	private boolean useBatchUpdates = true;

	private int maxSize = 0;

	public MySQL(String tableName, ConfigurationSection section) {
		String tablePrefix = section.getString("Prefix");
		String hostName = section.getString("Host");
		int port = section.getInt("Port");
		String user = section.getString("Username");
		String pass = section.getString("Password");
		String database = section.getString("Database");
		int maxThreads = section.getInt("MaxConnections", 1);
		if (maxThreads < 1) {
			maxThreads = 1;
		}
		boolean useSSL = section.getBoolean("UseSSL", false);
		this.maxSize = section.getInt("MaxSize", -1);
		if (!section.getString("Name", "").isEmpty()) {
			tableName = section.getString("Name", "");
		}

		if (maxSize >= 0) {
			table = CompatibleCacheBuilder.newBuilder().concurrencyLevel(6).expireAfterAccess(20, TimeUnit.MINUTES)
					.maximumSize(maxSize).build(new CacheLoader<String, ArrayList<Column>>() {

						@Override
						public ArrayList<Column> load(String key) {
							return getExactQuery(new Column("uuid", key, DataType.STRING));
						}
					});
		}

		name = tableName;
		if (tablePrefix != null) {
			name = tablePrefix + tableName;
		}
		mysql = new com.Ben12345rocks.AdvancedCore.mysql.api.MySQL(maxThreads);
		if (!mysql.connect(hostName, "" + port, user, pass, database, useSSL)) {
			AdvancedCoreHook.getInstance().getPlugin().getLogger().warning("Failed to connect to MySQL");
		}
		try {
			Query q = new Query(mysql, "USE " + database + ";");
			q.executeUpdateAsync();
		} catch (SQLException e) {
			e.printStackTrace();
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

		// tempoary to improve performance
		addToQue("ALTER TABLE " + getName() + " MODIFY uuid VARCHAR(37);");

		loadData();

		new Timer().schedule(new TimerTask() {

			@Override
			public void run() {
				updateBatch();
			}

		}, 10 * 1000, 500);

	}

	public void addColumn(String column, DataType dataType) {
		synchronized (object3) {
			String sql = "ALTER TABLE " + getName() + " ADD COLUMN " + column + " text" + ";";

			AdvancedCoreHook.getInstance().debug("Adding column: " + column);
			try {
				Query query = new Query(mysql, sql);
				query.executeUpdate();
			} catch (SQLException e) {
				e.printStackTrace();
			}

			getColumns().add(column);

			Column col = new Column(column, dataType);
			for (Entry<String, ArrayList<Column>> entry : table.entrySet()) {
				entry.getValue().add(col);
			}
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
		AdvancedCoreHook.getInstance().debug("CLearing cache");
		table.clear();
		clearCacheBasic();
	}

	public void clearCacheBasic() {
		AdvancedCoreHook.getInstance().debug("CLearing cache basic");
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
		return table.containsKey(uuid);
	}

	public boolean containsKeyQuery(String index) {
		String sql = "SELECT uuid FROM " + getName() + ";";
		try {
			Query query = new Query(mysql, sql);

			ResultSet rs = query.executeQuery();
			while (rs.next()) {
				if (rs.getString("uuid").equals(index)) {
					return true;
				}
			}

		} catch (SQLException ex) {
			ex.printStackTrace();
		}
		return false;
	}

	public void deletePlayer(String uuid) {
		String q = "DELETE FROM " + getName() + " WHERE uuid='" + uuid + "';";
		uuids.remove(uuid);
		this.query.add(q);
		removePlayer(uuid);
		names.remove(PlayerUtils.getInstance().getPlayerName(UserManager.getInstance().getUser(new UUID(uuid)), uuid));
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
		try {
			Query query = new Query(mysql, "SELECT * FROM " + getName() + ";");

			ResultSet rs = query.executeQuery();

			ResultSetMetaData metadata = rs.getMetaData();
			int columnCount = metadata.getColumnCount();

			for (int i = 1; i <= columnCount; i++) {
				String columnName = metadata.getColumnName(i);
				columns.add(columnName);
			}
			return columns;
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return columns;

	}

	public ArrayList<Column> getExact(String uuid) {
		// AdvancedCoreHook.getInstance().debug("Get Exact: " + uuid);
		loadPlayerIfNeeded(uuid);
		// AdvancedCoreHook.getInstance().debug("test one: " + uuid);
		return table.get(uuid);
	}

	public ArrayList<Column> getExactQuery(Column column) {
		ArrayList<Column> result = new ArrayList<>();
		String query = "SELECT * FROM " + getName() + " WHERE `" + column.getName() + "`=?" + ";";

		try {
			Query sql = new Query(mysql, query);

			sql.setParameter(1, column.getValue().toString());

			ResultSet rs = sql.executeQuery();
			rs.next();
			for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
				Column rCol = new Column(rs.getMetaData().getColumnLabel(i), DataType.STRING);
				// System.out.println(i + " " +
				// rs.getMetaData().getColumnLabel(i));
				rCol.setValue(rs.getString(i));
				// System.out.println(rCol.getValue());
				result.add(rCol);
			}
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

	public ArrayList<Column> getRowsQuery() {
		ArrayList<Column> result = new ArrayList<Column>();
		String sql = "SELECT uuid FROM " + getName() + ";";

		try {
			Query query = new Query(mysql, sql);
			ResultSet rs = query.executeQuery();

			while (rs.next()) {
				Column rCol = new Column("uuid", rs.getString("uuid"), DataType.STRING);
				result.add(rCol);
			}
		} catch (SQLException e) {
			return null;
		}

		return result;
	}

	public ArrayList<Column> getRowsNameQuery() {
		ArrayList<Column> result = new ArrayList<Column>();
		String sql = "SELECT PlayerName FROM " + getName() + ";";

		try {
			Query query = new Query(mysql, sql);
			ResultSet rs = query.executeQuery();

			while (rs.next()) {
				Column rCol = new Column("PlayerName", rs.getString("PlayerName"), DataType.STRING);
				result.add(rCol);
			}
		} catch (SQLException e) {
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

	public Set<String> getNames() {
		if (names == null || names.size() == 0) {
			names.clear();
			names.addAll(getNamesQuery());
			return names;
		}
		return names;
	}

	public ArrayList<String> getUuidsQuery() {
		ArrayList<String> uuids = new ArrayList<String>();

		ArrayList<Column> rows = getRowsQuery();
		for (Column c : rows) {
			uuids.add((String) c.getValue());
		}

		return uuids;
	}

	public ArrayList<String> getNamesQuery() {
		ArrayList<String> uuids = new ArrayList<String>();

		ArrayList<Column> rows = getRowsNameQuery();
		for (Column c : rows) {
			uuids.add((String) c.getValue());
		}

		return uuids;
	}

	public void insert(String index, String column, Object value, DataType dataType) {
		insertQuery(index, column, value, dataType);

	}

	public void insertQuery(String index, String column, Object value, DataType dataType) {
		synchronized (object5) {
			String query = "INSERT " + getName() + " ";

			query += "set uuid='" + index + "', ";
			query += column + "='" + value.toString() + "';";
			// AdvancedCoreHook.getInstance().extraDebug(query);

			try {
				new Query(mysql, query).executeUpdateAsync();
				uuids.add(index);
				names.add(PlayerUtils.getInstance().getPlayerName(UserManager.getInstance().getUser(new UUID(index)),
						index));
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}

	public boolean isUseBatchUpdates() {
		return useBatchUpdates;
	}

	public void loadData() {
		columns = getColumnsQueury();

		try {
			useBatchUpdates = mysql.getConnectionManager().getConnection().getMetaData().supportsBatchUpdates();

		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	private void loadPlayer(String uuid) {
		table.put(uuid, getExactQuery(new Column("uuid", uuid, DataType.STRING)));
		// AdvancedCoreHook.getInstance().extraDebug("Loading player: " + uuid);
	}

	public void loadPlayerIfNeeded(String uuid) {

		if (!containsKey(uuid)) {
			synchronized (object1) {
				// AdvancedCoreHook.getInstance().debug("Load: " + uuid);
				loadPlayer(uuid);
			}
		}
	}

	public void removePlayer(String uuid) {
		table.remove(uuid);
	}

	private Object object1 = new Object();
	private Object object2 = new Object();
	private Object object3 = new Object();
	private Object object4 = new Object();
	private Object object5 = new Object();

	public void update(String index, String column, Object value, DataType dataType) {

		checkColumn(column, dataType);
		if (getUuids().contains(index)) {
			synchronized (object2) {

				for (Column col : getExact(index)) {
					if (col.getName().equals(column)) {
						col.setValue(value);
					}
				}

				String query = "UPDATE " + getName() + " SET ";

				if (dataType == DataType.STRING) {
					query += "`" + column + "`='" + value.toString() + "'";
				} else {
					query += "`" + column + "`=" + value.toString();

				}
				query += " WHERE `uuid`=";
				query += "'" + index + "';";

				// AdvancedCoreHook.getInstance().extraDebug(query);
				addToQue(query);
			}
		} else {
			insert(index, column, value, dataType);
		}
	}

	public void addToQue(String query) {
		// if (!this.query.contains(query)) {
		this.query.add(query);
		// }
	}

	// private Object ob = new Object();

	public void updateBatch() {

		if (query.size() > 0) {
			AdvancedCoreHook.getInstance().extraDebug("Query Size: " + query.size());
			String sql = "";
			while (query.size() > 0) {

				String text = query.poll();
				if (!text.endsWith(";")) {
					text += ";";
				}
				sql += text;
			}

			try {
				if (useBatchUpdates) {
					Connection conn = mysql.getConnectionManager().getConnection();
					Statement st = conn.createStatement();
					for (String str : sql.split(";")) {
						st.addBatch(str);
					}
					st.executeBatch();
					st.close();
					conn.close();
				} else {
					for (String text : sql.split(";")) {
						try {
							Query query = new Query(mysql, text);
							query.executeUpdateAsync();
						} catch (SQLException e) {
							AdvancedCoreHook.getInstance().getPlugin().getLogger()
									.severe("Error occoured while executing sql: " + e.toString()
											+ ", turn debug on to see full stacktrace");
							AdvancedCoreHook.getInstance().debug(e);
						}
					}
				}
			} catch (SQLException e1) {
				AdvancedCoreHook.getInstance().extraDebug("Failed to send query: " + sql);
				e1.printStackTrace();
			}

		}

	}

	public int getMaxSize() {
		return maxSize;
	}
}
