package com.bencodez.advancedcore.data;

import java.io.File;
import java.io.IOException;

import org.bukkit.plugin.Plugin;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.misc.files.AtomicYamlWriter;
import com.bencodez.simpleapi.file.YMLFile;

/**
 * Persistent server-level AdvancedCore data.
 */
public class ServerData extends YMLFile {
	public ServerData(AdvancedCorePlugin plugin) {
		super(plugin, new File(plugin.getDataFolder(), "ServerData.yml"));
	}

	public long getLastUpdated() {
		return getData().getLong("LastUpdated", -1);
	}

	public String getPluginVersion(Plugin plugin) {
		return getData().getString("PluginVersions." + plugin.getName(), "");
	}

	public int getPrevDay() {
		return getData().getInt("PrevDay", -1);
	}

	public String getPrevMonth() {
		return getData().getString("Month", "");
	}

	public int getPrevWeekDay() {
		return getData().getInt("PrevWeek", -1);
	}

	public boolean isIgnoreTime() {
		return getData().getBoolean("IgnoreTime", false);
	}

	@Override
	public void onFileCreation() {
	}

	@Override
	public void saveData() {
		try {
			AtomicYamlWriter.save(getdFile(), getData());
		} catch (IOException | RuntimeException e) {
			getPlugin().getLogger().severe("Failed to save " + getdFile().getName() + ": " + e.getMessage());
			if (getPlugin() instanceof AdvancedCorePlugin) {
				((AdvancedCorePlugin) getPlugin()).debug(e);
			}
		}
	}

	public void setData(String path, Object value) {
		getData().set(path, value);
		saveData();
	}

	public void setIgnoreTime(boolean value) {
		getData().set("IgnoreTime", value);
		saveData();
	}

	public void setLastUpdated() {
		getData().set("LastUpdated", System.currentTimeMillis());
		saveData();
	}

	public void setPluginVersion(Plugin plugin) {
		getData().set("PluginVersions." + plugin.getName(), plugin.getDescription().getVersion());
		saveData();
	}

	public void setPrevDay(int day) {
		getData().set("PrevDay", day);
		saveData();
	}

	public void setPrevMonth(String month) {
		getData().set("Month", month);
		saveData();
	}

	public void setPrevWeekDay(int week) {
		getData().set("PrevWeek", week);
		saveData();
	}
}
