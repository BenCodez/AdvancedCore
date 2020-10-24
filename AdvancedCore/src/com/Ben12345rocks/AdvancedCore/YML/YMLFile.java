package com.Ben12345rocks.AdvancedCore.YML;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Map.Entry;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import com.Ben12345rocks.AdvancedCore.AdvancedCorePlugin;
import com.Ben12345rocks.AdvancedCore.Util.Files.FilesManager;

import lombok.Getter;
import net.md_5.bungee.api.ChatColor;

/**
 * The Class YMLFile.
 */
public abstract class YMLFile {

	/** The data. */
	private FileConfiguration data;

	/** The d file. */
	private File dFile;

	@Getter
	private boolean failedToRead = false;

	private boolean created = false;

	/**
	 * Instantiates a new YML file.
	 *
	 * @param file the file
	 */
	public YMLFile(File file) {
		dFile = file;
	}

	/**
	 * Instantiates a new YML file.
	 *
	 * @param file  the file
	 * @param setup the setup
	 */
	public YMLFile(File file, boolean setup) {
		dFile = file;
		if (setup) {
			setup();
		}
	}

	/**
	 * Gets the data.
	 *
	 * @return the data
	 */
	public FileConfiguration getData() {
		return data;
	}

	/**
	 * Gets the d file.
	 *
	 * @return the d file
	 */
	public File getdFile() {
		return dFile;
	}

	public boolean isJustCreated() {
		return created;
	}

	public void loadValues() {

	}

	/**
	 * On file creation.
	 */
	public abstract void onFileCreation();

	/**
	 * Reload data.
	 */
	public void reloadData() {
		try {
			data = YamlConfiguration.loadConfiguration(dFile);
			failedToRead = false;
			if (data.getConfigurationSection("").getKeys(false).size() == 0) {
				failedToRead = true;
			}
		} catch (Exception e) {
			failedToRead = true;
			e.printStackTrace();
			AdvancedCorePlugin.getInstance().getLogger().severe("Failed to load " + dFile.getName());
			Bukkit.getScheduler().runTaskAsynchronously(AdvancedCorePlugin.getInstance(), new Runnable() {

				@Override
				public void run() {
					AdvancedCorePlugin.getInstance().getLogger()
							.severe("Detected failure to load files on startup, see server log for details");
				}
			});
		}
	}

	/**
	 * Save data.
	 */
	public void saveData() {

		FilesManager.getInstance().editFile(dFile, data);

	}

	public void setData(FileConfiguration data) {
		Map<String, Object> map = data.getConfigurationSection("").getValues(true);
		for (Entry<String, Object> entry : map.entrySet()) {
			this.data.set(entry.getKey(), entry.getValue());
		}
	}

	/**
	 * Setup.
	 */
	public void setup() {
		failedToRead = false;
		getdFile().getParentFile().mkdirs();

		if (!dFile.exists()) {
			try {
				getdFile().createNewFile();
				onFileCreation();
				created = true;
			} catch (IOException e) {
				Bukkit.getServer().getLogger().severe(ChatColor.RED + "Could not create " + getdFile().getName() + "!");
			}
		}

		try {
			data = YamlConfiguration.loadConfiguration(dFile);
			if (data.getConfigurationSection("").getKeys(false).size() == 0) {
				failedToRead = true;
			}
			loadValues();

		} catch (Exception e) {
			failedToRead = true;
			e.printStackTrace();
			AdvancedCorePlugin.getInstance().getLogger().severe("Failed to load " + dFile.getName());
			Bukkit.getScheduler().runTaskAsynchronously(AdvancedCorePlugin.getInstance(), new Runnable() {

				@Override
				public void run() {
					AdvancedCorePlugin.getInstance().getLogger()
							.severe("Detected failure to load files on startup, see server log for details");
				}
			});
		}
	}
}
