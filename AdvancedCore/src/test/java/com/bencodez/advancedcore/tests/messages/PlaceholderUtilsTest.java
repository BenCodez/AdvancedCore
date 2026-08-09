package com.bencodez.advancedcore.tests.messages;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.bencodez.advancedcore.AdvancedCoreConfigOptions;
import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.messages.PlaceholderUtils;

import me.clip.placeholderapi.PlaceholderAPI;

public class PlaceholderUtilsTest {

	@Test
	public void replaceJavascriptOnlyDoesNotExpandPlaceholderApiOutput() {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		AdvancedCoreConfigOptions options = mock(AdvancedCoreConfigOptions.class);
		Player player = mock(Player.class);
		String command = "say %untrusted%";

		when(plugin.getOptions()).thenReturn(options);
		when(options.isJavascriptEngineEnabled()).thenReturn(false);

		try (MockedStatic<AdvancedCorePlugin> pluginStatic = mockStatic(AdvancedCorePlugin.class);
				MockedStatic<PlaceholderAPI> placeholderApiStatic = mockStatic(PlaceholderAPI.class)) {
			pluginStatic.when(AdvancedCorePlugin::getInstance).thenReturn(plugin);

			assertEquals(command, PlaceholderUtils.replaceJavascriptOnly(player, command));
			placeholderApiStatic.verifyNoInteractions();
		}
	}
}
