from pathlib import Path

binder_path = Path("AdvancedCore/src/main/java/com/bencodez/advancedcore/api/javascript/JavascriptPlaceholderBinder.java")
modern_test_path = Path("AdvancedCore/src/test/java/com/bencodez/advancedcore/api/javascript/JavascriptPlaceholderModernSyntaxFallbackTest.java")
priority_test_path = Path("AdvancedCore/src/test/java/com/bencodez/advancedcore/api/javascript/JavascriptPlaceholderResolutionPriorityTest.java")

binder = binder_path.read_text()


def replace_once(text, old, new, label):
    if old not in text:
        raise SystemExit(f"missing expected block: {label}")
    return text.replace(old, new, 1)

binder = replace_once(
    binder,
    '''    public static String bind(String expression, OfflinePlayer player, Map<String, String> placeholders,\n            JavascriptEngine engine) {\n        return bind(expression, token -> resolve(token, player, placeholders), engine::addToEngine);\n    }\n\n    static String bind(String expression, Function<String, String> resolver, BiConsumer<String, Object> bindings) {\n        if (expression == null || expression.isEmpty()) {\n''',
    '''    public static String bind(String expression, OfflinePlayer player, Map<String, String> placeholders,\n            JavascriptEngine engine) {\n        return bind(expression, token -> resolve(token, player, placeholders),\n                value -> resolvePapiValue(value, player), engine::addToEngine);\n    }\n\n    static String bind(String expression, Function<String, String> resolver, BiConsumer<String, Object> bindings) {\n        return bind(expression, resolver, Function.identity(), bindings);\n    }\n\n    private static String bind(String expression, Function<String, String> resolver,\n            Function<String, String> decodedResolver, BiConsumer<String, Object> bindings) {\n        if (expression == null || expression.isEmpty()) {\n''',
    "binder overload")

binder = replace_once(
    binder,
    '''            String token = matcher.group();\n            String value = JavascriptPlaceholderValue.decode(token);\n            if (value == null) {\n                value = resolver.apply(token);\n            }\n''',
    '''            String token = matcher.group();\n            String value = JavascriptPlaceholderValue.decode(token);\n            if (value == null) {\n                value = resolver.apply(token);\n            } else {\n                // Values encoded by PlaceholderUtils are already known to be data, but\n                // they may still contain PlaceholderAPI tokens from legacy custom -> PAPI\n                // replacement chains. Resolve those tokens before escaping/binding.\n                value = decodedResolver.apply(value);\n            }\n''',
    "decoded resolver")

binder = replace_once(
    binder,
    '''                    String value = entry.getValue();\n                    if (value != null && player != null && plugin != null && plugin.isPlaceHolderAPIEnabled()) {\n                        String resolved = PlaceholderAPI.setPlaceholders(player, value);\n                        if (resolved != null) {\n                            value = resolved;\n                        }\n                    }\n                    return value;\n''',
    '''                    return resolvePapiValue(entry.getValue(), player);\n''',
    "custom papi helper")

binder = replace_once(
    binder,
    '''        return token;\n    }\n\n    private static Object coerce(String value) {\n''',
    '''        return token;\n    }\n\n    private static String resolvePapiValue(String value, OfflinePlayer player) {\n        AdvancedCorePlugin plugin = AdvancedCorePlugin.getInstance();\n        if (value != null && player != null && plugin != null && plugin.isPlaceHolderAPIEnabled()) {\n            String resolved = PlaceholderAPI.setPlaceholders(player, value);\n            if (resolved != null) {\n                return resolved;\n            }\n        }\n        return value;\n    }\n\n    private static Object coerce(String value) {\n''',
    "papi helper")

binder = replace_once(
    binder,
    '''        private final List<Range> strings = new ArrayList<>();\n        private final List<Range> regexes = new ArrayList<>();\n        private final List<Range> templates = new ArrayList<>();\n        private final List<Range> templateExpressions = new ArrayList<>();\n        private boolean parsed;\n''',
    '''        private final List<Range> strings = new ArrayList<>();\n        private final List<Range> regexes = new ArrayList<>();\n        private final List<Range> templates = new ArrayList<>();\n        private final List<Range> templateExpressions = new ArrayList<>();\n        private final List<Range> comments = new ArrayList<>();\n        private boolean parsed;\n''',
    "comments field")

