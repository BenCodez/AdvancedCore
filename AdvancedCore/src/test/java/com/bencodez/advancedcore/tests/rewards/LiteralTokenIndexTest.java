package com.bencodez.advancedcore.tests.rewards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.api.messages.LiteralMessageTemplate;
import com.bencodez.advancedcore.api.messages.PlaceholderUtils;
import com.bencodez.advancedcore.api.javascript.JavascriptTextTemplate;
import com.bencodez.advancedcore.api.rewards.RewardDisplayPlaceholders;
import com.bencodez.simpleapi.array.ArrayUtils;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;

class LiteralTokenIndexTest {
    private static final String PREFIX = "VCORELITERAL";
    private static final String OLD_TOKEN = PREFIX + "1Q2Z";

    @Test
    void indexesTheExistingTokenGrammarIncludingEmbeddedTokensAndRejectsLookalikes() throws Exception {
        String longest = PREFIX + "z".repeat(13) + "Q" + "0".repeat(13) + "Z";
        List<String> invalid = List.of(PREFIX + "Q1Z", PREFIX + "1QZ", PREFIX + "A" + "Q1Z",
                PREFIX + "a".repeat(14) + "Q1Z", PREFIX + "1Q" + "a".repeat(14) + "Z",
                PREFIX + "1q2Z", PREFIX + "1Q2z", "vcoreliteral1Q2Z", PREFIX + "PROBE",
                PREFIX + "1Q2_ Z", PREFIX + "1Q2", PREFIX);
        Object index = index(Set.of());
        add(index, "left" + OLD_TOKEN + "suffix/" + longest + "/" + String.join("/", invalid));
        assertEquals(Set.of(OLD_TOKEN, longest), snapshot(index));
    }

    @Test
    void indexesOneMiBCorpusOnceAndAllocates4096TokensWithoutReadingItAgain() throws Exception {
        Object index = index(Set.of());
        String fragment = PREFIX + "a".repeat(14) + "Q1Z/" + OLD_TOKEN + "/";
        CountingText corpus = new CountingText(fragment.repeat(1048576 / fragment.length() + 1).substring(0, 1048576));
        add(index, corpus);
        assertTrue(corpus.reads <= corpus.length() * 3, "indexing must be bounded by corpus characters");
        int indexedReads = corpus.reads;
        AtomicInteger next = new AtomicInteger();
        for (int position = 0; position < 4096; position++) {
            String expected = token(position + 100);
            assertEquals(expected, allocate(index, () -> token(next.getAndIncrement() + 100)));
        }
        assertEquals(indexedReads, corpus.reads, "allocations must query the index rather than reread input");
        assertEquals(4097, snapshot(index).size());
    }

    @Test
    void collisionRetriesRemainBoundedAndGeneratedTokensAreReserved() throws Exception {
        Object index = index(Set.of(OLD_TOKEN));
        AtomicInteger attempts = new AtomicInteger();
        assertThrows(IllegalStateException.class, () -> allocate(index, () -> {
            attempts.incrementAndGet();
            return OLD_TOKEN;
        }));
        assertEquals(16, attempts.get());
        assertEquals(token(10), allocate(index, () -> token(10)));
        AtomicInteger collisionThenFree = new AtomicInteger();
        assertEquals(token(11), allocate(index,
                () -> collisionThenFree.getAndIncrement() == 0 ? token(10) : token(11)));
        assertEquals(2, collisionThenFree.get());
    }

    @Test
    void retainedCollisionIndexRejectsGrowthAtItsBoundWithoutEvictingExistingTokens() throws Exception {
        Set<String> full = new AbstractSet<>() {
            @Override public int size() { return 131072; }
            @Override public boolean contains(Object value) { return OLD_TOKEN.equals(value); }
            @Override public Iterator<String> iterator() { throw new AssertionError("no corpus copy is needed"); }
        };
        Object index = index(full);
        assertThrows(IllegalArgumentException.class, () -> allocate(index, () -> token(10)));
        AtomicInteger attempts = new AtomicInteger();
        assertThrows(IllegalStateException.class, () -> allocate(index, () -> {
            attempts.incrementAndGet();
            return OLD_TOKEN;
        }));
        assertEquals(16, attempts.get());
        assertSame(full, snapshot(index));
    }

