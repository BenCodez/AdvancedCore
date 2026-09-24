package com.bencodez.advancedcore.api.permissions;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.simpleapi.scheduler.BukkitScheduler;
import com.bencodez.simpleapi.time.ParsedDuration;

class PlayerPermissionHandlerThreadingTest {
	@Test
	void offlineGrantsReuseTheHandlerPreservedByLogout() {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class, RETURNS_DEEP_STUBS);
		when(plugin.getServerDataFile().getData()).thenReturn(null);
		PermissionHandler manager = new PermissionHandler(plugin);
		UUID uuid = UUID.randomUUID();
		PlayerPermissionHandler preserved = new PlayerPermissionHandler(uuid, null, manager)
				.addOfflinePerm("existing.use", ParsedDuration.empty());
		manager.getPermsToAdd().put(uuid, preserved);

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getPlayer(uuid)).thenReturn(null);
			manager.addPermission(uuid, "new.use");
			assertSame(preserved, manager.getPermsToAdd().get(uuid));
			PermissionAttachment attachment = mock(PermissionAttachment.class);
			preserved.setAttachment(attachment);
			preserved.onLogin(mock(Player.class));
			verify(attachment).setPermission("existing.use", true);
			verify(attachment).setPermission("new.use", true);
		} finally { manager.getTimer().shutdownNow(); }
	}

	@Test
	void entitySchedulerRejectionRetriesCurrentExpiration() {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class, RETURNS_DEEP_STUBS);
		when(plugin.getServerDataFile().getData()).thenReturn(null);
		BukkitScheduler scheduler = mock(BukkitScheduler.class);
		when(plugin.getBukkitScheduler()).thenReturn(scheduler);
		PermissionHandler manager = spy(new PermissionHandler(plugin));
		PlayerPermissionHandler handle = mock(PlayerPermissionHandler.class);
		UUID uuid = UUID.randomUUID();
		long expiry = System.currentTimeMillis();
		when(handle.getUuid()).thenReturn(uuid);
		when(handle.isExpirationCurrent("example.use", expiry)).thenReturn(true);
		Player player = mock(Player.class);
		doAnswer(call -> { ((Runnable) call.getArgument(1)).run(); return null; })
				.when(scheduler).runTask(eq(plugin), any(Runnable.class));
		doThrow(new IllegalStateException("entity retired"))
				.when(scheduler).runTask(eq(plugin), any(Runnable.class), eq(player));
		doNothing().when(manager).scheduleExpiration(handle, "example.use", expiry, 1_000L);

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(Bukkit::getServer).thenReturn(mock(Server.class));
			bukkit.when(() -> Bukkit.getPlayer(uuid)).thenReturn(player);
			manager.dispatchExpiration(handle, "example.use", expiry);
			verify(manager).scheduleExpiration(handle, "example.use", expiry, 1_000L);
			verify(handle, never()).expirePermission(anyString(), anyLong(), anyBoolean());
		} finally { manager.getTimer().shutdownNow(); }
	}

	@Test
	void staleExpirationCannotRevokeExtendedPermission() {
		PermissionHandler manager = mock(PermissionHandler.class);
		PermissionAttachment attachment = mock(PermissionAttachment.class);
		when(attachment.getPermissions()).thenReturn(new java.util.LinkedHashMap<>());
		PlayerPermissionHandler handler = new PlayerPermissionHandler(UUID.randomUUID(), attachment, manager);

		handler.addExpiration("example.use", ParsedDuration.ofMillis(60_000));
		long firstExpiry = handler.getTimedPermissions().get("example.use");
		handler.addExpiration("example.use", ParsedDuration.ofMillis(120_000));
		long extendedExpiry = handler.getTimedPermissions().get("example.use");
		assertTrue(extendedExpiry >= firstExpiry);
		clearInvocations(attachment);

		handler.expirePermission("example.use", firstExpiry, true);

		assertEquals(extendedExpiry, handler.getTimedPermissions().get("example.use"));
		verify(attachment, never()).unsetPermission("example.use");
	}

	@Test
	void offOwnerExpirationUpdatesStateWithoutTouchingAttachment() {
		PermissionHandler manager = mock(PermissionHandler.class);
		PermissionAttachment attachment = mock(PermissionAttachment.class);
		when(attachment.getPermissions()).thenReturn(new java.util.LinkedHashMap<>());
		PlayerPermissionHandler handler = new PlayerPermissionHandler(UUID.randomUUID(), attachment, manager);

		handler.addExpiration("example.use", ParsedDuration.ofMillis(60_000));
		long expiry = handler.getTimedPermissions().get("example.use");
		clearInvocations(attachment);
		handler.expirePermission("example.use", expiry, false);

		assertFalse(handler.getTimedPermissions().containsKey("example.use"));
		verify(attachment, never()).unsetPermission(anyString());
		verify(attachment, never()).setPermission(anyString(), anyBoolean());
	}

	@Test
	void offlineExpirationDropsEmptyHandlerDespiteStaleAttachmentState() {
		PermissionHandler manager = mock(PermissionHandler.class);
		PermissionAttachment attachment = mock(PermissionAttachment.class);
		when(attachment.getPermissions()).thenReturn(java.util.Map.of("example.use", true));
		UUID uuid = UUID.randomUUID();
		PlayerPermissionHandler handler = new PlayerPermissionHandler(uuid, attachment, manager);
		handler.addExpiration("example.use", ParsedDuration.ofMillis(60_000));
		long expiry = handler.getTimedPermissions().get("example.use");
		clearInvocations(attachment);

		handler.expirePermission("example.use", expiry, false);

		verify(manager).removePermission(uuid, handler);
		verify(attachment, never()).unsetPermission(anyString());
		verify(attachment, never()).setPermission(anyString(), anyBoolean());
	}

	@Test
	void expirationClearsAttachmentEntryInsteadOfInstallingDenial() {
		PermissionHandler manager = mock(PermissionHandler.class);
		PermissionAttachment attachment = mock(PermissionAttachment.class);
		when(attachment.getPermissions()).thenReturn(new java.util.LinkedHashMap<>());
		PlayerPermissionHandler handler = new PlayerPermissionHandler(UUID.randomUUID(), attachment, manager);
		handler.addExpiration("example.use", ParsedDuration.ofMillis(60_000));
		long expiry = handler.getTimedPermissions().get("example.use");
		clearInvocations(attachment);
		handler.expirePermission("example.use", expiry, true);
		verify(attachment).unsetPermission("example.use");
		verify(attachment, never()).setPermission("example.use", false);
	}

}
