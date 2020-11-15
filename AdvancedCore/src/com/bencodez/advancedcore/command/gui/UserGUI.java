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
import com.bencodez.advancedcore.api.inventory.BInventoryButton;
import com.bencodez.advancedcore.api.inventory.BInventory.ClickEvent;
import com.bencodez.advancedcore.api.inventory.editgui.EditGUI;
import com.bencodez.advancedcore.api.inventory.editgui.EditGUIButton;
import com.bencodez.advancedcore.api.inventory.editgui.valuetypes.EditGUIValueString;
import com.bencodez.advancedcore.api.item.ItemBuilder;
import com.bencodez.advancedcore.api.misc.ArrayUtils;
import com.bencodez.advancedcore.api.misc.PlayerUtils;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.rewards.RewardHandler;
import com.bencodez.advancedcore.api.rewards.RewardOptions;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.advancedcore.api.user.UserManager;
import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.advancedcore.api.valuerequest.ValueRequest;
import com.bencodez.advancedcore.api.valuerequest.listeners.StringListener;

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

	AdvancedCorePlugin plugin = AdvancedCorePlugin.getInstance();

	/** The plugin buttons. */
	private HashMap<Plugin, BInventoryButton> extraButtons = new HashMap<Plugin, BInventoryButton>();

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
		return (String) PlayerUtils.getInstance().getPlayerMeta(player, "UserGUI");
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
				ArrayList<String> rewards = new ArrayList<String>();
				for (Reward reward : RewardHandler.getInstance().getRewards()) {
					rewards.add(reward.getRewardName());
				}

				new ValueRequest().requestString(clickEvent.getPlayer(), "", ArrayUtils.getInstance().convert(rewards),
						true, new StringListener() {

							@Override
							public void onInput(Player player, String value) {
								AdvancedCoreUser user = UserManager.getInstance()
										.getUser(UserGUI.getInstance().getCurrentPlayer(player));
								RewardHandler.getInstance().giveReward(user, value, new RewardOptions());
								player.sendMessage("Given " + user.getPlayerName() + " reward file " + value);

							}
						});

			}
		});

		inv.addButton(new BInventoryButton(new ItemBuilder(Material.WRITABLE_BOOK).setName("Edit Data")) {

			@Override
			public void onClick(ClickEvent clickEvent) {
				Player player = clickEvent.getPlayer();
				EditGUI inv = new EditGUI("Edit Data, click to change");
				final AdvancedCoreUser user = UserManager.getInstance().getUser(playerName);
				for (final String key : user.getData().getKeys()) {
					String value = user.getData().getString(key, true);
					if (plugin.getOptions().getStorageType().equals(UserStorage.MYSQL)) {
						if (plugin.getMysql().isIntColumn(key)) {
							value = "" + user.getData().getInt(key, true);
						}
					}
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
				Player player = clickEvent.getPlayer();
				AdvancedCoreUser user = UserManager.getInstance().getUser(player);
				for (String key : user.getData().getKeys()) {
					String str = user.getData().getString(key, true);
					if (plugin.getOptions().getStorageType().equals(UserStorage.MYSQL)) {
						if (plugin.getMysql().isIntColumn(key)) {
							str = "" + user.getData().getInt(key, true);
						}
					}
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

		ArrayList<String> players = new ArrayList<String>();
		for (Player p : Bukkit.getOnlinePlayers()) {
			players.add(p.getName());
		}
		new ValueRequest().requestString(player, "", ArrayUtils.getInstance().convert(players), true,
				new StringListener() {

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
		PlayerUtils.getInstance().setPlayerMeta(player, "UserGUI", playerName);
	}
}
