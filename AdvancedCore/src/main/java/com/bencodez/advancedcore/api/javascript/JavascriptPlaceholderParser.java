package com.bencodez.advancedcore.api.javascript;

import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class JavascriptPlaceholderParser {
	private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("%([^%]+)%|\\{([^{}]+)\\}");
	private static final Pattern INTEGER_PATTERN = Pattern.compile("[-+]?\\d+");
	private static final Pattern DECIMAL_PATTERN = Pattern.compile("[-+]?(?:\\d+\\.\\d*|\\d*\\.\\d+|\\d+)(?:[eE][-+]?\\d+)?");
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
			boolean templateExpression = quote == '`' && isInsideTemplateExpression(script, matcher.start());
			String replacement;
			if (quote == 0 || templateExpression) {
				String variable = VARIABLE_PREFIX + index++;
				bindings.accept(variable, coercePrimitive(value));
				replacement = variable;
			} else {
				replacement = escape(value, quote);
			}
			matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
		}
		matcher.appendTail(result);
		return result.toString();
	}

	private static Object coercePrimitive(String value) {
		if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
			return Boolean.valueOf(value);
		}
		if (INTEGER_PATTERN.matcher(value).matches()) {
			try {
				return Long.valueOf(value);
			} catch (NumberFormatException ignored) {
				// Fall through to string binding for values outside the long range.
			}
		}
		if (DECIMAL_PATTERN.matcher(value).matches()) {
			try {
				return Double.valueOf(value);
			} catch (NumberFormatException ignored) {
				// Fall through to string binding for values outside the double range.
			}
		}
		return value;
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

	private static boolean isInsideTemplateExpression(String script, int end) {
		boolean inTemplate = false;
		boolean escaped = false;
		int expressionDepth = 0;
		char expressionQuote = 0;

		for (int i = 0; i < end; i++) {
			char current = script.charAt(i);
			if (!inTemplate) {
				if (current == '`') {
					inTemplate = true;
					expressionDepth = 0;
					escaped = false;
				}
				continue;
			}

			if (expressionDepth == 0) {
				if (escaped) {
					escaped = false;
					continue;
				}
				if (current == '\\') {
					escaped = true;
					continue;
				}
				if (current == '`') {
					inTemplate = false;
					continue;
				}
				if (current == '$' && i + 1 < end && script.charAt(i + 1) == '{') {
					expressionDepth = 1;
					i++;
				}
				continue;
			}

			if (expressionQuote != 0) {
				if (escaped) {
					escaped = false;
				} else if (current == '\\') {
					escaped = true;
				} else if (current == expressionQuote) {
					expressionQuote = 0;
				}
				continue;
			}

			if (current == '\'' || current == '"') {
				expressionQuote = current;
			} else if (current == '{') {
				expressionDepth++;
			} else if (current == '}') {
				expressionDepth--;
			}
		}
		return inTemplate && expressionDepth > 0;
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
