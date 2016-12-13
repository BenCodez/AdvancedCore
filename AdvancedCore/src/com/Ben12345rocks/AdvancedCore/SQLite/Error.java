package com.Ben12345rocks.AdvancedCore.SQLite;

import java.util.logging.Level;

import org.bukkit.plugin.Plugin;

public class Error {
	public static void execute(Plugin plugin, Exception ex) {
		plugin.getLogger().log(Level.SEVERE, "Couldn't execute MySQL statement: ", ex);
	}

	public static void close(Plugin plugin, Exception ex) {
		plugin.getLogger().log(Level.SEVERE, "Failed to close MySQL connection: ", ex);
	}
}
