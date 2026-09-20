package com.bencodez.advancedcore.core.reward;

import java.util.concurrent.CompletionStage;

/**
 * Durable action admission for one logical reward occurrence. The injected owner
 * serializes the occurrence across processes. Its database is authoritative.
 */
public interface SharedRewardKeyedDurability extends SharedRewardDurability {
    /**
     * Atomically persist a pending action before any native side effect. Return
     * STARTED to exactly one caller. If a prior pending action or a stale cursor
     * exists, return INDETERMINATE without granting execution. An acknowledgement
     * lost after committing STARTED must also return INDETERMINATE on retry.
     * Validate the fingerprint and step index against the stored occurrence.
     */
    CompletionStage<SharedRewardActionClaim> claimAction(String executionPath, String fingerprint,
            int stepIndex, SharedRewardContext context);

    /**
     * {@inheritDoc} For keyed execution this must atomically advance the cursor
     * and clear the matching pending action. A failure after the native effect
     * leaves the action pending for explicit reconciliation, never auto-replay.
     */
    @Override
    CompletionStage<Void> checkpoint(String executionPath, String fingerprint, int completedSteps,
            SharedRewardContext context);
}