binder = replace_once(
    binder,
    '''        private static JavascriptContexts fallback(String source) {\n            JavascriptContexts contexts = new JavascriptContexts();\n\n            addFallbackTemplateRanges(source, 0, source.length(), contexts);\n\n            // Quote-looking text is a string only outside template text. Strings inside\n            // ${...} remain ordinary JavaScript strings and are tracked normally.\n            addPatternRanges(source, FALLBACK_STRING, contexts.strings, contexts);\n            addFallbackRegexRanges(source, contexts);\n            contexts.sort();\n            return contexts;\n        }\n''',
    '''        private static JavascriptContexts fallback(String source) {\n            JavascriptContexts contexts = new JavascriptContexts();\n\n            // First identify template text so comment delimiters inside template text are\n            // ignored. Then find comments, mask them with same-length whitespace, and\n            // rebuild every literal range from the masked source. This prevents quotes or\n            // backticks inside comments from manufacturing fake literal ranges around\n            // executable placeholders.\n            addFallbackTemplateRanges(source, 0, source.length(), contexts);\n            addFallbackCommentRanges(source, contexts);\n            String scanSource = maskRanges(source, contexts.comments);\n\n            contexts.templates.clear();\n            contexts.templateExpressions.clear();\n            addFallbackTemplateRanges(scanSource, 0, scanSource.length(), contexts);\n\n            // Quote-looking text is a string only outside template text. Strings inside\n            // ${...} remain ordinary JavaScript strings and are tracked normally.\n            addPatternRanges(scanSource, FALLBACK_STRING, contexts.strings, contexts);\n            addFallbackRegexRanges(scanSource, contexts);\n            contexts.sort();\n            return contexts;\n        }\n\n        private static void addFallbackCommentRanges(String source, JavascriptContexts contexts) {\n            for (int i = 0; i < source.length(); i++) {\n                if (contexts.isTemplateText(i)) {\n                    continue;\n                }\n\n                char current = source.charAt(i);\n                if (current == '\\'' || current == '\"') {\n                    i = skipQuotedLiteral(source, i, source.length(), current);\n                    continue;\n                }\n                if (current != '/' || i + 1 >= source.length()) {\n                    continue;\n                }\n\n                char next = source.charAt(i + 1);\n                if (next == '/') {\n                    int end = i + 2;\n                    while (end < source.length() && source.charAt(end) != '\\n' && source.charAt(end) != '\\r') {\n                        end++;\n                    }\n                    addFallbackComment(contexts, new Range(i, end));\n                    i = end - 1;\n                    continue;\n                }\n                if (next == '*') {\n                    int end = i + 2;\n                    while (end + 1 < source.length()\n                            && !(source.charAt(end) == '*' && source.charAt(end + 1) == '/')) {\n                        end++;\n                    }\n                    end = end + 1 < source.length() ? end + 2 : source.length();\n                    addFallbackComment(contexts, new Range(i, end));\n                    i = end - 1;\n                    continue;\n                }\n\n                if (canStartRegex(source, i)) {\n                    int regexEnd = skipRegexLiteral(source, i, source.length());\n                    if (regexEnd > i) {\n                        i = regexEnd;\n                    }\n                }\n            }\n        }\n\n        private static void addFallbackComment(JavascriptContexts contexts, Range comment) {\n            contexts.comments.add(comment);\n            // Initial template discovery is only used to distinguish template text from\n            // comments. A backtick inside a comment can create a false template range, so\n            // discard any such range as soon as the comment is known.\n            contexts.removeOverlapping(contexts.templates, comment);\n            contexts.removeOverlapping(contexts.templateExpressions, comment);\n        }\n\n        private static String maskRanges(String source, List<Range> ranges) {\n            StringBuilder masked = new StringBuilder(source);\n            for (Range range : ranges) {\n                for (int i = Math.max(0, range.start); i < Math.min(masked.length(), range.end); i++) {\n                    char current = masked.charAt(i);\n                    if (current != '\\n' && current != '\\r') {\n                        masked.setCharAt(i, ' ');\n                    }\n                }\n            }\n            return masked.toString();\n        }\n''',
    "fallback comments")

