package com.bencodez.advancedcore.api.yml;

import org.bukkit.configuration.ConfigurationSection;

import com.bencodez.advancedcore.AdvancedCorePlugin;

import lombok.Getter;

/**
 * The Class YMLFile.
 */
public abstract class YMLConfig {

	@Getter
	private ConfigurationSection data;

	@Getter
	private boolean failedToRead = false;

	@Getter
	private AdvancedCorePlugin plugin;

	public YMLConfig(AdvancedCorePlugin plugin, ConfigurationSection data) {
		this.data = data;
		this.plugin = plugin;
	}

	public abstract void createSection(String key);

	public abstract void saveData();

	public abstract void setValue(String path, Object value);

}
