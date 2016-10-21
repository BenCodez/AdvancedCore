package com.Ben12345rocks.AdvancedCore.Commands.GUI;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import com.Ben12345rocks.AdvancedCore.Main;
import com.Ben12345rocks.AdvancedCore.Utils;
import com.Ben12345rocks.AdvancedCore.Util.Inventory.BInventory;
import com.Ben12345rocks.AdvancedCore.Util.Inventory.BInventory.ClickEvent;
import com.Ben12345rocks.AdvancedCore.Util.Inventory.BInventoryButton;
import com.Ben12345rocks.AdvancedCore.Util.ValueRequest.ValueRequest;
import com.Ben12345rocks.AdvancedCore.Util.ValueRequest.Listeners.StringListener;

public class UserGUI {

	static UserGUI instance = new UserGUI();

	/**
	 * Gets the single instance of Commands.
	 *
	 * @return single instance of Commands
	 */
	public static UserGUI getInstance() {
		return instance;
	}

	/** The plugin. */
	Main plugin = Main.plugin;

	private HashMap<Plugin, BInventory> pluginButtons = new HashMap<Plugin, BInventory>();

	/**
	 * Instantiates a new commands.
	 */
	private UserGUI() {
	}

	public synchronized void addPluginButton(Plugin plugin, BInventory inv) {
		pluginButtons.put(plugin, inv);
	}

	public String getCurrentPlayer(Player player) {
		return (String) Utils.getInstance().getPlayerMeta(player, "UserGUI");
	}

	public void openUserGUI(Player player, String playerName) {
		BInventory inv = new BInventory("UserGUI: " + playerName);

		for (Entry<Plugin, BInventory> entry : pluginButtons.entrySet()) {
			inv.addButton(inv.getNextSlot(), new BInventoryButton(entry
					.getKey().getName(), new String[] {}, new ItemStack(
							Material.STONE)) {

				@Override
				public void onClick(ClickEvent clickEvent) {
					for (Plugin p : pluginButtons.keySet()) {
						if (p.getName().equals(
								clickEvent.getClickedItem().getItemMeta()
								.getDisplayName())) {
							pluginButtons.get(p).openInventory(player);
							return;
						}
					}
				}
			});
		}

		inv.openInventory(player);
	}

	public void openUsersGUI(Player player) {
		ArrayList<String> players = new ArrayList<String>();
		for (Player p : Bukkit.getOnlinePlayers()) {
			players.add(p.getName());
		}
		new ValueRequest().requestString(player, "", Utils.getInstance()
				.convertArray(players), true, new StringListener() {

			@Override
			public void onInput(Player player, String value) {
				setCurrentPlayer(player, value);
				openUserGUI(player, value);
			}
		});
	}

	private void setCurrentPlayer(Player player, String playerName) {
		Utils.getInstance().setPlayerMeta(player, "UserGUI", playerName);
	}
}
