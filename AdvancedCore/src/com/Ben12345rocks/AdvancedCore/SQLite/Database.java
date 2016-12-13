package com.Ben12345rocks.AdvancedCore.SQLite;

import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.logging.Level;

import org.bukkit.plugin.Plugin;

import com.Ben12345rocks.AdvancedCore.AdvancedCoreHook;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

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
			ps = conn.prepareStatement("INSERT INTO IF NOT EXISTS Users (uuid) VALUES (" + uuid + ");");
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

	public int getInt(String uuid, String column) {
		Connection conn = null;
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			uuid = uuid.replace("-", "_");
			create(uuid);
			conn = getSQLConnection();
			ps = conn.prepareStatement("SELECT * FROM " + table + " WHERE uuid = '" + uuid + "';");

			rs = ps.executeQuery();
			while (rs.next()) {
				if (rs.getString("uuid").equalsIgnoreCase(uuid)) {
					return rs.getInt(column);
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
		return 0;
	}

	public String getString(String uuid, String column) {
		Connection conn = null;
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			uuid = uuid.replace("-", "_");
			create(uuid);
			conn = getSQLConnection();
			ps = conn.prepareStatement("SELECT * FROM " + table + " WHERE uuid = '" + uuid + "';");

			rs = ps.executeQuery();
			while (rs.next()) {
				if (rs.getString("uuid").equalsIgnoreCase(uuid)) {
					return rs.getString(column);
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

	public ArrayList<String> getArray(String uuid, String column) {
		Connection conn = null;
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			uuid = uuid.replace("-", "_");
			create(uuid);
			conn = getSQLConnection();
			ps = conn.prepareStatement("SELECT * FROM " + table + " WHERE uuid = '" + uuid + "';");

			rs = ps.executeQuery();
			while (rs.next()) {
				if (rs.getString("uuid").equalsIgnoreCase(uuid)) {
					String value = rs.getString(column);
					Type type = new TypeToken<ArrayList<String>>() {
					}.getType();
					Gson gson = new Gson();
					return gson.fromJson(value, type);
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
		return new ArrayList<String>();
	}

	public void setString(String uuid, String column, String value) {
		Connection conn = null;
		PreparedStatement ps = null;
		try {
			uuid = uuid.replace("-", "_");
			create(uuid);
			conn = getSQLConnection();
			ps = conn.prepareStatement("UPDATE " + table + " SET " + column + "=" + value + " WHERE uuid=" + uuid);
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

	public void setInt(String uuid, String column, int value) {
		Connection conn = null;
		PreparedStatement ps = null;
		try {
			uuid = uuid.replace("-", "_");
			create(uuid);
			conn = getSQLConnection();
			ps = conn.prepareStatement("UPDATE " + table + " SET " + column + "=" + value + " WHERE uuid=" + uuid);
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

	public void setArray(String uuid, String column, ArrayList<String> values) {
		Connection conn = null;
		PreparedStatement ps = null;
		try {
			conn = getSQLConnection();
			Gson gson = new Gson();
			uuid = uuid.replace("-", "_");
			create(uuid);

			String value = gson.toJson(values);
			ps = conn.prepareStatement("UPDATE " + table + " SET " + column + "=" + value + " WHERE uuid=" + uuid);
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
}
