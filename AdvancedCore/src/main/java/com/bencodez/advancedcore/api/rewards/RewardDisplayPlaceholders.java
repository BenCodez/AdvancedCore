package com.bencodez.advancedcore.api.rewards;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import com.bencodez.advancedcore.api.javascript.JavascriptPlaceholderValue;
import com.bencodez.advancedcore.api.javascript.JavascriptTextTemplate;
import com.bencodez.advancedcore.api.messages.PlaceholderUtils;
import com.bencodez.simpleapi.messages.MessageAPI;

/**
 * Stores optional display-only reward placeholder values alongside the raw
 * values used by commands and other exact-value actions.
 *
 * <p>The reserved entries deliberately live in the ordinary placeholder map so
 * delayed and offline reward serialization preserves them.</p>
 */
public final class RewardDisplayPlaceholders {
	private static final String PREFIX = "__AdvancedCoreDisplayPlaceholder__";
	private static final String MANIFEST_KEY = PREFIX + "manifest:";
	private static final String MANIFEST_VALUE = "v1";
	private static final String VALUE_PREFIX = PREFIX + "value:";
	private static final Pattern MESSAGE_ATTRIBUTE = Pattern.compile(
			"(?i)(command|url|suggest_command|hover)\\s*=\\s*\"([^\"]*)\"");

	private RewardDisplayPlaceholders() {
	}

	/** Adds a display-only value without replacing the raw action value. */
	public static void put(HashMap<String, String> placeholders, String key, String value) {
		if (placeholders == null || key == null) return;
		placeholders.put(MANIFEST_KEY, MANIFEST_VALUE);
		placeholders.keySet().removeIf(existing -> isEncodedOverrideFor(existing, key));
		placeholders.put(VALUE_PREFIX + encode(key), encode(value == null ? "" : value));
	}

	/** Returns whether the serialized placeholder map contains display overrides. */
	public static boolean hasDisplayValues(HashMap<String, String> placeholders) {
		return placeholders != null && MANIFEST_VALUE.equals(placeholders.get(MANIFEST_KEY));
	}

	/**
	 * Returns an isolated placeholder map for formatted output. Display-specific
	 * values replace their raw counterparts and internal storage keys are removed.
	 */
	public static HashMap<String, String> forDisplay(HashMap<String, String> placeholders) {
		if (placeholders == null) return new HashMap<>();
		boolean hasDisplayValues = hasDisplayValues(placeholders);
		if (!hasDisplayValues) return placeholders;
		HashMap<String, String> display = new HashMap<>();
		for (Map.Entry<String, String> entry : placeholders.entrySet()) {
			String key = entry.getKey();
			if (!hasDisplayValues || key == null
					|| (!key.equals(MANIFEST_KEY) && !key.startsWith(VALUE_PREFIX))) {
				display.put(key, entry.getValue());
			}
		}
		if (hasDisplayValues) {
			for (Map.Entry<String, String> entry : displayOverrides(placeholders).entrySet()) {
				display.keySet().removeIf(existing -> existing != null && existing.equalsIgnoreCase(entry.getKey()));
				display.put(entry.getKey(), entry.getValue());
			}
		}
		return display;
	}

	/**
	 * Renders a configured interactive message with display values in visible text
	 * and raw values in exact click-action attributes.
	 */
	public static String replaceFormattedMessage(String template, HashMap<String, String> placeholders) {
		return replaceFormattedMessage(null, template, placeholders);
	}

	/** Player-aware form that completes visible-text rendering before JSON parsing. */
	public static String replaceFormattedMessage(Player player, String template,
			HashMap<String, String> placeholders) {
		if (template == null || !hasDisplayValues(placeholders)) {
			return PlaceholderUtils.replacePlaceHolder(template, placeholders);
		}
		return renderInteractive(template, value -> renderVisible(player, value, placeholders),
				value -> renderExact(player, value, placeholders));
	}

