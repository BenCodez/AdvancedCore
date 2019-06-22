package com.Ben12345rocks.AdvancedCore.Util.Files;

import java.io.File;
import java.io.IOException;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;

import com.Ben12345rocks.AdvancedCore.AdvancedCorePlugin;
import com.Ben12345rocks.AdvancedCore.Thread.FileThread;

// TODO: Auto-generated Javadoc
/**
 * The Class FilesManager.
 */
public class FilesManager {

	/** The instance. */
	static FilesManager instance = new FilesManager();

	/**
	 * Gets the single instance of FilesManager.
	 *
	 * @return single instance of FilesManager
	 */
	public static FilesManager getInstance() {
		return instance;
	}

	/** The plugin. */
	AdvancedCorePlugin plugin = AdvancedCorePlugin.getInstance();

	/**
	 * Instantiates a new files manager.
	 */
	private FilesManager() {
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
		FileThread.getInstance().run(new Runnable() {

			@Override
			public void run() {
				try {
					data.save(file);
				} catch (IOException e) {
					Bukkit.getServer().getLogger().severe(ChatColor.RED + "Could not save " + file.getName());
				}

			}
		});
	}
}
