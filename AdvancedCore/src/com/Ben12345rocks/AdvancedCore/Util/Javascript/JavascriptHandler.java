package com.Ben12345rocks.AdvancedCore.Util.Javascript;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.Ben12345rocks.AdvancedCore.Main;
import com.Ben12345rocks.AdvancedCore.Objects.User;
import com.Ben12345rocks.AdvancedCore.UserManager.UserManager;
import com.Ben12345rocks.AdvancedCore.Util.Misc.StringUtils;

/**
 * The Class JavascriptHandler.
 */
public class JavascriptHandler {

	/** The instance. */
	static JavascriptHandler instance = new JavascriptHandler();

	/** The plugin. */
	@SuppressWarnings("unused")
	private Main plugin = Main.plugin;

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

	public boolean evalute(User user, String expression) {
		return evalute(user.getPlayer(), expression);
	}

	public boolean evalute(Player player, String expression) {
		ScriptEngine engine = null;
		if (player == null || expression.equals("")) {
			return false;
		}

		if (engine == null) {
			engine = new ScriptEngineManager().getEngineByName("javascript");
			engine.put("BukkitServer", Bukkit.getServer());
		}

		String exp = StringUtils.getInstance().replacePlaceHolders(player, expression);

		engine.put("BukkitPlayer", player);
		engine.put("User", UserManager.getInstance().getUser(player));

		try {
			Object result = engine.eval(exp);

			if (result instanceof Boolean) {
				return ((Boolean) result).booleanValue();
			}
		} catch (ScriptException e) {
			e.printStackTrace();
		}
		return false;
	}
}
