package com.Ben12345rocks.AdvancedCore.Listeners;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import com.Ben12345rocks.AdvancedCore.AdvancedCoreHook;
import com.Ben12345rocks.AdvancedCore.Objects.UUID;
import com.Ben12345rocks.AdvancedCore.Objects.User;
import com.Ben12345rocks.AdvancedCore.Objects.UserStorage;
import com.Ben12345rocks.AdvancedCore.UserManager.UserManager;

// TODO: Auto-generated Javadoc
/**
 * The Class PlayerJoinEvent.
 */
public class PlayerJoinEvent implements Listener {

	/** The plugin. */
	private static Plugin plugin;

	/**
	 * Instantiates a new player join event.
	 *
	 * @param plugin
	 *            the plugin
	 */
	public PlayerJoinEvent(Plugin plugin) {
		PlayerJoinEvent.plugin = plugin;
	}

	/**
	 * On player login.
	 *
	 * @param event
	 *            the event
	 */
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onPlayerLogin(PlayerLoginEvent event) {
		AdvancedCoreHook.getInstance()
				.debug("Login: " + event.getPlayer().getName() + " (" + event.getPlayer().getUniqueId() + ")");
		Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, new Runnable() {

			@Override
			public void run() {

				Player player = event.getPlayer();

				if (player != null
						&& UserManager.getInstance().getAllUUIDs().contains(player.getUniqueId().toString())) {
					boolean userExist = UserManager.getInstance()
							.userExist(new UUID(event.getPlayer().getUniqueId().toString()));
					if (AdvancedCoreHook.getInstance().getStorageType().equals(UserStorage.MYSQL)
							&& AdvancedCoreHook.getInstance().getMysql() != null) {
						if (userExist) {
							AdvancedCoreHook.getInstance().getMysql()
									.loadPlayerIfNeeded(player.getUniqueId().toString());

						}
					}

					if (userExist) {
						User user = UserManager.getInstance().getUser(player);

						user.checkOfflineRewards();
						user.setLastOnline(System.currentTimeMillis());
					}
					AdvancedCoreHook.getInstance().getUuids().put(player.getName(), player.getUniqueId().toString());

				}
			}
		}, 10L);
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onPlayerQuit(PlayerQuitEvent event) {
		AdvancedCoreHook.getInstance()
				.debug("Logout: " + event.getPlayer().getName() + " (" + event.getPlayer().getUniqueId() + ")");
		/*
		 * final String uuid = event.getPlayer().getPlayer().getUniqueId().toString();
		 * Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, new Runnable() {
		 *
		 * @Override public void run() { if (Bukkit.getPlayer(UUID.fromString(uuid)) ==
		 * null) { if
		 * (AdvancedCoreHook.getInstance().getStorageType().equals(UserStorage.MYSQL)) {
		 * AdvancedCoreHook.getInstance().getMysql().removePlayer(uuid); } } } }, 100L);
		 */
	}
}