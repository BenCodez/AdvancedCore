package com.bencodez.advancedcore.api.rewards.editbuttons;

import org.bukkit.entity.Player;

import com.bencodez.advancedcore.api.inventory.editgui.EditGUI;
import com.bencodez.advancedcore.api.rewards.RewardEditData;

public abstract class RewardEditMessages extends RewardEdit {
	public RewardEditMessages() {
	}

	@Override
	public void open(Player player, RewardEditData reward) {
		EditGUI inv = new EditGUI("Edit Messages: " + reward.getName());
		inv.addData("Reward", reward);

		// message
		inv.addButton(getStringButton("Message", reward).addLore("Single line player message"));
		inv.addButton(getStringListButton("Message", reward).addLore("List of player messages"));

		// messages.player
		inv.addButton(getStringButton("Messages.Player", reward).addLore("Single line player message"));
		inv.addButton(getStringListButton("Messages.Player", reward).addLore("List of player messages"));

		// messages.broadcast
		inv.addButton(getStringButton("Messages.Broadcast", reward).addLore("Single line broadcast"));
		inv.addButton(getStringListButton("Messages.Broadcast", reward).addLore("List of broadcast messages"));

		inv.addButton(getBackButton(reward));

		inv.openInventory(player);
	}
}
