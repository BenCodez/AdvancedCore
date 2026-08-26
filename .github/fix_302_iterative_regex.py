from pathlib import Path

p = Path('AdvancedCore/src/main/java/com/bencodez/advancedcore/api/javascript/JavascriptPlaceholderBinder.java')
text = p.read_text()

old = '    private static final Pattern FALLBACK_REGEX = Pattern.compile("/(?:\\\\\\\\.|[^/\\\\\\\\\\r\\n])+/[dgimsuvy]*");\n'
if old not in text:
    # Match the currently rendered Java source regardless of Python escape confusion.
    lines = text.splitlines(True)
    filtered = []
    removed = False
    for line in lines:
        if line.strip().startswith('private static final Pattern FALLBACK_REGEX ='):
            removed = True
            continue
        filtered.append(line)
    if not removed:
        raise SystemExit('FALLBACK_REGEX constant not found')
    text = ''.join(filtered)
else:
    text = text.replace(old, '', 1)

old = '''            addPatternRanges(source, FALLBACK_REGEX, contexts.regexes, contexts);
            contexts.sort();
            return contexts;
        }

        private static void addPatternRanges(String source, Pattern pattern, List<Range> target,
                JavascriptContexts existing) {
'''
new = '''            addFallbackRegexRanges(source, contexts);
            contexts.sort();
            return contexts;
        }

        private static void addFallbackRegexRanges(String source, JavascriptContexts contexts) {
            for (int i = 0; i < source.length(); i++) {
                if (source.charAt(i) != '/' || contexts.containing(contexts.strings, i) != null
                        || contexts.containing(contexts.templates, i) != null) {
                    continue;
                }

                boolean escaped = false;
                boolean characterClass = false;
                for (int j = i + 1; j < source.length(); j++) {
                    char current = source.charAt(j);
                    if (current == '\\r' || current == '\\n') {
                        break;
                    }
                    if (escaped) {
                        escaped = false;
                        continue;
                    }
                    if (current == '\\\\') {
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
                    if (!contexts.overlapsLiteral(candidate)) {
                        contexts.regexes.add(candidate);
                    }
                    i = end - 1;
                    break;
                }
            }
        }

        private static void addPatternRanges(String source, Pattern pattern, List<Range> target,
                JavascriptContexts existing) {
'''
if text.count(old) != 1:
    raise SystemExit(f'fallback insertion point expected once, got {text.count(old)}')
text = text.replace(old, new, 1)

old_comment = '''            // nashorn-core is packaged with AdvancedCore so parser support remains
            // available even when the active ScriptEngine is Rhino/GraalJS.
'''
new_comment = '''            // Use a parser already visible to AdvancedCore when available. If not,
            // the handler can prepare a dedicated runtime Nashorn parser loader below.
'''
if old_comment in text:
    text = text.replace(old_comment, new_comment, 1)

p.write_text(text)
