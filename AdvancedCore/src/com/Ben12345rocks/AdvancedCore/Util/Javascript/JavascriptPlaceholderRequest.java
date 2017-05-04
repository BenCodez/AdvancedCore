package com.Ben12345rocks.AdvancedCore.Util.Javascript;

import org.bukkit.entity.Player;

public abstract class JavascriptPlaceholderRequest {

	private String str;

	public JavascriptPlaceholderRequest(String str) {
		this.str = str;
	}

	public abstract Object getObject(Player player);

	/**
	 * @return the str
	 */
	public String getStr() {
		return str;
	}

	/**
	 * @param str
	 *            the str to set
	 */
	public void setStr(String str) {
		this.str = str;
	}

}
