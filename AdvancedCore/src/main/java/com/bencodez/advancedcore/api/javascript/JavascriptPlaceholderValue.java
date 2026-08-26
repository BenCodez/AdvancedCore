package com.bencodez.advancedcore.api.javascript;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Encodes placeholder output so it can pass through normal text replacement
 * without ever becoming JavaScript source.
 */
public final class JavascriptPlaceholderValue {
    private static final String PREFIX = "%__advancedcore_bound_";
    private static final String SUFFIX = "%";

    private JavascriptPlaceholderValue() {
    }

    public static String encode(String value) {
        String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
        return PREFIX + encoded + SUFFIX;
    }

    static String decode(String token) {
        if (token == null || !token.startsWith(PREFIX) || !token.endsWith(SUFFIX)) {
            return null;
        }
        String encoded = token.substring(PREFIX.length(), token.length() - SUFFIX.length());
        try {
            return new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