    @Test
    void preparationIndexesSourceAllInputValuesAndKeysWithoutTreatingOldTokensAsItsOwn() throws Exception {
        Map<String, String> literals = Map.of("Site", "display/" + token(3), "Unused", token(4), token(5), "unused");
        Map<String, String> actions = Map.of("Site", "raw/" + token(6), token(7), token(8));
        var prepared = LiteralMessageTemplate.prepare(OLD_TOKEN + "/%Site%", literals, actions);
        assertTrue(forbidden(prepared).containsAll(Set.of(OLD_TOKEN, token(3), token(4), token(5), token(6), token(7), token(8))));
        assertEquals(OLD_TOKEN + "/display/" + token(3), prepared.finishText(prepared.template()));
        assertThrows(UnsupportedOperationException.class, () -> forbidden(prepared).add(token(99)));
    }

    @Test
    void composedScriptTokensEnterBothCollisionLanesAndRemainLiteral() throws Exception {
        var prepared = LiteralMessageTemplate.prepare("[Javascript=%A%%B%]",
                Map.of("A", PREFIX, "B", "1Q2Z"), Map.of("A", PREFIX + "3", "B", "Q4Z"));
        assertTrue(forbidden(prepared).containsAll(Set.of(OLD_TOKEN, token(3))));
        assertEquals("[Javascript=" + OLD_TOKEN + "]", prepared.finishText(prepared.template()));
        var action = LiteralMessageTemplate.prepare("[Text=\"open\",Command=\"[Javascript=%A%%B%]\"]",
                Map.of("A", PREFIX, "B", "1Q2Z"), Map.of("A", PREFIX + "3", "B", "Q4Z"));
        assertEquals("[Javascript=" + token(3) + "]", actions(render(action)).get(0));
    }

    @Test
    void restorationReadsOriginalTextOnceWith4096BindingsAndNeverEnumeratesThem() throws Exception {
        Map<String, String> values = new HashMap<>();
        StringBuilder source = new StringBuilder();
        StringBuilder expected = new StringBuilder();
        for (int position = 0; position < 4096; position++) {
            values.put(token(position + 100), "x");
            source.append(token(position + 100));
            expected.append('x');
            if (position % 64 == 0) {
                source.append(PREFIX).append("a".repeat(14)).append("Q1Z/");
                expected.append(PREFIX).append("a".repeat(14)).append("Q1Z/");
            }
        }
        CountingText text = new CountingText(source.toString());
        LookupOnlyMap lookup = new LookupOnlyMap(values);
        String restored = restore(text, lookup, false);
        assertEquals(expected.toString(), restored);
        assertEquals(4096, lookup.lookups);
        assertTrue(text.reads <= text.length() * 3, "restoration must not scan once per token or occurrence");
    }

    @Test
    void earliestTokenAndSharedNumericPrefixesKeepOrderAndInsertedValuesAreNeverRescanned() throws Exception {
        String first = PREFIX + "1QaZ";
        String second = PREFIX + "1aQaZ";
        Map<String, String> values = new LinkedHashMap<>();
        values.put(second, "second");
        values.put(first, second);
        assertEquals("second/" + second + "/second", restore(second + "/" + first + "/" + second, values, false));
        assertEquals("(section)a", restore(first, Map.of(first, "§a"), true));
        assertEquals("§a", restore(first, Map.of(first, "§a"), false));
        assertEquals(OLD_TOKEN + "/" + PREFIX + "PROBE", restore(OLD_TOKEN + "/" + PREFIX + "PROBE", values, true));
    }

    @Test
    void all4096ReferencedValuesFitOneMiBBindingCorpusAndExactly262144OutputCharacters() {
        Map<String, String> literals = new LinkedHashMap<>();
        StringBuilder source = new StringBuilder();
        int remaining = 1048576;
        for (int position = 0; position < 4096; position++) {
            String key = "K" + position;
            literals.put(key, "x".repeat(64));
            remaining -= key.length() + 64;
            source.append('%').append(key).append('%');
        }
        Map<String, String> actions = new LinkedHashMap<>();
        for (int position = 0; position < 96; position++) remaining -= ("Unused" + position).length();
        for (int position = 0; position < 96; position++) {
            int length = Math.min(8192, remaining);
            String fragment = OLD_TOKEN + "/";
            actions.put("Unused" + position, fragment.repeat(length / fragment.length() + 1).substring(0, length));
            remaining -= length;
        }
        assertEquals(0, remaining);
        var prepared = LiteralMessageTemplate.prepare(source.toString(), literals, actions);
        assertEquals("x".repeat(262144), prepared.finishText(prepared.template()));
        assertThrows(IllegalArgumentException.class, () -> prepared.finishText(prepared.template() + "x"));
    }

