package com.bencodez.advancedcore.api.rewards;

import org.bukkit.configuration.ConfigurationSection;

import lombok.Getter;
import lombok.Setter;

public class RewardFileDefinedReward implements DefinedReward {
	@Getter
	@Setter
	private String path;

	@Getter
	@Setter
	private Reward master;

	public RewardFileDefinedReward(Reward master, String path) {
		this.master = master;
		this.path = path;
	}

	public RewardFileDefinedReward(Reward master) {
		this.master = master;
		path = "";
	}

	public String getFullPath() {
		return master.getName() + "." + path;
	}

	public void createSection(String key) {
		master.getConfig().createSection(key);
	}

	public void createSectionLocal(String key) {
		if (getPath().isEmpty()) {
			createSection(key);
		} else {
			createSection(getPath() + "." + key);
		}
	}

	public ConfigurationSection getFileData() {
		return master.getConfig().getConfigData();
	}

	public Reward getReward() {
		if (getPath().isEmpty()) {
			return master;
		}
		if (isDirectlyDefined()) {
			return new Reward((getPath()).replace(".", "_"), getFileData().getConfigurationSection(getPath()))
					.needsRewardFile(false);
		} else {
			return null;
		}
	}

	public Object getValue(String path) {
		if (getPath().isEmpty()) {
			return getFileData().get(path);
		}
		return getFileData().get(getPath() + "." + path);
	}

	public boolean isDirectlyDefined() {
		if (path.isEmpty()) {
			return true;
		}
		return getFileData().isConfigurationSection(getPath());
	}

	public void save() {
		master.getConfig().save();
	}

	public void setData(String path, Object value) {
		master.getConfig().set(path, value);
	}

	public void setParentValue(Object value) {
		setData(getPath(), value);
	}

	public void setValue(String path, Object value) {
		if (getPath().isEmpty()) {
			setData(path, value);
		} else {
			setData(getPath() + "." + path, value);
		}
	}

	@Override
	public String needsDot() {
		if (getPath().isEmpty()) {
			return "";
		}
		return ".";
	}
}
