package com.bencodez.advancedcore.api.messages;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;

import java.util.concurrent.ThreadLocalRandom;
import java.util.IdentityHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.function.Function;

/**
 * Keeps literal placeholder values out of the formatting and component parsers.
 * Values are inserted only after the trusted template has been parsed.
 */
public final class LiteralMessageTemplate {
    private static final int MAX_TEMPLATE = 65536;
    private static final int MAX_VALUES = 4096;
    private static final int MAX_VALUE = 8192;
    private static final int MAX_COMPONENTS = 4096;
    private static final Pattern EXACT_ATTRIBUTE = Pattern.compile(
            "(?i)(?<![A-Za-z0-9_])(command|url|suggest_command)\\s*=\\s*\"([^\"]*)\"");

    private LiteralMessageTemplate() {
    }

    public static Prepared prepare(String template, Map<String, String> literalValues, Map<String, String> actionValues) {
        String source = template == null ? "" : template;
        if (source.length() > MAX_TEMPLATE) {
            throw new IllegalArgumentException("template is too large");
        }
        Map<String, String> values = new LinkedHashMap<>();
        java.util.Set<String> keys = new java.util.HashSet<>();
        if (literalValues != null) {
            for (Map.Entry<String, String> entry : literalValues.entrySet()) {
                String key = Objects.requireNonNull(entry.getKey(), "literal key");
                if (!key.matches("[A-Za-z_][A-Za-z0-9_]{0,127}") || entry.getValue() == null
                        || entry.getValue().length() > MAX_VALUE) {
                    throw new IllegalArgumentException("invalid literal value");
                }
                if (!keys.add(key.toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("duplicate literal key");
                // A summary may have many sites but only a few occur in this line.
                // Unreferenced bindings never enter a parser or the restoration map.
                if (!containsMarker(source, key)) continue;
                if (values.size() >= MAX_VALUES) throw new IllegalArgumentException("too many referenced literal values");
                if (values.putIfAbsent(key.toLowerCase(Locale.ROOT), entry.getValue()) != null) {
                    throw new IllegalArgumentException("duplicate literal key");
                }
            }
        }
        Map<String, String> tokens = new LinkedHashMap<>();
        Map<String, String> replacements = new LinkedHashMap<>();
        Map<String, String> actionTokens = new LinkedHashMap<>();
        Map<String, String> exact = new LinkedHashMap<>();
        if (actionValues != null) for (Map.Entry<String, String> entry : actionValues.entrySet()) {
            String key = Objects.requireNonNull(entry.getKey(), "action key").toLowerCase(Locale.ROOT);
            if (exact.containsKey(key)) throw new IllegalArgumentException("duplicate action key");
            exact.put(key, entry.getValue());
        }
        Map<String, String> collisions = new LinkedHashMap<>(values);
        exact.forEach((key, value) -> { if (value != null) collisions.put("action:" + key, value); });
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String token = newToken(source, collisions, tokens);
            replacements.put(entry.getKey(), token);
            tokens.put(token, entry.getValue());
            String action = exact.getOrDefault(entry.getKey(), "");
            if (action == null || action.length() > MAX_VALUE) throw new IllegalArgumentException("invalid action value");
            actionTokens.put(token, action);
        }

        // Match the existing JavaScript parser's authored marker/first-closing-bracket
        // grammar. A script containing literal display data is displayed as text;
        // it is never evaluated with externally supplied program fragments.
        StringBuilder protectedScripts = new StringBuilder(source.length());
        int cursor = 0;
        while (cursor < source.length()) {
            int start = indexOfIgnoreCase(source, "[Javascript=", cursor);
            if (start < 0) break;
            int end = source.indexOf(']', start + "[Javascript=".length());
            if (end < 0) break;
            String block = source.substring(start, end + 1);
            String substituted = replaceMarkers(block, values);
            protectedScripts.append(source, cursor, start);
            if (!block.equals(replaceMarkers(block, replacements))) {
                if (tokens.size() >= MAX_VALUES) throw new IllegalArgumentException("too many literal script segments");
                String token = newToken(source, collisions, tokens);
                tokens.put(token, substituted);
                // The visible script is kept as text, while an action containing the
                // same protected block must retain the exact raw values (or empty
                // values when no raw action was supplied).  Only literal bindings
                // are substituted here; unrelated provider markers remain for the
                // normal action/provider pass.
                Map<String, String> scriptActions = new LinkedHashMap<>();
                for (String key : values.keySet()) scriptActions.put(key, exact.getOrDefault(key, ""));
                actionTokens.put(token, replaceMarkers(block, scriptActions));
                protectedScripts.append(token);
            } else {
                protectedScripts.append(block);
            }
            cursor = end + 1;
        }
        protectedScripts.append(source, cursor, source.length());
        String protectedActions = protectExactAttributes(protectedScripts.toString(), values, exact, tokens, actionTokens);
        return new Prepared(replaceMarkers(protectedActions, replacements), tokens, actionTokens);
    }

    /** Shield raw legacy display overrides only in administrator-authored click attributes. */
    private static String protectExactAttributes(String source, Map<String, String> literalValues,
            Map<String, String> exactValues, Map<String, String> tokens, Map<String, String> actionTokens) {
        Map<String, String> replacements = new LinkedHashMap<>();
        Map<String, String> collisions = new LinkedHashMap<>(literalValues);
        exactValues.forEach((key, value) -> { if (value != null) collisions.put("action:" + key, value); });
        return mapExactAttributes(source, value -> {
            for (Map.Entry<String, String> entry : exactValues.entrySet()) {
                String key = Objects.requireNonNull(entry.getKey(), "action key").toLowerCase(Locale.ROOT);
                if (literalValues.containsKey(key) || replacements.containsKey(key) || !containsMarker(value, key)) continue;
                if (key.isEmpty() || key.length() > 1024 || entry.getValue() == null
                        || entry.getValue().length() > MAX_VALUE) throw new IllegalArgumentException("invalid action value");
                if (tokens.size() >= MAX_VALUES) throw new IllegalArgumentException("too many action values");
                String token = newToken(source, collisions, tokens);
                replacements.put(key, token);
                tokens.put(token, entry.getValue());
                actionTokens.put(token, entry.getValue());
            }
            return replaceMarkers(value, replacements);
        });
    }

    /** Visit only administrator-authored attributes, before any inserted value is parsed. */
    private static String mapExactAttributes(String source, Function<String, String> mapper) {
        StringBuilder result = new StringBuilder(source.length());
        int cursor = 0;
        while (cursor < source.length()) {
            int componentStart = indexOfIgnoreCase(source, "[Text=\"", cursor);
            int scriptStart = indexOfIgnoreCase(source, "[Javascript=", cursor);
            // Text-looking strings inside a script are program text, not authored message attributes.
            if (scriptStart >= 0 && (componentStart < 0 || scriptStart < componentStart)) {
                int scriptEnd = source.indexOf(']', scriptStart);
                if (scriptEnd < 0) break;
                result.append(source, cursor, scriptEnd + 1);
                cursor = scriptEnd + 1;
                continue;
            }
            if (componentStart < 0) break;
            int componentEnd = source.indexOf("\"]", componentStart);
            if (componentEnd < 0) break;
            componentEnd += 2;
            int attributesStart = source.indexOf("\",", componentStart);
            result.append(source, cursor, componentStart);
            if (attributesStart < 0 || attributesStart >= componentEnd) {
                result.append(source, componentStart, componentEnd);
            } else {
                Matcher attributes = EXACT_ATTRIBUTE.matcher(source);
                attributes.region(attributesStart + 2, componentEnd);
                int attributeCursor = componentStart;
                while (attributes.find()) {
                    result.append(source, attributeCursor, attributes.start(2));
                    result.append(mapper.apply(attributes.group(2)));
                    attributeCursor = attributes.end(2);
                }
                result.append(source, attributeCursor, componentEnd);
            }
            cursor = componentEnd;
        }
        return result.append(source, cursor, source.length()).toString();
    }

    private static String newToken(String source, Map<String, String> values, Map<String, String> tokens) {
        for (int attempts = 0; attempts < 16; attempts++) {
            // No percent signs or underscores: this cannot introduce a PAPI provider
            // token. The first character cannot complete a legacy color code.
            String token = "VCORELITERAL" + Long.toUnsignedString(ThreadLocalRandom.current().nextLong(), 36)
                    + "Q" + Long.toUnsignedString(ThreadLocalRandom.current().nextLong(), 36) + "Z";
            if (!source.contains(token) && !tokens.containsKey(token)
                    && values.values().stream().noneMatch(value -> value.contains(token))) return token;
        }
        throw new IllegalStateException("literal token allocation failed");
    }

    private static int indexOfIgnoreCase(String source, String target, int from) {
        for (int index = from; index <= source.length() - target.length(); index++) {
            if (source.regionMatches(true, index, target, 0, target.length())) return index;
        }
        return -1;
    }

    private static boolean containsMarker(String source, String key) {
        return indexOfIgnoreCase(source, "%" + key + "%", 0) >= 0
                || indexOfIgnoreCase(source, "{" + key + "}", 0) >= 0;
    }

    /** Replace only markers in the original input, never markers inside inserted values. */
    private static String replaceMarkers(String source, Map<String, String> values) {
        StringBuilder result = new StringBuilder(source.length());
        int cursor = 0;
        while (cursor < source.length()) {
            boolean matched = false;
            char opening = source.charAt(cursor);
            if (opening == '%' || opening == '{') {
                for (Map.Entry<String, String> entry : values.entrySet()) {
                    String marker = opening + entry.getKey() + (opening == '%' ? "%" : "}");
                    if (source.regionMatches(true, cursor, marker, 0, marker.length())) {
                        result.append(entry.getValue());
                        cursor += marker.length();
                        matched = true;
                        break;
                    }
                }
            }
            if (!matched) result.append(source.charAt(cursor++));
        }
        return result.toString();
    }

    private static String protectSectionTokenBoundaries(String source, Map<String, String> tokens,
            Map<String, String> actionTokens) {
        if (source.indexOf('§') < 0 || tokens.isEmpty()) return source;
        List<String> originalTokens = List.copyOf(tokens.keySet());
        String guard = null;
        StringBuilder protectedText = new StringBuilder(source.length());
        for (int index = 0; index < source.length(); index++) {
            if (source.charAt(index) == '§') {
                int following = index + 1;
                boolean tokenFollows = originalTokens.stream().anyMatch(token -> source.startsWith(token, following));
                if (tokenFollows) {
                    if (guard == null) {
                        // A section-sign label may have a different raw action value.
                        // Reuse a boundary token only when both restoration lanes agree.
                        guard = tokens.entrySet().stream().filter(entry -> entry.getValue().equals("§")
                                && "§".equals(actionTokens.getOrDefault(entry.getKey(), entry.getValue())))
                                .map(Map.Entry::getKey).findFirst().orElse(null);
                        if (guard == null) {
                            guard = newToken(source, tokens, tokens);
                            tokens.put(guard, "§");
                        }
                    }
                    // Invalid §V is consumed by legacy parsers before restoration.
                    // Keep this prefix opaque too; finish restores literal visible
                    // text or the exact original prefix in an action attribute.
                    protectedText.append(guard);
                    continue;
                }
            }
            protectedText.append(source.charAt(index));
        }
        return protectedText.toString();
    }

    public static final class Prepared {
        private final String template;
        private final Map<String, String> tokens;
        private final Map<String, String> actionTokens;

        private Prepared(String template, Map<String, String> tokens, Map<String, String> actionTokens) {
            Map<String, String> bindings = new LinkedHashMap<>(tokens);
            Map<String, String> actions = new LinkedHashMap<>(actionTokens);
            this.template = protectSectionTokenBoundaries(template, bindings, actions);
            this.tokens = Collections.unmodifiableMap(bindings);
            bindings.forEach(actions::putIfAbsent);
            this.actionTokens = Collections.unmodifiableMap(actions);
        }

        public String template() {
            return template;
        }

        /** Split authored message separators while labels are still opaque. */
        public List<Prepared> splitLines() {
            List<Prepared> lines = new ArrayList<>();
            for (String line : template.split("(?i)" + java.util.regex.Pattern.quote("%NewLine%"))) {
                lines.add(new Prepared(line, tokens, actionTokens));
            }
            return List.copyOf(lines);
        }

        /** Continue trusted formatting while retaining the same literal bindings. */
        public Prepared withTemplate(String formattedTemplate) {
            if (formattedTemplate == null || formattedTemplate.length() > MAX_TEMPLATE) {
                throw new IllegalArgumentException("formatted template is invalid");
            }
            return new Prepared(formattedTemplate, tokens, actionTokens);
        }

        /** Render authored action values once, then keep their exact output out of the component parser. */
        public Prepared protectFormattedActions(Function<String, String> renderer) {
            Objects.requireNonNull(renderer, "renderer");
            Map<String, String> bindings = new LinkedHashMap<>(tokens);
            Map<String, String> actions = new LinkedHashMap<>(actionTokens);
            String protectedTemplate = mapExactAttributes(template, value -> {
                String rendered = restore(renderer.apply(value), actionTokens, false);
                if (rendered == null || rendered.length() > 262144)
                    throw new IllegalArgumentException("formatted action exceeds limits");
                if (bindings.size() >= MAX_VALUES) throw new IllegalArgumentException("too many formatted actions");
                String token = newToken(template, Map.of("action", rendered), bindings);
                bindings.put(token, rendered);
                actions.put(token, rendered);
                return token;
            });
            return new Prepared(protectedTemplate, bindings, actions);
        }

        public TextComponent finish(TextComponent parsedComponent) {
            if (parsedComponent != null) validateTree(parsedComponent, 0, new int[] { 0, 0 }, new IdentityHashMap<>());
            TextComponent result = parsedComponent == null ? new TextComponent() : parsedComponent.duplicate();
            rewrite(result, tokens, actionTokens, 0);
            validateTree(result, 0, new int[] { 0, 0 }, new IdentityHashMap<>());
            return result;
        }

        /** Restores values for a terminal that will not parse the returned text again. */
        public String finishText(String parsedText) {
            return restore(parsedText, tokens, true);
        }
    }

    @SuppressWarnings("deprecation")
    private static void validateTree(BaseComponent component, int depth, int[] count,
            IdentityHashMap<BaseComponent, Boolean> path) {
        if (component == null) return;
        if (depth > 64 || ++count[0] > MAX_COMPONENTS || path.put(component, Boolean.TRUE) != null) {
            throw new IllegalArgumentException("literal component tree exceeds limits");
        }
        if (component instanceof TextComponent text) count[1] += text.getText().length();
        if (component.getInsertion() != null) count[1] += component.getInsertion().length();
        if (component.getClickEvent() != null) count[1] += component.getClickEvent().getValue().length();
        if (count[1] > 262144) throw new IllegalArgumentException("literal component text exceeds limits");
        if (component.getExtra() != null) {
            for (BaseComponent child : component.getExtra()) validateTree(child, depth + 1, count, path);
        }
        HoverEvent hover = component.getHoverEvent();
        if (hover != null && hover.getAction() == HoverEvent.Action.SHOW_TEXT) {
            for (BaseComponent child : hoverText(hover)) validateTree(child, depth + 1, count, path);
        }
        path.remove(component);
    }

    @SuppressWarnings("deprecation")
    private static void rewrite(BaseComponent component, Map<String, String> tokens, Map<String, String> actionTokens, int depth) {
        if (component == null) return;
        if (component instanceof TextComponent text) {
            // The existing rich-text parser retains legacy colors inside its text node.
            // Convert that trusted node before restoring external values, preserving
            // the authored click/hover wrapper and existing child order.
            if (text.getText().indexOf('§') >= 0) {
                List<BaseComponent> children = new ArrayList<>();
                Collections.addAll(children, TextComponent.fromLegacyText(text.getText(), text.getColor()));
                if (text.getExtra() != null) children.addAll(text.getExtra());
                text.setText("");
                text.setExtra(children);
            }
            text.setText(restore(text.getText(), tokens, true));
        }
        component.setInsertion(restore(component.getInsertion(), actionTokens, false));
        if (component.getClickEvent() != null) {
            ClickEvent click = component.getClickEvent();
            component.setClickEvent(new ClickEvent(click.getAction(), restore(click.getValue(), actionTokens, false)));
        }
        HoverEvent hover = component.getHoverEvent();
        if (hover != null && hover.getAction() == HoverEvent.Action.SHOW_TEXT) {
            BaseComponent[] original = hoverText(hover);
            BaseComponent[] copy = new BaseComponent[original.length];
            for (int index = 0; index < original.length; index++) {
                copy[index] = original[index].duplicate();
                rewrite(copy[index], tokens, actionTokens, depth + 1);
            }
            component.setHoverEvent(new HoverEvent(hover.getAction(), copy));
        }
        if (component.getExtra() != null) {
            for (BaseComponent child : component.getExtra()) rewrite(child, tokens, actionTokens, depth + 1);
        }
    }

    /** Avoid linking modern hover-content classes on older supported clients/servers. */
    private static BaseComponent[] hoverText(HoverEvent hover) {
        try {
            Object legacy;
            try {
                legacy = hover.getClass().getMethod("getValue").invoke(hover);
                if (legacy instanceof BaseComponent[] components) return components;
                throw new IllegalArgumentException("unsupported legacy hover text");
            } catch (NoSuchMethodException modern) {
                Object contents = hover.getClass().getMethod("getContents").invoke(hover);
                if (!(contents instanceof List<?> list) || list.size() > MAX_COMPONENTS) {
                    throw new IllegalArgumentException("unsupported hover contents");
                }
                List<BaseComponent> components = new ArrayList<>();
                for (Object content : list) {
                    Object value = content.getClass().getMethod("getValue").invoke(content);
                    if (value instanceof BaseComponent[] array) Collections.addAll(components, array);
                    else if (value instanceof String text) {
                        if (text.length() > MAX_TEMPLATE) throw new IllegalArgumentException("hover text exceeds limits");
                        Collections.addAll(components, TextComponent.fromLegacyText(text));
                    } else throw new IllegalArgumentException("unsupported hover text value");
                    if (components.size() > MAX_COMPONENTS) throw new IllegalArgumentException("hover text exceeds limits");
                }
                return components.toArray(new BaseComponent[0]);
            }
        } catch (ReflectiveOperationException failure) {
            throw new IllegalArgumentException("hover text cannot be safely read", failure);
        }
    }

    private static String restore(String value, Map<String, String> tokens, boolean visible) {
        return restore(value, tokens, visible, false);
    }

    private static String restore(String value, Map<String, String> tokens, boolean visible, boolean legacySink) {
        if (value == null || value.isEmpty()) return value;
        StringBuilder result = new StringBuilder(value.length());
        int cursor = 0;
        while (cursor < value.length()) {
            int next = -1;
            Map.Entry<String, String> selected = null;
            for (Map.Entry<String, String> entry : tokens.entrySet()) {
                int position = value.indexOf(entry.getKey(), cursor);
                if (position >= 0 && (next < 0 || position < next)) {
                    next = position;
                    selected = entry;
                }
            }
            if (selected == null) break;
            result.append(value, cursor, next);
            // A trusted but incomplete section-sign code must not acquire its
            // missing character from an external value at a later legacy sink.
            if (legacySink && result.length() > 0 && result.charAt(result.length() - 1) == '§') {
                result.setLength(result.length() - 1);
                result.append("(section)");
            }
            if (result.length() + selected.getValue().length() > 262144)
                throw new IllegalArgumentException("literal text exceeds limits");
            String literal = selected.getValue();
            result.append(visible ? literal.replace("§", "(section)") : literal);
            cursor = next + selected.getKey().length();
        }
        return result.append(value, cursor, value.length()).toString();
    }
}
