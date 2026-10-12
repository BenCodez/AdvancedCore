package com.bencodez.advancedcore.tests.rewards;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.api.javascript.JavascriptTextTemplate;
import com.bencodez.advancedcore.api.messages.LiteralMessageTemplate;
import com.bencodez.advancedcore.api.messages.PlaceholderUtils;
import com.bencodez.advancedcore.api.rewards.RewardDisplayPlaceholders;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;

class MixedLegacyBindingIndexTest {
    private static final String OLD = "VCORELITERAL1Q2Z";

    @Test
    void markerIndexVisitsSixtyFourKiBOnceWith8192Bindings() throws Exception {
        Map<String, String> values = new LinkedHashMap<>();
        for (int index = 0; index < 8192; index++) values.put("K" + index, "x");
        AtomicInteger enumerations = new AtomicInteger();
        Map<String, String> counted = new AbstractMap<>() {
            @Override public Set<Entry<String, String>> entrySet() {
                enumerations.incrementAndGet();
                return values.entrySet();
            }
        };
        Class<?> type = Class.forName(LiteralMessageTemplate.class.getName() + "$MarkerMatcher");
        Constructor<?> constructor = type.getDeclaredConstructor(Map.class);
        constructor.setAccessible(true);
        Object index = constructor.newInstance(counted);
        assertEquals(1, enumerations.get());
        String fragment = "%K8191%/{k0}/%missing%/";
        String source = fragment.repeat(65536 / fragment.length() + 1).substring(0, 65536);
        CountingText input = new CountingText(source);
        Method replace = type.getDeclaredMethod("replace", CharSequence.class, int.class);
        replace.setAccessible(true);
        assertEquals(source.replace("%K8191%", "x").replace("{k0}", "x"), invoke(replace, index, input, 262144));
        assertTrue(input.reads <= input.length() * 8, "source reads must depend on text, not binding count");
        assertEquals(1, enumerations.get(), "replacement must not re-enumerate the bindings");
    }

    @Test
    void bothLegacyRestorationLanesUseOneLookupPerTokenWithoutEnumeratingTheTable() throws Exception {
        Map<String, String> values = new LinkedHashMap<>();
        StringBuilder source = new StringBuilder();
        StringBuilder expected = new StringBuilder();
        for (int index = 0; index < 4096; index++) {
            String token = "VCORELITERAL" + Integer.toString(index + 10, 36) + "Q4Z";
            String value = index == 0 ? OLD : "&cx";
            values.put(token, value);
            source.append(token).append('/');
            expected.append(value).append('/');
        }
        Method restore = LiteralMessageTemplate.class.getDeclaredMethod("restoreTokens", CharSequence.class,
                Map.class, boolean.class, boolean.class, boolean.class);
        restore.setAccessible(true);
        for (boolean guard : List.of(false, true)) {
            LookupOnlyMap table = new LookupOnlyMap(values);
            CountingText input = new CountingText(source.toString());
            assertEquals(expected.toString(), invoke(restore, null, input, table, false, false, guard));
            assertEquals(4096, table.lookups);
            assertTrue(input.reads <= input.length() * 4, "restoration must not scan once per binding");
        }
    }

    @Test
    void legacyCorpusIsIndexedBeforeLiteralAllocationAndNoInsertedOldTokenIsRescanned() throws Exception {
        var legacy = new LiteralMessageTemplate.LegacyValues(Map.of("Legacy", "visible/" + OLD),
                Map.of("Legacy", "raw/VCORELITERAL3Q4Z"), Map.of("VCORELITERAL5Q6Z", "VCORELITERAL7Q8Z"));
        var prepared = LiteralMessageTemplate.prepare("%Site%/{Legacy}", Map.of("Site", "literal"),
                Map.of("Site", "raw-site"), legacy);
        @SuppressWarnings("unchecked") Set<String> forbidden = (Set<String>) field(prepared, "forbiddenTokens");
        assertTrue(forbidden.containsAll(Set.of(OLD, "VCORELITERAL3Q4Z", "VCORELITERAL5Q6Z", "VCORELITERAL7Q8Z")));
        var indexed = legacy.bind(prepared);
        String source = indexed.protect(prepared.template(), false, false);
        assertEquals("literal/visible/" + OLD, prepared.finishText(indexed.finish(source, false)));
        String raw = indexed.protect("{Legacy}", true, false);
        assertEquals("raw/VCORELITERAL3Q4Z", indexed.finish(raw, true));
        @SuppressWarnings("unchecked") Map<String, String> tokens = (Map<String, String>) field(indexed, "visibleTokens");
        assertFalse(tokens.containsKey(OLD));
        assertThrows(UnsupportedOperationException.class, () -> tokens.put(OLD, "changed"));
    }

