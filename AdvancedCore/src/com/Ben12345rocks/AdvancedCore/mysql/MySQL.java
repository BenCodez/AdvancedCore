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
import com.Ben12345rocks.AdvancedCore.Thread.Thread;
import com.Ben12345rocks.AdvancedCore.Util.Misc.ArrayUtils;
import com.Ben12345rocks.AdvancedCore.sql.Column;
import com.Ben12345rocks.AdvancedCore.sql.DataType;

import me.mrten.mysqlapi.queries.Query;

public class MySQL {
	private me.mrten.mysqlapi.MySQL mysql;

	private ArrayList<String> columns;

	private HashMap<Column, ArrayList<Column>> table;

	private Queue<String> query;

	public String getName() {
		return "VotingPlugin_Users";
	}

	public MySQL(String hostName, int port, String database, String user, String pass, int maxThreads) {
		mysql = new me.mrten.mysqlapi.MySQL(maxThreads);
		if (!mysql.connect(hostName, "" + port, user, pass, database)) {
			AdvancedCoreHook.getInstance().getPlugin().getLogger().warning("Failed to connect to MySQL");
		}
		Query q = new Query(mysql, "USE " + database + ";");
		try {
			q.executeUpdate();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		String sql = "CREATE TABLE IF NOT EXISTS " + getName() + " (";
		sql += "uuid VARCHAR(255),";
		sql += "PRIMARY KEY ( uuid )";
		sql += ");";
		System.out.println(sql);
		Query query = new Query(mysql, sql);
		query.executeUpdateAsync();

		this.query = new LinkedBlockingQueue<>();
		columns = getColumnsQueury();
		table = new HashMap<Column, ArrayList<Column>>();
		for (Column uuid : getRowsQuery()) {
			table.put(uuid, getExactQuery(uuid));
		}

		new Timer().schedule(new TimerTask() {

			@Override
			public void run() {
				updateBatch();

				AdvancedCoreHook.getInstance().debug(ArrayUtils.getInstance().makeStringList(columns));
				String str = "";
				for (Entry<Column, ArrayList<Column>> entry : table.entrySet()) {
					str += entry.getKey().getValue() + ":";
					for (Column col : entry.getValue()) {
						if (col.getValue() != null) {
							str += col.getValue().toString() + ",";
						}
					}
				}
				AdvancedCoreHook.getInstance().debug(str);
			}
		}, 10 * 1000, 5 * 1000);
	}

	public void updateBatch() {
		if (this.query.size() > 0) {

			String sql = "";
			while (query.size() > 0) {
				String text = query.poll();
				if (!text.endsWith(";")) {
					text += ";";
				}
				sql += text;
			}

			/*
			 * Connection connection = null; PreparedStatement statement = null;
			 * try { connection = mysql.getConnectionManager().getConnection();
			 * statement = connection.prepareStatement(sql); if
			 * (connection.getAutoCommit()) { connection.setAutoCommit(false); }
			 * statement.addBatch(); statement.executeBatch(); } catch
			 * (SQLException e) { e.printStackTrace(); } finally { if (statement
			 * != null) { try { statement.close(); } catch (SQLException e) { //
			 * TODO Auto-generated catch block e.printStackTrace(); } }
			 * 
			 * if (connection != null) { try { connection.commit();
			 * connection.close(); } catch (SQLException e) { // TODO
			 * Auto-generated catch block e.printStackTrace(); } } }
			 */

			for (String text : sql.split(";")) {
				Query query = new Query(mysql, text);
				try {
					query.executeUpdate();
				} catch (SQLException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			final String t = sql;

			AdvancedCoreHook.getInstance().debug(t);

		}
	}

	public void update(String index, String column, Object value, DataType dataType) {
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

			this.query.add(query);

			for (Column col : getExact(index)) {
				if (col.getName().equals(column)) {
					col.setValue(value);
				}
			}

		} else {
			insert(index, column, value, dataType);
		}
	}

	public void insertQuery(String index, String column, Object value, DataType dataType) {
		String query = "INSERT OR REPLACE INTO " + getName() + " (";

		query += "`" + index + "`, ";

		query += "`" + column + "`) ";

		query += "VALUES (";

		query += "?, ?)";

		query += ";";
		Query sql = new Query(mysql, query);
		sql.setParameter(1, index);
		sql.setParameter(2, value);

		Thread.getInstance().run(new Runnable() {

			@Override
			public void run() {
				try {
					sql.executeUpdate();
				} catch (SQLException e) {
					e.printStackTrace();
				}
			}
		});
	}

	public void insert(String index, String column, Object value, DataType dataType) {
		String query = "INSERT " + getName() + " ";

		query += "set uuid='" + index + "', ";
		query += column + "='" + value.toString() + "';";
		this.query.add(query);

		Column uuid = new Column("uuid", index, DataType.STRING);
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

	}

	public boolean containsKeyQuery(String index) {
		String sql = "SELECT uuid FROM " + getName() + ";";
		Query query = new Query(mysql, sql);
		try {
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

	public void checkColumn(String column, DataType dataType) {
		if (!columns.contains(column)) {
			addColumn(column, dataType);
		}
	}

	public ArrayList<String> getColumnsQueury() {
		Query query = new Query(mysql, "SELECT * FROM " + getName() + ";");
		ArrayList<String> columns = new ArrayList<String>();
		try {
			ResultSet rs = query.executeQuery();

			ResultSetMetaData metadata = rs.getMetaData();
			int columnCount = metadata.getColumnCount();

			for (int i = 1; i <= columnCount; i++) {
				String columnName = metadata.getColumnName(i);
				columns.add(columnName);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return columns;
	}

	public void addColumn(String column, DataType dataType) {
		String sql = "ALTER TABLE " + getName() + " ADD COLUMN " + column + " text" + ";";
		query.add(sql);
		columns.add(column);

		Column col = new Column(column, dataType);
		for (Entry<Column, ArrayList<Column>> entry : table.entrySet()) {
			entry.getValue().add(col);
		}
	}

	public ArrayList<Column> getExactQuery(Column column) {
		ArrayList<Column> result = new ArrayList<>();
		String query = "SELECT * FROM " + getName() + " WHERE `" + column.getName() + "`=?" + ";";

		Query sql = new Query(mysql, query);
		sql.setParameter(1, column.getValue().toString());

		ArrayList<String> columns = getColumnsQueury();
		try {
			ResultSet rs = sql.executeQuery();
			for (int i = 0; i < columns.size(); i++) {
				Column rCol = new Column(columns.get(i), DataType.STRING);
				rCol.setValue(rs.getString(columns.get(i)));
				System.out.println(i + " " + columns.get(i) + " = " + rCol.getValue());
				result.add(rCol);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		return result;
	}

	public ArrayList<Column> getExact(String uuid) {
		Column col = new Column("uuid", uuid, DataType.STRING);
		if (table.containsKey(col)) {
			return table.get(col);
		}
		return new ArrayList<Column>();
	}

	public ArrayList<Column> getRowsQuery() {
		ArrayList<Column> result = new ArrayList<Column>();
		String sql = "SELECT uuid FROM " + getName() + ";";

		Query query = new Query(mysql, sql);
		try {
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

	public ArrayList<String> getUuids() {
		ArrayList<String> uuids = new ArrayList<String>();
		for (Column col : table.keySet()) {
			uuids.add((String) col.getValue());
		}

		return uuids;
	}
}
