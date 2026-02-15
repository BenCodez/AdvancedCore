package com.bencodez.advancedcore.api.hologram;

import java.util.ArrayList;

import com.bencodez.advancedcore.AdvancedCorePlugin;

/**
 * Handler for managing holograms.
 */
public class HologramHandler {
	private ArrayList<Hologram> list;
	@SuppressWarnings("unused")
	private AdvancedCorePlugin plugin;

	/**
	 * Constructs a new HologramHandler.
	 * 
	 * @param plugin the plugin instance
	 */
	public HologramHandler(AdvancedCorePlugin plugin) {
		this.plugin = plugin;
		list = new ArrayList<>();
	}

	/**
	 * Adds a hologram to the handler.
	 * 
	 * @param hologram the hologram to add
	 */
	public void add(Hologram hologram) {
		list.add(hologram);
	}

	/**
	 * Called on shutdown to clean up all holograms.
	 */
	public void onShutDown() {
		for (Hologram hologram : list) {
			hologram.kill();
		}
		list = new ArrayList<>();
	}

	/**
	 * Removes a hologram from the handler and kills it.
	 * 
	 * @param hologram the hologram to remove
	 */
	public void remove(Hologram hologram) {
		remove(hologram, true);
	}

	/**
	 * Removes a hologram from the handler.
	 * 
	 * @param hologram the hologram to remove
	 * @param kill whether to kill the hologram
	 */
	public void remove(Hologram hologram, boolean kill) {
		if (kill) {
			hologram.kill();
		}
		list.remove(hologram);
	}
}
