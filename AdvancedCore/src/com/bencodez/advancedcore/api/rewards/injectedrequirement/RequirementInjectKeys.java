package com.bencodez.advancedcore.api.rewards.injectedrequirement;

import java.util.Set;

import org.bukkit.configuration.ConfigurationSection;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.rewards.RewardOptions;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;

public abstract class RequirementInjectKeys extends RequirementInject {

	public RequirementInjectKeys(String path) {
		super(path);
	}

	@Override
	public boolean onRequirementRequest(Reward reward, AdvancedCoreUser user, ConfigurationSection data,
			RewardOptions rewardOptions) {
		if (data.isConfigurationSection(getPath()) || (isAlwaysForce() && data.contains(getPath(), true))
				|| isAlwaysForceNoData()) {
			Set<String> value = data.getConfigurationSection(getPath()).getKeys(false);
			AdvancedCorePlugin.getInstance().extraDebug(reward.getRewardName() + ": Checking " + getPath());
			return onRequirementsRequested(reward, user, value, data.getConfigurationSection(getPath()), rewardOptions);
		}
		return true;
	}

	public abstract boolean onRequirementsRequested(Reward reward, AdvancedCoreUser user, Set<String> section,
			ConfigurationSection data, RewardOptions rewardOptions);

}
