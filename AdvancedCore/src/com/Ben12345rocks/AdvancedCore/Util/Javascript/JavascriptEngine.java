package com.Ben12345rocks.AdvancedCore.Util.Javascript;

import java.util.HashMap;
import java.util.Map.Entry;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.Ben12345rocks.AdvancedCore.AdvancedCoreHook;
import com.Ben12345rocks.AdvancedCore.UserManager.UserManager;

public class JavascriptEngine {
	private HashMap<String, Object> engineAPI;

	public JavascriptEngine() {
		engineAPI = new HashMap<String, Object>();
	}

	public JavascriptEngine addToEngine(String text, Object ob) {
		engineAPI.put(text, ob);
		return this;
	}

	public Object getResult(String expression) {
		if (!expression.equals("")) {
			ScriptEngine engine = new ScriptEngineManager().getEngineByName("javascript");
			engine.put("BukkitServer", Bukkit.getServer());
			engine.put("AdvancedCore", AdvancedCoreHook.getInstance());

			// String exp =
			// StringUtils.getInstance().replacePlaceHolders(player,
			// expression);

			for (Entry<String, Object> entry : engineAPI.entrySet()) {
				engine.put(entry.getKey(), entry.getValue());
			}

			try {
				return engine.eval(expression);
			} catch (ScriptException e) {
				e.printStackTrace();
			}
		}
		return null;
	}

	public JavascriptEngine addPlayer(Player player) {
		addToEngine("Player", player);
		addToEngine("User", UserManager.getInstance().getUser(player));
		return this;
	}

	public JavascriptEngine addPlayer(CommandSender player) {
		addToEngine("Sender", player);
		return this;
	}

	public String getStringValue(String expression) {
		Object result = getResult(expression);
		try {
			return result.toString();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return "";
	}

	public boolean getBooleanValue(String expression) {
		Object result = getResult(expression);
		try {
			return ((Boolean) result);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}
}
