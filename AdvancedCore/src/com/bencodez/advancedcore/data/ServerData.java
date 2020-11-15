package com.bencodez.advancedcore.data;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.plugin.Plugin;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.yml.YMLFile;

// TODO: Auto-generated Javadoc
/**
 * The Class ServerData.
 */
public class ServerData extends YMLFile {

	/** The instance. */
	static ServerData instance = new ServerData();

	/**
	 * Gets the single instance of ServerData.
	 *
	 * @return single instance of ServerData
	 */
	public static ServerData getInstance() {
		return instance;
	}

	/** The plugin. */
	AdvancedCorePlugin plugin = AdvancedCorePlugin.getInstance();

	/**
	 * Instantiates a new server data.
	 */
	public ServerData() {
		super(new File(AdvancedCorePlugin.getInstance().getDataFolder(), "ServerData.yml"));
	}

	@SuppressWarnings("unchecked")
	public List<String> getIntColumns() {
		return (List<String>) getData().getList("IntColumns", new ArrayList<String>());
	}

	/**
	 * Gets the plugin version.
	 *
	 * @param plugin the plugin
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
	public String getPrevMonth() {
		return getData().getString("Month", "");
	}

	/**
	 * Gets the prev week day.
	 *
	 * @return the prev week day
	 */
	public int getPrevWeekDay() {
		return getData().getInt("PrevWeek", -1);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.Ben12345rocks.AdvancedCore.YML.YMLFile#onFileCreation()
	 */
	@Override
	public void onFileCreation() {
	}

	public void setData(String path, Object value) {
		getData().set(path, value);
		saveData();
	}

	public void setIntColumns(List<String> intColumns) {
		getData().set("IntColumns", intColumns);
		saveData();
	}

	/**
	 * Sets the plugin version.
	 *
	 * @param plugin the new plugin version
	 */
	public void setPluginVersion(Plugin plugin) {
		getData().set("PluginVersions." + plugin.getName(), plugin.getDescription().getVersion());
		saveData();
	}

	/**
	 * Sets the prev day.
	 *
	 * @param day the new prev day
	 */
	public void setPrevDay(int day) {
		getData().set("PrevDay", day);
		saveData();
	}

	/**
	 * Sets the prev month.
	 *
	 * @param month the new prev month
	 */
	public void setPrevMonth(String month) {
		getData().set("Month", month);
		saveData();
	}

	/**
	 * Sets the prev week day.
	 *
	 * @param week the new prev week day
	 */
	public void setPrevWeekDay(int week) {
		getData().set("PrevWeek", week);
		saveData();
	}
}
