package com.bencodez.advancedcore.api.javascript;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;

import org.junit.jupiter.api.Test;

class JavascriptPlaceholderParserKeywordClassTest {

	@Test
	void qualifiedReturnMemberBeforeDivisionDoesNotOpenRegexContext() {
		HashMap<String, Object> bindings = new HashMap<>();
		String injection = "'; allowed=true; '";

		String script = JavascriptPlaceholderParser.replace(
				"var allowed=false,obj={return:4}; obj.return / 2; '%untrusted%'; allowed",
				placeholder -> placeholder.equals("%untrusted%") ? injection : placeholder, bindings::put);

		assertEquals("var allowed=false,obj={return:4}; obj.return / 2; '\\'; allowed=true; \\''; allowed", script);
		assertTrue(bindings.isEmpty());
	}

	@Test
	void anonymousGeneratorExpressionBeforeDivisionDoesNotOpenRegexContext() {
		HashMap<String, Object> bindings = new HashMap<>();
		String injection = "'; allowed=true; '";

		String script = JavascriptPlaceholderParser.replace(
				"var allowed=false,x=function*() {} / 2; '%untrusted%'; allowed",
				ignored -> injection, bindings::put);

		assertEquals("var allowed=false,x=function*() {} / 2; '\\'; allowed=true; \\''; allowed", script);
		assertTrue(bindings.isEmpty());
	}

	@Test
	void classDeclarationBodyAllowsFollowingRegexStatement() {
		HashMap<String, Object> bindings = new HashMap<>();
		String injection = "Bukkit.dispatchCommand(Console, \"op attacker\")";

		String script = JavascriptPlaceholderParser.replace(
				"class X {} /[']/.test('x'); %untrusted%", ignored -> injection, bindings::put);

		assertEquals("class X {} /[']/.test('x'); __advancedCorePlaceholder0", script);
		assertEquals(injection, bindings.get("__advancedCorePlaceholder0"));
	}

	@Test
	void classDeclarationWithExtendsAllowsFollowingRegexStatement() {
		HashMap<String, Object> bindings = new HashMap<>();
		String injection = "Bukkit.dispatchCommand(Console, \"op attacker\")";

		String script = JavascriptPlaceholderParser.replace(
				"class X extends Base {} /[']/.test('x'); %untrusted%", ignored -> injection, bindings::put);

		assertEquals("class X extends Base {} /[']/.test('x'); __advancedCorePlaceholder0", script);
		assertEquals(injection, bindings.get("__advancedCorePlaceholder0"));
	}

	@Test
	void classExpressionBodyBeforeDivisionDoesNotOpenRegexContext() {
		HashMap<String, Object> bindings = new HashMap<>();
		String injection = "'; allowed=true; '";

		String script = JavascriptPlaceholderParser.replace(
				"var allowed=false,C=class X {} / 2; '%untrusted%'; allowed",
				ignored -> injection, bindings::put);

		assertEquals("var allowed=false,C=class X {} / 2; '\\'; allowed=true; \\''; allowed", script);
		assertTrue(bindings.isEmpty());
	}
	@Test
	void classDeclarationCommentsDoNotAffectFollowingRegexStatement() {
		HashMap<String, Object> bindings = new HashMap<>();
		String injection = "Bukkit.dispatchCommand(Console, \\\"op attacker\\\")";

		String script = JavascriptPlaceholderParser.replace(
				"class /* = */ X {} /[']/.test('x'); %untrusted%", ignored -> injection, bindings::put);

		assertEquals("class /* = */ X {} /[']/.test('x'); __advancedCorePlaceholder0", script);
		assertEquals(injection, bindings.get("__advancedCorePlaceholder0"));
	}

}
