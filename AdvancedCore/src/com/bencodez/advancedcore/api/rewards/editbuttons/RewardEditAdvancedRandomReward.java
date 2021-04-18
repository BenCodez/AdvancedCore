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
import com.bencodez.advancedcore.api.valuerequest.InputMethod;
import com.bencodez.advancedcore.api.valuerequest.ValueRequestBuilder;
import com.bencodez.advancedcore.api.valuerequest.listeners.StringListener;

public abstract class RewardEditAdvancedRandomReward extends RewardEdit {
	public RewardEditAdvancedRandomReward() {
	}

	public void open(Player player, RewardEditData reward) {
		EditGUI inv = new EditGUI("Edit AdvancedRandomReward: " + reward.getName());
		inv.addData("Reward", reward);

		inv.addButton(new EditGUIButton(new EditGUIValueInventory("AdvancedRandomReward") {

			@Override
			public void openInventory(ClickEvent clickEvent) {
				new ValueRequestBuilder(new StringListener() {

					@Override
					public void onInput(Player player, String value) {
						RewardEditData reward = (RewardEditData) getInv().getData("Reward");
						reward.createSection("AdvancedRandomReward." + value);
						reloadAdvancedCore();
					}
				}, new String[] {}).usingMethod(InputMethod.CHAT).request(clickEvent.getPlayer());
				;
			}
		}).setName("&aAdd sub reward"));

		inv.addButton(new EditGUIButton(new EditGUIValueInventory("AdvancedRandomReward") {

			@Override
			public void openInventory(ClickEvent clickEvent) {
				RewardEditData reward = (RewardEditData) getInv().getData("Reward");
				if (reward.hasPath("AdvancedRandomReward")) {
					openRemove(player, reward);
				}
			}
		}).setName("&aRemove sub reward"));

		inv.addButton(new EditGUIButton(new EditGUIValueInventory("AdvancedRandomReward") {

			@Override
			public void openInventory(ClickEvent clickEvent) {
				RewardEditData reward = (RewardEditData) getInv().getData("Reward");
				if (reward.hasPath("AdvancedRandomReward")) {
					openRename(player, reward);
				}
			}
		}).setName("&aRename sub reward"));

		inv.addButton(new EditGUIButton(new EditGUIValueInventory("AdvancedRandomReward") {

			@Override
			public void openInventory(ClickEvent clickEvent) {
				RewardEditData reward = (RewardEditData) getInv().getData("Reward");
				if (reward.hasPath("AdvancedRandomReward")) {
					openEditSub(player, reward);
				}
			}
		}).setName("&aEdit sub reward"));
		
		inv.addButton(getBackButton(reward));

		inv.openInventory(player);
	}

	public void openRemove(Player player, RewardEditData reward) {
		EditGUI inv = new EditGUI("Edit AdvancedRandomReward Remove: " + reward.getName());
		inv.addData("Reward", reward);

		for (String key : reward.getData().getConfigurationSection("AdvancedRandomReward").getKeys(false)) {
			inv.addButton(new BInventoryButton(
					new ItemBuilder(Material.PAPER).setName("&c" + key).addLoreLine("&cClick to remove")) {

				@Override
				public void onClick(ClickEvent clickEvent) {
					RewardEditData reward = (RewardEditData) getInv().getData("Reward");
					reward.setValue("AdvancedRandomReward." + key, null);
					reloadAdvancedCore();
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
		EditGUI inv = new EditGUI("Edit AdvancedRandomReward Rename: " + reward.getName());
		inv.addData("Reward", reward);

		for (String key : reward.getData().getConfigurationSection("AdvancedRandomReward").getKeys(false)) {
			inv.addButton(new BInventoryButton(
					new ItemBuilder(Material.PAPER).setName("&a" + key).addLoreLine("&aClick to rename")) {

				@Override
				public void onClick(ClickEvent clickEvent) {
					new ValueRequestBuilder(new StringListener() {

						@Override
						public void onInput(Player player, String value) {
							RewardEditData reward = (RewardEditData) getInv().getData("Reward");
							reward.setValue("AdvancedRandomReward." + value,
									reward.getData().getConfigurationSection("AdvancedRandomReward." + key));
							reward.setValue("AdvancedRandomReward." + key, null);
							reloadAdvancedCore();
						}
					}, new String[] {}).usingMethod(InputMethod.CHAT).request(clickEvent.getPlayer());
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

	public void openEditSub(Player player, RewardEditData reward) {
		EditGUI inv = new EditGUI("Edit AdvancedRandomReward Edit Sub: " + reward.getName());
		inv.addData("Reward", reward);

		for (String key : reward.getData().getConfigurationSection("AdvancedRandomReward").getKeys(false)) {
			inv.addButton(new BInventoryButton(
					new ItemBuilder(Material.PAPER).setName("&a" + key).addLoreLine("&aClick to edit")) {

				@Override
				public void onClick(ClickEvent clickEvent) {
					RewardEditData reward = (RewardEditData) getInv().getData("Reward");
					openSubReward(clickEvent.getPlayer(), "AdvancedRandomReward." + key, reward);
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
