package com.Ben12345rocks.AdvancedCore.Rewards.Injected;

import java.util.HashMap;

import org.bukkit.configuration.ConfigurationSection;

import com.Ben12345rocks.AdvancedCore.Rewards.Reward;
import com.Ben12345rocks.AdvancedCore.UserManager.User;

public abstract class RewardInjectConfigurationSection extends RewardInject {

	public RewardInjectConfigurationSection(String path) {
		super(path);
	}

	@Override
	public void onRewardRequest(Reward reward, User user, ConfigurationSection data, HashMap<String, String> placeholders) {
		if (data.isConfigurationSection(getPath())) {
			onRewardRequested(reward, user, data.getConfigurationSection(getPath()), placeholders);
		}
	}

	public abstract void onRewardRequested(Reward reward, User user, ConfigurationSection section,
			HashMap<String, String> placeholders);

}
