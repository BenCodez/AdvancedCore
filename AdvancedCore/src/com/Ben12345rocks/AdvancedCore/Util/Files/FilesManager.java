package com.Ben12345rocks.AdvancedCore.Util.Files;

import java.io.File;
import java.io.IOException;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;

import com.Ben12345rocks.AdvancedCore.Main;

// TODO: Auto-generated Javadoc
/**
 * The Class Files.
 */
public class FilesManager {

	/**
	 * The Class ReadThread.
	 */
	public class ReadThread extends Thread {

		/**
		 * Edits the file.
		 *
		 * @param file
		 *            the file
		 * @param data
		 *            the data
		 */
		public void editFile(File file, FileConfiguration data) {
			try {
				data.save(file);
			} catch (IOException e) {
				Bukkit.getServer()
						.getLogger()
						.severe(ChatColor.RED + "Could not save "
								+ file.getName());
			}
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.lang.Thread#run()
		 */
		@Override
		public void run() {
			// plugin.getLogger().info("File Editing Thread Loaded!");
		}
	}

	/** The instance. */
	static FilesManager instance = new FilesManager();

	/** The plugin. */
	static Main plugin = Main.plugin;

	/**
	 * Gets the single instance of Files.
	 *
	 * @return single instance of Files
	 */
	public static FilesManager getInstance() {
		return instance;
	}

	/** The thread. */
	public ReadThread thread;

	/**
	 * Instantiates a new files.
	 */
	private FilesManager() {
	}

	/**
	 * Instantiates a new files.
	 *
	 * @param plugin
	 *            the plugin
	 */
	public FilesManager(Main plugin) {
		FilesManager.plugin = plugin;
	}

	/**
	 * Edits the file.
	 *
	 * @param file
	 *            the file
	 * @param data
	 *            the data
	 */
	public void editFile(File file, FileConfiguration data) {
		thread.editFile(file, data);
	}

	/**
	 * Load file editng thread.
	 */
	public void loadFileEditngThread() {
		thread = new ReadThread();
		thread.start();
	}
}
