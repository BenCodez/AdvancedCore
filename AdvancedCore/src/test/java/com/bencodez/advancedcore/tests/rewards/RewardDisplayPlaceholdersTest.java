package com.bencodez.advancedcore.tests.rewards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.HashMap;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.messages.PlaceholderUtils;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.rewards.RewardBuilder;
import com.bencodez.advancedcore.api.rewards.RewardDisplayPlaceholders;
import com.bencodez.advancedcore.api.rewards.RewardHandler;
import com.bencodez.advancedcore.api.rewards.builtin.RewardMessages;
import com.bencodez.advancedcore.api.rewards.injected.RewardInjectString;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.simpleapi.array.ArrayUtils;

public class RewardDisplayPlaceholdersTest {
	@TempDir
	File tempDir;

	@Test
	public void displayValueDoesNotReplaceExactActionValue() {
		RewardBuilder builder = new RewardBuilder((org.bukkit.configuration.ConfigurationSection) null, "Reward")
				.withPlaceHolder("ServiceSite", "be_secret%20")
				.withDisplayPlaceHolder("ServiceSite", "\u2060be_secret%20\u2060");

		HashMap<String, String> stored = builder.getRewardOptions().getPlaceholders();
		assertEquals("be_secret%20", stored.get("ServiceSite"));
		assertEquals("\u2060be_secret%20\u2060", RewardDisplayPlaceholders.forDisplay(stored).get("ServiceSite"));
	}

	@Test
	public void displayValueSurvivesOfflinePlaceholderSerialization() {
		HashMap<String, String> stored = new HashMap<>();
		stored.put("ServiceSite", "be_secret%20");
		RewardDisplayPlaceholders.put(stored, "ServiceSite", "\u2060value%entry%with%pair%delimiters\u2060");

		HashMap<String, String> restored = ArrayUtils.fromString(ArrayUtils.makeString(stored));
		HashMap<String, String> display = RewardDisplayPlaceholders.forDisplay(restored);

		assertEquals("be_secret%20", restored.get("ServiceSite"));
		assertEquals("\u2060value%entry%with%pair%delimiters\u2060", display.get("ServiceSite"));
		assertFalse(display.keySet().stream().anyMatch(key -> key.startsWith("__AdvancedCoreDisplayPlaceholder__")));
	}

	@Test
	public void ordinaryReservedPrefixAndNullKeysRemainVisibleWithoutManifest() {
		HashMap<String, String> placeholders = new HashMap<>();
		placeholders.put("__AdvancedCoreDisplayPlaceholder__legacy", "kept");
		placeholders.put(null, "null-key");

		HashMap<String, String> display = RewardDisplayPlaceholders.forDisplay(placeholders);

		assertSame(placeholders, display);
		assertEquals("kept", display.get("__AdvancedCoreDisplayPlaceholder__legacy"));
		assertEquals("null-key", display.get(null));
	}

	@Test
	public void displayOverrideReplacesRawKeyCaseInsensitively() {
		HashMap<String, String> stored = new HashMap<>();
		stored.put("ServiceSite", "raw");
		RewardDisplayPlaceholders.put(stored, "servicesite", "display");

		HashMap<String, String> display = RewardDisplayPlaceholders.forDisplay(stored);

		assertEquals(1, display.size());
		assertEquals("display", display.get("servicesite"));
		assertFalse(display.containsKey("ServiceSite"));
	}

	@Test
	public void latestCaseVariantDisplayOverrideWins() {
		HashMap<String, String> stored = new HashMap<>();
		stored.put("ServiceSite", "raw");
		RewardDisplayPlaceholders.put(stored, "ServiceSite", "first");
		RewardDisplayPlaceholders.put(stored, "servicesite", "second");

		HashMap<String, String> display = RewardDisplayPlaceholders.forDisplay(stored);

		assertEquals(1, display.size());
		assertEquals("second", display.get("servicesite"));
	}

