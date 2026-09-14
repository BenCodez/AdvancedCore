package com.bencodez.advancedcore.tests.rewards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.core.reward.SharedRewardContext;
import com.bencodez.advancedcore.core.reward.SharedRewardDurability;
import com.bencodez.advancedcore.core.reward.SharedRewardOrchestrator;
import com.bencodez.advancedcore.core.reward.SharedRewardPlan;
import com.bencodez.advancedcore.core.reward.SharedRewardPlatform;
import com.bencodez.advancedcore.core.reward.SharedRewardProgress;
import com.bencodez.advancedcore.core.reward.SharedRewardResult;
import com.bencodez.advancedcore.core.reward.SharedRewardStep;

class SharedRewardOrchestratorTest {
    @Test
    void runsRequirementsDelayActionsAndDurableCheckpointsInOrder() {
        ArrayList<String> events = new ArrayList<>();
        FakePlatform platform = new FakePlatform(events);
        FakeDurability durability = new FakeDurability(events, true);
        SharedRewardOrchestrator orchestrator = new SharedRewardOrchestrator(platform);
        SharedRewardContext context = context();
        SharedRewardPlan plan = new SharedRewardPlan("root", 1.0, Duration.ofSeconds(3),
                List.of(ctx -> {
                    events.add("requirement");
                    return CompletableFuture.completedFuture(Boolean.TRUE);
                }),
                List.of(step("command", true, events), step("message", true, events))).withDefinitionFingerprint("fixture-v1");

        SharedRewardResult result = orchestrator.execute(plan, context, durability).toCompletableFuture().join();

        assertEquals(SharedRewardResult.COMPLETED, result);
        assertEquals(List.of("requirement", "delay:3", "command", "checkpoint:root:1", "message",
                "checkpoint:root:2"), events);
    }

    @Test
    void defersBeforePlayerBoundWorkWhenOffline() {
        ArrayList<String> events = new ArrayList<>();
        FakePlatform platform = new FakePlatform(events);
        platform.online = false;
        FakeDurability durability = new FakeDurability(events, true);
        SharedRewardOrchestrator orchestrator = new SharedRewardOrchestrator(platform);
        SharedRewardPlan plan = SharedRewardPlan.immediate("offline", List.of(step("player-command", true, events))).withDefinitionFingerprint("fixture-v1");

        SharedRewardResult result = orchestrator.execute(plan, context(), durability).toCompletableFuture().join();

        assertEquals(SharedRewardResult.DEFERRED, result);
        assertEquals(List.of("defer:offline:0"), events);
    }

    @Test
    void disconnectAfterCompletedStepDefersOnlyRemainingSuffix() {
        ArrayList<String> events = new ArrayList<>();
        FakePlatform platform = new FakePlatform(events);
        FakeDurability durability = new FakeDurability(events, true);
        SharedRewardOrchestrator orchestrator = new SharedRewardOrchestrator(platform);
        SharedRewardStep first = new SharedRewardStep("first", true, (ctx, path) -> {
            events.add("first");
            platform.online = false;
            return CompletableFuture.completedFuture(SharedRewardResult.COMPLETED);
        });
        SharedRewardPlan plan = SharedRewardPlan.immediate("disconnect",
                List.of(first, step("second", true, events))).withDefinitionFingerprint("fixture-v1");

        SharedRewardResult result = orchestrator.execute(plan, context(), durability).toCompletableFuture().join();

        assertEquals(SharedRewardResult.DEFERRED, result);
        assertEquals(List.of("first", "checkpoint:disconnect:1", "defer:disconnect:1"), events);
    }

    @Test
    void partialFailureNeverCheckpointsOrRunsLaterWork() {
        ArrayList<String> events = new ArrayList<>();
        FakePlatform platform = new FakePlatform(events);
        FakeDurability durability = new FakeDurability(events, true);
        SharedRewardOrchestrator orchestrator = new SharedRewardOrchestrator(platform);
        SharedRewardStep failure = new SharedRewardStep("failure", false, (ctx, path) -> {
            events.add("failure");
            return CompletableFuture.failedFuture(new IllegalStateException("boom"));
        });
        SharedRewardPlan plan = SharedRewardPlan.immediate("partial",
                List.of(step("first", false, events), failure, step("third", false, events))).withDefinitionFingerprint("fixture-v1");

        assertThrows(CompletionException.class,
                () -> orchestrator.execute(plan, context(), durability).toCompletableFuture().join());
        assertEquals(List.of("first", "checkpoint:partial:1", "failure"), events);
    }

    @Test
    void delayedWorkFailsClosedWhenShutdownStartsBeforeCallback() {
        ArrayList<String> events = new ArrayList<>();
        FakePlatform platform = new FakePlatform(events);
        platform.shutdownBeforeDelayedCallback = true;
        FakeDurability durability = new FakeDurability(events, true);
        SharedRewardOrchestrator orchestrator = new SharedRewardOrchestrator(platform);
        SharedRewardPlan plan = new SharedRewardPlan("shutdown", 1.0, Duration.ofSeconds(1), List.of(),
                List.of(step("never", false, events))).withDefinitionFingerprint("fixture-v1");

        assertThrows(CompletionException.class,
                () -> orchestrator.execute(plan, context(), durability).toCompletableFuture().join());
        assertEquals(List.of("delay:1"), events);
    }

