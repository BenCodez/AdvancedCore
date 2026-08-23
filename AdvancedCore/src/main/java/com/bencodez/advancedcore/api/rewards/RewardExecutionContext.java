package com.bencodez.advancedcore.api.rewards;

import java.util.HashMap;

import com.bencodez.advancedcore.api.user.AdvancedCoreUser;

/**
 * Internal execution state derived from {@link RewardOptions} for one reward
 * dispatch operation.
 * <p>
 * RewardOptions remains the public compatibility object; this class centralizes
 * execution-specific normalization and naming rules so they are not repeated
 * throughout the reward dispatcher.
 */
public final class RewardExecutionContext {

    private final RewardOptions options;

    public RewardExecutionContext(RewardOptions options) {
        this.options = options == null ? new RewardOptions() : options;
    }

    public RewardExecutionContext initializeOnlineState(AdvancedCoreUser user) {
        if (user != null && !options.isOnlineSet()) {
            options.setOnline(user.isOnline());
        }
        return this;
    }

    public String buildRewardName(String path) {
        String rewardName = "";
        String prefix = options.getPrefix();
        if (prefix != null && !prefix.isEmpty()) {
            rewardName += prefix + "_";
        }
        rewardName += path == null ? "" : path.replace(".", "_");

        String suffix = options.getSuffix();
        if (suffix != null && !suffix.isEmpty()) {
            rewardName += "_" + suffix;
        }
        return rewardName;
    }

    public HashMap<String, String> getPlaceholders() {
        return options.getPlaceholders();
    }

    public RewardOptions getOptions() {
        return options;
    }

    /**
     * Preserves the existing direct/sub-direct dispatch condition. Prefix and
     * suffix default to empty strings, so only explicit null values disable that
     * compatibility path.
     */
    public boolean supportsDirectDispatch() {
        return options.getPrefix() != null && options.getSuffix() != null;
    }
}
