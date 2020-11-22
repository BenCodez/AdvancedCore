package com.bencodez.advancedcore.api.javascript;

import org.bukkit.OfflinePlayer;

public abstract class JavascriptPlaceholderRequest {

	private String str;

	public JavascriptPlaceholderRequest(String str) {
		this.str = str;
	}

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
