package com.bencodez.advancedcore.api.javascript;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;

import org.junit.jupiter.api.Test;

class JavascriptPlaceholderParserTest {
	@Test
	void bindsUnquotedPlaceholderValuesInsteadOfAddingThemToSource() {
		HashMap<String, Object> bindings = new HashMap<>();
		String injection = "\"); Bukkit.dispatchCommand(Console, \"op attacker\"); //";

		String script = JavascriptPlaceholderParser.replace("%player_name% == 'allowed'", ignored -> injection,
				bindings::put);

		assertEquals("__advancedCorePlaceholder0 == 'allowed'", script);
		assertEquals(injection, bindings.get("__advancedCorePlaceholder0"));
	}

	@Test
	void escapesPlaceholderValuesInsideQuotedStrings() {
		HashMap<String, Object> bindings = new HashMap<>();

		String script = JavascriptPlaceholderParser.replace("check('%player_name%', \"%player_name%\")",
				ignored -> "'\"\\\n${attack}", bindings::put);

		assertEquals("check('\\'\"\\\\\\n${attack}', \"'\\\"\\\\\\n${attack}\")", script);
		assertEquals(0, bindings.size());
	}

	@Test
	void escapesTemplateLiteralText() {
		String script = JavascriptPlaceholderParser.replace("`Hello %player_name%`", ignored -> "${attack}`",
				(name, value) -> {
				});

		assertEquals("`Hello \\${attack}\\``", script);
	}

	@Test
	void bindsPlaceholdersInsideTemplateExpressions() {
		HashMap<String, Object> bindings = new HashMap<>();
		String injection = "Bukkit.dispatchCommand(Console, \"op attacker\")";

		String script = JavascriptPlaceholderParser.replace("`${%untrusted%}`", ignored -> injection, bindings::put);

		assertEquals("`${__advancedCorePlaceholder0}`", script);
		assertEquals(injection, bindings.get("__advancedCorePlaceholder0"));
	}

	@Test
	void ignoresCommentBracesInsideTemplateExpressions() {
		HashMap<String, Object> bindings = new HashMap<>();
		String injection = "Bukkit.dispatchCommand(Console, \"op attacker\")";

		String script = JavascriptPlaceholderParser.replace("`${/* } */ %untrusted%}`", ignored -> injection,
				bindings::put);

		assertEquals("`${/* } */ __advancedCorePlaceholder0}`", script);
		assertEquals(injection, bindings.get("__advancedCorePlaceholder0"));
	}

	@Test
	void preservesNestedTemplateLiteralTextInsideExpressions() {
		HashMap<String, Object> bindings = new HashMap<>();

		String script = JavascriptPlaceholderParser.replace("`${`Hello %name%`}`", ignored -> "Ben", bindings::put);

		assertEquals("`${`Hello Ben`}`", script);
		assertTrue(bindings.isEmpty());
	}

	@Test
	void regexLiteralQuotesDoNotChangePlaceholderContext() {
		HashMap<String, Object> bindings = new HashMap<>();
		String injection = "Bukkit.dispatchCommand(Console, \"op attacker\")";

		String script = JavascriptPlaceholderParser.replace("/[']/.test('x'); %untrusted%", ignored -> injection,
				bindings::put);

		assertEquals("/[']/.test('x'); __advancedCorePlaceholder0", script);
		assertEquals(injection, bindings.get("__advancedCorePlaceholder0"));
	}

	@Test
	void recognizesRegexLiteralAfterControlHead() {
		HashMap<String, Object> bindings = new HashMap<>();
		String injection = "Bukkit.dispatchCommand(Console, \"op attacker\")";

		String script = JavascriptPlaceholderParser.replace("if (ok) /[']/.test('x'); %untrusted%", ignored -> injection,
				bindings::put);

		assertEquals("if (ok) /[']/.test('x'); __advancedCorePlaceholder0", script);
		assertEquals(injection, bindings.get("__advancedCorePlaceholder0"));
	}