    @Test
    void legacyBindingRequiresAdmissionBeforeLiteralTokensAreAllocated() {
        var separatelyPrepared = LiteralMessageTemplate.prepare("%Site%", Map.of("Site", "literal"), Map.of());
        var late = new LiteralMessageTemplate.LegacyValues(Map.of("Legacy", separatelyPrepared.template()), Map.of(), Map.of());
        assertThrows(IllegalArgumentException.class, () -> late.bind(separatelyPrepared));
        var admitted = LiteralMessageTemplate.prepare("%Site% {Legacy}", Map.of("Site", "literal"), Map.of(), late);
        var indexed = late.bind(admitted);
        assertEquals("literal " + separatelyPrepared.template(),
                admitted.finishText(indexed.finish(indexed.protect(admitted.template(), false, false), false)));
        var different = new LiteralMessageTemplate.LegacyValues(Map.of("Legacy", "different"), Map.of(), Map.of());
        assertThrows(IllegalArgumentException.class, () -> different.bind(admitted));
    }

    @Test
    void decodedAdmissionCountsAll8192EntriesAndSharesTheOneMiBInputBudget() {
        Map<String, String> ordinary = corpus(8192, 1048576 - (4 + 7) - (4 + 3) - (6 + 7) - (6 + 3));
        var legacy = new LiteralMessageTemplate.LegacyValues(Map.of("Legacy", "visible"),
                Map.of("Legacy", "raw"), ordinary);
        var prepared = LiteralMessageTemplate.prepare("%Site%/{Legacy}", Map.of("Site", "literal"),
                Map.of("Site", "raw"), legacy);
        var indexed = legacy.bind(prepared);
        assertEquals("literal/visible", prepared.finishText(indexed.finish(indexed.protect(prepared.template(), false, false), false)));
        ordinary.put("K8191", ordinary.get("K8191") + "x");
        var larger = new LiteralMessageTemplate.LegacyValues(Map.of("Legacy", "visible"), Map.of("Legacy", "raw"), ordinary);
        assertThrows(IllegalArgumentException.class,
                () -> LiteralMessageTemplate.prepare("plain", Map.of("Site", "literal"), Map.of("Site", "raw"), larger));
        ordinary.put("Extra", "");
        assertThrows(IllegalArgumentException.class, () -> new LiteralMessageTemplate.LegacyValues(Map.of(), Map.of(), ordinary));
        assertEquals("visible", indexed.finish(indexed.protect("{Legacy}", false, false), false),
                "an admitted snapshot must be independent of later caller changes");
    }

    @Test
    void guarded32768CharacterLegacyValuesAreAdmittedAndActiveBindingsRemainCombinedAndBounded() {
        var legacy = new LiteralMessageTemplate.LegacyValues(Map.of("Legacy", "x".repeat(32768)),
                Map.of("Legacy", "raw"), Map.of());
        var prepared = LiteralMessageTemplate.prepare("{Legacy} %Site%", Map.of("Site", "literal"), Map.of(), legacy);
        var indexed = legacy.bind(prepared);
        assertEquals("x".repeat(32768) + " literal",
                prepared.finishText(indexed.finish(indexed.protect(prepared.template(), false, false), false)));
        assertThrows(IllegalArgumentException.class,
                () -> new LiteralMessageTemplate.LegacyValues(Map.of("Legacy", "x".repeat(32769)), Map.of(), Map.of()));
        Map<String, String> visible = new LinkedHashMap<>();
        StringBuilder template = new StringBuilder("%Site%");
        for (int position = 0; position < 4095; position++) {
            String key = "K" + position;
            visible.put(key, ""); template.append('{').append(key).append('}');
        }
        var full = new LiteralMessageTemplate.LegacyValues(visible, Map.of(), Map.of());
        var fullPrepared = LiteralMessageTemplate.prepare(template.toString(), Map.of("Site", ""), Map.of(), full);
        full.bind(fullPrepared);
        visible.put("Extra", ""); template.append("{Extra}");
        var extra = new LiteralMessageTemplate.LegacyValues(visible, Map.of(), Map.of());
        var extraPrepared = LiteralMessageTemplate.prepare(template.toString(), Map.of("Site", ""), Map.of(), extra);
        assertThrows(IllegalArgumentException.class, () -> extra.bind(extraPrepared));
    }

