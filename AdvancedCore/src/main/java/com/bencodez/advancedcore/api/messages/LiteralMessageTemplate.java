package com.bencodez.advancedcore.api.messages;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;

import java.util.concurrent.ThreadLocalRandom;
import java.util.IdentityHashMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
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
import java.util.function.Supplier;

/**
 * Keeps literal placeholder values out of the formatting and component parsers.
 * Values are inserted only after the trusted template has been parsed.
 */
public final class LiteralMessageTemplate {
    private static final int MAX_TEMPLATE = 65536;
    private static final int MAX_VALUES = 4096;
    private static final int MAX_BINDINGS = 8192;
    private static final int MAX_BINDING_CHARACTERS = 1048576;
    private static final int MAX_LITERAL_KEY = 128;
    private static final int MAX_ACTION_KEY = 1024;
    private static final Pattern LITERAL_KEY = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,127}");
    private static final int MAX_VALUE = 8192;
    private static final int MAX_COMPONENTS = 4096;
    private static final int MAX_RESULT = 262144;
    private static final String TOKEN_PREFIX = "VCORELITERAL";
    // Bounds retained collision metadata even if callers repeatedly format an existing Prepared.
    private static final int MAX_FORBIDDEN_TOKENS = 131072;
    private static final Pattern EXACT_ATTRIBUTE = Pattern.compile(
            "(?i)(?<![A-Za-z0-9_])(command|url|suggest_command)\\s*=\\s*\"([^\"]*)\"");

    private LiteralMessageTemplate() {
    }

    public static Prepared prepare(String template, Map<String, String> literalValues, Map<String, String> actionValues) {
        String source = template == null ? "" : template;
        if (source.length() > MAX_TEMPLATE) {
            throw new IllegalArgumentException("template is too large");
        }
        validateBindings(literalValues, actionValues);
        TokenIndex tokenIndex = new TokenIndex(Set.of());
        tokenIndex.addCorpus(source);
        for (Map<String, String> bindings : java.util.Arrays.asList(literalValues, actionValues)) {
            if (bindings != null) bindings.forEach((key, value) -> {
                tokenIndex.addCorpus(key);
                tokenIndex.addCorpus(value);
            });
        }
        Set<String> referenced = literalMarkerKeys(source);
        Map<String, String> values = new LinkedHashMap<>();
        Set<String> keys = new HashSet<>();
        if (literalValues != null) {
            for (Map.Entry<String, String> entry : literalValues.entrySet()) {
                String key = Objects.requireNonNull(entry.getKey(), "literal key");
                if (!keys.add(key.toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("duplicate literal key");
                // A summary may have many sites but only a few occur in this line.
                // Unreferenced bindings never enter a parser or the restoration map.
                if (!referenced.contains(key.toLowerCase(Locale.ROOT))) continue;
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
            if (key.length() > MAX_ACTION_KEY) throw new IllegalArgumentException("invalid action key");
            if (exact.containsKey(key)) throw new IllegalArgumentException("duplicate action key");
            exact.put(key, entry.getValue());
        }
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String token = newToken(tokenIndex);
            replacements.put(entry.getKey(), token);
            tokens.put(token, entry.getValue());
            String action = exact.getOrDefault(entry.getKey(), "");
            if (action == null || action.length() > MAX_VALUE) throw new IllegalArgumentException("invalid action value");
            actionTokens.put(token, action);
        }

        // Match the existing JavaScript parser's authored marker/first-closing-bracket
        // grammar. A script containing literal display data is displayed as text;
        // it is never evaluated with externally supplied program fragments.
        MarkerMatcher literalMarkers = new MarkerMatcher(values);
        MarkerMatcher replacementMarkers = new MarkerMatcher(replacements);
        Map<String, String> scriptActions = new LinkedHashMap<>();
        for (String key : values.keySet()) scriptActions.put(key, exact.getOrDefault(key, ""));
        MarkerMatcher scriptActionMarkers = new MarkerMatcher(scriptActions);
        StringBuilder protectedScripts = new StringBuilder(source.length());
        int cursor = 0;
        int scriptTextCharacters = 0;
        int scriptActionCharacters = 0;
        while (cursor < source.length()) {
            int start = indexOfIgnoreCase(source, "[Javascript=", cursor);
            if (start < 0) break;
            int end = source.indexOf(']', start + "[Javascript=".length());
            if (end < 0) break;
            String block = source.substring(start, end + 1);
            String substituted = literalMarkers.replace(block, MAX_RESULT);
            protectedScripts.append(source, cursor, start);
            if (!block.equals(replacementMarkers.replace(block))) {
                if (tokens.size() >= MAX_VALUES) throw new IllegalArgumentException("too many literal script segments");
                String rawAction = scriptActionMarkers.replace(block, MAX_RESULT);
                scriptTextCharacters += substituted.length();
                scriptActionCharacters += rawAction.length();
                if (scriptTextCharacters > MAX_RESULT || scriptActionCharacters > MAX_RESULT)
                    throw new IllegalArgumentException("literal scripts exceed limits");
                tokenIndex.addCorpus(substituted);
                tokenIndex.addCorpus(rawAction);
                String token = newToken(tokenIndex);
                tokens.put(token, substituted);
                // The visible script is kept as text, while an action containing the
                // same protected block must retain the exact raw values (or empty
                // values when no raw action was supplied).  Only literal bindings
                // are substituted here; unrelated provider markers remain for the
                // normal action/provider pass.
                actionTokens.put(token, rawAction);
                protectedScripts.append(token);
            } else {
                protectedScripts.append(block);
            }
            cursor = end + 1;
        }
        protectedScripts.append(source, cursor, source.length());
        String protectedActions = protectExactAttributes(protectedScripts.toString(), values, exact, tokens, actionTokens,
                tokenIndex);
        return Prepared.create(replacementMarkers.replace(protectedActions), tokens, actionTokens, tokenIndex, 0);
    }

    /** Shield raw legacy display overrides only in administrator-authored click attributes. */
    private static String protectExactAttributes(String source, Map<String, String> literalValues,
            Map<String, String> exactValues, Map<String, String> tokens, Map<String, String> actionTokens,
            TokenIndex tokenIndex) {
        Map<String, String> replacements = new LinkedHashMap<>();
        Map<String, String> actionOnlyValues = new LinkedHashMap<>();
        exactValues.forEach((key, value) -> { if (!literalValues.containsKey(key)) actionOnlyValues.put(key, value); });
        MarkerMatcher exactMarkers = new MarkerMatcher(actionOnlyValues);
        Map<String, Integer> bindingOrder = new HashMap<>();
        int ordinal = 0;
        for (String key : exactValues.keySet()) bindingOrder.put(key, ordinal++);
        mapExactAttributes(source, value -> {
            Set<String> referenced = new HashSet<>();
            exactMarkers.collectKeys(value, referenced);
            List<String> ordered = new ArrayList<>(referenced);
            ordered.sort(java.util.Comparator.comparingInt(bindingOrder::get));
            // Preserve first-use admission across attributes, then input-map order
            // within each attribute, without scanning its text once per binding.
            for (String key : ordered) {
                if (literalValues.containsKey(key) || replacements.containsKey(key)) continue;
                String exact = exactValues.get(key);
                if (exact == null) throw new IllegalArgumentException("invalid action value");
                if (tokens.size() >= MAX_VALUES) throw new IllegalArgumentException("too many action values");
                String token = newToken(tokenIndex);
                replacements.put(key, token);
                tokens.put(token, exact);
                actionTokens.put(token, exact);
            }
            return value;
        });
        MarkerMatcher replacementMarkers = new MarkerMatcher(replacements);
        return mapExactAttributes(source, replacementMarkers::replace);
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

    private static String newToken(TokenIndex index) {
        return newToken(index, () -> TOKEN_PREFIX + Long.toUnsignedString(ThreadLocalRandom.current().nextLong(), 36)
                + "Q" + Long.toUnsignedString(ThreadLocalRandom.current().nextLong(), 36) + "Z");
    }

    private static String newToken(TokenIndex index, Supplier<String> candidates) {
        for (int attempts = 0; attempts < 16; attempts++) {
            // No percent signs or underscores: this cannot introduce a PAPI provider
            // token. The first character cannot complete a legacy color code.
            String token = candidates.get();
            if (index.reserve(token)) return token;
        }
        throw new IllegalStateException("literal token allocation failed");
    }

    /** Recognize exactly the existing random-token grammar, including tokens embedded in other text. */
    private static int tokenEnd(CharSequence source, int start) {
        if (start >= source.length() || source.charAt(start) != TOKEN_PREFIX.charAt(0)
                || source.length() - start < TOKEN_PREFIX.length() + 4) return -1;
        for (int offset = 1; offset < TOKEN_PREFIX.length(); offset++) {
            if (source.charAt(start + offset) != TOKEN_PREFIX.charAt(offset)) return -1;
        }
        int cursor = start + TOKEN_PREFIX.length();
        for (int part = 0; part < 2; part++) {
            char delimiter = part == 0 ? 'Q' : 'Z';
            int digits = 0;
            while (cursor < source.length()) {
                char character = source.charAt(cursor++);
                if (character == delimiter) {
                    if (digits == 0) return -1;
                    break;
                }
                if (++digits > 13 || !((character >= '0' && character <= '9')
                        || (character >= 'a' && character <= 'z'))) return -1;
                if (cursor == source.length()) return -1;
            }
            if (digits == 0 || source.charAt(cursor - 1) != delimiter) return -1;
        }
        return cursor;
    }

    /** A shared immutable corpus plus operation-local additions; never rescan it for each allocation. */
    private static final class TokenIndex {
        private final Set<String> corpus;
        private final Set<String> additions = new HashSet<>();

        private TokenIndex(Set<String> corpus) {
            this.corpus = corpus;
        }

        private boolean reserve(String token) {
            if (corpus.contains(token) || additions.contains(token)) return false;
            if (corpus.size() + additions.size() >= MAX_FORBIDDEN_TOKENS)
                throw new IllegalArgumentException("literal token corpus exceeds limits");
            additions.add(token);
            return true;
        }

        private void addCorpus(CharSequence source) {
            if (source == null) return;
            for (int cursor = 0; cursor < source.length();) {
                int end = tokenEnd(source, cursor);
                if (end < 0) cursor++;
                else {
                    reserve(source.subSequence(cursor, end).toString());
                    cursor = end;
                }
            }
        }

        private Set<String> snapshot() {
            if (additions.isEmpty()) return corpus;
            Set<String> snapshot = new HashSet<>(corpus);
            snapshot.addAll(additions);
            return Collections.unmodifiableSet(snapshot);
        }
    }

    private static int indexOfIgnoreCase(String source, String target, int from) {
        for (int index = from; index <= source.length() - target.length(); index++) {
            if (source.regionMatches(true, index, target, 0, target.length())) return index;
        }
        return -1;
    }

    /** Admit bounded input before constructing indexes or scanning the template. */
    private static void validateBindings(Map<String, String> literals, Map<String, String> actions) {
        int characters = 0;
        for (Map<String, String> bindings : java.util.Arrays.asList(literals, actions)) {
            if (bindings == null) continue;
            if (bindings.size() > MAX_BINDINGS) throw new IllegalArgumentException("too many literal bindings");
            int count = 0;
            for (Map.Entry<String, String> entry : bindings.entrySet()) {
                if (++count > MAX_BINDINGS) throw new IllegalArgumentException("too many literal bindings");
                String key = Objects.requireNonNull(entry.getKey(), "binding key");
                String value = entry.getValue();
                if (bindings == literals) {
                    if (!LITERAL_KEY.matcher(key).matches() || value == null)
                        throw new IllegalArgumentException("invalid literal value");
                } else if (key.isEmpty() || key.length() > MAX_ACTION_KEY) {
                    throw new IllegalArgumentException("invalid action key");
                }
                if (value != null && value.length() > MAX_VALUE)
                    throw new IllegalArgumentException("invalid binding value");
                characters += key.length() + (value == null ? 0 : value.length());
                if (characters > MAX_BINDING_CHARACTERS)
                    throw new IllegalArgumentException("literal bindings exceed character limits");
            }
        }
    }

    private static char foldCase(char value) {
        // Preserve Java case-insensitive ASCII equivalents, including long s,
        // dotless i and the Kelvin sign, without Locale string expansions.
        return Character.toLowerCase(Character.toUpperCase(value));
    }

    /** Index valid literal markers once, including markers sharing a percent boundary. */
    private static Set<String> literalMarkerKeys(CharSequence source) {
        Set<String> keys = new HashSet<>();
        for (int start = 0; start < source.length(); start++) {
            char opening = source.charAt(start);
            if (opening != '%' && opening != '{') continue;
            char closing = opening == '%' ? '%' : '}';
            StringBuilder key = new StringBuilder();
            for (int cursor = start + 1; cursor < source.length() && cursor <= start + MAX_LITERAL_KEY + 1; cursor++) {
                char character = source.charAt(cursor);
                if (character == closing) {
                    if (!key.isEmpty()) keys.add(key.toString());
                    break;
                }
                character = foldCase(character);
                boolean letter = character >= 'a' && character <= 'z';
                boolean digit = character >= '0' && character <= '9';
                if ((!letter && character != '_' && (key.isEmpty() || !digit)) || key.length() == MAX_LITERAL_KEY) break;
                key.append(character);
            }
        }
        return keys;
    }

    /** One bounded key index per replacement lane, never one source scan per binding. */
    private static final class MarkerMatcher {
        private final MarkerNode root = new MarkerNode();

        private MarkerMatcher(Map<String, String> values) {
            int order = 0;
            for (Map.Entry<String, String> entry : values.entrySet()) {
                MarkerNode node = root;
                for (int index = 0; index < entry.getKey().length();) {
                    int character = entry.getKey().codePointAt(index);
                    index += Character.charCount(character);
                    character = Character.toLowerCase(Character.toUpperCase(character));
                    if (node.children == null) node.children = new HashMap<>();
                    node = node.children.computeIfAbsent(character, ignored -> new MarkerNode());
                }
                // Case-equivalent non-ASCII action keys retain insertion-order priority.
                if (node.key == null) {
                    node.key = entry.getKey();
                    node.value = entry.getValue();
                    node.order = order;
                }
                order++;
            }
        }

        private MarkerNode match(CharSequence source, int start, Set<String> keys) {
            char opening = source.charAt(start);
            if (opening != '%' && opening != '{') return null;
            char closing = opening == '%' ? '%' : '}';
            MarkerNode node = root;
            MarkerNode selected = null;
            for (int cursor = start + 1; cursor < source.length();) {
                int character = Character.codePointAt(source, cursor);
                if (character == closing && node.key != null) {
                    if (keys != null) keys.add(node.key);
                    if (selected == null || node.order < selected.order) selected = node;
                }
                cursor += Character.charCount(character);
                character = Character.toLowerCase(Character.toUpperCase(character));
                node = node.children == null ? null : node.children.get(character);
                if (node == null) break;
            }
            return selected;
        }

        private void collectKeys(String source, Set<String> keys) {
            for (int cursor = 0; cursor < source.length(); cursor++) match(source, cursor, keys);
        }

        /** Replace only original markers; inserted values are never scanned again. */
        private String replace(String source) {
            return replace(source, Integer.MAX_VALUE);
        }

        private String replace(String source, int limit) {
            StringBuilder result = new StringBuilder(source.length());
            int cursor = 0;
            while (cursor < source.length()) {
                MarkerNode selected = match(source, cursor, null);
                if (selected == null) {
                    if (result.length() >= limit) throw new IllegalArgumentException("literal scripts exceed limits");
                    result.append(source.charAt(cursor++));
                }
                else {
                    if (selected.value.length() > limit - result.length())
                        throw new IllegalArgumentException("literal scripts exceed limits");
                    result.append(selected.value);
                    cursor += selected.key.length() + 2;
                }
            }
            return result.toString();
        }
    }

    private static final class MarkerNode {
        private Map<Integer, MarkerNode> children;
        private String key;
        private String value;
        private int order;
    }

    private static ProtectedBoundaries protectSectionTokenBoundaries(String source, Map<String, String> tokens,
            Map<String, String> actionTokens, TokenIndex tokenIndex) {
        if (source.indexOf('§') < 0 || tokens.isEmpty()) return new ProtectedBoundaries(source, tokens, actionTokens);
        String guard = null;
        StringBuilder protectedText = new StringBuilder(source.length());
        for (int index = 0; index < source.length(); index++) {
            if (source.charAt(index) == '§') {
                int following = index + 1;
                int end = tokenEnd(source, following);
                if (end >= 0 && tokens.containsKey(source.substring(following, end))) {
                    if (guard == null) {
                        // A section-sign label may have a different raw action value.
                        // Reuse a boundary token only when both restoration lanes agree.
                        for (Map.Entry<String, String> entry : tokens.entrySet()) {
                            if (entry.getValue().equals("§") && "§".equals(actionTokens.get(entry.getKey()))) {
                                guard = entry.getKey();
                                break;
                            }
                        }
                        if (guard == null) {
                            guard = newToken(tokenIndex);
                            tokens = new LinkedHashMap<>(tokens);
                            actionTokens = new LinkedHashMap<>(actionTokens);
                            tokens.put(guard, "§");
                            actionTokens.put(guard, "§");
                            tokens = Collections.unmodifiableMap(tokens);
                            actionTokens = Collections.unmodifiableMap(actionTokens);
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
        return new ProtectedBoundaries(protectedText.toString(), tokens, actionTokens);
    }

    private record ProtectedBoundaries(String template, Map<String, String> tokens, Map<String, String> actionTokens) {
    }

    public static final class Prepared {
        private final String template;
        private final Map<String, String> tokens;
        private final Map<String, String> actionTokens;
        private final Set<String> forbiddenTokens;
        private final int formattedActionCharacters;

        /** All maps passed here are immutable and can be shared by lines and recipients. */
        private Prepared(String template, Map<String, String> tokens, Map<String, String> actionTokens,
                TokenIndex tokenIndex, int formattedActionCharacters) {
            tokenIndex.addCorpus(template);
            ProtectedBoundaries protectedText = protectSectionTokenBoundaries(template, tokens, actionTokens, tokenIndex);
            this.template = protectedText.template();
            this.tokens = protectedText.tokens();
            this.actionTokens = protectedText.actionTokens();
            this.forbiddenTokens = tokenIndex.snapshot();
            this.formattedActionCharacters = formattedActionCharacters;
        }

        /** Publish newly owned maps; no mutable index or map survives into Prepared. */
        private static Prepared create(String template, Map<String, String> tokens, Map<String, String> actions,
                TokenIndex tokenIndex, int formattedActionCharacters) {
            tokens.forEach(actions::putIfAbsent);
            return new Prepared(template, Collections.unmodifiableMap(tokens), Collections.unmodifiableMap(actions),
                    tokenIndex, formattedActionCharacters);
        }

        public String template() {
            return template;
        }

        /** Split authored message separators while labels are still opaque. */
        public List<Prepared> splitLines() {
            List<Prepared> lines = new ArrayList<>();
            for (String line : template.split("(?i)" + java.util.regex.Pattern.quote("%NewLine%"))) {
                lines.add(new Prepared(line, tokens, actionTokens, new TokenIndex(forbiddenTokens), formattedActionCharacters));
            }
            return List.copyOf(lines);
        }

        /** Continue trusted formatting while retaining the same literal bindings. */
        public Prepared withTemplate(String formattedTemplate) {
            if (formattedTemplate == null || formattedTemplate.length() > MAX_TEMPLATE) {
                throw new IllegalArgumentException("formatted template is invalid");
            }
            return new Prepared(formattedTemplate, tokens, actionTokens, new TokenIndex(forbiddenTokens), formattedActionCharacters);
        }

        /** Render authored action values once, then keep their exact output out of the component parser. */
        public Prepared protectFormattedActions(Function<String, String> renderer) {
            Objects.requireNonNull(renderer, "renderer");
            final class FormattedBindings {
                private Map<String, String> bindings = tokens;
                private Map<String, String> actions = actionTokens;
                private int characters = formattedActionCharacters;
                private boolean changed;
            }
            FormattedBindings formatted = new FormattedBindings();
            TokenIndex tokenIndex = new TokenIndex(forbiddenTokens);
            String protectedTemplate = mapExactAttributes(template, value -> {
                String rendered = restore(renderer.apply(value), actionTokens, false);
                if (rendered == null || rendered.length() > MAX_RESULT - formatted.characters)
                    throw new IllegalArgumentException("formatted action exceeds limits");
                formatted.characters += rendered.length();
                if (formatted.bindings.size() >= MAX_VALUES) throw new IllegalArgumentException("too many formatted actions");
                if (!formatted.changed) {
                    formatted.bindings = new LinkedHashMap<>(tokens);
                    formatted.actions = new LinkedHashMap<>(actionTokens);
                    formatted.changed = true;
                }
                tokenIndex.addCorpus(rendered);
                String token = newToken(tokenIndex);
                formatted.bindings.put(token, rendered);
                formatted.actions.put(token, rendered);
                return token;
            });
            if (!formatted.changed) return this;
            return create(protectedTemplate, formatted.bindings, formatted.actions, tokenIndex, formatted.characters);
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
        if (count[1] > MAX_RESULT) throw new IllegalArgumentException("literal component text exceeds limits");
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
        return restoreTokens(value, tokens, visible, legacySink);
    }

    /** Scan only the parser's original output, never the literal strings appended to it. */
    private static String restoreTokens(CharSequence value, Map<String, String> tokens, boolean visible, boolean legacySink) {
        if (value == null || value.isEmpty()) return value == null ? null : "";
        StringBuilder result = new StringBuilder(Math.min(value.length(), MAX_RESULT));
        int cursor = 0;
        int copied = 0;
        while (cursor < value.length()) {
            int end = tokenEnd(value, cursor);
            if (end < 0) {
                cursor++;
                continue;
            }
            String literal = tokens.get(value.subSequence(cursor, end).toString());
            if (literal == null) {
                cursor = end;
                continue;
            }
            appendBounded(result, value, copied, cursor);
            // A trusted but incomplete section-sign code must not acquire its
            // missing character from an external value at a later legacy sink.
            if (legacySink && result.length() > 0 && result.charAt(result.length() - 1) == '§') {
                result.setLength(result.length() - 1);
                appendBounded(result, "(section)", 0, "(section)".length());
            }
            int start = 0;
            if (visible) {
                for (int section = literal.indexOf('§'); section >= 0; section = literal.indexOf('§', start)) {
                    appendBounded(result, literal, start, section);
                    appendBounded(result, "(section)", 0, "(section)".length());
                    start = section + 1;
                }
            }
            appendBounded(result, literal, start, literal.length());
            cursor = end;
            copied = end;
        }
        appendBounded(result, value, copied, value.length());
        return result.toString();
    }

    private static void appendBounded(StringBuilder result, CharSequence source, int start, int end) {
        if (end - start > MAX_RESULT - result.length())
            throw new IllegalArgumentException("literal text exceeds limits");
        result.append(source, start, end);
    }
}
