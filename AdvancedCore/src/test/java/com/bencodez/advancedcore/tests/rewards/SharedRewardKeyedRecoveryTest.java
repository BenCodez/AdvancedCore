package com.bencodez.advancedcore.tests.rewards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.core.reward.SharedRewardActionClaim;
import com.bencodez.advancedcore.core.reward.SharedRewardContext;
import com.bencodez.advancedcore.core.reward.SharedRewardIndeterminateException;
import com.bencodez.advancedcore.core.reward.SharedRewardKeyedDurability;
import com.bencodez.advancedcore.core.reward.SharedRewardOrchestrator;
import com.bencodez.advancedcore.core.reward.SharedRewardPlan;
import com.bencodez.advancedcore.core.reward.SharedRewardPlatform;
import com.bencodez.advancedcore.core.reward.SharedRewardProgress;
import com.bencodez.advancedcore.core.reward.SharedRewardResult;
import com.bencodez.advancedcore.core.reward.SharedRewardStep;

class SharedRewardKeyedRecoveryTest {
    private static final UUID USER = UUID.fromString("fef273b7-aa45-42f9-ac14-cb047533afde");

    @Test
    void pendingClaimPrecedesNativeActionAndCheckpointClearsIt() {
        Store store = new Store();
        AtomicInteger actions = new AtomicInteger();
        SharedRewardPlan plan = plan(new SharedRewardStep("command", false, (context, path) -> {
            assertEquals(0, store.cursor("vote-a/vote"));
            assertEquals(0, store.pending.get("vote-a/vote"));
            actions.incrementAndGet();
            return done();
        }));

        assertEquals(SharedRewardResult.COMPLETED, execute(store, plan, "vote-a").join());
        assertEquals(1, actions.get());
        assertEquals(1, store.cursor("vote-a/vote"));
        assertNull(store.pending.get("vote-a/vote"));
        assertEquals(SharedRewardResult.COMPLETED, execute(store, plan, "vote-a").join());
        assertEquals(1, actions.get());
    }

    @Test
    void concurrentSubmissionCannotRunAnAlreadyClaimedAction() {
        Store store = new Store();
        AtomicInteger actions = new AtomicInteger();
        CompletableFuture<SharedRewardResult> effect = new CompletableFuture<>();
        SharedRewardPlan plan = plan(new SharedRewardStep("command", false, (context, path) -> {
            actions.incrementAndGet();
            return effect;
        }));

        CompletableFuture<SharedRewardResult> first = execute(store, plan, "vote-a");
        assertEquals(1, actions.get());
        CompletionException failure = assertThrows(CompletionException.class,
                () -> execute(store, plan, "vote-a").join());
        assertInstanceOf(SharedRewardIndeterminateException.class, failure.getCause());
        assertEquals(1, actions.get());

        effect.complete(SharedRewardResult.COMPLETED);
        assertEquals(SharedRewardResult.COMPLETED, first.join());
        assertEquals(SharedRewardResult.COMPLETED, execute(store, plan, "vote-a").join());
        assertEquals(1, actions.get());
    }

    @Test
    void keyedNestedPlanIsRejectedBeforeAnyChildSideEffect() {
        Store store = new Store();
        Platform platform = new Platform();
        SharedRewardOrchestrator orchestrator = new SharedRewardOrchestrator(platform);
        AtomicInteger nestedActions = new AtomicInteger();
        SharedRewardPlan nested = new SharedRewardPlan("child", 1, Duration.ZERO, List.of(),
                List.of(step("item", nestedActions)), "child-config-v1");
        SharedRewardPlan parent = plan(new SharedRewardStep("nested", false,
                (context, path) -> orchestrator.executeNested(nested, context, store, path)));

        assertThrows(CompletionException.class, () -> execute(platform, store, parent, "vote-a").join());
        assertEquals(0, nestedActions.get());
        assertEquals(0, store.completedSteps("vote-a/vote/nested:0/child"));
        assertNull(store.pending.get("vote-a/vote/nested:0/child"));
    }

