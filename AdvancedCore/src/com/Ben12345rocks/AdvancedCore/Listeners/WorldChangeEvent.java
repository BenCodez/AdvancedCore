package com.Ben12345rocks.AdvancedCore.Listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.plugin.Plugin;

import com.Ben12345rocks.AdvancedCore.Objects.User;
import com.Ben12345rocks.AdvancedCore.UserManager.UserManager;

/**
 * The Class WorldChangeEvent.
 */
public class WorldChangeEvent implements Listener {

	/** The plugin. */
	private static Plugin plugin;

	/**
	 * Instantiates a new world change event.
	 *
	 * @param plugin2
	 *            the plugin
	 */
	public WorldChangeEvent(Plugin plugin) {
		WorldChangeEvent.plugin = plugin;
	}

	/**
	 * On world change.
	 *
	 * @param event
	 *            the event
	 */
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onWorldChange(PlayerChangedWorldEvent event) {
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

				User user = UserManager.getInstance().getUser(player);

				user.checkOfflineRewards();
				user.offVoteWorld(player.getWorld().getName());
			}
		}, 20L);

	}

}
