package com.bencodez.advancedcore.api.rewards.injected;

import java.util.HashMap;

import org.bukkit.configuration.ConfigurationSection;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;

public abstract class RewardInjectConfigurationSection extends RewardInject {

	public RewardInjectConfigurationSection(String path) {
		super(path);
	}

	@Override
	public String onRewardRequest(Reward reward, AdvancedCoreUser user, ConfigurationSection data,
			HashMap<String, String> placeholders) {
		if (data.isConfigurationSection(getPath()) || (isAlwaysForce() && data.contains(getPath(), true))) {
			AdvancedCorePlugin.getInstance().extraDebug(reward.getRewardName() + ": Giving " + getPath());
			return onRewardRequested(reward, user, data.getConfigurationSection(getPath()), placeholders);
		}
		return null;
	}

	public abstract String onRewardRequested(Reward reward, AdvancedCoreUser user, ConfigurationSection section,
			HashMap<String, String> placeholders);

}
