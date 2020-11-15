package com.bencodez.advancedcore.api.valuerequest.book;

import org.bukkit.entity.Player;

/**
 * The Class BookSign.
 */
public abstract class BookSign {

	/**
	 * On book sign.
	 *
	 * @param player the player
	 * @param input  the input
	 */
	public abstract void onBookSign(Player player, String input);
}