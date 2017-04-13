package com.Ben12345rocks.AdvancedCore.Util.Javascript;

import org.bukkit.entity.Player;

import com.Ben12345rocks.AdvancedCore.Objects.User;

/**
 * The Class JavascriptHandler.
 */
public class JavascriptHandler {

	/** The instance. */
	static JavascriptHandler instance = new JavascriptHandler();

	/**
	 * Gets the single instance of JavascriptHandler.
	 *
	 * @return single instance of JavascriptHandler
	 */
	public static JavascriptHandler getInstance() {
		return instance;
	}

	/**
	 * Instantiates a new JavascriptHandler.
	 */
	private JavascriptHandler() {
	}

	public boolean evalute(Player player, String expression) {
		return new JavascriptEngine().addPlayer(player).getBooleanValue(expression);
	}

	public boolean evalute(User user, String expression) {
		return evalute(user.getPlayer(), expression);
	}
}
