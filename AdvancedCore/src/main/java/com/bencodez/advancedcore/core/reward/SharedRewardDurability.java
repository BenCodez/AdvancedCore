package com.bencodez.advancedcore.core.reward;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Adapter over the existing durable replay owner. The orchestrator stores no
 * replay queue or cursor itself; #317's replay state can implement this boundary.
 */
public interface SharedRewardDurability {
    SharedRewardDurability NONE = new SharedRewardDurability() {
        @Override public int completedSteps(String executionPath) { return 0; }
        @Override public CompletionStage<Void> checkpoint(String executionPath, int completedSteps,
                SharedRewardContext context) { return CompletableFuture.completedFuture(null); }
        @Override public CompletionStage<Void> defer(String executionPath, int nextStep,
                SharedRewardContext context) { return CompletableFuture.failedFuture(
                        new IllegalStateException("Offline reward deferral is unavailable")); }
        @Override public boolean durable() { return false; }
    };

    int completedSteps(String executionPath);

    /** Completes only after progress and placeholder state are durable. */
    CompletionStage<Void> checkpoint(String executionPath, int completedSteps, SharedRewardContext context);

    /** Completes only after the still-pending reward occurrence is durable. */
    CompletionStage<Void> defer(String executionPath, int nextStep, SharedRewardContext context);

    boolean durable();
}
