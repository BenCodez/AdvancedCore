package com.bencodez.advancedcore.tests.rewards;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.core.reward.SharedRewardContext;
import com.bencodez.advancedcore.core.reward.SharedRewardDurability;
import com.bencodez.advancedcore.core.reward.SharedRewardOrchestrator;
import com.bencodez.advancedcore.core.reward.SharedRewardPlan;
import com.bencodez.advancedcore.core.reward.SharedRewardPlatform;
import com.bencodez.advancedcore.core.reward.SharedRewardResult;
import com.bencodez.advancedcore.core.reward.SharedRewardStep;

class SharedRewardStackSafetyTest {
    @Test
    void thousandsOfSynchronousStepsCompleteWithoutRecursiveStackGrowth() {
        AtomicInteger executions = new AtomicInteger();
        ArrayList<SharedRewardStep> steps = new ArrayList<>();
        for (int i = 0; i < 5_000; i++) {
            steps.add(new SharedRewardStep("step-" + i, false, (context, path) -> {
                executions.incrementAndGet();
                return CompletableFuture.completedFuture(SharedRewardResult.COMPLETED);
            }));
        }
        SharedRewardPlatform platform = new SharedRewardPlatform() {
            public boolean isOnline(UUID userId) { return true; }
            public double nextChanceRoll() { return 0.0; }
            public CompletionStage<SharedRewardResult> delay(Duration delay,
                    java.util.function.Supplier<CompletionStage<SharedRewardResult>> operation) {
                return operation.get();
            }
            public boolean isShuttingDown() { return false; }
        };
        SharedRewardResult result = new SharedRewardOrchestrator(platform).execute(
                SharedRewardPlan.immediate("large", steps),
                new SharedRewardContext(UUID.randomUUID(), "Ben", new HashMap<>()),
                SharedRewardDurability.NONE).toCompletableFuture().join();
        assertEquals(SharedRewardResult.COMPLETED, result);
        assertEquals(5_000, executions.get());
    }
}
