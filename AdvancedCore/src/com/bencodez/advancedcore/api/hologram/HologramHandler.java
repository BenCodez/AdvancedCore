package com.bencodez.advancedcore.api.hologram;

import java.util.ArrayList;

import com.bencodez.advancedcore.AdvancedCorePlugin;

public class HologramHandler {
	private ArrayList<Hologram> list;
	@SuppressWarnings("unused")
	private AdvancedCorePlugin plugin;

	public HologramHandler(AdvancedCorePlugin plugin) {
		this.plugin = plugin;
		list = new ArrayList<Hologram>();
	}

	public void add(Hologram hologram) {
		list.add(hologram);
	}

	public void remove(Hologram hologram) {
		remove(hologram, true);
	}

	public void remove(Hologram hologram, boolean kill) {
		if (kill) {
			hologram.kill();
		}
		list.remove(hologram);
	}

	public void onShutDown() {
		for (Hologram hologram : list) {
			hologram.kill();
		}
		list = new ArrayList<Hologram>();
	}
}
