package com.bencodez.advancedcore.listeners;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.permissions.PermissionHandler;

class PlayerJoinEventSessionTest {
	@Test
	void delayedLoginDoesNotRestorePermissionsAfterThePlayerQuit() {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class, RETURNS_DEEP_STUBS);
		ScheduledExecutorService loginTimer = mock(ScheduledExecutorService.class);
		PermissionHandler permissions = mock(PermissionHandler.class);
		when(plugin.isEnabled()).thenReturn(true);
		when(plugin.isLoadUserData()).thenReturn(true);
		when(plugin.getOptions().isHideLoginMessage()).thenReturn(true);
		when(plugin.getLoginTimer()).thenReturn(loginTimer);
		when(plugin.getPermissionHandler()).thenReturn(permissions);
		Player player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());
		when(player.getName()).thenReturn("Player");
		org.bukkit.event.player.PlayerJoinEvent join = mock(org.bukkit.event.player.PlayerJoinEvent.class);
		when(join.getPlayer()).thenReturn(player);
		org.bukkit.event.player.PlayerQuitEvent quit = mock(org.bukkit.event.player.PlayerQuitEvent.class);
		when(quit.getPlayer()).thenReturn(player);
		ArgumentCaptor<Runnable> delayed = ArgumentCaptor.forClass(Runnable.class);

		PlayerJoinEvent listener = new PlayerJoinEvent(plugin);
		listener.onPlayerLogin(join);
		verify(loginTimer).schedule(delayed.capture(), anyLong(), eq(TimeUnit.MILLISECONDS));
		listener.onPlayerQuit(quit);
		delayed.getValue().run();

		verify(permissions).logout(player);
		verify(permissions, never()).login(any());
	}
}
