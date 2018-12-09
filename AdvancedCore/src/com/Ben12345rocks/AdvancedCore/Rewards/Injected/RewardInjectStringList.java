package com.Ben12345rocks.AdvancedCore.Rewards.Injected;

import java.util.ArrayList;
import java.util.HashMap;

import org.bukkit.configuration.ConfigurationSection;

import com.Ben12345rocks.AdvancedCore.Rewards.Reward;
import com.Ben12345rocks.AdvancedCore.UserManager.User;

import lombok.Getter;
import lombok.Setter;

public abstract class RewardInjectStringList extends RewardInject {

	@Getter
	@Setter
	private ArrayList<String> defaultValue = new ArrayList<String>();

	public RewardInjectStringList(String path) {
		super(path);
	}

	public RewardInjectStringList(String path, ArrayList<String> defaultValue) {
		super(path);
		this.defaultValue = defaultValue;
	}

	@SuppressWarnings("unchecked")
	@Override
	public void onRewardRequest(Reward reward, User user, ConfigurationSection data, HashMap<String,String> placeholders) {
		if (data.isList(getPath())) {
			onRewardRequest(reward, user, (ArrayList<String>) data.getList(getPath(), getDefaultValue()), placeholders);
		}
	}

	public abstract void onRewardRequest(Reward reward, User user, ArrayList<String> num, HashMap<String, String> placeholders);

}
