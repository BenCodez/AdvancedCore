package com.Ben12345rocks.AdvancedCore.Rewards.Injected;

import org.bukkit.configuration.ConfigurationSection;

import com.Ben12345rocks.AdvancedCore.AdvancedCorePlugin;
import com.Ben12345rocks.AdvancedCore.Rewards.Reward;

public abstract class RewardInjectValidator {
	public abstract void onValidate(Reward reward, RewardInject inject, ConfigurationSection data);

	public void warning(Reward reward, RewardInject inject, String str) {
		AdvancedCorePlugin.getInstance().getLogger()
				.warning("RewardInject Validator: " + reward.getName() + ", Directly Defined: "
						+ reward.getConfig().isDirectlyDefinedReward() + " Path: " + inject.getPath() + " : " + str);
	}
}
