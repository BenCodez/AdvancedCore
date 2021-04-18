package com.bencodez.advancedcore.api.rewards.editbuttons;

import org.bukkit.entity.Player;

import com.bencodez.advancedcore.api.inventory.editgui.EditGUI;
import com.bencodez.advancedcore.api.rewards.RewardEditData;

public abstract class RewardEditEffect extends RewardEdit {
	public RewardEditEffect() {
	}

	public void open(Player player, RewardEditData reward) {
		EditGUI inv = new EditGUI("Edit Effect: " + reward.getName());
		inv.addData("Reward", reward);

		inv.addButton(getBooleanButton("Effect.Enabled", reward));
		inv.addButton(getStringButton("Effect.Effect", reward));
		inv.addButton(getIntButton("Effect.Data", reward));
		inv.addButton(getIntButton("Effect.Particles", reward));
		inv.addButton(getIntButton("Effect.Radius", reward));
		
		inv.addButton(getBackButton(reward));

		inv.openInventory(player);
	}
}
