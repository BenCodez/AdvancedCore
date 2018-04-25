package com.Ben12345rocks.AdvancedCore.Util.Misc;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.Ben12345rocks.AdvancedCore.AdvancedCoreHook;
import com.Ben12345rocks.AdvancedCore.Objects.User;
import com.Ben12345rocks.AdvancedCore.Util.Javascript.JavascriptEngine;

import me.clip.placeholderapi.PlaceholderAPI;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;

public class StringUtils {
	/** The instance. */
	static StringUtils instance = new StringUtils();

	public static StringUtils getInstance() {
		return instance;
	}

	/** The plugin. */
	AdvancedCoreHook plugin = AdvancedCoreHook.getInstance();

	private StringUtils() {
	}

	/**
	 * Colorize.
	 *
	 * @param format
	 *            the format
	 * @return the string
	 */
	public String colorize(String format) {
		if (format == null) {
			return null;
		}
		format = format.replace("{AQUA}", "§b").replace("{BLACK}", "§0").replace("{BLUE}", "§9")
				.replace("{DARK_AQUA}", "§3").replace("{DARK_BLUE}", "§1").replace("{DARK_GRAY}", "§8")
				.replace("{DARK_GREEN}", "§2").replace("{DARK_PURPLE}", "§5").replace("{DARK_RED}", "§4")
				.replace("{GOLD}", "§6").replace("{GRAY}", "§7").replace("{GREEN}", "§a")
				.replace("{LIGHT_PURPLE}", "§d").replace("{RED}", "§c").replace("{WHITE}", "§f")
				.replace("{YELLOW}", "§e").replace("{BOLD}", "§l").replace("{ITALIC}", "§o").replace("{MAGIC}", "§k")
				.replace("{RESET}", "§r").replace("{STRIKE}", "§m").replace("{STRIKETHROUGH}", "§m")
				.replace("{UNDERLINE}", "§n");

		return ChatColor.translateAlternateColorCodes('&', format);
	}

	/**
	 * Comp to string.
	 *
	 * @param comp
	 *            the comp
	 * @return the string
	 */
	public String compToString(TextComponent comp) {
		return colorize(comp.toPlainText());
	}

	public boolean containsIgnorecase(String str1, String str2) {
		if (str1 == null || str2 == null) {
			return false;
		}
		return str1.toLowerCase().contains(str2.toLowerCase());
	}

	public boolean isDouble(String st) {
		try {
			@SuppressWarnings("unused")
			double num = Double.parseDouble(st);
			return true;

		} catch (NumberFormatException ex) {
			return false;
		}
	}

	/**
	 * Checks if is int.
	 *
	 * @param st
	 *            the st
	 * @return true, if is int
	 */
	public boolean isInt(String st) {
		try {
			@SuppressWarnings("unused")
			int num = Integer.parseInt(st);
			return true;

		} catch (NumberFormatException ex) {
			return false;
		}
	}

