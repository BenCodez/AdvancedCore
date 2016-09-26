package com.Ben12345rocks.AdvancedCore.Util.Javascript;

import java.util.ArrayList;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import me.clip.placeholderapi.PlaceholderAPI;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.Ben12345rocks.AdvancedCore.Configs.ConfigRewards;
import com.Ben12345rocks.AdvancedCore.Objects.User;

public class JavascriptHandler {

	public JavascriptHandler(User user, boolean online, String expression,
			ArrayList<String> trueRewards, ArrayList<String> falseRewards) {
		ScriptEngine engine = null;
		Player player = user.getPlayer();
		if (player == null) {
			return;
		}

		if (engine == null) {
			engine = new ScriptEngineManager().getEngineByName("javascript");
			engine.put("BukkitServer", Bukkit.getServer());
		}

		String exp = PlaceholderAPI.setPlaceholders(player, expression);

		engine.put("BukkitPlayer", player);

		try {
			engine.put("BukkitPlayer", player);

			Object result = engine.eval(exp);

			if (!(result instanceof Boolean)) {
				return;
			}

			if (((Boolean) result).booleanValue()) {
				for (String reward : trueRewards) {
					if (!reward.equals("")) {
						ConfigRewards.getInstance().getReward(reward)
								.giveReward(user, online);
					}
				}
			}
		} catch (ScriptException e) {
			e.printStackTrace();
		}

	}

}
