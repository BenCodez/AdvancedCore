package com.bencodez.advancedcore.api.rewards.injectedrequirement;

import org.bukkit.configuration.ConfigurationSection;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.rewards.RewardOptions;
import com.bencodez.advancedcore.api.user.User;

public abstract class RequirementInjectConfigurationSection extends RequirementInject {

	public RequirementInjectConfigurationSection(String path) {
		super(path);
	}

	@Override
	public boolean onRequirementRequest(Reward reward, User user, ConfigurationSection data,
			RewardOptions rewardOptions) {
		if (data.isConfigurationSection(getPath()) || (isAlwaysForce() && data.contains(getPath(), true))) {
			AdvancedCorePlugin.getInstance().extraDebug(reward.getRewardName() + ": Checking " + getPath());
			return onRequirementsRequested(reward, user, data.getConfigurationSection(getPath()), rewardOptions);
		}
		return true;
	}

	public abstract boolean onRequirementsRequested(Reward reward, User user, ConfigurationSection section,
			RewardOptions rewardOptions);

}
