package com.Ben12345rocks.AdvancedCore.Util.Javascript;

import java.util.ArrayList;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.Ben12345rocks.AdvancedCore.Main;
import com.Ben12345rocks.AdvancedCore.Utils;
import com.Ben12345rocks.AdvancedCore.Objects.RewardHandler;
import com.Ben12345rocks.AdvancedCore.Objects.User;

/**
 * The Class JavascriptHandler.
 */
public class JavascriptHandler {

	/**
	 * Instantiates a new javascript handler.
	 *
	 * @param user
	 *            the user
	 * @param online
	 *            the online
	 * @param expression
	 *            the expression
	 * @param trueRewards
	 *            the true rewards
	 * @param falseRewards
	 *            the false rewards
	 */
	public JavascriptHandler(User user, boolean online, String expression,
			ArrayList<String> trueRewards, ArrayList<String> falseRewards) {
		ScriptEngine engine = null;
		Player player = user.getPlayer();
		if (player == null || expression.equals("")) {
			return;
		}

		if (engine == null) {
			engine = new ScriptEngineManager().getEngineByName("javascript");
			engine.put("BukkitServer", Bukkit.getServer());
		}

		String exp = Utils.getInstance()
				.replacePlaceHolders(player, expression);

		engine.put("BukkitPlayer", player);
		// Main.plugin.debug("Trying");

		try {
			engine.put("BukkitPlayer", player);

			Object result = engine.eval(exp);

			if (!(result instanceof Boolean)) {
				// Main.plugin.debug("Not boolean");
				return;
			}

			if (((Boolean) result).booleanValue()) {
				Main.plugin.debug("javascript true");
				for (String reward : trueRewards) {
					if (!reward.equals("")) {
						RewardHandler.getInstance().giveReward(user, reward,
								online);
					}
				}
			} else {
				Main.plugin.debug("javascript false");
				for (String reward : falseRewards) {
					if (!reward.equals("")) {
						RewardHandler.getInstance().giveReward(user, reward,
								online);
					}
				}
			}
		} catch (ScriptException e) {
			e.printStackTrace();
		}

	}

}
