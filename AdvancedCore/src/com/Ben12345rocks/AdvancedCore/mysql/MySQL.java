package com.Ben12345rocks.AdvancedCore.mysql;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.Ben12345rocks.AdvancedCore.AdvancedCoreHook;
import com.Ben12345rocks.AdvancedCore.Thread.Thread;
import com.Ben12345rocks.AdvancedCore.sql.Column;
import com.Ben12345rocks.AdvancedCore.sql.DataType;

import me.mrten.mysqlapi.queries.Query;

public class MySQL {
	private me.mrten.mysqlapi.MySQL mysql;

	public String getName() {
		return "VotingPlugin_Users";
	}

	public MySQL(String hostName, int port, String database, String user, String pass) {
		mysql = new me.mrten.mysqlapi.MySQL();
		if (!mysql.connect(hostName, "" + port, user, pass, database)) {
			AdvancedCoreHook.getInstance().getPlugin().getLogger().warning("Failed to connect to MySQL");
		}
		String sql = "CREATE TABLE IF NOT EXISTS " + getName() + " (";
		sql += "uuid VARCHAR(36),";
		sql += "PRIMARY KEY ( uuid )";
		sql += ");";
		Query query = new Query(mysql, sql);
		Thread.getInstance().run(new Runnable() {

			@Override
			public void run() {
				try {
					query.executeUpdate();
				} catch (SQLException e) {
					e.printStackTrace();
				}
			}
		});

	}

	public void update(String index, String column, Object value, DataType dataType) {
		checkColumn(column, dataType);
		if (containsKey(index)) {
			String query = "UPDATE " + getName() + " SET ";

			if (dataType == DataType.STRING) {
				query += "`" + column + "`='" + value.toString() + "'";
			} else {
				query += "`" + column + "`=" + value.toString();

			}
			query += "WHERE `uuid`=";
			query += "'" + index + "'";
			Query sql = new Query(mysql, query);

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

		} else {
			insert(index, column, value, dataType);
		}
	}

	public void insert(String index, String column, Object value, DataType dataType) {
		String query = "INSERT OR REPLACE INTO " + getName() + " (";

		query += "`" + index + "`, ";

		query += "`" + column + "`) ";

		query += "VALUES (";

		query += "?, ";

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

	public boolean containsKey(String index) {
		String sql = "SELECT uuid FROM " + getName();
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
		if (!getColumns().contains(column)) {
			addColumn(column, dataType);
		}
	}

	public ArrayList<String> getColumns() {
		Query query = new Query(mysql, "SELECT * FROM " + getName());
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
		String sql = "ALTER TABLE " + getName() + " ADD COLUMN " + column + " " + dataType.toString();
		Query query = new Query(mysql, sql);

		Thread.getInstance().run(new Runnable() {

			@Override
			public void run() {
				try {
					query.executeUpdate();
				} catch (SQLException e) {
					e.printStackTrace();
				}
			}
		});
	}

	public List<Column> getExact(Column column) {
		List<Column> result = new ArrayList<>();
		String query = "SELECT * FROM " + getName() + " WHERE `" + column.getName() + "`=?";
		Query sql = new Query(mysql, query);
		sql.setParameter(1, column.getValue().toString());

		try {
			ResultSet rs = sql.executeQuery();
			ArrayList<String> columns = getColumns();
			for (int i = 0; i < columns.size(); i++) {
				Column rCol = new Column(columns.get(i), DataType.STRING);
				rCol.setValue(rs.getString(i + 1));
				result.add(rCol);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		return result;
	}
}
