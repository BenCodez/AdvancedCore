package com.Ben12345rocks.AdvancedCore.Commands.GUI;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import com.Ben12345rocks.AdvancedCore.AdvancedCoreHook;
import com.Ben12345rocks.AdvancedCore.Rewards.Reward;
import com.Ben12345rocks.AdvancedCore.Rewards.RewardHandler;
import com.Ben12345rocks.AdvancedCore.UserManager.User;
import com.Ben12345rocks.AdvancedCore.UserManager.UserManager;
import com.Ben12345rocks.AdvancedCore.Util.Inventory.BInventory;
import com.Ben12345rocks.AdvancedCore.Util.Inventory.BInventory.ClickEvent;
import com.Ben12345rocks.AdvancedCore.Util.Inventory.BInventoryButton;
import com.Ben12345rocks.AdvancedCore.Util.Item.ItemBuilder;

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

	AdvancedCoreHook plugin = AdvancedCoreHook.getInstance();

	/**
	 * Instantiates a new user GUI.
	 */
	private ChoiceGUI() {
	}

	public void openClaimChoices(Player player) {
		User user = UserManager.getInstance().getUser(player);
		BInventory inv = new BInventory("UnClaimed Choices");

		ArrayList<String> choices = user.getUnClaimedChoices();
		Set<String> unClaimedChoices = new HashSet<String>();

		for (String str : choices) {
			unClaimedChoices.add(str);
		}

		for (String rewardName : unClaimedChoices) {
			Reward reward = RewardHandler.getInstance().getReward(rewardName);
			if (reward.isEnableChoices()) {
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
		if (!reward.isEnableChoices()) {
			player.sendMessage("Choice rewards not enabled");
			return;
		}

		User user = UserManager.getInstance().getUser(player);

		BInventory inv = new BInventory("Pick reward");

		for (String choice : reward.getChoices()) {
			ItemBuilder builder = new ItemBuilder(reward.getConfig().getChoicesItem(choice))
					.setNameIfNotExist("&a" + choice);

			inv.addButton(new BInventoryButton(builder) {

				@Override
				public void onClick(ClickEvent clickEvent) {
					Reward reward = (Reward) getInv().getData("Reward");
					User user = (User) getInv().getData("User");
					String choice = (String) getData("Choice");

					user.removeUnClaimedChoiceReward(reward.getName());
					reward.giveChoicesReward(user, choice);

				}
			}.addData("Choice", choice));
		}
		inv.addData("User", user);
		inv.addData("Reward", reward);

		inv.openInventory(player);
	}

	public void openPreferenceReward(Player player, String rewardName) {
		Reward reward = RewardHandler.getInstance().getReward(rewardName);
		if (!reward.isEnableChoices()) {
			player.sendMessage("Choice rewards not enabled");
			return;
		}

		User user = UserManager.getInstance().getUser(player);

		BInventory inv = new BInventory("Select Preference");

		for (String choice : reward.getChoices()) {
			ItemBuilder builder = new ItemBuilder(reward.getConfig().getChoicesItem(choice));
			if (user.getChoicePreference(rewardName).equalsIgnoreCase(choice)) {
				builder.addLoreLine("&cCurrent preference");
			} else {
				builder.addLoreLine("&aClick to set as preference");
			}

			builder.setName("&a" + choice);

			inv.addButton(new BInventoryButton(builder) {

				@Override
				public void onClick(ClickEvent clickEvent) {
					User user = (User) getInv().getData("User");
					user.setChoicePreference((String) getInv().getData("Reward"), (String) getData("Choice"));

					user.sendMessage(plugin.getOptions().getFormatChoiceRewardsPreferenceSet(), "choice",
							(String) getData("Choice"));
				}
			}.addData("Choice", choice));
		}

		inv.addButton(new BInventoryButton(new ItemBuilder(Material.PAPER).setName("&cRemove preference")) {

			@Override
			public void onClick(ClickEvent clickEvent) {
				User user = (User) getInv().getData("User");
				user.setChoicePreference((String) getInv().getData("Reward"), "");

				user.sendMessage("&cRemoved choice reward preference");
			}
		}.addData("Choice", ""));
		inv.addData("User", user);
		inv.addData("Reward", rewardName);

		inv.openInventory(player);
	}
}
