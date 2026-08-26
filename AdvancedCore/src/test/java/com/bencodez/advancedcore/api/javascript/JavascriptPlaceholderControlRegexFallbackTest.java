package com.bencodez.advancedcore.api.javascript;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JavascriptPlaceholderControlRegexFallbackTest {

    @BeforeEach
    void forceFallbackCompatibleSetup() {
        JavascriptEngineHandler.getInstance().setNashornClassLoader(null);
        JavascriptEngineHandler.getInstance().setCachedEngine(null);
    }

    @Test
    void regexAfterIfControlHeadUsesRegexEscaping() {
        HashMap<String, Object> bindings = new HashMap<>();

        String prepared = JavascriptPlaceholderBinder.bind("if (obj?.name) /^%name%$/.test(value)",
                ignored -> "Ben.*", bindings::put);

        assertEquals("if (obj?.name) /^Ben\\.\\*$/.test(value)", prepared);
        assertTrue(bindings.isEmpty());
    }

    @Test
    void regexAfterNestedWhileControlHeadUsesRegexEscaping() {
        HashMap<String, Object> bindings = new HashMap<>();

        String prepared = JavascriptPlaceholderBinder.bind("while ((obj?.name)) /^%name%$/.test(value)",
                ignored -> "Ben.*", bindings::put);

        assertEquals("while ((obj?.name)) /^Ben\\.\\*$/.test(value)", prepared);
        assertTrue(bindings.isEmpty());
    }
}
