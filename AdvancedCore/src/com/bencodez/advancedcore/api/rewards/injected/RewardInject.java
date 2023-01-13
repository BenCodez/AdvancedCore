package com.bencodez.advancedcore.api.rewards.injected;

import java.util.ArrayList;
import java.util.HashMap;

import org.bukkit.configuration.ConfigurationSection;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.inventory.editgui.EditGUIButton;
import com.bencodez.advancedcore.api.rewards.DefinedReward;
import com.bencodez.advancedcore.api.rewards.Inject;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.rewards.SubDirectlyDefinedReward;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;

import lombok.Getter;
import lombok.Setter;

public abstract class RewardInject extends Inject {

	@Getter
	private boolean addAsPlaceholder = false;

	@Getter
	private boolean alwaysForce = false;

	@Getter
	private boolean alwaysForceNoData = false;

	@Getter
	private Object object;

	@Getter
	private String placeholderName;

	@Getter
	private boolean postReward = false;

	@Getter
	@Setter
	private boolean synchronize = false;

	@Getter
	private boolean alwaysValid = false;

	public RewardInject alwaysValid() {
		alwaysValid = true;
		return this;
	}

	@Getter
	private RewardInjectValidator validate;

	public RewardInject(String path) {
		super(path);
	}

	public RewardInject addEditButton(EditGUIButton button) {
		getEditButtons().add(button);
		return this;
	}

	public RewardInject alwaysForce() {
		this.alwaysForce = true;
		return this;
	}

	public RewardInject alwaysForceNoData() {
		this.alwaysForce = true;
		this.alwaysForceNoData = true;
		return this;
	}

	public RewardInject asPlaceholder(String placeholderName) {
		addAsPlaceholder = true;
		this.placeholderName = placeholderName;
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

	public abstract Object onRewardRequest(Reward reward, AdvancedCoreUser user, ConfigurationSection data,
			HashMap<String, String> placeholders);

	public ArrayList<SubDirectlyDefinedReward> subRewards(DefinedReward direct) {
		return new ArrayList<SubDirectlyDefinedReward>();
	}

	public RewardInject postReward() {
		postReward = true;
		return this;
	}

	public RewardInject priority(int priority) {
		setPriority(priority);
		return this;
	}

	public RewardInject synchronize() {
		synchronize = true;
		object = new Object();
		return this;
	}

	public void validate(Reward reward, ConfigurationSection data) {
		if (validate != null && data.contains(getPath())) {
			validate.onValidate(reward, this, data);
		}
	}

	public boolean hasValidator() {
		return getValidate() != null;
	}

	public RewardInject validator(RewardInjectValidator validate) {
		this.validate = validate;
		return this;
	}
}
