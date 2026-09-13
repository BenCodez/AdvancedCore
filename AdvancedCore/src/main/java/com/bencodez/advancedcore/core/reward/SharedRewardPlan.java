package com.bencodez.advancedcore.core.reward;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Platform-neutral plan prepared by the existing reward configuration layer.
 * Parsing and native item/sound/effect behavior intentionally remain outside it.
 */
public record SharedRewardPlan(String id, double chance, Duration delay,
        List<SharedRewardRequirement> requirements, List<SharedRewardStep> steps) {
    public SharedRewardPlan {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(delay, "delay");
        requirements = List.copyOf(Objects.requireNonNull(requirements, "requirements"));
        steps = List.copyOf(Objects.requireNonNull(steps, "steps"));
        if (id.isBlank()) throw new IllegalArgumentException("Reward plan id cannot be blank");
        if (chance < 0.0 || chance > 1.0 || Double.isNaN(chance)) {
            throw new IllegalArgumentException("chance must be between 0 and 1");
        }
        if (delay.isNegative()) throw new IllegalArgumentException("delay cannot be negative");
    }

    public static SharedRewardPlan immediate(String id, List<SharedRewardStep> steps) {
        return new SharedRewardPlan(id, 1.0, Duration.ZERO, List.of(), steps);
    }
}
