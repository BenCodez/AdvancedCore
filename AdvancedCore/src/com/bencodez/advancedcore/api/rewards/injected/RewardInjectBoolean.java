package com.bencodez.advancedcore.api.rewards.injected;

import java.util.HashMap;

import org.bukkit.configuration.ConfigurationSection;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;

import lombok.Getter;
import lombok.Setter;

public abstract class RewardInjectBoolean extends RewardInject {

	@Getter
	@Setter
	private boolean defaultValue = false;

	public RewardInjectBoolean(String path) {
		super(path);
	}

	public RewardInjectBoolean(String path, boolean defaultValue) {
		super(path);
		this.defaultValue = defaultValue;
	}

	public abstract String onRewardRequest(Reward reward, AdvancedCoreUser user, boolean num, HashMap<String, String> placeholders);

	@Override
	public String onRewardRequest(Reward reward, AdvancedCoreUser user, ConfigurationSection data,
			HashMap<String, String> placeholders) {
		if (data.isBoolean(getPath()) || (isAlwaysForce() && data.contains(getPath(), true))) {
			boolean value = data.getBoolean(getPath(), isDefaultValue());
			AdvancedCorePlugin.getInstance()
					.extraDebug(reward.getRewardName() + ": Giving " + getPath() + ", value: " + value);
			String re = onRewardRequest(reward, user, value, placeholders);
			if (re == null) {
				return "" + value;
			} else {
				return re;
			}
		}
		return null;
	}

}
