package com.Ben12345rocks.AdvancedCore.Util.Request;

import org.bukkit.entity.Player;

/**
 * InputListener
 */
@Deprecated
public abstract class InputListener {

	/**
	 * On input.
	 *
	 * @param player
	 *            the player
	 * @param input
	 *            the input
	 */
	public abstract void onInput(Player player, String input);
}
