package com.bencodez.advancedcore.api.rewards.editbuttons;

import org.bukkit.entity.Player;

import com.bencodez.advancedcore.api.inventory.editgui.EditGUI;
import com.bencodez.advancedcore.api.rewards.RewardEditData;

public abstract class RewardEditTitle extends RewardEdit {
	public RewardEditTitle() {
	}

	public void open(Player player, RewardEditData reward) {
		EditGUI inv = new EditGUI("Edit Title: " + reward.getName());
		inv.addData("Reward", reward);

		inv.addButton(getBooleanButton("Title.Enabled", reward));
		inv.addButton(getStringButton("Title.Title", reward));
		inv.addButton(getStringButton("Title.SubTitle", reward));
		inv.addButton(getIntButton("Title.FadeIn", reward));
		inv.addButton(getIntButton("Title.ShowTime", reward));
		inv.addButton(getIntButton("Title.FadeOut", reward));

		inv.openInventory(player);
	}
}
