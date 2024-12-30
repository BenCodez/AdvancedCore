package com.bencodez.advancedcore.api.rewards.editbuttons;

import java.util.ArrayList;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

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
import com.bencodez.simpleapi.array.ArrayUtils;

public abstract class RewardEditPotions extends RewardEdit {
	public RewardEditPotions() {
	}

	@SuppressWarnings("deprecation")
	@Override
	public void open(Player player, RewardEditData reward) {
		EditGUI inv = new EditGUI("Edit Potions: " + reward.getName());
		inv.addData("Reward", reward);
		ArrayList<String> potionEffects = new ArrayList<>();

		for (PotionEffectType effect : PotionEffectType.values()) {
			potionEffects.add(effect.toString());
		}

		inv.addButton(new EditGUIButton(new EditGUIValueInventory("Potions") {

			@Override
			public void openInventory(ClickEvent clickEvent) {
				new ValueRequestBuilder(new StringListener() {

					@Override
					public void onInput(Player player, String value) {
						RewardEditData reward = (RewardEditData) getInv().getData("Reward");
						reward.createSection("Potions." + value);
						reloadAdvancedCore();
						open(player, reward);
					}
				}, ArrayUtils.convert(potionEffects)).usingMethod(InputMethod.INVENTORY)
						.request(clickEvent.getPlayer());

			}
		}).setName("&aAdd potion effect"));

		inv.addButton(new EditGUIButton(new EditGUIValueInventory("Potions") {

			@Override
			public void openInventory(ClickEvent clickEvent) {
				RewardEditData reward = (RewardEditData) getInv().getData("Reward");
				if (reward.hasPath("Potions")) {
					openRemove(player, reward);
				}
			}
		}).setName("&aRemove potion"));

		inv.addButton(new EditGUIButton(new EditGUIValueInventory("Potions") {

			@Override
			public void openInventory(ClickEvent clickEvent) {
				RewardEditData reward = (RewardEditData) getInv().getData("Reward");
				if (reward.hasPath("Potions")) {
					openEditSub(player, reward);
				}
			}
		}).setName("&aEdit potion effect"));

		inv.addButton(getBackButton(reward));

		inv.openInventory(player);
	}

	public void openEditSub(Player player, RewardEditData reward) {
		EditGUI inv = new EditGUI("Edit Potions Edit Sub: " + reward.getName());
		inv.addData("Reward", reward);

		for (String key : reward.getData().getConfigurationSection("Potions").getKeys(false)) {
			inv.addButton(new BInventoryButton(
					new ItemBuilder(Material.PAPER).setName("&a" + key).addLoreLine("&aClick to edit")) {

				@Override
				public void onClick(ClickEvent clickEvent) {
					RewardEditData reward = (RewardEditData) getInv().getData("Reward");
					openEditSub(player, reward, key);
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

	public void openEditSub(Player player, RewardEditData reward, String potion) {
		EditGUI inv = new EditGUI("Edit Potions Edit " + potion + ": " + reward.getName());
		inv.addData("Reward", reward);

		inv.addButton(getIntButton("Potions." + potion + ".Duration", reward));
		inv.addButton(getIntButton("Potions." + potion + ".Amplifier", reward));

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
		EditGUI inv = new EditGUI("Edit Potions Remove: " + reward.getName());
		inv.addData("Reward", reward);

		for (String key : reward.getData().getConfigurationSection("Potions").getKeys(false)) {
			inv.addButton(new BInventoryButton(
					new ItemBuilder(Material.PAPER).setName("&c" + key).addLoreLine("&cClick to remove")) {

				@Override
				public void onClick(ClickEvent clickEvent) {
					RewardEditData reward = (RewardEditData) getInv().getData("Reward");
					reward.setValue("Potions." + key, null);
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

}
