package com.bencodez.advancedcore.api.rewards;

import com.bencodez.advancedcore.api.user.AdvancedCoreUser;

import lombok.Getter;
import lombok.Setter;

public abstract class RewardPlaceholderHandle {
	@Getter
	@Setter
	private String key;

	@Getter
	@Setter
	private boolean preProcess = false;

	public RewardPlaceholderHandle(String key) {
		this.key = key;
	}

	public abstract String getValue(Reward reward, AdvancedCoreUser user);

	public RewardPlaceholderHandle preProcess() {
		preProcess = true;
		return this;
	}

}
