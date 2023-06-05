package com.bencodez.advancedcore.data;

import java.io.File;

import org.bukkit.plugin.Plugin;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.yml.YMLFile;

// TODO: Auto-generated Javadoc
/**
 * The Class ServerData.
 */
public class ServerData extends YMLFile {
	public ServerData(AdvancedCorePlugin plugin) {
		super(plugin, new File(plugin.getDataFolder(), "ServerData.yml"));
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

	public boolean isIgnoreTime() {
		return getData().getBoolean("IgnoreTime", false);
	}

	@Override
	public void onFileCreation() {
	}

	public long getLastUpdated() {
		return getData().getLong("LastUpdated", -1);
	}

	public void setLastUpdated() {
		getData().set("LastUpdated", System.currentTimeMillis());
		saveData();
	}

	public void setData(String path, Object value) {
		getData().set(path, value);
		saveData();
	}

	public void setIgnoreTime(boolean value) {
		getData().set("IgnoreTime", value);
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
