package com.bencodez.advancedcore.api.rewards.editbuttons;

import org.bukkit.entity.Player;

import com.bencodez.advancedcore.api.inventory.editgui.EditGUI;
import com.bencodez.advancedcore.api.rewards.RewardEditData;

public abstract class RewardEditEXPLevels extends RewardEdit{
	public RewardEditEXPLevels() {
	}

	public void open(Player player, RewardEditData reward) {
		EditGUI inv = new EditGUI("Edit EXP: " + reward.getName());
		inv.addData("Reward", reward);

		inv.addButton(getIntButton("EXPLevels",reward));

		inv.addButton(getIntButton("EXPLevels.Min",reward));
		inv.addButton(getIntButton("EXPLevels.Max",reward));
		
		inv.addButton(getBackButton(reward));

		inv.openInventory(player);
	}
}
