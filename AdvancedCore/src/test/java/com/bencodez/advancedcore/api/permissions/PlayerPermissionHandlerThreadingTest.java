package com.bencodez.advancedcore.api.permissions;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.UUID;

import org.bukkit.permissions.PermissionAttachment;
import org.junit.jupiter.api.Test;

import com.bencodez.simpleapi.time.ParsedDuration;

class PlayerPermissionHandlerThreadingTest {

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

		verify(manager).removePermission(uuid);
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
