package com.Ben12345rocks.AdvancedCore.Rewards.Injected;

import java.util.ArrayList;
import java.util.HashMap;

import org.bukkit.configuration.ConfigurationSection;

import com.Ben12345rocks.AdvancedCore.Rewards.Reward;
import com.Ben12345rocks.AdvancedCore.UserManager.User;
import com.Ben12345rocks.AdvancedCore.Util.EditGUI.EditGUIButton;

import lombok.Getter;
import lombok.Setter;

public abstract class RewardInject {
	@Getter
	@Setter
	private String path;

	@Getter
	@Setter
	private boolean internalReward = false;

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
	@Setter
	private ArrayList<EditGUIButton> editButtons = new ArrayList<EditGUIButton>();

	@Getter
	private int priority = 50;

	public RewardInject(String path) {
		this.path = path;
	}

	public RewardInject addEditButton(EditGUIButton button) {
		editButtons.add(button);
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

	public boolean isEditable() {
		return !editButtons.isEmpty();
	}

	public abstract Object onRewardRequest(Reward reward, User user, ConfigurationSection data,
			HashMap<String, String> placeholders);

	public RewardInject priority(int priority) {
		this.priority = priority;
		return this;
	}

	public RewardInject synchronize() {
		synchronize = true;
		object = new Object();
		return this;
	}
}
