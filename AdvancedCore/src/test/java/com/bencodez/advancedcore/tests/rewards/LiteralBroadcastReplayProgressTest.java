package com.bencodez.advancedcore.tests.rewards;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.rewards.Reward;

/** Regression coverage for the bounded per-target literal broadcast replay lane. */
class LiteralBroadcastReplayProgressTest {
    private static final String PREFIX = "P|00000000-0000-0000-0000-000000000001|";

    private static Reward.ReplayState state(AdvancedCorePlugin plugin, HashMap<String, String> values,
            List<Reward.ReplayCheckpoint> saved, boolean failPersistence) throws Exception {
        var type = Class.forName("com.bencodez.advancedcore.api.rewards.Reward$ReplayState");
        var constructor = type.getDeclaredConstructor(Map.class, Map.class, boolean.class);
        constructor.setAccessible(true);
        Reward.ReplayState state = (Reward.ReplayState) constructor.newInstance(new HashMap<>(), new HashMap<>(), false);
        var setter = type.getDeclaredMethod("setCheckpointConsumer", java.util.function.Consumer.class);
        setter.setAccessible(true);
        setter.invoke(state, (java.util.function.Consumer<Reward.ReplayCheckpoint>) checkpoint -> {
            if (failPersistence && !saved.isEmpty()) throw new IllegalStateException("synthetic checkpoint failure");
            saved.add(checkpoint);
            values.clear();
            values.putAll(checkpoint.getPlaceholders());
        });
        return state;
    }

    private AdvancedCorePlugin plugin() {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        ScheduledExecutorService timer = mock(ScheduledExecutorService.class);
        when(plugin.getTimer()).thenReturn(timer);
        doAnswer(invocation -> { invocation.<Runnable>getArgument(0).run(); return null; })
                .when(timer).execute(any(Runnable.class));
        return plugin;
    }

    @Test
    void failedLaterTargetResumesOnlyTheUncompletedSuffixAfterRestart() throws Exception {
        AdvancedCorePlugin plugin = plugin();
        HashMap<String, String> values = new HashMap<>();
        List<Reward.ReplayCheckpoint> saved = new ArrayList<>();
        Reward.ReplayState state = state(plugin, values, saved, false);
        List<String> first = new ArrayList<>();
        var attempt = Reward.replayNestedRewardSequence(plugin, values, "literal-broadcast:test",
                List.of(PREFIX + "0", PREFIX + "1", PREFIX + "2"), state, "outer/0",
                (entry, index) -> {
                    first.add(entry);
                    return index == 1 ? CompletableFuture.failedFuture(new IllegalStateException("transport"))
                            : CompletableFuture.completedFuture(null);
                }, entry -> entry.startsWith(PREFIX), 8192, 96, 1048576L);
        assertThrows(Exception.class, attempt.toCompletableFuture()::join);
        assertEquals(List.of(PREFIX + "0", PREFIX + "1"), first);
        assertFalse(saved.isEmpty());

        values = com.bencodez.simpleapi.array.ArrayUtils.fromString(
                com.bencodez.simpleapi.array.ArrayUtils.makeString(values));
        Reward.ReplayState restored = state(plugin, values, new ArrayList<>(), false);
        List<String> retry = new ArrayList<>();
        Reward.replayNestedRewardSequence(plugin, values, "literal-broadcast:test", List.of(), restored, "outer/0",
                (entry, index) -> { retry.add(entry); return CompletableFuture.completedFuture(null); },
                entry -> entry.startsWith(PREFIX), 8192, 96, 1048576L).toCompletableFuture().join();
        assertEquals(List.of(PREFIX + "1", PREFIX + "2"), retry);
    }

    @Test
    void checkpointFailurePreventsTheNextTargetFromBeingDispatched() throws Exception {
        AdvancedCorePlugin plugin = plugin();
        HashMap<String, String> values = new HashMap<>();
        Reward.ReplayState state = state(plugin, values, new ArrayList<>(), true);
        List<String> dispatched = new ArrayList<>();
        var attempt = Reward.replayNestedRewardSequence(plugin, values, "literal-broadcast:test",
                List.of(PREFIX + "0", PREFIX + "1"), state, "outer/0",
                (entry, index) -> { dispatched.add(entry); return CompletableFuture.completedFuture(null); },
                entry -> entry.startsWith(PREFIX), 8192, 96, 1048576L);
        assertThrows(Exception.class, attempt.toCompletableFuture()::join);
        assertEquals(List.of(PREFIX + "0"), dispatched);
    }