	public TextComponent parseJson(String msg) {
		TextComponent comp = new TextComponent("");
		if (containsIgnorecase(msg, "[Text=\"")) {
			String preMessage = "";
			String postMessage = "";

			int startIndex = msg.indexOf("[Text=\"");
			int endIndex = msg.indexOf("\"]");
			int middle = msg.indexOf("\",", startIndex);
			preMessage = msg.substring(0, startIndex);
			postMessage = msg.substring(endIndex + "\"]".length());

			String text = msg.substring(startIndex + "[Text=\"".length(), middle);
			int secondMiddle = msg.indexOf("=\"", middle);
			String type = msg.substring(middle + "\",".length(), secondMiddle);
			String typeData = msg.substring(secondMiddle + "=\"".length(), endIndex);

			comp.addExtra(parseJson(preMessage));

			TextComponent t = new TextComponent(text);
			if (type.equalsIgnoreCase("hover")) {
				t.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(typeData).create()));
			} else if (type.equalsIgnoreCase("command")) {
				t.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, typeData));
			}

			comp.addExtra(parseJson(postMessage));
		} else {
			comp.addExtra(msg);
		}
		return comp;
	}

	public String parseText(Player player, String str) {
		return parseText(player, str, null);
	}

	public String parseText(Player player, String str, HashMap<String, String> placeholders) {
		if (placeholders != null) {
			str = replacePlaceHolder(str, placeholders);
		}

		str = replacePlaceHolders(player, str);

		str = replaceJavascript(player, str);
		return colorize(str);
	}

	public String parseText(String str) {
		return parseText(str, null);
	}

	public String parseText(String str, HashMap<String, String> placeholders) {
		if (placeholders != null) {
			str = replacePlaceHolder(str, placeholders);
		}

		str = replaceJavascript(str);
		return colorize(str);
	}

	/**
	 * Replace ignore case.
	 *
	 * @param str
	 *            the str
	 * @param toReplace
	 *            the to replace
	 * @param replaceWith
	 *            the replace with
	 * @return the string
	 */
	public String replaceIgnoreCase(String str, String toReplace, String replaceWith) {
		if (str == null) {
			return "";
		}
		if ((toReplace == null) || (replaceWith == null)) {
			return str;
		}

		return Pattern.compile(toReplace, Pattern.CASE_INSENSITIVE).matcher(str).replaceAll(replaceWith);
	}

	public String replaceJavascript(CommandSender player, String text) {
		JavascriptEngine engine = new JavascriptEngine().addPlayer(player);
		return replaceJavascript(text, engine);
	}

	public String replaceJavascript(OfflinePlayer player, String text) {
		JavascriptEngine engine = new JavascriptEngine().addPlayer(player);
		return replaceJavascript(text, engine);
	}

	public String replaceJavascript(Player player, String text) {
		JavascriptEngine engine = new JavascriptEngine().addPlayer(player);
		return replaceJavascript(replacePlaceHolders(player, text), engine);
	}

	public String replaceJavascript(String text) {
		return replaceJavascript(text, null);
	}

	public String replaceJavascript(String text, JavascriptEngine engine) {
		String msg = "";
		if (containsIgnorecase(text, "[Javascript=")) {
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
					// plugin.debug(startIndex + ":" + endIndex + " from " +
					// text + " to " + str + " currently " + msg);
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
		// plugin.debug(msg);
		return msg;
	}

	public String replaceJavascript(User user, String text) {
		JavascriptEngine engine = new JavascriptEngine().addPlayer(user);
		return replaceJavascript(text, engine);
	}

	public String replacePlaceHolder(String str, HashMap<String, String> placeholders) {
		if (placeholders != null) {
			for (Entry<String, String> entry : placeholders.entrySet()) {
				str = replacePlaceHolder(str, entry.getKey(), entry.getValue());
			}
		}
		return str;
	}

	public String replacePlaceHolder(String str, HashMap<String, String> placeholders, boolean ignoreCase) {
		if (placeholders != null) {
			for (Entry<String, String> entry : placeholders.entrySet()) {
				str = replacePlaceHolder(str, entry.getKey(), entry.getValue(), ignoreCase);
			}
		}
		return str;
	}

	/**
	 * Replace place holder.
	 *
	 * @param str
	 *            the str
	 * @param toReplace
	 *            the to replace
	 * @param replaceWith
	 *            the replace with
	 * @return the string
	 */
	public String replacePlaceHolder(String str, String toReplace, String replaceWith) {
		return replacePlaceHolder(str, toReplace, replaceWith, true);
	}

	public String replacePlaceHolder(String str, String toReplace, String replaceWith, boolean ignoreCase) {
		if (ignoreCase) {
			return replaceIgnoreCase(replaceIgnoreCase(str, "%" + toReplace + "%", replaceWith),
					"\\{" + toReplace + "\\}", replaceWith);
		} else {
			str = str.replaceAll("\\{", "%");
			str = str.replaceAll("\\}", "%");
			str = str.replace("%" + toReplace + "%", replaceWith);
			return str;

		}
	}

	/**
	 * Replace place holders.
	 *
	 * @param player
	 *            the player
	 * @param text
	 *            the text
	 * @return the string
	 */
	public String replacePlaceHolders(Player player, String text) {
		if (player == null) {
			return text;
		}
		if (plugin.isPlaceHolderAPIEnabled()) {
			return PlaceholderAPI.setBracketPlaceholders(player, PlaceholderAPI.setPlaceholders(player, text));
		} else {
			return text;
		}
	}

	/**
	 * Round decimals.
	 *
	 * @param num
	 *            the num
	 * @param decimals
	 *            the decimals
	 * @return the string
	 */
	public String roundDecimals(double num, int decimals) {
		num = num * Math.pow(10, decimals);
		num = Math.round(num);
		num = num / Math.pow(10, decimals);
		DecimalFormat df = new DecimalFormat("#.00");
		return df.format(num);
	}

	/**
	 * Starts with ignore case.
	 *
	 * @param str1
	 *            the str 1
	 * @param str2
	 *            the str 2
	 * @return true, if successful
	 */
	public boolean startsWithIgnoreCase(String str1, String str2) {
		return str1.toLowerCase().startsWith(str2.toLowerCase());
	}

	/**
	 * String to comp.
	 *
	 * @param string
	 *            the string
	 * @return the text component
	 */
	public TextComponent stringToComp(String string) {
		TextComponent base = new TextComponent("");
		boolean previousLetter = false;
		ChatColor currentColor = null;
		boolean bold = false;
		boolean italic = false;
		boolean underline = false;
		boolean strike = false;
		boolean magic = false;
		String currentstring = "";
		for (int i = 0; i < string.length(); i++) {
			char c = string.charAt(i);
			if (c == '&') {
				if (string.charAt(i + 1) == 'l') {
					if (previousLetter) {
						TextComponent newTC = new TextComponent(currentstring);
						if (currentColor != null) {
							newTC.setColor(currentColor);
						}
						newTC.setBold(bold);
						newTC.setItalic(italic);
						newTC.setUnderlined(underline);
						newTC.setStrikethrough(strike);
						newTC.setObfuscated(magic);
						base.addExtra(newTC);
						bold = false;
						italic = false;
						underline = false;
						strike = false;
						magic = false;
						currentstring = "";
						currentColor = null;
						i++;
						previousLetter = false;
					} else {
						bold = true;
						i++;
					}
				} else if (string.charAt(i + 1) == 'k') {
					if (previousLetter) {
						TextComponent newTC = new TextComponent(currentstring);
						if (currentColor != null) {
							newTC.setColor(currentColor);
						}
						newTC.setBold(bold);
						newTC.setItalic(italic);
						newTC.setUnderlined(underline);
						newTC.setStrikethrough(strike);
						newTC.setObfuscated(magic);
						base.addExtra(newTC);
						bold = false;
						italic = false;
						underline = false;
						strike = false;
						magic = false;
						currentstring = "";
						currentColor = null;
						i++;
						previousLetter = false;
					} else {
						magic = true;
						i++;
					}
				} else if (string.charAt(i + 1) == 'm') {
					if (previousLetter) {
						TextComponent newTC = new TextComponent(currentstring);
						if (currentColor != null) {
							newTC.setColor(currentColor);
						}
						newTC.setBold(bold);
						newTC.setItalic(italic);
						newTC.setUnderlined(underline);
						newTC.setStrikethrough(strike);
						newTC.setObfuscated(magic);
						base.addExtra(newTC);
						bold = false;
						italic = false;
						underline = false;
						strike = false;
						magic = false;
						currentstring = "";
						currentColor = null;
						i++;
						previousLetter = false;
					} else {
						strike = true;
						i++;
					}
				} else if (string.charAt(i + 1) == 'n') {
					if (previousLetter) {
						TextComponent newTC = new TextComponent(currentstring);
						if (currentColor != null) {
							newTC.setColor(currentColor);
						}
						newTC.setBold(bold);
						newTC.setItalic(italic);
						newTC.setUnderlined(underline);
						newTC.setStrikethrough(strike);
						newTC.setObfuscated(magic);
						base.addExtra(newTC);
						bold = false;
						italic = false;
						underline = false;
						strike = false;
						magic = false;
						currentstring = "";
						currentColor = null;
						i++;
						previousLetter = false;
					} else {
						underline = true;
						i++;
					}
				} else if (string.charAt(i + 1) == 'o') {
					if (previousLetter) {
						TextComponent newTC = new TextComponent(currentstring);
						if (currentColor != null) {
							newTC.setColor(currentColor);
						}
						newTC.setBold(bold);
						newTC.setItalic(italic);
						newTC.setUnderlined(underline);
						newTC.setStrikethrough(strike);
						newTC.setObfuscated(magic);
						base.addExtra(newTC);
						bold = false;
						italic = false;
						underline = false;
						strike = false;
						magic = false;
						currentstring = "";
						currentColor = null;
						i++;
						previousLetter = false;
					} else {
						italic = true;
						i++;
					}
				} else if (string.charAt(i + 1) == 'r') {
					TextComponent newTC = new TextComponent(currentstring);
					if (currentColor != null) {
						newTC.setColor(currentColor);
					}
					newTC.setBold(bold);
					newTC.setItalic(italic);
					newTC.setUnderlined(underline);
					newTC.setStrikethrough(strike);
					newTC.setObfuscated(magic);
					base.addExtra(newTC);
					bold = false;
					italic = false;
					underline = false;
					strike = false;
					magic = false;
					currentstring = "";
					currentColor = null;
					i++;
					previousLetter = false;
				} else if (ChatColor.getByChar(string.charAt(i + 1)) != null) {
					if (previousLetter) {
						TextComponent newTC = new TextComponent(currentstring);
						if (currentColor != null) {
							newTC.setColor(currentColor);
						}
						newTC.setBold(bold);
						newTC.setItalic(italic);
						newTC.setUnderlined(underline);
						newTC.setStrikethrough(strike);
						newTC.setObfuscated(magic);
						base.addExtra(newTC);
						bold = false;
						italic = false;
						underline = false;
						strike = false;
						magic = false;
						currentColor = ChatColor.getByChar(string.charAt(i + 1));
						currentstring = "";
						i++;
						previousLetter = false;
					} else {
						currentColor = ChatColor.getByChar(string.charAt(i + 1));
						i++;
					}
				} else {
					previousLetter = true;
					currentstring = currentstring + c;
				}
			} else {
				previousLetter = true;
				currentstring = currentstring + c;
			}
		}
		TextComponent newTC = new TextComponent(currentstring);
		if (currentColor != null) {
			newTC.setColor(currentColor);
		}
		newTC.setBold(bold);
		newTC.setItalic(italic);
		newTC.setUnderlined(underline);
		newTC.setStrikethrough(strike);
		newTC.setObfuscated(magic);
		base.addExtra(newTC);
		return base;
	}

}
