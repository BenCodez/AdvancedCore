package com.bencodez.advancedcore.core.reward;

/** A native action may have taken effect but has no durable completion acknowledgement. */
public final class SharedRewardIndeterminateException extends IllegalStateException {
    private static final long serialVersionUID = 1L;

    public SharedRewardIndeterminateException(String executionPath, int stepIndex) {
        super("Native reward action requires reconciliation: " + executionPath + " step " + stepIndex);
    }
}
