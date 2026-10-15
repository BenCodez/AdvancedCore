package com.bencodez.advancedcore.api.user.presence;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.List;
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
    private record BatchRequest(BatchPlayerPresenceLookup.Query query, CompletableFuture<PlayerPresence> result) { }
    private final ArrayDeque<BatchRequest> queuedBatch = new ArrayDeque<>();
    private boolean batchScheduled;
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
        submit(identity, result);
        return result.minimalCompletionStage();
    }

    /** Check up to eight verified current/historical UUIDs for the same identity. */
    public CompletionStage<PlayerPresence> checkIdentitiesAsync(Collection<UUID> identities) {
        return checkIdentitiesAsync(identities, false);
    }

    /** Require retained offline-history scope proof when current names cannot enumerate old migrations. */
    public CompletionStage<PlayerPresence> checkIdentitiesAsync(Collection<UUID> identities,
            boolean requireCompleteOnlineHistory) {
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
        var results = new ArrayList<CompletableFuture<PlayerPresence>>(distinct.size());
        synchronized (admission) {
            if (closed || MAX_PENDING - pending.size() < 1 + distinct.size())
                return CompletableFuture.completedStage(PlayerPresence.UNKNOWN);
            // Reserve the aggregate and every child before scheduling any lookup.
            // Concurrent requests cannot turn an admitted family into partial work.
            pending.put(aggregate, Boolean.TRUE);
            for (UUID ignored : distinct) {
                var child = new CompletableFuture<PlayerPresence>();
                pending.put(child, Boolean.TRUE);
                results.add(child);
            }
        }
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
        int index = 0;
        for (UUID identity : distinct) submit(identity, results.get(index++), requireCompleteOnlineHistory);
        return aggregate.minimalCompletionStage();
    }

    private void submit(UUID identity, CompletableFuture<PlayerPresence> result) {
        submit(identity, result, false);
    }

    private void submit(UUID identity, CompletableFuture<PlayerPresence> result, boolean completeOnlineHistory) {
        if (lookup instanceof BatchPlayerPresenceLookup batch) {
            var request = new BatchRequest(new BatchPlayerPresenceLookup.Query(identity, completeOnlineHistory), result);
            try {
                // Registration must reach its own place on the shared worker.
                // Joining a previously queued read must never overtake persistence.
                worker.execute(() -> registerBatch(request, batch));
            } catch (RuntimeException rejected) {
                finish(result, PlayerPresence.UNKNOWN);
            }
            return;
        }
        try {
            worker.execute(() -> completeLookup(identity, result, completeOnlineHistory));
        } catch (RuntimeException rejected) {
            finish(result, PlayerPresence.UNKNOWN);
        }
    }

    private void registerBatch(BatchRequest request, BatchPlayerPresenceLookup batch) {
        synchronized (admission) {
            if (closed || !pending.containsKey(request.result())) return;
            queuedBatch.add(request);
            if (batchScheduled) return;
            batchScheduled = true;
        }
        try {
            // Yield behind already queued storage and registrations before reading.
            worker.execute(() -> completeBatch(batch));
        } catch (RuntimeException rejected) {
            ArrayList<BatchRequest> retiring;
            synchronized (admission) {
                retiring = new ArrayList<>(queuedBatch);
                queuedBatch.clear();
                batchScheduled = false;
            }
            for (var entry : retiring) finish(entry.result(), PlayerPresence.UNKNOWN);
        }
    }

    private void completeBatch(BatchPlayerPresenceLookup batch) {
        ArrayList<BatchRequest> cohort;
        synchronized (admission) {
            if (closed) return;
            cohort = new ArrayList<>(queuedBatch);
            queuedBatch.clear();
            batchScheduled = false;
        }
        List<PlayerPresence> evidence = null;
        try {
            evidence = batch.checkBatch(cohort.stream().map(BatchRequest::query).toList());
            if (evidence == null || evidence.size() != cohort.size()) evidence = null;
        } catch (Exception failed) {
            if (failed instanceof InterruptedException) Thread.currentThread().interrupt();
        }
        for (int index = 0; index < cohort.size(); index++) {
            PlayerPresence result = evidence == null ? null : evidence.get(index);
            finish(cohort.get(index).result(), result == null ? PlayerPresence.UNKNOWN : result);
        }
    }

    private void completeLookup(UUID identity, CompletableFuture<PlayerPresence> result, boolean completeOnlineHistory) {
        synchronized (admission) {
            // Queued retired work never touches the provider or replacement state.
            if (closed || !pending.containsKey(result)) return;
        }
        PlayerPresence evidence;
        try {
            evidence = completeOnlineHistory ? lookup.checkOnlineHistory(identity) : lookup.check(identity);
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
            queuedBatch.clear();
        }
        for (CompletableFuture<PlayerPresence> result : retiring)
            result.complete(PlayerPresence.UNKNOWN);
    }
}
