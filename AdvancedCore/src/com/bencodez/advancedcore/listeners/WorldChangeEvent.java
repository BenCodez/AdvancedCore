package com.bencodez.advancedcore.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.advancedcore.scheduler.BukkitScheduler;

/**
 * The Class WorldChangeEvent.
 */
public class WorldChangeEvent implements Listener {

	/** The plugin. */
	private AdvancedCorePlugin plugin;

	/**
	 * Instantiates a new world change event.
	 *
	 * @param plugin Plugin
	 */
	public WorldChangeEvent(AdvancedCorePlugin plugin) {
		this.plugin = plugin;
	}

	/**
	 * On world change.
	 *
	 * @param event the event
	 */
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onWorldChange(PlayerChangedWorldEvent event) {
		if (plugin != null && plugin.isEnabled() && plugin.isLoadUserData()) {
			BukkitScheduler.runTaskLaterAsynchronously(plugin, new Runnable() {

				@Override
				public void run() {
					if (plugin.getOptions().isDisableCheckOnWorldChange() || event.getPlayer() == null
							|| !plugin.isLoadUserData()) {
						return;
					}
					Player player = event.getPlayer();

					AdvancedCoreUser user = plugin.getUserManager().getUser(player);
					if (user.isCheckWorld()) {
						user.checkOfflineRewards();
					}
				}
			}, 1l);
		}
	}

}