binder = replace_once(
    binder,
    '''        private void removeContained(List<Range> ranges, Range container) {\n            ranges.removeIf(range -> range.start >= container.start && range.end <= container.end);\n        }\n''',
    '''        private void removeContained(List<Range> ranges, Range container) {\n            ranges.removeIf(range -> range.start >= container.start && range.end <= container.end);\n        }\n\n        private void removeOverlapping(List<Range> ranges, Range overlap) {\n            ranges.removeIf(range -> range.start < overlap.end && overlap.start < range.end);\n        }\n''',
    "remove overlap")

binder = replace_once(
    binder,
    '''            strings.sort(comparator);\n            regexes.sort(comparator);\n            templates.sort(comparator);\n            templateExpressions.sort(comparator);\n''',
    '''            strings.sort(comparator);\n            regexes.sort(comparator);\n            templates.sort(comparator);\n            templateExpressions.sort(comparator);\n            comments.sort(comparator);\n''',
    "sort comments")

binder_path.write_text(binder)

modern = modern_test_path.read_text()
insert = '''\n    @Test\n    void commentsCannotCreateFakeStringRangeAroundExecutablePlaceholder() {\n        HashMap<String, Object> bindings = new HashMap<>();\n        String injection = "Bukkit.dispatchCommand(Console, 'op attacker')";\n\n        String prepared = JavascriptPlaceholderBinder.bind("obj?.x; /* ' */ %name%; /* ' */",\n                ignored -> injection, bindings::put);\n\n        assertEquals("obj?.x; /* ' */ __advancedCorePlaceholder0; /* ' */", prepared);\n        assertEquals(injection, bindings.get("__advancedCorePlaceholder0"));\n        assertFalse(prepared.contains(injection));\n    }\n\n    @Test\n    void lineCommentsCannotCreateFakeStringRangeAroundExecutablePlaceholder() {\n        HashMap<String, Object> bindings = new HashMap<>();\n        String injection = "Bukkit.dispatchCommand(Console, 'op attacker')";\n\n        String prepared = JavascriptPlaceholderBinder.bind("obj?.x; // '\\n%name%; // '\\n",\n                ignored -> injection, bindings::put);\n\n        assertEquals("obj?.x; // '\\n__advancedCorePlaceholder0; // '\\n", prepared);\n        assertEquals(injection, bindings.get("__advancedCorePlaceholder0"));\n        assertFalse(prepared.contains(injection));\n    }\n'''
if "commentsCannotCreateFakeStringRangeAroundExecutablePlaceholder" not in modern:
    modern = modern.replace("\n}\n", insert + "\n}\n")
modern_test_path.write_text(modern)

priority = priority_test_path.read_text()
insert = '''\n    @Test\n    void decodedCustomValueStillExpandsNestedPapiToken() {\n        AdvancedCorePlugin plugin = BaseTest.getInstance().plugin;\n        OfflinePlayer player = mock(OfflinePlayer.class);\n        JavascriptEngine engine = new JavascriptEngine();\n\n        when(plugin.isPlaceHolderAPIEnabled()).thenReturn(true);\n        String encoded = JavascriptPlaceholderValue.encode("%player_name%");\n\n        try (MockedStatic<PlaceholderAPI> papiStatic = mockStatic(PlaceholderAPI.class)) {\n            papiStatic.when(() -> PlaceholderAPI.setPlaceholders(player, "%player_name%"))\n                    .thenReturn("Ben");\n\n            String prepared = JavascriptPlaceholderBinder.bind("'" + encoded + "' == 'Ben'", player,\n                    Map.of(), engine);\n\n            assertEquals("'Ben' == 'Ben'", prepared);\n            papiStatic.verify(() -> PlaceholderAPI.setPlaceholders(player, "%player_name%"));\n        }\n    }\n'''
if "decodedCustomValueStillExpandsNestedPapiToken" not in priority:
    priority = priority.replace("\n}\n", insert + "\n}\n")
priority_test_path.write_text(priority)
