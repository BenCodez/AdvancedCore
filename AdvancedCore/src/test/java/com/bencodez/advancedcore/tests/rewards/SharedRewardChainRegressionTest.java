package com.bencodez.advancedcore.tests.rewards;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.bencodez.advancedcore.core.reward.*;

/** Non-Bukkit execution and in-memory checkpoint adapter, not a live server/database. */
@Timeout(15)
class SharedRewardChainRegressionTest {
    private static final int LENGTH = 20_000;

    @Test void synchronousActionsAndCheckpointsDoNotGrowTheCallStack() {
        Fixture fixture = new Fixture();
        assertEquals(SharedRewardResult.COMPLETED, fixture.run(fixture.steps(LENGTH)).join());
        assertEquals(LENGTH, fixture.actions);
        assertEquals(LENGTH, fixture.progress.completedSteps());
        assertEquals(Integer.toString(LENGTH), fixture.progress.placeholders().get("count"));
    }

    @Test void synchronousRequirementsDoNotGrowTheCallStack() {
        Fixture fixture = new Fixture();
        List<SharedRewardRequirement> requirements = new ArrayList<>();
        int[] checks = {0};
        for (int i = 0; i < LENGTH; i++) {
            int expected = i;
            requirements.add(context -> {
                assertEquals(expected, checks[0]++);
                return CompletableFuture.completedFuture(Boolean.TRUE);
            });
        }
        var plan = new SharedRewardPlan("chain", 1, Duration.ZERO, requirements, fixture.steps(1))
                .withDefinitionFingerprint("chain-v1");
        assertEquals(SharedRewardResult.COMPLETED, fixture.run(plan).join());
        assertEquals(LENGTH, checks[0]);
        assertEquals(1, fixture.actions);
    }

    @Test void asynchronousRequirementStillShortCircuitsTheRemainingSuffix() {
        Fixture fixture = new Fixture();
        CompletableFuture<Boolean> held = new CompletableFuture<>();
        List<SharedRewardRequirement> requirements = new ArrayList<>();
        int[] checks = {0};
        for (int i = 0; i < LENGTH; i++) {
            int index = i;
            requirements.add(context -> {
                checks[0]++;
                return index == 1_000 ? held.minimalCompletionStage()
                        : CompletableFuture.completedFuture(Boolean.TRUE);
            });
        }
        var result = fixture.run(new SharedRewardPlan("chain", 1, Duration.ZERO, requirements, fixture.steps(1))
                .withDefinitionFingerprint("chain-v1"));
        assertFalse(result.isDone());
        assertEquals(1_001, checks[0]);
        assertEquals(0, fixture.actions);
        held.complete(false);
        assertEquals(SharedRewardResult.NOT_ELIGIBLE, result.join());
        assertEquals(1_001, checks[0]);
        assertEquals(0, fixture.actions);
    }

