package com.bencodez.advancedcore.api.javascript;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.script.ScriptEngine;

import org.bukkit.OfflinePlayer;

import com.bencodez.advancedcore.AdvancedCorePlugin;

import me.clip.placeholderapi.PlaceholderAPI;

/**
 * Resolves JavaScript placeholders without allowing placeholder output to become
 * executable source.
 * <p>
 * AdvancedCore asks the already-loaded Nashorn parser to identify whether each
 * placeholder is in executable code, a string, template text, or a regular
 * expression. This preserves existing JavaScript syntax without maintaining a
 * second JavaScript lexer inside AdvancedCore.
 */
public final class JavascriptPlaceholderBinder {
    private static final Pattern PLACEHOLDER = Pattern.compile("%([^%\\s]+)%|(?<!\\$)\\{([^{}%\\s]+)\\}");
    private static final Pattern INTEGER = Pattern.compile("[-+]?\\d+");
    private static final Pattern DECIMAL = Pattern
            .compile("[-+]?(?:\\d+\\.\\d*|\\d*\\.\\d+|\\d+)(?:[eE][-+]?\\d+)?");
    private static final String VARIABLE_PREFIX = "__advancedCorePlaceholder";
    private static final String PARSER_CLASS = "org.openjdk.nashorn.api.tree.Parser";
    private static final String DIAGNOSTIC_LISTENER_CLASS = "org.openjdk.nashorn.api.tree.DiagnosticListener";
    private static final String TREE_CLASS = "org.openjdk.nashorn.api.tree.Tree";
    private static final String TREE_PACKAGE = "org.openjdk.nashorn.api.tree";
    private static final Pattern FALLBACK_STRING = Pattern.compile("'(?:\\\\.|[^'\\\\])*'|\"(?:\\\\.|[^\"\\\\])*\"");
    private static final Pattern FALLBACK_TEMPLATE = Pattern.compile("`(?:\\\\.|[^`\\\\])*`");

    private JavascriptPlaceholderBinder() {
    }

    public static String bind(String expression, OfflinePlayer player, Map<String, String> placeholders,
            JavascriptEngine engine) {
        return bind(expression, token -> resolve(token, player, placeholders), engine::addToEngine);
    }

    static String bind(String expression, Function<String, String> resolver, BiConsumer<String, Object> bindings) {
        if (expression == null || expression.isEmpty()) {
            return expression;
        }

        Matcher matcher = PLACEHOLDER.matcher(expression);
        List<PlaceholderMatch> matches = new ArrayList<>();
        StringBuilder sanitized = new StringBuilder(expression);
        while (matcher.find()) {
            String token = matcher.group();
            String value = JavascriptPlaceholderValue.decode(token);
            if (value == null) {
                value = resolver.apply(token);
            }
            matches.add(new PlaceholderMatch(matcher.start(), matcher.end(), token, value));
            // Keep all source offsets unchanged while making resolved placeholders parse
            // as an ordinary identifier. Unresolved brace syntax may be valid JavaScript
            // (for example an object/block), so only sanitize brace placeholders when
            // they actually resolve as AdvancedCore custom data.
            boolean bracePlaceholder = token.charAt(0) == '{';
            if (!bracePlaceholder || (value != null && !value.equals(token))) {
                for (int i = matcher.start(); i < matcher.end(); i++) {
                    sanitized.setCharAt(i, 'p');
                }
            }
        }
        if (matches.isEmpty()) {
            return expression;
        }

        JavascriptContexts contexts = JavascriptContexts.parse(sanitized.toString());
        if (!contexts.parsed) {
            contexts = JavascriptContexts.fallback(sanitized.toString());
        }
        String[] replacements = new String[matches.size()];
        int bindingIndex = 0;
        for (int i = 0; i < matches.size(); i++) {
            PlaceholderMatch match = matches.get(i);
            if (match.value == null || match.value.equals(match.token)) {
                replacements[i] = match.token;
                continue;
            }

            Range regex = contexts.containing(contexts.regexes, match.start);
            Range string = contexts.containing(contexts.strings, match.start);
            Range template = contexts.containing(contexts.templates, match.start);
            if (regex != null) {
                replacements[i] = escapeRegex(match.value, expression, regex, match.start);
            } else if (template != null && !contexts.insideTemplateExpression(match.start)) {
                // Template text wins over quote-looking text inside the template. A value
                // containing ${...} must never become a live interpolation.
                replacements[i] = escapeTemplate(match.value);
            } else if (string != null) {
                char delimiter = literalDelimiter(expression, string);
                if (delimiter == '`' && !contexts.insideTemplateExpression(match.start)) {
                    replacements[i] = escapeTemplate(match.value);
                } else {
                    replacements[i] = escapeString(match.value, delimiter);
                }
            } else {
                String variable = VARIABLE_PREFIX + bindingIndex++;
                bindings.accept(variable, coerce(match.value));
                replacements[i] = variable;
            }
        }

        // Apply from right to left so source positions from the parser remain valid.
        StringBuilder result = new StringBuilder(expression);
        for (int i = matches.size() - 1; i >= 0; i--) {
            PlaceholderMatch match = matches.get(i);
            result.replace(match.start, match.end, replacements[i]);
        }
        return result.toString();
    }

