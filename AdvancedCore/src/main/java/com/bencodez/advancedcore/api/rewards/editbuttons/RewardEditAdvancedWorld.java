package com.bencodez.advancedcore.api.rewards.editbuttons;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import com.bencodez.advancedcore.api.inventory.BInventory.ClickEvent;
import com.bencodez.advancedcore.api.inventory.BInventoryButton;
import com.bencodez.advancedcore.api.inventory.editgui.EditGUI;
import com.bencodez.advancedcore.api.inventory.editgui.EditGUIButton;
import com.bencodez.advancedcore.api.inventory.editgui.valuetypes.EditGUIValueInventory;
import com.bencodez.advancedcore.api.item.ItemBuilder;
import com.bencodez.advancedcore.api.rewards.RewardEditData;
import com.bencodez.simpleapi.valuerequest.InputMethod;

public abstract class RewardEditAdvancedWorld extends RewardEdit {
	public RewardEditAdvancedWorld() {
	}

	@Override
	public void open(Player player, RewardEditData reward) {
		EditGUI inv = new EditGUI("Edit AdvancedWorld: " + reward.getName());
		inv.addData("Reward", reward);

		inv.addButton(new EditGUIButton(new EditGUIValueInventory("AdvancedWorld") {

			@Override
			public void openInventory(ClickEvent clickEvent) {
				List<String> worlds = new ArrayList<String>();
				for (World w : Bukkit.getWorlds()) {
					worlds.add(w.getName());
				}
				requestString(clickEvent.getPlayer(), "", worlds, true, "Enter world name", InputMethod.DIALOG,
						(p, value) -> {
							RewardEditData reward = (RewardEditData) getInv().getData("Reward");
							reward.createSection("AdvancedWorld." + value);
							reloadAdvancedCore();
							open(p, reward);
						});
			}
		}).setName("&aAdd sub reward").addLore("Rewards execute in order of addition"));

		inv.addButton(new EditGUIButton(new EditGUIValueInventory("AdvancedWorld") {

			@Override
			public void openInventory(ClickEvent clickEvent) {
				RewardEditData reward = (RewardEditData) getInv().getData("Reward");
				if (reward.hasPath("AdvancedWorld")) {
					openRemove(player, reward);
				}
			}
		}).setName("&aRemove sub reward"));

		inv.addButton(new EditGUIButton(new EditGUIValueInventory("AdvancedWorld") {

			@Override
			public void openInventory(ClickEvent clickEvent) {
				RewardEditData reward = (RewardEditData) getInv().getData("Reward");
				if (reward.hasPath("AdvancedWorld")) {
					openRename(player, reward);
				}
			}
		}).setName("&aRename sub reward"));

		inv.addButton(new EditGUIButton(new EditGUIValueInventory("AdvancedWorld") {

			@Override
			public void openInventory(ClickEvent clickEvent) {
				RewardEditData reward = (RewardEditData) getInv().getData("Reward");
				if (reward.hasPath("AdvancedWorld")) {
					openEditSub(player, reward);
				}
			}
		}).setName("&aEdit sub reward"));

		inv.addButton(getBackButton(reward));

		inv.openInventory(player);
	}

	public void openEditSub(Player player, RewardEditData reward) {
		EditGUI inv = new EditGUI("Edit AdvancedWorld World Rewards: " + reward.getName());
		inv.addData("Reward", reward);

		for (String key : reward.getData().getConfigurationSection("AdvancedWorld").getKeys(false)) {
			inv.addButton(new BInventoryButton(
					new ItemBuilder(Material.PAPER).setName("&a" + key).addLoreLine("&aClick to edit")) {

				@Override
				public void onClick(ClickEvent clickEvent) {
					RewardEditData reward = (RewardEditData) getInv().getData("Reward");
					openSubReward(clickEvent.getPlayer(), "AdvancedWorld." + key, reward);
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
		EditGUI inv = new EditGUI("Edit AdvancedWorld Remove: " + reward.getName());
		inv.addData("Reward", reward);

		for (String key : reward.getData().getConfigurationSection("AdvancedWorld").getKeys(false)) {
			inv.addButton(new BInventoryButton(
					new ItemBuilder(Material.PAPER).setName("&c" + key).addLoreLine("&cClick to remove")) {

				@Override
				public void onClick(ClickEvent clickEvent) {
					RewardEditData reward = (RewardEditData) getInv().getData("Reward");
					reward.setValue("AdvancedWorld." + key, null);
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
		EditGUI inv = new EditGUI("Edit AdvancedWorld Rename: " + reward.getName());
		inv.addData("Reward", reward);

		for (String key : reward.getData().getConfigurationSection("AdvancedWorld").getKeys(false)) {
			inv.addButton(new BInventoryButton(
					new ItemBuilder(Material.PAPER).setName("&a" + key).addLoreLine("&aClick to rename")) {

				@Override
				public void onClick(ClickEvent clickEvent) {
					requestString(clickEvent.getPlayer(), key, "Rename sub reward", InputMethod.DIALOG, (p, value) -> {
						RewardEditData reward = (RewardEditData) getInv().getData("Reward");
						reward.setValue("AdvancedWorld." + value,
								reward.getData().getConfigurationSection("AdvancedWorld." + key));
						reward.setValue("AdvancedWorld." + key, null);
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