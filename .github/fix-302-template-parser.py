from pathlib import Path

p = Path('AdvancedCore/src/main/java/com/bencodez/advancedcore/api/javascript/JavascriptPlaceholderBinder.java')
text = p.read_text()
old = '''            } else if (string != null) {
                char delimiter = literalDelimiter(expression, string);
                replacements[i] = escapeString(match.value, delimiter);
            } else {
'''
new = '''            } else if (string != null) {
                char delimiter = literalDelimiter(expression, string);
                if (delimiter == '`' && !contexts.insideTemplateExpression(match.start)) {
                    replacements[i] = escapeTemplate(match.value);
                } else {
                    replacements[i] = escapeString(match.value, delimiter);
                }
            } else {
'''
if text.count(old) != 1:
    raise SystemExit('template string branch not found')
p.write_text(text.replace(old, new, 1))
