package com.bencodez.advancedcore.api.rewards;

import org.bukkit.configuration.ConfigurationSection;

/**
 * Interface for defined rewards.
 */
public interface DefinedReward {
	/**
	 * Creates a section.
	 *
	 * @param key the key
	 */
	public void createSection(String key);

	/**
	 * Gets the file data.
	 *
	 * @return the file data
	 */
	public ConfigurationSection getFileData();

	/**
	 * Gets the full path.
	 *
	 * @return the full path
	 */
	public String getFullPath();

	/**
	 * Gets the path.
	 *
	 * @return the path
	 */
	public String getPath();

	/**
	 * Needs dot separator.
	 *
	 * @return the dot string
	 */
	public String needsDot();

	/**
	 * Saves the configuration.
	 */
	public void save();

	/**
	 * Sets the data.
	 *
	 * @param str the path
	 * @param value the value
	 */
	public void setData(String str, Object value);
}
