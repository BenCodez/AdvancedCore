package com.bencodez.advancedcore.core.reward;

import java.util.concurrent.CompletionStage;

@FunctionalInterface
public interface SharedRewardRequirement {
    CompletionStage<Boolean> test(SharedRewardContext context);
}
