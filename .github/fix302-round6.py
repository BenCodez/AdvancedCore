from pathlib import Path

p = Path('AdvancedCore/src/main/java/com/bencodez/advancedcore/api/javascript/JavascriptPlaceholderBinder.java')
text = p.read_text()

old = '''        if (placeholders != null) {
            String name = token.substring(1, token.length() - 1);
            for (Entry<String, String> entry : placeholders.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(name)) {
                    return entry.getValue();
                }
            }
        }
        return token;
    }
'''
new = '''        if (placeholders != null) {
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
        return token;
    }
'''
if text.count(old) != 1:
    raise SystemExit('custom placeholder resolve block not found')
text = text.replace(old, new, 1)

old = '''        private static JavascriptContexts fallback(String source) {
            JavascriptContexts contexts = new JavascriptContexts();

            Matcher templates = FALLBACK_TEMPLATE.matcher(source);
            while (templates.find()) {
                Range template = new Range(templates.start(), templates.end());
                contexts.templates.add(template);
                addFallbackTemplateExpressions(source, template, contexts.templateExpressions);
            }

            // Do not classify quote-looking text inside a template as a string literal.
            addPatternRanges(source, FALLBACK_STRING, contexts.strings, contexts);
            addFallbackRegexRanges(source, contexts);
            contexts.sort();
            return contexts;
        }
'''
new = '''        private static JavascriptContexts fallback(String source) {
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
                if (current == '\\'' || current == '\"') {
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
'''
if text.count(old) != 1:
    raise SystemExit('fallback template block not found')
text = text.replace(old, new, 1)

old = '''                if (source.charAt(i) != '/' || contexts.containing(contexts.strings, i) != null
                        || contexts.containing(contexts.templates, i) != null || !canStartRegex(source, i)) {
                    continue;
                }
'''
new = '''                if (source.charAt(i) != '/' || contexts.containing(contexts.strings, i) != null
                        || contexts.isTemplateText(i) || !canStartRegex(source, i)) {
                    continue;
                }
'''
if text.count(old) != 1:
    raise SystemExit('fallback regex start block not found')
text = text.replace(old, new, 1)

old = '''                    contexts.removeContained(contexts.strings, candidate);
                    if (!contexts.overlaps(contexts.templates, candidate)) {
                        contexts.regexes.add(candidate);
                    }
                    i = end - 1;
'''
new = '''                    contexts.removeContained(contexts.strings, candidate);
                    contexts.regexes.add(candidate);
                    i = end - 1;
'''
if text.count(old) != 1:
    raise SystemExit('fallback regex add block not found')
text = text.replace(old, new, 1)

old = '''                Range candidate = new Range(matcher.start(), matcher.end());
                if (existing == null || !existing.overlapsLiteral(candidate)) {
                    target.add(candidate);
                }
'''
new = '''                Range candidate = new Range(matcher.start(), matcher.end());
                if (existing == null || !existing.isTemplateText(candidate.start)) {
                    target.add(candidate);
                }
'''
if text.count(old) != 1:
    raise SystemExit('pattern range block not found')
text = text.replace(old, new, 1)

old = '''        private boolean insideTemplateExpression(int position) {
            return containing(templateExpressions, position) != null;
        }
'''
new = '''        private boolean insideTemplateExpression(int position) {
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
'''
if text.count(old) != 1:
    raise SystemExit('insideTemplateExpression block not found')
text = text.replace(old, new, 1)

p.write_text(text)
