package com.bencodez.advancedcore.api.javascript;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map.Entry;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.messages.StringParser;
import com.bencodez.advancedcore.api.rewards.RewardHandler;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.advancedcore.api.user.UserManager;
import com.bencodez.advancedcore.nms.ReflectionUtils;

public class JavascriptEngine {
	private HashMap<String, Object> engineAPI;

	public JavascriptEngine() {
		engineAPI = new HashMap<String, Object>();
	}

	public JavascriptEngine addPlayer(AdvancedCoreUser user) {
		addToEngine("PlayerName", user.getPlayerName());
		addToEngine("PlayerUUID", user.getUUID());
		addToEngine("AdvancedCoreUser", user);
		// addToEngine("CommandSender", player);

		for (JavascriptPlaceholderRequest request : AdvancedCorePlugin.getInstance().getJavascriptEngineRequests()) {
			addToEngine(request.getStr(), request.getObject(user.getOfflinePlayer()));
		}

		if (user.isOnline()) {
			return addPlayer(user.getPlayer());
		}
		return this;
	}

	public JavascriptEngine addPlayer(CommandSender player) {
		addToEngine("CommandSender", player);
		if (player instanceof Player) {
			Player p = (Player) player;
			addToEngine("Player", p);
			addToEngine("PlayerName", p.getName());
			addToEngine("PlayerUUID", p.getUniqueId().toString());
			addToEngine("AdvancedCoreUser", UserManager.getInstance().getUser(p));

			for (JavascriptPlaceholderRequest request : AdvancedCorePlugin.getInstance()
					.getJavascriptEngineRequests()) {
				addToEngine(request.getStr(), request.getObject(p));
			}
		} else {
			addToEngine("Player", player);
		}
		return this;
	}

	public JavascriptEngine addPlayer(OfflinePlayer player) {
		addToEngine("Player", player);
		addToEngine("PlayerName", player.getName());
		addToEngine("PlayerUUID", player.getUniqueId().toString());
		addToEngine("AdvancedCoreUser", UserManager.getInstance().getUser(player));
		addToEngine("CommandSender", player);

		for (JavascriptPlaceholderRequest request : AdvancedCorePlugin.getInstance().getJavascriptEngineRequests()) {
			addToEngine(request.getStr(), request.getObject(player));
		}

		if (player.isOnline()) {
			return addPlayer(player.getPlayer());
		}
		return this;
	}

	public JavascriptEngine addPlayer(Player player) {
		if (player != null) {
			addToEngine("Player", player);
			addToEngine("PlayerName", player.getName());
			addToEngine("PlayerUUID", player.getUniqueId().toString());
			addToEngine("AdvancedCoreUser", UserManager.getInstance().getUser(player));
			addToEngine("CommandSender", player);

			for (JavascriptPlaceholderRequest request : AdvancedCorePlugin.getInstance()
					.getJavascriptEngineRequests()) {
				addToEngine(request.getStr(), request.getObject(player));
			}
		}
		return this;
	}

	public JavascriptEngine addToEngine(HashMap<String, Object> engineAPI) {
		if (engineAPI != null && !engineAPI.isEmpty()) {
			this.engineAPI.putAll(engineAPI);
		}
		return this;
	}

	public JavascriptEngine addToEngine(String text, Object ob) {
		engineAPI.put(text, ob);
		return this;
	}

	public void execute(String expression) {
		getResult(expression);
	}

	public boolean getBooleanValue(String expression) {
		Object result = getResult(expression);
		if (result != null) {
			try {
				return ((boolean) result);
			} catch (Exception e) {
				AdvancedCorePlugin.getInstance().debug(e);
			}
		}
		return false;
	}

	public ScriptEngine getJSScriptEngine() {
		if (Double.parseDouble(System.getProperty("java.specification.version")) < 15) {
			return new ScriptEngineManager().getEngineByName("js");
		} else {
			Class<?> factory = ReflectionUtils
					.getClassForName("org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory");
			Method methodToUse = null;
			for (Method m : factory.getDeclaredMethods()) {
				if (m.getParameterCount() == 0) {
					if (m.getName().equals("getScriptEngine")) {
						methodToUse = m;
					}
				}
			}
			try {
				return (ScriptEngine) methodToUse.invoke(factory.newInstance(), new Object[] {});
			} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | InstantiationException e) {
				e.printStackTrace();
			}
			return null;
		}
	}

	public Object getResult(String expression) {
		if (!expression.equals("")) {
			ScriptEngine engine = getJSScriptEngine();
			if (engine != null) {
				engine.put("Bukkit", Bukkit.getServer());
				engine.put("AdvancedCore", AdvancedCorePlugin.getInstance());
				engine.put("Console", Bukkit.getConsoleSender());
				engine.put("UserManager", UserManager.getInstance());
				engine.put("RewardHandler", RewardHandler.getInstance());
				engine.put("StringParser", StringParser.getInstance());

				engineAPI.putAll(AdvancedCorePlugin.getInstance().getJavascriptEngine());

				for (Entry<String, Object> entry : engineAPI.entrySet()) {
					engine.put(entry.getKey(), entry.getValue());
				}

				try {
					return engine.eval(expression);
				} catch (ScriptException e) {
					AdvancedCorePlugin.getInstance().getLogger()
							.warning("Error occoured while evaluating javascript, turn debug on to see stacktrace: "
									+ e.toString());
					AdvancedCorePlugin.getInstance().debug(e);
				}
			} else {
				AdvancedCorePlugin.getInstance().debug("Failed to process javascript, engine == null");
				return null;
			}
		}
		return null;
	}

	public String getStringValue(String expression) {
		Object result = getResult(expression);
		if (result != null) {
			try {
				return result.toString();
			} catch (Exception e) {
				AdvancedCorePlugin.getInstance().debug(e);
			}
		}
		return "";
	}
}
