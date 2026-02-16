package com.bencodez.advancedcore.api.valuerequest.listeners;

import org.bukkit.entity.Player;

/**
 * Abstract listener for value request inputs.
 * 
 * @param <T> the type of value this listener handles
 */
public abstract class Listener<T> {

	/**
	 * Called when a player provides input.
	 * 
	 * @param player the player providing input
	 * @param value the input value
	 */
	public abstract void onInput(Player player, T value);

}
