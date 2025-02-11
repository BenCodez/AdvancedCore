package com.bencodez.advancedcore.api.rewards.injectedrequirement;

import java.util.ArrayList;

import org.bukkit.configuration.ConfigurationSection;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.rewards.Reward;

import lombok.Getter;

public abstract class RequirementInjectValidator {

	@Getter
	private ArrayList<String> paths = new ArrayList<>();

	public RequirementInjectValidator addPath(String path) {
		paths.add(path);
		return this;
	}

	public boolean isValid(RequirementInject inject, String path) {
		return inject.getPath().startsWith(path) || paths.contains(path);
	}

	public abstract void onValidate(Reward reward, RequirementInject inject, ConfigurationSection data);

	public void warning(Reward reward, RequirementInject inject, String str) {
		AdvancedCorePlugin.getInstance().getLogger()
				.warning("RequirementInject Validator: " + reward.getName() + ", Directly Defined: "
						+ reward.getConfig().isDirectlyDefinedReward() + " Path: " + inject.getPath() + " : " + str);
	}
}
