package com.bencodez.advancedcore.api.rewards.editbuttons;

import org.bukkit.entity.Player;

import com.bencodez.advancedcore.api.inventory.editgui.EditGUI;
import com.bencodez.advancedcore.api.rewards.RewardEditData;

public abstract class RewardEditBossBar extends RewardEdit {
	public RewardEditBossBar() {
	}

	public void open(Player player, RewardEditData reward) {
		EditGUI inv = new EditGUI("Edit BossBar: " + reward.getName());
		inv.addData("Reward", reward);

		inv.addButton(getBooleanButton("BossBar.Enabled", reward));
		inv.addButton(getStringButton("BossBar.Message", reward));
		inv.addButton(getStringButton("BossBar.Color", reward));
		inv.addButton(getStringButton("BossBar.Style", reward));
		inv.addButton(getDoubleButton("BossBar.Progress", reward));
		inv.addButton(getIntButton("BossBar.Delay", reward));

		inv.openInventory(player);
	}
}
