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
import com.bencodez.advancedcore.api.messages.LiteralMessageTemplate;
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
		LiteralPreparation preparation = prepareLiteral(template, placeholders);
		var prepared = preparation.prepared();
		var legacy = preparation.legacy();
		var rendering = legacy.renderer();
		prepared = prepared.withTemplate(renderInteractiveBounded(prepared.template(),
				text -> legacy.revealDisplayLineSeparators(text, false), Function.identity()));
		prepared = prepared.protectFormattedActions(value -> renderIndexedExact(player, value, rendering));
		String parsed;
		if (legacy.hasDisplayValues()) {
			// Evaluate visible provider/script output before splitting, as the legacy
			// prepared-message path does. Exact attributes and literal values remain opaque.
			parsed = renderInteractiveBounded(prepared.template(),
					value -> MessageAPI.colorize(renderIndexedVisible(player, value, rendering)), Function.identity());
		} else {
			String text = rendering.protectAfterOrdinary(prepared.template(), false, true);
			parsed = player == null ? PlaceholderUtils.parseText(text) : PlaceholderUtils.parseText(player, text);
		}
		prepared = prepared.withTemplate(parsed);
		java.util.ArrayList<net.md_5.bungee.api.chat.TextComponent> result = new java.util.ArrayList<>();
		for (var line : prepared.splitLines()) {
			result.add(line.finish(PlaceholderUtils.parseJson(line.template())));
		}
		return result.toArray(new net.md_5.bungee.api.chat.TextComponent[0]);
	}

	/** Captures source-player placeholders once on that player's owner before recipient handoffs. */
	public static LiteralBroadcast prepareLiteralBroadcast(Player triggeringPlayer, String template,
			HashMap<String, String> placeholders) {
		LiteralPreparation preparation = prepareLiteral(template, placeholders);
		var legacy = preparation.legacy();
		var rendering = legacy.renderer();
		var prepared = preparation.prepared();
		prepared = prepared.withTemplate(renderInteractiveBounded(prepared.template(),
				text -> legacy.revealDisplayLineSeparators(text, true), Function.identity()));
		prepared = prepared.withTemplate(renderInteractiveBounded(prepared.template(),
				value -> rendering.protectAfterOrdinary(value, false, false), value -> rendering.protect(value, true, false)));
		if (triggeringPlayer != null) prepared = prepared.withTemplate(
				PlaceholderUtils.replacePlaceHolders(triggeringPlayer, prepared.template()));
		return new LiteralBroadcast(prepared, legacy);
	}

	/** Immutable protected template and indexes; retains no player, user, or caller-owned map reference. */
	public static final class LiteralBroadcast {
		private final LiteralMessageTemplate.Prepared prepared;
		private final java.util.List<LiteralMessageTemplate.Prepared> lines;
		private final LiteralMessageTemplate.LegacyBindings legacy;
		private LiteralBroadcast(LiteralMessageTemplate.Prepared prepared, LiteralMessageTemplate.LegacyBindings legacy) {
			this.prepared = prepared;
			this.lines = prepared.splitBroadcastLines();
			this.legacy = legacy;
		}
		/** Recipient placeholders must run on that recipient's owner; console uses null on the global owner. */
		public net.md_5.bungee.api.chat.TextComponent[] render(Player recipient) {
			java.util.ArrayList<net.md_5.bungee.api.chat.TextComponent> result = new java.util.ArrayList<>();
			for (int index = 0; index < lineCount(); index++) {
				for (var fragment : renderRecipientLineFragments(recipient, index)) {
					if (result.size() >= 4096) throw new IllegalArgumentException("Literal broadcast has too many rendered lines");
					result.add(fragment);
				}
			}
			return result.toArray(new net.md_5.bungee.api.chat.TextComponent[0]);
		}

		public int lineCount() { return lines.size(); }

		/** Recreates the old single console payload; rich JSON syntax remains literal. */
		public String renderConsoleMessage() {
			return renderInteractiveBounded(prepared.template(),
					value -> prepared.finishText(MessageAPI.colorize(legacy.finish(value, false))),
					value -> prepared.finishActionText(MessageAPI.colorize(legacy.finish(value, true))));
		}

		public net.md_5.bungee.api.chat.TextComponent renderLine(Player recipient, int index) {
			var line = renderProtectedLine(recipient, index);
			return line.finish(PlaceholderUtils.parseJson(line.template()));
		}

		/** Expand recipient placeholders before splitting; exact actions and literal values stay protected. */
		public net.md_5.bungee.api.chat.TextComponent[] renderRecipientLineFragments(Player recipient, int index) {
			var preparedLine = renderProtectedLine(recipient, index);
			var fragments = preparedLine.splitBroadcastLines();
			if (fragments.size() > 4096) throw new IllegalArgumentException("Literal broadcast has too many rendered lines");
			var result = new net.md_5.bungee.api.chat.TextComponent[fragments.size()];
			for (int fragment = 0; fragment < result.length; fragment++) {
				var line = fragments.get(fragment);
				result[fragment] = line.finish(PlaceholderUtils.parseJson(line.template()));
			}
			return result;
		}

		private LiteralMessageTemplate.Prepared renderProtectedLine(Player recipient, int index) {
			if (index < 0 || index >= lines.size()) throw new IllegalArgumentException("broadcast line index out of range");
			var line = lines.get(index);
			String text;
			if (legacy.hasDisplayValues()) {
				line = line.protectFormattedActions(value -> finishIndexedBroadcast(value, recipient, legacy, true));
				text = renderInteractiveBounded(line.template(),
						value -> MessageAPI.colorize(finishIndexedBroadcast(value, recipient, legacy, false)), Function.identity());
			} else {
				text = recipient == null ? line.template() : PlaceholderUtils.replacePlaceHolders(recipient, line.template());
				text = MessageAPI.colorize(text);
			}
			return line.withTemplate(text);
		}
	}

	private static LiteralPreparation prepareLiteral(String template, HashMap<String, String> placeholders) {
		placeholders = Reward.copyWithoutReplayMetadata(placeholders);
		Map<String, String> literals = literalValues(placeholders);
		Map<String, String> actions = new HashMap<>();
		if (placeholders != null) for (Map.Entry<String, String> entry : placeholders.entrySet()) {
			if (entry.getKey() != null && literals.containsKey(LiteralMessageTemplate.normalizeBindingKey(entry.getKey())))
				actions.put(entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
		}
		var legacy = indexedLegacyValues(legacyPlaceholders(placeholders, literals.keySet()));
		var prepared = LiteralMessageTemplate.prepareRewardDisplay(template, literals, actions, legacy);
		return new LiteralPreparation(prepared, legacy.bind(prepared));
	}

	private record LiteralPreparation(LiteralMessageTemplate.Prepared prepared,
			LiteralMessageTemplate.LegacyBindings legacy) { }

	/** Decode and admit the mixed lane once; avoid rebuilding or probing the override table for every segment. */
	private static LiteralMessageTemplate.LegacyValues indexedLegacyValues(HashMap<String, String> placeholders) {
		if (placeholders.size() > 8192) throw new IllegalArgumentException("literal metadata exceeds limits");
		Map<String, String> display = new java.util.LinkedHashMap<>();
		Map<String, String> displayKeys = new HashMap<>();
		Map<String, String> exact = new java.util.LinkedHashMap<>();
		Map<String, String> ordinary = new HashMap<>();
		int characters = 0;
		if (hasDisplayValues(placeholders)) for (Map.Entry<String, String> entry : placeholders.entrySet()) {
			String key = entry.getKey();
			if (key == null || !key.startsWith(VALUE_PREFIX)) continue;
			if (key.length() > VALUE_PREFIX.length() + 4097 || entry.getValue() == null
					|| entry.getValue().length() > 131073) throw new IllegalArgumentException("legacy metadata exceeds limits");
			String decodedKey;
			String value;
			try {
				decodedKey = decode(key.substring(VALUE_PREFIX.length()));
				value = decode(entry.getValue());
			} catch (IllegalArgumentException ignored) {
				// Malformed v1 metadata keeps its historical ignored behavior.
				continue;
			}
			if (decodedKey.isEmpty() || decodedKey.length() > 1024 || value.length() > 32768)
				throw new IllegalArgumentException("legacy metadata exceeds limits");
			characters += decodedKey.length() + value.length();
			if (characters > 1048576) throw new IllegalArgumentException("legacy metadata exceeds limits");
			String normalized = LiteralMessageTemplate.normalizeBindingKey(decodedKey);
			if (displayKeys.putIfAbsent(normalized, decodedKey) == null) display.put(decodedKey, value);
		}
		for (Map.Entry<String, String> entry : placeholders.entrySet()) {
			String key = entry.getKey();
			if (key == null || key.equals(MANIFEST_KEY) || key.startsWith(VALUE_PREFIX) || key.startsWith(LITERAL_PREFIX)) continue;
			String value = entry.getValue() == null ? "" : entry.getValue();
			if (key.isEmpty() || key.length() > 1024 || value.length() > 32768)
				throw new IllegalArgumentException("legacy bindings exceed limits");
			characters += key.length() + value.length();
			if (characters > 1048576) throw new IllegalArgumentException("legacy bindings exceed limits");
			String normalized = LiteralMessageTemplate.normalizeBindingKey(key);
			if (displayKeys.containsKey(normalized)) exact.put(normalized, value);
			else ordinary.put(key, value);
		}
		return new LiteralMessageTemplate.LegacyValues(display, exact, ordinary);
	}

	private static String renderIndexedVisible(Player player, String source, LiteralMessageTemplate.LegacyBindings.Renderer values) {
		String protectedValue = values.protectAfterOrdinary(source, false, true);
		String parsed = player == null ? PlaceholderUtils.parseText(protectedValue) : PlaceholderUtils.parseText(player, protectedValue);
		return values.finish(parsed, false);
	}

	private static String renderIndexedExact(Player player, String source, LiteralMessageTemplate.LegacyBindings.Renderer values) {
		String protectedValue = values.protect(source, true, true);
		String parsed = player == null ? PlaceholderUtils.parseText(protectedValue) : PlaceholderUtils.parseText(player, protectedValue);
		return values.finish(parsed, true);
	}

	private static String finishIndexedBroadcast(String source, Player recipient, LiteralMessageTemplate.LegacyBindings values,
			boolean action) {
		String parsed = recipient == null ? source : PlaceholderUtils.replacePlaceHolders(recipient, source);
		return values.finish(MessageAPI.colorize(parsed), action);
	}

	/** Snapshot only the existing legacy lane; explicit literal bindings remain protected separately. */
	private static HashMap<String, String> legacyPlaceholders(HashMap<String, String> placeholders, java.util.Set<String> literals) {
		HashMap<String, String> legacy = placeholders == null ? new HashMap<>() : new HashMap<>(placeholders);
		HashMap<String, String> replay = new HashMap<>();
		Reward.mergeReplayMetadata(replay, legacy);
		legacy.keySet().removeAll(replay.keySet());
		legacy.keySet().removeIf(key -> {
			if (key == null) return false;
			if (key.startsWith(LITERAL_PREFIX) || literals.contains(LiteralMessageTemplate.normalizeBindingKey(key))) return true;
			if (!key.startsWith(VALUE_PREFIX)) return false;
			try { return literals.contains(LiteralMessageTemplate.normalizeBindingKey(decode(key.substring(VALUE_PREFIX.length())))); }
			catch (IllegalArgumentException ignored) { return false; }
		});
		return legacy;
	}

	private static Map<String, String> literalValues(HashMap<String, String> placeholders) {
		Map<String, String> values = new HashMap<>();
		int characters = 0;
		if (!hasLiteralValues(placeholders)) return values;
		if (placeholders.size() > 8192) throw new IllegalArgumentException("literal metadata exceeds limits");
		for (Map.Entry<String, String> entry : placeholders.entrySet()) {
			String key = entry.getKey();
			if (key == null || !key.startsWith(LITERAL_PREFIX)) continue;
			if (key.length() > 1024 || entry.getValue() == null || entry.getValue().length() > MAX_ENCODED_LITERAL_CHARACTERS)
				throw new IllegalArgumentException("literal metadata exceeds limits");
			String decodedKey = decode(key.substring(LITERAL_PREFIX.length()));
			if (!decodedKey.matches("[A-Za-z_][A-Za-z0-9_]{0,127}"))
				throw new IllegalArgumentException("invalid literal binding");
			String decoded = LiteralMessageTemplate.normalizeBindingKey(decodedKey);
			String literal = decode(entry.getValue());
			characters += decodedKey.length() + literal.length();
			if (characters > 1048576) throw new IllegalArgumentException("literal metadata exceeds limits");
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

	private static String renderInteractiveBounded(String template, Function<String, String> visibleRenderer,
			Function<String, String> exactRenderer) {
		int[] characters = { template.length() };
		Function<Function<String, String>, Function<String, String>> bounded = renderer -> source -> {
			String rendered = renderer.apply(source);
			characters[0] += rendered.length() - source.length();
			if (characters[0] > 262144) throw new IllegalArgumentException("legacy text exceeds limits");
			return rendered;
		};
		return renderInteractive(template, bounded.apply(visibleRenderer), bounded.apply(exactRenderer));
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
