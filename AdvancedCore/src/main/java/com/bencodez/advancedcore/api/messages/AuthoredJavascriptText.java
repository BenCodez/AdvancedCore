package com.bencodez.advancedcore.api.messages;

import java.util.function.Function;

import com.bencodez.advancedcore.api.javascript.JavascriptEngine;

/**
 * Separates operator-authored {@code [Javascript=...]} regions from ordinary
 * text before any placeholder result is applied.
 * <p>
 * Only regions present in the input to {@link #transform(String, Function,
 * Function)} are preserved as executable JavaScript. Marker-shaped text
 * produced by a placeholder transformation is neutralized and remains text.
 */
final class AuthoredJavascriptText {
    private static final String MARKER = "[Javascript=";
    private static final String NEUTRALIZED_MARKER = "[Javascript =";

    private AuthoredJavascriptText() {
    }

    static String transform(String text, Function<String, String> textTransform,
            Function<String, String> scriptTransform) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        StringBuilder result = new StringBuilder(text.length());
        int cursor = 0;
        while (cursor < text.length()) {
            int start = indexOfIgnoreCase(text, MARKER, cursor);
            if (start < 0) {
                result.append(neutralizeMarkers(apply(textTransform, text.substring(cursor))));
                break;
            }

            int bodyStart = start + MARKER.length();
            int end = text.indexOf(']', bodyStart);
            if (end < 0) {
                result.append(neutralizeMarkers(apply(textTransform, text.substring(cursor))));
                break;
            }

            result.append(neutralizeMarkers(apply(textTransform, text.substring(cursor, start))));
            result.append(text, start, bodyStart);
            result.append(apply(scriptTransform, text.substring(bodyStart, end)));
            result.append(']');
            cursor = end + 1;
        }
        return result.toString();
    }

    static String evaluate(String text, JavascriptEngine engine) {
        return transform(text, Function.identity(), script -> {
            Object result = engine.getResult(script);
            return result == null ? "" : result.toString();
        });
    }

    private static String apply(Function<String, String> transform, String value) {
        String transformed = transform.apply(value);
        return transformed == null ? "" : transformed;
    }

    private static String neutralizeMarkers(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        StringBuilder result = new StringBuilder(text.length());
        int cursor = 0;
        while (cursor < text.length()) {
            int start = indexOfIgnoreCase(text, MARKER, cursor);
            if (start < 0) {
                result.append(text, cursor, text.length());
                break;
            }
            result.append(text, cursor, start).append(NEUTRALIZED_MARKER);
            cursor = start + MARKER.length();
        }
        return result.toString();
    }

    private static int indexOfIgnoreCase(String text, String target, int fromIndex) {
        int maximum = text.length() - target.length();
        for (int index = Math.max(0, fromIndex); index <= maximum; index++) {
            if (text.regionMatches(true, index, target, 0, target.length())) {
                return index;
            }
        }
        return -1;
    }
}
