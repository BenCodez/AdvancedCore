package com.bencodez.advancedcore.api.messages;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.function.Function;

import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.javascript.JavascriptEngine;
import com.bencodez.advancedcore.api.javascript.JavascriptPlaceholderValue;
import com.bencodez.advancedcore.api.javascript.JavascriptTextTemplate;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.simpleapi.messages.MessageAPI;

import me.clip.placeholderapi.PlaceholderAPI;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;

public class PlaceholderUtils {
	@SuppressWarnings("deprecation")
	public static TextComponent parseJson(String msg) {
		TextComponent comp = new TextComponent("");
		if (MessageAPI.contains(msg, "[Text=\"")) {
			String preMessage = "";
			String postMessage = "";

			int startIndex = msg.indexOf("[Text=\"");
			int endIndex = msg.indexOf("\"]");
			int middle = msg.indexOf("\",", startIndex);
			preMessage = msg.substring(0, startIndex);
			postMessage = msg.substring(endIndex + "\"]".length());

			int postText = startIndex + "[Text=\"".length();

			String text = MessageAPI.colorize(msg.substring(postText, middle));

			TextComponent t = new TextComponent(text);

			String typeMsg = msg;
			// types
			boolean parsing = true;
			while (parsing) {
				int nextTypeIndex = typeMsg.indexOf("\",");
				int typeMiddle = typeMsg.indexOf("=\"", nextTypeIndex);
				String type = typeMsg.substring(nextTypeIndex + "\",".length(), typeMiddle);
				int typeEndIndex = typeMsg.indexOf("\",", typeMiddle);
				int endIndex1 = typeMsg.indexOf("\"]");

				if (typeEndIndex == -1 || typeEndIndex > endIndex1) {
					typeEndIndex = endIndex1;
					parsing = false;
				}
				String typeData = typeMsg.substring(typeMiddle + "=\"".length(), typeEndIndex);
				if (parsing) {
					typeMsg = typeMsg.substring(typeEndIndex);
				}

				if (type.equalsIgnoreCase("hover")) {
					BaseComponent[] hoverContent = TextComponent.fromLegacyText(typeData);
					t.setHoverEvent(MessageAPI.getHoverEventSupport().createHoverEvent(hoverContent));
				} else if (type.equalsIgnoreCase("command")) {
					t.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, typeData));
				} else if (type.equalsIgnoreCase("url")) {
					t.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, typeData));
				} else if (type.equalsIgnoreCase("suggest_command")) {
					t.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, typeData));
				}

			}
			/*
			 * int secondMiddle = msg.indexOf("=\"", middle); String type =
			 * msg.substring(middle + "\",".length(), secondMiddle); String typeData =
			 * msg.substring(secondMiddle + "=\"".length(), endIndex);
			 */

			comp.addExtra(parseJson(preMessage));

			comp.addExtra(t);

			comp.addExtra(parseJson(postMessage));
		} else {

			comp.addExtra(new TextComponent(TextComponent.fromLegacyText(msg)));
		}
		return comp;
	}

	public static String parseText(Player player, String str) {
		return parseText(player, str, null);
	}

	public static String parseText(Player player, String str, HashMap<String, String> placeholders) {
		if (placeholders != null) {
			str = replacePlaceHolder(str, placeholders);
		}

		str = replacePlaceHolders(player, str);

		str = replaceJavascript(player, str);
		return MessageAPI.colorize(str);
	}

	public static String parseText(String str) {
		return parseText(str, null);
	}

	public static String parseText(String str, HashMap<String, String> placeholders) {
		if (placeholders != null) {
			str = replacePlaceHolder(str, placeholders);
		}

		str = replaceJavascript(str);
		return MessageAPI.colorize(str);
	}

	public static ArrayList<String> replaceJavascript(AdvancedCoreUser user, ArrayList<String> list) {
		ArrayList<String> msg = new ArrayList<>();
		for (String str : list) {
			msg.add(replaceJavascript(user, str));
		}
		return msg;
	}

	public static String replaceJavascript(AdvancedCoreUser user, String text) {
		if (user.getPlugin().getOptions().isJavascriptEngineEnabled()) {
			JavascriptEngine engine = new JavascriptEngine().addPlayer(user);
			return replaceJavascript(text, engine);
		}
		return text;
	}

	public static ArrayList<String> replaceJavascript(ArrayList<String> list) {
		return replaceJavascript(list, null);
	}

	public static ArrayList<String> replaceJavascript(ArrayList<String> list, JavascriptEngine engine) {
		ArrayList<String> msg = new ArrayList<>();
		for (String str : list) {
			msg.add(replaceJavascript(str, engine));
		}
		return msg;
	}

	public static ArrayList<String> replaceJavascript(CommandSender sender, ArrayList<String> list) {
		ArrayList<String> msg = new ArrayList<>();
		for (String str : list) {
			msg.add(replaceJavascript(sender, str));
		}
		return msg;
	}

	public static String replaceJavascript(CommandSender player, String text) {
		if (AdvancedCorePlugin.getInstance().getOptions().isJavascriptEngineEnabled()) {
			if (player instanceof Player) {
				return replaceJavascript((Player) player, text);
			}
			JavascriptEngine engine = new JavascriptEngine().addPlayer(player);
			return replaceJavascript(text, engine);
		}
		return text;
	}

	public static ArrayList<String> replaceJavascript(OfflinePlayer player, ArrayList<String> list) {
		ArrayList<String> msg = new ArrayList<>();
		for (String str : list) {
			msg.add(replaceJavascript(player, str));
		}
		return msg;
	}

	public static String replaceJavascript(OfflinePlayer player, String text) {
		if (AdvancedCorePlugin.getInstance().getOptions().isJavascriptEngineEnabled()) {
			if (player.isOnline()) {
				return replaceJavascript(player.getPlayer(), text);
			}
			JavascriptEngine engine = new JavascriptEngine().addPlayer(player);
			return replaceJavascript(text, engine);
		}
		return text;
	}

	public static ArrayList<String> replaceJavascriptOnly(OfflinePlayer player, ArrayList<String> list) {
		ArrayList<String> msg = new ArrayList<>();
		for (String str : list) {
			msg.add(replaceJavascriptOnly(player, str));
		}
		return msg;
	}

	public static String replaceJavascriptOnly(OfflinePlayer player, String text) {
		if (AdvancedCorePlugin.getInstance().getOptions().isJavascriptEngineEnabled()) {
			if (player.isOnline()) {
				return replaceJavascriptOnly(player.getPlayer(), text);
			}
			JavascriptEngine engine = new JavascriptEngine().addPlayer(player);
			return replaceJavascript(text, engine);
		}
		return text;
	}

	public static ArrayList<String> replaceJavascript(Player player, ArrayList<String> list) {
		ArrayList<String> msg = new ArrayList<>();
		for (String str : list) {
			msg.add(replaceJavascript(player, str));
		}
		return msg;
	}

	public static String replaceJavascript(Player player, String text) {
		boolean javascriptEnabled = AdvancedCorePlugin.getInstance().getOptions().isJavascriptEngineEnabled();
		String msg = replacePlaceHolders(player, text, !javascriptEnabled);
		if (javascriptEnabled) {
			JavascriptEngine engine = new JavascriptEngine().addPlayer(player);
			return replaceJavascript(msg, engine);
		}
		return msg;
	}

	public static ArrayList<String> replaceJavascriptOnly(Player player, ArrayList<String> list) {
		ArrayList<String> msg = new ArrayList<>();
		for (String str : list) {
			msg.add(replaceJavascriptOnly(player, str));
		}
		return msg;
	}

	public static String replaceJavascriptOnly(Player player, String text) {
		if (AdvancedCorePlugin.getInstance().getOptions().isJavascriptEngineEnabled()) {
			JavascriptEngine engine = new JavascriptEngine().addPlayer(player);
			return replaceJavascript(text, engine);
		}
		return text;
	}

	public static String replaceJavascript(String text) {
		return replaceJavascript(text, null);
	}

	public static String replaceJavascript(String text, JavascriptEngine engine) {
		if (text == null || text.isEmpty()) {
			return text;
		}
		JavascriptEngine activeEngine = engine == null ? new JavascriptEngine() : engine;
		return JavascriptTextTemplate.parse(text).evaluate(Function.identity(), activeEngine::getStringValue);
	}

	public static ArrayList<String> replacePlaceHolder(ArrayList<String> list, HashMap<String, String> placeholders) {
		ArrayList<String> newList = new ArrayList<>();
		for (int i = 0; i < list.size(); i++) {
			newList.add(replacePlaceHolder(list.get(i), placeholders));
		}
		return newList;
	}

	public static String replacePlaceHolder(String str, HashMap<String, String> placeholders) {
		return replacePlaceHolder(str, placeholders, true);
	}

	public static String replacePlaceHolder(String str, HashMap<String, String> placeholders, boolean ignoreCase) {
		if (str == null || placeholders == null || placeholders.isEmpty()) {
			return str;
		}
		return JavascriptTextTemplate.parse(str).transform(
				value -> replacePlaceHolderMapRaw(value, placeholders, ignoreCase),
				value -> replacePlaceHolderMapEncoded(value, placeholders, ignoreCase));
	}

	/**
	 * Replace place holder.
	 *
	 * @param str         the str
	 * @param toReplace   the to replace
	 * @param replaceWith the replace with
	 * @return the string
	 */
	public static String replacePlaceHolder(String str, String toReplace, String replaceWith) {
		return replacePlaceHolder(str, toReplace, replaceWith, true);
	}

	public static String replacePlaceHolder(String str, String toReplace, String replaceWith, boolean ignoreCase) {
		if (str == null) {
			return null;
		}
		return JavascriptTextTemplate.parse(str).transform(
				value -> replacePlaceHolderRaw(value, toReplace, replaceWith, ignoreCase),
				value -> replacePlaceHolderRaw(value, toReplace, JavascriptPlaceholderValue.encode(replaceWith),
						ignoreCase));
	}

	private static String replacePlaceHolderMapRaw(String str, HashMap<String, String> placeholders,
			boolean ignoreCase) {
		String result = str;
		for (Entry<String, String> entry : placeholders.entrySet()) {
			result = replacePlaceHolderRaw(result, entry.getKey(), entry.getValue(), ignoreCase);
		}
		return result;
	}

	private static String replacePlaceHolderMapEncoded(String str, HashMap<String, String> placeholders,
			boolean ignoreCase) {
		String result = str;
		for (Entry<String, String> entry : placeholders.entrySet()) {
			result = replacePlaceHolderRaw(result, entry.getKey(), JavascriptPlaceholderValue.encode(entry.getValue()),
					ignoreCase);
		}
		return result;
	}

	private static String replacePlaceHolderRaw(String str, String toReplace, String replaceWith, boolean ignoreCase) {
		String safeReplacement = replaceWith == null ? "" : replaceWith;
		if (ignoreCase) {
			return MessageAPI.replaceIgnoreCase(
					MessageAPI.replaceIgnoreCase(str, "%" + toReplace + "%", safeReplacement),
					"\\{" + toReplace + "\\}", safeReplacement);
		}
		str = str.replaceAll("\\{", "%");
		str = str.replaceAll("\\}", "%");
		return str.replace("%" + toReplace + "%", safeReplacement);
	}

	public static ArrayList<String> replacePlaceHolders(ArrayList<String> list, Player p) {
		ArrayList<String> newList = new ArrayList<>();
		for (int i = 0; i < list.size(); i++) {
			newList.add(replacePlaceHolders(p, list.get(i)));
		}
		return newList;
	}

	public static ArrayList<String> replacePlaceHolders(OfflinePlayer player, ArrayList<String> list) {
		ArrayList<String> newList = new ArrayList<>();
		for (int i = 0; i < list.size(); i++) {
			newList.add(replacePlaceHolders(player, list.get(i)));
		}
		return newList;
	}

	public static String replacePlaceHolders(OfflinePlayer player, String text) {
		return replacePlaceHolders(player, text, false);
	}

	private static String replacePlaceHolders(OfflinePlayer player, String text,
			boolean replaceJavascriptSegments) {
		if (player == null || text == null || text.isEmpty()) {
			return text;
		}
		if (AdvancedCorePlugin.getInstance().isPlaceHolderAPIEnabled()) {
			Function<String, String> replacement = value -> PlaceholderAPI.setPlaceholders(player, value);
			Function<String, String> javascriptReplacement = replaceJavascriptSegments
					? value -> JavascriptTextTemplate.neutralizeGeneratedMarkers(replacement.apply(value))
					: Function.identity();
			return JavascriptTextTemplate.parse(text).transform(
					replacement, javascriptReplacement);
		}
		return text;
	}

	/**
	 * Replace place holders.
	 *
	 * @param player the player
	 * @param text   the text
	 * @return the string
	 */
	public static String replacePlaceHolders(Player player, String text) {
		return replacePlaceHolders((OfflinePlayer) player, text);
	}

}
