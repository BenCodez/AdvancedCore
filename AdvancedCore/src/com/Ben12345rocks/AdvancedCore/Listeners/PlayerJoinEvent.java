package com.Ben12345rocks.AdvancedCore.Listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;

import com.Ben12345rocks.AdvancedCore.Main;
import com.Ben12345rocks.AdvancedCore.Objects.User;

// TODO: Auto-generated Javadoc
/**
 * The Class PlayerJoinEvent.
 */
public class PlayerJoinEvent implements Listener {

	/** The plugin. */
	private static Main plugin;

	/**
	 * Instantiates a new player join event.
	 *
	 * @param plugin
	 *            the plugin
	 */
	public PlayerJoinEvent(Main plugin) {
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
		plugin.getServer().getScheduler().runTaskLater(plugin, new Runnable() {

			@Override
			public void run() {
				if (event.getPlayer() == null) {
					return;
				}
				Player player = event.getPlayer();

				if (!plugin.getDataFolder().exists()) {
					plugin.getDataFolder().mkdir();
				}

				User user = new User(plugin, player);
				user.setPlayerName();
			}
		}, 20L);

	}
}