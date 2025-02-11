package com.bencodez.advancedcore.api.cmi;

import org.bukkit.entity.Player;

import com.Zrips.CMI.CMI;

public class CMIHandler {
	public boolean isVanished(Player p) {
		return CMI.getInstance().getPlayerManager().getUser(p).isVanished();
	}
}