    @Test
    void nestedCompletionIsAwaitedBeforeParentCheckpoint() {
        ArrayList<String> events = new ArrayList<>();
        FakePlatform platform = new FakePlatform(events);
        FakeDurability durability = new FakeDurability(events, true);
        SharedRewardOrchestrator orchestrator = new SharedRewardOrchestrator(platform);
        SharedRewardPlan child = SharedRewardPlan.immediate("child", List.of(step("child-command", false, events))).withDefinitionFingerprint("fixture-v1");
        SharedRewardStep nested = new SharedRewardStep("nested", false,
                (ctx, path) -> orchestrator.executeNested(child, ctx, durability, path));
        SharedRewardPlan parent = SharedRewardPlan.immediate("parent", List.of(nested, step("after", false, events))).withDefinitionFingerprint("fixture-v1");

        orchestrator.execute(parent, context(), durability).toCompletableFuture().join();

        assertEquals(List.of("child-command", "checkpoint:parent/nested:0/child:1", "checkpoint:parent:1", "after",
                "checkpoint:parent:2"), events);
    }

    @Test
    void durableResumeSkipsCompletedPrefixAndDoesNotRerollChanceOrDelay() {
        ArrayList<String> events = new ArrayList<>();
        FakePlatform platform = new FakePlatform(events);
        platform.chanceRoll = 0.99;
        FakeDurability durability = new FakeDurability(events, true);
        SharedRewardOrchestrator orchestrator = new SharedRewardOrchestrator(platform);
        SharedRewardPlan plan = new SharedRewardPlan("resume", 0.1, Duration.ofSeconds(4),
                List.of(ctx -> {
                    events.add("requirement");
                    return CompletableFuture.completedFuture(Boolean.FALSE);
                }), List.of(step("already-done", false, events), step("remaining", false, events))).withDefinitionFingerprint("fixture-v1");

        durability.completed.put("resume", 1);
        durability.progress.put("resume", new SharedRewardProgress(plan.fingerprint(), true, 1, Map.of()));
        SharedRewardResult result = orchestrator.execute(plan, context(), durability).toCompletableFuture().join();

        assertEquals(SharedRewardResult.COMPLETED, result);
        assertEquals(List.of("remaining", "checkpoint:resume:2"), events);
    }

    private static SharedRewardContext context() {
        return new SharedRewardContext(UUID.randomUUID(), "Ben", Map.of("player", "Ben"));
    }

    private static SharedRewardStep step(String id, boolean requiresOnline, List<String> events) {
        return new SharedRewardStep(id, requiresOnline, (ctx, path) -> {
            events.add(id);
            return CompletableFuture.completedFuture(SharedRewardResult.COMPLETED);
        });
    }

    private static final class FakePlatform implements SharedRewardPlatform {
        private final List<String> events;
        private boolean online = true;
        private boolean shuttingDown;
        private boolean shutdownBeforeDelayedCallback;
        private double chanceRoll;
        private Instant time = Instant.EPOCH;

        @Override public Instant now() { return time; }

        private FakePlatform(List<String> events) {
            this.events = events;
        }

        @Override
        public boolean isOnline(UUID userId) {
            return online;
        }

        @Override
        public double nextChanceRoll() {
            return chanceRoll;
        }

        @Override
        public CompletionStage<SharedRewardResult> delay(Duration delay,
                Supplier<CompletionStage<SharedRewardResult>> operation) {
            events.add("delay:" + delay.toSeconds());
            time = time.plus(delay);
            if (shutdownBeforeDelayedCallback) shuttingDown = true;
            return operation.get();
        }

        @Override
        public boolean isShuttingDown() {
            return shuttingDown;
        }
    }

    private static final class FakeDurability implements SharedRewardDurability {
        private final List<String> events;
        private final boolean durable;
        private final HashMap<String, Integer> completed = new HashMap<>();
        private final HashMap<String, SharedRewardProgress> progress = new HashMap<>();

        private FakeDurability(List<String> events, boolean durable) {
            this.events = events;
            this.durable = durable;
        }

        @Override
        public SharedRewardProgress loadProgress(String path) { return progress.get(path); }

        @Override
        public CompletionStage<SharedRewardProgress> begin(String path, SharedRewardProgress state) {
            progress.putIfAbsent(path, state);
            return CompletableFuture.completedFuture(progress.get(path));
        }

        @Override
        public int completedSteps(String executionPath) {
            return completed.getOrDefault(executionPath, 0);
        }

        @Override
        public CompletionStage<Void> checkpoint(String executionPath, int completedSteps,
                SharedRewardContext context) {
            events.add("checkpoint:" + executionPath + ":" + completedSteps);
            completed.put(executionPath, completedSteps);
            progress.put(executionPath, progress.get(executionPath).advance(completedSteps, context));
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> defer(String executionPath, int nextStep, SharedRewardContext context) {
            events.add("defer:" + executionPath + ":" + nextStep);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public boolean durable() {
            return durable;
        }
    }
}
