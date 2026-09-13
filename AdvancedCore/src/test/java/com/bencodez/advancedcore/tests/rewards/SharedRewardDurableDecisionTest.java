package com.bencodez.advancedcore.tests.rewards;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.bencodez.advancedcore.core.reward.*;

/** Headless orchestration with test replay adapters; no replacement production replay store. */
class SharedRewardDurableDecisionTest {
    @TempDir Path directory;
    private static final UUID USER = UUID.fromString("fef273b7-aa45-42f9-ac14-cb047533afde");

    @Test
    void firstFailureAndReconstructedReplayRetainTheDecisionAtStepZero() throws Exception {
        Platform platform = new Platform();
        platform.roll = 0.1;
        AtomicInteger requirements = new AtomicInteger(), attempts = new AtomicInteger();
        SharedRewardPlan plan = plan(0.5, List.of(ctx -> {
            requirements.incrementAndGet();
            ctx.placeholders().put("token", "original");
            return CompletableFuture.completedFuture(true);
        }), List.of(new SharedRewardStep("command", false, (ctx, path) -> {
            if (attempts.incrementAndGet() == 1) return CompletableFuture.failedFuture(new IllegalStateException("offline service"));
            assertEquals("original", ctx.placeholders().get("token"));
            return done();
        })));
        Path file = directory.resolve("replay.properties");
        DiskReplay first = new DiskReplay(file);
        assertThrows(CompletionException.class, () -> execute(platform, plan, first).join());
        assertEquals(0, first.loadProgress("vote").completedSteps());
        platform.roll = 0.99;
        DiskReplay reopened = new DiskReplay(file);
        assertEquals(SharedRewardResult.COMPLETED, execute(platform, plan, reopened).join());
        assertEquals(1, requirements.get());
        assertEquals(1, platform.rolls);
        assertEquals(1, new DiskReplay(file).loadProgress("vote").completedSteps());
    }

    @Test
    void declinedChanceDecisionDoesNotBecomeEligibleOnRetry() {
        Platform platform = new Platform();
        platform.roll = 0.9;
        AtomicInteger requirements = new AtomicInteger(), actions = new AtomicInteger();
        SharedRewardPlan plan = plan(0.5, List.of(ctx -> {
            requirements.incrementAndGet();
            return CompletableFuture.completedFuture(true);
        }), List.of(action("command", actions)));
        Replay replay = new Replay();
        assertEquals(SharedRewardResult.NOT_ELIGIBLE, execute(platform, plan, replay).join());
        platform.roll = 0.1;
        assertEquals(SharedRewardResult.NOT_ELIGIBLE, execute(platform, plan, replay).join());
        assertEquals(1, requirements.get());
        assertEquals(1, platform.rolls);
        assertEquals(0, actions.get());
    }

    @Test
    void offlineZeroCursorResumesWithoutReevaluatingRequirements() {
        Platform platform = new Platform();
        platform.online = false;
        AtomicInteger requirements = new AtomicInteger(), actions = new AtomicInteger();
        SharedRewardPlan plan = plan(0.5, List.of(ctx -> {
            requirements.incrementAndGet();
            return CompletableFuture.completedFuture(true);
        }), List.of(new SharedRewardStep("message", true, (ctx, path) -> {
            actions.incrementAndGet();
            return done();
        })));
        Replay replay = new Replay();
        assertEquals(SharedRewardResult.DEFERRED, execute(platform, plan, replay).join());
        assertEquals(0, replay.loadProgress("vote").completedSteps());
        platform.online = true;
        platform.roll = 0.99;
        assertEquals(SharedRewardResult.COMPLETED, execute(platform, plan, replay).join());
        assertEquals(1, requirements.get());
        assertEquals(1, platform.rolls);
        assertEquals(1, actions.get());
    }

    @Test
    void initialDecisionPersistenceCompletesBeforeDelayAndAction() {
        Platform platform = new Platform();
        Replay replay = new Replay();
        replay.beginAck = new CompletableFuture<>();
        AtomicInteger actions = new AtomicInteger();
        SharedRewardPlan plan = new SharedRewardPlan("vote", 1, Duration.ofSeconds(1), List.of(),
                List.of(action("command", actions))).withDefinitionFingerprint("config-v1");
        CompletableFuture<SharedRewardResult> result = execute(platform, plan, replay);
        assertFalse(result.isDone());
        assertEquals(0, platform.delays);
        assertEquals(0, actions.get());
        replay.beginAck.complete(null);
        assertEquals(SharedRewardResult.COMPLETED, result.join());
        assertEquals(1, platform.delays);
        assertEquals(1, actions.get());
    }

