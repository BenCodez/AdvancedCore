/*
 * 
 */
package com.Ben12345rocks.AdvancedCore.Configs;

import java.io.File;
import java.io.IOException;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import com.Ben12345rocks.AdvancedCore.Main;
import com.Ben12345rocks.AdvancedCore.Util.Files.FilesManager;

// TODO: Auto-generated Javadoc
/**
 * The Class Config.
 */
public class Config {

	/** The instance. */
	static Config instance = new Config();

	/** The plugin. */
	static Main plugin = Main.plugin;

	/**
	 * Gets the single instance of Config.
	 *
	 * @return single instance of Config
	 */
	public static Config getInstance() {
		return instance;
	}

	/** The data. */
	FileConfiguration data;

	/** The d file. */
	File dFile;

	/**
	 * Instantiates a new config.
	 */
	private Config() {
	}

	/**
	 * Instantiates a new config.
	 *
	 * @param plugin
	 *            the plugin
	 */
	public Config(Main plugin) {
		Config.plugin = plugin;
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
	 * Gets the debug enabled.
	 *
	 * @return the debug enabled
	 */
	public boolean getDebugEnabled() {
		return getData().getBoolean("Debug");
	}

	public String getFormatNoPerms() {
		return getData().getString("Format.NoPerms",
				"&cYou do not have enough permission!");
	}

	public String getFormatNotNumber() {
		return getData().getString("Format.NotNumber",
				"&cError on &6%arg%&c, number expected!");
	}

	public String getFormatHelpLine() {
		return getData().getString("Format.HelpLine",
				"&3&l%Command% - &3%HelpMessage%");
	}

	/**
	 * Reload data.
	 */
	public void reloadData() {
		data = YamlConfiguration.loadConfiguration(dFile);
	}

	/**
	 * Save data.
	 */
	public void saveData() {

		FilesManager.getInstance().editFile(dFile, data);

	}

	/**
	 * Sets the up.
	 *
	 * @param p
	 *            the new up
	 */
	public void setup(Plugin p) {
		if (!p.getDataFolder().exists()) {
			p.getDataFolder().mkdir();
		}

		dFile = new File(p.getDataFolder(), "Config.yml");

		if (!dFile.exists()) {
			try {
				dFile.createNewFile();
				plugin.saveResource("Config.yml", true);
			} catch (IOException e) {
				Bukkit.getServer().getLogger()
						.severe(ChatColor.RED + "Could not create Config.yml!");
			}
		}

		data = YamlConfiguration.loadConfiguration(dFile);
	}

}
