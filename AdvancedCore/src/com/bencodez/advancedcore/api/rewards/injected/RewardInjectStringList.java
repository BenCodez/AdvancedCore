package com.bencodez.advancedcore.api.rewards.injected;

import java.util.ArrayList;
import java.util.HashMap;

import org.bukkit.configuration.ConfigurationSection;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.misc.ArrayUtils;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;

import lombok.Getter;
import lombok.Setter;

public abstract class RewardInjectStringList extends RewardInject {

	@Getter
	@Setter
	private ArrayList<String> defaultValue = new ArrayList<String>();

	public RewardInjectStringList(String path) {
		super(path);
	}

	public RewardInjectStringList(String path, ArrayList<String> defaultValue) {
		super(path);
		this.defaultValue = defaultValue;
	}

	public abstract String onRewardRequest(Reward reward, AdvancedCoreUser user, ArrayList<String> num,
			HashMap<String, String> placeholders);

	@SuppressWarnings("unchecked")
	@Override
	public String onRewardRequest(Reward reward, AdvancedCoreUser user, ConfigurationSection data,
			HashMap<String, String> placeholders) {
		if (data.isList(getPath()) || (isAlwaysForce() && data.contains(getPath(), true))) {
			ArrayList<String> value = (ArrayList<String>) data.getList(getPath(), getDefaultValue());
			AdvancedCorePlugin.getInstance().extraDebug(reward.getRewardName() + ": Giving " + getPath() + ", value: "
					+ ArrayUtils.getInstance().makeStringList(value));
			return onRewardRequest(reward, user, value, placeholders);

		}
		return null;
	}

}
