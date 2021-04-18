package com.bencodez.advancedcore.api.rewards.editbuttons;

import org.bukkit.entity.Player;

import com.bencodez.advancedcore.api.inventory.editgui.EditGUI;
import com.bencodez.advancedcore.api.rewards.RewardEditData;

public abstract class RewardEditTimed extends RewardEdit {
	public RewardEditTimed() {
	}

	public void open(Player player, RewardEditData reward) {
		EditGUI inv = new EditGUI("Edit Timed: " + reward.getName());
		inv.addData("Reward", reward);

		inv.addButton(getBooleanButton("Timed.Enabled", reward));
		inv.addButton(getIntButton("Timed.Hour", reward));
		inv.addButton(getIntButton("Timed.Minute", reward));
		
		inv.addButton(getBackButton(reward));

		inv.openInventory(player);
	}
}
