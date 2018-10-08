package com.Ben12345rocks.AdvancedCore.Listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.plugin.Plugin;

import com.Ben12345rocks.AdvancedCore.AdvancedCoreHook;
import com.Ben12345rocks.AdvancedCore.UserManager.User;
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
	 * @param plugin
	 *            Plugin
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

		plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, new Runnable() {

			@Override
			public void run() {
				if (AdvancedCoreHook.getInstance().getOptions().isDisableCheckOnWorldChange()
						|| event.getPlayer() == null) {
					return;
				}
				Player player = event.getPlayer();

				User user = UserManager.getInstance().getUser(player);
				if (user.isCheckWorld()) {
					user.checkOfflineRewards();
				}
			}
		}, 1l);

	}

}
