package com.Ben12345rocks.AdvancedCore.Listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import com.Ben12345rocks.AdvancedCore.Main;

// TODO: Auto-generated Javadoc
/**
 * The Class PlayerJoinEvent.
 */
public class AdvancedCoreUpdateEvent implements Listener {

	/** The plugin. */
	private static Main plugin;

	/**
	 * Instantiates a new player join event.
	 *
	 * @param plugin
	 *            the plugin
	 */
	public AdvancedCoreUpdateEvent(Main plugin) {
		AdvancedCoreUpdateEvent.plugin = plugin;
	}

	/**
	 * On player login.
	 *
	 * @param event
	 *            the event
	 */
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onPluginUpdate(PluginUpdateVersionEvent event) {
		plugin.getServer().getScheduler().runTaskLater(plugin, new Runnable() {

			@Override
			public void run() {
				if (event.getOldVersion().equals("")) {
					plugin.getLogger().info("First load of AdvancedCore");
				}
				if (event.getPlugin().getName()
						.equals(plugin.getDescription().getName())) {
					plugin.getLogger().info("Updated AdvancedCore");
				}
			}
		}, 20L);

	}
}