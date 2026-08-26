from pathlib import Path

p = Path('AdvancedCore/src/main/java/com/bencodez/advancedcore/api/javascript/JavascriptPlaceholderBinder.java')
text = p.read_text()

old = '''                    Range candidate = new Range(i, end);
                    if (!contexts.overlapsLiteral(candidate)) {
                        contexts.regexes.add(candidate);
                    }
                    i = end - 1;
                    break;
'''
new = '''                    Range candidate = new Range(i, end);
                    // A regex literal may legitimately contain quote characters. Since the
                    // opening slash was already proven to be outside a string/template and
                    // can start a regex, discard fallback string ranges fully contained by
                    // this regex instead of letting quote-looking regex text win.
                    contexts.removeContained(contexts.strings, candidate);
                    if (!contexts.overlaps(contexts.templates, candidate)) {
                        contexts.regexes.add(candidate);
                    }
                    i = end - 1;
                    break;
'''
if text.count(old) != 1:
    raise SystemExit('regex candidate block not found')
text = text.replace(old, new, 1)

old = '''        private static void addFallbackTemplateExpressions(String source, Range template, List<Range> target) {
            boolean escaped = false;
            int expressionStart = -1;
            int depth = 0;
            for (int i = template.start + 1; i < template.end - 1; i++) {
                char current = source.charAt(i);
                if (escaped) {
                    escaped = false;
                    continue;
                }
                if (current == '\\\\') {
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
'''
new = '''        private static void addFallbackTemplateExpressions(String source, Range template, List<Range> target) {
            boolean escaped = false;
            for (int i = template.start + 1; i < template.end - 1; i++) {
                char current = source.charAt(i);
                if (escaped) {
                    escaped = false;
                    continue;
                }
                if (current == '\\\\') {
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
                if (current == '\\'' || current == '"') {
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
                } else if (current == '\\\\') {
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
                if (current == '\\\\') {
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
                if (current == '\\r' || current == '\\n') {
                    return start;
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
'''
if text.count(old) != 1:
    raise SystemExit('template expression block not found')
text = text.replace(old, new, 1)

marker = '''        private boolean overlaps(List<Range> ranges, Range candidate) {
            for (Range range : ranges) {
                if (candidate.start < range.end && range.start < candidate.end) {
                    return true;
                }
            }
            return false;
        }
'''
replacement = marker + '''\n        private void removeContained(List<Range> ranges, Range container) {
            ranges.removeIf(range -> range.start >= container.start && range.end <= container.end);
        }
'''
if text.count(marker) != 1:
    raise SystemExit('overlaps marker not found')
text = text.replace(marker, replacement, 1)

p.write_text(text)
