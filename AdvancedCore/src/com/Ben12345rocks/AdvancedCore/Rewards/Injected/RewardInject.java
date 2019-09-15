package com.Ben12345rocks.AdvancedCore.Rewards.Injected;

import java.util.HashMap;

import org.bukkit.configuration.ConfigurationSection;

import com.Ben12345rocks.AdvancedCore.AdvancedCorePlugin;
import com.Ben12345rocks.AdvancedCore.Rewards.Inject;
import com.Ben12345rocks.AdvancedCore.Rewards.Reward;
import com.Ben12345rocks.AdvancedCore.UserManager.User;
import com.Ben12345rocks.AdvancedCore.Util.EditGUI.EditGUIButton;

import lombok.Getter;
import lombok.Setter;

public abstract class RewardInject extends Inject {

	@Getter
	@Setter
	private boolean synchronize = false;

	@Getter
	private Object object;

	@Getter
	private boolean addAsPlaceholder = false;

	@Getter
	private String placeholderName;

	@Getter
	private boolean alwaysForce = false;

	@Getter
	private RewardInjectValidator validate;

	@Getter
	private boolean postReward = false;

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

	public abstract Object onRewardRequest(Reward reward, User user, ConfigurationSection data,
			HashMap<String, String> placeholders);

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

	public RewardInject validator(RewardInjectValidator validate) {
		this.validate = validate;
		return this;
	}
}