    @Test
    void legacyNestedCallAcceptsAKeyedCapableAdapter() {
        Store store = new Store();
        SharedRewardPlan empty = new SharedRewardPlan("child", 1, Duration.ZERO, List.of(), List.of(), "empty");

        assertEquals(SharedRewardResult.COMPLETED, new SharedRewardOrchestrator(new Platform())
                .executeNested(empty, new SharedRewardContext(USER, "Ben", Map.of()), store, "legacy-parent")
                .toCompletableFuture().join());
    }

    @Test
    void disconnectWhileClaimIsPendingCannotStartOnlineAction() {
        Store store = new Store();
        Platform platform = new Platform();
        AtomicInteger actions = new AtomicInteger();
        CompletableFuture<SharedRewardActionClaim> claim = new CompletableFuture<>();
        store.delayedClaim = claim;
        SharedRewardPlan plan = plan(new SharedRewardStep("item", true, (context, path) -> {
            assertTrue(platform.nativeDispatch);
            actions.incrementAndGet();
            return done();
        }));

        CompletableFuture<SharedRewardResult> execution = execute(platform, store, plan, "vote-a");
        platform.online = false;
        claim.complete(SharedRewardActionClaim.STARTED);

        CompletionException failure = assertThrows(CompletionException.class, execution::join);
        assertInstanceOf(SharedRewardIndeterminateException.class, failure.getCause());
        assertEquals(0, actions.get());
        assertEquals(0, store.pending.get("vote-a/vote"));
    }

    @Test
    void shutdownWhileClaimIsPendingCannotStartNativeAction() {
        Store store = new Store();
        Platform platform = new Platform();
        AtomicInteger actions = new AtomicInteger();
        CompletableFuture<SharedRewardActionClaim> claim = new CompletableFuture<>();
        store.delayedClaim = claim;
        SharedRewardPlan plan = plan(step("command", actions));

        CompletableFuture<SharedRewardResult> execution = execute(platform, store, plan, "vote-a");
        platform.shuttingDown = true;
        claim.complete(SharedRewardActionClaim.STARTED);

        CompletionException failure = assertThrows(CompletionException.class, execution::join);
        assertInstanceOf(SharedRewardIndeterminateException.class, failure.getCause());
        assertEquals(0, actions.get());
        assertEquals(0, store.pending.get("vote-a/vote"));
        assertEquals(USER, platform.lastDispatchUser);
        assertEquals(false, platform.lastDispatchRequiresOnline);
    }

    @Test
    void completedNativeEffectWithFailedCheckpointIsIndeterminateOnRetry() {
        Store store = new Store();
        store.failCheckpoint = true;
        AtomicInteger actions = new AtomicInteger();
        SharedRewardPlan plan = plan(step("money", actions));

        assertThrows(CompletionException.class, () -> execute(store, plan, "vote-a").join());
        assertEquals(1, actions.get());
        assertEquals(0, store.cursor("vote-a/vote"));
        assertEquals(0, store.pending.get("vote-a/vote"));

        Store restarted = store.reopen();
        CompletionException failure = assertThrows(CompletionException.class,
                () -> execute(restarted, plan, "vote-a").join());
        assertInstanceOf(SharedRewardIndeterminateException.class, failure.getCause());
        assertEquals(1, actions.get());
    }

    @Test
    void lostCheckpointAcknowledgementRecoversCommittedPrefixWithoutRepeatingAction() {
        Store store = new Store();
        store.loseCheckpointAck = true;
        AtomicInteger actions = new AtomicInteger();
        SharedRewardPlan plan = plan(step("command", actions));

        assertThrows(CompletionException.class, () -> execute(store, plan, "vote-a").join());
        assertEquals(1, actions.get());
        assertEquals(1, store.cursor("vote-a/vote"));
        assertNull(store.pending.get("vote-a/vote"));
        assertEquals(SharedRewardResult.COMPLETED, execute(store.reopen(), plan, "vote-a").join());
        assertEquals(1, actions.get());
    }

