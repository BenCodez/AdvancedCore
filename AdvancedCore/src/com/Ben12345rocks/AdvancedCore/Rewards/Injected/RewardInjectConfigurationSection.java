package com.Ben12345rocks.AdvancedCore.Rewards.Injected;

import java.util.HashMap;

import org.bukkit.configuration.ConfigurationSection;

import com.Ben12345rocks.AdvancedCore.AdvancedCoreHook;
import com.Ben12345rocks.AdvancedCore.Rewards.Reward;
import com.Ben12345rocks.AdvancedCore.UserManager.User;

public abstract class RewardInjectConfigurationSection extends RewardInject {

	public RewardInjectConfigurationSection(String path) {
		super(path);
	}

	@Override
	public String onRewardRequest(Reward reward, User user, ConfigurationSection data,
			HashMap<String, String> placeholders) {
		if (data.isConfigurationSection(getPath()) || isAlwaysForce()) {
			AdvancedCoreHook.getInstance().extraDebug(reward.getRewardName() + ": Giving " + getPath());
			return onRewardRequested(reward, user, data.getConfigurationSection(getPath()), placeholders);
		}
		return null;
	}

	public abstract String onRewardRequested(Reward reward, User user, ConfigurationSection section,
			HashMap<String, String> placeholders);

}
