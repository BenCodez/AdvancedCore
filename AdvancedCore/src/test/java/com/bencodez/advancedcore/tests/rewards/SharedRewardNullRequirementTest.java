package com.bencodez.advancedcore.tests.rewards;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.core.reward.*;

class SharedRewardNullRequirementTest {
    @Test void completedNullFinalRequirementIsNotEligible() {
        assertNullRequirement(false);
    }

    @Test void asynchronouslyCompletedNullFinalRequirementIsNotEligible() {
        assertNullRequirement(true);
    }

    private void assertNullRequirement(boolean asynchronous) {
        CompletableFuture<Boolean> requirement = new CompletableFuture<>();
        if (!asynchronous) requirement.complete(null);
        SharedRewardPlatform platform = new SharedRewardPlatform() {
            public boolean isOnline(UUID uuid) { return true; }
            public boolean isShuttingDown() { return false; }
            public double nextChanceRoll() { throw new AssertionError("ineligible request rerolled chance"); }
            public CompletionStage<SharedRewardResult> delay(Duration delay,
                    Supplier<CompletionStage<SharedRewardResult>> work) {
                throw new AssertionError("ineligible request was delayed");
            }
        };
        var plan = new SharedRewardPlan("null-requirement", 0.5, Duration.ofHours(1),
                List.of(context -> CompletableFuture.completedFuture(true), context -> requirement),
                List.of(new SharedRewardStep("never", false, (context, path) -> {
                    throw new AssertionError("ineligible reward executed");
                })));
        var result = new SharedRewardOrchestrator(platform).execute(plan,
                new SharedRewardContext(UUID.randomUUID(), "Ben", Map.of()), SharedRewardDurability.NONE)
                .toCompletableFuture();
        if (asynchronous) {
            assertFalse(result.isDone());
            requirement.complete(null);
        }
        assertEquals(SharedRewardResult.NOT_ELIGIBLE, result.join());
    }
}
