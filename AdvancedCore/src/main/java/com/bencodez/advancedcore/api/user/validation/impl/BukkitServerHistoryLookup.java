package com.bencodez.advancedcore.api.user.validation.impl;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import com.bencodez.advancedcore.api.user.validation.interfaces.ServerHistoryLookup;

@SuppressWarnings("deprecation")
public class BukkitServerHistoryLookup implements ServerHistoryLookup {

	@Override
	public boolean hasJoinedBefore(String name) {
		if (name == null || name.isEmpty()) {
			return false;
		}

		OfflinePlayer player = Bukkit.getOfflinePlayer(name);
		return player != null && (player.hasPlayedBefore() || player.isOnline() || player.getLastPlayed() != 0);
	}
}