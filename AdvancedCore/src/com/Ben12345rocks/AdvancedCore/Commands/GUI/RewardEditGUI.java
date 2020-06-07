package com.Ben12345rocks.AdvancedCore.Commands.GUI;

import java.util.ArrayList;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.Ben12345rocks.AdvancedCore.AdvancedCorePlugin;
import com.Ben12345rocks.AdvancedCore.Rewards.Reward;
import com.Ben12345rocks.AdvancedCore.Rewards.RewardHandler;
import com.Ben12345rocks.AdvancedCore.Rewards.Injected.RewardInject;
import com.Ben12345rocks.AdvancedCore.Rewards.InjectedRequirement.RequirementInject;
import com.Ben12345rocks.AdvancedCore.Util.EditGUI.EditGUI;
import com.Ben12345rocks.AdvancedCore.Util.EditGUI.EditGUIButton;
import com.Ben12345rocks.AdvancedCore.Util.EditGUI.ValueTypes.EditGUIValueBoolean;
import com.Ben12345rocks.AdvancedCore.Util.Inventory.BInventory;
import com.Ben12345rocks.AdvancedCore.Util.Inventory.BInventory.ClickEvent;
import com.Ben12345rocks.AdvancedCore.Util.Inventory.BInventoryButton;
import com.Ben12345rocks.AdvancedCore.Util.Item.ItemBuilder;
import com.Ben12345rocks.AdvancedCore.Util.Misc.ArrayUtils;
import com.Ben12345rocks.AdvancedCore.Util.Misc.PlayerUtils;

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
	 * @param player
	 *            the player
	 * @return the current reward
	 */
	public Reward getCurrentReward(Player player) {
		return (Reward) PlayerUtils.getInstance().getPlayerMeta(player, "Reward");
	}

	public void openRewardGUI(Player player, Reward reward) {
		if (!player.hasPermission(AdvancedCorePlugin.getInstance().getOptions().getPermPrefix() + ".RewardEdit")) {
			player.sendMessage("You do not have enough permission to do this");
			return;
		}
		EditGUI inv = new EditGUI("Reward: " + reward.getRewardName());

		setCurrentReward(player, reward);
		inv.addData("Reward", reward);

		inv.addButton(new EditGUIButton(new ItemBuilder(Material.PAPER),
				new EditGUIValueBoolean("ForceOffline", reward.isForceOffline()) {

					@Override
					public void setValue(Player player, boolean value) {
						getCurrentReward(player).getConfig().setRequirePermission(value);
						plugin.reloadAdvancedCore();
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
	 * @param player
	 *            the player
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
					lore.add("&cReward is directly defined, can not edit");
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

	/**
	 * Sets the current reward.
	 *
	 * @param player
	 *            the player
	 * @param reward
	 *            the reward
	 */
	private void setCurrentReward(Player player, Reward reward) {
		PlayerUtils.getInstance().setPlayerMeta(player, "Reward", reward);
	}

}
