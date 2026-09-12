package com.bencodez.advancedcore.core.platform;

import java.util.Optional;
import java.util.UUID;

/** Minimal game services used by shared code; no Bukkit or mod-loader types. */
public interface PlatformServices {
    /**
     * Read-only UUID lookup; never creates a persistent/offline user or cache.
     * Use the returned session only on its owning game thread. For asynchronous
     * callers, prefer {@link PlatformScheduler#runPlayer} for lookup plus handoff.
     */
    Optional<PlatformPlayer> findOnlinePlayer(UUID playerId);

    PlatformScheduler scheduler();

    /** Native synchronous dispatch; call from server-wide game context, not async. */
    boolean dispatchConsoleCommand(String command);

    /** Sends already-rendered text from server-wide game context. */
    void sendConsoleMessage(String message);
}
