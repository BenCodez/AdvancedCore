package com.bencodez.advancedcore.api.rewards.injected;

import java.util.HashMap;
import java.util.Set;

import org.bukkit.configuration.ConfigurationSection;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;

public abstract class RewardInjectKeys extends RewardInject {

	public RewardInjectKeys(String path) {
		super(path);
	}

	@Override
	public String onRewardRequest(Reward reward, AdvancedCoreUser user, ConfigurationSection data,
			HashMap<String, String> placeholders) {
		if (data.isConfigurationSection(getPath()) || (isAlwaysForce() && data.contains(getPath(), true))
				|| isAlwaysForceNoData()) {
			Set<String> value = data.getConfigurationSection(getPath()).getKeys(false);
			AdvancedCorePlugin.getInstance().extraDebug(reward.getRewardName() + ": Giving " + getPath());
			return onRewardRequested(reward, user, value, data.getConfigurationSection(getPath()), placeholders);
		}
		return null;
	}

	public abstract String onRewardRequested(Reward reward, AdvancedCoreUser user, Set<String> section,
			ConfigurationSection data, HashMap<String, String> placeholders);

}
