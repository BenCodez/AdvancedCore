package com.Ben12345rocks.AdvancedCore.mysql;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
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

import com.Ben12345rocks.AdvancedCore.AdvancedCoreHook;
import com.Ben12345rocks.AdvancedCore.Util.Misc.CompatibleCacheBuilder;
import com.Ben12345rocks.AdvancedCore.mysql.api.queries.Query;
import com.Ben12345rocks.AdvancedCore.sql.Column;
import com.Ben12345rocks.AdvancedCore.sql.DataType;
import com.google.common.cache.CacheLoader;

public class MySQL {
	private com.Ben12345rocks.AdvancedCore.mysql.api.MySQL mysql;

	private List<String> columns = Collections.synchronizedList(new ArrayList<String>());

	// private HashMap<String, ArrayList<Column>> table;

	ConcurrentMap<String, ArrayList<Column>> table = CompatibleCacheBuilder.newBuilder().concurrencyLevel(4)
			.expireAfterAccess(20, TimeUnit.MINUTES).build(new CacheLoader<String, ArrayList<Column>>() {
				@Override
				public ArrayList<Column> load(String key) {
					return getExactQuery(new Column("uuid", key, DataType.STRING));
				}
			});

	private ConcurrentLinkedQueue<String> query = new ConcurrentLinkedQueue<String>();

	private String name;

	private Set<String> uuids = (Set<String>) Collections.synchronizedSet(new HashSet<String>());

	public MySQL(String tableName, String hostName, int port, String database, String user, String pass,
			int maxThreads) {
		if (AdvancedCoreHook.getInstance().getMaxMysqlSize() >= 0) {
			table = CompatibleCacheBuilder.newBuilder().concurrencyLevel(4).expireAfterAccess(20, TimeUnit.MINUTES)
					.maximumSize(AdvancedCoreHook.getInstance().getMaxMysqlSize())
					.build(new CacheLoader<String, ArrayList<Column>>() {
						@Override
						public ArrayList<Column> load(String key) {
							return getExactQuery(new Column("uuid", key, DataType.STRING));
						}
					});
		}
		name = tableName;
		mysql = new com.Ben12345rocks.AdvancedCore.mysql.api.MySQL(maxThreads);
		if (!mysql.connect(hostName, "" + port, user, pass, database)) {
			AdvancedCoreHook.getInstance().getPlugin().getLogger().warning("Failed to connect to MySQL");
		}
		mysql.getConnectionManager().open();
		try {
			Query q = new Query(mysql, "USE " + database + ";");
			q.executeUpdateAsync();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		String sql = "CREATE TABLE IF NOT EXISTS " + getName() + " (";
		sql += "uuid VARCHAR(191),";
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

		new Timer().schedule(new TimerTask() {

			@Override
			public void run() {
				updateBatch();
			}
		}, 10 * 1000, 500);

	}

	public synchronized void addColumn(String column, DataType dataType) {
		String sql = "ALTER TABLE " + getName() + " ADD COLUMN " + column + " text" + ";";

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

	public synchronized void checkColumn(String column, DataType dataType) {
		if (!getColumns().contains(column)) {
			if (!getColumnsQueury().contains(column)) {
				addColumn(column, dataType);
			}
		}
	}

	public void clearCache() {
		table.clear();
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
		if (!containsKey(uuid)) {
			loadPlayer(uuid);
		}
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

	public ArrayList<String> getUuidsQuery() {
		ArrayList<String> uuids = new ArrayList<String>();

		ArrayList<Column> rows = getRowsQuery();
		for (Column c : rows) {
			uuids.add((String) c.getValue());
		}

		return uuids;
	}

	public Set<String> getUuids() {
		if (uuids == null || uuids.size() == 0) {
			uuids.clear();
			uuids.addAll(getUuidsQuery());
			return uuids;
		}
		return uuids;
	}

	public synchronized void insert(String index, String column, Object value, DataType dataType) {
		insertQuery(index, column, value, dataType);

	}

	public synchronized void insertQuery(String index, String column, Object value, DataType dataType) {
		String query = "INSERT " + getName() + " ";

		query += "set uuid='" + index + "', ";
		query += column + "='" + value.toString() + "';";

		try {
			new Query(mysql, query).executeUpdateAsync();
			uuids.add(index);
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public void loadData() {
		columns = getColumnsQueury();
	}

	public void loadPlayer(String uuid) {
		table.put(uuid, getExactQuery(new Column("uuid", uuid, DataType.STRING)));
		if (uuids.contains(uuid)) {
			uuids.add(uuid);
		}
	}

	public void removePlayer(String uuid) {
		table.remove(uuid);
	}

	public synchronized void update(String index, String column, Object value, DataType dataType) {
		checkColumn(column, dataType);
		if (getUuids().contains(index)) {
			String query = "UPDATE " + getName() + " SET ";

			if (dataType == DataType.STRING) {
				query += "`" + column + "`='" + value.toString() + "'";
			} else {
				query += "`" + column + "`=" + value.toString();

			}
			query += " WHERE `uuid`=";
			query += "'" + index + "';";

			for (Column col : getExact(index)) {
				if (col.getName().equals(column)) {
					col.setValue(value);
				}
			}

			this.query.add(query);
		} else {
			insert(index, column, value, dataType);
		}
	}

	public synchronized void updateBatch() {
		try {
			String sql = "";
			if (query.size() > 0) {
				while (query.size() > 0) {
					String text = query.poll();
					if (!text.endsWith(";")) {
						text += ";";
					}
					sql += text;

				}
				AdvancedCoreHook.getInstance().debug(sql);
				Query query = new Query(mysql, sql);
				query.addBatch();
				query.executeBatchAsync();
			}
		} catch (SQLException e1) {
			e1.printStackTrace();
		}

		/*
		 * if (query.size() > 0) { String sql = ""; while (query.size() > 0) { String
		 * text = query.poll(); if (!text.endsWith(";")) { text += ";"; } sql += text; }
		 * 
		 * for (String text : sql.split(";")) { try { Query query = new Query(mysql,
		 * text); query.executeUpdateAsync(); } catch (SQLException e) {
		 * AdvancedCoreHook.getInstance().getPlugin().getLogger().
		 * severe("Error occoured while executing sql: " + e.toString() +
		 * ", turn debug on to see full stacktrace");
		 * AdvancedCoreHook.getInstance().debug(e); } }
		 * 
		 * }
		 */

	}

	public void loadPlayerIfNeeded(String uuid) {
		if (!containsKey(uuid)) {
			loadPlayer(uuid);
		}
	}
}
