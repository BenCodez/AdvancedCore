package com.bencodez.advancedcore.api.rewards.editbuttons;

import org.bukkit.entity.Player;

import com.bencodez.advancedcore.api.inventory.editgui.EditGUI;
import com.bencodez.advancedcore.api.rewards.RewardEditData;

public abstract class RewardEditMoney extends RewardEdit {
	public RewardEditMoney() {
	}

	public void open(Player player, RewardEditData reward) {
		EditGUI inv = new EditGUI("Edit Money: " + reward.getName());
		inv.addData("Reward", reward);

		inv.addButton(getIntButton("Money", reward));

		inv.addButton(getIntButton("Money.Min", reward));
		inv.addButton(getIntButton("Money.Max", reward));

		inv.openInventory(player);
	}
}
