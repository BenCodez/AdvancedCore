package com.bencodez.advancedcore.api.rewards;

import org.bukkit.configuration.ConfigurationSection;

public interface DefinedReward {
	public void createSection(String key);

	public ConfigurationSection getFileData();

	public String getFullPath();

	public String getPath();

	public String needsDot();

	public void save();

	public void setData(String str, Object value);
}
