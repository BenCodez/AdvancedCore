package com.bencodez.advancedcore.api.user.userstorage.sql;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.messages.PlaceholderUtils;
import com.bencodez.advancedcore.api.user.usercache.keys.UserDataKey;
import com.bencodez.advancedcore.api.user.usercache.keys.UserDataKeyInt;
import com.bencodez.simpleapi.sql.Column;
import com.bencodez.simpleapi.sql.DataType;
import com.bencodez.simpleapi.sql.data.DataValueBoolean;
import com.bencodez.simpleapi.sql.data.DataValueInt;
import com.bencodez.simpleapi.sql.data.DataValueString;
import com.bencodez.simpleapi.sql.sqlite.db.SQLite;

public class UserTable extends com.bencodez.simpleapi.sql.sqlite.Table {

	private List<Column> columns = new ArrayList<>();
	private String name;
	private Object object = new Object();

	private Column primaryKey;

	private SQLite sqLite;
	private AdvancedCorePlugin plugin;

	public UserTable(AdvancedCorePlugin plugin, String name, Collection<Column> columns) {
		this.name = name;
		this.columns.addAll(columns);
		primaryKey = this.columns.get(0);
		this.plugin = plugin;
	}

	public UserTable(AdvancedCorePlugin plugin, String name, Collection<Column> columns, Column primaryKey) {
		this.name = name;
		this.primaryKey = primaryKey;
		this.columns.addAll(columns);
		this.plugin = plugin;
	}

	/**
	 * Streams every user row from SQLite without building a giant in-memory map.
	 *
	 * @param perUser    called once per row with (uuid, columns)
	 * @param onFinished called once at the end with the number of rows processed
	 */
	public void forEachUser(BiConsumer<UUID, ArrayList<Column>> perUser, Consumer<Integer> onFinished) {
		int processed = 0;
		final String query = "SELECT * FROM " + getName() + ";";

		try (PreparedStatement ps = sqLite.getSQLConnection().prepareStatement(query, ResultSet.TYPE_FORWARD_ONLY,
				ResultSet.CONCUR_READ_ONLY); ResultSet rs = ps.executeQuery()) {

			// Helps on some JDBC drivers (SQLite varies, but harmless)
			try {
				ps.setFetchSize(500);
			} catch (Exception ignored) {
			}

			// Compute metadata ONCE
			final ResultSetMetaData meta = rs.getMetaData();
			final int colCount = meta.getColumnCount();

			// Precompute how each column should be read
			final String[] colNames = new String[colCount + 1];
			final byte[] kind = new byte[colCount + 1]; // 0=string, 1=int, 2=bool
			int uuidIndex = -1;

			for (int i = 1; i <= colCount; i++) {
				String name = meta.getColumnLabel(i);
				colNames[i] = name;

				if ("uuid".equalsIgnoreCase(name)) {
					uuidIndex = i;
				}

				if (plugin.getUserManager().getDataManager().isInt(name)) {
					kind[i] = 1;
				} else if (plugin.getUserManager().getDataManager().isBoolean(name)) {
					kind[i] = 2;
				} else {
					kind[i] = 0;
				}
			}

			while (rs.next()) {
				UUID uuid = null;

				// Fast path: read uuid column directly (avoid scanning for it)
				if (uuidIndex > 0) {
					String uuidStr = rs.getString(uuidIndex);
					if (uuidStr != null && !uuidStr.isEmpty() && !"null".equalsIgnoreCase(uuidStr)) {
						try {
							uuid = UUID.fromString(uuidStr);
						} catch (IllegalArgumentException ignored) {
							// bad uuid; skip row below
						}
					}
				}

				// Build cols only if we have a valid uuid (saves a ton if db has junk)
				if (uuid == null) {
					continue;
				}

				ArrayList<Column> cols = new ArrayList<>(colCount);

				for (int i = 1; i <= colCount; i++) {
					String columnName = colNames[i];
					Column rCol;

					switch (kind[i]) {
					case 1: { // int
						rCol = new Column(columnName, DataType.INTEGER);
						int v;
						try {
							v = rs.getInt(i);
							if (rs.wasNull())
								v = 0;
						} catch (Exception e) {
							String data = rs.getString(i);
							if (data != null) {
								try {
									v = Integer.parseInt(data);
								} catch (NumberFormatException ex) {
									v = 0;
								}
							} else {
								v = 0;
							}
						}
						rCol.setValue(new DataValueInt(v));
						break;
					}
					case 2: { // bool
						rCol = new Column(columnName, DataType.BOOLEAN);
						// Keep same semantics as your original: Boolean.valueOf(String)
						rCol.setValue(new DataValueBoolean(Boolean.valueOf(rs.getString(i))));
						break;
					}
					default: { // string
						rCol = new Column(columnName, DataType.STRING);
						rCol.setValue(new DataValueString(rs.getString(i)));
						break;
					}
					}

					cols.add(rCol);
				}

				processed++;
				perUser.accept(uuid, cols);
			}

		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			if (onFinished != null) {
				onFinished.accept(processed);
			}
		}
	}

