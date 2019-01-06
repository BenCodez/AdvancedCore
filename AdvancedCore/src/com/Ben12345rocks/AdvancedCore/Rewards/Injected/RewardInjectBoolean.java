package com.Ben12345rocks.AdvancedCore.Rewards.Injected;

import java.util.HashMap;

import org.bukkit.configuration.ConfigurationSection;

import com.Ben12345rocks.AdvancedCore.AdvancedCoreHook;
import com.Ben12345rocks.AdvancedCore.Rewards.Reward;
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

	public abstract void onRewardRequest(Reward reward, User user, boolean num, HashMap<String, String> placeholders);

	@Override
	public void onRewardRequest(Reward reward, User user, ConfigurationSection data,
			HashMap<String, String> placeholders) {
		if (data.isBoolean(getPath())) {
			AdvancedCoreHook.getInstance().extraDebug(reward.getRewardName() + ": Giving " + getPath());
			onRewardRequest(reward, user, data.getBoolean(getPath(), isDefaultValue()), placeholders);
		}
	}

}
