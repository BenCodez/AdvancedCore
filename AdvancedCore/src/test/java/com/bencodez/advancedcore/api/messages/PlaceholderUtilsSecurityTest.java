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
	void normalPlaceholderFormattingIsPreserved() {
		HashMap<String, String> placeholders = new HashMap<>();
		placeholders.put("displayname", "&aDisplay Name");

		String result = PlaceholderUtils.replacePlaceHolder("Thanks %displayname%", placeholders);

		assertTrue(result.contains("&aDisplay Name"));
	}
}
