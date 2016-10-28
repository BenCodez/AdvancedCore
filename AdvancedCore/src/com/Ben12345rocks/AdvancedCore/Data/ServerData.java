package com.Ben12345rocks.AdvancedCore.Data;

import java.io.File;

import org.bukkit.plugin.Plugin;

import com.Ben12345rocks.AdvancedCore.Main;
import com.Ben12345rocks.AdvancedCore.YML.YMLFile;

// TODO: Auto-generated Javadoc
/**
 * The Class ServerData.
 */
public class ServerData extends YMLFile {

	/** The instance. */
	static ServerData instance = new ServerData();

	/** The plugin. */
	static Main plugin = Main.plugin;

	/**
	 * Gets the single instance of ServerData.
	 *
	 * @return single instance of ServerData
	 */
	public static ServerData getInstance() {
		return instance;
	}

	/**
	 * Instantiates a new server data.
	 */
	public ServerData() {
		super(new File(plugin.getDataFolder(), "ServerData.yml"));
	}

	/**
	 * Gets the plugin version.
	 *
	 * @param plugin
	 *            the plugin
	 * @return the plugin version
	 */
	public String getPluginVersion(Plugin plugin) {
		return getData().getString("PluginVersions." + plugin.getName(), "");
	}

	/**
	 * Gets the prev day.
	 *
	 * @return the prev day
	 */
	public int getPrevDay() {
		return getData().getInt("PrevDay", -1);
	}

	/**
	 * Gets the prev month.
	 *
	 * @return the prev month
	 */
	public int getPrevMonth() {
		return getData().getInt("PrevMonth", -1);
	}

	/**
	 * Gets the prev week day.
	 *
	 * @return the prev week day
	 */
	public int getPrevWeekDay() {
		return getData().getInt("PrevWeek", -1);
	}

	/**
	 * Sets the plugin version.
	 *
	 * @param plugin
	 *            the new plugin version
	 */
	public void setPluginVersion(Plugin plugin) {
		getData().set("PluginVersions." + plugin.getName(),
				plugin.getDescription().getVersion());
		saveData();
	}

	/**
	 * Sets the prev day.
	 *
	 * @param day
	 *            the new prev day
	 */
	public void setPrevDay(int day) {
		getData().set("PrevDay", day);
		saveData();
	}

	/**
	 * Sets the prev month.
	 *
	 * @param value
	 *            the new prev month
	 */
	public void setPrevMonth(int value) {
		getData().set("PrevMonth", value);
		saveData();
	}

	/**
	 * Sets the prev week day.
	 *
	 * @param week
	 *            the new prev week day
	 */
	public void setPrevWeekDay(int week) {
		getData().set("PrevWeek", week);
		saveData();
	}

	@Override
	public void onFileCreation() {
	}
}
