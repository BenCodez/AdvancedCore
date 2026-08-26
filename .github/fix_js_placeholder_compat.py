from pathlib import Path


def replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected 1 match, got {count}")
    return text.replace(old, new, 1)


binder = Path("AdvancedCore/src/main/java/com/bencodez/advancedcore/api/javascript/JavascriptPlaceholderBinder.java")
text = binder.read_text()

text = replace_once(
    text,
    '    private static final Pattern PLACEHOLDER = Pattern.compile("%([^%\\\\s]+)%");',
    '    private static final Pattern PLACEHOLDER = Pattern.compile("%([^%\\\\s]+)%|(?<!\\\\$)\\\\{([^{}%\\\\s]+)\\\\}");',
    "placeholder pattern",
)

old = '''            matches.add(new PlaceholderMatch(matcher.start(), matcher.end(), token, value));
            // Keep all source offsets unchanged while making a bare %placeholder%
            // parse as an ordinary identifier.
            for (int i = matcher.start(); i < matcher.end(); i++) {
                sanitized.setCharAt(i, 'p');
            }
'''
new = '''            matches.add(new PlaceholderMatch(matcher.start(), matcher.end(), token, value));
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
'''
text = replace_once(text, old, new, "sanitization")

old = '''            if (regex != null) {
                replacements[i] = escapeRegex(match.value, expression, regex, match.start);
            } else if (string != null) {
                replacements[i] = escapeString(match.value, expression.charAt(string.start));
            } else if (template != null && !contexts.insideTemplateExpression(match.start)) {
                replacements[i] = escapeTemplate(match.value);
            } else {
'''
new = '''            if (regex != null) {
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
'''
text = replace_once(text, old, new, "context block")

old = '''    private static String resolve(String token, OfflinePlayer player, Map<String, String> placeholders) {
        AdvancedCorePlugin plugin = AdvancedCorePlugin.getInstance();
        if (player != null && plugin != null && plugin.isPlaceHolderAPIEnabled()) {
            String resolved = PlaceholderAPI.setPlaceholders(player, token);
            if (resolved != null && !resolved.equals(token)) {
                return resolved;
            }
        }

        if (placeholders != null) {
            String name = token.substring(1, token.length() - 1);
'''
new = '''    private static String resolve(String token, OfflinePlayer player, Map<String, String> placeholders) {
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
'''
text = replace_once(text, old, new, "resolve block")

marker = '''    private static String escapeString(String value, char quote) {
'''
helper = '''    private static char literalDelimiter(String expression, Range range) {
        int[] candidates = { range.start - 1, range.start, range.end, range.end - 1 };
        for (int candidate : candidates) {
            if (candidate < 0 || candidate >= expression.length()) {
                continue;
            }
            char value = expression.charAt(candidate);
            if (value == '\\'' || value == '"' || value == '`') {
                return value;
            }
        }
        for (int i = Math.max(0, range.start - 2);
                i <= Math.min(expression.length() - 1, range.start + 1); i++) {
            char value = expression.charAt(i);
            if (value == '\\'' || value == '"' || value == '`') {
                return value;
            }
        }
        return '\\'';
    }

'''
text = replace_once(text, marker, helper + marker, "escape helper marker")

old = '''        private static ClassLoader parserClassLoader() {
            JavascriptEngineHandler handler = JavascriptEngineHandler.getInstance();
            if (handler.getNashornClassLoader() != null) {
                return handler.getNashornClassLoader();
            }
            ScriptEngine cached = handler.getCachedEngine();
            if (cached != null && cached.getClass().getClassLoader() != null) {
                return cached.getClass().getClassLoader();
            }
            ClassLoader own = JavascriptPlaceholderBinder.class.getClassLoader();
            try {
                Class.forName(PARSER_CLASS, false, own);
                return own;
            } catch (ClassNotFoundException ignored) {
                return null;
            }
        }
'''
new = '''        private static ClassLoader parserClassLoader() {
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
            return canLoadParser(cachedLoader) ? cachedLoader : null;
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
'''
text = replace_once(text, old, new, "parser loader")
binder.write_text(text)

pom = Path("AdvancedCore/pom.xml")
text = pom.read_text()
old = '''        <dependency>
            <groupId>org.openjdk.nashorn</groupId>
            <artifactId>nashorn-core</artifactId>
            <version>15.7</version>
            <scope>provided</scope>
        </dependency>'''
new = '''        <dependency>
            <groupId>org.openjdk.nashorn</groupId>
            <artifactId>nashorn-core</artifactId>
            <version>15.7</version>
            <scope>compile</scope>
        </dependency>'''
text = replace_once(text, old, new, "nashorn dependency")
pom.write_text(text)

test = Path("AdvancedCore/src/test/java/com/bencodez/advancedcore/api/javascript/JavascriptPlaceholderBinderTest.java")
text = test.read_text()
insert = '''
    @Test
    void exactQuotedNumericLookingPlaceholderRemainsAString() {
        HashMap<String, Object> bindings = new HashMap<>();

        String prepared = JavascriptPlaceholderBinder.bind("'%code%' === '001'", ignored -> "001", bindings::put);

        assertEquals("'001' === '001'", prepared);
        assertTrue(bindings.isEmpty());
    }

    @Test
    void braceDelimitedCustomPlaceholderIsAutomaticallyBound() {
        HashMap<String, Object> bindings = new HashMap<>();

        String prepared = JavascriptPlaceholderBinder.bind("{count} > 0",
                token -> token.equals("{count}") ? "5" : token, bindings::put);

        assertEquals("__advancedCorePlaceholder0 > 0", prepared);
        assertEquals(Long.valueOf(5), bindings.get("__advancedCorePlaceholder0"));
    }

    @Test
    void unresolvedBraceSyntaxRemainsOrdinaryJavascript() {
        HashMap<String, Object> bindings = new HashMap<>();

        String prepared = JavascriptPlaceholderBinder.bind("var value = {count: 1}; %name%",
                token -> token.equals("%name%") ? "Ben" : token, bindings::put);

        assertEquals("var value = {count: 1}; __advancedCorePlaceholder0", prepared);
        assertEquals("Ben", bindings.get("__advancedCorePlaceholder0"));
    }

'''
marker = '''
    @Test
    void unresolvedTokensRemainUntouched() {
'''
text = replace_once(text, marker, "\n" + insert + marker.lstrip("\n"), "test marker")
test.write_text(text)
