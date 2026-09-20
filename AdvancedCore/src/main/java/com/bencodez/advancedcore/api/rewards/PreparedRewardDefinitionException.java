package com.bencodez.advancedcore.api.rewards;

/** Raised when a prepared reward definition cannot be safely captured or restored. */
public final class PreparedRewardDefinitionException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    public PreparedRewardDefinitionException(String message) {
        super(message);
    }

    public PreparedRewardDefinitionException(String message, Throwable cause) {
        super(message, cause);
    }
}
