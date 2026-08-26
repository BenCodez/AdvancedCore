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
    void newerEngineSyntaxRegexFallbackKeepsCharacterClassSlashes() {
        HashMap<String, Object> bindings = new HashMap<>();

        String prepared = JavascriptPlaceholderBinder.bind("obj?.name && /^[a/b]%name%$/.test(value)",
                ignored -> "Ben.*", bindings::put);

        assertEquals("obj?.name && /^[a/b]Ben\\.\\*$/.test(value)", prepared);
        assertTrue(bindings.isEmpty());
    }

    @Test
    void regexFallbackHandlesLongOrdinaryRunsWithoutChangingPlaceholderSemantics() {
        HashMap<String, Object> bindings = new HashMap<>();
        String dots = ".".repeat(10_000);
        String script = "obj?.name && /^" + dots + "%name%$/.test(value)";

        String prepared = JavascriptPlaceholderBinder.bind(script, ignored -> "Ben", bindings::put);

        assertTrue(prepared.startsWith("obj?.name && /^" + dots));
        assertTrue(prepared.endsWith("Ben$/.test(value)"));
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

    @Test
    void quoteLookingTextInsideTemplateStillUsesTemplateEscaping() {
        HashMap<String, Object> bindings = new HashMap<>();

        String prepared = JavascriptPlaceholderBinder.bind("obj?.name; `'%name%'`",
                ignored -> "${attack()}", bindings::put);

        assertEquals("obj?.name; `'\\${attack()}'`", prepared);
        assertTrue(bindings.isEmpty());
    }

    @Test
    void divisionOperatorsDoNotBecomeFallbackRegexRanges() {
        HashMap<String, Object> bindings = new HashMap<>();

        String prepared = JavascriptPlaceholderBinder.bind("obj?.value; 10 / %count% / 2",
                ignored -> "2.5", bindings::put);

        assertEquals("obj?.value; 10 / __advancedCorePlaceholder0 / 2", prepared);
        assertEquals(2.5D, bindings.get("__advancedCorePlaceholder0"));
    }
}
