package com.bencodez.advancedcore.tests.command.gui;

import com.bencodez.advancedcore.tests.support.ReflectiveAccess;

import com.bencodez.advancedcore.command.gui.UserGUI;

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
import com.bencodez.simpleapi.scheduler.BukkitScheduler;

class UserGUITest {
    private static java.lang.reflect.Field pluginField() throws NoSuchFieldException {
        var field = UserGUI.class.getDeclaredField("plugin");
        field.setAccessible(true);
        return field;
    }

    private static boolean isCurrentEditorTarget(Player player, String name) {
        return ReflectiveAccess.invoke(UserGUI.class, UserGUI.getInstance(), "isCurrentEditorTarget",
                new Class<?>[] { Player.class, String.class }, player, name);
    }

    private static void withResolvedEditorUser(Player player, String name,
            java.util.function.Consumer<AdvancedCoreUser> action) {
        ReflectiveAccess.invoke(UserGUI.class, UserGUI.getInstance(), "withResolvedEditorUser",
                new Class<?>[] { Player.class, String.class, java.util.function.Consumer.class }, player, name, action);
    }

	private AdvancedCorePlugin plugin;
	private AdvancedCorePlugin previousPlugin;

	@BeforeEach
	void setUp() throws ReflectiveOperationException {
		previousPlugin = (AdvancedCorePlugin) pluginField().get(UserGUI.getInstance());
		plugin = mock(AdvancedCorePlugin.class);
		pluginField().set(UserGUI.getInstance(), plugin);
	}

	@AfterEach
	void tearDown() throws ReflectiveOperationException {
		pluginField().set(UserGUI.getInstance(), previousPlugin);
	}

	@Test
	void deferredEditorRequiresTheSameSelectedTarget() {
		Player player = mock(Player.class);
		when(player.hasPermission("AdvancedCore.UserEdit")).thenReturn(true);
		try (MockedStatic<PlayerUtils> players = mockStatic(PlayerUtils.class)) {
			players.when(() -> PlayerUtils.getPlayerMeta(plugin, player, "UserGUI")).thenReturn("OtherPlayer");

			assertFalse(isCurrentEditorTarget(player, "OriginalPlayer"));
		}
	}

	@Test
	void deferredEditorRechecksPermission() {
		Player player = mock(Player.class);
		when(player.hasPermission("AdvancedCore.UserEdit")).thenReturn(false);

		assertFalse(isCurrentEditorTarget(player, "OriginalPlayer"));

		verify(player).sendMessage("Not enough permissions");
	}

	@Test
	void deferredEditorAcceptsTheCurrentAuthorizedTarget() {
		Player player = mock(Player.class);
		when(player.hasPermission("AdvancedCore.UserEdit")).thenReturn(true);
		try (MockedStatic<PlayerUtils> players = mockStatic(PlayerUtils.class)) {
			players.when(() -> PlayerUtils.getPlayerMeta(plugin, player, "UserGUI")).thenReturn("OriginalPlayer");

			assertTrue(isCurrentEditorTarget(player, "OriginalPlayer"));
		}
	}

	@Test
	void editorResolvesUnknownTargetsAsynchronouslyBeforeConstructingUsers() {
		Player player = mock(Player.class);
		UserManager users = mock(UserManager.class);
		BukkitScheduler scheduler = mock(BukkitScheduler.class);
		AdvancedCoreUser user = mock(AdvancedCoreUser.class);
		when(player.hasPermission("AdvancedCore.UserEdit")).thenReturn(true);
		when(plugin.getUserManager()).thenReturn(users);
		when(plugin.getBukkitScheduler()).thenReturn(scheduler);
		AtomicReference<Runnable> entityCallback = new AtomicReference<>();
		org.mockito.Mockito.doAnswer(invocation -> {
			entityCallback.set(invocation.getArgument(1, Runnable.class));
			return null;
		}).when(scheduler).runTask(org.mockito.ArgumentMatchers.eq(plugin),
				org.mockito.ArgumentMatchers.any(Runnable.class), org.mockito.ArgumentMatchers.eq(player));
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
			withResolvedEditorUser(player, "OfflineTarget", resolved::set);
			org.junit.jupiter.api.Assertions.assertNull(resolved.get());
			success.get().accept(user);
			org.junit.jupiter.api.Assertions.assertNull(resolved.get());
			org.junit.jupiter.api.Assertions.assertNotNull(entityCallback.get());
			entityCallback.get().run();
			org.junit.jupiter.api.Assertions.assertSame(user, resolved.get());
		}

		verify(users).getUserAsync(org.mockito.ArgumentMatchers.eq("OfflineTarget"),
				org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
		verify(scheduler).runTask(plugin, entityCallback.get(), player);
	}

	@Test
	void editorDispatchesResolutionFailuresToThePlayerEntityLane() {
		Player player = mock(Player.class);
		UserManager users = mock(UserManager.class);
		BukkitScheduler scheduler = mock(BukkitScheduler.class);
		when(player.hasPermission("AdvancedCore.UserEdit")).thenReturn(true);
		when(plugin.getUserManager()).thenReturn(users);
		when(plugin.getBukkitScheduler()).thenReturn(scheduler);
		AtomicReference<Consumer<Throwable>> failure = new AtomicReference<>();
		org.mockito.Mockito.doAnswer(invocation -> {
			@SuppressWarnings("unchecked") Consumer<Throwable> callback = invocation.getArgument(2, Consumer.class);
			failure.set(callback);
			return null;
		}).when(users).getUserAsync(org.mockito.ArgumentMatchers.eq("OfflineTarget"),
				org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
		AtomicReference<Runnable> entityCallback = new AtomicReference<>();
		org.mockito.Mockito.doAnswer(invocation -> {
			entityCallback.set(invocation.getArgument(1, Runnable.class));
			return null;
		}).when(scheduler).runTask(org.mockito.ArgumentMatchers.eq(plugin),
				org.mockito.ArgumentMatchers.any(Runnable.class), org.mockito.ArgumentMatchers.eq(player));

		try (MockedStatic<PlayerUtils> players = mockStatic(PlayerUtils.class)) {
			players.when(() -> PlayerUtils.getPlayerMeta(plugin, player, "UserGUI")).thenReturn("OfflineTarget");
			withResolvedEditorUser(player, "OfflineTarget", user -> { });
			failure.get().accept(new IllegalStateException("storage unavailable"));
			verify(player, org.mockito.Mockito.never()).sendMessage(org.mockito.ArgumentMatchers.anyString());
			entityCallback.get().run();
		}

		verify(scheduler).runTask(plugin, entityCallback.get(), player);
		verify(player).sendMessage("Unable to resolve user; check the server log.");
	}
}
