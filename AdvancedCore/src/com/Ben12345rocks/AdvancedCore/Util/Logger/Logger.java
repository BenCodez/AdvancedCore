package com.Ben12345rocks.AdvancedCore.Util.Logger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

import org.bukkit.plugin.Plugin;

public class Logger {
	Plugin plugin;
	File location;

	public Logger(Plugin plugin, File location) {
		this.plugin = plugin;
		this.location = location;
	}

	public void logToFile(String message) {
		try {
			File dataFolder = plugin.getDataFolder();
			if (!dataFolder.exists()) {
				dataFolder.mkdir();
			}

			File saveTo = location;
			if (!saveTo.exists()) {
				saveTo.createNewFile();
			}

			FileWriter fw = new FileWriter(saveTo, true);

			PrintWriter pw = new PrintWriter(fw);

			pw.println(message);

			pw.flush();

			pw.close();

		} catch (IOException e) {
			e.printStackTrace();
		}

	}
}
