package com.bencodez.advancedcore.api.rewards;

import org.bukkit.configuration.ConfigurationSection;

import lombok.Getter;
import lombok.Setter;

public class SubDirectlyDefinedReward implements DefinedReward {
	@Getter
	@Setter
	private String path;

	@Getter
	@Setter
	private DefinedReward master;

	public SubDirectlyDefinedReward(DefinedReward master, String path) {
		this.master = master;
		this.path = path;
	}

	public String getFullPath() {
		return master.getFullPath() + master.needsDot() + path;
	}

	public void createSection(String key) {
		master.createSection(master.getPath() + master.needsDot() + key);
	}

	public void createSectionLocal(String key) {
		createSection(getPath() + "." + key);
	}

	public ConfigurationSection getFileData() {
		return master.getFileData().getConfigurationSection(master.getPath());
	}

	public Reward getReward() {
		if (isDirectlyDefined()) {
			return new Reward((master.getFullPath() + master.needsDot() + getPath()).replace(".", "_"),
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
		master.setData(master.getPath() + master.needsDot() + path, value);
	}

	public void setParentValue(Object value) {
		setData(getPath(), value);
	}

	public void setValue(String path, Object value) {
		setData(getPath() + "." + path, value);
	}

	@Override
	public String needsDot() {
		return ".";
	}
}
