package com.bencodez.advancedcore.api.javascript;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.OfflinePlayer;
import org.mozilla.javascript.CompilerEnvirons;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Parser;
import org.mozilla.javascript.ast.AstRoot;
import org.mozilla.javascript.ast.Comment;
import org.mozilla.javascript.ast.RegExpLiteral;
import org.mozilla.javascript.ast.StringLiteral;
import org.mozilla.javascript.ast.TemplateCharacters;

import com.bencodez.advancedcore.AdvancedCorePlugin;

import me.clip.placeholderapi.PlaceholderAPI;

/**
 * Resolves placeholders inside a known, operator-authored JavaScript segment.
 * Dynamic values in executable expression context are engine bindings; values
 * inside existing literals are escaped for that literal. Rhino supplies the AST
 * classification, so AdvancedCore does not maintain a JavaScript lexer.
 */
public final class JavascriptPlaceholderBinder {
    // Keep the percent-token range aligned with PlaceholderAPI. Resolution below
    // decides whether a percent-delimited candidate is really a placeholder; this
    // is important because ordinary JavaScript such as 10%3%2 has the same shape.
    private static final Pattern PLACEHOLDER = Pattern.compile("%([^%]+)%|(?<!\\$)\\{([^{}%]+)\\}");
    private static final Pattern INTEGER = Pattern.compile("[-+]?\\d+");
    private static final Pattern DECIMAL = Pattern
            .compile("[-+]?(?:\\d+\\.\\d*|\\d*\\.\\d+|\\d+)(?:[eE][-+]?\\d+)?");
    private static final String VARIABLE_PREFIX = "__advancedCorePlaceholder";

    private JavascriptPlaceholderBinder() {
    }

    public static String bind(String expression, OfflinePlayer player, Map<String, String> placeholders,
            JavascriptEngine engine) {
        return bind(expression, token -> resolve(token, player, placeholders),
                value -> resolvePapiValue(value, player), engine::addToEngine);
    }

    static String bind(String expression, Function<String, String> resolver, BiConsumer<String, Object> bindings) {
        return bind(expression, resolver, Function.identity(), bindings);
    }

    private static String bind(String expression, Function<String, String> resolver,
            Function<String, String> decodedResolver, BiConsumer<String, Object> bindings) {
        if (expression == null || expression.isEmpty()) {
            return expression;
        }

        Matcher matcher = PLACEHOLDER.matcher(expression);
        List<PlaceholderMatch> matches = new ArrayList<>();
        StringBuilder sanitized = new StringBuilder(expression);
        int searchFrom = 0;
        while (searchFrom < expression.length() && matcher.find(searchFrom)) {
            String token = matcher.group();
            String decoded = JavascriptPlaceholderValue.decode(token);
            String value = decoded == null ? resolver.apply(token) : decodedResolver.apply(decoded);

            // Percent signs are also JavaScript modulo operators, and brace-delimited
            // text may be an object or block. Do not parse or rewrite a candidate that
            // the configured placeholder sources did not actually resolve.
            if (value == null || (decoded == null && value.equals(token))) {
                // Retry after this opening delimiter instead of after the candidate's
                // closing delimiter. An unresolved modulo-shaped candidate can overlap
                // the opening percent of a real placeholder later in the expression.
                searchFrom = matcher.start() + 1;
                continue;
            }
            matches.add(new PlaceholderMatch(matcher.start(), matcher.end(), token, value));
            for (int i = matcher.start(); i < matcher.end(); i++) {
                sanitized.setCharAt(i, 'p');
            }
            searchFrom = matcher.end();
        }
        if (matches.isEmpty()) {
            return expression;
        }

        JavascriptContexts contexts = JavascriptContexts.parse(sanitized.toString());
        String[] replacements = new String[matches.size()];
        int[] replacementStarts = new int[matches.size()];
        int bindingIndex = 0;
        for (int i = 0; i < matches.size(); i++) {
            PlaceholderMatch match = matches.get(i);
            replacementStarts[i] = match.start;
            if (contexts.containing(contexts.comments, match.start) != null) {
                replacements[i] = match.token;
                continue;
            }

            Range regex = contexts.containing(contexts.regexes, match.start);
            LiteralRange string = contexts.containingLiteral(match.start);
            Range templateText = contexts.containing(contexts.templateText, match.start);
            if (regex != null) {
                replacementStarts[i] = literalReplacementStart(expression, match.start);
                replacements[i] = escapeRegex(match.value, expression, regex, match.start);
            } else if (templateText != null) {
                replacementStarts[i] = literalReplacementStart(expression, match.start);
                replacements[i] = escapeTemplate(match.value);
            } else if (string != null) {
                replacementStarts[i] = literalReplacementStart(expression, match.start);
                replacements[i] = escapeString(match.value, string.quote);
            } else {
                String variable = VARIABLE_PREFIX + bindingIndex++;
                bindings.accept(variable, coerce(match.value));
                replacements[i] = variable;
            }
        }

        StringBuilder result = new StringBuilder(expression);
        for (int i = matches.size() - 1; i >= 0; i--) {
            PlaceholderMatch match = matches.get(i);
            result.replace(replacementStarts[i], match.end, replacements[i]);
        }
        return result.toString();
    }

