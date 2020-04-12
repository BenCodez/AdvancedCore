package com.Ben12345rocks.AdvancedCore.UserStorage.sql;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;

import com.Ben12345rocks.AdvancedCore.AdvancedCorePlugin;
import com.Ben12345rocks.AdvancedCore.UserStorage.sql.db.SQLite;
import com.Ben12345rocks.AdvancedCore.Util.Misc.CompatibleCacheBuilder;
import com.google.common.cache.CacheLoader;

import lombok.Getter;

public class Table {

	private static String getStringType(DataType dataType) {
		switch (dataType) {
			case STRING:
				return "VARCHAR";
			case INTEGER:
				return "INT";
			case FLOAT:
				return "FLOAT";
			default:
				return null;
		}
	}

	ConcurrentMap<String, ArrayList<Column>> table = CompatibleCacheBuilder.newBuilder().concurrencyLevel(6)
			.build(new CacheLoader<String, ArrayList<Column>>() {

				@Override
				public ArrayList<Column> load(String key) {
					return getExactQuery(new Column("uuid", key, DataType.STRING));
				}
			});

	private String name;
	private List<Column> columns = new ArrayList<>();
	private Column primaryKey;

	private SQLite sqLite;

	private Object object = new Object();

	private void loadTimer() {
		new Timer().schedule(new TimerTask() {

			@Override
			public void run() {
				updateBatch();
			}

		}, 10 * 1000, 500);
	}

	public Table(String name, Collection<Column> columns) {
		this.name = name;
		this.columns.addAll(columns);
		primaryKey = this.columns.get(0);
		loadTimer();
	}

	public Table(String name, Collection<Column> columns, Column primaryKey) {
		this.name = name;
		this.primaryKey = primaryKey;
		this.columns.addAll(columns);
		loadTimer();
	}

	public Table(String name, Column... columns) {
		this.name = name;
		for (Column column : columns) {
			this.columns.add(column);
		}
		primaryKey = this.columns.get(0);
		loadTimer();
	}

	public Table(String name, Column primaryKey, Column... columns) {
		this.name = name;
		this.primaryKey = primaryKey;
		for (Column column : columns) {
			this.columns.add(column);
		}
		loadTimer();
	}

