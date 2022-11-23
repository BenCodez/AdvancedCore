package com.bencodez.advancedcore.api.rewards;

import org.bukkit.configuration.ConfigurationSection;

import lombok.Getter;
import lombok.Setter;

public class SubDirectlyDefinedReward {
	@Getter
	@Setter
	private String path;

	@Getter
	@Setter
	private DirectlyDefinedReward master;

	public SubDirectlyDefinedReward(DirectlyDefinedReward master, String path) {
		this.master = master;
		this.path = path;
	}

	public String getFullPath() {
		return master.getPath() + "." + path;
	}

	public void createSection(String key) {
		master.createSection(master.getPath() + "." + key);
	}

	public void createSectionLocal(String key) {
		createSection(getPath() + "." + key);
	}

	public ConfigurationSection getFileData() {
		return master.getFileData().getConfigurationSection(master.getPath());
	}

	public Reward getReward() {
		if (isDirectlyDefined()) {
			return new Reward((master.getPath() + "." + getPath()).replace(".", "_"),
					getFileData().getConfigurationSection(getPath())).needsRewardFile(false);
		} else {
			return null;
		}
	}

	public Object getValue(String path) {
		return getFileData().get(getPath() + "." + path);
	}

	public boolean isDirectlyDefined() {
		return getFileData().isConfigurationSection(getPath());
	}

	public void save() {
		master.save();
	}

	public void setData(String path, Object value) {
		master.setData(master.getPath() + "." + path, value);
	}

	public void setParentValue(Object value) {
		setData(getPath(), value);
	}

	public void setValue(String path, Object value) {
		setData(getPath() + "." + path, value);
	}
}
