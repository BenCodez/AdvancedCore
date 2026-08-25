package com.bencodez.advancedcore.api.messages;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;

import org.junit.jupiter.api.Test;

class PlaceholderUtilsSecurityTest {

	@Test
	void placeholderValueCannotCreateJavascriptMarker() {
		HashMap<String, String> placeholders = new HashMap<>();
		placeholders.put("displayname", "[Javascript=Bukkit.dispatchCommand(Console,'op attacker')]");

		String result = PlaceholderUtils.replacePlaceHolder("Thanks %displayname%", placeholders);

		assertFalse(result.contains("[Javascript="));
		assertTrue(result.contains("Javascript="));
	}

	@Test
	void substitutionsCannotAssembleJavascriptMarkerAcrossTemplateText() {
		HashMap<String, String> placeholders = new HashMap<>();
		placeholders.put("part", "script");

		String result = PlaceholderUtils.replacePlaceHolder(
				"[Java%part%=Bukkit.dispatchCommand(Console,'op attacker')]", placeholders);

		assertFalse(result.contains("[Javascript="));
		assertTrue(result.contains("Javascript="));
	}

	@Test
	void multipleSubstitutionsCannotAssembleJavascriptMarker() {
		HashMap<String, String> placeholders = new HashMap<>();
		placeholders.put("prefix", "Java");
		placeholders.put("suffix", "script");

		String result = PlaceholderUtils.replacePlaceHolder(
				"[%prefix%%suffix%=Bukkit.dispatchCommand(Console,'op attacker')]", placeholders);

		assertFalse(result.contains("[Javascript="));
		assertTrue(result.contains("Javascript="));
	}

	@Test
	void operatorAuthoredJavascriptMarkerRemainsAvailable() {
		HashMap<String, String> placeholders = new HashMap<>();
		placeholders.put("name", "Ben");

		String result = PlaceholderUtils.replacePlaceHolder("[Javascript=1+1] %name%", placeholders);

		assertTrue(result.contains("[Javascript=1+1]"));
		assertTrue(result.contains("Ben"));
	}

	@Test
	void mapValueInsideAuthoredJavascriptMarkerIsNotCopiedIntoSource() {
		HashMap<String, String> placeholders = new HashMap<>();
		String injection = "'; Bukkit.dispatchCommand(Console, \"op attacker\"); '";
		placeholders.put("displayname", injection);

		String result = PlaceholderUtils.replacePlaceHolder("[Javascript='%displayname%']", placeholders);

		assertTrue(result.startsWith("[Javascript='"));
		assertFalse(result.contains("Bukkit.dispatchCommand"));
		assertFalse(result.contains(injection));
		assertTrue(result.contains("__advancedcore_js_value:"));
	}

	@Test
	void emptyPlaceholderMapLeavesOperatorAuthoredMarkerUntouched() {
		String result = PlaceholderUtils.replacePlaceHolder("[Javascript=1+1]", new HashMap<>());

		assertTrue(result.contains("[Javascript=1+1]"));
	}

	@Test
	void normalPlaceholderFormattingIsPreserved() {
		HashMap<String, String> placeholders = new HashMap<>();
		placeholders.put("displayname", "&aDisplay Name");

		String result = PlaceholderUtils.replacePlaceHolder("Thanks %displayname%", placeholders);

		assertTrue(result.contains("&aDisplay Name"));
	}
}
