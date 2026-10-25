package com.bencodez.advancedcore.tests.api.permissions;

import static com.bencodez.advancedcore.tests.api.permissions.PermissionAccess.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissibleBase;
import org.bukkit.permissions.PermissionAttachment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.permissions.PermissionHandler;
import com.bencodez.advancedcore.api.permissions.PlayerPermissionHandler;
import com.bencodez.simpleapi.time.ParsedDuration;

/** Real Bukkit registration catches empty attachments invisible to effective-permission assertions. */
class PermissionAttachmentRetirementTest {
    private final UUID uuid = UUID.randomUUID();
    private MockedStatic<Bukkit> bukkit;
    private AdvancedCorePlugin plugin;
    private PermissionHandler manager;
    private PermissibleBase permissible;
    private Player player;

    @BeforeEach
    void setUp() {
        plugin = mock(AdvancedCorePlugin.class, RETURNS_DEEP_STUBS);
        when(plugin.isEnabled()).thenReturn(true);
        when(plugin.getServerDataFile().getData()).thenReturn(null);
        Server server = mock(Server.class, RETURNS_DEEP_STUBS);
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getServer).thenReturn(server);
        bukkit.when(() -> Bukkit.getPlayer(uuid)).thenReturn(player);
        permissible = new PermissibleBase(null);
        when(player.addAttachment(plugin)).thenAnswer(call -> permissible.addAttachment(plugin));
        doAnswer(call -> { permissible.removeAttachment(call.getArgument(0)); return null; })
                .when(player).removeAttachment(any(PermissionAttachment.class));
        manager = new PermissionHandler(plugin);
    }

    @AfterEach
    void close() throws InterruptedException {
        try {
            if (manager != null) {
                manager.getTimer().shutdownNow();
                assertTrue(manager.getTimer().awaitTermination(2, TimeUnit.SECONDS));
            }
        } finally {
            if (bukkit != null) bukkit.close();
        }
    }

    private List<?> registeredAttachments() throws ReflectiveOperationException {
        var field = PermissibleBase.class.getDeclaredField("attachments");
        field.setAccessible(true);
        return List.copyOf((List<?>) field.get(permissible));
    }

    @Test
    void repeatedPermanentAndTimedPublicRemovalsReleaseRegisteredAttachments() throws Exception {
        for (ParsedDuration duration : List.of(ParsedDuration.empty(), ParsedDuration.ofMillis(60_000))) {
            for (int cycle = 0; cycle < 3; cycle++) {
                manager.addPermission(uuid, "example.use", duration);
                PlayerPermissionHandler old = manager.getPerms().get(uuid);
                assertEquals(1, registeredAttachments().size());
                assertTrue(permissible.hasPermission("example.use"));

                manager.removePermission(uuid, null, "example.use");

                assertTrue(registeredAttachments().isEmpty(), "empty attachments must not accumulate");
                assertFalse(permissible.isPermissionSet("example.use"));
                assertFalse(manager.getPerms().containsKey(uuid));
                assertNull(old.getAttachment());
            }
        }
    }

    @Test
    void removalPreservesAnotherAttachmentsGrantInsteadOfInstallingADenial() throws Exception {
        PermissionAttachment other = permissible.addAttachment(plugin, "example.use", true);
        manager.addPermission(uuid, "example.use");

        manager.removePermission(uuid, null, "example.use");

        assertEquals(List.of(other), registeredAttachments());
        assertTrue(permissible.hasPermission("example.use"));
    }

    @Test
    void removingOneOfTwoGrantsKeepsTheRegisteredAttachment() throws Exception {
        manager.addPermission(uuid, "removed.use|remaining.use");
        PlayerPermissionHandler current = manager.getPerms().get(uuid);

        manager.removePermission(uuid, null, "removed.use");

        assertEquals(List.of(current.getAttachment()), registeredAttachments());
        assertSame(current, manager.getPerms().get(uuid));
        assertTrue(permissible.hasPermission("remaining.use"));
        assertFalse(permissible.isPermissionSet("removed.use"));
    }

    @Test
    void owningContextExpirationAlsoReleasesItsEmptyAttachment() throws Exception {
        manager.addPermission(uuid, "example.use", ParsedDuration.ofMillis(60_000));
        PlayerPermissionHandler old = manager.getPerms().get(uuid);
        long expired = System.currentTimeMillis() - 1;
        old.getTimedPermissions().put("example.use", expired);

        expirePermission(old, "example.use", expired, true);

        assertTrue(registeredAttachments().isEmpty());
        assertNull(old.getAttachment());
        assertFalse(manager.getPerms().containsKey(uuid));
    }

    @Test
    void offlineExpirationNeverDetachesAnAttachmentFromTheWrongContext() throws Exception {
        manager.addPermission(uuid, "example.use", ParsedDuration.ofMillis(60_000));
        PlayerPermissionHandler old = manager.getPerms().remove(uuid);
        PermissionAttachment attachment = old.getAttachment();
        manager.getPermsToAdd().put(uuid, old);
        long expired = System.currentTimeMillis() - 1;
        old.getTimedPermissions().put("example.use", expired);

        expirePermission(old, "example.use", expired, false);

        assertEquals(List.of(attachment), registeredAttachments());
        assertTrue(permissible.hasPermission("example.use"));
        assertFalse(manager.getPermsToAdd().containsKey(uuid));
    }

    @Test
    void aRemovalCallbackCanGrantThroughANewTrackedAttachment() throws Exception {
        manager.addPermission(uuid, "removed.use");
        PlayerPermissionHandler old = manager.getPerms().get(uuid);
        old.getAttachment().setRemovalCallback(ignored -> manager.addPermission(uuid, "replacement.use"));

        manager.removePermission(uuid, null, "removed.use");

        PlayerPermissionHandler replacement = manager.getPerms().get(uuid);
        assertNotNull(replacement);
        assertNotSame(old, replacement);
        assertNull(old.getAttachment());
        assertEquals(List.of(replacement.getAttachment()), registeredAttachments());
        assertTrue(permissible.hasPermission("replacement.use"));
        assertFalse(permissible.isPermissionSet("removed.use"));
    }

    @Test
    void staleHandlerCleanupCannotDetachAReplacementSession() throws Exception {
        PlayerPermissionHandler old = new PlayerPermissionHandler(uuid, null, manager);
        manager.addPermission(uuid, "replacement.use");
        PlayerPermissionHandler replacement = manager.getPerms().get(uuid);
        // Even a stale wrapper retaining the new attachment must not retire its current owner.
        old.setAttachment(replacement.getAttachment());
        replacement.getAttachment().unsetPermission("replacement.use");

        removePermissionIfEmpty(manager, uuid, old, false);

        assertSame(replacement, manager.getPerms().get(uuid));
        assertEquals(List.of(replacement.getAttachment()), registeredAttachments());
    }
}