    @Test
    void outputBudgetIncludesOrdinarySuffixesAndSectionSignExpansion() {
        var plain = LiteralMessageTemplate.prepare("plain", Map.of(), Map.of());
        assertEquals("x".repeat(262144), plain.finishText("x".repeat(262144)));
        assertThrows(IllegalArgumentException.class, () -> plain.finishText("x".repeat(262145)));
        var sections = LiteralMessageTemplate.prepare("%Site%".repeat(4), Map.of("Site", "§".repeat(8192)), Map.of());
        assertThrows(IllegalArgumentException.class, () -> sections.finishText(sections.template()));
    }

    @Test
    void visibleHoverAndRawActionsPreserveUnicodeOverlapsAndOldTokenLookingData() {
        String literal = "§a[Text=\"unsafe\"]%Other%{Other}/" + OLD_TOKEN;
        String raw = "raw/§z/[Javascript=1]/\"/" + token(3);
        var prepared = LiteralMessageTemplate.prepare(
                "[Text=\"%\u017fITE%\",Hover=\"{s\u0131te}\",Command=\"/inspect %Site%\"]",
                Map.of("Site", literal, "Other", "must not be expanded"), Map.of("Site", raw));
        TextComponent rendered = render(prepared);
        assertEquals(literal.replace("§", "(section)"), rendered.toPlainText());
        assertEquals(List.of("/inspect " + raw), actions(rendered));
        assertEquals(List.of(literal.replace("§", "(section)")), hovers(rendered));
        var overlap = LiteralMessageTemplate.prepare("%missing%Site%/%Site%Next%/{{SITE}}",
                Map.of("Site", "%Next%", "Next", "next"), Map.of());
        assertEquals("%missing%Next%/%Next%Next%/{%Next%}", overlap.finishText(overlap.template()));
    }

    @Test
    void persistedRewardMetadataAndProductionConsumerPreserveOldValidTokenData() {
        HashMap<String, String> stored = new HashMap<>();
        RewardDisplayPlaceholders.putLiteral(stored, "Site", "visible/" + OLD_TOKEN, "fallback");
        stored.put("Site", "raw/" + token(3));
        HashMap<String, String> restored = ArrayUtils.fromString(ArrayUtils.makeString(stored));
        try (var placeholders = mockStatic(PlaceholderUtils.class, CALLS_REAL_METHODS)) {
            placeholders.when(() -> PlaceholderUtils.replaceJavascript(anyString()))
                    .thenAnswer(call -> call.getArgument(0));
            TextComponent[] rendered = RewardDisplayPlaceholders.renderLiteralComponents(null,
                    "[Text=\"%Site%\",Command=\"/inspect %Site%\",Hover=\"{Site}\"]", restored);
            assertEquals("visible/" + OLD_TOKEN, BaseComponent.toPlainText(rendered));
            assertEquals(List.of("/inspect raw/" + token(3)), actions(rendered[0]));
            assertEquals(List.of("visible/" + OLD_TOKEN), hovers(rendered[0]));
        }
    }

    @Test
    void productionScriptLaneNeverEvaluatesRestoredDisplayDataOrProtectedRawScriptActions() {
        String literal = "data/[Javascript=danger()]%Other%/" + OLD_TOKEN;
        HashMap<String, String> stored = new HashMap<>();
        RewardDisplayPlaceholders.putLiteral(stored, "Site", literal, "fallback");
        stored.put("Site", "raw-site");
        AtomicInteger executions = new AtomicInteger();
        try (var placeholders = mockStatic(PlaceholderUtils.class, CALLS_REAL_METHODS)) {
            placeholders.when(() -> PlaceholderUtils.replaceJavascript(anyString())).thenAnswer(call ->
                    JavascriptTextTemplate.parse(call.getArgument(0)).evaluate(java.util.function.Function.identity(),
                            expression -> { executions.incrementAndGet(); return "executed"; }));
            TextComponent[] rendered = RewardDisplayPlaceholders.renderLiteralComponents(null,
                    "[Text=\"%Site%\",Command=\"[Javascript=%Site%]\"]|[Javascript='%Site%']", stored);
            assertEquals(literal + "|[Javascript='" + literal + "']", BaseComponent.toPlainText(rendered));
            assertEquals(List.of("[Javascript=raw-site]"), actions(rendered[0]));
            assertEquals(0, executions.get());
        }
    }

