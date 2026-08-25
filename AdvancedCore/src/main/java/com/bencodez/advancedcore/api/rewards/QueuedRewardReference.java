package com.bencodez.advancedcore.api.rewards;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Versioned envelope used only for persisted offline/timed reward references.
 * The prefix contains a backslash, which is not a valid reward file name, so a
 * normal reward name cannot be mistaken for queue metadata.
 */
public final class QueuedRewardReference {

    private static final String PREFIX = "\\AdvancedCoreQueueV1\\";

    private QueuedRewardReference() {
    }

    public static String encode(String rewardName, boolean generatedSnapshot) {
        String encodedName = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(rewardName.getBytes(StandardCharsets.UTF_8));
        return PREFIX + (generatedSnapshot ? "1:" : "0:") + encodedName;
    }

    public static Parsed parse(String reference) {
        if (reference == null || !reference.startsWith(PREFIX)) {
            return null;
        }
        int flagIndex = PREFIX.length();
        if (reference.length() <= flagIndex + 2 || reference.charAt(flagIndex + 1) != ':') {
            return null;
        }
        char flag = reference.charAt(flagIndex);
        if (flag != '0' && flag != '1') {
            return null;
        }
        try {
            String rewardName = new String(Base64.getUrlDecoder().decode(reference.substring(flagIndex + 2)),
                    StandardCharsets.UTF_8);
            return new Parsed(rewardName, flag == '1');
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static final class Parsed {
        private final boolean generatedSnapshot;
        private final String rewardName;

        private Parsed(String rewardName, boolean generatedSnapshot) {
            this.rewardName = rewardName;
            this.generatedSnapshot = generatedSnapshot;
        }

        public boolean isGeneratedSnapshot() {
            return generatedSnapshot;
        }

        public String getRewardName() {
            return rewardName;
        }
    }
}
