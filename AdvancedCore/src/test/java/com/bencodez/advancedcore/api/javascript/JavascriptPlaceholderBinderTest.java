package com.bencodez.advancedcore.api.javascript;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JavascriptPlaceholderBinderTest {

    @BeforeEach
    void useTestClasspathNashornParser() {
        JavascriptEngineHandler.getInstance().setNashornClassLoader(null);
        JavascriptEngineHandler.getInstance().setCachedEngine(null);
    }

    @Test
    void ordinaryJavascriptWithoutPlaceholdersIsUntouched() {
        HashMap<String, Object> bindings = new HashMap<>();
        String script = "Player.hasPermission(\"someper\") == true";

        String prepared = JavascriptPlaceholderBinder.bind(script, token -> token, bindings::put);

        assertEquals(script, prepared);
        assertTrue(bindings.isEmpty());
    }

    @Test
    void placeholderExpressionIsAutomaticallyBound() {
        HashMap<String, Object> bindings = new HashMap<>();

        String prepared = JavascriptPlaceholderBinder.bind("%permission_result% == true", ignored -> "true",
                bindings::put);

        assertEquals("__advancedCorePlaceholder0 == true", prepared);
        assertEquals(Boolean.TRUE, bindings.get("__advancedCorePlaceholder0"));
    }

    @Test
    void placeholderOutputIsBoundInsteadOfCopiedIntoSource() {
        HashMap<String, Object> bindings = new HashMap<>();
        String injection = "Bukkit.dispatchCommand(Console, 'op attacker')";

        String prepared = JavascriptPlaceholderBinder.bind("%name% == 'safe'", ignored -> injection, bindings::put);

        assertEquals("__advancedCorePlaceholder0 == 'safe'", prepared);
        assertEquals(injection, bindings.get("__advancedCorePlaceholder0"));
        assertFalse(prepared.contains(injection));
    }

    @Test
    void existingQuotedPlaceholderSyntaxIsPreserved() {
        HashMap<String, Object> bindings = new HashMap<>();

        String prepared = JavascriptPlaceholderBinder.bind("'%name%' == 'Ben'", ignored -> "Ben", bindings::put);

        assertEquals("'Ben' == 'Ben'", prepared);
        assertTrue(bindings.isEmpty());
    }

    @Test
    void placeholderInsideExistingStringNeedsNoMigration() {
        HashMap<String, Object> bindings = new HashMap<>();
        String value = "Ben's \\ server";

        String prepared = JavascriptPlaceholderBinder.bind("'Hello %name%!'", ignored -> value, bindings::put);

        assertEquals("'Hello Ben\\'s \\\\ server!'", prepared);
        assertTrue(bindings.isEmpty());
    }

    @Test
    void placeholderInsideTemplateTextIsEscapedAutomatically() {
        HashMap<String, Object> bindings = new HashMap<>();
        String value = "${attack}`";

        String prepared = JavascriptPlaceholderBinder.bind("`Hello %name%`", ignored -> value, bindings::put);

        assertEquals("`Hello \\${attack}\\``", prepared);
        assertTrue(bindings.isEmpty());
    }

    @Test
    void placeholderInsideTemplateExpressionIsBound() {
        HashMap<String, Object> bindings = new HashMap<>();

        String prepared = JavascriptPlaceholderBinder.bind("`${%count% + 1}`", ignored -> "5", bindings::put);

        assertEquals("`${__advancedCorePlaceholder0 + 1}`", prepared);
        assertEquals(Long.valueOf(5), bindings.get("__advancedCorePlaceholder0"));
    }

    @Test
    void placeholderInsideRegexKeepsExistingSyntax() {
        HashMap<String, Object> bindings = new HashMap<>();

        String prepared = JavascriptPlaceholderBinder.bind("/^%name%$/.test(value)", ignored -> "Ben.* /admin",
                bindings::put);

        assertEquals("/^Ben\\.\\* \\/admin$/.test(value)", prepared);
        assertTrue(bindings.isEmpty());
    }

    @Test
    void encodedCustomPlaceholderInsideStringCannotBreakOut() {
        HashMap<String, Object> bindings = new HashMap<>();
        String injection = "'; Bukkit.dispatchCommand(Console, 'op attacker'); '";
        String encoded = JavascriptPlaceholderValue.encode(injection);

        String prepared = JavascriptPlaceholderBinder.bind("'" + encoded + "'", token -> token, bindings::put);

        assertEquals("'\\'; Bukkit.dispatchCommand(Console, \\'op attacker\\'); \\''", prepared);
        assertTrue(bindings.isEmpty());
    }

    @Test
    void preservesPrimitiveTypesForExpressionPlaceholders() {
        HashMap<String, Object> bindings = new HashMap<>();

        String prepared = JavascriptPlaceholderBinder.bind("%allowed% && %count% > 2 && %ratio% < 2.0", token -> {
            if (token.equals("%allowed%")) {
                return "true";
            }
            if (token.equals("%count%")) {
                return "5";
            }
            return "1.5";
        }, bindings::put);

        assertEquals("__advancedCorePlaceholder0 && __advancedCorePlaceholder1 > 2 && __advancedCorePlaceholder2 < 2.0",
                prepared);
        assertEquals(Boolean.TRUE, bindings.get("__advancedCorePlaceholder0"));
        assertEquals(Long.valueOf(5), bindings.get("__advancedCorePlaceholder1"));
        assertEquals(Double.valueOf(1.5), bindings.get("__advancedCorePlaceholder2"));
    }


    @Test
    void exactQuotedNumericLookingPlaceholderRemainsAString() {
        HashMap<String, Object> bindings = new HashMap<>();

        String prepared = JavascriptPlaceholderBinder.bind("'%code%' === '001'", ignored -> "001", bindings::put);

        assertEquals("'001' === '001'", prepared);
        assertTrue(bindings.isEmpty());
    }

    @Test
    void braceDelimitedCustomPlaceholderIsAutomaticallyBound() {
        HashMap<String, Object> bindings = new HashMap<>();

        String prepared = JavascriptPlaceholderBinder.bind("{count} > 0",
                token -> token.equals("{count}") ? "5" : token, bindings::put);

        assertEquals("__advancedCorePlaceholder0 > 0", prepared);
        assertEquals(Long.valueOf(5), bindings.get("__advancedCorePlaceholder0"));
    }

    @Test
    void unresolvedBraceSyntaxRemainsOrdinaryJavascript() {
        HashMap<String, Object> bindings = new HashMap<>();

        String prepared = JavascriptPlaceholderBinder.bind("var value = {count: 1}; %name%",
                token -> token.equals("%name%") ? "Ben" : token, bindings::put);

        assertEquals("var value = {count: 1}; __advancedCorePlaceholder0", prepared);
        assertEquals("Ben", bindings.get("__advancedCorePlaceholder0"));
    }

    @Test
    void parserFailurePreservesQuotedPlaceholderUnderModernSyntax() {
        HashMap<String, Object> bindings = new HashMap<>();

        String prepared = JavascriptPlaceholderBinder.bind("obj?.name && '%name%' === 'Ben'",
                ignored -> "Ben", bindings::put);

        assertEquals("obj?.name && 'Ben' === 'Ben'", prepared);
        assertTrue(bindings.isEmpty());
    }

    @Test
    void parserFailurePreservesTemplateTextUnderModernSyntax() {
        HashMap<String, Object> bindings = new HashMap<>();

        String prepared = JavascriptPlaceholderBinder.bind("obj?.name && `Hello %name%`",
                ignored -> "Ben` ${attack}", bindings::put);

        assertEquals("obj?.name && `Hello Ben\\` \\${attack}`", prepared);
        assertTrue(bindings.isEmpty());
    }

    @Test
    void parserFailurePreservesRegexPlaceholderUnderModernSyntax() {
        HashMap<String, Object> bindings = new HashMap<>();

        String prepared = JavascriptPlaceholderBinder.bind("obj?.name && /^%name%$/.test(value)",
                ignored -> "Ben.*", bindings::put);

        assertEquals("obj?.name && /^Ben\\.\\*$/.test(value)", prepared);
        assertTrue(bindings.isEmpty());
    }

    @Test
    void unresolvedTokensRemainUntouched() {
        HashMap<String, Object> bindings = new HashMap<>();

        String prepared = JavascriptPlaceholderBinder.bind("%unknown% == true", token -> token, bindings::put);

        assertEquals("%unknown% == true", prepared);
        assertTrue(bindings.isEmpty());
    }
}
