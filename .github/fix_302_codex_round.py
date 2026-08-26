from pathlib import Path


def replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected 1 match, got {count}")
    return text.replace(old, new, 1)

binder = Path("AdvancedCore/src/main/java/com/bencodez/advancedcore/api/javascript/JavascriptPlaceholderBinder.java")
text = binder.read_text()

text = replace_once(text,
'''    private static final String TREE_PACKAGE = "org.openjdk.nashorn.api.tree";
''',
'''    private static final String TREE_PACKAGE = "org.openjdk.nashorn.api.tree";
    private static final Pattern FALLBACK_STRING = Pattern.compile("'(?:\\\\.|[^'\\\\])*'|\\\"(?:\\\\.|[^\\\"\\\\])*\\\"");
    private static final Pattern FALLBACK_TEMPLATE = Pattern.compile("`(?:\\\\.|[^`\\\\])*`");
    private static final Pattern FALLBACK_REGEX = Pattern.compile("/(?:\\\\.|\\[(?:\\\\.|[^\\]\\\\])*\\]|[^/\\\\\\r\\n])+/[dgimsuvy]*");
''', "fallback patterns")

text = replace_once(text,
'''        JavascriptContexts contexts = JavascriptContexts.parse(sanitized.toString());
        String[] replacements = new String[matches.size()];
''',
'''        JavascriptContexts contexts = JavascriptContexts.parse(sanitized.toString());
        if (!contexts.parsed) {
            contexts = JavascriptContexts.fallback(expression);
        }
        String[] replacements = new String[matches.size()];
''', "fallback selection")

text = replace_once(text,
'''        private final List<Range> strings = new ArrayList<>();
        private final List<Range> regexes = new ArrayList<>();
        private final List<Range> templates = new ArrayList<>();
        private final List<Range> templateExpressions = new ArrayList<>();
''',
'''        private final List<Range> strings = new ArrayList<>();
        private final List<Range> regexes = new ArrayList<>();
        private final List<Range> templates = new ArrayList<>();
        private final List<Range> templateExpressions = new ArrayList<>();
        private boolean parsed;
''', "parsed flag")

text = replace_once(text,
'''                Object root = parse.invoke(parser, "AdvancedCore", source, diagnostic);
                if (root != null) {
                    walk(root, treeClass, contexts, new IdentityHashMap<>());
                }
''',
'''                Object root = parse.invoke(parser, "AdvancedCore", source, diagnostic);
                if (root != null) {
                    contexts.parsed = true;
                    walk(root, treeClass, contexts, new IdentityHashMap<>());
                }
''', "parse success")

marker = '''        private static Object createParser(Class<?> parserClass) throws ReflectiveOperationException {
'''
fallback = r'''        private static JavascriptContexts fallback(String source) {
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

'''
if marker not in text:
    raise SystemExit("createParser marker missing")
text = text.replace(marker, fallback + marker, 1)

old_loader = '''            ScriptEngine cached = handler.getCachedEngine();
            ClassLoader cachedLoader = cached == null ? null : cached.getClass().getClassLoader();
            return canLoadParser(cachedLoader) ? cachedLoader : null;
'''
new_loader = '''            ScriptEngine cached = handler.getCachedEngine();
            ClassLoader cachedLoader = cached == null ? null : cached.getClass().getClassLoader();
            if (canLoadParser(cachedLoader)) {
                return cachedLoader;
            }

            ClassLoader prepared = handler.getOrCreateNashornParserClassLoader();
            return canLoadParser(prepared) ? prepared : null;
'''
text = replace_once(text, old_loader, new_loader, "parser loader fallback")
binder.write_text(text)

