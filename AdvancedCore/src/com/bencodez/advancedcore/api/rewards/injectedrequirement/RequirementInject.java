package com.bencodez.advancedcore.api.rewards.injectedrequirement;

import org.bukkit.configuration.ConfigurationSection;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.inventory.editgui.EditGUIButton;
import com.bencodez.advancedcore.api.rewards.Inject;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.rewards.RewardOptions;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;

import lombok.Getter;

public abstract class RequirementInject extends Inject {

	@Getter
	private boolean allowReattempt = false;

	@Getter
	private boolean alwaysForce = false;

	@Getter
	private boolean alwaysForceNoData = false;

	@Getter
	private RequirementInjectValidator validate;

	public RequirementInject(String path) {
		super(path);
	}

	public RequirementInject addEditButton(EditGUIButton button) {
		getEditButtons().add(button);
		return this;
	}

	public RequirementInject allowReattempt() {
		this.allowReattempt = true;
		return this;
	}

	public RequirementInject alwaysForce() {
		this.alwaysForce = true;
		return this;
	}

	public RequirementInject alwaysForceNoData() {
		this.alwaysForce = true;
		this.alwaysForceNoData = true;
		return this;
	}

	public void debug(String str) {
		AdvancedCorePlugin.getInstance().debug(str);
	}

	public void extraDebug(String str) {
		AdvancedCorePlugin.getInstance().extraDebug(str);
	}

	public boolean isEditable() {
		return !getEditButtons().isEmpty();
	}

	public abstract boolean onRequirementRequest(Reward reward, AdvancedCoreUser user, ConfigurationSection data,
			RewardOptions rewardOptions);

	public RequirementInject priority(int priority) {
		setPriority(priority);
		return this;
	}

	public void validate(Reward reward, ConfigurationSection data) {
		if (validate != null && data.contains(getPath())) {
			validate.onValidate(reward, this, data);
		}
	}

	public RequirementInject validator(RequirementInjectValidator validate) {
		this.validate = validate;
		return this;
	}
}
