package com.bencodez.advancedcore.api.rewards;

import org.bukkit.configuration.ConfigurationSection;

import lombok.Getter;
import lombok.Setter;

/**
 * Abstract class for directly defined rewards.
 */
public abstract class DirectlyDefinedReward implements DefinedReward {
	/**
	 * Gets the path.
	 *
	 * @return the path
	 * @param path the path to set
	 */
	@Getter
	@Setter
	private String path;

	/**
	 * Instantiates a new directly defined reward.
	 *
	 * @param path the path
	 */
	public DirectlyDefinedReward(String path) {
		this.path = path;
	}

	public abstract void createSection(String key);

	/**
	 * Creates a local section.
	 *
	 * @param key the key
	 */
	public void createSectionLocal(String key) {
		createSection(getPath() + "." + key);
	}

	public abstract ConfigurationSection getFileData();

	/**
	 * Gets the full path.
	 *
	 * @return the full path
	 */
	public String getFullPath() {
		return path;
	}

	/**
	 * Gets the reward.
	 *
	 * @return the reward
	 */
	public Reward getReward() {
		if (isDirectlyDefined()) {
			return new Reward(getPath().replace(".", "_"), getFileData().getConfigurationSection(getPath()))
					.needsRewardFile(false);
		}
		return null;
	}

	/**
	 * Gets the value.
	 *
	 * @param path the path
	 * @return the value
	 */
	public Object getValue(String path) {
		return getFileData().get(getPath() + "." + path);
	}

	/**
	 * Checks if directly defined.
	 *
	 * @return true if directly defined
	 */
	public boolean isDirectlyDefined() {
		return getFileData().isConfigurationSection(getPath());
	}

	public String needsDot() {
		return ".";
	}

	public abstract void save();

	public abstract void setData(String path, Object value);

	/**
	 * Sets the parent value.
	 *
	 * @param value the value
	 */
	public void setParentValue(Object value) {
		setData(getPath(), value);
	}

	/**
	 * Sets the value.
	 *
	 * @param path the path
	 * @param value the value
	 */
	public void setValue(String path, Object value) {
		setData(getPath() + "." + path, value);
	}
}