	@Test
	public void emptyDisplayValueSurvivesOfflinePlaceholderSerialization() {
		HashMap<String, String> stored = new HashMap<>();
		stored.put("ServiceSite", "raw");
		RewardDisplayPlaceholders.put(stored, "ServiceSite", null);

		HashMap<String, String> restored = ArrayUtils.fromString(ArrayUtils.makeString(stored));

		assertEquals("", RewardDisplayPlaceholders.forDisplay(restored).get("ServiceSite"));
	}

	@Test
	public void javascriptSinkEvaluatesBeforeRestoringDisplayValue() {
		HashMap<String, String> display = new HashMap<>();
		display.put("ServiceSite", "raw");
		RewardDisplayPlaceholders.put(display, "ServiceSite", "[Javascript=danger()]");

		try (MockedStatic<PlaceholderUtils> placeholders = mockStatic(PlaceholderUtils.class, CALLS_REAL_METHODS)) {
			placeholders.when(() -> PlaceholderUtils.replaceJavascript(anyString()))
					.thenAnswer(invocation -> {
						String protectedText = invocation.getArgument(0);
						assertFalse(protectedText.contains("[Javascript=danger()]"));
						return protectedText;
					});

			assertEquals("[Javascript=danger()]", RewardDisplayPlaceholders.replaceFormattedJavascript(null,
					"%ServiceSite%", display));
		}
	}

	@Test
	public void unrelatedOrdinaryPlaceholderKeepsLegacyEvaluationOrder() {
		HashMap<String, String> placeholders = new HashMap<>();
		placeholders.put("ServiceSite", "raw");
		placeholders.put("rank", "%vault_rank%");
		RewardDisplayPlaceholders.put(placeholders, "ServiceSite", "[Javascript=danger()]");

		try (MockedStatic<PlaceholderUtils> placeholderUtils = mockStatic(PlaceholderUtils.class, CALLS_REAL_METHODS)) {
			placeholderUtils.when(() -> PlaceholderUtils.replaceJavascript(anyString()))
					.thenAnswer(invocation -> {
						String protectedText = invocation.getArgument(0);
						assertTrue(protectedText.contains("%vault_rank%"));
						assertFalse(protectedText.contains("[Javascript=danger()]"));
						return protectedText.replace("%vault_rank%", "Admin");
					});

			assertEquals("Admin [Javascript=danger()]", RewardDisplayPlaceholders.replaceFormattedJavascript(null,
					"%rank% %ServiceSite%", placeholders));
		}
	}

	@Test
	public void broadcastRenderingDoesNotEnableJavascript() {
		HashMap<String, String> placeholders = new HashMap<>();
		placeholders.put("ServiceSite", "raw");
		RewardDisplayPlaceholders.put(placeholders, "ServiceSite", "display");

		String rendered = RewardDisplayPlaceholders.replaceFormattedBroadcast(null, null,
				"[Javascript='%ServiceSite%']", placeholders);

		assertTrue(rendered.startsWith("[Javascript="));
	}

	@Test
	public void actionLikeVisibleTextStillUsesDisplayValue() {
		HashMap<String, String> placeholders = new HashMap<>();
		placeholders.put("ServiceSite", "raw");
		RewardDisplayPlaceholders.put(placeholders, "ServiceSite", "display");

		assertEquals("Use command=\"display\"",
				RewardDisplayPlaceholders.replaceFormattedMessage("Use command=\"%ServiceSite%\"", placeholders));
	}

	@Test
	public void configuredInteractiveComponentKeepsHarmlessDisplayTextUnchanged() {
		HashMap<String, String> placeholders = new HashMap<>();
		placeholders.put("ServiceSite", "raw");
		RewardDisplayPlaceholders.put(placeholders, "ServiceSite", "display");

		assertEquals("[Text=\"display\",command=\"/trusted\"]",
				RewardDisplayPlaceholders.replaceFormattedMessage(
						"[Text=\"%ServiceSite%\",command=\"/trusted\"]", placeholders));
	}

