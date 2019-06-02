package com.Ben12345rocks.AdvancedCore.Rewards.InjectedRequirement;

import org.bukkit.configuration.ConfigurationSection;

import com.Ben12345rocks.AdvancedCore.AdvancedCoreHook;
import com.Ben12345rocks.AdvancedCore.Rewards.Reward;
import com.Ben12345rocks.AdvancedCore.Rewards.RewardOptions;
import com.Ben12345rocks.AdvancedCore.UserManager.User;

public abstract class RequirementInjectConfigurationSection extends RequirementInject {

	public RequirementInjectConfigurationSection(String path) {
		super(path);
	}

	@Override
	public boolean onRequirementRequest(Reward reward, User user, ConfigurationSection data,
			RewardOptions rewardOptions) {
		if (data.isConfigurationSection(getPath()) || isAlwaysForce()) {
			AdvancedCoreHook.getInstance().extraDebug(reward.getRewardName() + ": Checking " + getPath());
			return onRequirementsRequested(reward, user, data.getConfigurationSection(getPath()), rewardOptions);
		}
		return true;
	}

	public abstract boolean onRequirementsRequested(Reward reward, User user, ConfigurationSection section,
			RewardOptions rewardOptions);

}
