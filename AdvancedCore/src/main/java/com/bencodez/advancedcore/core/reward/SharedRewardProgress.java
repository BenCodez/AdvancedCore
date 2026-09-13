package com.bencodez.advancedcore.core.reward;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Durable decision and cursor for one occurrence/path, including cursor zero. */
public record SharedRewardProgress(String planFingerprint, boolean eligible, int completedSteps,
        Map<String, String> placeholders) {
    public SharedRewardProgress {
        Objects.requireNonNull(planFingerprint, "planFingerprint");
        if (planFingerprint.isBlank()) throw new IllegalArgumentException("Plan fingerprint is blank");
        if (completedSteps < 0 || (!eligible && completedSteps != 0)) {
            throw new IllegalArgumentException("Invalid reward progress");
        }
        placeholders = Collections.unmodifiableMap(new HashMap<>(Objects.requireNonNull(placeholders, "placeholders")));
    }

    public SharedRewardProgress advance(int nextStep, SharedRewardContext context) {
        if (nextStep < completedSteps) throw new IllegalArgumentException("Reward progress cannot move backwards");
        return new SharedRewardProgress(planFingerprint, eligible, nextStep, context.placeholders());
    }
}
