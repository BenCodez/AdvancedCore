package com.bencodez.advancedcore.command.gui;

import java.util.ArrayList;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.inventory.BInventory;
import com.bencodez.advancedcore.api.inventory.BInventory.ClickEvent;
import com.bencodez.advancedcore.api.inventory.BInventoryButton;
import com.bencodez.advancedcore.api.inventory.editgui.EditGUI;
import com.bencodez.advancedcore.api.inventory.editgui.EditGUIButton;
import com.bencodez.advancedcore.api.inventory.editgui.valuetypes.EditGUIValueBoolean;
import com.bencodez.advancedcore.api.item.ItemBuilder;
import com.bencodez.advancedcore.api.misc.ArrayUtils;
import com.bencodez.advancedcore.api.misc.PlayerUtils;
import com.bencodez.advancedcore.api.rewards.DirectlyDefinedReward;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.rewards.RewardEditData;
import com.bencodez.advancedcore.api.rewards.RewardHandler;
import com.bencodez.advancedcore.api.rewards.injected.RewardInject;
import com.bencodez.advancedcore.api.rewards.injectedrequirement.RequirementInject;

/**
 * The Class RewardGUI.
 */
public class RewardEditGUI {

	/** The instance. */
	static RewardEditGUI instance = new RewardEditGUI();

	/**
	 * Gets the single instance of RewardGUI.
	 *
	 * @return single instance of RewardGUI
	 */
	public static RewardEditGUI getInstance() {
		return instance;
	}

	AdvancedCorePlugin plugin = AdvancedCorePlugin.getInstance();

	/**
	 * Instantiates a new reward GUI.
	 */
	private RewardEditGUI() {
	}

	/**
	 * Gets the current reward.
	 *
	 * @param player the player
	 * @return the current reward
	 */
	public Reward getCurrentReward(Player player) {
		return (Reward) PlayerUtils.getInstance().getPlayerMeta(player, "Reward");
	}

	public void openRewardGUIRequirements(Player player, RewardEditData rewardEditData, String rewardName) {
		EditGUI inv = new EditGUI("Requirements: " + rewardName);

		inv.addData("Reward", rewardEditData);

		inv.addButton(new EditGUIButton(new ItemBuilder(Material.PAPER),
				new EditGUIValueBoolean("ForceOffline", rewardEditData.getValue("ForceOffline")) {

					@Override
					public void setValue(Player player, boolean value) {
						RewardEditData reward = (RewardEditData) getInv().getData("Reward");
						reward.setValue(getKey(), value);
						plugin.reloadAdvancedCore(false);
					}
				}));

		for (RequirementInject injectReward : RewardHandler.getInstance().getInjectedRequirements()) {
			if (injectReward.isEditable()) {
				for (BInventoryButton b : injectReward.getEditButtons()) {
					if (b instanceof EditGUIButton) {
						EditGUIButton eb = (EditGUIButton) b;
						eb.getEditor().setCurrentValue(rewardEditData.getValue(eb.getEditor().getKey()));
						inv.addButton(eb);
					} else {
						inv.addButton(b);
					}
				}

			}
		}
		inv.sort();
		inv.addButton(new BInventoryButton(new ItemBuilder(Material.BARRIER).setName("&cGo back")) {

			@Override
			public void onClick(ClickEvent clickEvent) {
				openRewardGUI(clickEvent.getPlayer(), rewardEditData, rewardName);
			}
		});
		inv.openInventory(player);
	}

	public void openRewardGUIRewards(Player player, RewardEditData rewardEditData, String rewardName,
			boolean unsetValuesShown) {
		EditGUI inv = new EditGUI("Rewards: " + rewardName);

		inv.addData("Reward", rewardEditData);

		for (RewardInject injectReward : RewardHandler.getInstance().getInjectedRewards()) {
			if (injectReward.isEditable()) {
				for (BInventoryButton b : injectReward.getEditButtons()) {
					if (b instanceof EditGUIButton) {
						EditGUIButton eb = (EditGUIButton) b;
						if (rewardEditData.hasPath(eb.getEditor().getKey()) || unsetValuesShown) {
							eb.getEditor().setCurrentValue(rewardEditData.getValue(eb.getEditor().getKey()));
							inv.addButton(eb);
						}
					} else {
						inv.addButton(b);
					}
				}
			}
		}

		inv.sort();
		if (!unsetValuesShown) {
			inv.addButton(new BInventoryButton(new ItemBuilder(Material.CHEST).setName("&cUnset Values")) {

				@Override
				public void onClick(ClickEvent clickEvent) {
					openRewardGUIRewards(player, rewardEditData, rewardName, true);
				}
			});
		} else {
			inv.addButton(new BInventoryButton(
					new ItemBuilder(Material.BOOK).setName("&cValues not setable in GUI yet:").setLore(
							"&aLocationDistance, AdvancedPriority, Javascript, Lucky, Random, Rewards, AdvancedRandomReward, Potions(Effects only), Title, BoosBar, Sound, Effect, FireWork, Item, Items, AdvancedPriority, SpecialChance, RandomItem, Choices",
							"&bThis is a long list, overtime these will eventually be aded to edit gui",
							"&3Also looking for feedback on GUI")) {

				@Override
				public void onClick(ClickEvent clickEvent) {
				}
			});
		}
		inv.addButton(new BInventoryButton(new ItemBuilder(Material.BARRIER).setName("&cGo back")) {

			@Override
			public void onClick(ClickEvent clickEvent) {
				openRewardGUI(clickEvent.getPlayer(), rewardEditData, rewardName);
			}
		});
		inv.openInventory(player);
	}