    @Test
    void lostClaimAcknowledgementFailsClosedWithoutRunningAction() {
        Store store = new Store();
        store.loseClaimAck = true;
        AtomicInteger actions = new AtomicInteger();
        SharedRewardPlan plan = plan(step("item", actions));

        assertThrows(CompletionException.class, () -> execute(store, plan, "vote-a").join());
        assertEquals(0, actions.get());
        assertEquals(0, store.pending.get("vote-a/vote"));
        CompletionException failure = assertThrows(CompletionException.class,
                () -> execute(store.reopen(), plan, "vote-a").join());
        assertInstanceOf(SharedRewardIndeterminateException.class, failure.getCause());
        assertEquals(0, actions.get());
    }

    @Test
    void offlineDeferralDoesNotClaimActionAndDistinctOccurrencesDoNotCollide() {
        Store store = new Store();
        Platform platform = new Platform();
        platform.online = false;
        AtomicInteger actions = new AtomicInteger();
        SharedRewardPlan plan = plan(new SharedRewardStep("item", true, (context, path) -> {
            assertTrue(platform.nativeDispatch);
            actions.incrementAndGet();
            return done();
        }));

        assertEquals(SharedRewardResult.DEFERRED, execute(platform, store, plan, "vote-a").join());
        assertNull(store.pending.get("vote-a/vote"));
        platform.online = true;
        assertEquals(SharedRewardResult.COMPLETED, execute(platform, store, plan, "vote-a").join());
        assertEquals(SharedRewardResult.COMPLETED, execute(platform, store, plan, "vote-b").join());
        assertEquals(2, actions.get());
        assertEquals(5, platform.nativeDispatches);
        assertEquals(2, platform.claimedDispatches);
        assertEquals(USER, platform.lastDispatchUser);
        assertTrue(platform.lastDispatchRequiresOnline);
    }

    @Test
    void completedPrefixIsSkippedAndChangedPlanFailsBeforeSideEffects() {
        Store store = new Store();
        AtomicInteger first = new AtomicInteger(), second = new AtomicInteger();
        SharedRewardPlan original = plan(step("first", first), step("second", second));
        store.failAtStep = 1;
        assertThrows(CompletionException.class, () -> execute(store, original, "vote-a").join());
        assertEquals(1, first.get());
        assertEquals(0, second.get());
        assertEquals(1, store.cursor("vote-a/vote"));
        store.failAtStep = -1;
        SharedRewardPlan changed = new SharedRewardPlan("vote", 1, Duration.ZERO, List.of(),
                List.of(step("first", first), step("second", second)), "changed-config");
        assertThrows(CompletionException.class, () -> execute(store, changed, "vote-a").join());
        assertEquals(0, second.get());
        assertEquals(SharedRewardResult.COMPLETED, execute(store, original, "vote-a").join());
        assertEquals(1, first.get());
        assertEquals(1, second.get());
    }

    private static SharedRewardPlan plan(SharedRewardStep... steps) {
        return new SharedRewardPlan("vote", 1, Duration.ZERO, List.of(), List.of(steps), "config-v1");
    }

    private static SharedRewardStep step(String id, AtomicInteger calls) {
        return new SharedRewardStep(id, false, (context, path) -> {
            calls.incrementAndGet();
            return done();
        });
    }

    private static CompletionStage<SharedRewardResult> done() {
        return CompletableFuture.completedFuture(SharedRewardResult.COMPLETED);
    }

    private static CompletableFuture<SharedRewardResult> execute(Store store, SharedRewardPlan plan, String key) {
        return execute(new Platform(), store, plan, key);
    }

    private static CompletableFuture<SharedRewardResult> execute(Platform platform, Store store,
            SharedRewardPlan plan, String key) {
        return new SharedRewardOrchestrator(platform)
                .executeKeyed(plan, new SharedRewardContext(USER, "Ben", Map.of()), store, key).toCompletableFuture();
    }

