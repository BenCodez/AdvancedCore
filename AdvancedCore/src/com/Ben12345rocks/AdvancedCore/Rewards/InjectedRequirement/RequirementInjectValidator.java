package com.Ben12345rocks.AdvancedCore.Rewards.InjectedRequirement;

import org.bukkit.configuration.ConfigurationSection;

import com.Ben12345rocks.AdvancedCore.AdvancedCorePlugin;
import com.Ben12345rocks.AdvancedCore.Rewards.Reward;

public abstract class RequirementInjectValidator {

	public abstract void onValidate(Reward reward, RequirementInject inject, ConfigurationSection data);

	public void warning(Reward reward, RequirementInject inject, String str) {
		AdvancedCorePlugin.getInstance().getLogger().warning(reward.getName() + "("
				+ reward.getConfig().isDirectlyDefinedReward() + ") : " + inject.getPath() + " : " + str);
	}
}
