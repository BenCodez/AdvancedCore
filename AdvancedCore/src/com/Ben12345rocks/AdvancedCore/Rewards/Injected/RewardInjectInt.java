package com.Ben12345rocks.AdvancedCore.Rewards.Injected;

import java.util.HashMap;

import org.bukkit.configuration.ConfigurationSection;

import com.Ben12345rocks.AdvancedCore.Rewards.Reward;
import com.Ben12345rocks.AdvancedCore.UserManager.User;

import lombok.Getter;
import lombok.Setter;

public abstract class RewardInjectInt extends RewardInject {

	@Getter
	@Setter
	private int defaultValue = 0;

	public RewardInjectInt(String path) {
		super(path);
	}

	public RewardInjectInt(String path, int defaultValue) {
		super(path);
		this.defaultValue = defaultValue;
	}

	@Override
	public void onRewardRequest(Reward reward, User user, ConfigurationSection data,
			HashMap<String, String> placeholders) {
		if (data.isInt(getPath())) {
			onRewardRequest(reward, user, data.getInt(getPath(), getDefaultValue()), placeholders);
		}
	}

	public abstract void onRewardRequest(Reward reward, User user, int num, HashMap<String, String> placeholders);

}
