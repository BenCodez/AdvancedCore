package com.bencodez.advancedcore.api.rewards.injectedrequirement;

import java.util.ArrayList;

import org.bukkit.configuration.ConfigurationSection;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.misc.ArrayUtils;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.rewards.RewardOptions;
import com.bencodez.advancedcore.api.user.User;

import lombok.Getter;
import lombok.Setter;

public abstract class RequirementInjectStringList extends RequirementInject {

	@Getter
	@Setter
	private ArrayList<String> defaultValue = new ArrayList<String>();

	public RequirementInjectStringList(String path) {
		super(path);
	}

	public RequirementInjectStringList(String path, ArrayList<String> defaultValue) {
		super(path);
		this.defaultValue = defaultValue;
	}

	@SuppressWarnings("unchecked")
	@Override
	public boolean onRequirementRequest(Reward reward, User user, ConfigurationSection data,
			RewardOptions rewardOptions) {
		if (data.isList(getPath()) || (isAlwaysForce() && data.contains(getPath(), true))) {
			ArrayList<String> value = (ArrayList<String>) data.getList(getPath(), getDefaultValue());
			AdvancedCorePlugin.getInstance().extraDebug(reward.getRewardName() + ": Checking " + getPath() + ", value: "
					+ ArrayUtils.getInstance().makeStringList(value));
			return onRequirementsRequest(reward, user, value, rewardOptions);

		}
		return true;
	}

	public abstract boolean onRequirementsRequest(Reward reward, User user, ArrayList<String> num,
			RewardOptions rewardOptions);

}
