package com.bencodez.advancedcore.tests.rewards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.api.messages.LiteralMessageTemplate;
import com.bencodez.advancedcore.api.messages.PlaceholderUtils;
import com.bencodez.advancedcore.api.rewards.RewardDisplayPlaceholders;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;

class LiteralActionKeyFoldTest {
    @Test
    void literalActionLookupUsesTheMarkerCodepointFoldForEveryAsciiAlias() {
        for (String key : List.of("S\u0130TE", "s\u0131te", "\u017fite", "SITE")) {
            var prepared = LiteralMessageTemplate.prepare(
                    "[Text=\"%\u017f\u0130TE%\",Command=\"/{S\u0131TE}\"]",
                    Map.of("Site", "visible"), Map.of(key, "raw/" + key));
            TextComponent rendered = prepared.finish(PlaceholderUtils.parseJson(prepared.template()));
            assertEquals("visible", rendered.toPlainText(), key);
            assertEquals(List.of("/raw/" + key), actions(rendered), key);
        }
        var kelvin = LiteralMessageTemplate.prepare("[Text=\"{\u212aey}\",Command=\"%KEY%\"]",
                Map.of("Key", "visible"), Map.of("\u212aEY", "raw-kelvin"));
        assertEquals(List.of("raw-kelvin"), actions(kelvin.finish(PlaceholderUtils.parseJson(kelvin.template()))));
    }

    @Test
    void arbitraryActionKeysMatchDottedIFinalSigmaAndSupplementaryCasePairs() {
        Map<String, String> actions = new LinkedHashMap<>();
        actions.put("\u0130", "i");
        actions.put("\u017f", "s");
        actions.put("\u212a", "k");
        actions.put("\u03a3", "greek");
        actions.put("\uD801\uDC00", "astral");
        var prepared = LiteralMessageTemplate.prepare(
                "[Text=\"open\",Command=\"%i%/{\u0131}/%S%/{K}/%\u03c2%/{\u03c3}/%\uD801\uDC28%\"]",
                Map.of(), actions);
        assertEquals(List.of("i/i/s/k/greek/greek/astral"),
                actions(prepared.finish(PlaceholderUtils.parseJson(prepared.template()))));
    }

    @Test
    void caseAmbiguousActionKeysAreRejectedBeforeUseInEitherMapOrder() {
        for (List<String> pair : List.of(List.of("i", "\u0130"), List.of("i", "\u0131"),
                List.of("s", "\u017f"), List.of("k", "\u212a"), List.of("\u03c3", "\u03c2"),
                List.of("\uD801\uDC00", "\uD801\uDC28"), List.of("Site", "S\u0130TE"))) {
            for (int first = 0; first < 2; first++) {
                Map<String, String> actions = new LinkedHashMap<>();
                actions.put(pair.get(first), "first");
                actions.put(pair.get(1 - first), "second");
                assertThrows(IllegalArgumentException.class,
                        () -> LiteralMessageTemplate.prepare("plain", Map.of(), actions), pair.toString());
            }
        }
    }

    @Test
    void fullStringLowercaseExpansionIsNotTheMarkerFold() {
        var prepared = LiteralMessageTemplate.prepare("[Text=\"open\",Command=\"%\u0130%/%i\u0307%\"]",
                Map.of(), Map.of("\u0130", "simple-i", "i\u0307", "combining-dot"));
        assertEquals(List.of("simple-i/combining-dot"),
                actions(prepared.finish(PlaceholderUtils.parseJson(prepared.template()))));
        assertEquals("i", LiteralMessageTemplate.normalizeBindingKey("\u0130"));
        assertEquals("i\u0307", LiteralMessageTemplate.normalizeBindingKey("i\u0307"));
    }

    @Test
    void productionSingleAndBroadcastPathsUseUnicodeRawAliasesAndRejectAmbiguousRawKeys() {
        HashMap<String, String> values = new HashMap<>();
        values.put("S\u0130TE", "raw-site");
        values.put("Legacy", "raw-legacy");
        RewardDisplayPlaceholders.putLiteral(values, "Site", "visible-site", "fallback");
        RewardDisplayPlaceholders.put(values, "Legacy", "&cLegacy");
        String source = "[Text=\"%\u017f\u0130TE% {Legacy}\",Command=\"/%Site%/{Legacy}\"]";
        try (var placeholders = mockStatic(PlaceholderUtils.class, CALLS_REAL_METHODS)) {
            placeholders.when(() -> PlaceholderUtils.replaceJavascript(anyString())).thenAnswer(call -> call.getArgument(0));
            TextComponent[] single = RewardDisplayPlaceholders.renderLiteralComponents(null, source, values);
            TextComponent[] broadcast = RewardDisplayPlaceholders.prepareLiteralBroadcast(null, source, values).render(null);
            assertEquals("visible-site Legacy", BaseComponent.toPlainText(single));
            assertEquals(BaseComponent.toPlainText(single), BaseComponent.toPlainText(broadcast));
            assertEquals(List.of("/raw-site/raw-legacy"), actions(single[0]));
            assertEquals(actions(single[0]), actions(broadcast[0]));
            values.put("Site", "ambiguous");
            assertThrows(IllegalArgumentException.class,
                    () -> RewardDisplayPlaceholders.renderLiteralComponents(null, source, values));
            assertThrows(IllegalArgumentException.class,
                    () -> RewardDisplayPlaceholders.prepareLiteralBroadcast(null, source, values));
        }
    }

    private static List<String> actions(BaseComponent component) {
        List<String> result = new ArrayList<>();
        if (component.getClickEvent() != null) result.add(component.getClickEvent().getValue());
        if (component.getExtra() != null) for (BaseComponent child : component.getExtra()) result.addAll(actions(child));
        return result;
    }
}
