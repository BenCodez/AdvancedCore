package com.bencodez.advancedcore.api.messages;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;

import org.junit.jupiter.api.Test;

class PlaceholderUtilsJavascriptBoundaryTest {

    @Test
    void customPlaceholderCannotCreateJavascriptMarker() {
        HashMap<String, String> placeholders = new HashMap<>();
        placeholders.put("value", "[Javascript=Bukkit.dispatchCommand(Console,'op attacker')]");

        String result = PlaceholderUtils.replacePlaceHolder("prefix %value%", placeholders);

        assertFalse(result.contains("[Javascript="));
        assertEquals("prefix [Javascript =Bukkit.dispatchCommand(Console,'op attacker')]", result);
    }

    @Test
    void multipleSubstitutionsCannotAssembleJavascriptMarker() {
        HashMap<String, String> placeholders = new HashMap<>();
        placeholders.put("part1", "Java");
        placeholders.put("part2", "script");

        String result = PlaceholderUtils.replacePlaceHolder("[%part1%%part2%=danger]", placeholders);

        assertFalse(result.contains("[Javascript="));
        assertEquals("[Javascript =danger]", result);
    }

    @Test
    void customValuesInsideAuthoredMarkerAreEncodedAsData() {
        HashMap<String, String> placeholders = new HashMap<>();
        String injection = "'; Bukkit.dispatchCommand(Console,'op attacker'); '";
        placeholders.put("value", injection);

        String result = PlaceholderUtils.replacePlaceHolder("[Javascript='%value%']", placeholders);

        assertTrue(result.startsWith("[Javascript='%__advancedcore_bound_"));
        assertFalse(result.contains(injection));
    }
}
