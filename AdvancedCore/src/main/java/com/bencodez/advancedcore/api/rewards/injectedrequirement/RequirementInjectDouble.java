package com.bencodez.advancedcore.api.rewards.injectedrequirement;

import org.bukkit.configuration.ConfigurationSection;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.rewards.RewardOptions;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;

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

	@Override
	public boolean onRequirementRequest(Reward reward, AdvancedCoreUser user, ConfigurationSection data,
			RewardOptions rewardOptions) {
		if (data.isDouble(getPath()) || data.isInt(getPath()) || (isAlwaysForce() && data.contains(getPath(), true))
				|| isAlwaysForceNoData()) {
			double value = data.getDouble(getPath(), getDefaultValue());
			AdvancedCorePlugin.getInstance()
					.extraDebug(reward.getRewardName() + ": Checking " + getPath() + ", value: " + value);
			return onRequirementsRequest(reward, user, value, rewardOptions);
		}
		return true;
	}

	public abstract boolean onRequirementsRequest(Reward reward, AdvancedCoreUser user, double num,
			RewardOptions rewardOptions);

}
