package com.bencodez.advancedcore.command.gui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.bencodez.simpleapi.player.PlayerUtils;
import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.advancedcore.api.user.UserManager;

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

	@Test
	void editorResolvesUnknownTargetsAsynchronouslyBeforeConstructingUsers() {
		Player player = mock(Player.class);
		UserManager users = mock(UserManager.class);
		AdvancedCoreUser user = mock(AdvancedCoreUser.class);
		when(player.hasPermission("AdvancedCore.UserEdit")).thenReturn(true);
		when(plugin.getUserManager()).thenReturn(users);
		AtomicReference<Consumer<AdvancedCoreUser>> success = new AtomicReference<>();
		org.mockito.Mockito.doAnswer(invocation -> {
			@SuppressWarnings("unchecked") Consumer<AdvancedCoreUser> callback = invocation.getArgument(1, Consumer.class);
			success.set(callback);
			return null;
		}).when(users).getUserAsync(org.mockito.ArgumentMatchers.eq("OfflineTarget"),
				org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
		AtomicReference<AdvancedCoreUser> resolved = new AtomicReference<>();

		try (MockedStatic<PlayerUtils> players = mockStatic(PlayerUtils.class)) {
			players.when(() -> PlayerUtils.getPlayerMeta(plugin, player, "UserGUI")).thenReturn("OfflineTarget");
			UserGUI.getInstance().withResolvedEditorUser(player, "OfflineTarget", resolved::set);
			org.junit.jupiter.api.Assertions.assertNull(resolved.get());
			success.get().accept(user);
			org.junit.jupiter.api.Assertions.assertSame(user, resolved.get());
		}

		verify(users).getUserAsync(org.mockito.ArgumentMatchers.eq("OfflineTarget"),
				org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
	}
}