    @Test
    void lostBeginAcknowledgementRecoversThePersistedDecision() {
        Platform platform = new Platform();
        Replay replay = new Replay();
        replay.beginAck = CompletableFuture.failedFuture(new IllegalStateException("lost acknowledgement"));
        AtomicInteger actions = new AtomicInteger();
        SharedRewardPlan plan = plan(0.5, List.of(), List.of(action("command", actions)));
        assertThrows(CompletionException.class, () -> execute(platform, plan, replay).join());
        assertEquals(0, actions.get());
        platform.roll = 0.99;
        assertEquals(SharedRewardResult.COMPLETED, execute(platform, plan, replay).join());
        assertEquals(1, actions.get());
        assertEquals(1, platform.rolls);
    }

    @Test
    void operationAndCheckpointAcknowledgementBothPrecedeTheNextStep() {
        Platform platform = new Platform();
        Replay replay = new Replay();
        replay.checkpointAck = new CompletableFuture<>();
        CompletableFuture<SharedRewardResult> operation = new CompletableFuture<>();
        AtomicInteger next = new AtomicInteger();
        SharedRewardPlan plan = plan(1, List.of(), List.of(
                new SharedRewardStep("first", false, (ctx, path) -> operation), action("next", next)));
        CompletableFuture<SharedRewardResult> result = execute(platform, plan, replay);
        assertFalse(result.isDone());
        assertEquals(0, replay.loadProgress("vote").completedSteps());
        operation.complete(SharedRewardResult.COMPLETED);
        assertFalse(result.isDone());
        assertEquals(0, next.get());
        replay.checkpointAck.complete(null);
        assertEquals(SharedRewardResult.COMPLETED, result.join());
        assertEquals(1, next.get());
    }

    @Test
    void lostCheckpointAcknowledgementDoesNotRepeatADurablePrefix() {
        Platform platform = new Platform();
        Replay replay = new Replay();
        replay.checkpointAck = CompletableFuture.failedFuture(new IllegalStateException("lost acknowledgement"));
        AtomicInteger first = new AtomicInteger(), second = new AtomicInteger();
        SharedRewardPlan plan = plan(1, List.of(), List.of(action("first", first), action("second", second)));
        assertThrows(CompletionException.class, () -> execute(platform, plan, replay).join());
        assertEquals(1, first.get());
        assertEquals(0, second.get());
        replay.checkpointAck = CompletableFuture.completedFuture(null);
        assertEquals(SharedRewardResult.COMPLETED, execute(platform, plan, replay).join());
        assertEquals(1, first.get());
        assertEquals(1, second.get());
    }

    @Test
    void changedPlansAreRejectedEvenWhenTheOldCursorIsWithinBounds() {
        Platform platform = new Platform();
        AtomicInteger actions = new AtomicInteger();
        SharedRewardStep a = action("a", actions), b = action("b", actions), c = action("c", actions);
        SharedRewardPlan original = plan(1, List.of(), List.of(a, b));
        Replay replay = new Replay();
        SharedRewardProgress progress = new SharedRewardProgress(original.fingerprint(), true, 1, Map.of());
        replay.progress.put("vote", progress);
        List<SharedRewardPlan> edited = List.of(
                plan(1, List.of(), List.of(b, a)),
                plan(1, List.of(), List.of(c, a, b)),
                plan(1, List.of(), List.of(b)),
                plan(0.5, List.of(), List.of(a, b)),
                new SharedRewardPlan("vote", 1, Duration.ofSeconds(2), List.of(), List.of(a, b), "config-v1"),
                original.withDefinitionFingerprint("different-native-payload-or-requirement-v2"),
                plan(1, List.of(), List.of(new SharedRewardStep("a", true, a.action()), b)));
        for (SharedRewardPlan changed : edited) {
            assertThrows(CompletionException.class, () -> execute(platform, changed, replay).join());
            assertSame(progress, replay.loadProgress("vote"));
        }
        assertEquals(0, actions.get());
        assertEquals(0, platform.rolls);
    }

    @Test
    void stableDefinitionsDoNotDependOnLambdaObjectIdentity() {
        assertEquals(plan(1, List.of(), List.of(action("a", new AtomicInteger()))).fingerprint(),
                plan(1, List.of(), List.of(action("a", new AtomicInteger()))).fingerprint());
    }

    @Test
    void unversionedCursorIsNotInterpretedAsCurrentPlanProgress() {
        Platform platform = new Platform();
        Replay replay = new Replay() {
            @Override public int completedSteps(String path) { return 1; }
        };
        AtomicInteger actions = new AtomicInteger();
        assertThrows(CompletionException.class,
                () -> execute(platform, plan(1, List.of(), List.of(action("a", actions))), replay).join());
        assertEquals(0, actions.get());
    }

