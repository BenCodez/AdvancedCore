package com.bencodez.advancedcore.api.javascript;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Encodes values that must survive an earlier placeholder pass but still be
 * treated as data by the JavaScript placeholder parser.
 */
public final class JavascriptSafeValue {
	private static final String PREFIX = "__advancedcore_js_value:";

	private JavascriptSafeValue() {
	}

	public static String encodePlaceholder(String value) {
		String encoded = Base64.getUrlEncoder().withoutPadding()
				.encodeToString((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
		return "{" + PREFIX + encoded + "}";
	}

	static String decodePlaceholder(String placeholder) {
		if (placeholder == null || placeholder.length() < 2 || placeholder.charAt(0) != '{'
				|| placeholder.charAt(placeholder.length() - 1) != '}') {
			return null;
		}
		String value = placeholder.substring(1, placeholder.length() - 1);
		if (!value.startsWith(PREFIX)) {
			return null;
		}
		try {
			return new String(Base64.getUrlDecoder().decode(value.substring(PREFIX.length())), StandardCharsets.UTF_8);
		} catch (IllegalArgumentException ignored) {
			return null;
		}
	}
}
