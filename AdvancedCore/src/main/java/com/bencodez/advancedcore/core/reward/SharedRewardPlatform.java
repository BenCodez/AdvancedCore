package com.bencodez.advancedcore.core.reward;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/** Native operations needed by the platform-neutral reward orchestrator. */
public interface SharedRewardPlatform {
    boolean isOnline(UUID userId);

    /** Wall-clock time for durable absolute deadlines; adapters/tests may supply their clock. */
    default Instant now() { return Instant.now(); }

    /** Returns a value in the range [0, 1). */
    double nextChanceRoll();

    /**
     * Runs {@code operation} after the delay and completes only when the operation's
     * returned stage completes. Implementations must not report task submission as
     * completion.
     */
    CompletionStage<SharedRewardResult> delay(Duration delay,
            Supplier<CompletionStage<SharedRewardResult>> operation);

    boolean isShuttingDown();
}
