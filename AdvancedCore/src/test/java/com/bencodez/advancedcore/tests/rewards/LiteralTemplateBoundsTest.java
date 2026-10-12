package com.bencodez.advancedcore.tests.rewards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.AbstractMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.api.messages.LiteralMessageTemplate;
import com.bencodez.advancedcore.api.messages.PlaceholderUtils;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;

class LiteralTemplateBoundsTest {
    @Test
    void acceptsMaximumBindingCountWithOnlyOneReferencedLiteral() {
        Map<String, String> values = summary(8192);
        String source = "x".repeat(65524) + "%Site0%";
        var prepared = LiteralMessageTemplate.prepare(source, values, Map.of());
        assertEquals("x".repeat(65524) + "literal0", prepared.finishText(prepared.template()));
    }

    @Test
    void repeatsAndUnreferencedBindingsDoNotConsumeTheActiveBindingLimit() {
        Map<String, String> values = summary(8192);
        String source = "%Site0%{sItE0}".repeat(100);
        var prepared = LiteralMessageTemplate.prepare(source, values, Map.of());
        assertEquals("literal0literal0".repeat(100), prepared.finishText(prepared.template()));
        assertEquals("plain", LiteralMessageTemplate.prepare("plain", values, null).template());
    }

    @Test
    void rejectsOverLimitMapsBeforeRequestingTheirEntries() {
        Map<String, String> oversized = new AbstractMap<>() {
            @Override public int size() { return 8193; }
            @Override public Set<Entry<String, String>> entrySet() {
                throw new AssertionError("oversized input must be rejected before iteration");
            }
        };
        assertThrows(IllegalArgumentException.class,
                () -> LiteralMessageTemplate.prepare("plain", oversized, Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> LiteralMessageTemplate.prepare("plain", Map.of(), oversized));
    }

    @Test
    void referencedBindingsAcceptTheirLimitAndRejectTheNextDistinctMarker() {
        Map<String, String> values = new LinkedHashMap<>();
        StringBuilder source = new StringBuilder();
        for (int index = 0; index < 4096; index++) {
            values.put("K" + index, "");
            source.append("%K").append(index).append('%');
        }
        assertTrue(LiteralMessageTemplate.prepare(source.toString(), values, null).template().contains("VCORELITERAL"));
        values.put("K4096", "");
        source.append("%K4096%");
        assertThrows(IllegalArgumentException.class,
                () -> LiteralMessageTemplate.prepare(source.toString(), values, null));
    }

    @Test
    void aggregateCharacterBudgetAppliesToBothMapsAtAndBeyondItsLimit() {
        Map<String, String> literals = fullCharacterBudget();
        assertEquals("plain", LiteralMessageTemplate.prepare("plain", literals, null).template());
        assertThrows(IllegalArgumentException.class,
                () -> LiteralMessageTemplate.prepare("plain", literals, Map.of("A", "")));
        String last = "K127";
        literals.put(last, literals.get(last) + "x");
        assertThrows(IllegalArgumentException.class,
                () -> LiteralMessageTemplate.prepare("plain", literals, null));
    }

    @Test
    void singleValueAndTemplateCharacterLimitsPreserveUtf16Counterexamples() {
        String value = "\uD83D\uDE00".repeat(4096);
        var prepared = LiteralMessageTemplate.prepare("%Name%", Map.of("Name", value), null);
        assertEquals(value, prepared.finishText(prepared.template()));
        assertThrows(IllegalArgumentException.class,
                () -> LiteralMessageTemplate.prepare("plain", Map.of("Name", value + "x"), null));
        assertEquals(65536, LiteralMessageTemplate.prepare("x".repeat(65536), null, null).template().length());
        assertThrows(IllegalArgumentException.class,
                () -> LiteralMessageTemplate.prepare("x".repeat(65537), null, null));
        String key = "A".repeat(128);
        var longestKey = LiteralMessageTemplate.prepare("{" + key + "}", Map.of(key, "valid"), null);
        assertEquals("valid", longestKey.finishText(longestKey.template()));
        assertThrows(IllegalArgumentException.class,
                () -> LiteralMessageTemplate.prepare("plain", Map.of(key + "A", "invalid"), null));
    }

    @Test
    void overlappingDelimiterMarkersKeepOriginalLeftToRightReplacement() {
        Map<String, String> values = Map.of("Site", "display", "Next", "next");
        Map<String, String> expected = Map.of(
                "%missing%Site%", "%missingdisplay",
                "%Site%Next%", "displayNext%",
                "{%Site%}", "{display}",
                "{{SITE}}", "{display}",
                "%Site%%Next%", "displaynext");
        expected.forEach((source, text) -> {
            var prepared = LiteralMessageTemplate.prepare(source, values, null);
            assertEquals(text, prepared.finishText(prepared.template()), source);
        });
    }

    @Test
    void javaCaseFoldingAndInsertedMarkerTextRemainCompatible() {
        for (String marker : new String[] { "%\u017fITE%", "{s\u0131te}", "%SITE%" }) {
            var prepared = LiteralMessageTemplate.prepare(marker,
                    Map.of("Site", "%Other%", "Other", "must not be rescanned"), null);
            assertEquals("%Other%", prepared.finishText(prepared.template()), marker);
        }
        var kelvin = LiteralMessageTemplate.prepare("%\u212aEY%", Map.of("Key", "ok"), null);
        assertEquals("ok", kelvin.finishText(kelvin.template()));
    }

    @Test
    void rawActionIndexSupportsNonLiteralKeysAndOriginalMapPriority() {
        Map<String, String> actions = new LinkedHashMap<>();
        actions.put("s", "short");
        actions.put("s%tail", "long");
        actions.put("\u03a3", "greek");
        actions.put("name with space", "space");
        actions.put("\uD801\uDC00", "supplementary");
        String source = "[Text=\"%Site%\",Command=\"/inspect %s%tail%/{\u03c2}/%name with space%/{\uD801\uDC28}/%Site%\"]";
        TextComponent result = render(source, Map.of("Site", "display"), actions);
        assertEquals("display", result.toPlainText());
        assertEquals("/inspect shorttail%/greek/space/supplementary/", action(result));
    }

    @Test
    void overlappingRawMarkersRetainFirstUseOrderingAcrossAttributes() {
        Map<String, String> actions = new LinkedHashMap<>();
        actions.put("s%tail", "long");
        actions.put("s", "short");
        TextComponent result = render("[Text=\"A\",Command=\"%s%\"][Text=\"B\",Command=\"%s%tail%\"]",
                Map.of(), actions);
        String json = net.md_5.bungee.chat.ComponentSerializer.toString(result);
        assertTrue(json.contains("shorttail%"), json);
        assertTrue(!json.contains("\"value\":\"long\""), json);
    }

    @Test
    void maximumRawActionMapRetainsItsSeparateValue() {
        Map<String, String> actions = summary(8192);
        actions.put("Site0", "raw");
        TextComponent result = render("[Text=\"%Site0%\",Command=\"/inspect %Site0%\"]",
                Map.of("Site0", "display"), actions);
        assertEquals("display", result.toPlainText());
        assertEquals("/inspect raw", action(result));
        assertThrows(IllegalArgumentException.class,
                () -> LiteralMessageTemplate.prepare("plain", null, Map.of("a".repeat(1025), "")));
        String key = "a".repeat(1024);
        TextComponent longestKey = render("[Text=\"ok\",Command=\"{" + key + "}\"]", Map.of(), Map.of(key, "raw"));
        assertEquals("raw", action(longestKey));
    }

    @Test
    void templateIndexReadsCharactersOnceRegardlessOfUnreferencedMapSize() throws Exception {
        Method index = LiteralMessageTemplate.class.getDeclaredMethod("literalMarkerKeys", CharSequence.class);
        index.setAccessible(true);
        CountingText source = new CountingText("%Absent%".repeat(8192));
        assertEquals(Set.of("absent"), index.invoke(null, source));
        assertTrue(source.reads <= source.length() * 3, "marker admission must not scan by binding");
        var prepared = LiteralMessageTemplate.prepare(source.text, summary(8192), Map.of());
        assertEquals(source.text, prepared.template());
    }

    @Test
    void replacementIndexReadsCharactersIndependentlyOfBindingCount() throws Exception {
        Class<?> matcher = Class.forName(LiteralMessageTemplate.class.getName() + "$MarkerMatcher");
        Constructor<?> constructor = matcher.getDeclaredConstructor(Map.class);
        constructor.setAccessible(true);
        Object indexed = constructor.newInstance(summary(8192));
        Method match = matcher.getDeclaredMethod("match", CharSequence.class, int.class, Set.class);
        match.setAccessible(true);
        CountingText source = new CountingText("%Absent%".repeat(8192));
        for (int cursor = 0; cursor < source.length(); cursor++) match.invoke(indexed, source, cursor, null);
        assertTrue(source.reads <= source.length() * 3, "unmatched markers must not try every binding");
    }

    private static Map<String, String> summary(int count) {
        Map<String, String> values = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) values.put("Site" + index, "literal" + index);
        return values;
    }

