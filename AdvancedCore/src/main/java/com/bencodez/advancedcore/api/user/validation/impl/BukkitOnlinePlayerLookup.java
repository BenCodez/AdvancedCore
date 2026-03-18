package com.bencodez.advancedcore.api.user.validation.impl;

import org.bukkit.Bukkit;

import com.bencodez.advancedcore.api.user.validation.interfaces.OnlinePlayerLookup;

public class BukkitOnlinePlayerLookup implements OnlinePlayerLookup {

	@Override
	public boolean isOnlineExact(String name) {
		if (name == null || name.isEmpty()) {
			return false;
		}
		return Bukkit.getPlayerExact(name) != null;
	}
}