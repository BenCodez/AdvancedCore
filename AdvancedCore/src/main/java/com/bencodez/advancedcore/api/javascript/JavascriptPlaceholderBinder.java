package com.bencodez.advancedcore.api.javascript;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedSet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.OfflinePlayer;
import org.mozilla.javascript.CompilerEnvirons;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Parser;
import org.mozilla.javascript.ast.AstRoot;
import org.mozilla.javascript.ast.Comment;
import org.mozilla.javascript.ast.ErrorCollector;
import org.mozilla.javascript.ast.ParseProblem;
import org.mozilla.javascript.ast.RegExpLiteral;
import org.mozilla.javascript.ast.StringLiteral;
import org.mozilla.javascript.ast.TemplateCharacters;

import com.bencodez.advancedcore.AdvancedCorePlugin;

import me.clip.placeholderapi.PlaceholderAPI;

/**
 * Resolves placeholders in an already-authored JavaScript source block.
 * Placeholder values in executable expression positions become engine bindings;
 * values in JavaScript literals are escaped for that literal. Rhino's parser is
 * the sole source-context classifier. Invalid source fails closed rather than
 * entering a heuristic lexer fallback.
 */
final class JavascriptPlaceholderBinder {
    private static final Pattern PLACEHOLDER = Pattern.compile("%([^%\\s]+)%|(?<!\\$)\\{([^{}%\\s]+)\\}");
    private static final Pattern INTEGER = Pattern.compile("[-+]?\\d+");
    private static final Pattern DECIMAL = Pattern
            .compile("[-+]?(?:\\d+\\.\\d*|\\d*\\.\\d+|\\d+)(?:[eE][-+]?\\d+)?");
    private static final AtomicLong EVALUATION_SEQUENCE = new AtomicLong();

    private JavascriptPlaceholderBinder() {
    }

    static PreparedJavascript prepare(String source, OfflinePlayer player, Map<String, String> placeholders) {
        return prepare(source, token -> resolve(token, player, placeholders),
                value -> resolvePapiValue(value, player));
    }

    static PreparedJavascript prepare(String source, Function<String, String> resolver) {
        return prepare(source, resolver, Function.identity());
    }

    private static PreparedJavascript prepare(String source, Function<String, String> resolver,
            Function<String, String> decodedResolver) {
        if (source == null || source.isEmpty()) {
            return new PreparedJavascript(source, Map.of());
        }

        Matcher matcher = PLACEHOLDER.matcher(source);
        List<PlaceholderMatch> matches = new ArrayList<>();
        StringBuilder sanitized = new StringBuilder(source);
        while (matcher.find()) {
            String token = matcher.group();
            String value = JavascriptPlaceholderValue.decode(token);
            if (value == null) {
                value = resolver.apply(token);
            } else {
                value = decodedResolver.apply(value);
            }
            matches.add(new PlaceholderMatch(matcher.start(), matcher.end(), token, value));

            boolean braceToken = token.charAt(0) == '{';
            boolean resolved = value != null && !value.equals(token);
            if (!braceToken || resolved) {
                for (int index = matcher.start(); index < matcher.end(); index++) {
                    sanitized.setCharAt(index, 'p');
                }
            }
        }

        if (matches.isEmpty()) {
            validate(source);
            return new PreparedJavascript(source, Map.of());
        }

        List<SourceRange> ranges = parseRanges(sanitized.toString());
        Map<String, Object> bindings = new LinkedHashMap<>();
        String[] replacements = new String[matches.size()];
        long evaluationId = EVALUATION_SEQUENCE.incrementAndGet();
        int bindingIndex = 0;

        for (int index = 0; index < matches.size(); index++) {
            PlaceholderMatch match = matches.get(index);
            if (match.value == null || match.value.equals(match.token)) {
                replacements[index] = match.token;
                continue;
            }

            SourceRange range = innermostContaining(ranges, match.start);
            if (range != null && range.type == RangeType.COMMENT) {
                replacements[index] = match.token;
            } else if (range != null && range.type == RangeType.STRING) {
                replacements[index] = escapeString(match.value, range.quote);
            } else if (range != null && range.type == RangeType.TEMPLATE) {
                replacements[index] = escapeTemplate(match.value);
            } else if (range != null && range.type == RangeType.REGEX) {
                replacements[index] = escapeRegex(match.value, source, range, match.start);
            } else {
                String variable = "__advancedCorePlaceholder_" + evaluationId + "_" + bindingIndex++;
                bindings.put(variable, coerce(match.value));
                replacements[index] = variable;
            }
        }

        StringBuilder prepared = new StringBuilder(source);
        for (int index = matches.size() - 1; index >= 0; index--) {
            PlaceholderMatch match = matches.get(index);
            prepared.replace(match.start, match.end, replacements[index]);
        }
        return new PreparedJavascript(prepared.toString(), bindings);
    }

