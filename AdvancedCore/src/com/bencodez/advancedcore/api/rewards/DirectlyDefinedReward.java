package com.bencodez.advancedcore.api.rewards;

import org.bukkit.configuration.ConfigurationSection;

import lombok.Getter;
import lombok.Setter;

public abstract class DirectlyDefinedReward {
	@Getter
	@Setter
	private String path;

	public DirectlyDefinedReward(String path) {
		this.path = path;
	}

	public abstract ConfigurationSection getFileData();

	public abstract void setData(String path, Object value);

	public void updateRewardData() {
		if (getFileData().isConfigurationSection(getPath())) {
			// update directlydefined reward
			ConfigurationSection section = getFileData().getConfigurationSection(path);
			Reward reward = new Reward(getPath().replace(".", "_"), section);
			reward.checkRewardFile();
		}
	}
}
