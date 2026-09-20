package com.bencodez.advancedcore.core.reward;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

/** One already-configured reward operation. Native adapters provide the action. */
public record SharedRewardStep(String id, boolean requiresOnlinePlayer, Action action) {
    public SharedRewardStep {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(action, "action");
        if (id.isBlank()) throw new IllegalArgumentException("Reward step id cannot be blank");
    }

    @FunctionalInterface
    public interface Action {
        CompletionStage<SharedRewardResult> execute(SharedRewardContext context, String executionPath);
    }
}
