package com.Ben12345rocks.AdvancedCore.Rewards.Injected;

import org.bukkit.configuration.ConfigurationSection;

import com.Ben12345rocks.AdvancedCore.UserManager.User;

import lombok.Getter;
import lombok.Setter;

public abstract class RewardInjectInt extends RewardInject {

	@Getter
	@Setter
	private int defaultValue;

	public RewardInjectInt(String path) {
		super(path);
	}

	public RewardInjectInt(String path, int defaultValue) {
		super(path);
		this.defaultValue = defaultValue;
	}

	@Override
	public void onRewardRequest(User user, ConfigurationSection data) {
		if (data.isInt(getPath())) {
			onRewardRequest(user, data.getInt(getPath(), getDefaultValue()));
		}
	}

	public abstract void onRewardRequest(User user, int num);

}
