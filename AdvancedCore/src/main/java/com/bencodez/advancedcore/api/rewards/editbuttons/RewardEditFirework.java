package com.bencodez.advancedcore.api.rewards.editbuttons;

import org.bukkit.entity.Player;

import com.bencodez.advancedcore.api.inventory.editgui.EditGUI;
import com.bencodez.advancedcore.api.rewards.RewardEditData;

public abstract class RewardEditFirework extends RewardEdit {
	public RewardEditFirework() {
	}

	@Override
	public void open(Player player, RewardEditData reward) {
		EditGUI inv = new EditGUI("Edit Firework: " + reward.getName());
		inv.addData("Reward", reward);

		inv.addButton(getBooleanButton("Firework.Enabled", reward));
		inv.addButton(getStringListButton("Firework.Colors", reward));
		inv.addButton(getStringListButton("Firework.FadeOutColor", reward));
		inv.addButton(getBooleanButton("Firework.Trail", reward));
		inv.addButton(getBooleanButton("Firework.Flicker", reward));
		inv.addButton(getStringListButton("Firework.Types", reward));

		inv.addButton(getBackButton(reward));

		inv.openInventory(player);
	}
}
