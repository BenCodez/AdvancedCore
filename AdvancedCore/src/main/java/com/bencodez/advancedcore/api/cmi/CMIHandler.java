package com.bencodez.advancedcore.api.cmi;

import org.bukkit.entity.Player;

import com.Zrips.CMI.CMI;

/**
 * Handler for CMI integration.
 */
public class CMIHandler {
	/**
	 * Checks if player is vanished.
	 *
	 * @param p the player
	 * @return true if vanished
	 */
	public boolean isVanished(Player p) {
		return CMI.getInstance().getPlayerManager().getUser(p).isVanished();
	}
}
