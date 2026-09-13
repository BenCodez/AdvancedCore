package com.bencodez.advancedcore.core.reward;

/** Outcome of a platform-neutral reward execution. */
public enum SharedRewardResult {
    /** The requested work completed and may be checkpointed by its parent. */
    COMPLETED,
    /** The request was durably deferred and must not be checkpointed as delivered. */
    DEFERRED,
    /** Requirements or chance intentionally prevented delivery. */
    NOT_ELIGIBLE
}