    private static void validate(String source) {
        parseRanges(source);
    }

    private static List<SourceRange> parseRanges(String source) {
        CompilerEnvirons environment = new CompilerEnvirons();
        environment.setLanguageVersion(Context.VERSION_ES6);
        environment.setRecordingComments(true);
        environment.setRecordingLocalJsDocComments(true);
        environment.setRecoverFromErrors(false);

        ErrorCollector errors = new ErrorCollector();
        environment.setErrorReporter(errors);

        AstRoot root;
        try {
            root = new Parser(environment, errors).parse(source, "AdvancedCore", 1);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Unsupported or invalid JavaScript: " + exception.getMessage(), exception);
        }
        if (!errors.getErrors().isEmpty()) {
            ParseProblem problem = errors.getErrors().get(0);
            throw new IllegalArgumentException("Unsupported or invalid JavaScript at offset " + problem.getFileOffset()
                    + ": " + problem.getMessage());
        }

        List<SourceRange> ranges = new ArrayList<>();
        root.visit(node -> {
            if (node instanceof StringLiteral literal) {
                ranges.add(new SourceRange(node.getAbsolutePosition(), node.getAbsolutePosition() + node.getLength(),
                        RangeType.STRING, literal.getQuoteCharacter()));
            } else if (node instanceof TemplateCharacters) {
                ranges.add(new SourceRange(node.getAbsolutePosition(), node.getAbsolutePosition() + node.getLength(),
                        RangeType.TEMPLATE, '\0'));
            } else if (node instanceof RegExpLiteral) {
                ranges.add(new SourceRange(node.getAbsolutePosition(), node.getAbsolutePosition() + node.getLength(),
                        RangeType.REGEX, '\0'));
            }
            return true;
        });

        SortedSet<Comment> comments = root.getComments();
        if (comments != null) {
            for (Comment comment : comments) {
                ranges.add(new SourceRange(comment.getAbsolutePosition(),
                        comment.getAbsolutePosition() + comment.getLength(), RangeType.COMMENT, '\0'));
            }
        }
        ranges.sort(Comparator.comparingInt((SourceRange range) -> range.start)
                .thenComparingInt(range -> range.end - range.start));
        return ranges;
    }

    private static SourceRange innermostContaining(List<SourceRange> ranges, int position) {
        SourceRange selected = null;
        for (SourceRange range : ranges) {
            if (!range.contains(position)) {
                continue;
            }
            if (selected == null || range.length() < selected.length()) {
                selected = range;
            }
        }
        return selected;
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

        if (token.startsWith("%")) {
            String resolved = resolvePapiValue(token, player);
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
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
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

    private static String escapeRegex(String value, String source, SourceRange regex, int placeholderStart) {
        boolean characterClass = false;
        boolean escaped = false;
        for (int index = regex.start + 1; index < placeholderStart; index++) {
            char current = source.charAt(index);
            if (escaped) {
                escaped = false;
            } else if (current == '\\') {
                escaped = true;
            } else if (current == '[') {
                characterClass = true;
            } else if (current == ']') {
                characterClass = false;
            }
        }

        String special = characterClass ? "\\/[]^-" : "\\/.*+?^${}()|[]";
        StringBuilder result = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
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

    static final class PreparedJavascript {
        private final String source;
        private final Map<String, Object> bindings;

        private PreparedJavascript(String source, Map<String, Object> bindings) {
            this.source = source;
            this.bindings = Map.copyOf(bindings);
        }

        String getSource() {
            return source;
        }

        Map<String, Object> getBindings() {
            return bindings;
        }
    }

    private enum RangeType {
        STRING,
        TEMPLATE,
        REGEX,
        COMMENT
    }

    private static final class SourceRange {
        private final int start;
        private final int end;
        private final RangeType type;
        private final char quote;

        private SourceRange(int start, int end, RangeType type, char quote) {
            this.start = start;
            this.end = end;
            this.type = type;
            this.quote = quote;
        }

        private boolean contains(int position) {
            return position >= start && position < end;
        }

        private int length() {
            return end - start;
        }
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
}
