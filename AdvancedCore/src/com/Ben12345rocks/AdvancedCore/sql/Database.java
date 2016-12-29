package com.Ben12345rocks.AdvancedCore.sql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.plugin.Plugin;

import com.Ben12345rocks.AdvancedCore.sql.db.SQLite;

public class Database {

	private List<Table> tables = new ArrayList<>();
	private SQLite sqLite;

	public Database(Plugin plugin, String dbName, Table table) {
		tables.add(table);
		sqLite = new SQLite(plugin, dbName, this);
		sqLite.load();
		table.setSqLite(sqLite);
	}

	public Database(Plugin plugin, String dbName, Table table, String file) {
		tables.add(table);
		sqLite = new SQLite(plugin, dbName, this, file);
		sqLite.load();
		table.setSqLite(sqLite);
	}

	public void addTable(Table table) {
		tables.add(table);
		table.setSqLite(sqLite);
		try {
			PreparedStatement statement = sqLite.getSQLConnection().prepareStatement(table.getQuery());
			statement.executeUpdate();
			statement.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public Connection getConnection() {
		return sqLite.getSQLConnection();
	}

	public SQLite getDB() {
		return sqLite;
	}

	public String getTableQuery() {
		return tables.get(0).getQuery();
	}

	public List<Table> getTables() {
		return tables;
	}

}
