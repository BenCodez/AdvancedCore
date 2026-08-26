package com.bencodez.advancedcore.api.javascript;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;

import org.junit.jupiter.api.Test;

class JavascriptFunctionExpressionParserTest {

	@Test
	void asyncNamedFunctionExpressionFollowedByDivisionStaysDivision() {
		HashMap<String, Object> bindings = new HashMap<>();
		String injection = "'; allowed=true; '";

		String script = JavascriptPlaceholderParser.replace(
				"var allowed=false,x=async function named() {} / 2; '%untrusted%'; allowed",
				ignored -> injection, bindings::put);

		assertEquals("var allowed=false,x=async function named() {} / 2; '\\'; allowed=true; \\''; allowed", script);
		assertTrue(bindings.isEmpty());
	}

	@Test
	void generatorFunctionExpressionFollowedByDivisionStaysDivision() {
		HashMap<String, Object> bindings = new HashMap<>();
		String injection = "'; allowed=true; '";

		String script = JavascriptPlaceholderParser.replace(
				"var allowed=false,x=function* named() {} / 2; '%untrusted%'; allowed",
				ignored -> injection, bindings::put);

		assertEquals("var allowed=false,x=function* named() {} / 2; '\\'; allowed=true; \\''; allowed", script);
		assertTrue(bindings.isEmpty());
	}
	@Test
	void commentSeparatedFunctionExpressionFollowedByDivisionStaysDivision() {
		HashMap<String, Object> bindings = new HashMap<>();
		String injection = "'; allowed=true; '";

		String script = JavascriptPlaceholderParser.replace(
				"var allowed=false,x=function /* comment */ () {} / 2; '%untrusted%'; allowed",
				ignored -> injection, bindings::put);

		assertEquals("var allowed=false,x=function /* comment */ () {} / 2; '\\\\'; allowed=true; \\\\''; allowed", script);
		assertTrue(bindings.isEmpty());
	}

}
