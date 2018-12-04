package com.Ben12345rocks.AdvancedCore.Rewards.Injected;

import java.util.HashMap;

import org.bukkit.configuration.ConfigurationSection;

import com.Ben12345rocks.AdvancedCore.UserManager.User;

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

	@Override
	public void onRewardRequest(User user, ConfigurationSection data, HashMap<String, String> placeholders) {
		if (data.isBoolean(getPath())) {
			onRewardRequest(user, data.getBoolean(getPath(), isDefaultValue()), placeholders);
		}
	}

	public abstract void onRewardRequest(User user, boolean num, HashMap<String, String> placeholders);

}
