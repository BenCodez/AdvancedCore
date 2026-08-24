package com.bencodez.advancedcore.api.javascript;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class JavascriptPlaceholderParser {
	private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("%([^%]+)%|(?<!\\$)\\{([^{}]+)\\}");
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

			Context context = contextAt(script, matcher.start());
			String replacement;
			if (context == Context.CODE || context == Context.TEMPLATE_EXPRESSION || context == Context.COMMENT
					|| context == Context.REGEX) {
				String variable = VARIABLE_PREFIX + index++;
				bindings.accept(variable, coercePrimitive(value));
				replacement = variable;
			} else {
				replacement = escape(value, context == Context.SINGLE_QUOTE ? '\''
						: context == Context.DOUBLE_QUOTE ? '"' : '`');
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
			}
		}
		if (DECIMAL_PATTERN.matcher(value).matches()) {
			try {
				return Double.valueOf(value);
			} catch (NumberFormatException ignored) {
			}
		}
		return value;
	}

	private static Context contextAt(String script, int end) {
		Deque<TemplateFrame> templates = new ArrayDeque<>();
		Context context = Context.CODE;
		boolean escaped = false;
		boolean regexCharacterClass = false;

		for (int i = 0; i < end; i++) {
			char current = script.charAt(i);
			char next = i + 1 < end ? script.charAt(i + 1) : '\0';

			if (context == Context.LINE_COMMENT) {
				if (current == '\n' || current == '\r') {
					context = codeContext(templates);
				}
				continue;
			}
			if (context == Context.BLOCK_COMMENT) {
				if (current == '*' && next == '/') {
					context = codeContext(templates);
					i++;
				}
				continue;
			}
			if (context == Context.REGEX) {
				if (escaped) {
					escaped = false;
					continue;
				}
				if (current == '\\') {
					escaped = true;
					continue;
				}
				if (current == '[') {
					regexCharacterClass = true;
					continue;
				}
				if (current == ']' && regexCharacterClass) {
					regexCharacterClass = false;
					continue;
				}
				if (current == '/' && !regexCharacterClass) {
					context = codeContext(templates);
				}
				continue;
			}
			if (context == Context.SINGLE_QUOTE || context == Context.DOUBLE_QUOTE) {
				char quote = context == Context.SINGLE_QUOTE ? '\'' : '"';
				if (escaped) {
					escaped = false;
				} else if (current == '\\') {
					escaped = true;
				} else if (current == quote) {
					context = codeContext(templates);
				}
				continue;
			}
			if (context == Context.TEMPLATE_TEXT) {
				if (escaped) {
					escaped = false;
					continue;
				}
				if (current == '\\') {
					escaped = true;
					continue;
				}
				if (current == '`') {
					templates.pop();
					context = codeContext(templates);
					continue;
				}
				if (current == '$' && next == '{') {
					templates.peek().expressionDepth = 1;
					context = Context.TEMPLATE_EXPRESSION;
					i++;
				}
				continue;
			}

			if (current == '/' && next == '/') {
				context = Context.LINE_COMMENT;
				i++;
				continue;
			}
			if (current == '/' && next == '*') {
				context = Context.BLOCK_COMMENT;
				i++;
				continue;
			}
			if (current == '/' && startsRegexLiteral(script, i)) {
				context = Context.REGEX;
				escaped = false;
				regexCharacterClass = false;
				continue;
			}
			if (current == '\'') {
				context = Context.SINGLE_QUOTE;
				escaped = false;
				continue;
			}
			if (current == '"') {
				context = Context.DOUBLE_QUOTE;
				escaped = false;
				continue;
			}
			if (current == '`') {
				templates.push(new TemplateFrame());
				context = Context.TEMPLATE_TEXT;
				continue;
			}
			if (!templates.isEmpty() && templates.peek().expressionDepth > 0) {
				if (current == '{') {
					templates.peek().expressionDepth++;
				} else if (current == '}') {
					templates.peek().expressionDepth--;
					if (templates.peek().expressionDepth == 0) {
						context = Context.TEMPLATE_TEXT;
					}
				}
			}
		}
		if (context == Context.LINE_COMMENT || context == Context.BLOCK_COMMENT) {
			return Context.COMMENT;
		}
		return context;
	}

	private static boolean startsRegexLiteral(String script, int slashIndex) {
		for (int i = slashIndex - 1; i >= 0; i--) {
			char previous = script.charAt(i);
			if (Character.isWhitespace(previous)) {
				continue;
			}
			return "([{:;,=!?&|+-*%^~<>".indexOf(previous) >= 0;
		}
		return true;
	}

	private static Context codeContext(Deque<TemplateFrame> templates) {
		return !templates.isEmpty() && templates.peek().expressionDepth > 0 ? Context.TEMPLATE_EXPRESSION : Context.CODE;
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

	private enum Context {
		CODE, SINGLE_QUOTE, DOUBLE_QUOTE, TEMPLATE_TEXT, TEMPLATE_EXPRESSION, REGEX, LINE_COMMENT, BLOCK_COMMENT, COMMENT
	}

	private static final class TemplateFrame {
		private int expressionDepth;
	}
}