    @Test
    void identicalBroadcastActionsUseIndependentOccurrences() throws Exception {
        AdvancedCorePlugin plugin = plugin();
        HashMap<String, String> firstValues = new HashMap<>();
        HashMap<String, String> secondValues = new HashMap<>();
        Reward.ReplayState first = state(plugin, firstValues, new ArrayList<>(), false);
        Reward.ReplayState second = state(plugin, secondValues, new ArrayList<>(), false);
        AtomicInteger dispatches = new AtomicInteger();
        Reward.replayNestedRewardSequence(plugin, firstValues, "literal-broadcast:same", List.of(PREFIX + "0"),
                first, "outer/0", (entry, index) -> { dispatches.incrementAndGet(); return CompletableFuture.completedFuture(null); },
                entry -> entry.startsWith(PREFIX), 8192, 96, 1048576L).toCompletableFuture().join();
        Reward.replayNestedRewardSequence(plugin, secondValues, "literal-broadcast:same", List.of(PREFIX + "0"),
                second, "outer/1", (entry, index) -> { dispatches.incrementAndGet(); return CompletableFuture.completedFuture(null); },
                entry -> entry.startsWith(PREFIX), 8192, 96, 1048576L).toCompletableFuture().join();
        assertEquals(2, dispatches.get());
    }

    @Test
    void aggregateReplayMetadataCapacityRejectsBeforeSavingOrDispatchingAnotherPlan() throws Exception {
        var plugin = plugin();
        var values = new HashMap<String, String>();
        String existing = Reward.legacyActionReplayKey("existing");
        values.put(existing, "x".repeat(1048576 - existing.length() - 16));
        var before = new HashMap<>(values);
        var saved = new ArrayList<Reward.ReplayCheckpoint>();
        var state = state(plugin, values, saved, false);
        var dispatches = new AtomicInteger();
        var attempt = Reward.replayNestedRewardSequence(plugin, values, "literal-broadcast:test",
                List.of(PREFIX + "0"), state, "outer/0", (entry, index) -> {
                    dispatches.incrementAndGet(); return CompletableFuture.completedFuture(null);
                }, entry -> entry.startsWith(PREFIX), 8192, 96, 1048576L);
        assertThrows(java.util.concurrent.CompletionException.class, attempt.toCompletableFuture()::join);
        assertEquals(0, dispatches.get());
        assertTrue(saved.isEmpty());
        assertEquals(before, values);
    }

    @Test
    void aggregateAdmissionIncludesDisjointStateOnlyAndCallerMetadata() throws Exception {
        var plugin = plugin();
        var values = new HashMap<String, String>();
        values.put(Reward.legacyActionReplayKey("caller"), "x".repeat(600000));
        var saved = new ArrayList<Reward.ReplayCheckpoint>();
        var state = state(plugin, values, saved, false);
        state.recordReplayMetadata(Reward.legacyActionReplayKey("state"), "x".repeat(600000));
        var dispatches = new AtomicInteger();
        var attempt = Reward.replayNestedRewardSequence(plugin, values, "literal-broadcast:test",
                List.of(PREFIX + "0"), state, "outer/0", (entry, index) -> {
                    dispatches.incrementAndGet(); return CompletableFuture.completedFuture(null);
                }, entry -> entry.startsWith(PREFIX), 8192, 96, 1048576L);
        assertThrows(java.util.concurrent.CompletionException.class, attempt.toCompletableFuture()::join);
        assertEquals(0, dispatches.get());
        assertTrue(saved.isEmpty());
        assertEquals(1, values.size());
    }

    @Test
    void ordinaryNestedRewardSequencesRetainTheirExistingMetadataAdmission() throws Exception {
        var plugin = plugin();
        var values = new HashMap<String, String>();
        values.put(Reward.legacyActionReplayKey("existing"), "x".repeat(1048577));
        var state = state(plugin, values, new ArrayList<>(), false);
        var dispatches = new AtomicInteger();
        Reward.replayNestedRewardSequence(plugin, values, "ordinary-reward", List.of("synthetic-child"),
                state, "ordinary/0", (entry, index) -> {
                    dispatches.incrementAndGet(); return CompletableFuture.completedFuture(null);
                }).toCompletableFuture().join();
        assertEquals(1, dispatches.get());
    }

    @Test
    void invalidOrOversizedDeliveryPlansFailBeforeAnyDispatch() throws Exception {
        AdvancedCorePlugin plugin = plugin();
        HashMap<String, String> values = new HashMap<>();
        Reward.ReplayState state = state(plugin, values, new ArrayList<>(), false);
        AtomicInteger dispatches = new AtomicInteger();
        var invalid = Reward.replayNestedRewardSequence(plugin, values, "literal-broadcast:test",
                List.of("P|not-a-uuid|0"), state, "outer/0",
                (entry, index) -> { dispatches.incrementAndGet(); return CompletableFuture.completedFuture(null); },
                entry -> entry.matches("P\\|[0-9a-f-]{36}\\|[0-9]+"), 8192, 96, 1048576L);
        assertThrows(Exception.class, invalid.toCompletableFuture()::join);
        assertEquals(0, dispatches.get());
        var oversized = Reward.replayNestedRewardSequence(plugin, values, "literal-broadcast:test",
                java.util.Collections.nCopies(8193, PREFIX + "0"), state, "outer/1",
                (entry, index) -> { dispatches.incrementAndGet(); return CompletableFuture.completedFuture(null); },
                entry -> true, 8192, 96, 1048576L);
        assertThrows(Exception.class, oversized.toCompletableFuture()::join);
        assertEquals(0, dispatches.get());
    }
}
