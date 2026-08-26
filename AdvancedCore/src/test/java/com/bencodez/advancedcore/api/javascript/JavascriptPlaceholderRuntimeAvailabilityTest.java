package com.bencodez.advancedcore.api.javascript;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

class JavascriptPlaceholderRuntimeAvailabilityTest {

    @Test
    void nashornParserIsAvailableToAdvancedCore() {
        assertDoesNotThrow(() -> Class.forName("org.openjdk.nashorn.api.tree.Parser", false,
                JavascriptPlaceholderBinder.class.getClassLoader()));
    }
}
