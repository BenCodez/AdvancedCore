package com.bencodez.advancedcore.core.reward;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Durable state for one path in one logical reward occurrence, including cursor zero. */
public record SharedRewardProgress(String planFingerprint, boolean eligible, int completedSteps,
        Map<String, String> placeholders, Instant notBefore) {
    public SharedRewardProgress {
        Objects.requireNonNull(planFingerprint, "planFingerprint");
        if (planFingerprint.isBlank()) throw new IllegalArgumentException("Plan fingerprint must not be blank");
        if (completedSteps < 0 || (!eligible && completedSteps != 0)) {
            throw new IllegalArgumentException("Invalid reward progress");
        }
        // Retain the existing context's support for null placeholder values.
        placeholders = Collections.unmodifiableMap(new HashMap<>(Objects.requireNonNull(placeholders, "placeholders")));
    }

    /** Legacy snapshots have no timing proof; delayed cursor-zero recovery rejects them. */
    public SharedRewardProgress(String planFingerprint, boolean eligible, int completedSteps,
            Map<String, String> placeholders) {
        this(planFingerprint, eligible, completedSteps, placeholders, null);
    }

    public SharedRewardProgress advance(int nextStep, SharedRewardContext context) {
        if (nextStep < completedSteps) throw new IllegalArgumentException("Reward progress cannot move backwards");
        return new SharedRewardProgress(planFingerprint, eligible, nextStep, context.placeholders(), notBefore);
    }
}
