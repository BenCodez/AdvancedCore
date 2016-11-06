package com.Ben12345rocks.AdvancedCore.Commands.GUI;

import java.util.ArrayList;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import com.Ben12345rocks.AdvancedCore.Main;
import com.Ben12345rocks.AdvancedCore.Configs.Config;
import com.Ben12345rocks.AdvancedCore.Objects.RewardHandler;
import com.Ben12345rocks.AdvancedCore.Util.Inventory.BInventory;
import com.Ben12345rocks.AdvancedCore.Util.Inventory.BInventory.ClickEvent;
import com.Ben12345rocks.AdvancedCore.Util.Inventory.BInventoryButton;
import com.Ben12345rocks.AdvancedCore.Util.ValueRequest.ValueRequest;
import com.Ben12345rocks.AdvancedCore.Util.ValueRequest.Listeners.BooleanListener;
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

	/** The plugin. */
	Main plugin = Main.plugin;

	/**
	 * Instantiates a new admin GUI.
	 */
	private AdminGUI() {
	}

	/** The plugin GU is. */
	private ArrayList<BInventoryButton> pluginGUIs;

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
		if (!player.hasPermission("AdvancedCore.AdminEdit")) {
			player.sendMessage("Not enough permissions");
			return;
		}
		BInventory inv = new BInventory("AdminGUI");
		inv.addButton(inv.getNextSlot(), new BInventoryButton("&cRewards",
				new String[] { "&cMiddle click to create" }, new ItemStack(
						Material.DIAMOND)) {

			@Override
			public void onClick(ClickEvent event) {
				Player player = event.getWhoClicked();
				if (event.getClick().equals(ClickType.MIDDLE)) {
					new ValueRequest().requestString(player,
							new StringListener() {

								@Override
								public void onInput(Player player, String value) {
									RewardHandler.getInstance()
											.getReward(value);
									player.sendMessage("Reward file created");
									plugin.reload();

								}
							});
				} else {
					RewardEditGUI.getInstance().openRewardsGUI(player);
				}
			}
		});
		inv.addButton(inv.getNextSlot(), new BInventoryButton(
				"&cAdvancedCore/Config", new String[] {}, new ItemStack(
						Material.PAPER)) {

			@Override
			public void onClick(ClickEvent event) {
				Player player = event.getWhoClicked();
				openConfigGUI(player);

			}

		});

		inv.addButton(inv.getNextSlot(), new BInventoryButton("&cUsers",
				new String[] {}, new ItemStack(Material.SKULL_ITEM)) {

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

	/**
	 * Open config GUI.
	 *
	 * @param player
	 *            the player
	 */
	public void openConfigGUI(Player player) {
		if (!player.hasPermission("AdvancedCore.AdminEdit")) {
			player.sendMessage("Not enough permissions");
			return;
		}
		BInventory inv = new BInventory("Config");
		inv.addButton(inv.getNextSlot(), new BInventoryButton("Debug",
				new String[] { "Currently: "
						+ Config.getInstance().getDebugEnabled() },
				new ItemStack(Material.STONE)) {

			@Override
			public void onClick(ClickEvent event) {
				Player player = event.getWhoClicked();
				new ValueRequest().requestBoolean(player, Boolean
						.toString(Config.getInstance().getDebugEnabled()),
						new BooleanListener() {

							@Override
							public void onInput(Player player, boolean value) {
								Config.getInstance().setDebugEnabled(value);
								player.sendMessage("Value set");

							}
						});

			}
		});
		inv.addButton(inv.getNextSlot(), new BInventoryButton("DebugInGame",
				new String[] { "Currently: "
						+ Config.getInstance().getDebugInfoIngame() },
				new ItemStack(Material.STONE)) {

			@Override
			public void onClick(ClickEvent event) {
				Player player = event.getWhoClicked();
				new ValueRequest().requestBoolean(player, Boolean
						.toString(Config.getInstance().getDebugInfoIngame()),
						new BooleanListener() {

							@Override
							public void onInput(Player player, boolean value) {
								Config.getInstance().setDebugInfoIngame(value);
								player.sendMessage("Value set");

							}
						});

			}
		});
		inv.openInventory(player);
	}
}
