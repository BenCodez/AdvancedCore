package com.bencodez.advancedcore.api.rewards.injected;

import java.util.HashMap;

import org.bukkit.configuration.ConfigurationSection;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.user.User;

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
	public String onRewardRequest(Reward reward, User user, ConfigurationSection data,
			HashMap<String, String> placeholders) {
		if ((data.isString(getPath()) && !data.getString(getPath(), "").isEmpty())
				|| (isAlwaysForce() && data.contains(getPath(), true))) {
			String value = data.getString(getPath(), getDefaultValue());
			AdvancedCorePlugin.getInstance()
					.extraDebug(reward.getRewardName() + ": Giving " + getPath() + ", value: " + value);
			String re = onRewardRequest(reward, user, value, placeholders);
			if (re == null) {
				return value;
			} else {
				return re;
			}
		}
		return null;
	}

	public abstract String onRewardRequest(Reward reward, User user, String value,
			HashMap<String, String> placeholders);

}
