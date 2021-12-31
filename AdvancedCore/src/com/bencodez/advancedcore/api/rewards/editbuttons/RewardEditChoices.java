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

public abstract class RewardEditChoices extends RewardEdit {
	public RewardEditChoices() {
	}

	@Override
	public void open(Player player, RewardEditData reward) {
		EditGUI inv = new EditGUI("Edit Choices: " + reward.getName());
		inv.addData("Reward", reward);

		inv.addButton(getBooleanButton("EnableChoices", reward).addLore("Enable choice rewards"));

		inv.addButton(new EditGUIButton(new EditGUIValueInventory("Choices") {

			@Override
			public void openInventory(ClickEvent clickEvent) {
				new ValueRequestBuilder(new StringListener() {

					@Override
					public void onInput(Player player, String value) {
						RewardEditData reward = (RewardEditData) getInv().getData("Reward");
						reward.createSection("Choices." + value);
						reloadAdvancedCore();
						open(player, reward);
					}
				}, new String[] {}).usingMethod(InputMethod.CHAT).request(clickEvent.getPlayer());

			}
		}).setName("&aAdd sub reward"));

		inv.addButton(new EditGUIButton(new EditGUIValueInventory("Choices") {

			@Override
			public void openInventory(ClickEvent clickEvent) {
				RewardEditData reward = (RewardEditData) getInv().getData("Reward");
				if (reward.hasPath("Choices")) {
					openRemove(player, reward);
				}
			}
		}).setName("&aRemove sub reward"));

		inv.addButton(new EditGUIButton(new EditGUIValueInventory("Choices") {

			@Override
			public void openInventory(ClickEvent clickEvent) {
				RewardEditData reward = (RewardEditData) getInv().getData("Reward");
				if (reward.hasPath("Choices")) {
					openRename(player, reward);
				}
			}
		}).setName("&aRename sub reward"));

		inv.addButton(new EditGUIButton(new EditGUIValueInventory("Choices") {

			@Override
			public void openInventory(ClickEvent clickEvent) {
				RewardEditData reward = (RewardEditData) getInv().getData("Reward");
				if (reward.hasPath("Choices")) {
					openEditSub(player, reward);
				}
			}
		}).setName("&aEdit sub reward"));

		inv.addButton(getBackButton(reward));

		inv.openInventory(player);
	}

	public void openEditSub(Player player, RewardEditData reward) {
		EditGUI inv = new EditGUI("Edit Choices Edit Sub: " + reward.getName());
		inv.addData("Reward", reward);

		for (String key : reward.getData().getConfigurationSection("Choices").getKeys(false)) {
			inv.addButton(new BInventoryButton(
					new ItemBuilder(Material.PAPER).setName("&a" + key).addLoreLine("&aClick to edit")) {

				@Override
				public void onClick(ClickEvent clickEvent) {
					RewardEditData reward = (RewardEditData) getInv().getData("Reward");
					openSubReward(clickEvent.getPlayer(), "Choices." + key, reward);
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
		EditGUI inv = new EditGUI("Edit Choices Remove: " + reward.getName());
		inv.addData("Reward", reward);

		for (String key : reward.getData().getConfigurationSection("Choices").getKeys(false)) {
			inv.addButton(new BInventoryButton(
					new ItemBuilder(Material.PAPER).setName("&c" + key).addLoreLine("&cClick to remove")) {

				@Override
				public void onClick(ClickEvent clickEvent) {
					RewardEditData reward = (RewardEditData) getInv().getData("Reward");
					reward.setValue("Choices." + key, null);
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
		EditGUI inv = new EditGUI("Edit Choices Rename: " + reward.getName());
		inv.addData("Reward", reward);

		for (String key : reward.getData().getConfigurationSection("Choices").getKeys(false)) {
			inv.addButton(new BInventoryButton(
					new ItemBuilder(Material.PAPER).setName("&a" + key).addLoreLine("&aClick to rename")) {

				@Override
				public void onClick(ClickEvent clickEvent) {
					new ValueRequestBuilder(new StringListener() {

						@Override
						public void onInput(Player player, String value) {
							RewardEditData reward = (RewardEditData) getInv().getData("Reward");
							reward.setValue("Choices." + value,
									reward.getData().getConfigurationSection("Choices." + key));
							reward.setValue("Choices." + key, null);
							reloadAdvancedCore();
							open(player, reward);
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

}