    private static String resolve(String token, OfflinePlayer player, Map<String, String> placeholders) {
        AdvancedCorePlugin plugin = AdvancedCorePlugin.getInstance();

        // Preserve the historical replacement order: AdvancedCore custom/reward
        // placeholders win name collisions, then PlaceholderAPI is applied to the
        // selected custom value so custom placeholders may themselves contain PAPI.
        if (placeholders != null) {
            String name = token.substring(1, token.length() - 1);
            for (Entry<String, String> entry : placeholders.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(name)) {
                    String value = entry.getValue();
                    if (value != null && player != null && plugin != null && plugin.isPlaceHolderAPIEnabled()) {
                        String resolved = PlaceholderAPI.setPlaceholders(player, value);
                        if (resolved != null) {
                            value = resolved;
                        }
                    }
                    return value;
                }
            }
        }

        // Only consult PlaceholderAPI for the original token when no custom
        // placeholder with the same name was supplied.
        if (token.startsWith("%") && player != null && plugin != null && plugin.isPlaceHolderAPIEnabled()) {
            String resolved = PlaceholderAPI.setPlaceholders(player, token);
            if (resolved != null && !resolved.equals(token)) {
                return resolved;
            }
        }
        return token;
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

    private static char literalDelimiter(String expression, Range range) {
        int[] candidates = { range.start - 1, range.start, range.end, range.end - 1 };
        for (int candidate : candidates) {
            if (candidate < 0 || candidate >= expression.length()) {
                continue;
            }
            char value = expression.charAt(candidate);
            if (value == '\'' || value == '"' || value == '`') {
                return value;
            }
        }
        for (int i = Math.max(0, range.start - 2);
                i <= Math.min(expression.length() - 1, range.start + 1); i++) {
            char value = expression.charAt(i);
            if (value == '\'' || value == '"' || value == '`') {
                return value;
            }
        }
        return '\'';
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

    private static final class Range {
        private final int start;
        private final int end;

        private Range(long start, long end) {
            this.start = (int) start;
            this.end = (int) end;
        }

        private boolean contains(int position) {
            return position >= start && position < end;
        }
    }

    /**
     * Context ranges obtained from Nashorn's parser API. Parser classes are loaded
     * reflectively because AdvancedCore can download Nashorn into its own
     * URLClassLoader at runtime rather than putting it on the plugin classpath.
     */
    private static final class JavascriptContexts {
        private final List<Range> strings = new ArrayList<>();
        private final List<Range> regexes = new ArrayList<>();
        private final List<Range> templates = new ArrayList<>();
        private final List<Range> templateExpressions = new ArrayList<>();
        private boolean parsed;

        private static JavascriptContexts parse(String source) {
            JavascriptContexts contexts = new JavascriptContexts();
            try {
                ClassLoader loader = parserClassLoader();
                if (loader == null) {
                    return contexts;
                }

                Class<?> parserClass = Class.forName(PARSER_CLASS, true, loader);
                Class<?> diagnosticClass = Class.forName(DIAGNOSTIC_LISTENER_CLASS, true, loader);
                Class<?> treeClass = Class.forName(TREE_CLASS, true, loader);
                Object parser = createParser(parserClass);
                boolean[] hadParseDiagnostic = new boolean[1];
                Object diagnostic = Proxy.newProxyInstance(loader, new Class<?>[] { diagnosticClass },
                        (proxy, method, args) -> {
                            if ("report".equals(method.getName())) {
                                hadParseDiagnostic[0] = true;
                            }
                            return null;
                        });
                Method parse = parserClass.getMethod("parse", String.class, String.class, diagnosticClass);
                Object root = parse.invoke(parser, "AdvancedCore", source, diagnostic);
                if (root != null && !hadParseDiagnostic[0]) {
                    contexts.parsed = true;
                    walk(root, treeClass, contexts, new IdentityHashMap<>());
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // If a script cannot be parsed, expression placeholders still fall back to
                // engine bindings below. Placeholder output is never copied into source code.
            }
            contexts.sort();
            return contexts;
        }

        private static JavascriptContexts fallback(String source) {
            JavascriptContexts contexts = new JavascriptContexts();

            addFallbackTemplateRanges(source, 0, source.length(), contexts);

            // Quote-looking text is a string only outside template text. Strings inside
            // ${...} remain ordinary JavaScript strings and are tracked normally.
            addPatternRanges(source, FALLBACK_STRING, contexts.strings, contexts);
            addFallbackRegexRanges(source, contexts);
            contexts.sort();
            return contexts;
        }

        private static void addFallbackTemplateRanges(String source, int start, int limit,
                JavascriptContexts contexts) {
            for (int i = start; i < limit; i++) {
                char current = source.charAt(i);
                if (current == '\'' || current == '"') {
                    i = skipQuotedLiteral(source, i, limit, current);
                    continue;
                }
                if (current == '/' && canStartRegex(source, i)) {
                    int regexEnd = skipRegexLiteral(source, i, limit);
                    if (regexEnd > i) {
                        i = regexEnd;
                        continue;
                    }
                }
                if (current != '`') {
                    continue;
                }

                int templateEnd = skipTemplateLiteral(source, i, limit);
                if (templateEnd <= i || templateEnd >= source.length() || source.charAt(templateEnd) != '`') {
                    continue;
                }

                Range template = new Range(i, templateEnd + 1);
                contexts.templates.add(template);
                List<Range> expressions = new ArrayList<>();
                addFallbackTemplateExpressions(source, template, expressions);
                contexts.templateExpressions.addAll(expressions);

                // Nested templates live inside an outer ${...}. Scan each interpolation
                // recursively so their text ranges override the enclosing expression.
                for (Range expression : expressions) {
                    addFallbackTemplateRanges(source, expression.start, expression.end, contexts);
                }
                i = templateEnd;
            }
        }

        private static void addFallbackRegexRanges(String source, JavascriptContexts contexts) {
            for (int i = 0; i < source.length(); i++) {
                if (source.charAt(i) != '/' || contexts.containing(contexts.strings, i) != null
                        || contexts.isTemplateText(i) || !canStartRegex(source, i)) {
                    continue;
                }

                boolean escaped = false;
                boolean characterClass = false;
                for (int j = i + 1; j < source.length(); j++) {
                    char current = source.charAt(j);
                    if (current == '\r' || current == '\n') {
                        break;
                    }
                    if (escaped) {
                        escaped = false;
                        continue;
                    }
                    if (current == '\\') {
                        escaped = true;
                        continue;
                    }
                    if (current == '[') {
                        characterClass = true;
                        continue;
                    }
                    if (current == ']') {
                        characterClass = false;
                        continue;
                    }
                    if (current != '/' || characterClass) {
                        continue;
                    }

                    int end = j + 1;
                    while (end < source.length() && "dgimsuvy".indexOf(source.charAt(end)) >= 0) {
                        end++;
                    }
                    Range candidate = new Range(i, end);
                    // A regex literal may legitimately contain quote characters. Since the
                    // opening slash was already proven to be outside a string/template and
                    // can start a regex, discard fallback string ranges fully contained by
                    // this regex instead of letting quote-looking regex text win.
                    contexts.removeContained(contexts.strings, candidate);
                    contexts.regexes.add(candidate);
                    i = end - 1;
                    break;
                }
            }
        }

        private static boolean canStartRegex(String source, int slashIndex) {
            int previousIndex = slashIndex - 1;
            while (previousIndex >= 0 && Character.isWhitespace(source.charAt(previousIndex))) {
                previousIndex--;
            }
            if (previousIndex < 0) {
                return true;
            }

            char previous = source.charAt(previousIndex);
            if ("([{:;,=!?&|+-*%^~<>".indexOf(previous) >= 0) {
                return true;
            }
            if (previous == ')' && closesControlStatementHead(source, previousIndex)) {
                return true;
            }

            if (Character.isJavaIdentifierPart(previous)) {
                int end = previousIndex + 1;
                int start = previousIndex;
                while (start >= 0 && Character.isJavaIdentifierPart(source.charAt(start))) {
                    start--;
                }
                String word = source.substring(start + 1, end);
                return word.equals("return") || word.equals("case") || word.equals("throw")
                        || word.equals("else") || word.equals("do") || word.equals("yield")
                        || word.equals("await") || word.equals("typeof") || word.equals("void")
                        || word.equals("delete") || word.equals("instanceof") || word.equals("in")
                        || word.equals("new");
            }
            return false;
        }

        private static boolean closesControlStatementHead(String source, int closeParen) {
            List<Integer> openingParens = new ArrayList<>();
            for (int i = 0; i <= closeParen; i++) {
                char current = source.charAt(i);
                if (current == '\'' || current == '"') {
                    i = skipQuotedLiteral(source, i, closeParen + 1, current);
                    continue;
                }
                if (current == '`') {
                    i = skipTemplateLiteral(source, i, closeParen + 1);
                    continue;
                }
                if (current == '/' && canStartRegex(source, i)) {
                    int regexEnd = skipRegexLiteral(source, i, closeParen + 1);
                    if (regexEnd > i) {
                        i = regexEnd;
                        continue;
                    }
                }
                if (current == '(') {
                    openingParens.add(i);
                } else if (current == ')') {
                    if (openingParens.isEmpty()) {
                        return false;
                    }
                    int openingParen = openingParens.remove(openingParens.size() - 1);
                    if (i == closeParen) {
                        return isControlKeywordBefore(source, openingParen);
                    }
                }
            }
            return false;
        }

        private static boolean isControlKeywordBefore(String source, int openingParen) {
            int end = openingParen - 1;
            while (end >= 0 && Character.isWhitespace(source.charAt(end))) {
                end--;
            }
            if (end < 0 || !Character.isJavaIdentifierPart(source.charAt(end))) {
                return false;
            }

            int start = end;
            while (start >= 0 && Character.isJavaIdentifierPart(source.charAt(start))) {
                start--;
            }
            String word = source.substring(start + 1, end + 1);
            if (word.equals("if") || word.equals("while") || word.equals("for") || word.equals("with")
                    || word.equals("switch") || word.equals("catch")) {
                return true;
            }

            // Modern JavaScript may use `for await (...)`.
            if (!word.equals("await")) {
                return false;
            }
            end = start;
            while (end >= 0 && Character.isWhitespace(source.charAt(end))) {
                end--;
            }
            start = end;
            while (start >= 0 && Character.isJavaIdentifierPart(source.charAt(start))) {
                start--;
            }
            return end >= 0 && source.substring(start + 1, end + 1).equals("for");
        }

        private static void addPatternRanges(String source, Pattern pattern, List<Range> target,
                JavascriptContexts existing) {
            Matcher matcher = pattern.matcher(source);
            while (matcher.find()) {
                Range candidate = new Range(matcher.start(), matcher.end());
                if (existing == null || !existing.isTemplateText(candidate.start)) {
                    target.add(candidate);
                }
            }
        }

        private static void addFallbackTemplateExpressions(String source, Range template, List<Range> target) {
            boolean escaped = false;
            for (int i = template.start + 1; i < template.end - 1; i++) {
                char current = source.charAt(i);
                if (escaped) {
                    escaped = false;
                    continue;
                }
                if (current == '\\') {
                    escaped = true;
                    continue;
                }
                if (current == '$' && i + 1 < template.end && source.charAt(i + 1) == '{') {
                    int expressionStart = i + 2;
                    int expressionEnd = findTemplateExpressionEnd(source, expressionStart, template.end - 1);
                    if (expressionEnd >= 0) {
                        target.add(new Range(expressionStart, expressionEnd));
                        i = expressionEnd;
                    }
                }
            }
        }

        private static int findTemplateExpressionEnd(String source, int start, int limit) {
            int depth = 1;
            for (int i = start; i < limit; i++) {
                char current = source.charAt(i);
                if (current == '\'' || current == '"') {
                    i = skipQuotedLiteral(source, i, limit, current);
                    continue;
                }
                if (current == '`') {
                    i = skipTemplateLiteral(source, i, limit);
                    continue;
                }
                if (current == '/' && canStartRegex(source, i)) {
                    int regexEnd = skipRegexLiteral(source, i, limit);
                    if (regexEnd > i) {
                        i = regexEnd;
                        continue;
                    }
                }
                if (current == '{') {
                    depth++;
                } else if (current == '}') {
                    depth--;
                    if (depth == 0) {
                        return i;
                    }
                }
            }
            return -1;
        }

        private static int skipQuotedLiteral(String source, int start, int limit, char quote) {
            boolean escaped = false;
            for (int i = start + 1; i < limit; i++) {
                char current = source.charAt(i);
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == quote) {
                    return i;
                }
            }
            return limit - 1;
        }

        private static int skipTemplateLiteral(String source, int start, int limit) {
            boolean escaped = false;
            for (int i = start + 1; i < limit; i++) {
                char current = source.charAt(i);
                if (escaped) {
                    escaped = false;
                    continue;
                }
                if (current == '\\') {
                    escaped = true;
                    continue;
                }
                if (current == '$' && i + 1 < limit && source.charAt(i + 1) == '{') {
                    int expressionEnd = findTemplateExpressionEnd(source, i + 2, limit);
                    if (expressionEnd >= 0) {
                        i = expressionEnd;
                        continue;
                    }
                }
                if (current == '`') {
                    return i;
                }
            }
            return limit - 1;
        }

        private static int skipRegexLiteral(String source, int start, int limit) {
            boolean escaped = false;
            boolean characterClass = false;
            for (int i = start + 1; i < limit; i++) {
                char current = source.charAt(i);
                if (current == '\r' || current == '\n') {
                    return start;
                }
                if (escaped) {
                    escaped = false;
                    continue;
                }
                if (current == '\\') {
                    escaped = true;
                    continue;
                }
                if (current == '[') {
                    characterClass = true;
                } else if (current == ']') {
                    characterClass = false;
                } else if (current == '/' && !characterClass) {
                    int end = i;
                    while (end + 1 < limit && "dgimsuvy".indexOf(source.charAt(end + 1)) >= 0) {
                        end++;
                    }
                    return end;
                }
            }
            return start;
        }

        private boolean overlapsLiteral(Range candidate) {
            return overlaps(strings, candidate) || overlaps(templates, candidate);
        }

        private boolean overlaps(List<Range> ranges, Range candidate) {
            for (Range range : ranges) {
                if (candidate.start < range.end && range.start < candidate.end) {
                    return true;
                }
            }
            return false;
        }

        private void removeContained(List<Range> ranges, Range container) {
            ranges.removeIf(range -> range.start >= container.start && range.end <= container.end);
        }

        private static Object createParser(Class<?> parserClass) throws ReflectiveOperationException {
            for (Method method : parserClass.getMethods()) {
                if (!method.getName().equals("create") || !Modifier.isStatic(method.getModifiers())) {
                    continue;
                }
                if (method.getParameterCount() == 0) {
                    return method.invoke(null);
                }
                if (method.getParameterCount() == 1 && method.getParameterTypes()[0].isArray()
                        && method.getParameterTypes()[0].getComponentType() == String.class) {
                    return method.invoke(null, (Object) new String[] { "--language=es6" });
                }
            }
            throw new NoSuchMethodException("Nashorn Parser.create");
        }

        private static ClassLoader parserClassLoader() {
            JavascriptEngineHandler handler = JavascriptEngineHandler.getInstance();
            ClassLoader downloaded = handler.getNashornClassLoader();
            if (canLoadParser(downloaded)) {
                return downloaded;
            }

            // Use a parser already visible to AdvancedCore when available. If not,
            // the handler can prepare a dedicated runtime Nashorn parser loader below.
            ClassLoader own = JavascriptPlaceholderBinder.class.getClassLoader();
            if (canLoadParser(own)) {
                return own;
            }

            ScriptEngine cached = handler.getCachedEngine();
            ClassLoader cachedLoader = cached == null ? null : cached.getClass().getClassLoader();
            if (canLoadParser(cachedLoader)) {
                return cachedLoader;
            }

            ClassLoader prepared = handler.getOrCreateNashornParserClassLoader();
            return canLoadParser(prepared) ? prepared : null;
        }

        private static boolean canLoadParser(ClassLoader loader) {
            if (loader == null) {
                return false;
            }
            try {
                Class.forName(PARSER_CLASS, false, loader);
                return true;
            } catch (ClassNotFoundException | LinkageError ignored) {
                return false;
            }
        }

        private static void walk(Object node, Class<?> treeClass, JavascriptContexts contexts,
                IdentityHashMap<Object, Boolean> visited) {
            if (node == null || !treeClass.isInstance(node) || visited.put(node, Boolean.TRUE) != null) {
                return;
            }

            String kind = stringValue(invokeTreeMethod(node, "getKind"));
            long start = longValue(invokeTreeMethod(node, "getStartPosition"));
            long end = longValue(invokeTreeMethod(node, "getEndPosition"));
            if (start >= 0 && end >= start) {
                if ("STRING_LITERAL".equals(kind)) {
                    contexts.strings.add(new Range(start, end));
                } else if (kind != null && kind.contains("REGEXP")) {
                    contexts.regexes.add(new Range(start, end));
                } else if ("TEMPLATE_LITERAL".equals(kind)) {
                    contexts.templates.add(new Range(start, end));
                    Object expressions = invokeTreeMethod(node, "getExpressions");
                    if (expressions instanceof Iterable<?>) {
                        for (Object expression : (Iterable<?>) expressions) {
                            long expressionStart = longValue(invokeTreeMethod(expression, "getStartPosition"));
                            long expressionEnd = longValue(invokeTreeMethod(expression, "getEndPosition"));
                            if (expressionStart >= 0 && expressionEnd >= expressionStart) {
                                contexts.templateExpressions.add(new Range(expressionStart, expressionEnd));
                            }
                        }
                    }
                }
            }

            for (Method method : treeApiMethods(node.getClass())) {
                if (method.getParameterCount() != 0 || Modifier.isStatic(method.getModifiers())) {
                    continue;
                }
                String name = method.getName();
                if (name.equals("getKind") || name.equals("getStartPosition") || name.equals("getEndPosition")
                        || name.equals("getSourceName") || name.equals("getClass")) {
                    continue;
                }
                try {
                    Object value = method.invoke(node);
                    walkValue(value, treeClass, contexts, visited);
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                }
            }
        }

        private static void walkValue(Object value, Class<?> treeClass, JavascriptContexts contexts,
                IdentityHashMap<Object, Boolean> visited) {
            if (value == null) {
                return;
            }
            if (treeClass.isInstance(value)) {
                walk(value, treeClass, contexts, visited);
            } else if (value instanceof Iterable<?>) {
                for (Object element : (Iterable<?>) value) {
                    if (treeClass.isInstance(element)) {
                        walk(element, treeClass, contexts, visited);
                    }
                }
            } else if (value.getClass().isArray()) {
                int length = Array.getLength(value);
                for (int i = 0; i < length; i++) {
                    Object element = Array.get(value, i);
                    if (treeClass.isInstance(element)) {
                        walk(element, treeClass, contexts, visited);
                    }
                }
            }
        }

        private static Set<Method> treeApiMethods(Class<?> type) {
            LinkedHashSet<Method> methods = new LinkedHashSet<>();
            collectTreeApiMethods(type, methods, new LinkedHashSet<>());
            return methods;
        }

        private static void collectTreeApiMethods(Class<?> type, Set<Method> methods, Set<Class<?>> visited) {
            if (type == null || !visited.add(type)) {
                return;
            }
            for (Class<?> iface : type.getInterfaces()) {
                Package pkg = iface.getPackage();
                if (pkg != null && TREE_PACKAGE.equals(pkg.getName())) {
                    for (Method method : iface.getMethods()) {
                        methods.add(method);
                    }
                }
                collectTreeApiMethods(iface, methods, visited);
            }
            collectTreeApiMethods(type.getSuperclass(), methods, visited);
        }

        private static Object invokeTreeMethod(Object node, String methodName) {
            if (node == null) {
                return null;
            }
            for (Method method : treeApiMethods(node.getClass())) {
                if (method.getName().equals(methodName) && method.getParameterCount() == 0) {
                    try {
                        return method.invoke(node);
                    } catch (ReflectiveOperationException | RuntimeException ignored) {
                        return null;
                    }
                }
            }
            return null;
        }

        private static String stringValue(Object value) {
            return value == null ? null : value.toString();
        }

        private static long longValue(Object value) {
            return value instanceof Number ? ((Number) value).longValue() : -1;
        }

        private Range containing(List<Range> ranges, int position) {
            for (Range range : ranges) {
                if (range.contains(position)) {
                    return range;
                }
            }
            return null;
        }

        private boolean insideTemplateExpression(int position) {
            Range expression = innermostContaining(templateExpressions, position);
            if (expression == null) {
                return false;
            }
            Range template = innermostContaining(templates, position);
            // An enclosing template expression must not override the text context of a
            // nested template literal that starts later inside that expression.
            return template == null || expression.start > template.start;
        }

        private boolean isTemplateText(int position) {
            return innermostContaining(templates, position) != null && !insideTemplateExpression(position);
        }

        private Range innermostContaining(List<Range> ranges, int position) {
            Range best = null;
            for (Range range : ranges) {
                if (!range.contains(position)) {
                    continue;
                }
                if (best == null || (range.end - range.start) < (best.end - best.start)) {
                    best = range;
                }
            }
            return best;
        }

        private void sort() {
            Comparator<Range> comparator = Comparator.comparingInt(range -> range.start);
            strings.sort(comparator);
            regexes.sort(comparator);
            templates.sort(comparator);
            templateExpressions.sort(comparator);
        }
    }
}
