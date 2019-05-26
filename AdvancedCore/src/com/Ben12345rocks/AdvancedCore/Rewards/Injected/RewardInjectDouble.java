package com.Ben12345rocks.AdvancedCore.Rewards.Injected;

import java.util.HashMap;

import org.bukkit.configuration.ConfigurationSection;

import com.Ben12345rocks.AdvancedCore.AdvancedCoreHook;
import com.Ben12345rocks.AdvancedCore.Rewards.Reward;
import com.Ben12345rocks.AdvancedCore.UserManager.User;

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
	public Double onRewardRequest(Reward reward, User user, ConfigurationSection data,
			HashMap<String, String> placeholders) {
		if (data.isDouble(getPath()) || data.isInt(getPath())) {
			double value = data.getDouble(getPath(), getDefaultValue());
			AdvancedCoreHook.getInstance()
					.extraDebug(reward.getRewardName() + ": Giving " + getPath() + ", value: " + value);
			Double d = onRewardRequest(reward, user, value, placeholders);
			if (d == null) {
				return value;
			} else {
				return d;
			}
		}
		return null;
	}

	public abstract Double onRewardRequest(Reward reward, User user, double num, HashMap<String, String> placeholders);

}