	/**
	 * Applies configured JavaScript while keeping display placeholder values inert
	 * until evaluation is complete. The caller remains responsible for any
	 * sink-specific color processing.
	 */
	public static String replaceFormattedJavascript(OfflinePlayer player, String template,
			HashMap<String, String> placeholders) {
		if (template == null || placeholders == null || placeholders.isEmpty()) return template;
		VisibleValues values = visibleValues(placeholders);
		HashMap<String, String> textTokens = new HashMap<>(values.overrides().size());
		HashMap<String, String> javascriptValues = new HashMap<>(values.overrides().size());
		HashMap<String, String> tokenValues = new HashMap<>(values.overrides().size());
		int index = 0;
		for (Map.Entry<String, String> entry : values.overrides().entrySet()) {
			String token;
			do {
				token = "\uE000AdvancedCoreDisplay" + index++ + "\uE001";
			} while (template.contains(token));
			String inert = neutralizeInteractiveSyntax(entry.getValue());
			textTokens.put(entry.getKey(), token);
			javascriptValues.put(entry.getKey(), JavascriptPlaceholderValue.encode("\u2060" + inert + "\u2060"));
			tokenValues.put(token, inert);
		}
		String protectedValue = JavascriptTextTemplate.parse(template).transform(
				text -> PlaceholderUtils.replacePlaceHolder(
						PlaceholderUtils.replacePlaceHolder(text, textTokens), values.ordinary()),
				javascript -> PlaceholderUtils.replacePlaceHolder(
						PlaceholderUtils.replacePlaceHolder(javascript, javascriptValues), values.ordinary()));
		String rendered = player == null ? PlaceholderUtils.replaceJavascript(protectedValue)
				: PlaceholderUtils.replaceJavascript(player, protectedValue);
		for (Map.Entry<String, String> entry : tokenValues.entrySet()) {
			rendered = replaceInertToken(rendered, entry.getKey(), entry.getValue());
		}
		return rendered;
	}

	/** Renders the legacy two-stage broadcast path without enabling JavaScript. */
	public static String replaceFormattedBroadcast(Player triggeringPlayer, Player recipient, String template,
			HashMap<String, String> placeholders) {
		if (template == null || !hasDisplayValues(placeholders)) {
			return PlaceholderUtils.replacePlaceHolder(template, placeholders);
		}
		return renderInteractive(template,
				value -> renderBroadcastVisible(triggeringPlayer, recipient, value, placeholders),
				value -> renderBroadcastExact(triggeringPlayer, recipient, value, placeholders));
	}

	private static String renderInteractive(String template, Function<String, String> visibleRenderer,
			Function<String, String> exactRenderer) {
		StringBuilder rendered = new StringBuilder(template.length());
		int cursor = 0;
		while (cursor < template.length()) {
			int componentStart = indexOfIgnoreCase(template, "[Text=\"", cursor);
			if (componentStart < 0) {
				rendered.append(visibleRenderer.apply(template.substring(cursor)));
				break;
			}
			rendered.append(visibleRenderer.apply(template.substring(cursor, componentStart)));
			int componentEnd = template.indexOf("\"]", componentStart);
			if (componentEnd < 0) {
				rendered.append(visibleRenderer.apply(template.substring(componentStart)));
				break;
			}
			componentEnd += 2;
			rendered.append(renderInteractiveComponent(template.substring(componentStart, componentEnd),
					visibleRenderer, exactRenderer));
			cursor = componentEnd;
		}
		return rendered.toString();
	}

	private static String renderInteractiveComponent(String component, Function<String, String> visibleRenderer,
			Function<String, String> exactRenderer) {
		int visibleStart = "[Text=\"".length();
		int attributesStart = component.indexOf("\",");
		int visibleEnd = attributesStart >= 0 ? attributesStart : component.length() - "\"]".length();
		StringBuilder rendered = new StringBuilder(component.length());
		rendered.append(component, 0, visibleStart);
		rendered.append(visibleRenderer.apply(component.substring(visibleStart, visibleEnd)));
		if (attributesStart < 0) {
			rendered.append(component.substring(visibleEnd));
			return rendered.toString();
		}

		rendered.append(component, attributesStart, attributesStart + 2);
		Matcher matcher = MESSAGE_ATTRIBUTE.matcher(component);
		matcher.region(attributesStart + 2, component.length());
		int cursor = attributesStart + 2;
		while (matcher.find()) {
			rendered.append(component, cursor, matcher.start(2));
			String type = matcher.group(1);
			rendered.append(isExactAttribute(type) ? exactRenderer.apply(matcher.group(2))
					: visibleRenderer.apply(matcher.group(2)));
			cursor = matcher.end(2);
		}
		rendered.append(component.substring(cursor));
		return rendered.toString();
	}

