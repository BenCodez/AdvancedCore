package com.Ben12345rocks.AdvancedCore.Rewards.Injected;

import org.bukkit.configuration.ConfigurationSection;

import com.Ben12345rocks.AdvancedCore.UserManager.User;

import lombok.Getter;
import lombok.Setter;

public abstract class RewardInjectString extends RewardInject {

	@Getter
	@Setter
	private String defaultValue;

	public RewardInjectString(String path) {
		super(path);
	}

	public RewardInjectString(String path, String defaultValue) {
		super(path);
		this.defaultValue = defaultValue;
	}

	@Override
	public void onRewardRequest(User user, ConfigurationSection data) {
		if (data.isString(getPath())) {
			onRewardRequest(user, data.getString(getPath(), getDefaultValue()));
		}
	}

	public abstract void onRewardRequest(User user, String value);

}
