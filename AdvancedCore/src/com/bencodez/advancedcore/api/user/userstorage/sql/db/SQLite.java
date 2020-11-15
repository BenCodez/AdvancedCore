package com.bencodez.advancedcore.api.user.userstorage.sql.db;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;

import org.bukkit.plugin.Plugin;

import com.bencodez.advancedcore.api.user.userstorage.sql.Database;

public class SQLite {

	private String dbName;

	private String query;

	private boolean inDF = true;

	private String dir;

	private Plugin plugin;

	private Connection connection;

	public SQLite(Plugin plugin, String dbName, Database db) {
		this.plugin = plugin;
		this.dbName = dbName;
		query = db.getTableQuery();
		connection = getSQLConnection();
	}

	public SQLite(Plugin instance, String dbName, Database db, String directory) {
		plugin = instance;
		this.dbName = dbName;
		query = db.getTableQuery();
		inDF = false;
		dir = directory;
		connection = getSQLConnection();
	}

	public void close(PreparedStatement ps, ResultSet rs) {
		try {
			if (ps != null) {
				ps.close();
			}
			if (rs != null) {
				rs.close();
			}
		} catch (SQLException ex) {
			Error.close(plugin, ex);
		}
	}

	public void closeConnection() {
		try {
			if (!(connection.isClosed())) {
				connection.close();
				connection = null;
			} else {
				connection = null;
			}
		} catch (Exception e) {
			Error.close(plugin, e);
		}
	}

	public Connection getConnection() {
		return connection;
	}

	public String getDbName() {
		return dbName;
	}

	public String getDir() {
		return dir;
	}

	public Plugin getPlugin() {
		return plugin;
	}

	public String getQuery() {
		return query;
	}

	public Connection getSQLConnection() {
		File file;
		if (inDF) {
			file = new File(plugin.getDataFolder(), dbName + ".db");
			plugin.getDataFolder().mkdirs();
		} else {
			new File(dir).mkdirs();
			file = new File(dir + "/" + dbName + ".db");
		}
		if (!file.exists()) {
			try {
				file.createNewFile();
			} catch (IOException e) {
				plugin.getLogger().log(Level.SEVERE, "File write error: " + dbName + ".db");
			}
		}
		try {
			if (connection != null && !connection.isClosed()) {
				return connection;
			}
			Class.forName("org.sqlite.JDBC");
			connection = DriverManager.getConnection("jdbc:sqlite:" + file);
			return connection;
		} catch (SQLException ex) {
			plugin.getLogger().log(Level.SEVERE, "SQLite exception on initialize", ex);
		} catch (ClassNotFoundException ex) {
			plugin.getLogger().log(Level.SEVERE, "You need the SQLite JBDC library. Google it. Put it in /lib folder.");
		}
		return null;
	}

	public boolean isInDF() {
		return inDF;
	}

	public void load() {
		connection = getSQLConnection();
		try {
			PreparedStatement s = connection.prepareStatement(query);
			s.executeUpdate();
			s.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

}