    private static final class Platform implements SharedRewardPlatform {
        boolean online = true;
        boolean shuttingDown;
        boolean nativeDispatch;
        int nativeDispatches;
        int claimedDispatches;
        UUID lastDispatchUser;
        boolean lastDispatchRequiresOnline;
        public Instant now() { return Instant.EPOCH; }
        public boolean isOnline(UUID uuid) {
            assertTrue(nativeDispatch);
            return online;
        }
        public boolean isShuttingDown() { return shuttingDown; }
        public double nextChanceRoll() { return 0; }
        public CompletionStage<SharedRewardResult> delay(Duration delay,
                Supplier<CompletionStage<SharedRewardResult>> work) { return work.get(); }
        public CompletionStage<Boolean> checkActionAvailability(UUID userId) {
            assertFalse(nativeDispatch);
            nativeDispatches++;
            nativeDispatch = true;
            try { return CompletableFuture.completedFuture(isOnline(userId)); }
            finally { nativeDispatch = false; }
        }
        public CompletionStage<SharedRewardResult> runClaimedAction(
                UUID userId, boolean requiresOnlinePlayer, Supplier<CompletionStage<SharedRewardResult>> operation) {
            assertFalse(nativeDispatch);
            nativeDispatches++;
            claimedDispatches++;
            lastDispatchUser = userId;
            lastDispatchRequiresOnline = requiresOnlinePlayer;
            nativeDispatch = true;
            try { return operation.get(); }
            finally { nativeDispatch = false; }
        }
    }

    /** Simulates a caller-owned durable row that survives constructing a new adapter. */
    private static final class Store implements SharedRewardKeyedDurability {
        final Map<String, SharedRewardProgress> progress;
        final Map<String, Integer> pending;
        boolean failCheckpoint, loseClaimAck, loseCheckpointAck;
        CompletableFuture<SharedRewardActionClaim> delayedClaim;
        int failAtStep = -1;

        Store() { this(new HashMap<>(), new HashMap<>()); }
        Store(Map<String, SharedRewardProgress> progress, Map<String, Integer> pending) {
            this.progress = progress;
            this.pending = pending;
        }
        Store reopen() { return new Store(progress, pending); }
        synchronized int cursor(String path) { return progress.get(path).completedSteps(); }
        public boolean durable() { return true; }
        public synchronized int completedSteps(String path) { return progress.containsKey(path) ? cursor(path) : 0; }
        public synchronized SharedRewardProgress loadProgress(String path) { return progress.get(path); }
        public synchronized CompletionStage<SharedRewardProgress> begin(String path, SharedRewardProgress proposed) {
            progress.putIfAbsent(path, proposed);
            return CompletableFuture.completedFuture(progress.get(path));
        }
        public synchronized CompletionStage<SharedRewardActionClaim> claimAction(String path, String fingerprint,
                int index, SharedRewardContext context) {
            SharedRewardProgress state = progress.get(path);
            if (state == null || !fingerprint.equals(state.planFingerprint()) || state.completedSteps() != index) {
                return CompletableFuture.completedFuture(SharedRewardActionClaim.INDETERMINATE);
            }
            if (failAtStep == index) return CompletableFuture.failedFuture(new IllegalStateException("claim failed"));
            if (pending.putIfAbsent(path, index) != null) {
                return CompletableFuture.completedFuture(SharedRewardActionClaim.INDETERMINATE);
            }
            if (delayedClaim != null) return delayedClaim;
            if (loseClaimAck) return CompletableFuture.failedFuture(new IllegalStateException("lost claim acknowledgement"));
            return CompletableFuture.completedFuture(SharedRewardActionClaim.STARTED);
        }
        public synchronized CompletionStage<Void> checkpoint(String path, String fingerprint,
                int nextStep, SharedRewardContext context) {
            if (failCheckpoint) return CompletableFuture.failedFuture(new IllegalStateException("checkpoint failed"));
            if (pending.get(path) == null || pending.get(path) != nextStep - 1) {
                return CompletableFuture.failedFuture(new IllegalStateException("missing pending action"));
            }
            progress.put(path, progress.get(path).advance(nextStep, context));
            pending.remove(path);
            if (loseCheckpointAck) return CompletableFuture.failedFuture(new IllegalStateException("lost checkpoint acknowledgement"));
            return CompletableFuture.completedFuture(null);
        }
        public CompletionStage<Void> checkpoint(String path, int nextStep, SharedRewardContext context) {
            return checkpoint(path, progress.get(path).planFingerprint(), nextStep, context);
        }
        public CompletionStage<Void> defer(String path, int nextStep, SharedRewardContext context) {
            return CompletableFuture.completedFuture(null);
        }
    }
}
