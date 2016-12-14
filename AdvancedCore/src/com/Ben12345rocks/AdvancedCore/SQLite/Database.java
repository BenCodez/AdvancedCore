package com.Ben12345rocks.AdvancedCore.SQLite;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Level;

import org.bukkit.plugin.Plugin;

import com.Ben12345rocks.AdvancedCore.AdvancedCoreHook;
import com.Ben12345rocks.AdvancedCore.Objects.User;

public abstract class Database {
	Plugin plugin = AdvancedCoreHook.getInstance().getPlugin();
	Connection connection;
	public String table;

	public abstract Connection getSQLConnection();

	public abstract void load();

	public Database(String table) {
		this.table = table;
	}

	public void create(String uuid) {
		Connection conn = null;
		PreparedStatement ps = null;
		try {
			uuid = uuid.replace("-", "_");
			conn = getSQLConnection();
			ps = conn.prepareStatement("INSERT INTO IGNORE " + table + " (uuid) VALUES ('" + uuid + "');");
			ps.executeQuery();
		} catch (SQLException ex) {
			plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionExecute(), ex);
		} finally {
			try {
				if (ps != null)
					ps.close();
				if (conn != null)
					conn.close();
			} catch (SQLException ex) {
				plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionClose(), ex);
			}
		}
	}

	public void initialize() {
		connection = getSQLConnection();
		try {
			PreparedStatement ps = connection
					.prepareStatement("SELECT * FROM " + table + " WHERE uuid = ? OR (uuid IS NULL AND ? IS NULL)");
			ps.setString(1, "");
			ResultSet rs = ps.executeQuery();
			close(ps, rs);

		} catch (SQLException ex) {
			plugin.getLogger().log(Level.SEVERE, "Unable to retreive connection", ex);
		}
	}

	public HashMap<String, String> getSet(String uuid) {
		// create(uuid);
		HashMap<String, String> data = new HashMap<String, String>();
		for (String column : columns()) {
			data.put(column, getString(uuid, column));
		}
		return data;
	}

	public String getString(String uuid, String key) {
		// create(uuid);
		Connection conn = null;
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			uuid = uuid.replace("-", "_");

			conn = getSQLConnection();
			ps = conn.prepareStatement("SELECT * FROM " + table + " WHERE uuid = '" + uuid + "';");

			rs = ps.executeQuery();
			while (rs.next()) {
				if (rs.getString("uuid").equalsIgnoreCase(uuid)) {
					return rs.getString(key);
				}
			}

		} catch (SQLException ex) {
			plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionExecute(), ex);
		} finally {
			try {
				if (ps != null)
					ps.close();
				if (conn != null)
					conn.close();
			} catch (SQLException ex) {
				plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionClose(), ex);
			}
		}
		return "";
	}

	public void setData(User user, HashMap<String, String> data) {
		if (data == null || data.size() == 0) {
			return;
		}
		for (String d : data.keySet()) {
			checkColumn(d);
		}
		Connection conn = null;
		PreparedStatement ps = null;
		try {
			conn = getSQLConnection();
			String str = "(";
			boolean first = true;
			for (String d : data.keySet()) {
				if (!first) {
					str += ",";
				}
				str += d;
				first = false;
			}
			str += ") VALUES(";
			first = true;
			for (String d : data.values()) {
				if (!first) {
					str += ",";
				}
				str += d;
				first = false;
			}
			str += ")";
			ps = conn.prepareStatement("REPLACE INTO " + table + " " + str);
			ps.executeUpdate();
			return;
		} catch (SQLException ex) {
			plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionExecute(), ex);
		} finally {
			try {
				if (ps != null)
					ps.close();
				if (conn != null)
					conn.close();
			} catch (SQLException ex) {
				plugin.getLogger().log(Level.SEVERE, Errors.sqlConnectionClose(), ex);
			}
		}
		return;
	}

	public void checkColumn(String column) {
		Connection con;
		Statement st;
		ResultSet rs;

		try {
			con = getSQLConnection();

			st = con.createStatement();

			String sql = "select * from table";
			rs = st.executeQuery(sql);
			ResultSetMetaData metaData = rs.getMetaData();
			int rowCount = metaData.getColumnCount();

			boolean isMyColumnPresent = false;
			for (int i = 1; i <= rowCount; i++) {
				if (column.equals(metaData.getColumnName(i))) {
					isMyColumnPresent = true;
				}
			}

			if (!isMyColumnPresent) {
				String myColumnType = "TEXT";
				st.executeUpdate("ALTER TABLE table ADD " + column + " " + myColumnType);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void close(PreparedStatement ps, ResultSet rs) {
		try {
			if (ps != null)
				ps.close();
			if (rs != null)
				rs.close();
		} catch (SQLException ex) {
			Error.close(plugin, ex);
		}
	}

	public ArrayList<String> columns() {
		ArrayList<String> columns = new ArrayList<String>();
		Connection con;
		Statement st;
		ResultSet rs;

		try {
			con = getSQLConnection();

			st = con.createStatement();

			String sql = "select * from " + table;
			rs = st.executeQuery(sql);
			ResultSetMetaData metaData = rs.getMetaData();
			int rowCount = metaData.getColumnCount();

			for (int i = 1; i <= rowCount; i++) {
				columns.add(metaData.getColumnName(i));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return columns;
	}
}
