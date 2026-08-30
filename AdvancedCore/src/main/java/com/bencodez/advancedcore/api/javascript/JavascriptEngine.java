package com.bencodez.advancedcore.api.javascript;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import javax.script.ScriptEngine;
import javax.script.ScriptException;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.simpleapi.messages.MessageAPI;

public class JavascriptEngine {
	private final HashMap<String, Object> engineAPI;
	private final HashMap<String, String> placeholders;
	private OfflinePlayer placeholderPlayer;

	public JavascriptEngine() {
		engineAPI = new HashMap<>();
		placeholders = new HashMap<>();
	}

	public JavascriptEngine addPlayer(AdvancedCoreUser user) {
		placeholderPlayer = user.getOfflinePlayer();
		addToEngine("PlayerName", user.getPlayerName());
		addToEngine("PlayerUUID", user.getUUID());
		addToEngine("AdvancedCoreUser", user);

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
			placeholderPlayer = p;
			addToEngine("Player", p);
			addToEngine("PlayerName", p.getName());
			addToEngine("PlayerUUID", p.getUniqueId().toString());
			addToEngine("AdvancedCoreUser", AdvancedCorePlugin.getInstance().getUserManager().getUser(p));

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
		placeholderPlayer = player;
		addToEngine("Player", player);
		addToEngine("PlayerName", player.getName());
		addToEngine("PlayerUUID", player.getUniqueId().toString());
		addToEngine("AdvancedCoreUser", AdvancedCorePlugin.getInstance().getUserManager().getUser(player));
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
			placeholderPlayer = player;
			addToEngine("Player", player);
			addToEngine("PlayerName", player.getName());
			addToEngine("PlayerUUID", player.getUniqueId().toString());
			addToEngine("AdvancedCoreUser", AdvancedCorePlugin.getInstance().getUserManager().getUser(player));
			addToEngine("CommandSender", player);

			for (JavascriptPlaceholderRequest request : AdvancedCorePlugin.getInstance()
					.getJavascriptEngineRequests()) {
				addToEngine(request.getStr(), request.getObject(player));
			}
		}
		return this;
	}

	public JavascriptEngine addPlaceholders(Map<String, String> placeholders) {
		if (placeholders != null && !placeholders.isEmpty()) {
			this.placeholders.putAll(placeholders);
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

	public Object getResult(String expression) {
		if (expression != null && !expression.isEmpty()) {
			if (!AdvancedCorePlugin.getInstance().getOptions().isJavascriptEngineEnabled()) {
				return null;
			}
			ScriptEngine engine = JavascriptEngineHandler.getInstance().getJSScriptEngine();
			if (engine == null) {
				AdvancedCorePlugin.getInstance().debug("Failed to process javascript, engine == null");
				return null;
			}

			HashMap<String, Object> placeholderBindings = new HashMap<>();
			String preparedExpression;
			try {
				preparedExpression = JavascriptPlaceholderBinder.bind(expression, placeholderPlayer, placeholders,
						placeholderBindings);
			} catch (IllegalArgumentException e) {
				AdvancedCorePlugin.getInstance().getLogger()
						.warning("Failed to safely prepare javascript placeholders: " + e.getMessage());
				AdvancedCorePlugin.getInstance().debug(e);
				return null;
			}

			HashMap<String, Object> evaluationBindings = new HashMap<>();
			evaluationBindings.put("Bukkit", Bukkit.getServer());
			evaluationBindings.put("AdvancedCore", AdvancedCorePlugin.getInstance());
			evaluationBindings.put("Console", Bukkit.getConsoleSender());
			evaluationBindings.put("UserManager", AdvancedCorePlugin.getInstance().getUserManager());
			evaluationBindings.put("RewardHandler", AdvancedCorePlugin.getInstance().getRewardHandler());
			evaluationBindings.put("MessageAPI", MessageAPI.class);
			evaluationBindings.putAll(engineAPI);
			evaluationBindings.putAll(placeholderBindings);
			evaluationBindings.putAll(AdvancedCorePlugin.getInstance().getJavascriptEngine());

			try {
				return evaluateWithBindings(engine, preparedExpression, evaluationBindings);
			} catch (ScriptException e) {
				AdvancedCorePlugin.getInstance().getLogger().warning(
						"Error occoured while evaluating javascript, turn debug on to see stacktrace: " + e.toString());
				AdvancedCorePlugin.getInstance().debug(e);
			}
		}
		return null;
	}

	/**
	 * The configured engine is cached and shared by every JavascriptEngine wrapper.
	 * Keep binding writes and evaluation under the same lock so concurrent rewards
	 * cannot observe or overwrite one another's per-evaluation values.
	 */
	static Object evaluateWithBindings(ScriptEngine engine, String expression, Map<String, Object> bindings)
			throws ScriptException {
		synchronized (engine) {
			for (Entry<String, Object> entry : bindings.entrySet()) {
				engine.put(entry.getKey(), entry.getValue());
			}
			return engine.eval(expression);
		}
	}

	public String getStringValue(String expression) {
		try {
			Object result = getResult(expression);
			if (result != null) {
				return result.toString();
			}
		} catch (Exception e) {
			AdvancedCorePlugin.getInstance().debug(e);
		}

		return "";
	}
}