    @Test void mixedAsyncActionsAndCheckpointsRemainOrderedOnTheCompletingThread() throws Exception {
        Fixture fixture = new Fixture();
        CompletableFuture<SharedRewardResult> action = new CompletableFuture<>();
        CompletableFuture<Void> checkpoint = new CompletableFuture<>();
        fixture.actionResult = index -> index == 1_000 ? action.minimalCompletionStage()
                : CompletableFuture.completedFuture(SharedRewardResult.COMPLETED);
        fixture.checkpointResult = next -> next == 8_000 ? checkpoint.minimalCompletionStage()
                : CompletableFuture.completedFuture(null);
        var result = fixture.run(fixture.steps(LENGTH));
        assertFalse(result.isDone());
        assertEquals(1_001, fixture.actions);
        assertEquals(1_000, fixture.progress.completedSteps());
        var worker = Executors.newSingleThreadExecutor();
        try {
            Thread completing = worker.submit(() -> {
                action.complete(SharedRewardResult.COMPLETED);
                return Thread.currentThread();
            }).get(5, TimeUnit.SECONDS);
            assertFalse(result.isDone());
            assertEquals(8_000, fixture.actions);
            assertEquals(7_999, fixture.progress.completedSteps());
            assertSame(completing, fixture.lastActionThread);
            worker.submit(() -> checkpoint.complete(null)).get(5, TimeUnit.SECONDS);
            assertEquals(SharedRewardResult.COMPLETED, result.get(5, TimeUnit.SECONDS));
            assertEquals(LENGTH, fixture.actions);
            assertEquals(LENGTH, fixture.progress.completedSteps());
            assertSame(completing, fixture.lastActionThread);
        } finally {
            worker.shutdownNow();
            assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test void failedActionDoesNotCheckpointOrExecuteAnyLaterStep() {
        Fixture fixture = new Fixture();
        IllegalStateException failure = new IllegalStateException("action failed");
        fixture.actionResult = index -> index == 1_000 ? CompletableFuture.failedFuture(failure)
                : CompletableFuture.completedFuture(SharedRewardResult.COMPLETED);
        assertSame(failure, assertThrows(CompletionException.class,
                () -> fixture.run(fixture.steps(LENGTH)).join()).getCause());
        assertEquals(1_001, fixture.actions);
        assertEquals(1_000, fixture.progress.completedSteps());
    }

    @Test void failedCheckpointDoesNotAdvanceOrRunLaterSteps() {
        Fixture fixture = new Fixture();
        IllegalStateException failure = new IllegalStateException("checkpoint failed");
        fixture.checkpointResult = next -> next == 1_000 ? CompletableFuture.failedFuture(failure)
                : CompletableFuture.completedFuture(null);
        assertSame(failure, assertThrows(CompletionException.class,
                () -> fixture.run(fixture.steps(LENGTH)).join()).getCause());
        assertEquals(1_000, fixture.actions);
        assertEquals(999, fixture.progress.completedSteps());
    }

    @Test void disconnectDefersOnlyTheUncompletedSuffixOfALongChain() {
        Fixture fixture = new Fixture();
        fixture.actionResult = index -> {
            if (index == 999) fixture.online = false;
            return CompletableFuture.completedFuture(SharedRewardResult.COMPLETED);
        };
        assertEquals(SharedRewardResult.DEFERRED, fixture.run(fixture.steps(LENGTH)).join());
        assertEquals(1_000, fixture.actions);
        assertEquals(1_000, fixture.progress.completedSteps());
        assertEquals(1_000, fixture.deferredAt);
    }

    @Test void anExplicitDeferredActionIsNotCheckpointed() {
        Fixture fixture = new Fixture();
        fixture.actionResult = index -> CompletableFuture.completedFuture(index == 1_000
                ? SharedRewardResult.DEFERRED : SharedRewardResult.COMPLETED);
        assertEquals(SharedRewardResult.DEFERRED, fixture.run(fixture.steps(LENGTH)).join());
        assertEquals(1_001, fixture.actions);
        assertEquals(1_000, fixture.progress.completedSteps());
    }

    @Test void shutdownAfterTheLastCheckpointStillFailsInsteadOfReportingCompletion() {
        Fixture fixture = new Fixture();
        CompletableFuture<Void> held = new CompletableFuture<>();
        fixture.checkpointResult = next -> held;
        var result = fixture.run(fixture.steps(1));
        assertFalse(result.isDone());
        fixture.stopping = true;
        held.complete(null);
        assertThrows(CompletionException.class, result::join);
        assertEquals(1, fixture.actions);
        assertEquals(1, fixture.progress.completedSteps());
    }

    private static final class Fixture implements SharedRewardPlatform, SharedRewardDurability {
        boolean online = true, stopping;
        int actions, deferredAt = -1;
        Thread lastActionThread;
        SharedRewardProgress progress;
        IntFunction<CompletionStage<SharedRewardResult>> actionResult = index ->
                CompletableFuture.completedFuture(SharedRewardResult.COMPLETED);
        IntFunction<CompletionStage<Void>> checkpointResult = next -> CompletableFuture.completedFuture(null);

        List<SharedRewardStep> steps(int count) {
            List<SharedRewardStep> steps = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                int index = i;
                steps.add(new SharedRewardStep("step-" + index, true, (context, path) -> {
                    assertEquals(index, actions++);
                    assertEquals(index, progress.completedSteps(), "action preceded its prior checkpoint");
                    assertEquals("chain/step-" + index + ":" + index, path);
                    lastActionThread = Thread.currentThread();
                    context.placeholders().put("count", Integer.toString(index + 1));
                    return actionResult.apply(index);
                }));
            }
            return steps;
        }
        CompletableFuture<SharedRewardResult> run(List<SharedRewardStep> steps) {
            return run(SharedRewardPlan.immediate("chain", steps).withDefinitionFingerprint("chain-v1"));
        }
        CompletableFuture<SharedRewardResult> run(SharedRewardPlan plan) {
            return new SharedRewardOrchestrator(this).execute(plan,
                    new SharedRewardContext(UUID.randomUUID(), "Ben", Map.of()), this).toCompletableFuture();
        }
        public boolean isOnline(UUID uuid) { return online; }
        public boolean isShuttingDown() { return stopping; }
        public double nextChanceRoll() { throw new AssertionError("Unexpected chance roll"); }
        public CompletionStage<SharedRewardResult> delay(Duration delay,
                Supplier<CompletionStage<SharedRewardResult>> work) { throw new AssertionError("Unexpected delay"); }
        public boolean durable() { return true; }
        public int completedSteps(String path) { return progress == null ? 0 : progress.completedSteps(); }
        public SharedRewardProgress loadProgress(String path) { return progress; }
        public CompletionStage<SharedRewardProgress> begin(String path, SharedRewardProgress proposed) {
            if (progress == null) progress = proposed;
            return CompletableFuture.completedFuture(progress);
        }
        public CompletionStage<Void> checkpoint(String path, int next, SharedRewardContext context) {
            assertEquals(next, actions, "checkpoint preceded action completion");
            return checkpointResult.apply(next).thenRun(() -> progress = progress.advance(next, context));
        }
        public CompletionStage<Void> defer(String path, int next, SharedRewardContext context) {
            deferredAt = next;
            return CompletableFuture.completedFuture(null);
        }
    }
}
