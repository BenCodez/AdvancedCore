package com.bencodez.advancedcore.api.valuerequest;

import com.bencodez.advancedcore.AdvancedCorePlugin;

/**
 * The Enum InputMethod.
 */
public enum InputMethod {

	/** The anvil. */
	ANVIL,

	/** The book. */
	BOOK,

	/** The chat. */
	CHAT,

	/** The inventory. */
	INVENTORY,

	SIGN;

	/**
	 * Gets the method.
	 *
	 * @param method the method
	 * @return the method
	 */
	public static InputMethod getMethod(String method) {
		for (InputMethod input : values()) {
			if (method.equalsIgnoreCase(input.toString())) {
				return input;
			}
		}
		try {
			return valueOf(AdvancedCorePlugin.getInstance().getOptions().getDefaultRequestMethod());
		} catch (Exception ex) {
			return CHAT;
		}

	}
}
