package com.bencodez.advancedcore.api.rewards.editbuttons;

import org.bukkit.entity.Player;

import com.bencodez.advancedcore.api.inventory.editgui.EditGUI;
import com.bencodez.advancedcore.api.rewards.RewardEditData;

public abstract class RewardEditActionBar extends RewardEdit {
	public RewardEditActionBar() {
	}

	public void open(Player player, RewardEditData reward) {
		EditGUI inv = new EditGUI("Edit ActionBar: " + reward.getName());
		inv.addData("Reward", reward);

		inv.addButton(getStringButton("ActionBar.Message", reward));
		inv.addButton(getIntButton("ActionBar.Delay", reward));

		inv.openInventory(player);
	}
}
