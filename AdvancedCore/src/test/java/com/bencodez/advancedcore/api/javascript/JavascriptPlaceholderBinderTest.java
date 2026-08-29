package com.bencodez.advancedcore.api.javascript;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;

import org.junit.jupiter.api.Test;

class JavascriptPlaceholderBinderTest {

    @Test
    void barePlaceholderIsBoundAsData() {
        HashMap<String, Object> bindings = new HashMap<>();
        String injection = "Bukkit.dispatchCommand(Console, 'op attacker')";

        String prepared = JavascriptPlaceholderBinder.bind("%value% == true", ignored -> injection, bindings::put);

        assertEquals("__advancedCorePlaceholder0 == true", prepared);
        assertEquals(injection, bindings.get("__advancedCorePlaceholder0"));
        assertFalse(prepared.contains(injection));
    }

    @Test
    void numericAndBooleanValuesKeepExpressionTypes() {
        HashMap<String, Object> bindings = new HashMap<>();

        String prepared = JavascriptPlaceholderBinder.bind("%count% > 1 && %allowed% == true",
                token -> token.equals("%count%") ? "2.5" : "true", bindings::put);

        assertEquals("__advancedCorePlaceholder0 > 1 && __advancedCorePlaceholder1 == true", prepared);
        assertEquals(2.5D, bindings.get("__advancedCorePlaceholder0"));
        assertEquals(Boolean.TRUE, bindings.get("__advancedCorePlaceholder1"));
    }

    @Test
    void ordinaryCompactModuloExpressionIsNotTreatedAsAPlaceholder() {
        HashMap<String, Object> bindings = new HashMap<>();

        String prepared = JavascriptPlaceholderBinder.bind("10%3%2", token -> token, bindings::put);

        assertEquals("10%3%2", prepared);
        assertTrue(bindings.isEmpty());
    }

    @Test
    void unresolvedPercentTokenRemainsUntouched() {
        HashMap<String, Object> bindings = new HashMap<>();

        String prepared = JavascriptPlaceholderBinder.bind("'%unknown%'", token -> token, bindings::put);

        assertEquals("'%unknown%'", prepared);
        assertTrue(bindings.isEmpty());
    }

    @Test
    void compactModuloCanAppearBesideAResolvedPlaceholder() {
        HashMap<String, Object> bindings = new HashMap<>();

        String prepared = JavascriptPlaceholderBinder.bind("10%3%2 + %value%",
                token -> token.equals("%value%") ? "4" : token, bindings::put);

        assertEquals("10%3%2 + __advancedCorePlaceholder0", prepared);
        assertEquals(4L, bindings.get("__advancedCorePlaceholder0"));
    }

    @Test
    void moduloCandidateCannotHideAnOverlappingResolvedPlaceholder() {
        HashMap<String, Object> bindings = new HashMap<>();

        String prepared = JavascriptPlaceholderBinder.bind("10%3 == 1 && %value% == true",
                token -> token.equals("%value%") ? "true" : token, bindings::put);

        assertEquals("10%3 == 1 && __advancedCorePlaceholder0 == true", prepared);
        assertEquals(Boolean.TRUE, bindings.get("__advancedCorePlaceholder0"));
    }

    @Test
    void placeholderApiStyleTokenMayContainWhitespace() {
        HashMap<String, Object> bindings = new HashMap<>();

        String prepared = JavascriptPlaceholderBinder.bind("%exp_argument with spaces% == true",
                ignored -> "true", bindings::put);

        assertEquals("__advancedCorePlaceholder0 == true", prepared);
        assertEquals(Boolean.TRUE, bindings.get("__advancedCorePlaceholder0"));
    }

    @Test
    void quotedAndEmbeddedPlaceholdersStayStrings() {
        HashMap<String, Object> bindings = new HashMap<>();

        String prepared = JavascriptPlaceholderBinder.bind("'%code%' === '001' && 'Hello %name%!' === 'Hello Ben!'",
                token -> token.equals("%code%") ? "001" : "Ben", bindings::put);

        assertEquals("'001' === '001' && 'Hello Ben!' === 'Hello Ben!'", prepared);
        assertTrue(bindings.isEmpty());
    }

