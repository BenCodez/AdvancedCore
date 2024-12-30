package com.bencodez.advancedcore.api.rewards.injected;

import java.util.ArrayList;

import org.bukkit.configuration.ConfigurationSection;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.rewards.Reward;

import lombok.Getter;

public abstract class RewardInjectValidator {
	@Getter
	private ArrayList<String> paths = new ArrayList<>();

	public RewardInjectValidator addPath(String path) {
		paths.add(path);
		return this;
	}

	public boolean isValid(RewardInject inject, String path) {
		return inject.getPath().startsWith(path) || paths.contains(path);
	}

	public abstract void onValidate(Reward reward, RewardInject inject, ConfigurationSection data);

	public void warning(Reward reward, RewardInject inject, String str) {
		AdvancedCorePlugin.getInstance().getLogger()
				.warning("RewardInject Validator: " + reward.getName() + ", Directly Defined: "
						+ reward.getConfig().isDirectlyDefinedReward() + " Path: " + inject.getPath() + " : " + str);
	}
}
