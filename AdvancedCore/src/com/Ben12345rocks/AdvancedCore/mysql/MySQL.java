package com.Ben12345rocks.AdvancedCore.mysql;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.LinkedBlockingQueue;

import com.Ben12345rocks.AdvancedCore.AdvancedCoreHook;
import com.Ben12345rocks.AdvancedCore.mysql.api.queries.Query;
import com.Ben12345rocks.AdvancedCore.sql.Column;
import com.Ben12345rocks.AdvancedCore.sql.DataType;

public class MySQL {
	private com.Ben12345rocks.AdvancedCore.mysql.api.MySQL mysql;

	private ArrayList<String> columns;

	private HashMap<String, ArrayList<Column>> table;

	private Queue<String> query;

	private String name;

	public String getName() {
		return name;
	}

	public MySQL(String tableName, String hostName, int port, String database, String user, String pass,
			int maxThreads) {
		this.name = tableName;
		mysql = new com.Ben12345rocks.AdvancedCore.mysql.api.MySQL(maxThreads);
		if (!mysql.connect(hostName, "" + port, user, pass, database)) {
			AdvancedCoreHook.getInstance().getPlugin().getLogger().warning("Failed to connect to MySQL");
		}
		try {
			Query q = new Query(mysql, "USE " + database + ";");
			q.executeUpdate();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		String sql = "CREATE TABLE IF NOT EXISTS " + getName() + " (";
		sql += "uuid VARCHAR(255),";
		sql += "PRIMARY KEY ( uuid )";
		sql += ");";
		Query query;
		try {
			query = new Query(mysql, sql);

			query.executeUpdateAsync();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		this.query = new LinkedBlockingQueue<>();

		loadData();

		new Timer().schedule(new TimerTask() {

			@Override
			public void run() {
				updateBatch();

				/*
				 * AdvancedCoreHook.getInstance().debug(ArrayUtils.getInstance()
				 * .makeStringList(columns)); String str = ""; for
				 * (Entry<String, ArrayList<Column>> entry : table.entrySet()) {
				 * str += entry.getKey() + ":"; for (Column col :
				 * entry.getValue()) { if (col.getValue() != null) { str +=
				 * col.getValue().toString() + ","; } } }
				 * AdvancedCoreHook.getInstance().debug(str);
				 */
			}
		}, 10 * 1000, 5 * 1000);
	}

	public synchronized void loadData() {
		columns = getColumnsQueury();
		table = new HashMap<String, ArrayList<Column>>();
		if (AdvancedCoreHook.getInstance().isPreloadTable()) {
			for (Column uuid : getRowsQuery()) {
				table.put((String) uuid.getValue(), getExactQuery(uuid));
			}
		}
	}

	public synchronized void updateBatch() {
		if (this.query.size() > 0) {

			String sql = "";
			while (query.size() > 0) {
				String text = query.poll();
				if (!text.endsWith(";")) {
					text += ";";
				}
				sql += text;
			}

			for (String text : sql.split(";")) {
				try {
					Query query = new Query(mysql, text);
					query.executeUpdate();
				} catch (SQLException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			// final String t = sql;

			// AdvancedCoreHook.getInstance().debug(t);

		}
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

			if (AdvancedCoreHook.getInstance().isPreloadTable()) {
				this.query.add(query);

				for (Column col : getExact(index)) {
					if (col.getName().equals(column)) {
						col.setValue(value);
					}
				}
			} else {
				try {
					Query q = new Query(mysql, query);
					q.executeUpdateAsync();
				} catch (SQLException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}

		} else {
			insert(index, column, value, dataType);
		}
	}

	public synchronized void insertQuery(String index, String column, Object value, DataType dataType) {
		String query = "INSERT " + getName() + " ";

		query += "set uuid='" + index + "', ";
		query += column + "='" + value.toString() + "';";

		try {
			Query sql = new Query(mysql, query);
			sql.executeUpdateAsync();
		} catch (SQLException e) {

			e.printStackTrace();
		}
	}

	public synchronized void insert(String index, String column, Object value, DataType dataType) {
		String query = "INSERT " + getName() + " ";

		query += "set uuid='" + index + "', ";
		query += column + "='" + value.toString() + "';";
		this.query.add(query);

		String uuid = index;
		if (AdvancedCoreHook.getInstance().isPreloadTable()) {
			if (!table.containsKey(uuid)) {
				ArrayList<Column> cols = new ArrayList<Column>();
				for (String col : columns) {
					cols.add(new Column(col, DataType.STRING));
				}
				for (Column col : cols) {
					if (col.getName().equals("uuid")) {
						col.setValue(index);
					} else if (col.getName().equals(column)) {
						col.setValue(value);
					}

				}
				table.put(uuid, cols);
			}
		} else {
			insertQuery(index, column, value, dataType);
		}

	}

	public synchronized boolean containsKeyQuery(String index) {
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

	public synchronized void checkColumn(String column, DataType dataType) {
		if (!columns.contains(column)) {
			addColumn(column, dataType);
		}
	}

	public synchronized ArrayList<String> getColumnsQueury() {
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

	public synchronized void addColumn(String column, DataType dataType) {
		String sql = "ALTER TABLE " + getName() + " ADD COLUMN " + column + " text" + ";";
		if (AdvancedCoreHook.getInstance().isPreloadTable()) {
			query.add(sql);
		} else {
			try {
				Query query = new Query(mysql, sql);
				query.executeUpdateAsync();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		columns.add(column);

		if (AdvancedCoreHook.getInstance().isPreloadTable()) {
			Column col = new Column(column, dataType);
			for (Entry<String, ArrayList<Column>> entry : table.entrySet()) {
				entry.getValue().add(col);
			}
		}
	}

	public synchronized ArrayList<Column> getExactQuery(Column column) {
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
		} catch (SQLException e) {
			e.printStackTrace();
		}

		return result;
	}

	public synchronized ArrayList<Column> getExact(String uuid) {
		if (AdvancedCoreHook.getInstance().isPreloadTable()) {
			if (table.containsKey(uuid)) {
				return table.get(uuid);
			}
			return new ArrayList<Column>();
		} else {
			return getExactQuery(new Column("uuid", uuid, DataType.STRING));
		}
	}

	public synchronized ArrayList<Column> getRowsQuery() {
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

	public synchronized ArrayList<String> getUuids() {
		ArrayList<String> uuids = new ArrayList<String>();
		if (AdvancedCoreHook.getInstance().isPreloadTable()) {

			for (String col : table.keySet()) {
				uuids.add(col);
			}

		} else {
			ArrayList<Column> rows = getRowsQuery();
			for (Column c : rows) {
				uuids.add((String) c.getValue());
			}
		}
		return uuids;
	}
}
