from pathlib import Path

p = Path('AdvancedCore/src/main/java/com/bencodez/advancedcore/api/javascript/JavascriptPlaceholderBinder.java')
text = p.read_text()

old = '            contexts = JavascriptContexts.fallback(expression);'
new = '            contexts = JavascriptContexts.fallback(sanitized.toString());'
assert text.count(old) == 1
text = text.replace(old, new, 1)

old = '''            if (regex != null) {
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
new = '''            if (regex != null) {
                replacements[i] = escapeRegex(match.value, expression, regex, match.start);
            } else if (template != null && !contexts.insideTemplateExpression(match.start)) {
                // Template text wins over quote-looking text inside the template. A value
                // containing ${...} must never become a live interpolation.
                replacements[i] = escapeTemplate(match.value);
            } else if (string != null) {
                char delimiter = literalDelimiter(expression, string);
                replacements[i] = escapeString(match.value, delimiter);
            } else {
'''
assert text.count(old) == 1
text = text.replace(old, new, 1)

old = '''        private static JavascriptContexts fallback(String source) {
            JavascriptContexts contexts = new JavascriptContexts();
            addPatternRanges(source, FALLBACK_STRING, contexts.strings, null);

            Matcher templates = FALLBACK_TEMPLATE.matcher(source);
            while (templates.find()) {
                Range template = new Range(templates.start(), templates.end());
                contexts.templates.add(template);
                addFallbackTemplateExpressions(source, template, contexts.templateExpressions);
            }

            addFallbackRegexRanges(source, contexts);
            contexts.sort();
            return contexts;
        }
'''
new = '''        private static JavascriptContexts fallback(String source) {
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
assert text.count(old) == 1
text = text.replace(old, new, 1)

old = '''                if (source.charAt(i) != '/' || contexts.containing(contexts.strings, i) != null
                        || contexts.containing(contexts.templates, i) != null) {
                    continue;
                }
'''
new = '''                if (source.charAt(i) != '/' || contexts.containing(contexts.strings, i) != null
                        || contexts.containing(contexts.templates, i) != null || !canStartRegex(source, i)) {
                    continue;
                }
'''
assert text.count(old) == 1
text = text.replace(old, new, 1)

marker = '''        private static void addPatternRanges(String source, Pattern pattern, List<Range> target,
                JavascriptContexts existing) {
'''
helper = '''        private static boolean canStartRegex(String source, int slashIndex) {
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

'''
assert text.count(marker) == 1
text = text.replace(marker, helper + marker, 1)
p.write_text(text)
