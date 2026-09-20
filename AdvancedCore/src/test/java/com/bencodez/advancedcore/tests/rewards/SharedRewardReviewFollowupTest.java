package com.bencodez.advancedcore.tests.rewards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.core.reward.*;

class SharedRewardReviewFollowupTest {
    @Test
    void retryableRequirementDefersWithoutPersistingARejectDecisionAndIsRetested() {
        AtomicBoolean ready = new AtomicBoolean(false);
        AtomicInteger executions = new AtomicInteger();
        MemoryDurability durability = new MemoryDurability();
        SharedRewardPlan plan = new SharedRewardPlan("retry", 1.0, Duration.ZERO,
                List.of(SharedRewardRequirement.retryable(ctx -> CompletableFuture.completedFuture(ready.get()))),
                List.of(new SharedRewardStep("work", false, (ctx, path) -> {
                    executions.incrementAndGet();
                    return CompletableFuture.completedFuture(SharedRewardResult.COMPLETED);
                }))).withDefinitionFingerprint("retry-v1");
        SharedRewardOrchestrator orchestrator = new SharedRewardOrchestrator(platform());
        SharedRewardContext context = context();

        assertEquals(SharedRewardResult.DEFERRED, orchestrator.execute(plan, context, durability).toCompletableFuture().join());
        assertNull(durability.progress);
        assertEquals(1, durability.deferrals);
        assertEquals(0, executions.get());
        ready.set(true);
        assertEquals(SharedRewardResult.COMPLETED, orchestrator.execute(plan, context, durability).toCompletableFuture().join());
        assertEquals(1, executions.get());
    }

    @Test
    void reservedCharactersInPlanAndStepIdsCannotProduceTheSameNestedPath() {
        SharedRewardOrchestrator orchestrator = new SharedRewardOrchestrator(platform());
        SharedRewardContext context = context();
        ArrayList<String> childPaths = new ArrayList<>();
        SharedRewardPlan childA = SharedRewardPlan.immediate("0/y:1/z", List.of(
                new SharedRewardStep("leaf", false, (ctx, path) -> {
                    childPaths.add(path);
                    return CompletableFuture.completedFuture(SharedRewardResult.COMPLETED);
                })));
        SharedRewardPlan childB = SharedRewardPlan.immediate("z", List.of(
                new SharedRewardStep("leaf", false, (ctx, path) -> {
                    childPaths.add(path);
                    return CompletableFuture.completedFuture(SharedRewardResult.COMPLETED);
                })));
        SharedRewardPlan parent = SharedRewardPlan.immediate("P", List.of(
                new SharedRewardStep("x", false,
                        (ctx, path) -> orchestrator.executeNested(childA, ctx, SharedRewardDurability.NONE, path)),
                new SharedRewardStep("x:0/0/y", false,
                        (ctx, path) -> orchestrator.executeNested(childB, ctx, SharedRewardDurability.NONE, path))));

        assertEquals(SharedRewardResult.COMPLETED,
                orchestrator.execute(parent, context, SharedRewardDurability.NONE).toCompletableFuture().join());
        assertEquals(2, childPaths.size());
        assertNotEquals(childPaths.get(0), childPaths.get(1));
    }

    private static SharedRewardContext context() {
        return new SharedRewardContext(UUID.randomUUID(), "Ben", new HashMap<>());
    }

    private static SharedRewardPlatform platform() {
        return new SharedRewardPlatform() {
            public boolean isOnline(UUID userId) { return true; }
            public double nextChanceRoll() { return 0.0; }
            public CompletionStage<SharedRewardResult> delay(Duration delay,
                    java.util.function.Supplier<CompletionStage<SharedRewardResult>> operation) { return operation.get(); }
            public boolean isShuttingDown() { return false; }
        };
    }

    private static final class MemoryDurability implements SharedRewardDurability {
        SharedRewardProgress progress;
        int deferrals;
        public int completedSteps(String path) { return progress == null ? 0 : progress.completedSteps(); }
        public SharedRewardProgress loadProgress(String path) { return progress; }
        public CompletionStage<SharedRewardProgress> begin(String path, SharedRewardProgress proposed) {
            if (progress == null) progress = proposed;
            return CompletableFuture.completedFuture(progress);
        }
        public CompletionStage<Void> checkpoint(String path, int completed, SharedRewardContext context) {
            progress = progress.advance(completed, context);
            return CompletableFuture.completedFuture(null);
        }
        public CompletionStage<Void> defer(String path, int next, SharedRewardContext context) {
            deferrals++;
            return CompletableFuture.completedFuture(null);
        }
        public boolean durable() { return true; }
    }
}
