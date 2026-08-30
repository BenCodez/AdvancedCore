package com.bencodez.advancedcore.api.javascript;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class JavascriptTextTemplateTest {

    @Test
    void evaluatesOnlyMarkersPresentInOriginalText() {
        String result = JavascriptTextTemplate.parse("A [Javascript=1 + 1] B")
                .evaluate(value -> value, script -> "2");

        assertEquals("A 2 B", result);
    }

    @Test
    void generatedMarkerFromTextRemainsText() {
        String result = JavascriptTextTemplate.parse("%value%")
                .evaluate(value -> value.replace("%value%", "[Javascript=danger()]"), script -> "executed");

        assertEquals("[Javascript =danger()]", result);
        assertFalse(result.contains("[Javascript="));
    }

    @Test
    void generatedMarkerFromJavascriptResultCannotRunOnSecondPass() {
        String first = JavascriptTextTemplate.parse("[Javascript=makeText()]")
                .evaluate(value -> value, script -> "[Javascript=danger()]");
        String second = JavascriptTextTemplate.parse(first)
                .evaluate(value -> value, script -> "executed");

        assertEquals("[Javascript =danger()]", first);
        assertEquals(first, second);
    }

    @Test
    void transformPreservesAuthoredBoundary() {
        String result = JavascriptTextTemplate.parse("before %value% [Javascript='%value%'] after")
                .transform(value -> value.replace("%value%", "text"),
                        script -> script.replace("%value%", "encoded"));

        assertEquals("before text [Javascript='encoded'] after", result);
    }
}
