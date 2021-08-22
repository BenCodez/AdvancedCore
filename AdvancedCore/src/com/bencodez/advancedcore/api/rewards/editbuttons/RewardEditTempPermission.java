package com.bencodez.advancedcore.api.rewards.editbuttons;

import org.bukkit.entity.Player;

import com.bencodez.advancedcore.api.inventory.editgui.EditGUI;
import com.bencodez.advancedcore.api.rewards.RewardEditData;

public abstract class RewardEditTempPermission extends RewardEdit {
	public RewardEditTempPermission() {
	}

	@Override
	public void open(Player player, RewardEditData reward) {
		EditGUI inv = new EditGUI("Edit TempPermission: " + reward.getName());
		inv.addData("Reward", reward);

		inv.addButton(getStringButton("Permission", reward));
		inv.addButton(getIntButton("Expiration", reward));

		inv.addButton(getBackButton(reward));

		inv.openInventory(player);
	}
}