	public UserTable(AdvancedCorePlugin plugin, String name, Column... columns) {
		this.name = name;
		for (Column column : columns) {
			this.columns.add(column);
		}
		primaryKey = this.columns.get(0);
		this.plugin = plugin;
	}

	public UserTable(AdvancedCorePlugin plugin, String name, Column primaryKey, Column... columns) {
		this.name = name;
		this.primaryKey = primaryKey;
		for (Column column : columns) {
			this.columns.add(column);
		}
		this.plugin = plugin;
	}

	public void addColoumn(Column column) {
		if (hasColumn(column)) {
			return;
		}
		try {
			String query = "ALTER TABLE " + getName() + " ADD COLUMN `" + column.getName() + "` "
					+ column.getDataType().toString();
			PreparedStatement s = sqLite.getSQLConnection().prepareStatement(query);
			s.executeUpdate();
			s.close();
			columns.add(column);
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public void addColoumn(UserDataKey column) {
		if (getTableColumns().contains(column.getKey())) {
			return;
		}
		try {
			String query = "ALTER TABLE " + getName() + " ADD COLUMN `" + column.getKey() + "` "
					+ column.getColumnType();
			PreparedStatement s = sqLite.getSQLConnection().prepareStatement(query);
			s.executeUpdate();
			s.close();
			if (column instanceof UserDataKeyInt) {
				columns.add(new Column(column.getKey(), DataType.INTEGER));
			} else {
				columns.add(new Column(column.getKey(), DataType.STRING));
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public void addCustomColumns() {
		// add custom column types
		for (UserDataKey key : plugin.getUserManager().getDataManager().getKeys()) {
			addColoumn(key);
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
			for (Column column : columns) {
				if (col != null) {
					if (col.equals(column.getName())) {
						has = true;
					}
				}
			}
			if (!has) {
				columns.add(new Column(col, DataType.STRING));
			}
		}
	}

	public boolean containsKey(String index) {
		String query = "SELECT uuid FROM " + getName();

		try {
			PreparedStatement s = sqLite.getSQLConnection().prepareStatement(query);
			ResultSet rs = s.executeQuery();
			/*
			 * Query query = new Query(mysql, sql); ResultSet rs = query.executeQuery();
			 */
			while (rs.next()) {
				String str = rs.getString("uuid");
				if (str != null && str.equals(index)) {
					rs.close();
					s.close();
					return true;
				}
			}
			rs.close();

		} catch (SQLException ex) {
			ex.printStackTrace();
		}
		return false;
	}

	public void copyColumnData(String columnFromName, String columnToName, DataType dataType) {
		checkColumn(new Column(columnToName, dataType));
		checkColumn(new Column(columnFromName, dataType));
		String sql = "UPDATE `" + getName() + "` SET `" + columnToName + "` = `" + columnFromName + "`;";
		try {
			PreparedStatement s = sqLite.getSQLConnection().prepareStatement(sql);
			s.executeUpdate();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public void delete(Column column) {
		if (column.getName().equalsIgnoreCase(primaryKey.getName())) {
			String query = "DELETE FROM " + getName() + " WHERE `" + column.getName() + "`=?";
			try {
				PreparedStatement s = sqLite.getSQLConnection().prepareStatement(query);
				if (column.getValue().isString()) {
					s.setString(1, column.getValue().getString());
				} else if (column.getValue().isInt()) {
					s.setInt(1, column.getValue().getInt());
				} else {
					s.setBoolean(1, column.getValue().getBoolean());
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

	public void executeQuery(String str) {
		try {
			PreparedStatement s = sqLite.getSQLConnection()
					.prepareStatement(PlaceholderUtils.replacePlaceHolder(str, "tablename", getName()));
			s.executeUpdate();
		} catch (SQLException e) {
			e.printStackTrace();
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
					Column rCol = new Column(getColumns().get(i).getName(), getColumns().get(i).getDataType(),
							getColumns().get(i).getLimit());
					if (getColumns().get(i).getValue().isString()) {
						s.setString(1, getColumns().get(i).getValue().getString());
					} else if (getColumns().get(i).getValue().isInt()) {
						s.setInt(1, getColumns().get(i).getValue().getInt());
					} else {
						s.setBoolean(1, getColumns().get(i).getValue().getBoolean());
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

	public HashMap<UUID, ArrayList<Column>> getAllQuery() {
		HashMap<UUID, ArrayList<Column>> result = new HashMap<>();
		String query = "SELECT * FROM " + getName() + ";";

		try {
			PreparedStatement s = sqLite.getSQLConnection().prepareStatement(query);
			ResultSet rs = s.executeQuery();

			while (rs.next()) {
				ArrayList<Column> cols = new ArrayList<>();
				UUID uuid = null;
				for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
					String columnName = rs.getMetaData().getColumnLabel(i);
					Column rCol = null;

					if (plugin.getUserManager().getDataManager().isInt(columnName)) {
						try {
							rCol = new Column(columnName, DataType.INTEGER);
							rCol.setValue(new DataValueInt(rs.getInt(i)));
						} catch (Exception e) {
							rCol = new Column(columnName, DataType.INTEGER);
							String data = rs.getString(i);
							if (data != null) {
								try {
									rCol.setValue(new DataValueInt(Integer.parseInt(data)));
								} catch (NumberFormatException ex) {
									rCol.setValue(new DataValueInt(0));
								}
							} else {
								rCol.setValue(new DataValueInt(0));
							}
						}
					} else if (plugin.getUserManager().getDataManager().isBoolean(columnName)) {
						rCol = new Column(columnName, DataType.BOOLEAN);
						rCol.setValue(new DataValueBoolean(Boolean.valueOf(rs.getString(i))));
					} else {
						rCol = new Column(columnName, DataType.STRING);
						rCol.setValue(new DataValueString(rs.getString(i)));
						if (columnName.equals("uuid")) {
							uuid = UUID.fromString(rs.getString(i));
						}
					}
					cols.add(rCol);
				}
				result.put(uuid, cols);
			}
			rs.close();
			return result;
		} catch (SQLException e) {
			e.printStackTrace();
		} catch (ArrayIndexOutOfBoundsException e) {
			e.printStackTrace();
		}

		return result;
	}

	public List<Column> getColumns() {
		return columns;
	}

	public List<String> getColumnsString() {
		List<Column> column = getColumns();
		ArrayList<String> list = new ArrayList<>();
		for (Column col : column) {
			list.add(col.getName());
		}
		return list;
	}

	public ArrayList<Column> getExact(Column column) {
		ArrayList<Column> result = new ArrayList<>();

		String query = "SELECT * FROM " + getName() + " WHERE `" + column.getName() + "`=?";
		try {
			synchronized (object) {
				PreparedStatement s = sqLite.getSQLConnection().prepareStatement(query);
				if (column.getValue().isString()) {
					s.setString(1, column.getValue().getString());
				} else if (column.getValue().isInt()) {
					s.setInt(1, column.getValue().getInt());
				} else {
					s.setBoolean(1, column.getValue().getBoolean());
				}
				ResultSet rs = s.executeQuery();

				if (rs.next()) {
					for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
						String columnName = rs.getMetaData().getColumnLabel(i);
						Column rCol = null;
						if (plugin.getUserManager().getDataManager().isInt(columnName)) {
							rCol = new Column(columnName, DataType.INTEGER);
							rCol.setValue(new DataValueInt(rs.getInt(i)));
						} else if (plugin.getUserManager().getDataManager().isBoolean(columnName)) {
							rCol = new Column(columnName, DataType.BOOLEAN);
							rCol.setValue(new DataValueBoolean(Boolean.valueOf(rs.getString(i))));
						} else {
							rCol = new Column(columnName, DataType.STRING);
							rCol.setValue(new DataValueString(rs.getString(i)));
						}
						result.add(rCol);
					}
				}
				rs.close();
				s.close();
				return result;
			}

		} catch (SQLException e) {
			e.printStackTrace();
		} catch (ArrayIndexOutOfBoundsException e) {
		}

		for (Column col : getColumns()) {
			result.add(new Column(col.getName(), col.getDataType()));
		}
		return result;
	}

	@Override
	public String getName() {
		return name;
	}

	public ArrayList<String> getNames() {
		ArrayList<String> names = new ArrayList<>();
		for (Column col : getRowsNames()) {
			if (col.getValue() != null && col.getValue().isString()) {
				names.add(col.getValue().getString());
			}
		}
		return names;
	}

	public ArrayList<Integer> getNumbersInColumn(String column) {
		ArrayList<Integer> result = new ArrayList<>();
		String sqlStr = "SELECT " + column + " FROM " + getName() + ";";

		try {
			PreparedStatement s = sqLite.getSQLConnection().prepareStatement(sqlStr);
			ResultSet rs = s.executeQuery();

			while (rs.next()) {
				result.add(rs.getInt(column));
			}

			sqLite.close(s, rs);
		} catch (SQLException e) {
			e.printStackTrace();
		}

		return result;
	}

	public Column getPrimaryKey() {
		return primaryKey;
	}

	@Override
	public String getQuery() {
		String sql = "CREATE TABLE IF NOT EXISTS " + getName() + " (";
		sql += "uuid VARCHAR(37), ";
		// add custom column types
		for (UserDataKey key : AdvancedCorePlugin.getInstance().getUserManager().getDataManager().getKeys()) {
			sql += key.getKey() + " " + key.getColumnType() + ", ";
		}
		sql += "PRIMARY KEY ( uuid ));";
		return sql;
	}

	public List<Column> getRows() {
		List<Column> result = new ArrayList<>();
		String query = "SELECT uuid FROM " + getName();

		try {
			PreparedStatement s = sqLite.getSQLConnection().prepareStatement(query);
			ResultSet rs = s.executeQuery();
			try {
				while (rs.next()) {
					Column rCol = new Column("uuid", new DataValueString(rs.getString("uuid")));
					result.add(rCol);
				}
				sqLite.close(s, rs);
			} catch (SQLException e) {
				s.close();
				rs.close();
				e.printStackTrace();
				return null;
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}

		return result;
	}

	public List<Column> getRowsNames() {
		checkColumn(new Column("PlayerName", DataType.STRING));
		List<Column> result = new ArrayList<>();
		String query = "SELECT PlayerName FROM " + getName();

		try {
			PreparedStatement s = sqLite.getSQLConnection().prepareStatement(query);
			ResultSet rs = s.executeQuery();
			try {
				while (rs.next()) {
					Column rCol = new Column("PlayerName", new DataValueString(rs.getString("PlayerName")));
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
		ArrayList<String> columns = new ArrayList<>();
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

	public String getUUID(String playerName) {
		String query = "SELECT uuid FROM " + getName() + " WHERE " + "PlayerName" + "='" + playerName + "';";

		try (PreparedStatement sql = sqLite.getSQLConnection().prepareStatement(query)) {
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
				if (columns.get(i).getValue() != null) {
					if (columns.get(i).getValue().isString()) {
						s.setString(i + 1, columns.get(i).getValue().getString());
					} else if (columns.get(i).getValue().isInt()) {
						s.setInt(i + 1, columns.get(i).getValue().getInt());
					} else {
						s.setBoolean(i + 1, columns.get(i).getValue().getBoolean());
					}
				} else {
					s.setString(i + 1, "");
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
		if (column.getName().equalsIgnoreCase(primaryKey.getName())) {
			return null;
		}
		String query = "SELECT * FROM " + getName() + " WHERE `" + column.getName() + "`=?";
		try {
			PreparedStatement s = sqLite.getSQLConnection().prepareStatement(query);
			if (column.getValue().isString()) {
				s.setString(1, column.getValue().getString());
			} else if (column.getValue().isInt()) {
				s.setInt(1, column.getValue().getInt());
			} else {
				s.setBoolean(1, column.getValue().getBoolean());
			}

			ResultSet rs = s.executeQuery();
			while (rs.next()) {
				List<Column> result = new ArrayList<>();
				for (int i = 0; i < getColumns().size(); i++) {
					Column rCol = new Column(getColumns().get(i).getName(), getColumns().get(i).getDataType(),
							getColumns().get(i).getLimit());

					if (getColumns().get(i).getValue().isString()) {
						rCol.setValue(new DataValueString(rs.getString(i + 1)));
					} else if (getColumns().get(i).getValue().isInt()) {
						rCol.setValue(new DataValueInt(rs.getInt(i + 1)));
					} else if (getColumns().get(i).getValue().isBoolean()) {
						rCol.setValue(new DataValueBoolean(rs.getBoolean(i + 1)));
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

	public void setColumns(List<Column> columns) {
		this.columns = columns;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setPrimaryKey(Column primaryKey) {
		this.primaryKey = primaryKey;
	}

	@Override
	public void setSqLite(SQLite sqLite) {
		this.sqLite = sqLite;
	}

	public void update(Column primaryKey, List<Column> columns) {
		for (Column c : columns) {
			checkColumn(c);
		}
		if (containsKey(primaryKey.getValue().toString())) {
			synchronized (object) {
				String query = "UPDATE " + getName() + " SET ";
				for (Column column : columns) {
					if (column.getValue().isString()) {
						query += "`" + column.getName() + "`='" + column.getValue().getString() + "'";
					} else if (column.getValue().isBoolean()) {
						query += "`" + column.getName() + "`=" + column.getValue().getBoolean();
					} else if (column.getValue().isInt()) {
						query += "`" + column.getName() + "`=" + column.getValue().getInt();
					}
					if (columns.indexOf(column) == columns.size() - 1) {
						query += " ";
					} else {
						query += ", ";
					}
				}
				query += "WHERE `" + primaryKey.getName() + "`=";
				query += "'" + primaryKey.getValue().getString() + "'";
				try {
					PreparedStatement s = sqLite.getSQLConnection().prepareStatement(query);
					s.executeUpdate();
					s.close();
				} catch (SQLException e) {
					e.printStackTrace();
				}
			}
		} else {
			boolean addPrimary = true;
			for (Column col : columns) {
				if (col.getName().equals("uuid")) {
					addPrimary = false;
				}
			}
			if (addPrimary) {
				columns.add(primaryKey);
			}
			insert(columns);
		}
	}

	public void wipeColumnData(String columnName, DataType dataType) {
		checkColumn(new Column(columnName, dataType));
		String sql = "UPDATE " + getName() + " SET " + columnName + " = " + dataType.getNoValue() + ";";
		try {
			PreparedStatement s = sqLite.getSQLConnection().prepareStatement(sql);
			s.executeUpdate();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	@Override
	public SQLite getSqLite() {
		return sqLite;
	}

}