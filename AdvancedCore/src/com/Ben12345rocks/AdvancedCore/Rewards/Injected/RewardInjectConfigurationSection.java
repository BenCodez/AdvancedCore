package com.Ben12345rocks.AdvancedCore.Rewards.Injected;

import java.util.HashMap;

import org.bukkit.configuration.ConfigurationSection;

import com.Ben12345rocks.AdvancedCore.UserManager.User;

public abstract class RewardInjectConfigurationSection extends RewardInject {

	public RewardInjectConfigurationSection(String path) {
		super(path);
	}

	@Override
	public void onRewardRequest(User user, ConfigurationSection data, HashMap<String, String> placeholders) {
		if (data.isInt(getPath())) {
			onRewardRequested(user, data.getConfigurationSection(getPath()), placeholders);
		}
	}

	public abstract void onRewardRequested(User user, ConfigurationSection section,
			HashMap<String, String> placeholders);

}
