package com.Ben12345rocks.AdvancedCore.Rewards.Injected;

import java.util.HashMap;
import java.util.Set;

import org.bukkit.configuration.ConfigurationSection;

import com.Ben12345rocks.AdvancedCore.AdvancedCoreHook;
import com.Ben12345rocks.AdvancedCore.Rewards.Reward;
import com.Ben12345rocks.AdvancedCore.UserManager.User;

public abstract class RewardInjectKeys extends RewardInject {

	public RewardInjectKeys(String path) {
		super(path);
	}

	@Override
	public String onRewardRequest(Reward reward, User user, ConfigurationSection data,
			HashMap<String, String> placeholders) {
		if (data.isConfigurationSection(getPath())) {
			Set<String> value = data.getConfigurationSection(getPath()).getKeys(false);
			AdvancedCoreHook.getInstance().extraDebug(reward.getRewardName() + ": Giving " + getPath());
			return onRewardRequested(reward, user, value, data.getConfigurationSection(getPath()), placeholders);
		}
		return null;
	}

	public abstract String onRewardRequested(Reward reward, User user, Set<String> section, ConfigurationSection data,
			HashMap<String, String> placeholders);

}
