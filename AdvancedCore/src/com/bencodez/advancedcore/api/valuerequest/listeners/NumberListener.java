package com.bencodez.advancedcore.api.valuerequest.listeners;

import org.bukkit.entity.Player;

public abstract class NumberListener {

	/**
	 * On input.
	 *
	 * @param player the player
	 * @param value  the value
	 */
	public abstract void onInput(Player player, Number value);
}
