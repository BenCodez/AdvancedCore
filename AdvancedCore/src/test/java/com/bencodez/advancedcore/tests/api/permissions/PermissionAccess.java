package com.bencodez.advancedcore.tests.api.permissions;

import java.util.UUID;

import com.bencodez.advancedcore.api.permissions.PermissionHandler;
import com.bencodez.advancedcore.api.permissions.PlayerPermissionHandler;
import com.bencodez.advancedcore.tests.support.ReflectiveAccess;

/** Typed calls also support Mockito stubbing and verification of the original methods. */
final class PermissionAccess {
    private PermissionAccess() { }

    static boolean isExpirationCurrent(PlayerPermissionHandler handler, String permission, long expiry) {
        return ReflectiveAccess.invoke(PlayerPermissionHandler.class, handler, "isExpirationCurrent",
                new Class<?>[] { String.class, long.class }, permission, expiry);
    }

    static void expirePermission(PlayerPermissionHandler handler, String permission, long expiry, boolean attachment) {
        ReflectiveAccess.invoke(PlayerPermissionHandler.class, handler, "expirePermission",
                new Class<?>[] { String.class, long.class, boolean.class }, permission, expiry, attachment);
    }

    static void scheduleExpiration(PermissionHandler handler, PlayerPermissionHandler player, String permission,
            long expiry, long delay) {
        ReflectiveAccess.invoke(PermissionHandler.class, handler, "scheduleExpiration",
                new Class<?>[] { PlayerPermissionHandler.class, String.class, long.class, long.class },
                player, permission, expiry, delay);
    }

    static void dispatchExpiration(PermissionHandler handler, PlayerPermissionHandler player, String permission, long expiry) {
        ReflectiveAccess.invoke(PermissionHandler.class, handler, "dispatchExpiration",
                new Class<?>[] { PlayerPermissionHandler.class, String.class, long.class }, player, permission, expiry);
    }

    static void removePermissionIfEmpty(PermissionHandler handler, UUID uuid, PlayerPermissionHandler player,
            boolean offline) {
        ReflectiveAccess.invoke(PermissionHandler.class, handler, "removePermissionIfEmpty",
                new Class<?>[] { UUID.class, PlayerPermissionHandler.class, boolean.class }, uuid, player, offline);
    }
}
