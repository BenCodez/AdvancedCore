package com.bencodez.advancedcore.core.platform;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Game-thread handoff only; no executor, persistent queue, or retry ownership.
 * The platform/plugin owns cancellation on disable. Scheduling is not completion:
 * accepted work can be retired on shutdown or disconnect. These void/boolean
 * methods must not be used as durable reward-completion acknowledgements.
 */
public interface PlatformScheduler {
    /** Queues server-wide work. This does not authorize access to player/world state. */
    void runServer(Runnable task);

    /**
     * Queues server-wide work using the existing scheduler's seconds-based delay.
     * This is not a tick count or a hard wall-clock deadline. Negative delays fail.
     */
    void runServerLater(Runnable task, long delaySeconds);

    /**
     * Queues work on the current online session's owning entity/game thread.
     * Async callers may locate the entity for scheduling, but must defer player
     * state reads (including online checks) to the owning-thread callback.
     * Returns false when UUID lookup finds no player; true means only submitted,
     * not that the captured session is still online. A disconnected/replaced
     * session must never fall back to a global callback or transfer the action
     * to a new login. The callback may therefore not run. Rejection exceptions
     * propagate; task failures belong to the native scheduler. Do not block on,
     * retry, or acknowledge rewards from this boolean.
     */
    boolean runPlayer(UUID playerId, Consumer<PlatformPlayer> task);
}
