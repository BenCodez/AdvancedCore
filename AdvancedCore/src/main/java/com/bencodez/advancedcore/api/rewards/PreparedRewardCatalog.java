package com.bencodez.advancedcore.api.rewards;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Immutable, persistable lookup data for the named rewards available while a
 * caller prepares one root reward plan.
 *
 * <p>The catalog preserves {@link RewardRegistry} lookup precedence: directly
 * defined entries, then sub-direct entries, then ordinary reward files. It
 * freezes only the definitions supplied at capture time; callers must capture
 * a new catalog when they intentionally want a later registry state. The
 * legacy reward dispatcher does not consume this catalog automatically: a
 * caller must resolve nested names through {@link #instantiate(String)} while
 * preparing its own execution plan.</p>
 */
public final class PreparedRewardCatalog {

    private static final String ENCODED_PREFIX = "AdvancedCorePreparedRewardCatalog/";
    private static final int FORMAT_VERSION = 1;
    private static final int MAX_DEFINITIONS = 1024;
    private static final int MAX_CATALOG_BYTES = 4 * 1024 * 1024;
    private static final int MAX_ENCODED_BYTES = 8 * 1024 * 1024;

    private final PreparedRewardDefinition root;
    private final Map<String, PreparedRewardDefinition> directDefinitions;
    private final Map<String, PreparedRewardDefinition> fileDefinitions;
    private final String records;
    private final String versionHash;

    private PreparedRewardCatalog(PreparedRewardDefinition root,
            Map<String, PreparedRewardDefinition> directDefinitions,
            Map<String, PreparedRewardDefinition> fileDefinitions,
            String records, String versionHash) {
        this.root = root;
        this.directDefinitions = Map.copyOf(directDefinitions);
        this.fileDefinitions = Map.copyOf(fileDefinitions);
        this.records = records;
        this.versionHash = versionHash;
    }

    /** Captures the root and every currently registered named definition. */
    public static PreparedRewardCatalog capture(Reward root, Collection<DirectlyDefinedReward> directlyDefined,
            Collection<SubDirectlyDefinedReward> subDirectlyDefined, Collection<Reward> rewardFiles) {
        PreparedRewardDefinition preparedRoot = PreparedRewardDefinition.capture(root);
        TreeMap<String, PreparedRewardDefinition> direct = new TreeMap<>();
        TreeMap<String, PreparedRewardDefinition> files = new TreeMap<>();
        int count = 1;

        for (DirectlyDefinedReward entry : directlyDefined) {
            count = checkCount(count + 1);
            String key = directKey(entry == null ? null : entry.getPath());
            PreparedRewardDefinition definition = definitionFor(entry == null ? null : entry.getReward(), key);
            direct.putIfAbsent(key, definition);
        }
        for (SubDirectlyDefinedReward entry : subDirectlyDefined) {
            count = checkCount(count + 1);
            String key = directKey(entry == null ? null : entry.getFullPath());
            PreparedRewardDefinition definition = definitionFor(entry == null ? null : entry.getReward(), key);
            direct.putIfAbsent(key, definition);
        }
        for (Reward entry : rewardFiles) {
            count = checkCount(count + 1);
            String key = fileKey(entry == null ? null : entry.getRewardName());
            files.putIfAbsent(key, definitionFor(entry, key));
        }

        String records = encodeRecords(direct, files);
        validateRecords(records);
        String rootEncoded = preparedRoot.encode();
        return new PreparedRewardCatalog(preparedRoot, direct, files, records, hash(rootEncoded, records));
    }

    /** Restores a catalog written by {@link #encode()}, rejecting changed data. */
    public static PreparedRewardCatalog decode(String encoded) {
        if (encoded == null || encoded.length() > MAX_ENCODED_BYTES || !encoded.startsWith(ENCODED_PREFIX)) {
            throw new PreparedRewardDefinitionException("Malformed prepared reward catalog");
        }
        String[] fields = encoded.split("/", -1);
        if (fields.length != 5 || !"AdvancedCorePreparedRewardCatalog".equals(fields[0])) {
            throw new PreparedRewardDefinitionException("Malformed prepared reward catalog");
        }
        if (!String.valueOf(FORMAT_VERSION).equals(fields[1])) {
            throw new PreparedRewardDefinitionException("Unsupported prepared reward catalog version: " + fields[1]);
        }
        String rootEncoded = decodeField(fields[2], "root");
        String records = decodeField(fields[3], "definitions");
        validateRecords(records);
        String actualHash = hash(rootEncoded, records);
        if (!MessageDigest.isEqual(fields[4].getBytes(StandardCharsets.US_ASCII),
                actualHash.getBytes(StandardCharsets.US_ASCII))) {
            throw new PreparedRewardDefinitionException("Prepared reward catalog hash does not match");
        }

        PreparedRewardDefinition root = PreparedRewardDefinition.decode(rootEncoded);
        TreeMap<String, PreparedRewardDefinition> direct = new TreeMap<>();
        TreeMap<String, PreparedRewardDefinition> files = new TreeMap<>();
        if (!records.isEmpty()) {
            for (String record : records.split("\n", -1)) {
                String[] parts = record.split("\\|", -1);
                if (parts.length != 3) throw new PreparedRewardDefinitionException("Malformed prepared reward catalog record");
                String key = decodeField(parts[1], "lookup key");
                PreparedRewardDefinition definition = PreparedRewardDefinition.decode(decodeField(parts[2], "definition"));
                Map<String, PreparedRewardDefinition> target;
                if ("D".equals(parts[0])) {
                    if (!key.equals(directKey(key))) {
                        throw new PreparedRewardDefinitionException("Malformed direct prepared reward lookup key");
                    }
                    target = direct;
                } else if ("F".equals(parts[0])) {
                    if (!key.equals(fileKey(key))) {
                        throw new PreparedRewardDefinitionException("Malformed file prepared reward lookup key");
                    }
                    target = files;
                } else {
                    throw new PreparedRewardDefinitionException("Malformed prepared reward catalog record type");
                }
                if (target.putIfAbsent(key, definition) != null) {
                    throw new PreparedRewardDefinitionException("Duplicate prepared reward catalog lookup key");
                }
                checkCount(1 + direct.size() + files.size());
            }
        }
        return new PreparedRewardCatalog(root, direct, files, records, actualHash);
    }

    /** Encodes this bounded catalog for durable storage. */
    public String encode() {
        String rootEncoded = root.encode();
        return ENCODED_PREFIX + FORMAT_VERSION + "/" + encodeField(rootEncoded) + "/" + encodeField(records) + "/"
                + versionHash;
    }

    /** Creates a fresh detached root reward for plan preparation. */
    public Reward instantiateRoot() {
        return root.instantiate();
    }

    /**
     * Resolves a named entry using the captured registry precedence and returns
     * a fresh detached reward. Unknown names fail closed.
     */
    public Reward instantiate(String rewardName) {
        PreparedRewardDefinition definition = directDefinitions.get(directKeyForLookup(rewardName));
        if (definition == null) definition = fileDefinitions.get(fileKeyForLookup(rewardName));
        if (definition == null) {
            throw new PreparedRewardDefinitionException("Prepared reward catalog has no definition for: " + rewardName);
        }
        return definition.instantiate();
    }

    public String getVersionHash() {
        return versionHash;
    }

    public int getDefinitionCount() {
        return 1 + directDefinitions.size() + fileDefinitions.size();
    }

    private static PreparedRewardDefinition definitionFor(Reward reward, String lookupKey) {
        if (reward == null) {
            throw new PreparedRewardDefinitionException("Could not resolve prepared reward for " + lookupKey);
        }
        return PreparedRewardDefinition.capture(reward);
    }

    private static int checkCount(int count) {
        if (count > MAX_DEFINITIONS) {
            throw new PreparedRewardDefinitionException("Prepared reward catalog exceeds " + MAX_DEFINITIONS + " definitions");
        }
        return count;
    }

    private static String encodeRecords(Map<String, PreparedRewardDefinition> direct,
            Map<String, PreparedRewardDefinition> files) {
        ArrayList<String> records = new ArrayList<>(direct.size() + files.size());
        for (Map.Entry<String, PreparedRewardDefinition> entry : direct.entrySet()) {
            records.add("D|" + encodeField(entry.getKey()) + "|" + encodeField(entry.getValue().encode()));
        }
        for (Map.Entry<String, PreparedRewardDefinition> entry : files.entrySet()) {
            records.add("F|" + encodeField(entry.getKey()) + "|" + encodeField(entry.getValue().encode()));
        }
        return String.join("\n", records);
    }

    private static String directKeyForLookup(String name) {
        return directKey(RewardRegistry.normalizeLookupName(name));
    }

    private static String fileKeyForLookup(String name) {
        return fileKey(RewardRegistry.normalizeLookupName(name));
    }

    private static String directKey(String name) {
        validateKey(name);
        return RewardRegistry.normalizeDirectPath(name);
    }

    private static String fileKey(String name) {
        validateKey(name);
        return RewardRegistry.normalizeLookupName(name).toLowerCase(Locale.ROOT);
    }

    private static void validateKey(String key) {
        if (key == null || key.isEmpty() || key.getBytes(StandardCharsets.UTF_8).length > 256) {
            throw new PreparedRewardDefinitionException("Invalid prepared reward catalog lookup key");
        }
    }

    private static void validateRecords(String records) {
        if (records == null || records.getBytes(StandardCharsets.UTF_8).length > MAX_CATALOG_BYTES) {
            throw new PreparedRewardDefinitionException("Prepared reward catalog exceeds " + MAX_CATALOG_BYTES + " bytes");
        }
    }

    private static String encodeField(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeField(String value, String field) {
        try {
            return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException failure) {
            throw new PreparedRewardDefinitionException("Malformed prepared reward catalog " + field, failure);
        }
    }

    private static String hash(String rootEncoded, String records) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((FORMAT_VERSION + "\n" + rootEncoded + "\n" + records).getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) result.append(String.format("%02x", value));
            return result.toString();
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is unavailable", failure);
        }
    }
}
