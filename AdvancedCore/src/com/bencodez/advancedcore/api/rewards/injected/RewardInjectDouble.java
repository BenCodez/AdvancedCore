package com.bencodez.advancedcore.api.rewards.injected;

import java.util.HashMap;

import org.bukkit.configuration.ConfigurationSection;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.user.User;

import lombok.Getter;
import lombok.Setter;

public abstract class RewardInjectDouble extends RewardInject {

	@Getter
	@Setter
	private double defaultValue = 0;

	public RewardInjectDouble(String path) {
		super(path);
	}

	public RewardInjectDouble(String path, double defaultValue) {
		super(path);
		this.defaultValue = defaultValue;
	}

	@Override
	public String onRewardRequest(Reward reward, User user, ConfigurationSection data,
			HashMap<String, String> placeholders) {
		if (data.isDouble(getPath()) || data.isInt(getPath()) || (isAlwaysForce() && data.contains(getPath(), true))) {
			double value = data.getDouble(getPath(), getDefaultValue());
			AdvancedCorePlugin.getInstance()
					.extraDebug(reward.getRewardName() + ": Giving " + getPath() + ", value: " + value);
			String d = onRewardRequest(reward, user, value, placeholders);
			if (d == null) {
				return "" + value;
			} else {
				return d;
			}
		}
		return null;
	}

	public abstract String onRewardRequest(Reward reward, User user, double num, HashMap<String, String> placeholders);

}
