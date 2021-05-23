package com.bencodez.advancedcore.logger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

import org.bukkit.plugin.Plugin;

/**
 * The Class Logger.
 */
public class Logger {

	/** The location. */
	File location;

	/** The plugin. */
	Plugin plugin;

	/**
	 * Instantiates a new logger.
	 *
	 * @param plugin   the plugin
	 * @param location the location
	 */
	public Logger(Plugin plugin, File location) {
		this.plugin = plugin;
		this.location = location;
	}

	/**
	 * Log to file.
	 *
	 * @param message the message
	 */
	public void logToFile(String message) {
		try {
			if (!location.getParentFile().exists()) {
				location.getParentFile().mkdirs();
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
