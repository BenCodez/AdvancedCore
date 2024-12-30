package com.bencodez.advancedcore.command.gui;

import java.util.ArrayList;
import java.util.HashMap;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.inventory.BInventory;
import com.bencodez.advancedcore.api.inventory.BInventory.ClickEvent;
import com.bencodez.advancedcore.api.inventory.BInventoryButton;
import com.bencodez.advancedcore.api.inventory.editgui.EditGUI;
import com.bencodez.advancedcore.api.inventory.editgui.EditGUIButton;
import com.bencodez.advancedcore.api.inventory.editgui.valuetypes.EditGUIValueString;
import com.bencodez.advancedcore.api.item.ItemBuilder;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.rewards.RewardOptions;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.advancedcore.api.valuerequest.ValueRequest;
import com.bencodez.advancedcore.api.valuerequest.listeners.StringListener;
import com.bencodez.simpleapi.array.ArrayUtils;
import com.bencodez.simpleapi.player.PlayerUtils;

/**
 * The Class UserGUI.
 */
public class UserGUI {

	/** The instance. */
	static UserGUI instance = new UserGUI();

	/**
	 * Gets the single instance of UserGUI.
	 *
	 * @return single instance of UserGUI
	 */
	public static UserGUI getInstance() {
		return instance;
	}

	/** The plugin buttons. */
	private HashMap<Plugin, BInventoryButton> extraButtons = new HashMap<>();

	AdvancedCorePlugin plugin = AdvancedCorePlugin.getInstance();

	/**
	 * Instantiates a new user GUI.
	 */
	private UserGUI() {
	}

	/**
	 * Adds the plugin button.
	 *
	 * @param plugin the plugin
	 * @param inv    the inv
	 */
	public synchronized void addPluginButton(Plugin plugin, BInventoryButton inv) {
		extraButtons.put(plugin, inv);
	}

	/**
	 * Gets the current player.
	 *
	 * @param player the player
	 * @return the current player
	 */
	public String getCurrentPlayer(Player player) {
		return (String) PlayerUtils.getPlayerMeta(plugin, player, "UserGUI");
	}

	/**
	 * Open user GUI.
	 *
	 * @param player     the player
	 * @param playerName the player name
	 */
	public void openUserGUI(Player player, final String playerName) {
		if (!player.hasPermission("AdvancedCore.UserEdit")) {
			player.sendMessage("Not enough permissions");
			return;
		}
		BInventory inv = new BInventory("UserGUI: " + playerName);
		inv.addData("player", playerName);
		inv.addButton(new BInventoryButton("Give Reward File", new String[] {}, new ItemStack(Material.STONE)) {

			@Override
			public void onClick(ClickEvent clickEvent) {
				ArrayList<String> rewards = new ArrayList<>();
				for (Reward reward : plugin.getRewardHandler().getRewards()) {
					rewards.add(reward.getRewardName());
				}

				new ValueRequest().requestString(clickEvent.getPlayer(), "", ArrayUtils.convert(rewards), true,
						new StringListener() {

							@Override
							public void onInput(Player player, String value) {
								AdvancedCoreUser user = plugin.getUserManager()
										.getUser(UserGUI.getInstance().getCurrentPlayer(player));
								plugin.getRewardHandler().giveReward(user, value, new RewardOptions());
								player.sendMessage("Given " + user.getPlayerName() + " reward file " + value);

							}
						});

			}
		});

		inv.addButton(new BInventoryButton(new ItemBuilder("WRITABLE_BOOK").setName("Edit Data")) {

			@Override
			public void onClick(ClickEvent clickEvent) {
				Player player = clickEvent.getPlayer();
				EditGUI inv = new EditGUI("Edit Data, click to change");
				final AdvancedCoreUser user = plugin.getUserManager().getUser(playerName);
				for (final String key : user.getData().getKeys()) {
					String value = user.getData().getValue(key);
					inv.addButton(new EditGUIButton(new ItemBuilder(Material.STONE).setName(key + " = " + value),
							new EditGUIValueString(key, value) {

								@Override
								public void setValue(Player player, String value) {
									if (value.equals("\"\"")) {
										value = "";
									}
									user.getData().setString(key, value);
									openUserGUI(player, playerName);
								}
							}));
				}

				inv.openInventory(player);

			}
		});

		inv.addButton(new BInventoryButton(new ItemBuilder(Material.PAPER).setName("&cView player data")) {

			@Override
			public void onClick(ClickEvent clickEvent) {
				AdvancedCoreUser user = plugin.getUserManager().getUser(playerName);
				for (String key : user.getData().getKeys()) {
					String str = user.getData().getValue(key);
					user.sendMessage("&c&l" + key + " &c" + str);
				}
			}
		});

		for (BInventoryButton button : extraButtons.values()) {
			inv.addButton(button);
		}

		inv.openInventory(player);
	}

	/**
	 * Open users GUI.
	 *
	 * @param player the player
	 */
	public void openUsersGUI(Player player) {
		if (!player.hasPermission("AdvancedCore.UserEdit")) {
			player.sendMessage("Not enough permissions");
			return;
		}

		ArrayList<String> players = new ArrayList<>();
		for (Player p : Bukkit.getOnlinePlayers()) {
			players.add(p.getName());
		}
		new ValueRequest().requestString(player, "", ArrayUtils.convert(players), true, new StringListener() {

			@Override
			public void onInput(Player player, String value) {
				setCurrentPlayer(player, value);
				openUserGUI(player, value);
			}
		});
	}

	/**
	 * Sets the current player.
	 *
	 * @param player     the player
	 * @param playerName the player name
	 */
	private void setCurrentPlayer(Player player, String playerName) {
		PlayerUtils.setPlayerMeta(plugin, player, "UserGUI", playerName);
	}
}
