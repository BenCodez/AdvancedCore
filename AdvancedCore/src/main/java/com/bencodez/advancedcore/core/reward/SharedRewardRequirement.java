package com.bencodez.advancedcore.core.reward;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

@FunctionalInterface
public interface SharedRewardRequirement {
    enum Outcome {
        PASS,
        FAIL,
        RETRY
    }

    /** Existing boolean contract: false is a permanent requirement failure. */
    CompletionStage<Boolean> test(SharedRewardContext context);

    /** Adapters with retryable requirements override this outcome instead of collapsing retry into false. */
    default CompletionStage<Outcome> evaluate(SharedRewardContext context) {
        CompletionStage<Boolean> stage = test(context);
        return stage == null ? null : stage.thenApply(passed -> Boolean.TRUE.equals(passed) ? Outcome.PASS : Outcome.FAIL);
    }

    /** Wrap an existing boolean requirement whose false result must remain pending for a later retry. */
    static SharedRewardRequirement retryable(SharedRewardRequirement delegate) {
        Objects.requireNonNull(delegate, "delegate");
        return new SharedRewardRequirement() {
            @Override
            public CompletionStage<Boolean> test(SharedRewardContext context) {
                return delegate.test(context);
            }

            @Override
            public CompletionStage<Outcome> evaluate(SharedRewardContext context) {
                CompletionStage<Boolean> stage = delegate.test(context);
                return stage == null ? null : stage.thenApply(passed ->
                        Boolean.TRUE.equals(passed) ? Outcome.PASS : Outcome.RETRY);
            }
        };
    }
}
