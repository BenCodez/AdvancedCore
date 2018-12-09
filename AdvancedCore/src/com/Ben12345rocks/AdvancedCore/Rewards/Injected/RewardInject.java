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

	public boolean isEditable() {
		return !editButtons.isEmpty();
	}

	@Getter
	@Setter
	private ArrayList<EditGUIButton> editButtons = new ArrayList<EditGUIButton>();

	public RewardInject(String path) {
		this.path = path;
	}

	public abstract void onRewardRequest(Reward reward, User user, ConfigurationSection data, HashMap<String, String> placeholders);

	public RewardInject addEditButton(EditGUIButton button) {
		editButtons.add(button);
		return this;
	}
}
