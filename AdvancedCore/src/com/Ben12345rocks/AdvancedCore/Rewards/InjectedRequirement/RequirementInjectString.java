package com.Ben12345rocks.AdvancedCore.Rewards.InjectedRequirement;

import org.bukkit.configuration.ConfigurationSection;

import com.Ben12345rocks.AdvancedCore.AdvancedCorePlugin;
import com.Ben12345rocks.AdvancedCore.Rewards.Reward;
import com.Ben12345rocks.AdvancedCore.Rewards.RewardOptions;
import com.Ben12345rocks.AdvancedCore.UserManager.User;

import lombok.Getter;
import lombok.Setter;

public abstract class RequirementInjectString extends RequirementInject {

	@Getter
	@Setter
	private String defaultValue;

	public RequirementInjectString(String path) {
		super(path);
	}

	public RequirementInjectString(String path, String defaultValue) {
		super(path);
		this.defaultValue = defaultValue;
	}

	@Override
	public boolean onRequirementRequest(Reward reward, User user, ConfigurationSection data,
			RewardOptions rewardOptions) {
		if ((data.isString(getPath()) && !data.getString(getPath(), "").isEmpty())
				|| (isAlwaysForce() && data.contains(getPath(), true))) {
			String value = data.getString(getPath(), getDefaultValue());
			AdvancedCorePlugin.getInstance()
					.extraDebug(reward.getRewardName() + ": Checking " + getPath() + ", value: " + value);
			return onRequirementsRequest(reward, user, value, rewardOptions);

		}
		return true;
	}

	public abstract boolean onRequirementsRequest(Reward reward, User user, String value, RewardOptions rewardOptions);

}
