package com.bencodez.advancedcore.api.rewards.editbuttons;

import org.bukkit.entity.Player;

import com.bencodez.advancedcore.api.inventory.editgui.EditGUI;
import com.bencodez.advancedcore.api.rewards.RewardEditData;

public abstract class RewardEditEXP extends RewardEdit {
	public RewardEditEXP() {
	}

	@Override
	public void open(Player player, RewardEditData reward) {
		EditGUI inv = new EditGUI("Edit EXP: " + reward.getName());
		inv.addData("Reward", reward);

		inv.addButton(getIntButton("EXP", reward));

		inv.addButton(getIntButton("EXP.Min", reward));
		inv.addButton(getIntButton("EXP.Max", reward));

		inv.addButton(getBackButton(reward));

		inv.openInventory(player);
	}
}
