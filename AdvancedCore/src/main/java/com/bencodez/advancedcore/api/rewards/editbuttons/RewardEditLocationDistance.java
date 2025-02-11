package com.bencodez.advancedcore.api.rewards.editbuttons;

import org.bukkit.entity.Player;

import com.bencodez.advancedcore.api.inventory.editgui.EditGUI;
import com.bencodez.advancedcore.api.rewards.RewardEditData;

public abstract class RewardEditLocationDistance extends RewardEdit {
	public RewardEditLocationDistance() {
	}

	@Override
	public void open(Player player, RewardEditData reward) {
		EditGUI inv = new EditGUI("Edit LocationDistance: " + reward.getName());
		inv.addData("Reward", reward);

		inv.addButton(getStringButton("LocationDistance.World", reward));
		inv.addButton(getIntButton("LocationDistance.X", reward));
		inv.addButton(getIntButton("LocationDistance.Y", reward));
		inv.addButton(getIntButton("LocationDistance.Z", reward));
		inv.addButton(getIntButton("LocationDistance.Distance", reward));

		inv.addButton(getBackButton(reward));

		inv.openInventory(player);
	}
}