	@Test
	void recognizesRegexLiteralAfterExpressionKeyword() {
		HashMap<String, Object> bindings = new HashMap<>();
		String injection = "Bukkit.dispatchCommand(Console, \"op attacker\")";

		String script = JavascriptPlaceholderParser.replace("return /[']/.test('x'); %untrusted%", ignored -> injection,
				bindings::put);

		assertEquals("return /[']/.test('x'); __advancedCorePlaceholder0", script);
		assertEquals(injection, bindings.get("__advancedCorePlaceholder0"));
	}

	@Test
	void recognizesRegexLiteralAfterStatementBlock() {
		HashMap<String, Object> bindings = new HashMap<>();
		String injection = "Bukkit.dispatchCommand(Console, \"op attacker\")";

		String script = JavascriptPlaceholderParser.replace("if (ok) {} /[']/.test('x'); %untrusted%", ignored -> injection,
				bindings::put);

		assertEquals("if (ok) {} /[']/.test('x'); __advancedCorePlaceholder0", script);
		assertEquals(injection, bindings.get("__advancedCorePlaceholder0"));
	}

	@Test
	void recognizesRegexLiteralAfterLabeledStatementBlock() {
		HashMap<String, Object> bindings = new HashMap<>();
		String injection = "Bukkit.dispatchCommand(Console, \"op attacker\")";

		String script = JavascriptPlaceholderParser.replace("label: {} /[']/.test('x'); %untrusted%", ignored -> injection,
				bindings::put);

		assertEquals("label: {} /[']/.test('x'); __advancedCorePlaceholder0", script);
		assertEquals(injection, bindings.get("__advancedCorePlaceholder0"));
	}

	@Test
	void nestedObjectPropertyBlockDoesNotBecomeStatementBlock() {
		HashMap<String, Object> bindings = new HashMap<>();
		String injection = "'; allowed=true; '";

		String script = JavascriptPlaceholderParser.replace("var x={a: {}} / 2; '%untrusted%'",
				ignored -> injection, bindings::put);

		assertEquals("var x={a: {}} / 2; '\\'; allowed=true; \\''", script);
		assertTrue(bindings.isEmpty());
	}

	@Test
	void postfixIncrementBeforeDivisionDoesNotOpenRegexContext() {
		HashMap<String, Object> bindings = new HashMap<>();
		String injection = "'; allowed=true; '";

		String script = JavascriptPlaceholderParser.replace("var allowed=false,i=1; i++ / 2; '%untrusted%'; allowed",
				ignored -> injection, bindings::put);

		assertEquals("var allowed=false,i=1; i++ / 2; '\\'; allowed=true; \\''; allowed", script);
		assertTrue(bindings.isEmpty());
	}

	@Test
	void controlHeadIgnoresParenthesesInsideStrings() {
		HashMap<String, Object> bindings = new HashMap<>();
		String injection = "Bukkit.dispatchCommand(Console, \"op attacker\")";

		String script = JavascriptPlaceholderParser.replace("if (fn(\")\")) /[']/.test('x'); %untrusted%",
				ignored -> injection, bindings::put);

		assertEquals("if (fn(\")\")) /[']/.test('x'); __advancedCorePlaceholder0", script);
		assertEquals(injection, bindings.get("__advancedCorePlaceholder0"));
	}

	@Test
	void controlHeadIgnoresParenthesesInsideRegexLiterals() {
		HashMap<String, Object> bindings = new HashMap<>();
		String injection = "Bukkit.dispatchCommand(Console, \"op attacker\")";

		String script = JavascriptPlaceholderParser.replace("if (/\\)/.test(value)) /[']/.test('x'); %untrusted%",
				ignored -> injection, bindings::put);

		assertEquals("if (/\\)/.test(value)) /[']/.test('x'); __advancedCorePlaceholder0", script);
		assertEquals(injection, bindings.get("__advancedCorePlaceholder0"));
	}