    @Test
    void oldConstructorRemainsUsableWithoutDurabilityButCannotResumeUnboundWork() {
        Platform platform = new Platform();
        AtomicInteger actions = new AtomicInteger();
        SharedRewardPlan unbound = SharedRewardPlan.immediate("vote", List.of(action("a", actions)));
        assertThrows(CompletionException.class, () -> execute(platform, unbound, new Replay()).join());
        assertEquals(0, actions.get());
        assertEquals(SharedRewardResult.COMPLETED, execute(platform, unbound, SharedRewardDurability.NONE).join());
        assertEquals(1, actions.get());
    }

    @Test
    void unsupportedLegacyDurabilityFailsBeforeRunningActions() {
        Platform platform = new Platform();
        AtomicInteger actions = new AtomicInteger();
        SharedRewardDurability legacy = new SharedRewardDurability() {
            public boolean durable() { return true; }
            public int completedSteps(String path) { return 0; }
            public CompletionStage<Void> checkpoint(String p, int n, SharedRewardContext c) { return CompletableFuture.completedFuture(null); }
            public CompletionStage<Void> defer(String p, int n, SharedRewardContext c) { return CompletableFuture.completedFuture(null); }
        };
        assertThrows(CompletionException.class,
                () -> execute(platform, plan(1, List.of(), List.of(action("a", actions))), legacy).join());
        assertEquals(0, actions.get());
    }

    @Test
    void firstStepFailureAfterItsDeadlineDoesNotRestartTheDelay() {
        Platform platform = new Platform();
        Replay replay = new Replay();
        AtomicInteger attempts = new AtomicInteger();
        SharedRewardPlan plan = new SharedRewardPlan("vote", 1, Duration.ofHours(12), List.of(),
                List.of(new SharedRewardStep("command", false, (ctx, path) -> {
                    if (attempts.incrementAndGet() == 1) return CompletableFuture.failedFuture(new IllegalStateException("temporary"));
                    return done();
                })), "config-v1");
        assertThrows(CompletionException.class, () -> execute(platform, plan, replay).join());
        assertEquals(0, replay.loadProgress("vote").completedSteps());
        assertEquals(Instant.EPOCH.plus(Duration.ofHours(12)), replay.loadProgress("vote").notBefore());
        assertEquals(SharedRewardResult.COMPLETED, execute(platform, plan, replay).join());
        assertEquals(List.of(Duration.ofHours(12)), platform.waited);
    }

    @Test
    void offlineAfterDelayDefersWithoutChargingTheDelayAgainOnReconnect() {
        Platform platform = new Platform();
        platform.online = false;
        Replay replay = new Replay();
        SharedRewardPlan plan = new SharedRewardPlan("vote", 1, Duration.ofDays(1), List.of(),
                List.of(new SharedRewardStep("message", true, (ctx, path) -> done())), "config-v1");
        assertEquals(SharedRewardResult.DEFERRED, execute(platform, plan, replay).join());
        platform.online = true;
        assertEquals(SharedRewardResult.COMPLETED, execute(platform, plan, replay).join());
        assertEquals(List.of(Duration.ofDays(1)), platform.waited);
    }

    @Test
    void reconstructedReplayWaitsOnlyUntilTheOriginalDeadline() throws Exception {
        Platform firstPlatform = new Platform();
        Path file = directory.resolve("deadline.properties");
        DiskReplay first = new DiskReplay(file);
        first.beginAck = CompletableFuture.failedFuture(new IllegalStateException("lost acknowledgement"));
        SharedRewardPlan plan = new SharedRewardPlan("vote", 1, Duration.ofHours(12), List.of(),
                List.of(action("command", new AtomicInteger())), "config-v1");
        assertThrows(CompletionException.class, () -> execute(firstPlatform, plan, first).join());
        assertTrue(firstPlatform.waited.isEmpty());
        Platform restarted = new Platform();
        restarted.time = Instant.EPOCH.plus(Duration.ofHours(10));
        DiskReplay reopened = new DiskReplay(file);
        assertEquals(SharedRewardResult.COMPLETED, execute(restarted, plan, reopened).join());
        assertEquals(List.of(Duration.ofHours(2)), restarted.waited);
        assertEquals(Instant.EPOCH.plus(Duration.ofHours(12)), new DiskReplay(file).loadProgress("vote").notBefore());
    }