	private static boolean isExactAttribute(String type) {
		return type.equalsIgnoreCase("command") || type.equalsIgnoreCase("url")
				|| type.equalsIgnoreCase("suggest_command");
	}

	private static int indexOfIgnoreCase(String text, String target, int fromIndex) {
		for (int index = Math.max(0, fromIndex); index <= text.length() - target.length(); index++) {
			if (text.regionMatches(true, index, target, 0, target.length())) return index;
		}
		return -1;
	}

	private static String renderVisible(Player player, String value, HashMap<String, String> placeholders) {
		VisibleValues values = visibleValues(placeholders);
		HashMap<String, String> textTokens = new HashMap<>(values.overrides().size());
		HashMap<String, String> javascriptValues = new HashMap<>(values.overrides().size());
		HashMap<String, String> tokenValues = new HashMap<>(values.overrides().size());
		int index = 0;
		for (Map.Entry<String, String> entry : values.overrides().entrySet()) {
			String token;
			do {
				token = "\uE000AdvancedCoreDisplay" + index++ + "\uE001";
			} while (value.contains(token));
			String inert = neutralizeInteractiveSyntax(entry.getValue());
			textTokens.put(entry.getKey(), token);
			javascriptValues.put(entry.getKey(), JavascriptPlaceholderValue.encode("\u2060" + inert + "\u2060"));
			tokenValues.put(token, inert);
		}
		String protectedValue = JavascriptTextTemplate.parse(value).transform(
				text -> PlaceholderUtils.replacePlaceHolder(
						PlaceholderUtils.replacePlaceHolder(text, textTokens), values.ordinary()),
				javascript -> PlaceholderUtils.replacePlaceHolder(
						PlaceholderUtils.replacePlaceHolder(javascript, javascriptValues), values.ordinary()));
		String rendered = player == null ? PlaceholderUtils.parseText(protectedValue)
				: PlaceholderUtils.parseText(player, protectedValue);
		for (Map.Entry<String, String> entry : tokenValues.entrySet()) {
			rendered = replaceInertToken(rendered, entry.getKey(), entry.getValue());
		}
		return rendered;
	}

	private static String renderExact(Player player, String value, HashMap<String, String> placeholders) {
		VisibleValues values = exactValues(placeholders);
		HashMap<String, String> textTokens = new HashMap<>(values.overrides().size());
		HashMap<String, String> javascriptValues = new HashMap<>(values.overrides().size());
		HashMap<String, String> tokenValues = new HashMap<>(values.overrides().size());
		int index = 0;
		for (Map.Entry<String, String> entry : values.overrides().entrySet()) {
			String token;
			do {
				token = "\uE000AdvancedCoreExact" + index++ + "\uE001";
			} while (value.contains(token));
			String raw = entry.getValue() == null ? "" : entry.getValue();
			textTokens.put(entry.getKey(), token);
			javascriptValues.put(entry.getKey(), JavascriptPlaceholderValue.encode(raw));
			tokenValues.put(token, raw);
		}
		String protectedValue = JavascriptTextTemplate.parse(value).transform(
				text -> PlaceholderUtils.replacePlaceHolder(
						PlaceholderUtils.replacePlaceHolder(text, textTokens), values.ordinary()),
				javascript -> PlaceholderUtils.replacePlaceHolder(
						PlaceholderUtils.replacePlaceHolder(javascript, javascriptValues), values.ordinary()));
		String rendered = player == null ? PlaceholderUtils.parseText(protectedValue)
				: PlaceholderUtils.parseText(player, protectedValue);
		for (Map.Entry<String, String> entry : tokenValues.entrySet()) {
			rendered = rendered.replace(entry.getKey(), entry.getValue());
		}
		return rendered;
	}

