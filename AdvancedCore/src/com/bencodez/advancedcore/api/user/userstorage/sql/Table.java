package com.bencodez.advancedcore.api.user.userstorage.sql;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.user.usercache.keys.UserDataKey;
import com.bencodez.advancedcore.api.user.usercache.keys.UserDataKeyInt;
import com.bencodez.advancedcore.api.user.usercache.value.DataValueBoolean;
import com.bencodez.advancedcore.api.user.usercache.value.DataValueInt;
import com.bencodez.advancedcore.api.user.usercache.value.DataValueString;
import com.bencodez.advancedcore.api.user.userstorage.Column;
import com.bencodez.advancedcore.api.user.userstorage.DataType;
import com.bencodez.advancedcore.api.user.userstorage.sql.db.SQLite;

public class Table {

	private List<Column> columns = new ArrayList<>();
	private String name;
	private Object object = new Object();
	private List<String> intColumns;

	private Column primaryKey;

	private SQLite sqLite;

	public Table(String name, Collection<Column> columns) {
		this.name = name;
		this.columns.addAll(columns);
		primaryKey = this.columns.get(0);
	}

	public Table(String name, Collection<Column> columns, Column primaryKey) {
		this.name = name;
		this.primaryKey = primaryKey;
		this.columns.addAll(columns);
	}

	public Table(String name, Column... columns) {
		this.name = name;
		for (Column column : columns) {
			this.columns.add(column);
		}
		primaryKey = this.columns.get(0);
	}

	public Table(String name, Column primaryKey, Column... columns) {
		this.name = name;
		this.primaryKey = primaryKey;
		for (Column column : columns) {
			this.columns.add(column);
		}
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
			if (column.getDataType().equals(DataType.INTEGER)) {
				if (!intColumns.contains(column.getName())) {
					intColumns.add(column.getName());
					AdvancedCorePlugin.getInstance().getServerDataFile().setIntColumns(intColumns);
				}
			}
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
				if (col != null) {
					if (col.equals(columns.get(i).getName())) {
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

	public List<Column> getColumns() {
		return columns;
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

	public String getName() {
		return name;
	}

	public ArrayList<String> getNames() {
		ArrayList<String> names = new ArrayList<String>();
		for (Column col : getRowsNames()) {
			if (col.getValue() != null && col.getValue().isString()) {
				names.add(col.getValue().getString());
			}
		}
		return names;
	}

	public Column getPrimaryKey() {
		return primaryKey;
	}

	public String getQuery() {
		intColumns = Collections.synchronizedList(AdvancedCorePlugin.getInstance().getServerDataFile().getIntColumns());
		String sql = "CREATE TABLE IF NOT EXISTS " + getName() + " (";
		sql += "uuid VARCHAR(37), ";
		// add custom column types
		for (UserDataKey key : AdvancedCorePlugin.getInstance().getUserManager().getDataManager().getKeys()) {
			sql += key.getKey() + " " + key.getColumnType() + ", ";
			if (key instanceof UserDataKeyInt) {
				if (!intColumns.contains(key.getKey())) {
					intColumns.add(key.getKey());
					AdvancedCorePlugin.getInstance().getServerDataFile().setIntColumns(intColumns);
				}
			}
		}
		sql += "PRIMARY KEY ( uuid ));";
		return sql;
	}

	public List<Column> getRows() {
		List<Column> result = new ArrayList<Column>();
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
		List<Column> result = new ArrayList<Column>();
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

	public void wipeColumnData(String columnName) {
		checkColumn(new Column(columnName, DataType.STRING));
		String sql = "UPDATE " + getName() + " SET " + columnName + " = NULL;";
		try {
			PreparedStatement s = sqLite.getSQLConnection().prepareStatement(sql);
			s.executeUpdate();
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

}