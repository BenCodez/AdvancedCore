package com.bencodez.advancedcore.tests.messages;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.bencodez.advancedcore.AdvancedCoreConfigOptions;
import com.bencodez.advancedcore.api.messages.PlaceholderUtils;
import com.bencodez.advancedcore.tests.BaseTest;

import me.clip.placeholderapi.PlaceholderAPI;

public class PlaceholderUtilsTest {

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
