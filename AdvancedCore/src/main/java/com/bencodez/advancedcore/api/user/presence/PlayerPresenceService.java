package com.bencodez.advancedcore.api.user.presence;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

/**
 * Bounded asynchronous checked history. The supplied executor belongs to the
 * existing persistence runtime; this service creates no worker or storage.
 * Completions can run on the worker or the requesting/retiring thread. Callers
 * must schedule platform effects explicitly, never attach blocking callbacks.
 */
public final class PlayerPresenceService implements AutoCloseable {
    private static final int MAX_PENDING = 128;
    private final Executor worker;
    private final PlayerPresenceLookup lookup;
    private final Object admission = new Object();
    private final IdentityHashMap<CompletableFuture<PlayerPresence>, Boolean> pending = new IdentityHashMap<>();
    private boolean closed;

    public PlayerPresenceService(Executor worker, PlayerPresenceLookup lookup) {
        this.worker = Objects.requireNonNull(worker, "worker");
        this.lookup = Objects.requireNonNull(lookup, "lookup");
    }

    /** No name/UUID resolution, storage or Bukkit call is performed by this method. */
    public CompletionStage<PlayerPresence> checkAsync(UUID identity) {
        CompletableFuture<PlayerPresence> result = new CompletableFuture<>();
        if (identity == null) return CompletableFuture.completedStage(PlayerPresence.UNKNOWN);
        synchronized (admission) {
            if (closed || pending.size() >= MAX_PENDING)
                return CompletableFuture.completedStage(PlayerPresence.UNKNOWN);
            pending.put(result, Boolean.TRUE);
        }
        try {
            worker.execute(() -> completeLookup(identity, result));
        } catch (RuntimeException rejected) {
            finish(result, PlayerPresence.UNKNOWN);
        }
        return result.minimalCompletionStage();
    }

    /** Check up to eight verified current/historical UUIDs for the same identity. */
    public CompletionStage<PlayerPresence> checkIdentitiesAsync(Collection<UUID> identities) {
        LinkedHashSet<UUID> distinct = new LinkedHashSet<>();
        if (identities == null) return CompletableFuture.completedStage(PlayerPresence.UNKNOWN);
        try {
            int examined = 0;
            for (UUID identity : identities) {
                // Bound iteration too: a very large duplicate collection isn't free.
                if (++examined > 8 || identity == null)
                    return CompletableFuture.completedStage(PlayerPresence.UNKNOWN);
                distinct.add(identity);
            }
        } catch (RuntimeException unavailable) {
            return CompletableFuture.completedStage(PlayerPresence.UNKNOWN);
        }
        if (distinct.isEmpty()) return CompletableFuture.completedStage(PlayerPresence.UNKNOWN);
        CompletableFuture<PlayerPresence> aggregate = new CompletableFuture<>();
        synchronized (admission) {
            if (closed || pending.size() >= MAX_PENDING)
                return CompletableFuture.completedStage(PlayerPresence.UNKNOWN);
            // The public aggregate owns admission until every alias has settled.
            // A completed child cannot outlive retirement of this request.
            pending.put(aggregate, Boolean.TRUE);
        }
        var results = distinct.stream().map(this::checkAsync).map(CompletionStage::toCompletableFuture).toList();
        CompletableFuture.allOf(results.toArray(CompletableFuture[]::new)).thenRun(() -> {
            boolean unknown = false;
            for (var result : results) {
                PlayerPresence evidence = result.join(); // All reads have already completed.
                if (evidence == PlayerPresence.JOINED) {
                    finish(aggregate, PlayerPresence.JOINED);
                    return;
                }
                unknown |= evidence == PlayerPresence.UNKNOWN;
            }
            finish(aggregate, unknown ? PlayerPresence.UNKNOWN : PlayerPresence.NEVER_JOINED);
        });
        return aggregate.minimalCompletionStage();
    }

    private void completeLookup(UUID identity, CompletableFuture<PlayerPresence> result) {
        synchronized (admission) {
            // Queued retired work never touches the provider or replacement state.
            if (closed || !pending.containsKey(result)) return;
        }
        PlayerPresence evidence;
        try {
            evidence = lookup.check(identity);
            if (evidence == null) evidence = PlayerPresence.UNKNOWN;
        } catch (Exception failed) {
            if (failed instanceof InterruptedException) Thread.currentThread().interrupt();
            evidence = PlayerPresence.UNKNOWN;
        }
        finish(result, evidence);
    }

    private void finish(CompletableFuture<PlayerPresence> result, PlayerPresence evidence) {
        synchronized (admission) {
            if (pending.remove(result) == null) return;
            // Publish the terminal result before retirement can acquire admission.
            // Callbacks are reentrant and, by contract, must never block.
            if (closed) evidence = PlayerPresence.UNKNOWN;
            result.complete(evidence);
        }
    }

    /** Retire this generation without waiting for or shutting down its shared worker. */
    @Override
    public void close() {
        ArrayList<CompletableFuture<PlayerPresence>> retiring;
        synchronized (admission) {
            if (closed) return;
            closed = true;
            retiring = new ArrayList<>(pending.keySet());
            pending.clear();
        }
        for (CompletableFuture<PlayerPresence> result : retiring)
            result.complete(PlayerPresence.UNKNOWN);
    }
}
