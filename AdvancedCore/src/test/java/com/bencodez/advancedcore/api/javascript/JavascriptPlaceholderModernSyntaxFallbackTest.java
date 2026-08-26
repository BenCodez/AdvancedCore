package com.bencodez.advancedcore.api.javascript;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JavascriptPlaceholderModernSyntaxFallbackTest {

    @BeforeEach
    void resetJavascriptHandler() {
        JavascriptEngineHandler.getInstance().setNashornClassLoader(null);
        JavascriptEngineHandler.getInstance().setCachedEngine(null);
    }

    @Test
    void newerEngineSyntaxStillPreservesQuotedPlaceholderSemantics() {
        HashMap<String, Object> bindings = new HashMap<>();

        String prepared = JavascriptPlaceholderBinder.bind("obj?.name && '%name%' === 'Ben'",
                ignored -> "Ben", bindings::put);

        assertEquals("obj?.name && 'Ben' === 'Ben'", prepared);
        assertTrue(bindings.isEmpty());
    }

    @Test
    void newerEngineSyntaxStillEscapesQuotedPlaceholderInjection() {
        HashMap<String, Object> bindings = new HashMap<>();
        String injection = "'; Bukkit.dispatchCommand(Console, 'op attacker'); '";

        String prepared = JavascriptPlaceholderBinder.bind("obj?.name && '%name%' === 'safe'",
                ignored -> injection, bindings::put);

        assertEquals("obj?.name && '\\'; Bukkit.dispatchCommand(Console, \\'op attacker\\'); \\'' === 'safe'",
                prepared);
        assertFalse(prepared.contains("&& ''; Bukkit"));
        assertTrue(bindings.isEmpty());
    }

    @Test
    void newerEngineSyntaxStillPreservesRegexPlaceholderSemantics() {
        HashMap<String, Object> bindings = new HashMap<>();

        String prepared = JavascriptPlaceholderBinder.bind("obj?.name && /^%name%$/.test(value)",
                ignored -> "Ben.*", bindings::put);

        assertEquals("obj?.name && /^Ben\\.\\*$/.test(value)", prepared);
        assertTrue(bindings.isEmpty());
    }

    @Test
    void newerEngineSyntaxRegexFallbackKeepsEscapedSlashSemantics() {
        HashMap<String, Object> bindings = new HashMap<>();

        String prepared = JavascriptPlaceholderBinder.bind("obj?.name && /^path\\/%name%$/.test(value)",
                ignored -> "Ben.*", bindings::put);

        assertEquals("obj?.name && /^path\\/Ben\\.\\*$/.test(value)", prepared);
        assertTrue(bindings.isEmpty());
    }

    @Test
    void newerEngineSyntaxStillPreservesTemplatePlaceholderSemantics() {
        HashMap<String, Object> bindings = new HashMap<>();

        String prepared = JavascriptPlaceholderBinder.bind("obj?.name && `Hello %name%`",
                ignored -> "Ben` ${attack}", bindings::put);

        assertEquals("obj?.name && `Hello Ben\\` \\${attack}`", prepared);
        assertTrue(bindings.isEmpty());
    }
}