    @Test
    void formattedActionsIndexEachOutputAndPreserveIndependentRawResults() throws Exception {
        var prepared = LiteralMessageTemplate.prepare(
                "[Text=\"%Site%\",Command=\"one\"][Text=\"open\",Url=\"two\"]", Map.of("Site", "display"), Map.of());
        AtomicInteger calls = new AtomicInteger();
        var formatted = prepared.protectFormattedActions(value -> {
            calls.incrementAndGet();
            return value + "/" + (value.equals("one") ? OLD_TOKEN : token(3)) + "/§/\"";
        });
        assertEquals(2, calls.get());
        assertTrue(forbidden(formatted).containsAll(Set.of(OLD_TOKEN, token(3))));
        assertFalse(forbidden(prepared).contains(OLD_TOKEN));
        assertEquals(List.of("one/" + OLD_TOKEN + "/§/\"", "two/" + token(3) + "/§/\""), actions(render(formatted)));
        assertEquals("displayopen", render(formatted).toPlainText());
    }

    @Test
    void cumulativeFormattedActionBudgetIncludesEveryAttribute() {
        var prepared = LiteralMessageTemplate.prepare("[Text=\"\",Command=\"one\"][Text=\"\",Url=\"two\"]", Map.of(), Map.of());
        var atLimit = prepared.protectFormattedActions(value -> "x".repeat(131072));
        assertEquals(List.of("x".repeat(131072), "x".repeat(131072)), actions(render(atLimit)));
        assertThrows(IllegalArgumentException.class,
                () -> prepared.protectFormattedActions(value -> "x".repeat(131073)));
    }

    @Test
    void retainedFormattedActionBudgetSurvivesRepeatedFormattingTemplatesAndLineSplits() {
        var prepared = LiteralMessageTemplate.prepare("[Text=\"\",Command=\"one\"]", Map.of(), Map.of());
        var first = prepared.protectFormattedActions(value -> "x".repeat(131072));
        var second = first.protectFormattedActions(value -> value);
        assertEquals(List.of("x".repeat(131072)), actions(render(second)));
        assertThrows(IllegalArgumentException.class, () -> second.protectFormattedActions(value -> value));
        assertThrows(IllegalArgumentException.class,
                () -> second.withTemplate(second.template()).protectFormattedActions(value -> value));
        assertThrows(IllegalArgumentException.class,
                () -> second.splitLines().get(0).protectFormattedActions(value -> value));
        assertEquals(List.of("x".repeat(131072)), actions(render(first)));
    }

    @Test
    void scriptCorpusExpansionIsBoundedBeforeRetainingCollisionMetadata() {
        var atLimit = LiteralMessageTemplate.prepare("[Javascript=" + "%Site%".repeat(31) + "]",
                Map.of("Site", "x".repeat(8192)), Map.of("Site", "raw"));
        assertEquals("[Javascript=" + "x".repeat(8192 * 31) + "]", atLimit.finishText(atLimit.template()));
        assertThrows(IllegalArgumentException.class, () -> LiteralMessageTemplate.prepare(
                "[Javascript=" + "%Site%".repeat(32) + "]", Map.of("Site", "x".repeat(8192)), Map.of("Site", "raw")));
    }

    @Test
    void manyLinesShareImmutable4096BindingMapsAndCorpusWithoutCopyingThem() throws Exception {
        Map<String, String> values = new LinkedHashMap<>();
        StringBuilder source = new StringBuilder();
        for (int position = 0; position < 4096; position++) {
            values.put("K" + position, "");
            source.append("%K").append(position).append('%');
        }
        source.append("%NewLine%line".repeat(2048));
        var prepared = LiteralMessageTemplate.prepare(source.toString(), values, Map.of());
        List<LiteralMessageTemplate.Prepared> lines = prepared.splitLines();
        assertEquals(2049, lines.size());
        for (var line : lines) {
            assertSame(field(prepared, "tokens"), field(line, "tokens"));
            assertSame(field(prepared, "actionTokens"), field(line, "actionTokens"));
            assertSame(forbidden(prepared), forbidden(line));
            assertSame(line, line.protectFormattedActions(value -> {
                throw new AssertionError("no authored actions exist");
            }));
        }
        var changed = lines.get(1).withTemplate("changed");
        assertSame(field(prepared, "tokens"), field(changed, "tokens"));
        assertSame(forbidden(prepared), forbidden(changed));
    }

