package com.Ben12345rocks.AdvancedCore.Listeners;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import com.Ben12345rocks.AdvancedCore.AdvancedCorePlugin;
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

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onJoin(AdvancedCoreLoginEvent event) {
		if (event.isUserInStorage()) {
			Player player = event.getPlayer();
			boolean userExist = UserManager.getInstance()
					.userExist(new UUID(event.getPlayer().getUniqueId().toString()));
			if (userExist) {
				if (AdvancedCorePlugin.getInstance().getStorageType().equals(UserStorage.MYSQL)
						&& AdvancedCorePlugin.getInstance().getMysql() != null) {
					AdvancedCorePlugin.getInstance().getMysql().playerJoin(player.getUniqueId().toString());
				} else if (AdvancedCorePlugin.getInstance().getStorageType().equals(UserStorage.SQLITE)) {
					AdvancedCorePlugin.getInstance().getSQLiteUserTable().playerJoin(player.getUniqueId().toString());
				}
			}

			if (userExist) {
				User user = UserManager.getInstance().getUser(player);

				user.checkOfflineRewards();
				user.setLastOnline(System.currentTimeMillis());
				user.updateName();
			}
			AdvancedCorePlugin.getInstance().getUuidNameCache().put(player.getUniqueId().toString(), player.getName());

		}
	}

	/**
	 * On player login.
	 *
	 * @param event
	 *            the event
	 */
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onPlayerLogin(final PlayerLoginEvent event) {
		Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, new Runnable() {

			@Override
			public void run() {
				if (AdvancedCorePlugin.getInstance().isAuthMeLoaded()
						&& AdvancedCorePlugin.getInstance().getOptions().isWaitUntilLoggedIn()) {
					return;
				}

				Player player = event.getPlayer();

				if (player != null) {
					AdvancedCorePlugin.getInstance().debug(
							"Login: " + event.getPlayer().getName() + " (" + event.getPlayer().getUniqueId() + ")");
					AdvancedCoreLoginEvent login = new AdvancedCoreLoginEvent(player);
					Bukkit.getPluginManager().callEvent(login);

					if (login.isCancelled()) {
						return;
					}

				}

			}
		}, 30L);
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onPlayerQuit(PlayerQuitEvent event) {
		AdvancedCorePlugin.getInstance()
				.debug("Logout: " + event.getPlayer().getName() + " (" + event.getPlayer().getUniqueId() + ")");

		if (AdvancedCorePlugin.getInstance().getOptions().isClearCacheOnLeave()) {
			Player player = event.getPlayer();
			if (AdvancedCorePlugin.getInstance().getStorageType().equals(UserStorage.MYSQL)
					&& AdvancedCorePlugin.getInstance().getMysql() != null) {
				AdvancedCorePlugin.getInstance().getMysql().removePlayer(player.getUniqueId().toString());
			} else if (AdvancedCorePlugin.getInstance().getStorageType().equals(UserStorage.SQLITE)) {
				AdvancedCorePlugin.getInstance().getSQLiteUserTable().removePlayer(player.getUniqueId().toString());
			}
		}
	}
}