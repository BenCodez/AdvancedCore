package com.bencodez.advancedcore.api.javascript;

import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.Deque;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class JavascriptPlaceholderParser {
	private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("%([A-Za-z_][^%\\r\\n]*)%|(?<!\\$)\\{([^{}%]+)\\}");
	private static final Pattern INTEGER_PATTERN = Pattern.compile("[-+]?\\d+");
	private static final Pattern DECIMAL_PATTERN = Pattern.compile("[-+]?(?:\\d+\\.\\d*|\\d*\\.\\d+|\\d+)(?:[eE][-+]?\\d+)?");
	private static final String VARIABLE_PREFIX = "__advancedCorePlaceholder";
	private static final Set<String> REGEX_PREFIX_KEYWORDS = Set.of("return", "throw", "case", "delete", "void",
			"typeof", "instanceof", "in", "of", "new", "yield", "await", "else", "do");
	private static final Set<String> CONTROL_HEAD_KEYWORDS = Set.of("if", "while", "for", "with", "switch", "catch");
	private static final Set<String> BLOCK_PREFIX_KEYWORDS = Set.of("else", "do", "try", "finally", "static");

	private JavascriptPlaceholderParser() {
	}

	static String replace(String script, Function<String, String> resolver, BiConsumer<String, Object> bindings) {
		Matcher matcher = PLACEHOLDER_PATTERN.matcher(script);
		StringBuffer result = new StringBuffer();
		int index = 0;
		while (matcher.find()) {
			String placeholder = matcher.group();
			String value = JavascriptSafeValue.decodePlaceholder(placeholder);
			if (value == null) {
				value = resolver.apply(placeholder);
			}
			if (value == null || value.equals(placeholder)) {
				matcher.appendReplacement(result, Matcher.quoteReplacement(placeholder));
				continue;
			}

			Context context = contextAt(script, matcher.start());
			String replacement;
			if (context == Context.REGEX || context == Context.REGEX_CHARACTER_CLASS) {
				replacement = escapeRegexLiteral(value, context == Context.REGEX_CHARACTER_CLASS);
			} else if (context == Context.CODE || context == Context.TEMPLATE_EXPRESSION || context == Context.COMMENT) {
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
		Deque<Boolean> controlParens = new ArrayDeque<>();
		Deque<Boolean> functionExpressionParens = new ArrayDeque<>();
		Deque<Boolean> statementBraces = new ArrayDeque<>();
		BitSet comments = new BitSet(end);
		Context context = Context.CODE;
		boolean escaped = false;
		boolean regexCharacterClass = false;
		int lastControlHeadClose = -1;
		int lastFunctionExpressionParenClose = -1;
		int lastStatementBlockClose = -1;

		for (int i = 0; i < end; i++) {
			char current = script.charAt(i);
			char next = i + 1 < end ? script.charAt(i + 1) : '\0';

			if (context == Context.LINE_COMMENT) {
				comments.set(i);
				if (current == '\n' || current == '\r') {
					context = codeContext(templates);
				}
				continue;
			}
			if (context == Context.BLOCK_COMMENT) {
				comments.set(i);
				if (current == '*' && next == '/') {
					comments.set(i + 1);
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
				comments.set(i);
				comments.set(i + 1);
				context = Context.LINE_COMMENT;
				i++;
				continue;
			}
			if (current == '/' && next == '*') {
				comments.set(i);
				comments.set(i + 1);
				context = Context.BLOCK_COMMENT;
				i++;
				continue;
			}
			if (current == '/' && startsRegexLiteral(script, i, lastControlHeadClose, lastStatementBlockClose, comments)) {
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
			if (current == '(') {
				int keywordEnd = previousNonWhitespace(script, i - 1, comments);
				controlParens.push(isStandaloneControlHead(script, keywordEnd, statementBraces, comments));
				functionExpressionParens.push(opensFunctionExpressionParameters(script, i, statementBraces, comments));
			} else if (current == ')' && !controlParens.isEmpty()) {
				if (controlParens.pop()) {
					lastControlHeadClose = i;
				}
				if (!functionExpressionParens.isEmpty() && functionExpressionParens.pop()) {
					lastFunctionExpressionParenClose = i;
				}
			}

			if (current == '{') {
				statementBraces.push(opensStatementBlock(script, i, statementBraces, lastFunctionExpressionParenClose, comments));
				if (!templates.isEmpty() && templates.peek().expressionDepth > 0) {
					templates.peek().expressionDepth++;
				}
			} else if (current == '}') {
				boolean closesTemplateExpression = !templates.isEmpty() && templates.peek().expressionDepth == 1;
				if (!closesTemplateExpression && !statementBraces.isEmpty() && statementBraces.pop()) {
					lastStatementBlockClose = i;
				}
				if (!templates.isEmpty() && templates.peek().expressionDepth > 0) {
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
		if (context == Context.REGEX && regexCharacterClass) {
			return Context.REGEX_CHARACTER_CLASS;
		}
		return context;
	}

	private static boolean startsRegexLiteral(String script, int slashIndex, int lastControlHeadClose,
			int lastStatementBlockClose, BitSet comments) {
		int previousIndex = previousNonWhitespace(script, slashIndex - 1, comments);
		if (previousIndex < 0) {
			return true;
		}
		char previous = script.charAt(previousIndex);
		if ((previous == '+' || previous == '-') && isPostfixIncrementOrDecrement(script, previousIndex, comments)) {
			return false;
		}
		if ("([{:;,=!?&|+-*%^~<>".indexOf(previous) >= 0) {
			return true;
		}
		if (previous == ')' && previousIndex == lastControlHeadClose) {
			return true;
		}
		if (previous == '}' && previousIndex == lastStatementBlockClose) {
			return true;
		}
		return isStandaloneRegexPrefixKeyword(script, previousIndex, comments);
	}

	private static boolean isStandaloneRegexPrefixKeyword(String script, int keywordEnd, BitSet comments) {
		String keyword = previousIdentifier(script, keywordEnd);
		if (!REGEX_PREFIX_KEYWORDS.contains(keyword)) {
			return false;
		}
		int keywordStart = identifierStart(script, keywordEnd);
		int beforeKeyword = previousNonWhitespace(script, keywordStart - 1, comments);
		return beforeKeyword < 0 || script.charAt(beforeKeyword) != '.';
	}

	private static boolean isStandaloneControlHead(String script, int keywordEnd, Deque<Boolean> statementBraces,
			BitSet comments) {
		String keyword = previousIdentifier(script, keywordEnd);
		if (!CONTROL_HEAD_KEYWORDS.contains(keyword)) {
			return false;
		}
		int keywordStart = identifierStart(script, keywordEnd);
		int beforeKeyword = previousNonWhitespace(script, keywordStart - 1, comments);
		if (beforeKeyword >= 0 && script.charAt(beforeKeyword) == '.') {
			return false;
		}
		return statementBraces.isEmpty() || statementBraces.peek();
	}

	private static boolean isPostfixIncrementOrDecrement(String script, int operatorEndIndex, BitSet comments) {
		char operator = script.charAt(operatorEndIndex);
		if (operatorEndIndex == 0 || script.charAt(operatorEndIndex - 1) != operator) {
			return false;
		}
		int operandEnd = previousNonWhitespace(script, operatorEndIndex - 2, comments);
		if (operandEnd < 0) {
			return false;
		}
		char operand = script.charAt(operandEnd);
		return Character.isJavaIdentifierPart(operand) || Character.isDigit(operand) || operand == ')' || operand == ']'
				|| operand == '}';
	}

	private static boolean opensStatementBlock(String script, int openBraceIndex, Deque<Boolean> statementBraces,
			int lastFunctionExpressionParenClose, BitSet comments) {
		int prefixIndex = previousNonWhitespace(script, openBraceIndex - 1, comments);
		if (prefixIndex < 0) {
			return true;
		}
		char prefix = script.charAt(prefixIndex);
		if (prefix == ')') {
			return prefixIndex != lastFunctionExpressionParenClose;
		}
		if (prefix == '}' || prefix == ';') {
			return true;
		}
		if (prefix == ':') {
			return followsStatementLabel(script, prefixIndex, statementBraces, comments);
		}
		if (prefix == '>' && prefixIndex > 0 && script.charAt(prefixIndex - 1) == '=') {
			// Arrow-function bodies are part of an expression. Their closing brace is an
			// operand and must not make a following slash look like a regex statement.
			return false;
		}
		if (opensClassDeclarationBody(script, prefixIndex, statementBraces, comments)) {
			return true;
		}
		return BLOCK_PREFIX_KEYWORDS.contains(previousIdentifier(script, prefixIndex));
	}

	private static boolean opensClassDeclarationBody(String script, int prefixIndex, Deque<Boolean> statementBraces,
			BitSet comments) {
		int cursor = prefixIndex;
		int parenDepth = 0;
		int bracketDepth = 0;
		while (cursor >= 0) {
			if (comments.get(cursor)) {
				cursor--;
				continue;
			}
			char current = script.charAt(cursor);
			if (Character.isWhitespace(current)) {
				cursor--;
				continue;
			}
			if (current == ')') {
				parenDepth++;
				cursor--;
				continue;
			}
			if (current == '(' && parenDepth > 0) {
				parenDepth--;
				cursor--;
				continue;
			}
			if (current == ']') {
				bracketDepth++;
				cursor--;
				continue;
			}
			if (current == '[' && bracketDepth > 0) {
				bracketDepth--;
				cursor--;
				continue;
			}
			if (parenDepth > 0 || bracketDepth > 0) {
				cursor--;
				continue;
			}
			if (Character.isJavaIdentifierPart(current)) {
				int start = identifierStart(script, cursor);
				String word = script.substring(start, cursor + 1);
				if (word.equals("class")) {
					int beforeClass = previousNonWhitespace(script, start - 1, comments);
					return isClassDeclarationBoundary(script, beforeClass, statementBraces, comments);
				}
				cursor = previousNonWhitespace(script, start - 1, comments);
				continue;
			}
			if ("=?:,;".indexOf(current) >= 0) {
				return false;
			}
			cursor--;
		}
		return false;
	}

	private static boolean isClassDeclarationBoundary(String script, int beforeClass, Deque<Boolean> statementBraces,
			BitSet comments) {
		if (isStatementBoundary(script, beforeClass, statementBraces)) {
			return true;
		}
		if (beforeClass < 0 || !Character.isJavaIdentifierPart(script.charAt(beforeClass))) {
			return false;
		}
		String previousWord = previousIdentifier(script, beforeClass);
		if (previousWord.equals("export")) {
			int exportStart = identifierStart(script, beforeClass);
			return isStatementBoundary(script, previousNonWhitespace(script, exportStart - 1, comments), statementBraces);
		}
		if (previousWord.equals("default")) {
			int defaultStart = identifierStart(script, beforeClass);
			int beforeDefault = previousNonWhitespace(script, defaultStart - 1, comments);
			if (beforeDefault >= 0 && previousIdentifier(script, beforeDefault).equals("export")) {
				int exportStart = identifierStart(script, beforeDefault);
				return isStatementBoundary(script, previousNonWhitespace(script, exportStart - 1, comments), statementBraces);
			}
		}
		return false;
	}

	private static boolean opensFunctionExpressionParameters(String script, int openParenIndex,
			Deque<Boolean> statementBraces, BitSet comments) {
		int tokenEnd = previousNonWhitespace(script, openParenIndex - 1, comments);
		if (tokenEnd >= 0 && script.charAt(tokenEnd) == '*') {
			tokenEnd = previousNonWhitespace(script, tokenEnd - 1, comments);
		}
		if (tokenEnd < 0 || !Character.isJavaIdentifierPart(script.charAt(tokenEnd))) {
			return false;
		}

		String token = previousIdentifier(script, tokenEnd);
		int functionEnd;
		boolean anonymous;
		if (token.equals("function")) {
			functionEnd = tokenEnd;
			anonymous = true;
		} else {
			int tokenStart = identifierStart(script, tokenEnd);
			int beforeName = previousNonWhitespace(script, tokenStart - 1, comments);
			if (beforeName >= 0 && script.charAt(beforeName) == '*') {
				beforeName = previousNonWhitespace(script, beforeName - 1, comments);
			}
			if (beforeName < 0 || !previousIdentifier(script, beforeName).equals("function")) {
				return false;
			}
			functionEnd = beforeName;
			anonymous = false;
		}

		int functionStart = identifierStart(script, functionEnd);
		int beforeFunction = previousNonWhitespace(script, functionStart - 1, comments);
		if (beforeFunction >= 0 && script.charAt(beforeFunction) == '.') {
			return false;
		}
		if (anonymous) {
			return true;
		}

		// Include a preceding async keyword when deciding whether a named function is
		// a declaration or an expression.
		if (beforeFunction >= 0 && Character.isJavaIdentifierPart(script.charAt(beforeFunction))
				&& previousIdentifier(script, beforeFunction).equals("async")) {
			int asyncStart = identifierStart(script, beforeFunction);
			beforeFunction = previousNonWhitespace(script, asyncStart - 1, comments);
		}

		return !isStatementBoundary(script, beforeFunction, statementBraces);
	}

	private static boolean isStatementBoundary(String script, int index, Deque<Boolean> statementBraces) {
		if (index < 0) {
			return true;
		}
		char previous = script.charAt(index);
		if (previous == ';' || previous == '}') {
			return true;
		}
		if (previous == '{') {
			return statementBraces.isEmpty() || statementBraces.peek();
		}
		return false;
	}

	private static boolean followsStatementLabel(String script, int colonIndex, Deque<Boolean> statementBraces,
			BitSet comments) {
		int labelEnd = previousNonWhitespace(script, colonIndex - 1, comments);
		if (labelEnd < 0 || !Character.isJavaIdentifierPart(script.charAt(labelEnd))) {
			return false;
		}
		int labelStart = identifierStart(script, labelEnd);
		int beforeLabel = previousNonWhitespace(script, labelStart - 1, comments);
		if (beforeLabel < 0) {
			return true;
		}
		char previous = script.charAt(beforeLabel);
		if (previous == ';' || previous == '}') {
			return true;
		}
		if (previous == '{') {
			return statementBraces.isEmpty() || statementBraces.peek();
		}
		String previousWord = previousIdentifier(script, beforeLabel);
		return previousWord.equals("case");
	}

	private static int previousNonWhitespace(String script, int index) {
		return previousNonWhitespace(script, index, null);
	}

	private static int previousNonWhitespace(String script, int index, BitSet ignored) {
		for (int i = index; i >= 0; i--) {
			if ((ignored == null || !ignored.get(i)) && !Character.isWhitespace(script.charAt(i))) {
				return i;
			}
		}
		return -1;
	}

	private static int identifierStart(String script, int endIndex) {
		if (endIndex < 0 || !Character.isJavaIdentifierPart(script.charAt(endIndex))) {
			return endIndex + 1;
		}
		int start = endIndex;
		while (start > 0 && Character.isJavaIdentifierPart(script.charAt(start - 1))) {
			start--;
		}
		return start;
	}

	private static String previousIdentifier(String script, int endIndex) {
		if (endIndex < 0 || !Character.isJavaIdentifierPart(script.charAt(endIndex))) {
			return "";
		}
		int start = identifierStart(script, endIndex);
		return script.substring(start, endIndex + 1);
	}

	private static Context codeContext(Deque<TemplateFrame> templates) {
		return !templates.isEmpty() && templates.peek().expressionDepth > 0 ? Context.TEMPLATE_EXPRESSION : Context.CODE;
	}

	private static String escapeRegexLiteral(String value, boolean characterClass) {
		StringBuilder escaped = new StringBuilder(value.length());
		String special = characterClass ? "\\/]^-" : "\\/.*+?^${}()|[]";
		for (int i = 0; i < value.length(); i++) {
			char current = value.charAt(i);
			switch (current) {
			case '\r':
				escaped.append("\\r");
				break;
			case '\n':
				escaped.append("\\n");
				break;
			case '\u2028':
				escaped.append("\\u2028");
				break;
			case '\u2029':
				escaped.append("\\u2029");
				break;
			default:
				if (special.indexOf(current) >= 0) {
					escaped.append('\\');
				}
				escaped.append(current);
				break;
			}
		}
		return escaped.toString();
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
		CODE, SINGLE_QUOTE, DOUBLE_QUOTE, TEMPLATE_TEXT, TEMPLATE_EXPRESSION, REGEX, REGEX_CHARACTER_CLASS,
		LINE_COMMENT, BLOCK_COMMENT, COMMENT
	}

	private static final class TemplateFrame {
		private int expressionDepth;
	}
}
