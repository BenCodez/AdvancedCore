package com.bencodez.advancedcore.api.rewards;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerializable;

/**
 * An immutable, persistable snapshot of one resolved reward definition.
 *
 * <p>It deliberately snapshots one reward at a time. Nested references must be
 * resolved explicitly while building a prepared plan. Passing an instantiated
 * reward to the legacy dispatcher can still resolve nested names from the live
 * registry and does not provide a frozen graph execution contract.</p>
 */
public final class PreparedRewardDefinition {

    private static final String ENCODED_PREFIX = "AdvancedCorePreparedReward/";
    private static final int FORMAT_VERSION = 2;
    private static final int MAX_REWARD_NAME_BYTES = 256;
    private static final int MAX_YAML_BYTES = 1024 * 1024;
    private static final int MAX_ENCODED_BYTES = 1400000;

    private final String rewardName;
    private final String yamlPayload;
    private final String versionHash;

    private PreparedRewardDefinition(String rewardName, String yamlPayload, String versionHash) {
        this.rewardName = rewardName;
        this.yamlPayload = yamlPayload;
        this.versionHash = versionHash;
    }

    /** Captures the current resolved reward configuration into a detached YAML payload. */
    public static PreparedRewardDefinition capture(Reward reward) {
        if (reward == null) throw new IllegalArgumentException("Reward to prepare must not be null");
        return capture(reward.getRewardName(), reward.getConfig().getConfigData());
    }

    /** Captures a named, inline reward definition into a detached YAML payload. */
    public static PreparedRewardDefinition capture(String rewardName, ConfigurationSection section) {
        validateRewardName(rewardName);
        if (section == null) throw new IllegalArgumentException("Prepared reward configuration must not be null");

        try {
            YamlConfiguration detached = new YamlConfiguration();
            copySection(section, detached);
            String payload = detached.saveToString();
            validatePayloadSize(payload);
            // Validate now, while capture still has not admitted a definition for execution.
            parsePayload(payload);
            return new PreparedRewardDefinition(rewardName, payload, hash(rewardName, payload));
        } catch (PreparedRewardDefinitionException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new PreparedRewardDefinitionException("Could not snapshot reward " + rewardName, failure);
        }
    }

    /** Decodes a value produced by {@link #encode()}, rejecting malformed or changed payloads. */
    public static PreparedRewardDefinition decode(String encoded) {
        if (encoded == null || encoded.length() > MAX_ENCODED_BYTES || !encoded.startsWith(ENCODED_PREFIX)) {
            throw new PreparedRewardDefinitionException("Malformed prepared reward payload");
        }
        String[] fields = encoded.split("/", -1);
        if (fields.length != 5 || !"AdvancedCorePreparedReward".equals(fields[0])) {
            throw new PreparedRewardDefinitionException("Malformed prepared reward payload");
        }
        int version;
        try {
            version = Integer.parseInt(fields[1]);
        } catch (NumberFormatException failure) {
            throw new PreparedRewardDefinitionException("Malformed prepared reward version", failure);
        }
        if (version != FORMAT_VERSION) {
            throw new PreparedRewardDefinitionException("Unsupported prepared reward version: " + version);
        }
        String rewardName = decodeField(fields[2], "name");
        String payload = decodeField(fields[3], "configuration");
        validateRewardName(rewardName);
        validatePayloadSize(payload);
        String expectedHash = fields[4];
        String actualHash = hash(rewardName, payload);
        if (!MessageDigest.isEqual(expectedHash.getBytes(StandardCharsets.US_ASCII),
                actualHash.getBytes(StandardCharsets.US_ASCII))) {
            throw new PreparedRewardDefinitionException("Prepared reward payload hash does not match");
        }
        parsePayload(payload);
        return new PreparedRewardDefinition(rewardName, payload, actualHash);
    }

    /** Encodes this immutable snapshot for durable storage. */
    public String encode() {
        return ENCODED_PREFIX + FORMAT_VERSION + "/" + encodeField(rewardName) + "/" + encodeField(yamlPayload)
                + "/" + versionHash;
    }

    /**
     * Creates a detached reward for plan preparation. It cannot create a legacy
     * generated reward file: the caller must persist this definition with its
     * own durable plan instead of queueing a name that may change on reload.
     */
    public Reward instantiate() {
        return new Reward(rewardName, parsePayload(yamlPayload)).needsRewardFile(false);
    }

    public String getRewardName() {
        return rewardName;
    }

    public String getVersionHash() {
        return versionHash;
    }

    private static void copySection(ConfigurationSection source, ConfigurationSection target) {
        for (String key : source.getKeys(false)) {
            Object value = source.get(key);
            if (value instanceof ConfigurationSection child) {
                ConfigurationSection targetChild = target.createSection(key);
                copySection(child, targetChild);
            } else {
                target.set(key, copyValue(value, key));
            }
        }
    }

    private static Object copyValue(Object value, String path) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean
                || value instanceof Character || value instanceof ConfigurationSerializable) {
            return value;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object entry : list) copy.add(copyValue(entry, path));
            return copy;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw new PreparedRewardDefinitionException(
                            "Unsupported non-string map key in prepared reward at " + path);
                }
                copy.put(key, copyValue(entry.getValue(), path + "." + key));
            }
            return copy;
        }
        throw new PreparedRewardDefinitionException(
                "Unsupported configuration value in prepared reward at " + path + ": " + value.getClass().getName());
    }

    private static YamlConfiguration parsePayload(String payload) {
        try {
            validatePayloadSize(payload);
            YamlConfiguration parsed = new YamlConfiguration();
            parsed.loadFromString(payload);
            return parsed;
        } catch (InvalidConfigurationException | RuntimeException failure) {
            throw new PreparedRewardDefinitionException("Malformed prepared reward configuration", failure);
        }
    }

    private static String encodeField(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeField(String value, String field) {
        try {
            return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException failure) {
            throw new PreparedRewardDefinitionException("Malformed prepared reward " + field, failure);
        }
    }

    private static String hash(String rewardName, String payload) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((FORMAT_VERSION + "\n" + encodeField(rewardName) + "\n" + encodeField(payload))
                            .getBytes(StandardCharsets.US_ASCII));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) result.append(String.format("%02x", value));
            return result.toString();
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is unavailable", failure);
        }
    }

    private static void validateRewardName(String rewardName) {
        if (rewardName == null || rewardName.isEmpty()) {
            throw new PreparedRewardDefinitionException("Prepared reward name must not be empty");
        }
        if (rewardName.getBytes(StandardCharsets.UTF_8).length > MAX_REWARD_NAME_BYTES) {
            throw new PreparedRewardDefinitionException("Prepared reward name exceeds " + MAX_REWARD_NAME_BYTES + " bytes");
        }
    }

    private static void validatePayloadSize(String payload) {
        if (payload == null || payload.getBytes(StandardCharsets.UTF_8).length > MAX_YAML_BYTES) {
            throw new PreparedRewardDefinitionException("Prepared reward YAML exceeds " + MAX_YAML_BYTES + " bytes");
        }
    }
}
