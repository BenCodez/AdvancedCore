package com.Ben12345rocks.AdvancedCore.Commands.GUI;

import java.util.ArrayList;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import com.Ben12345rocks.AdvancedCore.AdvancedCoreHook;
import com.Ben12345rocks.AdvancedCore.Objects.RewardHandler;
import com.Ben12345rocks.AdvancedCore.Util.Inventory.BInventory;
import com.Ben12345rocks.AdvancedCore.Util.Inventory.BInventory.ClickEvent;
import com.Ben12345rocks.AdvancedCore.Util.Inventory.BInventoryButton;
import com.Ben12345rocks.AdvancedCore.Util.Item.ItemBuilder;
import com.Ben12345rocks.AdvancedCore.Util.ValueRequest.ValueRequest;
import com.Ben12345rocks.AdvancedCore.Util.ValueRequest.Listeners.StringListener;

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

	AdvancedCoreHook plugin = AdvancedCoreHook.getInstance();

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
	 * @param b
	 *            the b
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
	 * @param player
	 *            the player
	 */
	public void openGUI(Player player) {
		if (!player.hasPermission(AdvancedCoreHook.getInstance().getPermPrefix() + ".AdminEdit")) {
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
							plugin.reload();

						}
					});
				} else {
					RewardEditGUI.getInstance().openRewardsGUI(player);
				}
			}
		});

		inv.addButton(inv.getNextSlot(),
				new BInventoryButton(new ItemBuilder(Material.SKULL_ITEM, 1, (short) 3).setName("&cUsers")) {

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
