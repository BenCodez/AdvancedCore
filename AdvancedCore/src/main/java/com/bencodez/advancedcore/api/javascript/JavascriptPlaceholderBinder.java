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
    private static final Pattern FALLBACK_REGEX = Pattern.compile("/(?:\\\\.|[^/\\\\\\r\\n])+/[dgimsuvy]*");

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
            contexts = JavascriptContexts.fallback(expression);
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
            } else if (string != null) {
                char delimiter = literalDelimiter(expression, string);
                if (delimiter == '`' && !contexts.insideTemplateExpression(match.start)) {
                    replacements[i] = escapeTemplate(match.value);
                } else {
                    replacements[i] = escapeString(match.value, delimiter);
                }
            } else if (template != null && !contexts.insideTemplateExpression(match.start)) {
                replacements[i] = escapeTemplate(match.value);
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
        // PlaceholderAPI uses percent-delimited placeholders. Brace-delimited tokens
        // are AdvancedCore's legacy custom placeholder form and are resolved below.
        if (token.startsWith("%") && player != null && plugin != null && plugin.isPlaceHolderAPIEnabled()) {
            String resolved = PlaceholderAPI.setPlaceholders(player, token);
            if (resolved != null && !resolved.equals(token)) {
                return resolved;
            }
        }

        if (placeholders != null) {
            String name = token.substring(1, token.length() - 1);
            for (Entry<String, String> entry : placeholders.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(name)) {
                    return entry.getValue();
                }
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
            addPatternRanges(source, FALLBACK_STRING, contexts.strings, null);

            Matcher templates = FALLBACK_TEMPLATE.matcher(source);
            while (templates.find()) {
                Range template = new Range(templates.start(), templates.end());
                contexts.templates.add(template);
                addFallbackTemplateExpressions(source, template, contexts.templateExpressions);
            }

            addPatternRanges(source, FALLBACK_REGEX, contexts.regexes, contexts);
            contexts.sort();
            return contexts;
        }

        private static void addPatternRanges(String source, Pattern pattern, List<Range> target,
                JavascriptContexts existing) {
            Matcher matcher = pattern.matcher(source);
            while (matcher.find()) {
                Range candidate = new Range(matcher.start(), matcher.end());
                if (existing == null || !existing.overlapsLiteral(candidate)) {
                    target.add(candidate);
                }
            }
        }

        private static void addFallbackTemplateExpressions(String source, Range template, List<Range> target) {
            boolean escaped = false;
            int expressionStart = -1;
            int depth = 0;
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
                if (expressionStart < 0) {
                    if (current == '$' && i + 1 < template.end && source.charAt(i + 1) == '{') {
                        expressionStart = i + 2;
                        depth = 1;
                        i++;
                    }
                    continue;
                }
                if (current == '{') {
                    depth++;
                } else if (current == '}') {
                    depth--;
                    if (depth == 0) {
                        target.add(new Range(expressionStart, i));
                        expressionStart = -1;
                    }
                }
            }
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

            // nashorn-core is packaged with AdvancedCore so parser support remains
            // available even when the active ScriptEngine is Rhino/GraalJS.
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
            return containing(templateExpressions, position) != null;
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
