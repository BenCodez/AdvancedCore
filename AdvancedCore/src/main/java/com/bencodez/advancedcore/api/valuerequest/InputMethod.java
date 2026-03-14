package com.bencodez.advancedcore.api.valuerequest;

import com.bencodez.advancedcore.AdvancedCorePlugin;

/**
 * The Enum InputMethod.
 */
@Deprecated
public enum InputMethod {

	/**
	 * Book input method.
	 */
	BOOK,

	/**
	 * Chat input method.
	 */
	CHAT,

	/**
	 * Inventory input method.
	 */
	INVENTORY,

	/**
	 * Sign input method.
	 */
	SIGN,
	
	DIALOG;

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