	@Test
	void memberNamedIfDoesNotCreateControlHeadRegexContext() {
		HashMap<String, Object> bindings = new HashMap<>();
		String injection = "'; allowed=true; '";

		String script = JavascriptPlaceholderParser.replace("obj.if(true) / 2; '%untrusted%'", ignored -> injection,
				bindings::put);

		assertEquals("obj.if(true) / 2; '\\'; allowed=true; \\''", script);
		assertTrue(bindings.isEmpty());
	}

	@Test
	void functionExpressionBodyDoesNotBecomeStatementBlock() {
		HashMap<String, Object> bindings = new HashMap<>();
		String injection = "'; allowed=true; '";

		String script = JavascriptPlaceholderParser.replace(
				"var allowed=false,x=function() {} / 2; '%untrusted%'; allowed", ignored -> injection, bindings::put);

		assertEquals("var allowed=false,x=function() {} / 2; '\\'; allowed=true; \\''; allowed", script);
		assertTrue(bindings.isEmpty());
	}

	@Test
	void namedFunctionExpressionBodyDoesNotBecomeStatementBlock() {
		HashMap<String, Object> bindings = new HashMap<>();
		String injection = "'; allowed=true; '";

		String script = JavascriptPlaceholderParser.replace(
				"var allowed=false,x=function named() {} / 2; '%untrusted%'; allowed", ignored -> injection, bindings::put);

		assertEquals("var allowed=false,x=function named() {} / 2; '\\'; allowed=true; \\''; allowed", script);
		assertTrue(bindings.isEmpty());
	}

	@Test
	void arrowFunctionBodyDoesNotBecomeStatementBlock() {
		HashMap<String, Object> bindings = new HashMap<>();
		String injection = "'; allowed=true; '";

		String script = JavascriptPlaceholderParser.replace(
				"var allowed=false,x=()=>{} / 2; '%untrusted%'; allowed", ignored -> injection, bindings::put);

		assertEquals("var allowed=false,x=()=>{} / 2; '\\'; allowed=true; \\''; allowed", script);
		assertTrue(bindings.isEmpty());
	}

	@Test
	void functionDeclarationBodyStillAllowsRegexStatement() {
		HashMap<String, Object> bindings = new HashMap<>();
		String injection = "Bukkit.dispatchCommand(Console, \"op attacker\")";

		String script = JavascriptPlaceholderParser.replace(
				"function named() {} /[']/.test('x'); %untrusted%", ignored -> injection, bindings::put);

		assertEquals("function named() {} /[']/.test('x'); __advancedCorePlaceholder0", script);
		assertEquals(injection, bindings.get("__advancedCorePlaceholder0"));
	}

	@Test
	void objectLiteralStringBraceDoesNotBecomeStatementBlock() {
		HashMap<String, Object> bindings = new HashMap<>();
		String injection = "'; allowed=true; '";

		String script = JavascriptPlaceholderParser.replace("var x={a:\"){\"} / 2; '%untrusted%'",
				placeholder -> placeholder.equals("%untrusted%") ? injection : placeholder, bindings::put);

		assertEquals("var x={a:\"){\"} / 2; '\\'; allowed=true; \\''", script);
		assertTrue(bindings.isEmpty());
	}

	@Test
	void objectLiteralDoesNotConsumeNestedPercentPlaceholder() {
		HashMap<String, Object> bindings = new HashMap<>();

		String script = JavascriptPlaceholderParser.replace("({allowed: %permission_result%}).allowed",
				placeholder -> placeholder.equals("%permission_result%") ? "true" : placeholder, bindings::put);

		assertEquals("({allowed: __advancedCorePlaceholder0}).allowed", script);
		assertEquals(Boolean.TRUE, bindings.get("__advancedCorePlaceholder0"));
	}

