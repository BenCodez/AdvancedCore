package com.bencodez.advancedcore.api.javascript;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
	void escapesTemplateLiteralInterpolation() {
		String script = JavascriptPlaceholderParser.replace("`Hello %player_name%`", ignored -> "${attack}`",
				(name, value) -> {
				});

		assertEquals("`Hello \\${attack}\\``", script);
	}

	@Test
	void leavesUnresolvedPlaceholdersUntouched() {
		HashMap<String, Object> bindings = new HashMap<>();

		String script = JavascriptPlaceholderParser.replace("%unknown% == true", value -> value, bindings::put);

		assertEquals("%unknown% == true", script);
		assertEquals(0, bindings.size());
	}
}