	private static String renderBroadcastVisible(Player triggeringPlayer, Player recipient, String value,
			HashMap<String, String> placeholders) {
		VisibleValues values = visibleValues(placeholders);
		HashMap<String, String> tokens = new HashMap<>(values.overrides().size());
		HashMap<String, String> tokenValues = new HashMap<>(values.overrides().size());
		int index = 0;
		for (Map.Entry<String, String> entry : values.overrides().entrySet()) {
			String token;
			do {
				token = "\uE000AdvancedCoreDisplay" + index++ + "\uE001";
			} while (value.contains(token));
			tokens.put(entry.getKey(), token);
			tokenValues.put(token, neutralizeInteractiveSyntax(entry.getValue()));
		}
		String rendered = PlaceholderUtils.replacePlaceHolder(
				PlaceholderUtils.replacePlaceHolder(value, tokens), values.ordinary());
		if (triggeringPlayer != null) rendered = PlaceholderUtils.replacePlaceHolders(triggeringPlayer, rendered);
		if (recipient != null) rendered = PlaceholderUtils.replacePlaceHolders(recipient, rendered);
		rendered = MessageAPI.colorize(rendered);
		for (Map.Entry<String, String> entry : tokenValues.entrySet()) {
			rendered = replaceInertToken(rendered, entry.getKey(), entry.getValue());
		}
		return rendered;
	}

	private static VisibleValues visibleValues(HashMap<String, String> placeholders) {
		if (!hasDisplayValues(placeholders)) {
			return new VisibleValues(new HashMap<>(), new HashMap<>(placeholders));
		}
		HashMap<String, String> overrides = displayOverrides(placeholders);
		HashMap<String, String> ordinary = new HashMap<>();
		for (Map.Entry<String, String> entry : placeholders.entrySet()) {
			String key = entry.getKey();
			if (key == null || key.equals(MANIFEST_KEY) || key.startsWith(VALUE_PREFIX)) continue;
			if (overrides.keySet().stream().noneMatch(override -> override.equalsIgnoreCase(key))) {
				ordinary.put(key, entry.getValue());
			}
		}
		return new VisibleValues(overrides, ordinary);
	}

	private static VisibleValues exactValues(HashMap<String, String> placeholders) {
		if (!hasDisplayValues(placeholders)) return new VisibleValues(new HashMap<>(), placeholders);
		HashMap<String, String> display = displayOverrides(placeholders);
		HashMap<String, String> exact = new HashMap<>();
		HashMap<String, String> ordinary = new HashMap<>();
		for (Map.Entry<String, String> entry : placeholders.entrySet()) {
			String key = entry.getKey();
			if (key == null || key.equals(MANIFEST_KEY) || key.startsWith(VALUE_PREFIX)) continue;
			String overrideKey = display.keySet().stream().filter(candidate -> candidate.equalsIgnoreCase(key))
					.findFirst().orElse(null);
			if (overrideKey == null) ordinary.put(key, entry.getValue());
			else exact.put(overrideKey, entry.getValue());
		}
		return new VisibleValues(exact, ordinary);
	}

	private static HashMap<String, String> displayOverrides(HashMap<String, String> placeholders) {
		HashMap<String, String> overrides = new HashMap<>();
		if (!hasDisplayValues(placeholders)) return overrides;
		for (Map.Entry<String, String> entry : placeholders.entrySet()) {
			String key = entry.getKey();
			if (key == null || !key.startsWith(VALUE_PREFIX)) continue;
			try {
				overrides.put(decode(key.substring(VALUE_PREFIX.length())), decode(entry.getValue()));
			} catch (IllegalArgumentException ignored) {
				// Malformed internal metadata is ignored rather than becoming output.
			}
		}
		return overrides;
	}

	private static boolean isEncodedOverrideFor(String encodedKey, String displayKey) {
		if (encodedKey == null || !encodedKey.startsWith(VALUE_PREFIX)) return false;
		try {
			return decode(encodedKey.substring(VALUE_PREFIX.length())).equalsIgnoreCase(displayKey);
		} catch (IllegalArgumentException ignored) {
			return false;
		}
	}

	private record VisibleValues(HashMap<String, String> overrides, HashMap<String, String> ordinary) { }

