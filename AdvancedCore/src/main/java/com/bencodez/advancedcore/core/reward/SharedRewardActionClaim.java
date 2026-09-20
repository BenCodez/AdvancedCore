package com.bencodez.advancedcore.core.reward;

/** Result of an atomic, durable attempt to claim one native reward action. */
public enum SharedRewardActionClaim {
    /** This caller persisted the pending action and may invoke it once. */
    STARTED,
    /** A prior caller may have invoked the action; automatic replay is unsafe. */
    INDETERMINATE
}
