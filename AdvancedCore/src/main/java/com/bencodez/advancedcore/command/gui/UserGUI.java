package com.bencodez.advancedcore.command.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;

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
import com.bencodez.simpleapi.player.PlayerUtils;
import com.bencodez.simpleapi.sql.data.DataValue;
import com.bencodez.simpleapi.valuerequest.StringListener;
import com.bencodez.simpleapi.valuerequest.ValueRequest;

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
		setCurrentPlayer(player, playerName);
		BInventory inv = new BInventory("UserGUI: " + playerName);
		inv.addData("player", playerName);
		inv.addButton(new BInventoryButton("Give Reward File", new String[] {}, new ItemStack(Material.STONE)) {

			@Override
			public void onClick(ClickEvent clickEvent) {
				ArrayList<String> rewards = new ArrayList<>();
				for (Reward reward : plugin.getRewardHandler().getRewards()) {
					rewards.add(reward.getRewardName());
				}

				new ValueRequest(plugin, plugin.getDialogService()).requestString(clickEvent.getPlayer(), "",
						rewards, true, null, new StringListener() {

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
				final AdvancedCoreUser user = plugin.getUserManager().getUser(playerName);
				if (plugin.getUserManager().getDataManager().deferSharedStorageResult(user.getData()::getValues,
						values -> {
							if (isCurrentEditorTarget(player, playerName)) openEditData(player, playerName, user, values);
						},
						failure -> player.sendMessage("Unable to read user data; check the server log."), player)) return;
				openEditData(player, playerName, user, user.getData().getValues());
			}
		});

		inv.addButton(new BInventoryButton(new ItemBuilder(Material.PAPER).setName("&cView player data")) {

			@Override
			public void onClick(ClickEvent clickEvent) {
				AdvancedCoreUser user = plugin.getUserManager().getUser(playerName);
				if (plugin.getUserManager().getDataManager().deferSharedStorageResult(user.getData()::getValues,
						values -> sendUserData(user, values),
						failure -> clickEvent.getPlayer().sendMessage("Unable to read user data; check the server log."),
						clickEvent.getPlayer())) return;
				sendUserData(user, user.getData().getValues());
			}
		});

		for (BInventoryButton button : extraButtons.values()) {
			inv.addButton(button);
		}

		inv.openInventory(player);
	}

	/** Reject a deferred editor completion after permission or selection changed. */
	boolean isCurrentEditorTarget(Player player, String playerName) {
		if (!player.hasPermission("AdvancedCore.UserEdit")) {
			player.sendMessage("Not enough permissions");
			return false;
		}
		return playerName.equals(getCurrentPlayer(player));
	}

	/** Build inventories only after a deferred shared-store read returns to Bukkit's thread. */
	private void openEditData(Player player, String playerName, AdvancedCoreUser user,
			HashMap<String, DataValue> values) {
		EditGUI edit = new EditGUI("Edit Data, click to change");
		for (Entry<String, DataValue> entry : values.entrySet()) {
			final String key = entry.getKey();
			String value = displayValue(entry.getValue());
			edit.addButton(new EditGUIButton(new ItemBuilder(Material.STONE).setName(key + " = " + value),
					new EditGUIValueString(key, value) {

						@Override
						public void setValue(Player player, String value) {
							if (value.equals("\"\"")) value = "";
							user.getData().setString(key, value);
							openUserGUI(player, playerName);
						}
					}));
		}
		edit.openInventory(player);
	}

	private void sendUserData(AdvancedCoreUser user, HashMap<String, DataValue> values) {
		for (Entry<String, DataValue> entry : values.entrySet()) {
			user.sendMessage("&c&l" + entry.getKey() + " &c" + displayValue(entry.getValue()));
		}
	}

	private String displayValue(DataValue value) {
		if (value == null) return "";
		if (value.isInt()) return String.valueOf(value.getInt());
		String string = value.getString();
		return string == null ? "" : string;
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
		new ValueRequest(plugin, plugin.getDialogService()).requestString(player, "", players, true,
				null, new StringListener() {

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
