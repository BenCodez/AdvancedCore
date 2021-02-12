package com.bencodez.advancedcore.api.rewards.injectedrequirement;

import org.bukkit.configuration.ConfigurationSection;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.rewards.RewardOptions;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;

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
	public boolean onRequirementRequest(Reward reward, AdvancedCoreUser user, ConfigurationSection data,
			RewardOptions rewardOptions) {
		if ((data.isString(getPath()) && !data.getString(getPath(), "").isEmpty())
				|| (isAlwaysForce() && data.contains(getPath(), true)) || isAlwaysForceNoData()) {
			String value = data.getString(getPath(), getDefaultValue());
			AdvancedCorePlugin.getInstance()
					.extraDebug(reward.getRewardName() + ": Checking " + getPath() + ", value: " + value);
			return onRequirementsRequest(reward, user, value, rewardOptions);

		}
		return true;
	}

	public abstract boolean onRequirementsRequest(Reward reward, AdvancedCoreUser user, String value, RewardOptions rewardOptions);

}
