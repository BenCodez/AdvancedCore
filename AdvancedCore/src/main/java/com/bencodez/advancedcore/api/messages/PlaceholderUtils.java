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
			return replaceJavascript(text, engine, user.getOfflinePlayer());
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
			return replaceJavascript(text, engine, null);
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
			return replaceJavascript(text, engine, player);
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
			return replaceJavascript(text, engine, player);
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
		String msg = replacePlaceHolders(player, text);
		if (AdvancedCorePlugin.getInstance().getOptions().isJavascriptEngineEnabled()) {
			JavascriptEngine engine = new JavascriptEngine().addPlayer(player);
			return replaceJavascript(msg, engine, player);
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
			return replaceJavascript(text, engine, player);
		}
		return text;
	}

	public static String replaceJavascript(String text) {
		return replaceJavascript(text, null);
	}

	public static String replaceJavascript(String text, JavascriptEngine engine) {
		return replaceJavascript(text, engine, null);
	}

	private static String replaceJavascript(String text, JavascriptEngine engine, OfflinePlayer player) {
		String msg = "";
		if (MessageAPI.containsIgnorecase(text, "[Javascript=")) {
			if (engine == null) {
				engine = new JavascriptEngine();
			}
			int lastIndex = 0;
			int startIndex = 0;
			int num = 0;
			while (startIndex != -1) {
				startIndex = text.indexOf("[Javascript=", lastIndex);

				int endIndex = -1;
				if (startIndex != -1) {
					if (num != 0) {
						msg += text.substring(lastIndex + 1, startIndex);
					} else {
						msg += text.substring(lastIndex, startIndex);
					}
					num++;
					endIndex = text.indexOf("]", startIndex);
					String str = text.substring(startIndex + "[Javascript=".length(), endIndex);
					String script = engine.getStringValue(str);
					if (script == null) {
						script = "" + engine.getBooleanValue(str);

					}

					if (script != null) {
						msg += script;
					}
					lastIndex = endIndex;
				}

			}
			msg += text.substring(lastIndex + 1);

		} else {
			msg = text;
		}
		return msg;
	}

	public static ArrayList<String> replacePlaceHolder(ArrayList<String> list, HashMap<String, String> placeholders) {
		ArrayList<String> newList = new ArrayList<>();
		for (int i = 0; i < list.size(); i++) {
			newList.add(replacePlaceHolder(list.get(i), placeholders));
		}
		return newList;
	}

	public static String replacePlaceHolder(String str, HashMap<String, String> placeholders) {
		if (placeholders == null) {
			return str;
		}
		return transformJavascriptMarkers(str, value -> replacePlaceHolderMapRaw(value, placeholders, true),
				value -> replacePlaceHolderMapEncoded(value, placeholders, true));
	}

	public static String replacePlaceHolder(String str, HashMap<String, String> placeholders, boolean ignoreCase) {
		if (placeholders == null) {
			return str;
		}
		return transformJavascriptMarkers(str, value -> replacePlaceHolderMapRaw(value, placeholders, ignoreCase),
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
		return transformJavascriptMarkers(str,
				value -> replacePlaceHolderRaw(value, toReplace, replaceWith, ignoreCase),
				value -> replacePlaceHolderRaw(value, toReplace, JavascriptPlaceholderValue.encode(replaceWith), ignoreCase));
	}

	private static String replacePlaceHolderMapRaw(String str, HashMap<String, String> placeholders, boolean ignoreCase) {
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
		if (ignoreCase) {
			return MessageAPI.replaceIgnoreCase(MessageAPI.replaceIgnoreCase(str, "%" + toReplace + "%", replaceWith),
					"\\{" + toReplace + "\\}", replaceWith);
		}
		str = str.replaceAll("\\{", "%");
		str = str.replaceAll("\\}", "%");
		str = str.replace("%" + toReplace + "%", replaceWith);
		return str;
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
		if (player == null) {
			return text;
		}
		if (AdvancedCorePlugin.getInstance().isPlaceHolderAPIEnabled()) {
			return transformJavascriptMarkers(text, value -> PlaceholderAPI.setPlaceholders(player, value),
					Function.identity());
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

	private static String transformJavascriptMarkers(String text, Function<String, String> outsideTransform,
			Function<String, String> insideTransform) {
		if (text == null || text.isEmpty()) {
			return text;
		}
		StringBuilder result = new StringBuilder(text.length());
		int cursor = 0;
		while (cursor < text.length()) {
			int start = indexOfIgnoreCase(text, "[Javascript=", cursor);
			if (start < 0) {
				result.append(neutralizeJavascriptMarkers(outsideTransform.apply(text.substring(cursor))));
				break;
			}
			int end = text.indexOf(']', start);
			if (end < 0) {
				result.append(neutralizeJavascriptMarkers(outsideTransform.apply(text.substring(cursor))));
				break;
			}
			result.append(neutralizeJavascriptMarkers(outsideTransform.apply(text.substring(cursor, start))));
			int bodyStart = start + "[Javascript=".length();
			result.append(text, start, bodyStart);
			result.append(insideTransform.apply(text.substring(bodyStart, end)));
			result.append(']');
			cursor = end + 1;
		}
		return result.toString();
	}

	private static String neutralizeJavascriptMarkers(String text) {
		StringBuilder result = new StringBuilder(text.length());
		int cursor = 0;
		while (cursor < text.length()) {
			int start = indexOfIgnoreCase(text, "[Javascript=", cursor);
			if (start < 0) {
				result.append(text.substring(cursor));
				break;
			}
			result.append(text, cursor, start).append("[Javascript =");
			cursor = start + "[Javascript=".length();
		}
		return result.toString();
	}

	private static int indexOfIgnoreCase(String text, String target, int fromIndex) {
		int max = text.length() - target.length();
		for (int i = Math.max(0, fromIndex); i <= max; i++) {
			if (text.regionMatches(true, i, target, 0, target.length())) {
				return i;
			}
		}
		return -1;
	}

}