    @Test
    void orderedOrdinaryValuesKeepLaterExpansionBackwardReferencesAndCycles() {
        for (Map<String, String> ordinary : List.of(ordered("A", "%B%", "B", "expanded"),
                ordered("B", "expanded", "A", "%B%"), ordered("A", "%B%", "B", "%A%"),
                ordered("A", "%A%", "a", "expanded"))) {
            var input = new LiteralMessageTemplate.LegacyValues(Map.of(), Map.of(), ordinary);
            var prepared = LiteralMessageTemplate.prepare("%A% %Site%", Map.of("Site", "literal"), Map.of(), input);
            var indexed = input.bind(prepared);
            String expected = PlaceholderUtils.replacePlaceHolder("%A%", new LinkedHashMap<>(ordinary)) + " literal";
            assertEquals(expected, prepared.finishText(indexed.protect(prepared.template(), false, false)));
        }
        HashMap<String, String> values = new HashMap<>();
        values.put("A", "%B%"); values.put("B", "expanded");
        RewardDisplayPlaceholders.putLiteral(values, "Site", "literal", "fallback");
        try (var placeholders = mockStatic(PlaceholderUtils.class, CALLS_REAL_METHODS)) {
            placeholders.when(() -> PlaceholderUtils.replaceJavascript(anyString())).thenAnswer(call -> call.getArgument(0));
            assertEquals("expanded literal", BaseComponent.toPlainText(
                    RewardDisplayPlaceholders.renderLiteralComponents(null, "%A% %Site%", values)));
            assertEquals("expanded literal", BaseComponent.toPlainText(
                    RewardDisplayPlaceholders.prepareLiteralBroadcast(null, "%A% %Site%", values).render(null)));
        }
    }

    @Test
    void productionOrdinaryOrderDoesNotDependOnUnusedMetadataCapacity() {
        HashMap<String, String> values = new HashMap<>();
        values.put("P", "%A%"); values.put("A", "expanded");
        RewardDisplayPlaceholders.putLiteral(values, "Site", "literal", "fallback");
        for (int index = 0; index < 5; index++) {
            values.put("Unused" + index, "raw");
            RewardDisplayPlaceholders.put(values, "Unused" + index, "visible");
        }
        String source = "[Text=\"%P% %Site%\",Command=\"/say %P%\"]";
        try (var placeholders = mockStatic(PlaceholderUtils.class, CALLS_REAL_METHODS)) {
            placeholders.when(() -> PlaceholderUtils.replaceJavascript(anyString()))
                    .thenAnswer(call -> call.getArgument(0));
            for (TextComponent[] rendered : List.of(RewardDisplayPlaceholders.renderLiteralComponents(null, source, values),
                    RewardDisplayPlaceholders.prepareLiteralBroadcast(null, source, values).render(null))) {
                assertEquals("expanded literal", BaseComponent.toPlainText(rendered));
                assertEquals(List.of("/say expanded"), actions(rendered));
            }
        }
    }

