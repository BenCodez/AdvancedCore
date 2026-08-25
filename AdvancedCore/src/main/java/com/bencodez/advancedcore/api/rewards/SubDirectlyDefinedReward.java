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

	public void createSection(String key) {
		master.createSection(master.getPath() + master.needsDot() + key);
	}

	public void createSectionLocal(String key) {
		createSection(getPath() + "." + key);
	}

	public ConfigurationSection getFileData() {
		return master.getFileData().getConfigurationSection(master.getPath());
	}

	public String getFullPath() {
		return master.getFullPath() + master.needsDot() + path;
	}

	public Reward getReward() {
		if (isDirectlyDefined()) {
			Reward reward = new Reward((master.getFullPath() + master.needsDot() + getPath()).replace(".", "_"),
					getFileData().getConfigurationSection(getPath()));
			// File-backed sub rewards are not exposed through standalone lookup, but they
			// still need a generated queue snapshot if delayed/timed/offline execution
			// defers them. Directly-defined sub rewards already have a stable internal
			// definition and do not need snapshot materialization.
			return isFileBacked() ? reward : reward.needsRewardFile(false);
		}
		return null;
	}

	private boolean isFileBacked() {
		DefinedReward current = master;
		while (current instanceof SubDirectlyDefinedReward) {
			current = ((SubDirectlyDefinedReward) current).getMaster();
		}
		return current instanceof RewardFileDefinedReward;
	}

	public Object getValue(String path) {
		return getFileData().get(getPath() + "." + path);
	}

	public boolean isDirectlyDefined() {
		return getFileData().isConfigurationSection(getPath());
	}

	@Override
	public String needsDot() {
		return ".";
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
}
