package com.bencodez.advancedcore.api.rewards.injected;

import org.bukkit.configuration.ConfigurationSection;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.rewards.Reward;

public abstract class RewardInjectValidator {
	public abstract void onValidate(Reward reward, RewardInject inject, ConfigurationSection data);

	public void warning(Reward reward, RewardInject inject, String str) {
		AdvancedCorePlugin.getInstance().getLogger()
				.warning("RewardInject Validator: " + reward.getName() + ", Directly Defined: "
						+ reward.getConfig().isDirectlyDefinedReward() + " Path: " + inject.getPath() + " : " + str);
	}
}
