package com.bencodez.advancedcore.command.gui;

import java.util.ArrayList;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.inventory.BInventory;
import com.bencodez.advancedcore.api.inventory.BInventoryButton;
import com.bencodez.advancedcore.api.inventory.BInventory.ClickEvent;
import com.bencodez.advancedcore.api.item.ItemBuilder;
import com.bencodez.advancedcore.api.rewards.RewardHandler;
import com.bencodez.advancedcore.api.valuerequest.ValueRequest;
import com.bencodez.advancedcore.api.valuerequest.listeners.StringListener;

/**
 * The Class AdminGUI.
 */
public class AdminGUI {

	/** The instance. */
	static AdminGUI instance = new AdminGUI();

	/**
	 * Gets the single instance of AdminGUI.
	 *
	 * @return single instance of AdminGUI
	 */
	public static AdminGUI getInstance() {
		return instance;
	}

	AdvancedCorePlugin plugin = AdvancedCorePlugin.getInstance();

	/** The plugin GU is. */
	private ArrayList<BInventoryButton> pluginGUIs;

	/**
	 * Instantiates a new admin GUI.
	 */
	private AdminGUI() {
	}

	/**
	 * Adds the button.
	 *
	 * @param b the b
	 */
	public void addButton(BInventoryButton b) {
		if (pluginGUIs == null) {
			pluginGUIs = new ArrayList<BInventoryButton>();
		}
		pluginGUIs.add(b);
	}

	/**
	 * Open GUI.
	 *
	 * @param player the player
	 */
	public void openGUI(Player player) {
		if (!player.hasPermission(AdvancedCorePlugin.getInstance().getOptions().getPermPrefix() + ".Admin")) {
			player.sendMessage("Not enough permissions");
			return;
		}
		BInventory inv = new BInventory("AdminGUI");
		inv.addButton(inv.getNextSlot(), new BInventoryButton("&cRewards", new String[] { "&cMiddle click to create" },
				new ItemStack(Material.DIAMOND)) {

			@Override
			public void onClick(ClickEvent event) {
				Player player = event.getWhoClicked();
				if (event.getClick().equals(ClickType.MIDDLE)) {
					new ValueRequest().requestString(player, new StringListener() {

						@Override
						public void onInput(Player player, String value) {
							RewardHandler.getInstance().getReward(value);
							player.sendMessage("Reward file created");
							plugin.reloadAdvancedCore(false);

						}
					});
				} else {
					RewardEditGUI.getInstance().openRewardsGUI(player);
				}
			}
		});

		inv.addButton(inv.getNextSlot(), new BInventoryButton(new ItemBuilder("PLAYER_HEAD").setName("&cUsers")) {

			@Override
			public void onClick(ClickEvent event) {
				Player player = event.getWhoClicked();
				UserGUI.getInstance().openUsersGUI(player);

			}

		});

		if (pluginGUIs != null) {
			for (BInventoryButton b : pluginGUIs) {
				inv.addButton(inv.getNextSlot(), b);
			}
		}

		inv.openInventory(player);
	}
}
