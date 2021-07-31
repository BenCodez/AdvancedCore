package com.bencodez.advancedcore.command.gui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.inventory.BInventory;
import com.bencodez.advancedcore.api.inventory.BInventory.ClickEvent;
import com.bencodez.advancedcore.api.inventory.BInventoryButton;
import com.bencodez.advancedcore.api.item.ItemBuilder;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.rewards.RewardHandler;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;

/**
 * The Class UserGUI.
 */
public class ChoiceGUI {

	/** The instance. */
	static ChoiceGUI instance = new ChoiceGUI();

	/**
	 * Gets the single instance of UserGUI.
	 *
	 * @return single instance of UserGUI
	 */
	public static ChoiceGUI getInstance() {
		return instance;
	}

	AdvancedCorePlugin plugin = AdvancedCorePlugin.getInstance();

	/**
	 * Instantiates a new user GUI.
	 */
	private ChoiceGUI() {
	}

	public void openClaimChoices(Player player) {
		AdvancedCoreUser user = plugin.getUserManager().getUser(player);
		BInventory inv = new BInventory("UnClaimed Choices");

		ArrayList<String> choices = user.getUnClaimedChoices();
		Set<String> unClaimedChoices = new HashSet<String>();

		for (String str : choices) {
			unClaimedChoices.add(str);
		}

		for (String rewardName : unClaimedChoices) {
			Reward reward = RewardHandler.getInstance().getReward(rewardName);
			if (reward.getConfig().getEnableChoices()) {
				inv.addButton(new BInventoryButton(new ItemBuilder(reward.getConfig().getDisplayItem())) {

					@Override
					public void onClick(ClickEvent clickEvent) {
						openClaimChoices(clickEvent.getPlayer(), (Reward) getData("Reward"));
					}
				}.addData("Reward", reward));
			}
		}

		inv.openInventory(player);
	}

	public void openClaimChoices(Player player, Reward reward) {
		if (!reward.getConfig().getEnableChoices()) {
			player.sendMessage("Choice rewards not enabled");
			return;
		}

		AdvancedCoreUser user = plugin.getUserManager().getUser(player);

		BInventory inv = new BInventory("Pick reward");
		inv.dontClose();

		for (String choice : reward.getConfig().getChoices()) {
			ItemBuilder builder = new ItemBuilder(reward.getConfig().getChoicesItem(choice))
					.setNameIfNotExist("&a" + choice);

			inv.addButton(new BInventoryButton(builder) {

				@Override
				public void onClick(ClickEvent clickEvent) {
					Reward reward = (Reward) getInv().getData("Reward");
					AdvancedCoreUser user = (AdvancedCoreUser) getInv().getData("User");
					String choice = (String) getData("Choice");

					user.removeUnClaimedChoiceReward(reward.getName());
					RewardHandler.getInstance().giveChoicesReward(reward, user, choice);
					if (user.getUnClaimedChoices().size() > 0) {
						openClaimChoices(clickEvent.getPlayer());
					} else {
						Bukkit.getScheduler().runTask(plugin, new Runnable() {

							@Override
							public void run() {
								clickEvent.getPlayer().closeInventory();
							}
						});

					}
				}
			}.addData("Choice", choice));
		}
		inv.addData("User", user);
		inv.addData("Reward", reward);

		inv.openInventory(player);
	}

	public void openPreferenceReward(Player player, String rewardName) {
		Reward reward = RewardHandler.getInstance().getReward(rewardName);
		if (!reward.getConfig().getEnableChoices()) {
			player.sendMessage("Choice rewards not enabled");
			return;
		}

		AdvancedCoreUser user = plugin.getUserManager().getUser(player);

		BInventory inv = new BInventory("Select Preference");

		for (String choice : reward.getConfig().getChoices()) {
			ItemBuilder builder = new ItemBuilder(reward.getConfig().getChoicesItem(choice));
			if (user.getChoicePreference(rewardName).equalsIgnoreCase(choice)) {
				builder.addLoreLine("&cCurrent preference");
				builder.addLoreLine("&c&lClick to remove");
			} else {
				builder.addLoreLine("&aClick to set as preference");
			}

			builder.setName("&a" + choice);

			inv.addButton(new BInventoryButton(builder) {

				@Override
				public void onClick(ClickEvent clickEvent) {
					AdvancedCoreUser user = (AdvancedCoreUser) getInv().getData("User");
					String rewardName = (String) getInv().getData("Reward");
					String choice = (String) getData("Choice");
					if (user.getChoicePreference(rewardName).equals(choice)) {
						choice = "none";
					}
					user.setChoicePreference(rewardName, choice);

					user.sendMessage(plugin.getOptions().getFormatChoiceRewardsPreferenceSet(), "choice", choice);
				}
			}.addData("Choice", choice));
		}

		inv.addData("User", user);
		inv.addData("Reward", rewardName);

		inv.openInventory(player);
	}
}
