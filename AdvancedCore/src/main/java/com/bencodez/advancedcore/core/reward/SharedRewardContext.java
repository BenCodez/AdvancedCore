package com.bencodez.advancedcore.core.reward;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Platform-neutral identity and placeholder state for one logical reward execution. */
public final class SharedRewardContext {
    private final UUID userId;
    private final String playerName;
    private final HashMap<String, String> placeholders;

    public SharedRewardContext(UUID userId, String playerName, Map<String, String> placeholders) {
        this.userId = Objects.requireNonNull(userId, "userId");
        this.playerName = playerName;
        this.placeholders = new HashMap<>();
        if (placeholders != null) {
            this.placeholders.putAll(placeholders);
        }
    }

    public UUID userId() {
        return userId;
    }

    public String playerName() {
        return playerName;
    }

    /** Mutable execution-local placeholders; durable adapters decide when to persist them. */
    public HashMap<String, String> placeholders() {
        return placeholders;
    }
}
