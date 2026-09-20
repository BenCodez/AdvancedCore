package com.bencodez.advancedcore.api.rewards;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
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
    private static final int FORMAT_VERSION = 2;
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
        this.directDefinitions = Collections.unmodifiableMap(new TreeMap<>(directDefinitions));
        this.fileDefinitions = Collections.unmodifiableMap(new LinkedHashMap<>(fileDefinitions));
        this.records = records;
        this.versionHash = versionHash;
    }

    /** Captures the root and every currently registered named definition. */
    public static PreparedRewardCatalog capture(Reward root, Collection<DirectlyDefinedReward> directlyDefined,
            Collection<SubDirectlyDefinedReward> subDirectlyDefined, Collection<Reward> rewardFiles) {
        PreparedRewardDefinition preparedRoot = PreparedRewardDefinition.capture(root);
        TreeMap<String, PreparedRewardDefinition> direct = new TreeMap<>();
        LinkedHashMap<String, PreparedRewardDefinition> files = new LinkedHashMap<>();
        int count = 1;
        int recordBytes = 0;

        for (DirectlyDefinedReward entry : directlyDefined) {
            if (entry == null) continue;
            count = checkCount(count + 1);
            String key = directKey(entry.getPath());
            if (!direct.containsKey(key)) {
                Reward reward = entry.getReward();
                PreparedRewardDefinition definition = reward == null ? null : definitionFor(reward, key);
                recordBytes = addRecordBytes(recordBytes, "D", key, definition);
                direct.put(key, definition);
            }
        }
        for (SubDirectlyDefinedReward entry : subDirectlyDefined) {
            if (entry == null) continue;
            count = checkCount(count + 1);
            String key = directKey(entry.getFullPath());
            if (!direct.containsKey(key)) {
                Reward reward = entry.getReward();
                PreparedRewardDefinition definition = reward == null ? null : definitionFor(reward, key);
                recordBytes = addRecordBytes(recordBytes, "D", key, definition);
                direct.put(key, definition);
            }
        }
        for (Reward entry : rewardFiles) {
            count = checkCount(count + 1);
            String key = fileKey(entry == null ? null : entry.getRewardName());
            if (!files.containsKey(key)) {
                PreparedRewardDefinition definition = definitionFor(entry, key);
                recordBytes = addRecordBytes(recordBytes, "F", key, definition);
                files.put(key, definition);
            }
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
        LinkedHashMap<String, PreparedRewardDefinition> files = new LinkedHashMap<>();
        if (!records.isEmpty()) {
            long recordCount = 1L + records.chars().filter(value -> value == '\n').count();
            if (recordCount > MAX_DEFINITIONS - 1L) {
                throw new PreparedRewardDefinitionException(
                        "Prepared reward catalog exceeds " + MAX_DEFINITIONS + " definitions");
            }
            for (String record : records.split("\n", -1)) {
                String[] parts = record.split("\\|", -1);
                if (parts.length != 3) throw new PreparedRewardDefinitionException("Malformed prepared reward catalog record");
                String key = decodeField(parts[1], "lookup key");
                Map<String, PreparedRewardDefinition> target;
                PreparedRewardDefinition definition;
                if ("D".equals(parts[0])) {
                    if (!key.equals(directKey(key))) {
                        throw new PreparedRewardDefinitionException("Malformed direct prepared reward lookup key");
                    }
                    target = direct;
                    definition = parts[2].isEmpty() ? null
                            : PreparedRewardDefinition.decode(decodeField(parts[2], "definition"));
                } else if ("F".equals(parts[0])) {
                    if (!key.equals(fileKey(key))) {
                        throw new PreparedRewardDefinitionException("Malformed file prepared reward lookup key");
                    }
                    target = files;
                    definition = PreparedRewardDefinition.decode(decodeField(parts[2], "definition"));
                } else {
                    throw new PreparedRewardDefinitionException("Malformed prepared reward catalog record type");
                }
                if (target.containsKey(key)) {
                    throw new PreparedRewardDefinitionException("Duplicate prepared reward catalog lookup key");
                }
                target.put(key, definition);
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
        String lookupName = RewardRegistry.normalizeLookupName(rewardName);
        if (lookupName.isEmpty()) lookupName = "EmptyName";
        String directKey = directKeyForLookup(lookupName);
        if (directDefinitions.containsKey(directKey) && directDefinitions.get(directKey) == null) {
            throw new PreparedRewardDefinitionException("Prepared reward catalog has no definition for: " + rewardName);
        }
        PreparedRewardDefinition definition = directDefinitions.get(directKey);
        if (definition == null) {
            String lookup = fileKeyForLookup(lookupName);
            for (Map.Entry<String, PreparedRewardDefinition> entry : fileDefinitions.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(lookup)) {
                    definition = entry.getValue();
                    break;
                }
            }
        }
        if (definition == null) {
            throw new PreparedRewardDefinitionException("Prepared reward catalog has no definition for: " + rewardName);
        }
        return definition.instantiate();
    }

    public String getVersionHash() {
        return versionHash;
    }

    public int getDefinitionCount() {
        return 1 + (int) directDefinitions.values().stream().filter(java.util.Objects::nonNull).count()
                + fileDefinitions.size();
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

    private static int addRecordBytes(int used, String type, String key, PreparedRewardDefinition definition) {
        // Records are ASCII: Base64 URL fields separated by two pipes and newlines.
        String record = type + "|" + encodeField(key) + "|"
                + (definition == null ? "" : encodeField(definition.encode()));
        long next = (long) used + (used == 0 ? 0 : 1) + record.length();
        if (next > MAX_CATALOG_BYTES) {
            throw new PreparedRewardDefinitionException("Prepared reward catalog exceeds " + MAX_CATALOG_BYTES + " bytes");
        }
        return (int) next;
    }

    private static String encodeRecords(Map<String, PreparedRewardDefinition> direct,
            Map<String, PreparedRewardDefinition> files) {
        StringBuilder records = new StringBuilder();
        for (Map.Entry<String, PreparedRewardDefinition> entry : direct.entrySet()) {
            appendRecord(records, "D|" + encodeField(entry.getKey()) + "|"
                    + (entry.getValue() == null ? "" : encodeField(entry.getValue().encode())));
        }
        for (Map.Entry<String, PreparedRewardDefinition> entry : files.entrySet()) {
            appendRecord(records, "F|" + encodeField(entry.getKey()) + "|" + encodeField(entry.getValue().encode()));
        }
        return records.toString();
    }

    private static void appendRecord(StringBuilder records, String record) {
        // All record characters are ASCII (the fields use Base64 URL encoding).
        int nextLength = records.length() + (records.length() == 0 ? 0 : 1) + record.length();
        if (nextLength > MAX_CATALOG_BYTES) {
            throw new PreparedRewardDefinitionException("Prepared reward catalog exceeds " + MAX_CATALOG_BYTES + " bytes");
        }
        if (records.length() != 0) records.append('\n');
        records.append(record);
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
        return name;
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
