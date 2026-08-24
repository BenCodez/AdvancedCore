package com.bencodez.advancedcore.api.misc.files;

import java.io.File;
import java.io.IOException;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.thread.FileThread;

import net.md_5.bungee.api.ChatColor;

/**
 * Handles asynchronous file writes for AdvancedCore.
 */
public class FilesManager {

	private static final FilesManager instance = new FilesManager();

	public static FilesManager getInstance() {
		return instance;
	}

	private FilesManager() {
	}

	/**
	 * Saves the supplied configuration asynchronously. The replacement is written to
	 * a temporary sibling file before it replaces the destination, preserving the
	 * previous file if serialization fails.
	 *
	 * @param file the file
	 * @param data the data
	 */
	public void editFile(File file, FileConfiguration data) {
		FileThread.getInstance().run(() -> {
			try {
				AtomicYamlWriter.save(file, data);
			} catch (IOException | RuntimeException e) {
				AdvancedCorePlugin plugin = AdvancedCorePlugin.getInstance();
				if (plugin != null) {
					plugin.getLogger().severe("Could not save " + (file == null ? "null file" : file.getName())
							+ ": " + e.getMessage());
					plugin.debug(e);
				} else {
					Bukkit.getServer().getLogger().severe(ChatColor.RED + "Could not save "
							+ (file == null ? "null file" : file.getName()) + ": " + e.getMessage());
				}
			}
		});
	}
}
