package com.Ben12345rocks.AdvancedCore.Rewards;

import com.Ben12345rocks.AdvancedCore.UserManager.User;

import lombok.Getter;
import lombok.Setter;

public abstract class RewardInject {
	@Getter
	@Setter
	private String path;

	@Getter
	@Setter
	private Object defaultValue;

	public RewardInject(String path) {
		this.path = path;
	}

	public RewardInject(String path, Object defaultValue) {
		this.path = path;
		this.defaultValue = defaultValue;
	}

	public abstract void onRewardRequest(User user, Object value);
}
