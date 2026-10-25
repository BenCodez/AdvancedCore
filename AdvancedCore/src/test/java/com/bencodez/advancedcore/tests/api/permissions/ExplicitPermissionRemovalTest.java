package com.bencodez.advancedcore.tests.api.permissions;

import static com.bencodez.advancedcore.tests.api.permissions.PermissionAccess.expirePermission;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.entity.Player;
import org.bukkit.permissions.Permissible;
import org.bukkit.permissions.PermissionAttachment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.permissions.PermissionHandler;
import com.bencodez.advancedcore.api.permissions.PlayerPermissionHandler;
import com.bencodez.simpleapi.time.ParsedDuration;

/** Exercises the public removal route with real Bukkit attachment copies and manager maps. */
class ExplicitPermissionRemovalTest {
    private AdvancedCorePlugin plugin;
    private PermissionHandler manager;
    private final UUID uuid = UUID.randomUUID();
    private final List<Permissible> permissibles = new ArrayList<>();

    @BeforeEach
    void setUp() {
        plugin = mock(AdvancedCorePlugin.class, RETURNS_DEEP_STUBS);
        when(plugin.isEnabled()).thenReturn(true);
        when(plugin.getServerDataFile().getData()).thenReturn(null);
        manager = new PermissionHandler(plugin);
    }

    @AfterEach
    void close() throws InterruptedException {
        manager.getTimer().shutdownNow();
        assertTrue(manager.getTimer().awaitTermination(2, TimeUnit.SECONDS));
    }

    private PermissionAttachment attachment() {
        Permissible permissible = mock(Permissible.class);
        permissibles.add(permissible);
        return new PermissionAttachment(plugin, permissible);
    }

    private PlayerPermissionHandler online(PermissionAttachment attachment) {
        PlayerPermissionHandler handler = new PlayerPermissionHandler(uuid, attachment, manager);
        manager.getPerms().put(uuid, handler);
        return handler;
    }

    @Test
    void removingTheOnlyTimedGrantUnsetsItsLiveNodeAndReclaimsTheHandler() {
        assertSoleGrantRemoval(true);
    }

    @Test
    void removingTheOnlyPermanentGrantUnsetsItsLiveNodeAndReclaimsTheHandler() {
        assertSoleGrantRemoval(false);
    }

    private void assertSoleGrantRemoval(boolean timed) {
        PermissionAttachment attachment = attachment();
        PlayerPermissionHandler handler = online(attachment);
        if (timed) handler.addExpiration("example.use", ParsedDuration.ofMillis(60_000));
        else handler.addPerm("example.use");
        clearInvocations(permissibles.getFirst());

        manager.removePermission(uuid, null, "example.use");

        assertTrue(attachment.getPermissions().isEmpty());
        assertTrue(handler.getPersistentPermissions().isEmpty());
        assertTrue(handler.getTimedPermissions() == null || handler.getTimedPermissions().isEmpty());
        assertFalse(manager.getPerms().containsKey(uuid));
        assertFalse(manager.getPermsToAdd().containsKey(uuid));
        verify(permissibles.getFirst()).recalculatePermissions();
    }

    @Test
    void removingOneGrantPreservesUnrelatedLiveAndTrackedGrants() {
        PermissionAttachment attachment = attachment();
        PlayerPermissionHandler handler = online(attachment);
        handler.addPerm("removed.use").addPerm("remaining.use");

        manager.removePermission(uuid, null, "removed.use");

        assertEquals(Map.of("remaining.use", true), attachment.getPermissions());
        assertEquals(java.util.Set.of("remaining.use"), handler.getPersistentPermissions());
        assertSame(handler, manager.getPerms().get(uuid));
    }

    @Test
    void removingUnknownPermissionDoesNotIntroduceADenial() {
        PermissionAttachment attachment = attachment();
        PlayerPermissionHandler handler = online(attachment).addPerm("remaining.use");

        manager.removePermission(uuid, null, "unknown.use");

        assertEquals(Map.of("remaining.use", true), attachment.getPermissions());
        assertSame(handler, manager.getPerms().get(uuid));
    }

