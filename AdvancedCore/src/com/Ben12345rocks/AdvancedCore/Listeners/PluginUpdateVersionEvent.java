package com.Ben12345rocks.AdvancedCore.Listeners;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;

/**
 * The Class PluginUpdateVersionEvent.
 */
public class PluginUpdateVersionEvent extends Event {

	/** The Constant handlers. */
	private static final HandlerList handlers = new HandlerList();

	/**
	 * Gets the handler list.
	 *
	 * @return the handler list
	 */
	public static HandlerList getHandlerList() {
		return handlers;
	}

	/** The plugin. */
	private Plugin plugin;

	/** The old version. */
	private String oldVersion;

	/**
	 * Instantiates a new plugin update version event.
	 *
	 * @param plugin
	 *            the plugin
	 * @param oldVersion
	 *            the old version
	 */
	public PluginUpdateVersionEvent(Plugin plugin, String oldVersion) {
		super(true);
		this.plugin = plugin;
		this.oldVersion = oldVersion;
	}

	/*
	 * (non-Javadoc)
	 * @see org.bukkit.event.Event#getHandlers()
	 */
	@Override
	public HandlerList getHandlers() {
		return handlers;
	}

	/**
	 * Gets the old version.
	 *
	 * @return the old version
	 */
	public String getOldVersion() {
		return oldVersion;
	}

	/**
	 * Gets the plugin.
	 *
	 * @return the plugin
	 */
	public Plugin getPlugin() {
		return plugin;
	}

	/**
	 * Sets the old version.
	 *
	 * @param oldVersion
	 *            the new old version
	 */
	public void setOldVersion(String oldVersion) {
		this.oldVersion = oldVersion;
	}

	/**
	 * Sets the plugin.
	 *
	 * @param plugin
	 *            the new plugin
	 */
	public void setPlugin(Plugin plugin) {
		this.plugin = plugin;
	}

}
