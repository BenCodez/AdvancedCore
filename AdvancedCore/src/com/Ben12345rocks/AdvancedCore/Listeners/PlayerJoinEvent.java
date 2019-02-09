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
import com.Ben12345rocks.AdvancedCore.UserManager.UUID;
import com.Ben12345rocks.AdvancedCore.UserManager.User;
import com.Ben12345rocks.AdvancedCore.UserManager.UserManager;
import com.Ben12345rocks.AdvancedCore.UserManager.UserStorage;

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
				if (AdvancedCoreHook.getInstance().isAuthMeLoaded()
						&& AdvancedCoreHook.getInstance().getOptions().isWaitUntilLoggedIn()) {
					return;
				}

				Player player = event.getPlayer();

				if (player != null) {
					AdvancedCoreLoginEvent login = new AdvancedCoreLoginEvent(player);
					Bukkit.getPluginManager().callEvent(login);

					if (login.isCancelled()) {
						return;
					}

				}

			}
		}, 10L);
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onJoin(AdvancedCoreLoginEvent event) {
		if (event.isUserInStorage()) {
			Player player = event.getPlayer();
			boolean userExist = UserManager.getInstance()
					.userExist(new UUID(event.getPlayer().getUniqueId().toString()));
			if (AdvancedCoreHook.getInstance().getStorageType().equals(UserStorage.MYSQL)
					&& AdvancedCoreHook.getInstance().getMysql() != null) {
				if (userExist) {
					AdvancedCoreHook.getInstance().getMysql().playerJoin(player.getUniqueId().toString());
				}
			}

			if (userExist) {
				User user = UserManager.getInstance().getUser(player);

				user.checkOfflineRewards();
				user.setLastOnline(System.currentTimeMillis());
				user.updateName();
			}
			AdvancedCoreHook.getInstance().getUuidNameCache().put(player.getUniqueId().toString(), player.getName());

		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onPlayerQuit(PlayerQuitEvent event) {
		AdvancedCoreHook.getInstance()
				.debug("Logout: " + event.getPlayer().getName() + " (" + event.getPlayer().getUniqueId() + ")");
		/*
		 * final String uuid = event.getPlayer().getPlayer().getUniqueId().toString();
		 * Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, new Runnable() {
		 * @Override public void run() { if (Bukkit.getPlayer(UUID.fromString(uuid)) ==
		 * null) { if
		 * (AdvancedCoreHook.getInstance().getStorageType().equals(UserStorage.MYSQL)) {
		 * AdvancedCoreHook.getInstance().getMysql().removePlayer(uuid); } } } }, 100L);
		 */
	}
}