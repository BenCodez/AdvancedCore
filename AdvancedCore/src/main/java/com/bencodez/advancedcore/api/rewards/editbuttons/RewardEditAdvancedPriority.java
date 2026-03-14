package com.bencodez.advancedcore.api.rewards.editbuttons;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import com.bencodez.advancedcore.api.inventory.BInventory.ClickEvent;
import com.bencodez.advancedcore.api.inventory.BInventoryButton;
import com.bencodez.advancedcore.api.inventory.editgui.EditGUI;
import com.bencodez.advancedcore.api.inventory.editgui.EditGUIButton;
import com.bencodez.advancedcore.api.inventory.editgui.valuetypes.EditGUIValueInventory;
import com.bencodez.advancedcore.api.item.ItemBuilder;
import com.bencodez.advancedcore.api.rewards.RewardEditData;
import com.bencodez.simpleapi.valuerequest.InputMethod;

public abstract class RewardEditAdvancedPriority extends RewardEdit {
	public RewardEditAdvancedPriority() {
	}

	@Override
	public void open(Player player, RewardEditData reward) {
		EditGUI inv = new EditGUI("Edit AdvancedPriority: " + reward.getName());
		inv.addData("Reward", reward);

		inv.addButton(new EditGUIButton(new EditGUIValueInventory("AdvancedPriority") {

			@Override
			public void openInventory(ClickEvent clickEvent) {
				requestString(clickEvent.getPlayer(), "", "Enter sub reward name", InputMethod.DIALOG, (p, value) -> {
					RewardEditData reward = (RewardEditData) getInv().getData("Reward");
					reward.createSection("AdvancedPriority." + value);
					reloadAdvancedCore();
					open(p, reward);
				});
			}
		}).setName("&aAdd sub reward").addLore("Rewards execute in order of addition"));

		inv.addButton(new EditGUIButton(new EditGUIValueInventory("AdvancedPriority") {

			@Override
			public void openInventory(ClickEvent clickEvent) {
				RewardEditData reward = (RewardEditData) getInv().getData("Reward");
				if (reward.hasPath("AdvancedPriority")) {
					openRemove(player, reward);
				}
			}
		}).setName("&aRemove sub reward"));

		inv.addButton(new EditGUIButton(new EditGUIValueInventory("AdvancedPriority") {

			@Override
			public void openInventory(ClickEvent clickEvent) {
				RewardEditData reward = (RewardEditData) getInv().getData("Reward");
				if (reward.hasPath("AdvancedPriority")) {
					openRename(player, reward);
				}
			}
		}).setName("&aRename sub reward"));

		inv.addButton(new EditGUIButton(new EditGUIValueInventory("AdvancedPriority") {

			@Override
			public void openInventory(ClickEvent clickEvent) {
				RewardEditData reward = (RewardEditData) getInv().getData("Reward");
				if (reward.hasPath("AdvancedPriority")) {
					openEditSub(player, reward);
				}
			}
		}).setName("&aEdit sub reward"));

		inv.addButton(getBackButton(reward));

		inv.openInventory(player);
	}

	public void openEditSub(Player player, RewardEditData reward) {
		EditGUI inv = new EditGUI("Edit AdvancedPriority Edit Sub: " + reward.getName());
		inv.addData("Reward", reward);

		for (String key : reward.getData().getConfigurationSection("AdvancedPriority").getKeys(false)) {
			inv.addButton(new BInventoryButton(
					new ItemBuilder(Material.PAPER).setName("&a" + key).addLoreLine("&aClick to edit")) {

				@Override
				public void onClick(ClickEvent clickEvent) {
					RewardEditData reward = (RewardEditData) getInv().getData("Reward");
					openSubReward(clickEvent.getPlayer(), "AdvancedPriority." + key, reward);
				}
			});
		}

		inv.addButton(getBackButtonCustom(reward, new EditGUIValueInventory("") {

			@Override
			public void openInventory(ClickEvent clickEvent) {
				RewardEditData reward = (RewardEditData) getInv().getData("Reward");
				open(player, reward);
			}
		}));

		inv.openInventory(player);
	}

	public void openRemove(Player player, RewardEditData reward) {
		EditGUI inv = new EditGUI("Edit AdvancedPriority Remove: " + reward.getName());
		inv.addData("Reward", reward);

		for (String key : reward.getData().getConfigurationSection("AdvancedPriority").getKeys(false)) {
			inv.addButton(new BInventoryButton(
					new ItemBuilder(Material.PAPER).setName("&c" + key).addLoreLine("&cClick to remove")) {

				@Override
				public void onClick(ClickEvent clickEvent) {
					RewardEditData reward = (RewardEditData) getInv().getData("Reward");
					reward.setValue("AdvancedPriority." + key, null);
					reloadAdvancedCore();
					open(player, reward);
				}
			});
		}

		inv.addButton(getBackButtonCustom(reward, new EditGUIValueInventory("") {

			@Override
			public void openInventory(ClickEvent clickEvent) {
				RewardEditData reward = (RewardEditData) getInv().getData("Reward");
				open(player, reward);
			}
		}));

		inv.openInventory(player);
	}

	public void openRename(Player player, RewardEditData reward) {
		EditGUI inv = new EditGUI("Edit AdvancedPriority Rename: " + reward.getName());
		inv.addData("Reward", reward);

		for (String key : reward.getData().getConfigurationSection("AdvancedPriority").getKeys(false)) {
			inv.addButton(new BInventoryButton(
					new ItemBuilder(Material.PAPER).setName("&a" + key).addLoreLine("&aClick to rename")) {

				@Override
				public void onClick(ClickEvent clickEvent) {
					requestString(clickEvent.getPlayer(), key, "Rename sub reward", InputMethod.DIALOG, (p, value) -> {
						RewardEditData reward = (RewardEditData) getInv().getData("Reward");
						reward.setValue("AdvancedPriority." + value,
								reward.getData().getConfigurationSection("AdvancedPriority." + key));
						reward.setValue("AdvancedPriority." + key, null);
						reloadAdvancedCore();
						open(p, reward);
					});
				}
			});
		}

		inv.addButton(getBackButtonCustom(reward, new EditGUIValueInventory("") {

			@Override
			public void openInventory(ClickEvent clickEvent) {
				RewardEditData reward = (RewardEditData) getInv().getData("Reward");
				open(player, reward);
			}
		}));

		inv.openInventory(player);
	}
}