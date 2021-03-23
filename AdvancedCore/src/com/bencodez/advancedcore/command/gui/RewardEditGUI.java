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

		EditGUI inv = new EditGUI("Reward: " + directlyDefinedReward.getPath());

		inv.addData("Reward", new RewardEditData(directlyDefinedReward));
		
		Reward reward = directlyDefinedReward.getReward();

		inv.addButton(new EditGUIButton(new ItemBuilder(Material.PAPER),
				new EditGUIValueBoolean("ForceOffline", reward.isForceOffline()) {

					@Override
					public void setValue(Player player, boolean value) {
						RewardEditData reward = (RewardEditData) getInv().getData("Reward");
						reward.setValue(getKey(), value);
						plugin.reloadAdvancedCore(false);
					}
				}));

		for (RequirementInject injectReward : RewardHandler.getInstance().getInjectedRequirements()) {
			if (injectReward.isEditable()) {
				for (EditGUIButton b : injectReward.getEditButtons()) {
					b.getEditer().setCurrentValue(reward.getConfig().getConfigData().get(b.getEditer().getKey()));
					inv.addButton(b);
				}
			}
		}

		for (RewardInject injectReward : RewardHandler.getInstance().getInjectedRewards()) {
			if (injectReward.isEditable()) {
				for (EditGUIButton b : injectReward.getEditButtons()) {
					b.getEditer().setCurrentValue(reward.getConfig().getConfigData().get(b.getEditer().getKey()));
					inv.addButton(b);
				}
			}
		}

		inv.sort();
		inv.openInventory(player);
	}

	public void openRewardGUI(Player player, Reward reward) {
		if (!player.hasPermission(AdvancedCorePlugin.getInstance().getOptions().getPermPrefix() + ".RewardEdit")) {
			player.sendMessage("You do not have enough permission to do this");
			return;
		}
		EditGUI inv = new EditGUI("Reward: " + reward.getRewardName());

		inv.addData("Reward", new RewardEditData(reward));

		inv.addButton(new EditGUIButton(new ItemBuilder(Material.PAPER),
				new EditGUIValueBoolean("ForceOffline", reward.isForceOffline()) {

					@Override
					public void setValue(Player player, boolean value) {
						RewardEditData reward = (RewardEditData) getInv().getData("Reward");
						reward.setValue(getKey(), value);
						plugin.reloadAdvancedCore(false);
					}
				}));

		for (RequirementInject injectReward : RewardHandler.getInstance().getInjectedRequirements()) {
			if (injectReward.isEditable()) {
				for (EditGUIButton b : injectReward.getEditButtons()) {
					b.getEditer().setCurrentValue(reward.getConfig().getConfigData().get(b.getEditer().getKey()));
					inv.addButton(b);
				}
			}
		}

		for (RewardInject injectReward : RewardHandler.getInstance().getInjectedRewards()) {
			if (injectReward.isEditable()) {
				for (EditGUIButton b : injectReward.getEditButtons()) {
					b.getEditer().setCurrentValue(reward.getConfig().getConfigData().get(b.getEditer().getKey()));
					inv.addButton(b);
				}
			}
		}

		inv.sort();
		inv.openInventory(player);
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
							openRewardGUI(player, reward);
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
