package com.bencodez.advancedcore.core.platform;

import java.util.UUID;

/**
 * A live online-player session, not a persistent user or an offline identity.
 * Implementations must not redirect a retained handle to a replacement session.
 * Except for the UUID, use this handle only on its owning game thread (normally
 * inside {@link PlatformScheduler#runPlayer}). Do not retain it across callbacks.
 */
public interface PlatformPlayer {
    UUID getUniqueId();

    String getName();

    boolean isOnline();

    /** Checks one literal native permission node; no offline/Vault fallback. */
    boolean hasPermission(String permission);

    /** Sends already-rendered text; no placeholders, scripts, or color parsing. */
    void sendMessage(String message);

    /**
     * Runs an already-rendered command as this player, without granting operator
     * privileges. The caller supplies the native command line without a slash.
     * Returns the native dispatch result, not a durable reward acknowledgement.
     */
    boolean performCommand(String command);
}