	public void openRewardGUI(Player player, RewardEditData rewardEditData, String rewardName) {
		EditGUI inv = new EditGUI("Reward: " + rewardName);

		inv.addData("Reward", rewardEditData);

		inv.addButton(new BInventoryButton(new ItemBuilder(Material.REDSTONE).setName("&cRequirements")) {

			@Override
			public void onClick(ClickEvent clickEvent) {
				openRewardGUIRequirements(clickEvent.getPlayer(), rewardEditData, rewardName);
			}
		});

		inv.addButton(new BInventoryButton(
				new ItemBuilder(Material.DIAMOND).setName("&cRewards").addLoreLine("&cOnly shows set values")) {

			@Override
			public void onClick(ClickEvent clickEvent) {
				openRewardGUIRewards(clickEvent.getPlayer(), rewardEditData, rewardName, false);
			}
		});

		inv.addButton(new BInventoryButton(new ItemBuilder(Material.DIAMOND).setName("&cAll Rewards")
				.addLoreLine("&cShows all possible settings")) {

			@Override
			public void onClick(ClickEvent clickEvent) {
				openRewardGUIRewards(clickEvent.getPlayer(), rewardEditData, rewardName, true);
			}
		});

		inv.openInventory(player);
	}

	public void openRewardGUI(Player player, DirectlyDefinedReward directlyDefinedReward) {
		if (!player.hasPermission(AdvancedCorePlugin.getInstance().getOptions().getPermPrefix() + ".RewardEdit")) {
			player.sendMessage("You do not have enough permission to do this");
			return;
		}

		if (directlyDefinedReward == null) {
			player.sendMessage("DirectlyDefinedReward not found");
			return;
		}

		if (!directlyDefinedReward.isDirectlyDefined()) {
			player.sendMessage("Reward " + directlyDefinedReward.getPath() + " is not directly defined");
			return;
		}

		openRewardGUI(player, new RewardEditData(directlyDefinedReward), directlyDefinedReward.getPath());
	}

	public void openRewardGUI(Player player, Reward reward) {
		if (!player.hasPermission(AdvancedCorePlugin.getInstance().getOptions().getPermPrefix() + ".RewardEdit")) {
			player.sendMessage("You do not have enough permission to do this");
			return;
		}

		openRewardGUI(player, new RewardEditData(reward), reward.getName());
	}

	/**
	 * Open rewards GUI.
	 *
	 * @param player the player
	 */
	public void openRewardsGUI(Player player) {
		if (!player.hasPermission(AdvancedCorePlugin.getInstance().getOptions().getPermPrefix() + ".RewardEdit")) {
			player.sendMessage("You do not have enough permission to do this");
			return;
		}
		BInventory inv = new BInventory("Rewards");
		for (Reward reward : RewardHandler.getInstance().getRewards()) {
			if (!reward.getConfig().isDirectlyDefinedReward()) {
				ArrayList<String> lore = new ArrayList<String>();
				if (reward.getConfig().isDirectlyDefinedReward()) {
					lore.add("&cReward is directly defined, can not edit in GUI");
				}

				inv.addButton(new BInventoryButton(reward.getRewardName(), ArrayUtils.getInstance().convert(lore),
						new ItemStack(Material.STONE)) {

					@Override
					public void onClick(ClickEvent event) {
						Player player = event.getWhoClicked();

						Reward reward = (Reward) getData("Reward");
						if (!reward.getConfig().isDirectlyDefinedReward()) {
							openRewardGUI(player, new RewardEditData(reward), reward.getRewardName());
						} else {
							player.sendMessage("Can't edit this reward, directly defined reward");
						}
					}
				}.addData("Reward", reward));
			}
		}

		inv.openInventory(player);
	}

}
