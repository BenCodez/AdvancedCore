package com.bencodez.advancedcore.api.javascript;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class JavascriptSecurityFindingRegressionTest {

	private static final String INJECTED_MARKER = "[Javascript=Bukkit.shutdown()]";

	@Test
	void displayNamePlaceholderCannotCreateExecutableJavascript() {
		String rendered = renderPlaceholder("Welcome %displayname%", "%displayname%");

		assertEquals("Welcome [Javascript =Bukkit.shutdown()]", rendered);
	}

	@Test
	void placeholderApiOutputCannotCreateExecutableJavascript() {
		String rendered = renderPlaceholder("Reward: %papi_value%", "%papi_value%");

		assertEquals("Reward: [Javascript =Bukkit.shutdown()]", rendered);
	}

	@Test
	void conditionalItemPlaceholderCannotCreateExecutableJavascript() {
		String rendered = renderPlaceholder("Item lore: {conditional_value}", "{conditional_value}");

		assertEquals("Item lore: [Javascript =Bukkit.shutdown()]", rendered);
	}

	private String renderPlaceholder(String configuredText, String placeholder) {
		return JavascriptTextTemplate.parse(configuredText)
				.evaluate(text -> text.replace(placeholder, INJECTED_MARKER), script -> "executed");
	}
}