	@Test
	public void exactAttributeKeepsOrdinaryPlaceholderEvaluationOrder() {
		HashMap<String, String> placeholders = new HashMap<>();
		placeholders.put("ServiceSite", "raw");
		placeholders.put("rank", "%vault_rank%");
		RewardDisplayPlaceholders.put(placeholders, "ServiceSite", "display");

		try (MockedStatic<PlaceholderUtils> placeholderUtils = mockStatic(PlaceholderUtils.class, CALLS_REAL_METHODS)) {
			placeholderUtils.when(() -> PlaceholderUtils.parseText(anyString()))
					.thenAnswer(invocation -> ((String) invocation.getArgument(0)).replace("%vault_rank%", "Admin"));

			assertEquals("[Text=\"display\",command=\"/rank Admin/raw\"]",
					RewardDisplayPlaceholders.replaceFormattedMessage(
							"[Text=\"%ServiceSite%\",command=\"/rank %rank%/%ServiceSite%\"]", placeholders));
		}
	}

	@Test
	public void displayValueCannotCreateInteractiveComponent() {
		HashMap<String, String> placeholders = new HashMap<>();
		placeholders.put("ServiceSite", "raw");
		RewardDisplayPlaceholders.put(placeholders, "ServiceSite",
				"[Text=\"Click\",command=\"/untrusted\"]");

		String rendered = RewardDisplayPlaceholders.replaceFormattedMessage("Site: %ServiceSite%", placeholders);

		assertFalse(rendered.contains("[Text=\""));
		assertFalse(rendered.contains("\",command=\"/untrusted"));
		assertTrue(rendered.replace("\u2060", "").contains("[Text=\"Click\",command=\"/untrusted\"]"));
	}

	@Test
	public void displayValueCannotTerminateConfiguredVisibleText() {
		HashMap<String, String> placeholders = new HashMap<>();
		placeholders.put("ServiceSite", "raw");
		RewardDisplayPlaceholders.put(placeholders, "ServiceSite", "shown\"],command=\"/untrusted");

		String rendered = RewardDisplayPlaceholders.replaceFormattedMessage(
				"[Text=\"%ServiceSite%\",command=\"/trusted\"]", placeholders);

		assertFalse(rendered.contains("shown\"],command=\"/untrusted"));
		assertTrue(rendered.endsWith(",command=\"/trusted\"]"));
	}

	@Test
	public void playerMessageSinkUsesDisplayValue() {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		when(plugin.getDataFolder()).thenReturn(tempDir);
		when(plugin.getLogger()).thenReturn(mock(Logger.class));
		RewardHandler handler = new RewardHandler(plugin);
		try {
			RewardMessages.register(handler, plugin);
			RewardInjectString message = handler.getInjectedRewards().stream()
					.filter(RewardInjectString.class::isInstance)
					.map(RewardInjectString.class::cast)
					.filter(inject -> inject.getPath().equals("Message"))
					.findFirst().orElseThrow();
			AdvancedCoreUser user = mock(AdvancedCoreUser.class);
			HashMap<String, String> placeholders = new HashMap<>();
			placeholders.put("ServiceSite", "be_secret&amount=20");
			RewardDisplayPlaceholders.put(placeholders, "ServiceSite", "\u2060be_secret%20\u2060");

			message.onRewardRequest(mock(Reward.class), user,
					"[Text=\"Vote at %ServiceSite%\",command=\"&a/vote %ServiceSite%\",url=\"https://x/?site=%ServiceSite%\"]",
					placeholders);

			verify(user).sendPreparedMessage(
					"[Text=\"Vote at \u2060be_secret%20\u2060\",command=\"\u00a7a/vote be_secret&amount=20\",url=\"https://x/?site=be_secret&amount=20\"]");
			assertEquals("be_secret&amount=20", placeholders.get("ServiceSite"));
		} finally {
			handler.getDelayedTimer().shutdownNow();
		}
	}
}
