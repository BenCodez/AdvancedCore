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
	private static final String LITERAL_PREFIX = PREFIX + "literal:";
	private static final int MAX_LITERAL_CHARACTERS = 8192;
	// At most three UTF-8 bytes per UTF-16 unit, four Base64 characters per three bytes, plus prefix.
	private static final int MAX_ENCODED_LITERAL_CHARACTERS = MAX_LITERAL_CHARACTERS * 4 + 1;
	private static final Pattern MESSAGE_ATTRIBUTE = Pattern.compile(
			"(?i)(command|url|suggest_command|hover)\\s*=\\s*\"([^\"]*)\"");

	private RewardDisplayPlaceholders() {
	}

	/** Adds a display-only value without replacing the raw action value. */
	public static void put(HashMap<String, String> placeholders, String key, String value) {
		if (placeholders == null || key == null) return;
		placeholders.put(MANIFEST_KEY, MANIFEST_VALUE);
		placeholders.keySet().removeIf(existing -> isEncodedOverrideFor(existing, key));
		placeholders.keySet().removeIf(existing -> isEncodedLiteralFor(existing, key));
		placeholders.put(VALUE_PREFIX + encode(key), encode(value == null ? "" : value));
	}

	/** Adds an explicit literal display binding while retaining the v1 fallback value. */
	public static void putLiteral(HashMap<String, String> placeholders, String key, String literal,
			String legacySafeDisplay) {
		if (placeholders == null || key == null) return;
		if (!key.matches("[A-Za-z_][A-Za-z0-9_]{0,127}") || (literal != null && literal.length() > MAX_LITERAL_CHARACTERS)
				|| (legacySafeDisplay != null && legacySafeDisplay.length() > 32768))
			throw new IllegalArgumentException("invalid literal binding");
		put(placeholders, key, legacySafeDisplay == null ? "" : legacySafeDisplay);
		placeholders.put(LITERAL_PREFIX + encode(key), encode(literal == null ? "" : literal));
	}

	private static boolean isEncodedLiteralFor(String encodedKey, String displayKey) {
		if (encodedKey == null || !encodedKey.startsWith(LITERAL_PREFIX)) return false;
		try { return decode(encodedKey.substring(LITERAL_PREFIX.length())).equalsIgnoreCase(displayKey); }
		catch (IllegalArgumentException ignored) { return false; }
	}

	public static boolean hasLiteralValues(HashMap<String, String> placeholders) {
		return hasDisplayValues(placeholders) && placeholders.keySet().stream()
				.anyMatch(key -> key != null && key.startsWith(LITERAL_PREFIX));
	}

	/** Parses trusted formatting before literal values enter final components. Caller owns the player. */
	public static net.md_5.bungee.api.chat.TextComponent[] renderLiteralComponents(Player player, String template,
			HashMap<String, String> placeholders) {
		com.bencodez.advancedcore.api.messages.LiteralMessageTemplate.Prepared prepared =
				prepareLiteral(template, placeholders);
		HashMap<String, String> legacy = legacyPlaceholders(placeholders);
		boolean hasLegacyDisplay = !displayOverrides(legacy).isEmpty();
		java.util.ArrayList<net.md_5.bungee.api.chat.TextComponent> result = new java.util.ArrayList<>();
		for (var line : prepared.splitLines()) {
			String parsed;
			if (hasLegacyDisplay) {
				line = line.protectFormattedActions(value -> renderExact(player, value, legacy));
				// Legacy display bindings are restored after their provider/script pass.
				// Color only visible text and hover; raw action attributes stay shielded.
				parsed = renderInteractive(line.template(),
						value -> MessageAPI.colorize(renderVisible(player, value, legacy)), Function.identity());
			} else {
				String text = PlaceholderUtils.replacePlaceHolder(line.template(), forDisplay(legacy));
				parsed = player == null ? PlaceholderUtils.parseText(text) : PlaceholderUtils.parseText(player, text);
			}
			var finalTemplate = line.withTemplate(parsed);
			result.add(finalTemplate.finish(PlaceholderUtils.parseJson(finalTemplate.template())));
		}
		return result.toArray(new net.md_5.bungee.api.chat.TextComponent[0]);
	}

	/** Captures source-player placeholders once on that player's owner before recipient handoffs. */
	public static LiteralBroadcast prepareLiteralBroadcast(Player triggeringPlayer, String template,
			HashMap<String, String> placeholders) {
		var prepared = prepareLiteral(template, placeholders);
		HashMap<String, String> legacyPlaceholders = legacyPlaceholders(placeholders);
		boolean hasLegacyDisplay = !displayOverrides(legacyPlaceholders).isEmpty();
		BroadcastValues legacy = new BroadcastValues(legacyPlaceholders, prepared.template());
		prepared = prepared.withTemplate(hasLegacyDisplay ? renderInteractive(prepared.template(),
				value -> legacy.protect(value, false), value -> legacy.protect(value, true))
				: PlaceholderUtils.replacePlaceHolder(prepared.template(), forDisplay(legacyPlaceholders)));
		if (triggeringPlayer != null) prepared = prepared.withTemplate(
				PlaceholderUtils.replacePlaceHolders(triggeringPlayer, prepared.template()));
		return new LiteralBroadcast(prepared, legacy, hasLegacyDisplay);
	}

	/** Immutable protected template; retains no player or user reference. */
	public static final class LiteralBroadcast {
		private final com.bencodez.advancedcore.api.messages.LiteralMessageTemplate.Prepared prepared;
		private final Map<String, String> visibleTokens;
		private final Map<String, String> exactTokens;
		private final boolean hasLegacyDisplay;
		private LiteralBroadcast(com.bencodez.advancedcore.api.messages.LiteralMessageTemplate.Prepared prepared,
				BroadcastValues legacy, boolean hasLegacyDisplay) {
			this.prepared = prepared;
			visibleTokens = Map.copyOf(legacy.visibleTokens);
			exactTokens = Map.copyOf(legacy.exactTokens);
			this.hasLegacyDisplay = hasLegacyDisplay;
		}
		/** Recipient placeholders must run on that recipient's owner; console uses null on the global owner. */
		public net.md_5.bungee.api.chat.TextComponent[] render(Player recipient) {
			java.util.ArrayList<net.md_5.bungee.api.chat.TextComponent> result = new java.util.ArrayList<>();
			for (var line : prepared.splitLines()) {
				String text;
				if (hasLegacyDisplay) {
					line = line.protectFormattedActions(value -> finishBroadcast(value, recipient, exactTokens, true));
					text = renderInteractive(line.template(),
							value -> MessageAPI.colorize(finishBroadcast(value, recipient, visibleTokens, false)), Function.identity());
				} else {
					text = recipient == null ? line.template() : PlaceholderUtils.replacePlaceHolders(recipient, line.template());
					text = MessageAPI.colorize(text);
				}
				var finalTemplate = line.withTemplate(text);
				result.add(finalTemplate.finish(PlaceholderUtils.parseJson(finalTemplate.template())));
			}
			return result.toArray(new net.md_5.bungee.api.chat.TextComponent[0]);
		}
	}

	private static com.bencodez.advancedcore.api.messages.LiteralMessageTemplate.Prepared prepareLiteral(
			String template, HashMap<String, String> placeholders) {
		Map<String, String> literals = literalValues(placeholders);
		Map<String, String> actions = new HashMap<>();
		if (placeholders != null) for (Map.Entry<String, String> entry : placeholders.entrySet()) {
			if (entry.getKey() != null && literals.containsKey(entry.getKey().toLowerCase(java.util.Locale.ROOT)))
				actions.put(entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
		}
		return com.bencodez.advancedcore.api.messages.LiteralMessageTemplate.prepare(template, literals, actions);
	}

	/** Snapshot only the existing legacy lane; explicit literal bindings remain protected separately. */
	private static HashMap<String, String> legacyPlaceholders(HashMap<String, String> placeholders) {
		HashMap<String, String> legacy = placeholders == null ? new HashMap<>() : new HashMap<>(placeholders);
		java.util.Set<String> literals = literalValues(placeholders).keySet();
		legacy.keySet().removeIf(key -> {
			if (key == null) return false;
			if (key.startsWith(LITERAL_PREFIX) || literals.contains(key.toLowerCase(java.util.Locale.ROOT))) return true;
			if (!key.startsWith(VALUE_PREFIX)) return false;
			try { return literals.contains(decode(key.substring(VALUE_PREFIX.length())).toLowerCase(java.util.Locale.ROOT)); }
			catch (IllegalArgumentException ignored) { return false; }
		});
		return legacy;
	}

	private static Map<String, String> literalValues(HashMap<String, String> placeholders) {
		Map<String, String> values = new HashMap<>();
		if (!hasLiteralValues(placeholders)) return values;
		if (placeholders.size() > 8192) throw new IllegalArgumentException("literal metadata exceeds limits");
		for (Map.Entry<String, String> entry : placeholders.entrySet()) {
			String key = entry.getKey();
			if (key == null || !key.startsWith(LITERAL_PREFIX)) continue;
			if (key.length() > 1024 || entry.getValue() == null || entry.getValue().length() > MAX_ENCODED_LITERAL_CHARACTERS)
				throw new IllegalArgumentException("literal metadata exceeds limits");
			String decoded = decode(key.substring(LITERAL_PREFIX.length())).toLowerCase(java.util.Locale.ROOT);
			String literal = decode(entry.getValue());
			if (literal.length() > MAX_LITERAL_CHARACTERS) throw new IllegalArgumentException("literal metadata exceeds limits");
			if (values.putIfAbsent(decoded, literal) != null)
				throw new IllegalArgumentException("duplicate literal binding");
		}
		return values;
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
					|| (!key.equals(MANIFEST_KEY) && !key.startsWith(VALUE_PREFIX) && !key.startsWith(LITERAL_PREFIX))) {
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

	/** The existing broadcast policy, separated at its source/recipient handoff. */
	private static final class BroadcastValues {
		private final VisibleValues visible;
		private final VisibleValues exact;
		private final String source;
		private final HashMap<String, String> visibleTokens = new HashMap<>();
		private final HashMap<String, String> exactTokens = new HashMap<>();
		private BroadcastValues(HashMap<String, String> placeholders, String source) {
			visible = visibleValues(placeholders);
			exact = exactValues(placeholders);
			this.source = source;
		}
		private String protect(String value, boolean action) {
			VisibleValues values = action ? exact : visible;
			HashMap<String, String> replacements = new HashMap<>();
			for (Map.Entry<String, String> entry : values.overrides().entrySet()) {
				HashMap<String, String> probe = new HashMap<>();
				probe.put(entry.getKey(), "VCORELITERALPROBE");
				if (PlaceholderUtils.replacePlaceHolder(value, probe).equals(value)) continue;
				if (visibleTokens.size() + exactTokens.size() >= 4096)
					throw new IllegalArgumentException("too many broadcast display values");
				String raw = entry.getValue() == null ? "" : entry.getValue();
				String token = token(raw);
				replacements.put(entry.getKey(), token);
				(action ? exactTokens : visibleTokens).put(token, action ? raw : neutralizeInteractiveSyntax(raw));
			}
			return PlaceholderUtils.replacePlaceHolder(
					PlaceholderUtils.replacePlaceHolder(value, replacements), values.ordinary());
		}
		private String token(String value) {
			for (int attempt = 0; attempt < 16; attempt++) {
				String token = "VCORELITERAL" + Long.toUnsignedString(java.util.concurrent.ThreadLocalRandom.current().nextLong(), 36)
						+ "Q" + Long.toUnsignedString(java.util.concurrent.ThreadLocalRandom.current().nextLong(), 36) + "Z";
				if (!source.contains(token) && !value.contains(token) && !visibleTokens.containsKey(token)
						&& !exactTokens.containsKey(token)
						&& visible.overrides().values().stream().noneMatch(text -> text != null && text.contains(token))
						&& exact.overrides().values().stream().noneMatch(text -> text != null && text.contains(token))) return token;
			}
			throw new IllegalStateException("broadcast token allocation failed");
		}
	}

	private static String finishBroadcast(String value, Player recipient, Map<String, String> tokens, boolean exact) {
		String rendered = recipient == null ? value : PlaceholderUtils.replacePlaceHolders(recipient, value);
		rendered = MessageAPI.colorize(rendered);
		for (Map.Entry<String, String> entry : tokens.entrySet()) {
			rendered = exact ? rendered.replace(entry.getKey(), entry.getValue())
					: replaceInertToken(rendered, entry.getKey(), entry.getValue());
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
			if (key == null || key.equals(MANIFEST_KEY) || key.startsWith(VALUE_PREFIX) || key.startsWith(LITERAL_PREFIX)) continue;
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
			if (key == null || key.equals(MANIFEST_KEY) || key.startsWith(VALUE_PREFIX) || key.startsWith(LITERAL_PREFIX)) continue;
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
