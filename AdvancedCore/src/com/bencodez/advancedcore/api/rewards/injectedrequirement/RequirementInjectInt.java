package com.bencodez.advancedcore.api.rewards.injectedrequirement;

import org.bukkit.configuration.ConfigurationSection;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.rewards.RewardOptions;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;

import lombok.Getter;
import lombok.Setter;

public abstract class RequirementInjectInt extends RequirementInject {

	@Getter
	@Setter
	private int defaultValue = 0;

	public RequirementInjectInt(String path) {
		super(path);
	}

	public RequirementInjectInt(String path, int defaultValue) {
		super(path);
		this.defaultValue = defaultValue;
	}

	@Override
	public boolean onRequirementRequest(Reward reward, AdvancedCoreUser user, ConfigurationSection data,
			RewardOptions rewardOptions) {
		if (data.isInt(getPath()) || (isAlwaysForce() && data.contains(getPath(), true)) || isAlwaysForceNoData()) {
			int value = data.getInt(getPath(), getDefaultValue());
			AdvancedCorePlugin.getInstance()
					.extraDebug(reward.getRewardName() + ": Checking " + getPath() + ", value: " + value);
			return onRequirementsRequest(reward, user, value, rewardOptions);
		}
		return true;
	}

	public abstract boolean onRequirementsRequest(Reward reward, AdvancedCoreUser user, int num, RewardOptions rewardOptions);

}
