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
