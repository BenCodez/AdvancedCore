package com.bencodez.advancedcore.api.javascript;

import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class JavascriptPlaceholderParser {
	private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("%([^%]+)%");
	private static final String VARIABLE_PREFIX = "__advancedCorePlaceholder";

	private JavascriptPlaceholderParser() {
	}

	static String replace(String script, Function<String, String> resolver, BiConsumer<String, Object> bindings) {
		Matcher matcher = PLACEHOLDER_PATTERN.matcher(script);
		StringBuffer result = new StringBuffer();
		int index = 0;
		while (matcher.find()) {
			String placeholder = matcher.group();
			String value = resolver.apply(placeholder);
			if (value == null || value.equals(placeholder)) {
				matcher.appendReplacement(result, Matcher.quoteReplacement(placeholder));
				continue;
			}

			char quote = quoteAt(script, matcher.start());
			String replacement;
			if (quote == 0) {
				String variable = VARIABLE_PREFIX + index++;
				bindings.accept(variable, value);
				replacement = variable;
			} else {
				replacement = escape(value, quote);
			}
			matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
		}
		matcher.appendTail(result);
		return result.toString();
	}

	private static char quoteAt(String script, int end) {
		char quote = 0;
		boolean escaped = false;
		for (int i = 0; i < end; i++) {
			char current = script.charAt(i);
			if (escaped) {
				escaped = false;
			} else if (current == '\\' && quote != 0) {
				escaped = true;
			} else if (current == quote) {
				quote = 0;
			} else if (quote == 0 && (current == '\'' || current == '"' || current == '`')) {
				quote = current;
			}
		}
		return quote;
	}

	private static String escape(String value, char quote) {
		String escaped = value.replace("\\", "\\\\").replace("\r", "\\r").replace("\n", "\\n")
				.replace("\u2028", "\\u2028").replace("\u2029", "\\u2029");
		escaped = escaped.replace(String.valueOf(quote), "\\" + quote);
		if (quote == '`') {
			escaped = escaped.replace("${", "\\${");
		}
		return escaped;
	}
}