    private static Map<String, String> fullCharacterBudget() {
        Map<String, String> values = new LinkedHashMap<>();
        int remaining = 1048576;
        for (int index = 0; index < 128; index++) remaining -= ("K" + index).length();
        for (int index = 0; index < 128; index++) {
            int length = Math.min(8192, remaining);
            values.put("K" + index, "x".repeat(length));
            remaining -= length;
        }
        assertEquals(0, remaining);
        return values;
    }

    private static TextComponent render(String source, Map<String, String> values, Map<String, String> actions) {
        var prepared = LiteralMessageTemplate.prepare(source, values, actions);
        return prepared.finish(PlaceholderUtils.parseJson(prepared.template()));
    }

    private static String action(BaseComponent component) {
        if (component.getClickEvent() != null) return component.getClickEvent().getValue();
        if (component.getExtra() != null) {
            for (BaseComponent child : component.getExtra()) {
                String result = action(child);
                if (result != null) return result;
            }
        }
        return null;
    }

    private static final class CountingText implements CharSequence {
        private final String text;
        private int reads;
        private CountingText(String text) { this.text = text; }
        @Override public int length() { return text.length(); }
        @Override public char charAt(int index) { reads++; return text.charAt(index); }
        @Override public CharSequence subSequence(int start, int end) { return text.subSequence(start, end); }
    }
}
