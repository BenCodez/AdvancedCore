package com.bencodez.advancedcore.core.reward;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Adapter over the existing replay owner, scoped to ONE logical reward occurrence.
 * That owner serializes execution/retries of each path; the orchestrator allocates
 * no second queue, replay store or lock registry. Snapshot reads must not block the
 * platform thread. Write stages complete only after persistence, never submission.
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

    /** Existing API retained; an unversioned nonzero cursor is not safe to resume. */
    int completedSteps(String executionPath);

    /** Already-loaded snapshot, or null only when this occurrence/path has never begun. */
    default SharedRewardProgress loadProgress(String executionPath) {
        if (durable()) throw new UnsupportedOperationException("Durable replay must load versioned decision/progress state");
        return null;
    }

    /**
     * Atomically persist the initial decision, fingerprint and placeholders at cursor
     * zero, or return the already-persisted state. Never overwrite an earlier decision.
     * A lost acknowledgement must still be recoverable by loadProgress on retry.
     */
    default CompletionStage<SharedRewardProgress> begin(String executionPath, SharedRewardProgress proposed) {
        if (durable()) return CompletableFuture.failedFuture(
                new UnsupportedOperationException("Durable replay must persist the initial execution decision"));
        return CompletableFuture.completedFuture(proposed);
    }

    /** Completes only after progress and placeholder state are durable. */
    CompletionStage<Void> checkpoint(String executionPath, int completedSteps, SharedRewardContext context);

    /** Bound form used by the shared orchestrator; adapters persist the same binding with the cursor. */
    default CompletionStage<Void> checkpoint(String executionPath, String fingerprint, int completedSteps,
            SharedRewardContext context) {
        requireFingerprint(executionPath, fingerprint);
        return checkpoint(executionPath, completedSteps, context);
    }

    /** Completes only after the still-pending reward occurrence is durable. */
    CompletionStage<Void> defer(String executionPath, int nextStep, SharedRewardContext context);

    default CompletionStage<Void> defer(String executionPath, String fingerprint, int nextStep,
            SharedRewardContext context) {
        requireFingerprint(executionPath, fingerprint);
        return defer(executionPath, nextStep, context);
    }

    private void requireFingerprint(String executionPath, String fingerprint) {
        if (!durable()) return;
        SharedRewardProgress state = loadProgress(executionPath);
        if (state == null || !state.planFingerprint().equals(fingerprint)) {
            throw new IllegalStateException("Reward checkpoint belongs to a different or unbound plan: " + executionPath);
        }
    }

    boolean durable();
}
