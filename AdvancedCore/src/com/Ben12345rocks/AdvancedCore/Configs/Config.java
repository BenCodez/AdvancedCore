/*
 *
 */
package com.Ben12345rocks.AdvancedCore.Configs;

import java.io.File;
import java.util.ArrayList;

import com.Ben12345rocks.AdvancedCore.Main;
import com.Ben12345rocks.AdvancedCore.YML.YMLFile;

// TODO: Auto-generated Javadoc
/**
 * The Class Config.
 */
public class Config extends YMLFile {

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

	/**
	 * Instantiates a new config.
	 */
	public Config() {
		super(new File(Main.plugin.getDataFolder(), "Config.yml"));
	}

	/**
	 * Gets the debug enabled.
	 *
	 * @return the debug enabled
	 */
	public boolean getDebugEnabled() {
		return getData().getBoolean("Debug");
	}

	/**
	 * Gets the debug info ingame.
	 *
	 * @return the debug info ingame
	 */
	public boolean getDebugInfoIngame() {
		return getData().getBoolean("DebugInfoIngame");
	}

	/**
	 * Gets the format help line.
	 *
	 * @return the format help line
	 */
	public String getFormatHelpLine() {
		return getData().getString("Format.HelpLine",
				"&3&l%Command% - &3%HelpMessage%");
	}

	/**
	 * Gets the format no perms.
	 *
	 * @return the format no perms
	 */
	public String getFormatNoPerms() {
		return getData().getString("Format.NoPerms",
				"&cYou do not have enough permission!");
	}

	/**
	 * Gets the format not number.
	 *
	 * @return the format not number
	 */
	public String getFormatNotNumber() {
		return getData().getString("Format.NotNumber",
				"&cError on &6%arg%&c, number expected!");
	}

	/**
	 * Gets the log debug to file.
	 *
	 * @return the log debug to file
	 */
	public boolean getLogDebugToFile() {
		return getData().getBoolean("LogDebugToFile", true);
	}

	/**
	 * Gets the request API default method.
	 *
	 * @return the request API default method
	 */
	public String getRequestAPIDefaultMethod() {
		return getData().getString("RequestAPI.DefaultMethod", "Anvil");
	}

	/**
	 * Gets the request API disabled methods.
	 *
	 * @return the request API disabled methods
	 */
	@SuppressWarnings("unchecked")
	public ArrayList<String> getRequestAPIDisabledMethods() {
		return (ArrayList<String>) getData().getList(
				"RequestAPI.DisabledMethods", new ArrayList<String>());
	}

	/**
	 * Gets the time zone.
	 *
	 * @return the time zone
	 */
	public String getTimeZone() {
		String str = getData().getString("TimeZone");
		if (str != null) {
			return str;
		}
		return "UTC";
	}

	/**
	 * Sets the debug enabled.
	 *
	 * @param value
	 *            the new debug enabled
	 */
	public void setDebugEnabled(boolean value) {
		getData().set("Debug", value);
		saveData();
	}

	/**
	 * Sets the debug info ingame.
	 *
	 * @param value
	 *            the new debug info ingame
	 */
	public void setDebugInfoIngame(boolean value) {
		getData().set("DebugInfoIngame", value);
	}

	/* (non-Javadoc)
	 * @see com.Ben12345rocks.AdvancedCore.YML.YMLFile#onFileCreation()
	 */
	@Override
	public void onFileCreation() {
		plugin.saveResource("Config.yml", true);
	}
}
