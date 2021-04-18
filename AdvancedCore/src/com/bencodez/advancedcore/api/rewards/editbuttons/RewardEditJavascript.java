package com.bencodez.advancedcore.api.rewards.editbuttons;

import org.bukkit.entity.Player;

import com.bencodez.advancedcore.api.inventory.BInventory.ClickEvent;
import com.bencodez.advancedcore.api.inventory.editgui.EditGUI;
import com.bencodez.advancedcore.api.inventory.editgui.EditGUIButton;
import com.bencodez.advancedcore.api.inventory.editgui.valuetypes.EditGUIValueInventory;
import com.bencodez.advancedcore.api.rewards.RewardEditData;

public abstract class RewardEditJavascript extends RewardEdit {
	public RewardEditJavascript() {
	}

	public void open(Player player, RewardEditData reward) {
		EditGUI inv = new EditGUI("Edit Javascript: " + reward.getName());
		inv.addData("Reward", reward);

		inv.addButton(getBooleanButton("Javascript.Enabled", reward));
		inv.addButton(getStringButton("Javascript.Expression", reward));

		inv.addButton(new EditGUIButton(new EditGUIValueInventory("Javascript.TrueRewards") {

			@Override
			public void openInventory(ClickEvent clickEvent) {
				RewardEditData reward = (RewardEditData) getInv().getData("Reward");
				if (reward.hasPath("AdvancedRandomReward")) {
					openSubReward(player, "Javascript.TrueRewards", reward);
				}
			}
		}).setName("&aEdit true rewards"));

		inv.addButton(new EditGUIButton(new EditGUIValueInventory("Javascript.FalseRewards") {

			@Override
			public void openInventory(ClickEvent clickEvent) {
				RewardEditData reward = (RewardEditData) getInv().getData("Reward");
				if (reward.hasPath("AdvancedRandomReward")) {
					openSubReward(player, "Javascript.FalseRewards", reward);
				}
			}
		}).setName("&aEdit false rewards"));
		
		inv.addButton(getBackButton(reward));

		inv.openInventory(player);
	}

}
