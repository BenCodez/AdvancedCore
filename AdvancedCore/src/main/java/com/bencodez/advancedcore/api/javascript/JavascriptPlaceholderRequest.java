package com.bencodez.advancedcore.api.javascript;

import org.bukkit.OfflinePlayer;

/**
 * Abstract class for Javascript placeholder requests.
 */
public abstract class JavascriptPlaceholderRequest {

	private String str;

	/**
	 * Creates a new Javascript placeholder request.
	 * 
	 * @param str the placeholder string
	 */
	public JavascriptPlaceholderRequest(String str) {
		this.str = str;
	}

	/**
	 * Gets the object value for the given player.
	 * 
	 * @param player the player to get the value for
	 * @return the object value
	 */
	public abstract Object getObject(OfflinePlayer player);

	/**
	 * @return the str
	 */
	public String getStr() {
		return str;
	}

	/**
	 * @param str the str to set
	 */
	public void setStr(String str) {
		this.str = str;
	}

}
