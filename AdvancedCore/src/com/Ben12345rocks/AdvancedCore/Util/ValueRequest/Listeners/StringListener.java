package com.Ben12345rocks.AdvancedCore.Util.ValueRequest.Listeners;

import org.bukkit.entity.Player;

/**
 * String Input
 */
public abstract class StringListener {

	/**
	 * On input.
	 *
	 * @param player
	 *            the player
	 * @param value
	 *            the value
	 */
	public abstract void onInput(Player player, String value);
}
