package com.Ben12345rocks.AdvancedCore.Rewards.InjectedRequirement;

import org.bukkit.configuration.ConfigurationSection;

import com.Ben12345rocks.AdvancedCore.AdvancedCoreHook;
import com.Ben12345rocks.AdvancedCore.Rewards.Reward;
import com.Ben12345rocks.AdvancedCore.Rewards.RewardOptions;
import com.Ben12345rocks.AdvancedCore.UserManager.User;

import lombok.Getter;
import lombok.Setter;

public abstract class RequirementInjectDouble extends RequirementInject {

	@Getter
	@Setter
	private double defaultValue = 0;

	public RequirementInjectDouble(String path) {
		super(path);
	}

	public RequirementInjectDouble(String path, double defaultValue) {
		super(path);
		this.defaultValue = defaultValue;
	}

	public abstract boolean onRequirementsRequest(Reward reward, User user, double num,
			RewardOptions rewardOptions);

	@Override
	public boolean onRequirementRequest(Reward reward, User user, ConfigurationSection data,
			RewardOptions rewardOptions) {
		if (data.isDouble(getPath()) || data.isInt(getPath())) {
			double value = data.getDouble(getPath(), getDefaultValue());
			AdvancedCoreHook.getInstance()
					.extraDebug(reward.getRewardName() + ": Checking " + getPath() + ", value: " + value);
			return onRequirementsRequest(reward, user, value, rewardOptions);
		}
		return true;
	}

}
