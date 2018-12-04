package com.Ben12345rocks.AdvancedCore.Rewards.Injected;

import org.bukkit.configuration.ConfigurationSection;

import com.Ben12345rocks.AdvancedCore.UserManager.User;

import lombok.Getter;
import lombok.Setter;

public abstract class RewardInject {
	@Getter
	@Setter
	private String path;

	public RewardInject(String path) {
		this.path = path;
	}

	public abstract void onRewardRequest(User user, ConfigurationSection data);
}
