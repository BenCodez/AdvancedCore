package com.bencodez.advancedcore.api.rewards.editbuttons;

import org.bukkit.entity.Player;

import com.bencodez.advancedcore.api.inventory.editgui.EditGUI;
import com.bencodez.advancedcore.api.rewards.RewardEditData;

public abstract class RewardEditDelayed extends RewardEdit {
	public RewardEditDelayed() {
	}

	public void open(Player player, RewardEditData reward) {
		EditGUI inv = new EditGUI("Edit Delayed: " + reward.getName());
		inv.addData("Reward", reward);

		inv.addButton(getBooleanButton("Delayed.Enabled", reward));
		inv.addButton(getIntButton("Delayed.Hours", reward));
		inv.addButton(getIntButton("Delayed.Minutes", reward));
		inv.addButton(getIntButton("Delayed.Seconds", reward));
		
		inv.addButton(getBackButton(reward));

		inv.openInventory(player);
	}
}