    /**
     * An odd authored backslash immediately before a placeholder already escapes
     * the placeholder's first character. Consume that pending escape before
     * inserting a separately escaped value. Otherwise the authored slash and the
     * value's leading escape can pair off and reactivate a quote, template
     * interpolation, or regex delimiter.
     */
    private static int literalReplacementStart(String expression, int placeholderStart) {
        int slashRunStart = placeholderStart;
        while (slashRunStart > 0 && expression.charAt(slashRunStart - 1) == '\\') {
            slashRunStart--;
        }
        return ((placeholderStart - slashRunStart) & 1) == 1 ? placeholderStart - 1 : placeholderStart;
    }

    private static String resolve(String token, OfflinePlayer player, Map<String, String> placeholders) {
        if (placeholders != null) {
            String name = token.substring(1, token.length() - 1);
            for (Entry<String, String> entry : placeholders.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(name)) {
                    return resolvePapiValue(entry.getValue(), player);
                }
            }
        }

        AdvancedCorePlugin plugin = AdvancedCorePlugin.getInstance();
        if (token.startsWith("%") && player != null && plugin != null && plugin.isPlaceHolderAPIEnabled()) {
            String resolved = PlaceholderAPI.setPlaceholders(player, token);
            if (resolved != null && !resolved.equals(token)) {
                return resolved;
            }
        }
        return token;
    }

    private static String resolvePapiValue(String value, OfflinePlayer player) {
        AdvancedCorePlugin plugin = AdvancedCorePlugin.getInstance();
        if (value != null && player != null && plugin != null && plugin.isPlaceHolderAPIEnabled()) {
            String resolved = PlaceholderAPI.setPlaceholders(player, value);
            if (resolved != null) {
                return resolved;
            }
        }
        return value;
    }

    private static Object coerce(String value) {
        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
            return Boolean.valueOf(value);
        }
        if (INTEGER.matcher(value).matches()) {
            try {
                return Long.valueOf(value);
            } catch (NumberFormatException ignored) {
            }
        }
        if (DECIMAL.matcher(value).matches()) {
            try {
                return Double.valueOf(value);
            } catch (NumberFormatException ignored) {
            }
        }
        return value;
    }

    private static String escapeString(String value, char quote) {
        StringBuilder result = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            switch (current) {
            case '\\':
                result.append("\\\\");
                break;
            case '\n':
                result.append("\\n");
                break;
            case '\r':
                result.append("\\r");
                break;
            case '\u2028':
                result.append("\\u2028");
                break;
            case '\u2029':
                result.append("\\u2029");
                break;
            default:
                if (current == quote) {
                    result.append('\\');
                }
                result.append(current);
                break;
            }
        }
        return result.toString();
    }

    private static String escapeTemplate(String value) {
        return value.replace("\\", "\\\\").replace("`", "\\`").replace("${", "\\${")
                .replace("\r", "\\r").replace("\n", "\\n").replace("\u2028", "\\u2028")
                .replace("\u2029", "\\u2029");
    }

    private static String escapeRegex(String value, String expression, Range regex, int placeholderStart) {
        boolean characterClass = false;
        boolean escaped = false;
        for (int i = regex.start + 1; i < placeholderStart; i++) {
            char current = expression.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (current == '\\') {
                escaped = true;
            } else if (current == '[') {
                characterClass = true;
            } else if (current == ']') {
                characterClass = false;
            }
        }

        String special = characterClass ? "\\/]^-" : "\\/.*+?^${}()|[]";
        StringBuilder result = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current == '\n') {
                result.append("\\n");
            } else if (current == '\r') {
                result.append("\\r");
            } else if (current == '\u2028') {
                result.append("\\u2028");
            } else if (current == '\u2029') {
                result.append("\\u2029");
            } else {
                if (special.indexOf(current) >= 0) {
                    result.append('\\');
                }
                result.append(current);
            }
        }
        return result.toString();
    }

    private static final class PlaceholderMatch {
        private final int start;
        private final int end;
        private final String token;
        private final String value;

        private PlaceholderMatch(int start, int end, String token, String value) {
            this.start = start;
            this.end = end;
            this.token = token;
            this.value = value;
        }
    }

    private static class Range {
        private final int start;
        private final int end;

        private Range(int start, int end) {
            this.start = start;
            this.end = end;
        }

        final boolean contains(int position) {
            return position >= start && position < end;
        }
    }

    private static final class LiteralRange extends Range {
        private final char quote;

        private LiteralRange(int start, int end, char quote) {
            super(start, end);
            this.quote = quote;
        }
    }

    private static final class JavascriptContexts {
        private final List<LiteralRange> strings = new ArrayList<>();
        private final List<Range> regexes = new ArrayList<>();
        private final List<Range> templateText = new ArrayList<>();
        private final List<Range> comments = new ArrayList<>();

        private static JavascriptContexts parse(String source) {
            try {
                CompilerEnvirons environs = new CompilerEnvirons();
                environs.setLanguageVersion(Context.VERSION_ES6);
                environs.setRecordingComments(true);
                environs.setRecordingLocalJsDocComments(true);
                environs.setRecoverFromErrors(false);

                AstRoot root = new Parser(environs).parse(source, "AdvancedCore", 1);
                JavascriptContexts contexts = new JavascriptContexts();
                root.visit(node -> {
                    int start = node.getAbsolutePosition();
                    int end = start + node.getLength();
                    if (node instanceof StringLiteral) {
                        contexts.strings.add(new LiteralRange(start, end,
                                ((StringLiteral) node).getQuoteCharacter()));
                    } else if (node instanceof RegExpLiteral) {
                        contexts.regexes.add(new Range(start, end));
                    } else if (node instanceof TemplateCharacters) {
                        contexts.templateText.add(new Range(start, end));
                    }
                    return true;
                });

                if (root.getComments() != null) {
                    for (Comment comment : root.getComments()) {
                        int start = comment.getAbsolutePosition();
                        contexts.comments.add(new Range(start, start + comment.getLength()));
                    }
                }
                contexts.sort();
                return contexts;
            } catch (RuntimeException e) {
                throw new IllegalArgumentException(
                        "Unable to safely parse JavaScript containing placeholders: " + e.getMessage(), e);
            }
        }

        private Range containing(List<Range> ranges, int position) {
            for (Range range : ranges) {
                if (range.contains(position)) {
                    return range;
                }
            }
            return null;
        }

        private LiteralRange containingLiteral(int position) {
            for (LiteralRange range : strings) {
                if (range.contains(position)) {
                    return range;
                }
            }
            return null;
        }

        private void sort() {
            Comparator<Range> comparator = Comparator.comparingInt(range -> range.start);
            strings.sort(comparator);
            regexes.sort(comparator);
            templateText.sort(comparator);
            comments.sort(comparator);
        }
    }
}
