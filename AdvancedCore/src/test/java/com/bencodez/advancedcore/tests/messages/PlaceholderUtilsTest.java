package com.bencodez.advancedcore.tests.messages;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.bencodez.advancedcore.AdvancedCoreConfigOptions;
import com.bencodez.advancedcore.api.messages.PlaceholderUtils;
import com.bencodez.advancedcore.tests.BaseTest;

import me.clip.placeholderapi.PlaceholderAPI;

public class PlaceholderUtilsTest {
	@Test
	public void disabledJavascriptStillExpandsPlaceholderApiInsideAuthoredMarker() {
		BaseTest base = BaseTest.getInstance();
		Player player = mock(Player.class);

		when(base.options.isJavascriptEngineEnabled()).thenReturn(false);
		when(base.plugin.isPlaceHolderAPIEnabled()).thenReturn(true);

		try (MockedStatic<PlaceholderAPI> placeholderApiStatic = mockStatic(PlaceholderAPI.class)) {
			placeholderApiStatic.when(() -> PlaceholderAPI.setPlaceholders(any(OfflinePlayer.class), anyString()))
					.thenAnswer(invocation -> invocation.<String>getArgument(1)
							.replace("%player_name%", "Ben")
							.replace("%attack%", "] [Javascript=danger()"));

			assertEquals("Hello Ben [Javascript='Ben']",
					PlaceholderUtils.replaceJavascript(player, "Hello %player_name% [Javascript='%player_name%']"));
			assertEquals("[Javascript='] [Javascript =danger()']",
					PlaceholderUtils.replaceJavascript(player, "[Javascript='%attack%']"));
		}
	}

	@Test
	public void replaceJavascriptOnlyDoesNotExpandPlaceholderApiOutput() {
		AdvancedCoreConfigOptions options = BaseTest.getInstance().options;
		Player player = mock(Player.class);
		String command = "say %untrusted%";

		when(options.isJavascriptEngineEnabled()).thenReturn(false);

		try (MockedStatic<PlaceholderAPI> placeholderApiStatic = mockStatic(PlaceholderAPI.class)) {
			assertEquals(command, PlaceholderUtils.replaceJavascriptOnly(player, command));
			placeholderApiStatic.verifyNoInteractions();
		}
	}
}
