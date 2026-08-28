from pathlib import Path

path = Path('.github/scripts/finalize_authored_javascript_boundaries.py')
text = path.read_text(encoding='utf-8')
old = r'''		return AuthoredJavascriptText.evaluate(text, engine);
	}
''' + "''' + placeholder[end:]"
new = r'''		return AuthoredJavascriptText.evaluate(text, engine);
	}

	private static String replaceJavascript(String text, JavascriptEngine engine, OfflinePlayer player) {
		return replaceJavascript(text, engine);
	}
''' + "''' + placeholder[end:]"
if text.count(old) != 1:
    raise RuntimeError(f'Expected one PlaceholderUtils helper insertion point, found {text.count(old)}')
path.write_text(text.replace(old, new, 1), encoding='utf-8')
