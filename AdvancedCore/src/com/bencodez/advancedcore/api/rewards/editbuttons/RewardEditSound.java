package com.bencodez.advancedcore.api.rewards.editbuttons;

import org.bukkit.entity.Player;

import com.bencodez.advancedcore.api.inventory.editgui.EditGUI;
import com.bencodez.advancedcore.api.rewards.RewardEditData;

public abstract class RewardEditSound extends RewardEdit {
	public RewardEditSound() {
	}

	public void open(Player player, RewardEditData reward) {
		EditGUI inv = new EditGUI("Edit Sound: " + reward.getName());
		inv.addData("Reward", reward);

		inv.addButton(getBooleanButton("Sound.Enabled", reward));
		inv.addButton(getStringButton("Sound.Sound", reward));
		inv.addButton(getDoubleButton("Sound.Volume", reward));
		inv.addButton(getDoubleButton("Sound.Pitch", reward));

		inv.openInventory(player);
	}
}
