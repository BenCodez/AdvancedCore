package com.bencodez.advancedcore.listeners;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.advancedcore.api.user.UUID;
import com.bencodez.advancedcore.api.user.UserManager;
import com.bencodez.advancedcore.api.user.UserStorage;

// TODO: Auto-generated Javadoc
/**
 * The Class PlayerJoinEvent.
 */
public class PlayerJoinEvent implements Listener {

	/** The plugin. */
	private AdvancedCorePlugin plugin;

	/**
	 * Instantiates a new player join event.
	 *
	 * @param plugin the plugin
	 */
	public PlayerJoinEvent(AdvancedCorePlugin plugin) {
		this.plugin = plugin;
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onJoin(AdvancedCoreLoginEvent event) {
		if (event.isUserInStorage()) {
			Player player = event.getPlayer();
			boolean userExist = UserManager.getInstance()
					.userExist(new UUID(event.getPlayer().getUniqueId().toString()));
			if (userExist) {
				if (plugin.getStorageType().equals(UserStorage.MYSQL) && plugin.getMysql() != null) {
					plugin.getMysql().playerJoin(player.getUniqueId().toString());
				} /*
					 * else if
					 * (AdvancedCorePlugin.getInstance().getStorageType().equals(UserStorage.SQLITE)
					 * ) { AdvancedCorePlugin.getInstance().getSQLiteUserTable().playerJoin(player.
					 * getUniqueId().toString()); }
					 */
			}

			if (userExist) {
				AdvancedCoreUser user = UserManager.getInstance().getUser(player);

				user.checkOfflineRewards();
				user.setLastOnline(System.currentTimeMillis());
				user.updateName(false);
			}
			plugin.getUuidNameCache().put(player.getUniqueId().toString(), player.getName());

		}
	}

	/**
	 * On player login.
	 *
	 * @param event the event
	 */
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onPlayerLogin(final PlayerLoginEvent event) {
		Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, new Runnable() {

			@Override
			public void run() {
				if (plugin.isAuthMeLoaded() && plugin.getOptions().isWaitUntilLoggedIn()) {
					return;
				}

				Player player = event.getPlayer();

				if (player != null) {
					plugin.debug(
							"Login: " + event.getPlayer().getName() + " (" + event.getPlayer().getUniqueId() + ")");
					AdvancedCoreLoginEvent login = new AdvancedCoreLoginEvent(player);
					Bukkit.getPluginManager().callEvent(login);

					if (login.isCancelled()) {
						return;
					}

				}

			}
		}, 60L);
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onPlayerQuit(PlayerQuitEvent event) {
		plugin.debug("Logout: " + event.getPlayer().getName() + " (" + event.getPlayer().getUniqueId() + ")");

		if (plugin.getOptions().isClearCacheOnLeave()) {
			Player player = event.getPlayer();
			if (plugin.getStorageType().equals(UserStorage.MYSQL) && plugin.getMysql() != null) {
				plugin.getMysql().removePlayer(player.getUniqueId().toString());
			} /*
				 * else if
				 * (AdvancedCorePlugin.getInstance().getStorageType().equals(UserStorage.SQLITE)
				 * ) {
				 * AdvancedCorePlugin.getInstance().getSQLiteUserTable().removePlayer(player.
				 * getUniqueId().toString()); }
				 */
		}
	}
}