    @Test
    void recipientActionIndexesAndSectionGuardsCannotMutateTheirSharedPrepared() throws Exception {
        var prepared = LiteralMessageTemplate.prepare("[Text=\"%Site%\",Command=\"source\"]",
                Map.of("Site", "display"), Map.of("Site", "raw"));
        Set<String> initial = forbidden(prepared);
        CompletableFuture<LiteralMessageTemplate.Prepared> first = CompletableFuture.supplyAsync(
                () -> prepared.protectFormattedActions(value -> "first/" + OLD_TOKEN));
        CompletableFuture<LiteralMessageTemplate.Prepared> second = CompletableFuture.supplyAsync(
                () -> prepared.protectFormattedActions(value -> "second/" + token(3)));
        var a = first.get(5, TimeUnit.SECONDS);
        var b = second.get(5, TimeUnit.SECONDS);
        assertEquals(List.of("first/" + OLD_TOKEN), actions(render(a)));
        assertEquals(List.of("second/" + token(3)), actions(render(b)));
        assertTrue(forbidden(a).contains(OLD_TOKEN));
        assertFalse(forbidden(a).contains(token(3)));
        assertTrue(forbidden(b).contains(token(3)));
        assertFalse(forbidden(b).contains(OLD_TOKEN));
        assertSame(initial, forbidden(prepared));
        assertFalse(initial.contains(OLD_TOKEN));
        String binding = ((Map<?, ?>) field(prepared, "tokens")).keySet().iterator().next().toString();
        var guarded = prepared.withTemplate("§" + binding);
        assertEquals("(section)display", guarded.finishText(guarded.template()));
        assertEquals(1, ((Map<?, ?>) field(prepared, "tokens")).size());
        assertEquals(2, ((Map<?, ?>) field(guarded, "tokens")).size());
        assertEquals("display", prepared.finishText(binding));
    }

    @Test
    void sectionBoundaryRecognitionUsesKnownExactTokensAndOneLookupPerBoundary() throws Exception {
        Map<String, String> values = new LinkedHashMap<>();
        Map<String, String> raw = new LinkedHashMap<>();
        for (int position = 0; position < 4096; position++) {
            values.put(token(position + 100), position == 0 ? "§" : "x");
            raw.put(token(position + 100), position == 0 ? "§" : "x");
        }
        AtomicInteger enumerations = new AtomicInteger();
        AtomicInteger lookups = new AtomicInteger();
        Map<String, String> counted = new AbstractMap<>() {
            @Override public Set<Entry<String, String>> entrySet() {
                enumerations.incrementAndGet();
                return values.entrySet();
            }
            @Override public boolean isEmpty() { return false; }
            @Override public boolean containsKey(Object key) {
                lookups.incrementAndGet();
                return values.containsKey(key);
            }
        };
        String source = ("§" + token(4095 + 100)).repeat(1000);
        Object index = index(Set.copyOf(values.keySet()));
        Method protect = LiteralMessageTemplate.class.getDeclaredMethod("protectSectionTokenBoundaries",
                String.class, Map.class, Map.class, index.getClass());
        protect.setAccessible(true);
        Object result = invoke(protect, null, source, counted, raw, index);
        Method template = result.getClass().getDeclaredMethod("template");
        template.setAccessible(true);
        assertEquals((token(100) + token(4195)).repeat(1000), invoke(template, result));
        assertEquals(1, enumerations.get(), "guard discovery may enumerate once, boundaries must use exact lookup");
        assertEquals(1000, lookups.get());
        var prepared = LiteralMessageTemplate.prepare("%Site%", Map.of("Site", "display"), Map.of());
        String unknown = "§" + OLD_TOKEN + "/§" + PREFIX + "PROBE";
        assertEquals(unknown, prepared.withTemplate(unknown).template());
        assertEquals(unknown, prepared.finishText(unknown));
    }

    @Test
    void thousandsOfComponentsRestoreThroughTheProductionTreeWalk() throws Exception {
        Map<String, String> values = new LinkedHashMap<>();
        StringBuilder source = new StringBuilder();
        for (int position = 0; position < 4096; position++) {
            values.put("K" + position, "x");
            source.append("%K").append(position).append('%');
        }
        var prepared = LiteralMessageTemplate.prepare(source.toString(), values, Map.of());
        Map<?, ?> tokens = (Map<?, ?>) field(prepared, "tokens");
        String last = new ArrayList<>(tokens.keySet()).get(4095).toString();
        TextComponent root = new TextComponent("");
        for (int position = 0; position < 4095; position++) root.addExtra(new TextComponent(last));
        assertEquals("x".repeat(4095), prepared.finish(root).toPlainText());
    }

