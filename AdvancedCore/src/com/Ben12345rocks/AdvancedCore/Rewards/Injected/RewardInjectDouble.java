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
	public void onRewardRequest(Reward reward, User user, ConfigurationSection data, HashMap<String, String> placeholders) {
		if (data.isDouble(getPath())) {
			AdvancedCoreHook.getInstance().extraDebug(reward.getRewardName() + ": Giving " + getPath());
			onRewardRequest(reward, user, data.getDouble(getPath(), getDefaultValue()), placeholders);
		}
	}

	public abstract void onRewardRequest(Reward reward, User user, double num, HashMap<String, String> placeholders);

}
