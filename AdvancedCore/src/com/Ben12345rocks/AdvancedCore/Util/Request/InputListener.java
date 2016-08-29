package com.Ben12345rocks.AdvancedCore.Util.Request;

import org.bukkit.conversations.Conversable;

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
	public abstract void onInput(Conversable conversable, String input);
}