    @Test
    void pendingOrderedExpansionBuffersAreBoundedUnderASmallHeap() throws Exception {
        String javaExecutable = java.nio.file.Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        Process child = new ProcessBuilder(javaExecutable, "-Xmx64m", "-cp", classpath,
                BoundedExpansionProbe.class.getName()).redirectErrorStream(true).start();
        try {
            assertTrue(child.waitFor(20, TimeUnit.SECONDS), "bounded expansion must terminate promptly");
            String output = new String(child.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertEquals(0, child.exitValue(), output);
        } finally {
            if (child.isAlive()) { child.destroyForcibly(); child.waitFor(5, TimeUnit.SECONDS); }
        }
    }

    public static final class BoundedExpansionProbe {
        public static void main(String[] arguments) {
            Map<String, String> ordinary = new LinkedHashMap<>();
            for (int index = 0; index < 256; index++)
                ordinary.put("K" + index, "%Big% %K" + (index + 1) + "%");
            ordinary.put("K256", "end");
            ordinary.put("Big", String.join(" ", java.util.Collections.nCopies(8, "%Base%")));
            ordinary.put("Base", "x".repeat(32767));
            var input = new LiteralMessageTemplate.LegacyValues(Map.of(), Map.of(), ordinary);
            try {
                var prepared = LiteralMessageTemplate.prepare("%K0%", Map.of(), Map.of(), input);
                input.bind(prepared).protect(prepared.template(), false, false);
            } catch (IllegalArgumentException expected) {
                if (expected.getMessage().equals("ordered placeholder expansion exceeds limits")) return;
                throw expected;
            }
            throw new AssertionError("oversized retained expansion was admitted");
        }
    }

    @Test
    void delimiterFreeValuesCanFormALaterMarkerAcrossReplacementBoundaries() {
        for (String replacement : List.of("", "C")) {
            HashMap<String, String> values = new HashMap<>();
            values.put("A", replacement);
            values.put("B" + replacement, "expanded");
            RewardDisplayPlaceholders.putLiteral(values, "Site", "literal", "fallback");
            try (var placeholders = mockStatic(PlaceholderUtils.class, CALLS_REAL_METHODS)) {
                placeholders.when(() -> PlaceholderUtils.replaceJavascript(anyString()))
                        .thenAnswer(call -> call.getArgument(0));
                assertEquals("expanded literal", BaseComponent.toPlainText(
                        RewardDisplayPlaceholders.renderLiteralComponents(null, "%B{A}% %Site%", values)));
                assertEquals("expanded literal", BaseComponent.toPlainText(
                        RewardDisplayPlaceholders.prepareLiteralBroadcast(null, "%B{A}% %Site%", values).render(null)));
            }
        }
    }

    @Test
    void sharedPercentEdgesAndMarkersFormedAcrossReplacementsKeepHistoricalGlobalOrder() {
        List<Map<String, String>> maps = List.of(ordered("B", "b", "A", "a"),
                ordered("A", "%B%", "D", "d", "B", "b"), ordered("A", "%", "B", "b"),
                ordered("A", "{A}x"), ordered("s%tail", "long", "s", "short"), ordered("A", "[{A}]"));
        List<String> templates = List.of("%A%B%", "%A%D%", "%A%B%", "%A%", "%s%tail%", "%A%");
        for (int position = 0; position < maps.size(); position++) {
            var input = new LiteralMessageTemplate.LegacyValues(Map.of(), Map.of(), maps.get(position));
            var prepared = LiteralMessageTemplate.prepare(templates.get(position), Map.of(), Map.of(), input);
            var indexed = input.bind(prepared);
            assertEquals(PlaceholderUtils.replacePlaceHolder(templates.get(position), new LinkedHashMap<>(maps.get(position))),
                    indexed.protect(prepared.template(), false, false), "case " + position);
        }
    }

    @Test
    void longOrderedChainsExpandIterativelyWithoutRescanningTheTemplatePerBinding() {
        Map<String, String> ordinary = new LinkedHashMap<>();
        for (int index = 0; index < 8191; index++) ordinary.put("K" + index, "%K" + (index + 1) + "%");
        ordinary.put("K8191", "expanded");
        var input = new LiteralMessageTemplate.LegacyValues(Map.of(), Map.of(), ordinary);
        var prepared = LiteralMessageTemplate.prepare("%K0% %Site%", Map.of("Site", "literal"), Map.of(), input);
        var indexed = input.bind(prepared);
        assertEquals("expanded literal", prepared.finishText(indexed.protect(prepared.template(), false, false)));
    }

    @Test
    void exceptionalOrderedCompatibilityWorkHasAFixedBudget() {
        Map<String, String> ordinary = new LinkedHashMap<>();
        StringBuilder source = new StringBuilder("padding".repeat(4500));
        for (int index = 0; index < 300; index++) {
            String key = "K " + index;
            ordinary.put(key, "x"); source.append('%').append(key).append('%');
        }
        var input = new LiteralMessageTemplate.LegacyValues(Map.of(), Map.of(), ordinary);
        var prepared = LiteralMessageTemplate.prepare(source.toString(), Map.of(), Map.of(), input);
        var indexed = input.bind(prepared);
        assertThrows(IllegalArgumentException.class, () -> indexed.protect(prepared.template(), false, false));
    }

    @Test
    void ordinaryCompatibilityBudgetAccumulatesAcrossAnOperationsSegments() {
        Map<String, String> ordinary = new LinkedHashMap<>();
        for (int index = 0; index < 99; index++) ordinary.put("K " + index, "%K " + (index + 1) + "%");
        ordinary.put("K 99", "done");
        String prefix = "padding".repeat(5000);
        var input = new LiteralMessageTemplate.LegacyValues(Map.of(), Map.of(), ordinary);
        var prepared = LiteralMessageTemplate.prepare(prefix + "%K 0%", Map.of(), Map.of(), input);
        var indexed = input.bind(prepared);
        var rendering = indexed.renderer();
        assertEquals(prefix + "done", rendering.protect(prepared.template(), false, false));
        assertThrows(IllegalArgumentException.class, () -> rendering.protect(prepared.template(), false, false));
        assertEquals(prefix + "done", indexed.renderer().protect(prepared.template(), false, false),
                "another rendering owns an independent budget");
    }

    @Test
    void productionEightThousandEntryMetadataRendersBothPathsWithColorsRawActionsAndOldTokenData() {
        HashMap<String, String> values = mixed("raw/" + OLD, "&cVisible/" + OLD);
        for (int index = 0; index < 4088; index++) {
            HashMap<String, String> entry = new HashMap<>();
            entry.put("Unused" + index, "raw"); RewardDisplayPlaceholders.put(entry, "Unused" + index, "display");
            values.putAll(entry);
        }
        while (values.size() < 8192) values.put("Ordinary" + values.size(), "unused");
        assertEquals(8192, values.size());
        String source = "[Text=\"{Legacy} %Site%\",Command=\"/{Legacy}/%Site%\",Hover=\"{Legacy}\"]";
        try (var placeholders = mockStatic(PlaceholderUtils.class, CALLS_REAL_METHODS)) {
            placeholders.when(() -> PlaceholderUtils.replaceJavascript(anyString())).thenAnswer(call -> call.getArgument(0));
            TextComponent[] single = RewardDisplayPlaceholders.renderLiteralComponents(null, source, values);
            var captured = RewardDisplayPlaceholders.prepareLiteralBroadcast(null, source, values);
            values.clear();
            TextComponent[] broadcast = captured.render(null);
            assertEquals("Visible/" + OLD + " &aLiteral", BaseComponent.toPlainText(single));
            assertEquals(BaseComponent.toPlainText(single), BaseComponent.toPlainText(broadcast));
            assertEquals(List.of("/raw/" + OLD + "/raw-site"), actions(broadcast));
            BaseComponent clickable = clickable(broadcast).get(0);
            Text hover = assertInstanceOf(Text.class, clickable.getHoverEvent().getContents().get(0));
            BaseComponent[] content = assertInstanceOf(BaseComponent[].class, hover.getValue());
            assertEquals("Visible/" + OLD, BaseComponent.toPlainText(content));
            assertEquals(ChatColor.RED, content[0].getColor());
            assertFalse(BaseComponent.toLegacyText(broadcast).contains("§aLiteral"));
        }
    }

    @Test
    void productionSourceRecipientAndSinglePlayerProviderPassesNeverSeeLiteralOrLegacyData() {
        Player sourcePlayer = mock(Player.class);
        Player recipient = mock(Player.class);
        HashMap<String, String> values = mixed("%untrusted_raw%", "%untrusted_visible%");
        values.put("Rank", "%rank_provider%");
        String source = "[Text=\"{Legacy} %Site% %Rank% %recipient%\",Command=\"/{Legacy}/%Site%/%recipient%\"]";
        try (var placeholders = mockStatic(PlaceholderUtils.class, CALLS_REAL_METHODS)) {
            placeholders.when(() -> PlaceholderUtils.replaceJavascript(eq(recipient), anyString())).thenAnswer(call -> call.getArgument(1));
            placeholders.when(() -> PlaceholderUtils.replacePlaceHolders(eq(sourcePlayer), anyString())).thenAnswer(call -> {
                String text = call.getArgument(1);
                assertFalse(text.contains("%untrusted_raw%")); assertFalse(text.contains("%untrusted_visible%"));
                assertFalse(text.contains("&aLiteral"));
                return text.replace("%rank_provider%", "SourceRank");
            });
            placeholders.when(() -> PlaceholderUtils.replacePlaceHolders(eq(recipient), anyString())).thenAnswer(call -> {
                String text = call.getArgument(1);
                assertFalse(text.contains("%untrusted_raw%")); assertFalse(text.contains("%untrusted_visible%"));
                assertFalse(text.contains("&aLiteral"));
                return text.replace("%rank_provider%", "RecipientRank").replace("%recipient%", "Recipient");
            });
            TextComponent[] broadcast = RewardDisplayPlaceholders.prepareLiteralBroadcast(sourcePlayer, source, values).render(recipient);
            assertEquals("%untrusted_visible% &aLiteral SourceRank Recipient", BaseComponent.toPlainText(broadcast));
            assertEquals(List.of("/%untrusted_raw%/raw-site/Recipient"), actions(broadcast));
            TextComponent[] single = RewardDisplayPlaceholders.renderLiteralComponents(recipient, source, values);
            assertEquals("%untrusted_visible% &aLiteral RecipientRank Recipient", BaseComponent.toPlainText(single));
            assertEquals(actions(broadcast), actions(single));
        }
    }

    @Test
    void legacyDisplayFragmentsCannotCreateInteractiveSyntaxAcrossAdjacentBindings() {
        HashMap<String, String> values = mixed("raw", "unused");
        values.put("A", "raw-a"); values.put("B", "raw-b");
        RewardDisplayPlaceholders.put(values, "A", "[Text=");
        RewardDisplayPlaceholders.put(values, "B", "\"unsafe\",Command=\"/bad\"]");
        String source = "%A%%B% %Site%";
        try (var placeholders = mockStatic(PlaceholderUtils.class, CALLS_REAL_METHODS)) {
            placeholders.when(() -> PlaceholderUtils.replaceJavascript(anyString())).thenAnswer(call -> call.getArgument(0));
            for (TextComponent[] rendered : List.of(RewardDisplayPlaceholders.renderLiteralComponents(null, source, values),
                    RewardDisplayPlaceholders.prepareLiteralBroadcast(null, source, values).render(null))) {
                assertTrue(actions(rendered).isEmpty());
                assertEquals("[Text=\"unsafe\",Command=\"/bad\"] &aLiteral",
                        BaseComponent.toPlainText(rendered).replace("\u2060", ""));
            }
        }
    }

    @Test
    void productionCumulativeRawActionBudgetIncludesEveryAuthoredAttributeInBothLanes() {
        HashMap<String, String> values = mixed("x".repeat(32768), "");
        RewardDisplayPlaceholders.putLiteral(values, "Site", "", "fallback");
        String attribute = "[Text=\"\",Command=\"{Legacy}\"]";
        try (var placeholders = mockStatic(PlaceholderUtils.class, CALLS_REAL_METHODS)) {
            placeholders.when(() -> PlaceholderUtils.replaceJavascript(anyString())).thenAnswer(call -> call.getArgument(0));
            assertEquals(262144, actions(RewardDisplayPlaceholders.renderLiteralComponents(null, attribute.repeat(8), values))
                    .stream().mapToInt(String::length).sum());
            var captured = RewardDisplayPlaceholders.prepareLiteralBroadcast(null, attribute.repeat(8), values);
            assertEquals(262144, actions(captured.render(null)).stream().mapToInt(String::length).sum());
            assertThrows(IllegalArgumentException.class,
                    () -> RewardDisplayPlaceholders.renderLiteralComponents(null, attribute.repeat(9), values));
            var tooLarge = RewardDisplayPlaceholders.prepareLiteralBroadcast(null, attribute.repeat(9), values);
            assertThrows(IllegalArgumentException.class, () -> tooLarge.render(null));
        }
    }

    @Test
    void indexedLegacyJavascriptKeepsTheHistoricalBoundArgumentAndFinalRawOutput() {
        String raw = "raw'$\\value";
        HashMap<String, String> values = mixed(raw, "&cVisible");
        List<String> expressions = new ArrayList<>();
        try (var placeholders = mockStatic(PlaceholderUtils.class, CALLS_REAL_METHODS)) {
            placeholders.when(() -> PlaceholderUtils.replaceJavascript(anyString())).thenAnswer(call -> {
                String text = call.getArgument(0);
                assertFalse(text.contains(raw));
                return JavascriptTextTemplate.parse(text).evaluate(java.util.function.Function.identity(), expression -> {
                    expressions.add(expression); return "/computed";
                });
            });
            String source = "[Text=\"%Site%\",Command=\"[Javascript='/{Legacy}']\"]";
            HashMap<String, String> legacy = new HashMap<>();
            legacy.put("Legacy", raw); RewardDisplayPlaceholders.put(legacy, "Legacy", "&cVisible");
            RewardDisplayPlaceholders.replaceFormattedMessage(source, legacy);
            TextComponent[] current = RewardDisplayPlaceholders.renderLiteralComponents(null, source, values);
            assertEquals(List.of("/computed"), actions(current));
            assertEquals(2, expressions.size());
            assertEquals(expressions.get(0), expressions.get(1));
        }
    }

    @Test
    void recipientVariantsShareImmutableIndexesAndKeepTheirIndependentOutputs() throws Exception {
        HashMap<String, String> values = mixed("raw", "visible");
        var captured = RewardDisplayPlaceholders.prepareLiteralBroadcast(null,
                "[Text=\"{Legacy} %Site%\",Command=\"/{Legacy}\"]", values);
        Object index = field(captured, "legacy");
        Object visible = field(index, "visibleTokens");
        CompletableFuture<TextComponent[]> first = CompletableFuture.supplyAsync(() -> captured.render(null));
        CompletableFuture<TextComponent[]> second = CompletableFuture.supplyAsync(() -> captured.render(null));
        assertEquals("visible &aLiteral", BaseComponent.toPlainText(first.get(5, TimeUnit.SECONDS)));
        assertEquals(List.of("/raw"), actions(second.get(5, TimeUnit.SECONDS)));
        assertSame(index, field(captured, "legacy"));
        assertSame(visible, field(index, "visibleTokens"));
    }

    private static Map<String, String> ordered(String... pairs) {
        Map<String, String> result = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) result.put(pairs[index], pairs[index + 1]);
        return result;
    }