    @Test
    void removingPipeSeparatedGrantsReclaimsTheSameHandler() {
        PermissionAttachment attachment = attachment();
        online(attachment).addPerm("first.use").addPerm("second.use");

        manager.removePermission(uuid, "SyntheticPlayer", "first.use|second.use");

        assertTrue(attachment.getPermissions().isEmpty());
        assertFalse(manager.getPerms().containsKey(uuid));
    }

    @Test
    void queuedOfflineRemovalDoesNotResurrectTheGrantOnLogin() {
        PlayerPermissionHandler handler = new PlayerPermissionHandler(uuid, null, manager)
                .addOfflinePerm("removed.use", ParsedDuration.ofMillis(60_000))
                .addOfflinePerm("remaining.use", ParsedDuration.empty());
        manager.getPermsToAdd().put(uuid, handler);

        manager.removePermission(uuid, null, "removed.use");

        assertSame(handler, manager.getPermsToAdd().get(uuid));
        PermissionAttachment attachment = attachment();
        handler.setAttachment(attachment);
        handler.onLogin(mock(Player.class));
        assertEquals(Map.of("remaining.use", true), attachment.getPermissions());
        assertTrue(handler.getTimedPermissions() == null || handler.getTimedPermissions().isEmpty());
    }

    @Test
    void removingTheOnlyQueuedOfflineGrantReclaimsItsManagerEntry() {
        PlayerPermissionHandler handler = new PlayerPermissionHandler(uuid, null, manager)
                .addOfflinePerm("removed.use", ParsedDuration.empty());
        manager.getPermsToAdd().put(uuid, handler);

        manager.removePermission(uuid, null, "removed.use");

        assertFalse(manager.getPermsToAdd().containsKey(uuid));
        PermissionAttachment attachment = attachment();
        handler.setAttachment(attachment);
        handler.onLogin(mock(Player.class));
        assertTrue(attachment.getPermissions().isEmpty());
    }

    @Test
    void staleExpirationAfterRemovalCannotAffectAReplacementGrant() {
        PermissionAttachment oldAttachment = attachment();
        PlayerPermissionHandler old = online(oldAttachment)
                .addExpiration("example.use", ParsedDuration.ofMillis(60_000));
        long oldExpiry = old.getTimedPermissions().get("example.use");
        manager.removePermission(uuid, null, "example.use");
        PermissionAttachment replacementAttachment = attachment();
        PlayerPermissionHandler replacement = online(replacementAttachment)
                .addExpiration("example.use", ParsedDuration.ofMillis(120_000));
        long replacementExpiry = replacement.getTimedPermissions().get("example.use");

        expirePermission(old, "example.use", oldExpiry, true);

        assertSame(replacement, manager.getPerms().get(uuid));
        assertEquals(replacementExpiry, replacement.getTimedPermissions().get("example.use"));
        assertEquals(Map.of("example.use", true), replacementAttachment.getPermissions());
    }

    @Test
    void cleanupCannotRemoveAReplacementPublishedDuringAttachmentRecalculation() {
        PermissionAttachment oldAttachment = attachment();
        PlayerPermissionHandler old = online(oldAttachment).addPerm("old.use");
        PlayerPermissionHandler replacement = new PlayerPermissionHandler(uuid, attachment(), manager)
                .addPerm("replacement.use");
        AtomicBoolean replace = new AtomicBoolean(true);
        doAnswer(call -> {
            if (replace.compareAndSet(true, false)) manager.getPerms().put(uuid, replacement);
            return null;
        }).when(permissibles.getFirst()).recalculatePermissions();

        manager.removePermission(uuid, null, "old.use");

        assertSame(replacement, manager.getPerms().get(uuid));
        assertTrue(old.getPersistentPermissions().isEmpty());
        assertEquals(Map.of("replacement.use", true), replacement.getAttachment().getPermissions());
    }
}