handler = Path("AdvancedCore/src/main/java/com/bencodez/advancedcore/api/javascript/JavascriptEngineHandler.java")
text = handler.read_text()
marker = '''\t/**
\t * Gets a downloaded Nashorn engine.
'''
method = r'''	/**
	 * Gets or prepares a Nashorn classloader for parser-only use. This is separate
	 * from the active ScriptEngine so a server-provided Rhino/GraalJS engine can
	 * still use Nashorn's parser for safe placeholder context detection.
	 *
	 * @return a classloader containing Nashorn's parser API, or null if unavailable
	 */
	public ClassLoader getOrCreateNashornParserClassLoader() {
		if (nashornClassLoader != null) {
			try {
				Class.forName("org.openjdk.nashorn.api.tree.Parser", false, nashornClassLoader);
				return nashornClassLoader;
			} catch (ClassNotFoundException | LinkageError ignored) {
			}
		}
		if (plugin == null) {
			return null;
		}

		URLClassLoader loader = createParserClassLoader(PRIMARY_NASHORN_VERSION, ASM_VERSION_FOR_PRIMARY);
		if (loader == null) {
			loader = createParserClassLoader(FALLBACK_NASHORN_VERSION, ASM_VERSION_FOR_FALLBACK);
		}
		if (loader != null) {
			nashornClassLoader = loader;
		}
		return loader;
	}

	private URLClassLoader createParserClassLoader(String nashornVersion, String asmVersion) {
		try {
			List<Path> jars = getOrDownloadJars(nashornVersion, asmVersion);
			if (jars.isEmpty()) {
				return null;
			}
			URLClassLoader loader = createClassLoader(jars);
			try {
				Class.forName("org.openjdk.nashorn.api.tree.Parser", false, loader);
				return loader;
			} catch (ClassNotFoundException | LinkageError e) {
				closeQuietly(loader);
				return null;
			}
		} catch (IOException e) {
			logDebug(e);
			return null;
		}
	}

'''
if marker not in text:
    raise SystemExit("handler insertion marker missing")
text = text.replace(marker, method + marker, 1)
handler.write_text(text)

pom = Path("AdvancedCore/pom.xml")
text = pom.read_text()
text = replace_once(text,
'''        <dependency>
            <groupId>org.openjdk.nashorn</groupId>
            <artifactId>nashorn-core</artifactId>
            <version>15.7</version>
            <scope>compile</scope>
        </dependency>
''',
'''        <dependency>
            <groupId>org.openjdk.nashorn</groupId>
            <artifactId>nashorn-core</artifactId>
            <version>15.7</version>
            <scope>provided</scope>
        </dependency>
''', "nashorn scope")
pom.write_text(text)

test = Path("AdvancedCore/src/test/java/com/bencodez/advancedcore/api/javascript/JavascriptPlaceholderBinderTest.java")
text = test.read_text()
marker = '''    @Test
    void unresolvedTokensRemainUntouched() {
'''
tests = r'''    @Test
    void parserFailurePreservesQuotedPlaceholderUnderModernSyntax() {
        HashMap<String, Object> bindings = new HashMap<>();

        String prepared = JavascriptPlaceholderBinder.bind("obj?.name && '%name%' === 'Ben'",
                ignored -> "Ben", bindings::put);

        assertEquals("obj?.name && 'Ben' === 'Ben'", prepared);
        assertTrue(bindings.isEmpty());
    }

    @Test
    void parserFailurePreservesTemplateTextUnderModernSyntax() {
        HashMap<String, Object> bindings = new HashMap<>();

        String prepared = JavascriptPlaceholderBinder.bind("obj?.name && `Hello %name%`",
                ignored -> "Ben` ${attack}", bindings::put);

        assertEquals("obj?.name && `Hello Ben\\` \\${attack}`", prepared);
        assertTrue(bindings.isEmpty());
    }

    @Test
    void parserFailurePreservesRegexPlaceholderUnderModernSyntax() {
        HashMap<String, Object> bindings = new HashMap<>();

        String prepared = JavascriptPlaceholderBinder.bind("obj?.name && /^%name%$/.test(value)",
                ignored -> "Ben.*", bindings::put);

        assertEquals("obj?.name && /^Ben\\.\\*$/.test(value)", prepared);
        assertTrue(bindings.isEmpty());
    }

'''
if marker not in text:
    raise SystemExit("test marker missing")
text = text.replace(marker, tests + marker, 1)
test.write_text(text)