    @Test
    void legacyCursorZeroWithoutTimingProofCannotRestartOrBypassTheDelay() {
        Platform platform = new Platform();
        Replay replay = new Replay();
        AtomicInteger actions = new AtomicInteger();
        SharedRewardPlan plan = new SharedRewardPlan("vote", 1, Duration.ofDays(1), List.of(),
                List.of(action("command", actions)), "config-v1");
        SharedRewardProgress legacy = new SharedRewardProgress(plan.fingerprint(), true, 0, Map.of());
        replay.progress.put("vote", legacy);
        assertThrows(CompletionException.class, () -> execute(platform, plan, replay).join());
        assertSame(legacy, replay.loadProgress("vote"));
        assertTrue(platform.waited.isEmpty());
        assertEquals(0, actions.get());
    }

    private static SharedRewardPlan plan(double chance, List<SharedRewardRequirement> requirements, List<SharedRewardStep> steps) {
        return new SharedRewardPlan("vote", chance, Duration.ZERO, requirements, steps).withDefinitionFingerprint("config-v1");
    }

    private static SharedRewardStep action(String id, AtomicInteger calls) {
        return new SharedRewardStep(id, false, (ctx, path) -> { calls.incrementAndGet(); return done(); });
    }

    private static CompletableFuture<SharedRewardResult> done() { return CompletableFuture.completedFuture(SharedRewardResult.COMPLETED); }

    private static CompletableFuture<SharedRewardResult> execute(Platform p, SharedRewardPlan plan, SharedRewardDurability replay) {
        return new SharedRewardOrchestrator(p).execute(plan, new SharedRewardContext(USER, "Ben", Map.of()), replay).toCompletableFuture();
    }

    private static final class Platform implements SharedRewardPlatform {
        boolean online = true;
        double roll;
        int rolls, delays;
        Instant time = Instant.EPOCH;
        final List<Duration> waited = new ArrayList<>();
        public Instant now() { return time; }
        public boolean isOnline(UUID uuid) { return online; }
        public boolean isShuttingDown() { return false; }
        public double nextChanceRoll() { rolls++; return roll; }
        public CompletionStage<SharedRewardResult> delay(Duration d, Supplier<CompletionStage<SharedRewardResult>> work) {
            delays++;
            waited.add(d);
            time = time.plus(d);
            return work.get();
        }
    }

    private static class Replay implements SharedRewardDurability {
        final Map<String, SharedRewardProgress> progress = new HashMap<>();
        CompletableFuture<Void> beginAck = CompletableFuture.completedFuture(null);
        CompletableFuture<Void> checkpointAck = CompletableFuture.completedFuture(null);
        public boolean durable() { return true; }
        public int completedSteps(String path) { return progress.containsKey(path) ? progress.get(path).completedSteps() : 0; }
        public SharedRewardProgress loadProgress(String path) { return progress.get(path); }
        public CompletionStage<SharedRewardProgress> begin(String path, SharedRewardProgress proposed) {
            progress.putIfAbsent(path, proposed);
            persist();
            return beginAck.thenApply(ignored -> progress.get(path));
        }
        public CompletionStage<Void> checkpoint(String path, int completed, SharedRewardContext context) {
            progress.put(path, progress.get(path).advance(completed, context));
            persist();
            return checkpointAck;
        }
        public CompletionStage<Void> defer(String path, int cursor, SharedRewardContext context) {
            assertEquals(cursor, completedSteps(path));
            return CompletableFuture.completedFuture(null);
        }
        void persist() {}
    }

    /** Test-only disk snapshot simulates restart; not a production SQL adapter or power-loss test. */
    private static final class DiskReplay extends Replay {
        private final Path file;
        DiskReplay(Path file) throws Exception {
            this.file = file;
            if (Files.exists(file)) {
                Properties p = new Properties();
                try (InputStream in = Files.newInputStream(file)) { p.load(in); }
                progress.put("vote", new SharedRewardProgress(p.getProperty("fingerprint"),
                        Boolean.parseBoolean(p.getProperty("eligible")), Integer.parseInt(p.getProperty("cursor")),
                        Map.of("token", p.getProperty("token", "")),
                        p.getProperty("notBefore") == null ? null : Instant.parse(p.getProperty("notBefore"))));
            }
        }
        @Override void persist() {
            SharedRewardProgress state = progress.get("vote");
            Properties p = new Properties();
            p.setProperty("fingerprint", state.planFingerprint());
            p.setProperty("eligible", Boolean.toString(state.eligible()));
            p.setProperty("cursor", Integer.toString(state.completedSteps()));
            p.setProperty("token", state.placeholders().getOrDefault("token", ""));
            if (state.notBefore() != null) p.setProperty("notBefore", state.notBefore().toString());
            Path pending = file.resolveSibling(file.getFileName() + ".tmp");
            try {
                try (OutputStream out = Files.newOutputStream(pending)) { p.store(out, "test replay"); }
                Files.move(pending, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception failure) { throw new IllegalStateException(failure); }
        }
    }
}