	private static String neutralizeInteractiveSyntax(String value) {
		String input = value == null ? "" : value;
		String boundary = "\u2060";
		StringBuilder safe = new StringBuilder(input.length());
		for (int index = 0; index < input.length(); index++) {
			if (index + "[Text=\"".length() <= input.length()
					&& input.regionMatches(true, index, "[Text=\"", 0, "[Text=\"".length())) {
				safe.append(input, index, index + "[Text=".length()).append(boundary).append('\"');
				index += "[Text=\"".length() - 1;
				continue;
			}
			char current = input.charAt(index);
			safe.append(current);
			if (current == '\"' && index + 1 < input.length()
					&& (input.charAt(index + 1) == ']' || input.charAt(index + 1) == ',')) {
				safe.append(boundary);
			}
		}
		return safe.toString();
	}

	private static String replaceInertToken(String rendered, String token, String value) {
		StringBuilder result = new StringBuilder(rendered.length() + value.length());
		int cursor = 0;
		int tokenIndex;
		while ((tokenIndex = rendered.indexOf(token, cursor)) >= 0) {
			result.append(rendered, cursor, tokenIndex);
			int afterToken = tokenIndex + token.length();
			int probeStart = Math.max(0, tokenIndex - "[Text=\"".length());
			int probeEnd = Math.min(rendered.length(), afterToken + "[Text=\"".length());
			String left = rendered.substring(probeStart, tokenIndex);
			String probe = left + value + rendered.substring(afterToken, probeEnd);
			if (crossesInteractiveSyntax(probe, left.length(), left.length() + value.length())) {
				result.append('\u2060').append(value).append('\u2060');
			} else {
				result.append(value);
			}
			cursor = afterToken;
		}
		result.append(rendered.substring(cursor));
		return result.toString();
	}

	private static boolean crossesInteractiveSyntax(String value, int insertedStart, int insertedEnd) {
		return overlaps(value, "[Text=\"", insertedStart, insertedEnd, true)
				|| overlaps(value, "\"]", insertedStart, insertedEnd, false)
				|| overlaps(value, "\",", insertedStart, insertedEnd, false);
	}

	private static boolean overlaps(String value, String pattern, int insertedStart, int insertedEnd,
			boolean ignoreCase) {
		for (int index = 0; index <= value.length() - pattern.length(); index++) {
			boolean matches = ignoreCase ? value.regionMatches(true, index, pattern, 0, pattern.length())
					: value.startsWith(pattern, index);
			if (matches && index < insertedEnd && index + pattern.length() > insertedStart) return true;
		}
		return false;
	}

	private static String renderBroadcastExact(Player triggeringPlayer, Player recipient, String value,
			HashMap<String, String> placeholders) {
		VisibleValues values = exactValues(placeholders);
		HashMap<String, String> tokens = new HashMap<>(values.overrides().size());
		HashMap<String, String> tokenValues = new HashMap<>(values.overrides().size());
		int index = 0;
		for (Map.Entry<String, String> entry : values.overrides().entrySet()) {
			String token;
			do {
				token = "\uE000AdvancedCoreExact" + index++ + "\uE001";
			} while (value.contains(token));
			tokens.put(entry.getKey(), token);
			tokenValues.put(token, entry.getValue() == null ? "" : entry.getValue());
		}
		String rendered = PlaceholderUtils.replacePlaceHolder(
				PlaceholderUtils.replacePlaceHolder(value, tokens), values.ordinary());
		if (triggeringPlayer != null) rendered = PlaceholderUtils.replacePlaceHolders(triggeringPlayer, rendered);
		if (recipient != null) rendered = PlaceholderUtils.replacePlaceHolders(recipient, rendered);
		rendered = MessageAPI.colorize(rendered);
		for (Map.Entry<String, String> entry : tokenValues.entrySet()) {
			rendered = rendered.replace(entry.getKey(), entry.getValue());
		}
		return rendered;
	}

	private static String encode(String value) {
		return "b" + Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
	}

	private static String decode(String value) {
		if (value == null || !value.startsWith("b")) throw new IllegalArgumentException("Invalid display placeholder");
		return new String(Base64.getUrlDecoder().decode(value.substring(1)), StandardCharsets.UTF_8);
	}
}
