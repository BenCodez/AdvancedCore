package com.Ben12345rocks.AdvancedCore.Util.Request;

import org.bukkit.entity.Player;

/**
 * Input listener
 */
public abstract class InputListener {
	
	/**
	 * On input.
	 *
	 * @param conversable
	 *            the conversable
	 * @param input
	 *            the input
	 */
	public abstract void onInput(Player player, String input);
}