	@Test
	void objectLiteralFollowedByDivisionIsNotTreatedAsRegex() {
		HashMap<String, Object> bindings = new HashMap<>();

		String script = JavascriptPlaceholderParser.replace("value = {} / 2; %name%", ignored -> "Ben", bindings::put);

		assertEquals("value = {} / 2; __advancedCorePlaceholder0", script);
		assertEquals("Ben", bindings.get("__advancedCorePlaceholder0"));
	}

	@Test
	void regexCharacterClassesAndEscapesRemainRegexText() {
		HashMap<String, Object> bindings = new HashMap<>();

		String script = JavascriptPlaceholderParser.replace("/[/\\']+/.test(value); %name%", ignored -> "Ben",
				bindings::put);

		assertEquals("/[/\\']+/.test(value); __advancedCorePlaceholder0", script);
		assertEquals("Ben", bindings.get("__advancedCorePlaceholder0"));
	}

	@Test
	void preservesPlaceholderValuesInsideRegexLiterals() {
		HashMap<String, Object> bindings = new HashMap<>();

		String script = JavascriptPlaceholderParser.replace("/^%player_name%$/.test(name)",
				ignored -> "Ben.* /admin", bindings::put);

		assertEquals("/^Ben\\.\\* \\/admin$/.test(name)", script);
		assertTrue(bindings.isEmpty());
	}

	@Test
	void escapesPlaceholderValuesInsideRegexCharacterClasses() {
		HashMap<String, Object> bindings = new HashMap<>();

		String script = JavascriptPlaceholderParser.replace("/[%chars%]/.test(name)", ignored -> "]^-", bindings::put);

		assertEquals("/[\\]\\^\\-]/.test(name)", script);
		assertTrue(bindings.isEmpty());
	}

	@Test
	void encodedMapValueIsDecodedAndBoundAsData() {
		HashMap<String, Object> bindings = new HashMap<>();
		String injection = "'; Bukkit.dispatchCommand(Console, \"op attacker\"); '";
		String encoded = JavascriptSafeValue.encodePlaceholder(injection);

		String script = JavascriptPlaceholderParser.replace(encoded, value -> value, bindings::put);

		assertEquals("__advancedCorePlaceholder0", script);
		assertEquals(injection, bindings.get("__advancedCorePlaceholder0"));
	}

	@Test
	void preservesBooleanAndNumericPlaceholderTypes() {
		HashMap<String, Object> bindings = new HashMap<>();

		String script = JavascriptPlaceholderParser.replace("%bool% == true && %count% > 2 && %ratio% < 2.0",
				placeholder -> {
					switch (placeholder) {
					case "%bool%":
						return "true";
					case "%count%":
						return "5";
					default:
						return "1.5";
					}
				}, bindings::put);

		assertEquals("__advancedCorePlaceholder0 == true && __advancedCorePlaceholder1 > 2 && __advancedCorePlaceholder2 < 2.0",
				script);
		assertEquals(Boolean.TRUE, bindings.get("__advancedCorePlaceholder0"));
		assertEquals(Long.valueOf(5), bindings.get("__advancedCorePlaceholder1"));
		assertEquals(Double.valueOf(1.5), bindings.get("__advancedCorePlaceholder2"));
	}

	@Test
	void supportsBraceFormCustomPlaceholders() {
		HashMap<String, Object> bindings = new HashMap<>();

		String script = JavascriptPlaceholderParser.replace("{permission_result} == true",
				placeholder -> placeholder.equals("{permission_result}") ? "true" : placeholder, bindings::put);

		assertEquals("__advancedCorePlaceholder0 == true", script);
		assertEquals(Boolean.TRUE, bindings.get("__advancedCorePlaceholder0"));
	}

	@Test
	void leavesUnresolvedPlaceholdersUntouched() {
		HashMap<String, Object> bindings = new HashMap<>();

		String script = JavascriptPlaceholderParser.replace("%unknown% == true && {unknown_brace} == false", value -> value,
				bindings::put);

		assertEquals("%unknown% == true && {unknown_brace} == false", script);
		assertTrue(bindings.isEmpty());
	}
}
