package com.bencodez.advancedcore.command.gui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.bencodez.simpleapi.player.PlayerUtils;
import com.bencodez.advancedcore.AdvancedCorePlugin;

class UserGUITest {
	private AdvancedCorePlugin plugin;
	private AdvancedCorePlugin previousPlugin;

	@BeforeEach
	void setUp() {
		previousPlugin = UserGUI.getInstance().plugin;
		plugin = mock(AdvancedCorePlugin.class);
		UserGUI.getInstance().plugin = plugin;
	}

	@AfterEach
	void tearDown() {
		UserGUI.getInstance().plugin = previousPlugin;
	}

	@Test
	void deferredEditorRequiresTheSameSelectedTarget() {
		Player player = mock(Player.class);
		when(player.hasPermission("AdvancedCore.UserEdit")).thenReturn(true);
		try (MockedStatic<PlayerUtils> players = mockStatic(PlayerUtils.class)) {
			players.when(() -> PlayerUtils.getPlayerMeta(plugin, player, "UserGUI")).thenReturn("OtherPlayer");

			assertFalse(UserGUI.getInstance().isCurrentEditorTarget(player, "OriginalPlayer"));
		}
	}

	@Test
	void deferredEditorRechecksPermission() {
		Player player = mock(Player.class);
		when(player.hasPermission("AdvancedCore.UserEdit")).thenReturn(false);

		assertFalse(UserGUI.getInstance().isCurrentEditorTarget(player, "OriginalPlayer"));

		verify(player).sendMessage("Not enough permissions");
	}

	@Test
	void deferredEditorAcceptsTheCurrentAuthorizedTarget() {
		Player player = mock(Player.class);
		when(player.hasPermission("AdvancedCore.UserEdit")).thenReturn(true);
		try (MockedStatic<PlayerUtils> players = mockStatic(PlayerUtils.class)) {
			players.when(() -> PlayerUtils.getPlayerMeta(plugin, player, "UserGUI")).thenReturn("OriginalPlayer");

			assertTrue(UserGUI.getInstance().isCurrentEditorTarget(player, "OriginalPlayer"));
		}
	}
}
