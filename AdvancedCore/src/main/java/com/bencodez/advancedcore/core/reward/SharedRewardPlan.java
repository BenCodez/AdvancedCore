package com.bencodez.advancedcore.core.reward;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * A prepared configuration snapshot. Durable plans supply a stable definition
 * fingerprint covering requirements, native payloads and injection-registry
 * versions; lambda identities are deliberately not used as persistent identity.
 */
public record SharedRewardPlan(String id, double chance, Duration delay,
        List<SharedRewardRequirement> requirements, List<SharedRewardStep> steps, String definitionFingerprint) {
    public SharedRewardPlan {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(delay, "delay");
        requirements = List.copyOf(Objects.requireNonNull(requirements, "requirements"));
        steps = List.copyOf(Objects.requireNonNull(steps, "steps"));
        if (id.isBlank()) throw new IllegalArgumentException("Reward plan id cannot be blank");
        if (chance < 0.0 || chance > 1.0 || Double.isNaN(chance)) {
            throw new IllegalArgumentException("chance must be between 0 and 1");
        }
        if (delay.isNegative()) throw new IllegalArgumentException("delay cannot be negative");
    }

    /** Retained for synchronous/non-durable callers; bind a definition before durable execution. */
    public SharedRewardPlan(String id, double chance, Duration delay,
            List<SharedRewardRequirement> requirements, List<SharedRewardStep> steps) {
        this(id, chance, delay, requirements, steps, null);
    }

    public static SharedRewardPlan immediate(String id, List<SharedRewardStep> steps) {
        return new SharedRewardPlan(id, 1.0, Duration.ZERO, List.of(), steps);
    }

    public SharedRewardPlan withDefinitionFingerprint(String fingerprint) {
        if (fingerprint == null || fingerprint.isBlank()) {
            throw new IllegalArgumentException("Definition fingerprint must not be blank");
        }
        return new SharedRewardPlan(id, chance, delay, requirements, steps, fingerprint);
    }

    /** Versioned, length-delimited identity includes ordered step IDs and execution policy. */
    public String fingerprint() {
        if (definitionFingerprint == null || definitionFingerprint.isBlank()) {
            throw new IllegalStateException("Durable reward plans require a definition fingerprint");
        }
        StringBuilder value = new StringBuilder("shared-reward-plan-v1");
        field(value, id);
        field(value, definitionFingerprint);
        field(value, Double.toHexString(chance));
        field(value, delay.toString());
        field(value, Integer.toString(requirements.size()));
        field(value, Integer.toString(steps.size()));
        for (SharedRewardStep step : steps) {
            field(value, step.id());
            field(value, Boolean.toString(step.requiresOnlinePlayer()));
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static void field(StringBuilder target, String value) {
        target.append(':').append(value.length()).append(':').append(value);
    }
}
