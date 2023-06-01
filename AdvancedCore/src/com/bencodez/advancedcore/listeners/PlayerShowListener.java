package com.bencodez.advancedcore.listeners;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.scheduler.BukkitScheduler;

import de.myzelyam.api.vanish.PlayerShowEvent;

// TODO: Auto-generated Javadoc
/**
 * The Class PlayerJoinEvent.
 */
public class PlayerShowListener implements Listener {

	/** The plugin. */
	private AdvancedCorePlugin plugin;

	/**
	 * Instantiates a new player join event.
	 *
	 * @param plugin the plugin
	 */
	public PlayerShowListener(AdvancedCorePlugin plugin) {
		this.plugin = plugin;
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onJoin(PlayerShowEvent event) {
		if (plugin != null && plugin.isEnabled()) {
			BukkitScheduler.runTaskLaterAsynchronously(plugin, new Runnable() {

				@Override
				public void run() {
					if (plugin != null && plugin.isEnabled()) {
						if (plugin.isAuthMeLoaded() && plugin.getOptions().isWaitUntilLoggedIn()) {
							return;
						}

						Player player = event.getPlayer();

						if (player != null) {
							plugin.debug("Vanish Login: " + event.getPlayer().getName() + " ("
									+ event.getPlayer().getUniqueId() + ")");
							if (plugin.getPermissionHandler() != null) {
								plugin.getPermissionHandler().login(player);
							}

							AdvancedCoreLoginEvent login = new AdvancedCoreLoginEvent(player);
							Bukkit.getPluginManager().callEvent(login);

							if (login.isCancelled()) {
								return;
							}
						}

					}

				}
			}, 10L);
		}
	}

}