    private static Map<String, String> corpus(int count, int totalCharacters) {
        Map<String, String> result = new LinkedHashMap<>();
        int remaining = totalCharacters;
        for (int index = 0; index < count; index++) remaining -= ("K" + index).length();
        for (int index = 0; index < count; index++) {
            int length = Math.min(32768, remaining);
            result.put("K" + index, "x".repeat(length)); remaining -= length;
        }
        assertEquals(0, remaining);
        return result;
    }

    private static HashMap<String, String> mixed(String raw, String display) {
        HashMap<String, String> values = new HashMap<>();
        values.put("Site", "raw-site"); values.put("Legacy", raw);
        RewardDisplayPlaceholders.putLiteral(values, "Site", "&aLiteral", "fallback");
        RewardDisplayPlaceholders.put(values, "Legacy", display);
        return values;
    }

    private static List<String> actions(BaseComponent[] components) {
        List<String> result = new ArrayList<>();
        for (BaseComponent component : clickable(components)) result.add(component.getClickEvent().getValue());
        return result;
    }

    private static List<BaseComponent> clickable(BaseComponent[] components) {
        List<BaseComponent> result = new ArrayList<>();
        for (BaseComponent component : components) {
            if (component.getClickEvent() != null) result.add(component);
            if (component.getExtra() != null) result.addAll(clickable(component.getExtra().toArray(new BaseComponent[0])));
        }
        return result;
    }

    private static Object field(Object receiver, String name) throws Exception {
        Field field = receiver.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(receiver);
    }

    private static Object invoke(Method method, Object receiver, Object... arguments) throws Exception {
        try { return method.invoke(receiver, arguments); }
        catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof Exception cause) throw cause;
            if (failure.getCause() instanceof Error cause) throw cause;
            throw failure;
        }
    }

    private static final class CountingText implements CharSequence {
        private final String value;
        private int reads;
        private CountingText(String value) { this.value = value; }
        @Override public int length() { return value.length(); }
        @Override public char charAt(int index) { reads++; return value.charAt(index); }
        @Override public CharSequence subSequence(int start, int end) { return value.subSequence(start, end); }
    }

    private static final class LookupOnlyMap extends AbstractMap<String, String> {
        private final Map<String, String> values;
        private int lookups;
        private LookupOnlyMap(Map<String, String> values) { this.values = values; }
        @Override public String get(Object key) { lookups++; return values.get(key); }
        @Override public Set<Entry<String, String>> entrySet() {
            throw new AssertionError("token restoration must use lookup, not enumerate the table");
        }
    }
}