    @Test
    void stringInjectionIsEscaped() {
        HashMap<String, Object> bindings = new HashMap<>();
        String injection = "'; Bukkit.dispatchCommand(Console, 'op attacker'); '";

        String prepared = JavascriptPlaceholderBinder.bind("'%name%' == 'safe'", ignored -> injection, bindings::put);

        assertEquals("'\\'; Bukkit.dispatchCommand(Console, \\'op attacker\\'); \\'' == 'safe'", prepared);
        assertTrue(bindings.isEmpty());
    }

    @Test
    void templateTextAndExpressionUseDifferentContexts() {
        HashMap<String, Object> bindings = new HashMap<>();
        String prepared = JavascriptPlaceholderBinder.bind("`Hello %name% ${%count% + 1}`",
                token -> token.equals("%name%") ? "${attack()}`" : "2", bindings::put);

        assertEquals("`Hello \\${attack()}\\` ${__advancedCorePlaceholder0 + 1}`", prepared);
        assertEquals(2L, bindings.get("__advancedCorePlaceholder0"));
    }

    @Test
    void oddBackslashBeforeTemplatePlaceholderCannotReactivateInterpolation() {
        HashMap<String, Object> bindings = new HashMap<>();

        String prepared = JavascriptPlaceholderBinder.bind("`\\%value%`", ignored -> "${attack()}", bindings::put);

        assertEquals("`\\${attack()}`", prepared);
        assertTrue(bindings.isEmpty());
    }

    @Test
    void evenBackslashesBeforeTemplatePlaceholderRemainLiteralAndSafe() {
        HashMap<String, Object> bindings = new HashMap<>();

        String prepared = JavascriptPlaceholderBinder.bind("`\\\\%value%`", ignored -> "${attack()}", bindings::put);

        assertEquals("`\\\\\\${attack()}`", prepared);
        assertTrue(bindings.isEmpty());
    }

    @Test
    void oddBackslashBeforeStringPlaceholderCannotReactivateQuote() {
        HashMap<String, Object> bindings = new HashMap<>();
        String injection = "'; attack(); '";

        String prepared = JavascriptPlaceholderBinder.bind("'\\%value%'", ignored -> injection, bindings::put);

        assertEquals("'\\'; attack(); \\''", prepared);
        assertTrue(bindings.isEmpty());
    }

    @Test
    void regexPlaceholderIsEscapedAsLiteralPatternText() {
        HashMap<String, Object> bindings = new HashMap<>();

        String prepared = JavascriptPlaceholderBinder.bind("/^%name%$/i.test(value)",
                ignored -> "Ben.*", bindings::put);

        assertEquals("/^Ben\\.\\*$/i.test(value)", prepared);
        assertTrue(bindings.isEmpty());
    }

    @Test
    void oddBackslashBeforeRegexPlaceholderCannotReactivateDelimiter() {
        HashMap<String, Object> bindings = new HashMap<>();

        String prepared = JavascriptPlaceholderBinder.bind("/\\%value%/.test(input)",
                ignored -> "/; attack() //", bindings::put);

        assertEquals("/\\/; attack\\(\\) \\/\\//.test(input)", prepared);
        assertTrue(bindings.isEmpty());
    }

    @Test
    void placeholdersInCommentsAreNotEvaluatedOrRewritten() {
        HashMap<String, Object> bindings = new HashMap<>();

        String prepared = JavascriptPlaceholderBinder.bind("obj?.name; /* %name% */ true",
                ignored -> "danger()", bindings::put);

        assertEquals("obj?.name; /* %name% */ true", prepared);
        assertTrue(bindings.isEmpty());
    }

    @Test
    void invalidPlaceholderBearingJavascriptFailsClosed() {
        HashMap<String, Object> bindings = new HashMap<>();

        assertThrows(IllegalArgumentException.class,
                () -> JavascriptPlaceholderBinder.bind("if ( %name%", ignored -> "Ben", bindings::put));
        assertTrue(bindings.isEmpty());
    }
}
