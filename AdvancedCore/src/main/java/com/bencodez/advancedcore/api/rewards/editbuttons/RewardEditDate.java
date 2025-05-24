package com.bencodez.advancedcore.api.rewards.editbuttons;

import org.bukkit.entity.Player;

import com.bencodez.advancedcore.api.inventory.editgui.EditGUI;
import com.bencodez.advancedcore.api.rewards.RewardEditData;

public abstract class RewardEditDate extends RewardEdit {
	public RewardEditDate() {
	}

	@Override
	public void open(Player player, RewardEditData reward) {
		EditGUI inv = new EditGUI("Edit Dzte: " + reward.getName());
		inv.addData("Reward", reward);

		inv.addButton(getStringButton("Date.WeekDay", reward).addOptions("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY",
				"FRIDAY", "SATURDAY", "SUNDAY"));
		inv.addButton(getIntButton("Date.DayOfMonth", reward));
		inv.addButton(getStringButton("Date.Month", reward).addOptions("JANUARY", "FEBRUARY", "MARCH", "APRIL", "MAY",
				"JUNE", "JULY", "AUGUST", "SEPTEMBER", "OCTOBER", "NOVEMBER", "DECEMBER"));

		inv.addButton(getBackButton(reward));

		inv.openInventory(player);
	}
}
