package com.bencodez.advancedcore.api.rewards;

import org.bukkit.configuration.ConfigurationSection;

import com.bencodez.advancedcore.AdvancedCorePlugin;

import lombok.Getter;
import lombok.Setter;

public abstract class DirectlyDefinedReward {
	@Getter
	@Setter
	private String path;

	public DirectlyDefinedReward(String path) {
		this.path = path;
	}

	public boolean isDirectlyDefined() {
		return getFileData().isConfigurationSection(getPath());
	}

	public abstract ConfigurationSection getFileData();

	public abstract void setData(String path, Object value);

	public void setValue(String path, Object value) {
		setData(getPath() + "." + path, value);
	}

	public Reward getReward() {
		if (isDirectlyDefined()) {
			return new Reward(getPath().replaceAll(".", "_"), getFileData().getConfigurationSection(getPath()));
		} else {
			return null;
		}
	}
	
	public Object getValue(String path) {
		return getFileData().get(getPath() + "." + path);
	}

	public void updateRewardData(AdvancedCorePlugin plugin) {
		if (isDirectlyDefined()) {
			// update directlydefined reward
			plugin.debug("Updating directlydefined reward: " + getPath());
			ConfigurationSection section = getFileData().getConfigurationSection(path);
			Reward reward = new Reward(getPath().replace(".", "_"), section);
			reward.checkRewardFile();
		}
	}
}