	public void addColoumn(Column column) {
		if (hasColumn(column)) {
			return;
		}
		try {
			String query = "ALTER TABLE " + getName() + " ADD COLUMN " + column.getName() + " "
					+ column.getDataType().toString();
			PreparedStatement s = sqLite.getSQLConnection().prepareStatement(query);
			s.executeUpdate();
			s.close();
			columns.add(column);
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public void checkColumn(Column c) {
		if (!hasColumn(c)) {
			Column col = new Column(c.getName(), c.getDataType());
			addColoumn(col);
		}
	}

	public void checkColumns() {
		for (String col : getTableColumns()) {
			boolean has = false;
			for (int i = 0; i < columns.size(); i++) {
				if (col.equals(columns.get(i).getName())) {
					has = true;
				}
			}
			if (!has) {
				columns.add(new Column(col, DataType.STRING));
			}
		}
	}

	public boolean containsKey(Column column) {
		for (Column col : getRows()) {
			if (col.getValue().equals(column.getValue())) {
				return true;
			}
		}
		return false;
	}

	public void delete(Column column) {
		if (column.getName().equalsIgnoreCase(primaryKey.getName())) {
			String query = "DELETE FROM " + getName() + " WHERE `" + column.getName() + "`=?";
			try {
				PreparedStatement s = sqLite.getSQLConnection().prepareStatement(query);
				if (column.dataType == DataType.STRING) {
					s.setString(1, column.getValue().toString());
				} else if (column.dataType == DataType.INTEGER) {
					s.setInt(1, Integer.parseInt(column.getValue().toString()));
				} else {
					s.setFloat(1, Float.parseFloat(column.getValue().toString()));
				}
				s.executeUpdate();
				s.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		} else {
			System.out.println("Primary key must be used!");
		}
	}

	public List<List<Column>> getAll() {
		List<List<Column>> results = new ArrayList<>();
		String query = "SELECT * FROM " + getName();
		try {
			PreparedStatement s = sqLite.getSQLConnection().prepareStatement(query);
			ResultSet rs = s.executeQuery();
			while (rs.next()) {
				List<Column> result = new ArrayList<>();
				for (int i = 0; i < getColumns().size(); i++) {
					Column rCol = new Column(getColumns().get(i).getName(), getColumns().get(i).dataType,
							getColumns().get(i).limit);
					if (getColumns().get(i).dataType == DataType.STRING) {
						s.setString(1, getColumns().get(i).getValue().toString());
					} else if (getColumns().get(i).dataType == DataType.INTEGER) {
						s.setInt(1, Integer.parseInt(getColumns().get(i).getValue().toString()));
					} else {
						s.setFloat(1, Float.parseFloat(getColumns().get(i).getValue().toString()));
					}
					result.add(rCol);
				}
				results.add(result);
			}
			sqLite.close(s, rs);
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return results;
	}

	public List<Column> getColumns() {
		return columns;
	}

	public ArrayList<Column> getExact(Column column) {
		String uuid = column.getValue().toString();
		// AdvancedCorePlugin.getInstance().debug("Get Exact: " + uuid);
		loadPlayerIfNeeded(uuid);
		// AdvancedCorePlugin.getInstance().debug("test one: " + uuid);
		return table.get(uuid);
	}

	public boolean containsKey(String uuid) {
		if (table.containsKey(uuid)) {
			return true;
		}
		return false;
	}

	private void loadPlayer(String uuid) {
		if (playerExists(uuid)) {
			table.put(uuid, getExactQuery(new Column("uuid", uuid, DataType.STRING)));
		}
	}

	private Object object1 = new Object();

	public void loadPlayerIfNeeded(String uuid) {
		if (!containsKey(uuid)) {
			// AdvancedCorePlugin.getInstance().debug("Caching " + uuid);
			synchronized (object1) {
				loadPlayer(uuid);
			}
		}
	}

	public boolean playerExists(String uuid) {
		List<Column> cols = AdvancedCorePlugin.getInstance().getSQLiteUserTable().getRows();
		ArrayList<String> uuids = new ArrayList<String>();
		for (Column col : cols) {
			uuids.add((String) col.getValue());
		}
		if (uuids.contains(uuid)) {
			return true;
		}
		return false;
	}

	public ArrayList<Column> getExactQuery(Column column) {
		checkColumns();
		ArrayList<Column> result = new ArrayList<Column>();
		String query = "SELECT * FROM " + getName() + " WHERE `" + column.getName() + "`=?";
		try {
			synchronized (object) {
				PreparedStatement s = sqLite.getSQLConnection().prepareStatement(query);
				String value = "";
				if (column.getValue() != null) {
					value = column.getValue().toString();
				}
				if (column.dataType == DataType.STRING) {
					s.setString(1, value);
				} else if (column.dataType == DataType.INTEGER) {
					s.setInt(1, Integer.parseInt(value));
				} else {
					s.setFloat(1, Float.parseFloat(value));
				}
				ResultSet rs = s.executeQuery();
				try {
					for (int i = 0; i < getColumns().size(); i++) {
						Column rCol = new Column(getColumns().get(i).getName(), getColumns().get(i).dataType,
								getColumns().get(i).limit);
						if (rCol.dataType == DataType.STRING) {
							rCol.setValue(rs.getString(i + 1));
						} else if (rCol.dataType == DataType.INTEGER) {
							rCol.setValue(rs.getInt(i + 1));
						} else {
							rCol.setValue(rs.getFloat(i + 1));
						}
						result.add(rCol);
					}
					sqLite.close(s, rs);
				} catch (SQLException e) {
					s.close();
					return null;
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return result;
	}

	public String getName() {
		return name;
	}

	public ArrayList<String> getNames() {
		ArrayList<String> names = new ArrayList<String>();
		for (Column col : getRowsNames()) {
			if (col.getValue() != null) {
				names.add(col.getValue().toString());
			}
		}
		return names;
	}

	public Column getPrimaryKey() {
		return primaryKey;
	}

	public String getCreateQuery() {
		String query = "CREATE TABLE IF NOT EXISTS " + getName() + " (";
		for (Column column : getColumns()) {
			query += "`" + column.name + "` ";
			query += Table.getStringType(column.dataType);
			query += (column.limit > 0 ? " (" + column.limit + "), " : ", ");
		}
		query += "PRIMARY KEY (`" + primaryKey.getName() + "`)";
		query += ");";
		return query;
	}

	public List<Column> getRows() {
		List<Column> result = new ArrayList<Column>();
		String query = "SELECT uuid FROM " + getName();

		try {
			PreparedStatement s = sqLite.getSQLConnection().prepareStatement(query);
			ResultSet rs = s.executeQuery();
			try {
				while (rs.next()) {
					Column rCol = new Column("uuid", rs.getString("uuid"), DataType.STRING);
					result.add(rCol);
				}
				sqLite.close(s, rs);
			} catch (SQLException e) {
				s.close();
				return null;
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}

		return result;
	}

	public List<Column> getRowsNames() {
		checkColumn(new Column("PlayerName", DataType.STRING));
		List<Column> result = new ArrayList<Column>();
		String query = "SELECT PlayerName FROM " + getName();

		try {
			PreparedStatement s = sqLite.getSQLConnection().prepareStatement(query);
			ResultSet rs = s.executeQuery();
			try {
				while (rs.next()) {
					Column rCol = new Column("PlayerName", rs.getString("PlayerName"), DataType.STRING);
					result.add(rCol);
				}
				sqLite.close(s, rs);
			} catch (SQLException e) {
				s.close();
				return null;
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}

		return result;
	}

	public ArrayList<String> getTableColumns() {
		ArrayList<String> columns = new ArrayList<String>();
		String query = "SELECT * FROM " + getName();
		try {
			PreparedStatement s = sqLite.getSQLConnection().prepareStatement(query);
			ResultSet rs = s.executeQuery();
			ResultSetMetaData metadata = rs.getMetaData();
			int columnCount = metadata.getColumnCount();

			for (int i = 1; i <= columnCount; i++) {
				String columnName = metadata.getColumnName(i);

				columns.add(columnName);
			}

			sqLite.close(s, rs);
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return columns;
	}

	public boolean hasColumn(Column column) {
		return getTableColumns().contains(column.getName());
	}

	public void insert(List<Column> columns) {
		for (Column c : columns) {
			checkColumn(c);
		}
		String query = "INSERT OR REPLACE INTO " + getName() + " (";
		for (Column column : columns) {
			if (columns.indexOf(column) < columns.size() - 1) {
				query += "`" + column.getName() + "`, ";
			} else {
				query += "`" + column.getName() + "`) ";
			}
		}
		query += "VALUES (";
		for (int i = 0; i < columns.size(); i++) {
			if (i < columns.size() - 1) {
				query += "?, ";
			} else {
				query += "?)";
			}
		}
		query += ";";
		try {
			PreparedStatement s = sqLite.getSQLConnection().prepareStatement(query);
			for (int i = 0; i < columns.size(); i++) {
				if (columns.get(i).dataType == DataType.STRING) {
					s.setString(i + 1, columns.get(i).getValue().toString());
				} else if (columns.get(i).dataType == DataType.INTEGER) {
					s.setInt(i + 1, Integer.parseInt(columns.get(i).getValue().toString()));
				} else {
					s.setFloat(i + 1, Float.parseFloat(columns.get(i).getValue().toString()));
				}
			}
			s.executeUpdate();
			s.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}

	}

	public List<List<Column>> search(Column column) {
		List<List<Column>> results = new ArrayList<>();
		if (!column.getName().equalsIgnoreCase(primaryKey.getName())) {
			String query = "SELECT * FROM " + getName() + " WHERE `" + column.getName() + "`=?";
			try {
				PreparedStatement s = sqLite.getSQLConnection().prepareStatement(query);
				if (column.dataType == DataType.STRING) {
					s.setString(1, column.getValue().toString());
				} else if (column.dataType == DataType.INTEGER) {
					s.setInt(1, Integer.parseInt(column.getValue().toString()));
				} else {
					s.setFloat(1, Float.parseFloat(column.getValue().toString()));
				}
				ResultSet rs = s.executeQuery();
				while (rs.next()) {
					List<Column> result = new ArrayList<>();
					for (int i = 0; i < getColumns().size(); i++) {
						Column rCol = new Column(getColumns().get(i).getName(), getColumns().get(i).dataType,
								getColumns().get(i).limit);

						if (getColumns().get(i).dataType == DataType.STRING) {
							if (column.getValue() == null || column.getValue().equals(rs.getString(i + 1))) {
								rCol.setValue(rs.getString(i + 1));
							}
						} else if (getColumns().get(i).dataType == DataType.INTEGER) {
							if (column.getValue() == null || column.getValue().equals(rs.getString(i + 1))) {
								rCol.setValue(rs.getInt(i + 1));
							}
						} else {
							if (column.getValue() == null || column.getValue().equals(rs.getString(i + 1))) {
								rCol.setValue(rs.getFloat(i + 1));
							}
						}
						result.add(rCol);
					}
					results.add(result);
				}
				sqLite.close(s, rs);
			} catch (SQLException e) {
				e.printStackTrace();
			}
			return results;
		} else {
			return null;
		}
	}

	public void setColumns(List<Column> columns) {
		this.columns = columns;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setPrimaryKey(Column primaryKey) {
		this.primaryKey = primaryKey;
	}

	public void setSqLite(SQLite sqLite) {
		this.sqLite = sqLite;
	}

	public void playerJoin(String uuid) {
		if (AdvancedCorePlugin.getInstance().getOptions().isClearCacheOnJoin()) {
			removePlayer(uuid);
		}
	}

	public void removePlayer(String uuid) {
		table.remove(uuid);
	}

	public void update(Column primaryKey, List<Column> columns) {
		for (Column c : columns) {
			checkColumn(c);
		}
		synchronized (object) {
			if (containsKey(primaryKey.getValue().toString()) || containsKey(primaryKey)) {

				for (Column col : getExact(primaryKey)) {
					for (Column column : columns) {
						if (col.getName().equals(column.getName())) {
							col.setValue(column.getValue());
						}
					}
				}

				String query = "UPDATE " + getName() + " SET ";
				for (Column column : columns) {
					if (column.dataType == DataType.STRING) {
						query += "`" + column.getName() + "`='" + column.getValue().toString() + "'";
					} else {
						query += "`" + column.getName() + "`=" + column.getValue().toString();
					}
					if (columns.indexOf(column) == columns.size() - 1) {
						query += " ";
					} else {
						query += ", ";
					}
				}
				query += "WHERE `" + primaryKey.getName() + "`=";
				if (primaryKey.dataType == DataType.STRING) {
					query += "'" + primaryKey.getValue().toString() + "'";
				} else {
					query += primaryKey.getValue().toString();
				}
				addToQue(query);

			} else {
				insert(columns);
			}
		}
	}

	@Getter
	private ConcurrentLinkedQueue<String> query = new ConcurrentLinkedQueue<String>();

	public void addToQue(String query) {
		this.query.add(query);
	}

	public void updateBatch() {
		if (query.size() > 0) {
			AdvancedCorePlugin.getInstance().extraDebug("Query Size: " + query.size());
			String sql = "";
			while (query.size() > 0) {
				String text = query.poll();
				if (!text.endsWith(";")) {
					text += ";";
				}
				sql += text;
			}

			try {
				for (String text : sql.split(";")) {
					try {
						PreparedStatement s = sqLite.getSQLConnection().prepareStatement(text);
						s.executeUpdate();
						s.close();
					} catch (SQLException e) {
						AdvancedCorePlugin.getInstance().getLogger().severe("Error occoured while executing sql: "
								+ e.toString() + ", turn debug on to see full stacktrace");
						AdvancedCorePlugin.getInstance().debug(e);
					}
				}

			} catch (Exception e1) {
				AdvancedCorePlugin.getInstance().extraDebug("Failed to send query: " + sql);
				e1.printStackTrace();
			}
		}

	}

}
