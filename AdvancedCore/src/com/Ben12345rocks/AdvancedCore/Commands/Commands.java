package com.Ben12345rocks.AdvancedCore.Commands;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import com.Ben12345rocks.AdvancedCore.Main;
import com.Ben12345rocks.AdvancedCore.Commands.GUI.RewardGUI;
import com.Ben12345rocks.AdvancedCore.Configs.Config;
import com.Ben12345rocks.AdvancedCore.Configs.ConfigRewards;
import com.Ben12345rocks.AdvancedCore.Util.Inventory.BInventory;
import com.Ben12345rocks.AdvancedCore.Util.Inventory.BInventory.ClickEvent;
import com.Ben12345rocks.AdvancedCore.Util.Inventory.BInventoryButton;
import com.Ben12345rocks.AdvancedCore.Util.ValueRequest.ValueRequest;
import com.Ben12345rocks.AdvancedCore.Util.ValueRequest.Listeners.BooleanListener;
import com.Ben12345rocks.AdvancedCore.Util.ValueRequest.Listeners.StringListener;

// TODO: Auto-generated Javadoc
/**
 * The Class Commands.
 */
public class Commands {

	/** The instance. */
	static Commands instance = new Commands();

	/** The plugin. */
	static Main plugin = Main.plugin;

	/**
	 * Gets the single instance of Commands.
	 *
	 * @return single instance of Commands
	 */
	public static Commands getInstance() {
		return instance;
	}

	/**
	 * Instantiates a new commands.
	 */
	private Commands() {
	}

	/**
	 * Instantiates a new commands.
	 *
	 * @param plugin
	 *            the plugin
	 */
	public Commands(Main plugin) {
		Commands.plugin = plugin;
	}

	/**
	 * Open config GUI.
	 *
	 * @param player
	 *            the player
	 */
	public void openConfigGUI(Player player) {
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

	/**
	 * Open GUI.
	 *
	 * @param player
	 *            the player
	 */
	public void openGUI(Player player) {
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
									ConfigRewards.getInstance().getData(value);
									player.sendMessage("Reward file created");
									plugin.reload();

								}
							});
				} else {
					RewardGUI.getInstance().openRewardsGUI(player);
				}
			}
		});
		inv.addButton(inv.getNextSlot(), new BInventoryButton("&cConfig",
				new String[] {}, new ItemStack(Material.PAPER)) {

			@Override
			public void onClick(ClickEvent event) {
				Player player = event.getWhoClicked();
				openConfigGUI(player);

			}

		});
		inv.openInventory(player);
	}

}
