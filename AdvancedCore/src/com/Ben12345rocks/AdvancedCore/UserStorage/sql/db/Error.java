package com.Ben12345rocks.AdvancedCore.UserStorage.sql.db;

import java.util.logging.Level;

import org.bukkit.plugin.Plugin;

public class Error {
	public static void close(Plugin plugin, Exception ex) {
		plugin.getLogger().log(Level.SEVERE, "Failed to close MySQL connection: ", ex);
	}

	public static void execute(Plugin plugin, Exception ex) {
		plugin.getLogger().log(Level.SEVERE, "Couldn't execute MySQL statement: ", ex);
	}
}