    private static String token(int value) {
        return PREFIX + Integer.toString(value, 36) + "Q4Z";
    }

    private static Object index(Set<String> corpus) throws Exception {
        Class<?> type = Class.forName(LiteralMessageTemplate.class.getName() + "$TokenIndex");
        Constructor<?> constructor = type.getDeclaredConstructor(Set.class);
        constructor.setAccessible(true);
        return constructor.newInstance(corpus);
    }

    private static void add(Object index, CharSequence corpus) throws Exception {
        Method add = index.getClass().getDeclaredMethod("addCorpus", CharSequence.class);
        add.setAccessible(true);
        invoke(add, index, corpus);
    }

    @SuppressWarnings("unchecked")
    private static Set<String> snapshot(Object index) throws Exception {
        Method snapshot = index.getClass().getDeclaredMethod("snapshot");
        snapshot.setAccessible(true);
        return (Set<String>) invoke(snapshot, index);
    }

    private static String allocate(Object index, Supplier<String> candidates) throws Exception {
        Method allocate = LiteralMessageTemplate.class.getDeclaredMethod("newToken", index.getClass(), Supplier.class);
        allocate.setAccessible(true);
        return (String) invoke(allocate, null, index, candidates);
    }

    private static String restore(CharSequence text, Map<String, String> values, boolean visible) throws Exception {
        Method restore = LiteralMessageTemplate.class.getDeclaredMethod("restoreTokens", CharSequence.class,
                Map.class, boolean.class, boolean.class);
        restore.setAccessible(true);
        return (String) invoke(restore, null, text, values, visible, false);
    }

    private static Object invoke(Method method, Object receiver, Object... arguments) throws Exception {
        try {
            return method.invoke(receiver, arguments);
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof Exception cause) throw cause;
            if (failure.getCause() instanceof Error cause) throw cause;
            throw failure;
        }
    }

    private static Object field(LiteralMessageTemplate.Prepared prepared, String name) throws Exception {
        Field field = prepared.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(prepared);
    }

    @SuppressWarnings("unchecked")
    private static Set<String> forbidden(LiteralMessageTemplate.Prepared prepared) throws Exception {
        return (Set<String>) field(prepared, "forbiddenTokens");
    }

    private static TextComponent render(LiteralMessageTemplate.Prepared prepared) {
        return prepared.finish(PlaceholderUtils.parseJson(prepared.template()));
    }

    private static List<String> actions(BaseComponent component) {
        List<String> values = new ArrayList<>();
        if (component.getClickEvent() != null) values.add(component.getClickEvent().getValue());
        if (component.getExtra() != null) for (BaseComponent child : component.getExtra()) values.addAll(actions(child));
        return values;
    }

    @SuppressWarnings("deprecation")
    private static List<String> hovers(BaseComponent component) {
        List<String> values = new ArrayList<>();
        if (component.getHoverEvent() != null && component.getHoverEvent().getAction() == HoverEvent.Action.SHOW_TEXT)
            for (var content : component.getHoverEvent().getContents()) {
                var text = assertInstanceOf(net.md_5.bungee.api.chat.hover.content.Text.class, content);
                Object value = text.getValue();
                values.add(value instanceof BaseComponent[] components ? BaseComponent.toPlainText(components)
                        : assertInstanceOf(String.class, value));
            }
        if (component.getExtra() != null) for (BaseComponent child : component.getExtra()) values.addAll(hovers(child));
        return values;
    }

    private static final class CountingText implements CharSequence {
        private final String text;
        private int reads;
        private CountingText(String text) { this.text = text; }
        @Override public int length() { return text.length(); }
        @Override public char charAt(int index) { reads++; return text.charAt(index); }
        @Override public CharSequence subSequence(int start, int end) { return text.subSequence(start, end); }
    }

    private static final class LookupOnlyMap extends AbstractMap<String, String> {
        private final Map<String, String> values;
        private int lookups;
        private LookupOnlyMap(Map<String, String> values) { this.values = values; }
        @Override public String get(Object key) { lookups++; return values.get(key); }
        @Override public Set<Entry<String, String>> entrySet() {
            throw new AssertionError("restoration must use token lookup, never enumerate bindings");
        }
    }
}
