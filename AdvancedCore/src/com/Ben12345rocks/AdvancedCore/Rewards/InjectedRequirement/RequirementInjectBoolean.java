package com.Ben12345rocks.AdvancedCore.Rewards.InjectedRequirement;

import org.bukkit.configuration.ConfigurationSection;

import com.Ben12345rocks.AdvancedCore.AdvancedCorePlugin;
import com.Ben12345rocks.AdvancedCore.Rewards.Reward;
import com.Ben12345rocks.AdvancedCore.Rewards.RewardOptions;
import com.Ben12345rocks.AdvancedCore.UserManager.User;

import lombok.Getter;
import lombok.Setter;

public abstract class RequirementInjectBoolean extends RequirementInject {

	@Getter
	@Setter
	private boolean defaultValue = false;

	public RequirementInjectBoolean(String path) {
		super(path);
	}

	public RequirementInjectBoolean(String path, boolean defaultValue) {
		super(path);
		this.defaultValue = defaultValue;
	}

	public abstract boolean onRequirementsRequest(Reward reward, User user, boolean num,
			RewardOptions rewardOptions);

	@Override
	public boolean onRequirementRequest(Reward reward, User user, ConfigurationSection data,
			RewardOptions rewardOptions) {
		if (data.isBoolean(getPath()) || (isAlwaysForce() && data.contains(getPath(), true))) {
			boolean value = data.getBoolean(getPath(), isDefaultValue());
			AdvancedCorePlugin.getInstance()
					.extraDebug(reward.getRewardName() + ": Checking " + getPath() + ", value: " + value);
			return onRequirementsRequest(reward, user, value, rewardOptions);

		}
		return true;
	}

}
