package com.bencodez.advancedcore.api.rewards.injectedrequirement;

import org.bukkit.configuration.ConfigurationSection;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.rewards.Reward;

public abstract class RequirementInjectValidator {

	public abstract void onValidate(Reward reward, RequirementInject inject, ConfigurationSection data);

	public void warning(Reward reward, RequirementInject inject, String str) {
		AdvancedCorePlugin.getInstance().getLogger()
				.warning("RequirementInject Validator: " + reward.getName() + ", Directly Defined: "
						+ reward.getConfig().isDirectlyDefinedReward() + " Path: " + inject.getPath() + " : " + str);
	}
}
