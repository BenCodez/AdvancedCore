package com.bencodez.advancedcore.api.javascript;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Splits configured text into literal text and JavaScript segments before any
 * placeholder replacement occurs.
 * <p>
 * Only {@code [Javascript=...]} markers present in the original input become
 * executable segments. Marker-looking text produced later by a placeholder or
 * JavaScript result is neutralized and remains ordinary text.
 */
public final class JavascriptTextTemplate {
    private static final String MARKER = "[Javascript=";

    private final List<Segment> segments;

    private JavascriptTextTemplate(List<Segment> segments) {
        this.segments = segments;
    }

    public static JavascriptTextTemplate parse(String source) {
        List<Segment> segments = new ArrayList<>();
        if (source == null || source.isEmpty()) {
            segments.add(Segment.text(source));
            return new JavascriptTextTemplate(segments);
        }

        int cursor = 0;
        while (cursor < source.length()) {
            int start = indexOfIgnoreCase(source, MARKER, cursor);
            if (start < 0) {
                segments.add(Segment.text(source.substring(cursor)));
                break;
            }

            if (start > cursor) {
                segments.add(Segment.text(source.substring(cursor, start)));
            }

            int bodyStart = start + MARKER.length();
            int end = source.indexOf(']', bodyStart);
            if (end < 0) {
                segments.add(Segment.text(source.substring(start)));
                break;
            }

            segments.add(Segment.javascript(source.substring(start, bodyStart),
                    source.substring(bodyStart, end)));
            cursor = end + 1;
        }

        if (segments.isEmpty()) {
            segments.add(Segment.text(source));
        }
        return new JavascriptTextTemplate(segments);
    }

    /**
     * Transforms literal and JavaScript content while preserving authored marker
     * boundaries.
     */
    public String transform(Function<String, String> textTransformer,
            Function<String, String> javascriptTransformer) {
        StringBuilder result = new StringBuilder();
        for (Segment segment : segments) {
            if (segment.javascript) {
                result.append(segment.markerPrefix)
                        .append(apply(javascriptTransformer, segment.value))
                        .append(']');
            } else {
                result.append(neutralizeGeneratedMarkers(apply(textTransformer, segment.value)));
            }
        }
        return result.toString();
    }

    /**
     * Evaluates authored JavaScript segments and returns plain text. Any marker
     * produced by transformed text or a script result is neutralized so a second
     * processing pass cannot execute it.
     */
    public String evaluate(Function<String, String> textTransformer,
            Function<String, String> javascriptEvaluator) {
        StringBuilder result = new StringBuilder();
        for (Segment segment : segments) {
            String value = segment.javascript
                    ? apply(javascriptEvaluator, segment.value)
                    : apply(textTransformer, segment.value);
            result.append(value);
        }
        return neutralizeGeneratedMarkers(result.toString());
    }

    public static String neutralizeGeneratedMarkers(String text) {
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
            result.append(text, cursor, start + MARKER.length() - 1).append(" =");
            cursor = start + MARKER.length();
        }
        return result.toString();
    }

    private static String apply(Function<String, String> transformer, String value) {
        String transformed = transformer == null ? value : transformer.apply(value);
        return transformed == null ? "" : transformed;
    }

    private static int indexOfIgnoreCase(String text, String target, int fromIndex) {
        int max = text.length() - target.length();
        for (int i = Math.max(0, fromIndex); i <= max; i++) {
            if (text.regionMatches(true, i, target, 0, target.length())) {
                return i;
            }
        }
        return -1;
    }

    private static final class Segment {
        private final boolean javascript;
        private final String markerPrefix;
        private final String value;

        private Segment(boolean javascript, String markerPrefix, String value) {
            this.javascript = javascript;
            this.markerPrefix = markerPrefix;
            this.value = value;
        }

        private static Segment text(String value) {
            return new Segment(false, null, value == null ? "" : value);
        }

        private static Segment javascript(String markerPrefix, String value) {
            return new Segment(true, markerPrefix, value);
        }
    }
}
