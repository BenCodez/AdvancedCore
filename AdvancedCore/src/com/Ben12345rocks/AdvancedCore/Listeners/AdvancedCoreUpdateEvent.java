package com.Ben12345rocks.AdvancedCore.Listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import com.Ben12345rocks.AdvancedCore.Main;

// TODO: Auto-generated Javadoc
/**
 * The Class AdvancedCoreUpdateEvent.
 */
public class AdvancedCoreUpdateEvent implements Listener {

	/** The plugin. */
	private static Main plugin;

	/**
	 * Instantiates a new advanced core update event.
	 *
	 * @param plugin
	 *            the plugin
	 */
	public AdvancedCoreUpdateEvent(Main plugin) {
		AdvancedCoreUpdateEvent.plugin = plugin;
	}

	/**
	 * On plugin update.
	 *
	 * @param event
	 *            the event
	 */
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onPluginUpdate(PluginUpdateVersionEvent event) {

		if (event.getPlugin().getName()
				.equals(plugin.getDescription().getName())) {
			if (!event.getOldVersion().equals("")) {
				plugin.getLogger().info("Updated AdvancedCore");
			}
		}
	}

}