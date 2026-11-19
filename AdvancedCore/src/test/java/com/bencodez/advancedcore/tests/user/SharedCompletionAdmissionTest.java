package com.bencodez.advancedcore.tests.user;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.advancedcore.api.user.usercache.UserDataManager;
import com.bencodez.advancedcore.core.user.runtime.SharedUserDataRuntime;
import com.bencodez.advancedcore.core.user.runtime.UserCacheOwner;
import com.bencodez.advancedcore.core.user.storage.sql.SqlUserBackend;

class SharedCompletionAdmissionTest {
    static Object field(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(target);
    }
    static void set(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name); f.setAccessible(true); f.set(target, value);
    }
    static class Fixture implements AutoCloseable {
        final AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        final UserDataManager manager = new UserDataManager(plugin);
        final SqlUserBackend backend = mock(SqlUserBackend.class);
        final UserCacheOwner owner = mock(UserCacheOwner.class);
        final ScheduledExecutorService timer = mock(ScheduledExecutorService.class);
        final Queue<Runnable> tasks = new ConcurrentLinkedQueue<>();
        final SharedUserDataRuntime runtime;
        Fixture() throws Exception {
            manager.getTimer().shutdownNow(); set(manager, "timer", timer);
            when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
            when(backend.isOpen()).thenReturn(true); when(backend.storageType()).thenReturn(UserStorage.SQLITE);
            when(backend.supportsAtomicUserTransactions()).thenReturn(true);
            doAnswer(c -> { tasks.add(c.getArgument(0)); return null; }).when(timer).execute(any(Runnable.class));
            when(timer.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class))).thenAnswer(c -> {
                tasks.add(c.getArgument(0)); return mock(ScheduledFuture.class);
            });
            runtime = new SharedUserDataRuntime(backend, owner); manager.bindSharedRuntime(runtime);
        }
        void runOne() { assertNotNull(tasks.peek()); tasks.remove().run(); }
        public void close() throws Exception {
            tasks.clear(); manager.getTimer().shutdownNow();
            var pool = (ThreadPoolExecutor) field(manager, "mutationCallbacks");
            pool.shutdownNow(); assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
    }
    @Test void rejectedReadOwnerRetirementDoesNotFenceNotifications() throws Exception {
        try (var f = new Fixture()) {
            long generation = ((java.util.concurrent.atomic.AtomicLong) field(f.manager, "sharedNotificationGeneration")).get();
            f.runtime.withStorageReadAdmission(() -> {
                var stage = f.manager.closeSharedRuntimeAsyncCompletion(() -> {}).toCompletableFuture();
                assertTrue(stage.isCompletedExceptionally()); return null;
            });
            assertFalse((Boolean) field(f.manager, "sharedNotificationsClosed"));
            assertEquals(generation, ((java.util.concurrent.atomic.AtomicLong) field(f.manager, "sharedNotificationGeneration")).get());
            assertFalse(f.runtime.isRetiring());
        }
    }
    @Test void terminalNotificationFenceAdvancesOnce() throws Exception {
        try (var f = new Fixture()) {
            f.manager.closeSharedUserDataNotifications();
            long generation = ((java.util.concurrent.atomic.AtomicLong) field(f.manager, "sharedNotificationGeneration")).get();
            f.manager.closeSharedUserDataNotifications();
            assertEquals(generation, ((java.util.concurrent.atomic.AtomicLong) field(f.manager, "sharedNotificationGeneration")).get());
        }
    }
    @Test void runtimeRevokesQueuedWrapperWhenExecuteThrowsBeforeEntry() throws Exception {
        try (var f = new Fixture()) {
            var queue = new ArrayList<Runnable>();
            var stage = f.runtime.closeAsync(task -> { queue.add(task); throw new RejectedExecutionException(); });
            assertTrue(stage.toCompletableFuture().isCompletedExceptionally());
            queue.get(0).run();
            verify(f.backend, never()).close();
            assertFalse(f.runtime.isClosed());
        }
    }
    @Test void runtimeStartedWorkKeepsAuthoritativeStageDespiteExecuteThrow() throws Exception {
        try (var f = new Fixture()) {
            CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
            doAnswer(c -> { entered.countDown(); assertTrue(release.await(5, TimeUnit.SECONDS)); return null; }).when(f.owner).beginRetirement();
            var worker = new Thread[1];
            try {
                var stage = f.runtime.closeAsync(task -> {
                    worker[0] = new Thread(task); worker[0].start();
                    try { assertTrue(entered.await(5, TimeUnit.SECONDS)); }
                    catch (InterruptedException e) { throw new AssertionError(e); }
                    throw new RejectedExecutionException();
                }).toCompletableFuture();
                assertFalse(stage.isDone()); release.countDown(); stage.get(5, TimeUnit.SECONDS);
                verify(f.backend, times(1)).close();
            } finally { release.countDown(); if (worker[0] != null) worker[0].join(6000); }
        }
    }
    @Test void replacementObservationCannotSettlePhysicalOwner() throws Exception {
        try (var f = new Fixture()) {
            var next = mock(SqlUserBackend.class); when(next.isOpen()).thenReturn(true);
            var observed = f.manager.replaceSharedSqlBackendAsync(next).toCompletableFuture();
            observed.complete(null);
            var retiring = f.manager.closeSharedRuntimeAsyncCompletion(() -> {}).toCompletableFuture();
            assertFalse(retiring.isDone()); assertFalse(f.runtime.isRetiring());
            assertTrue((Boolean) field(f.manager, "sharedRuntimeReplacing"));
            f.runOne();
            for (int i = 0; i < 12 && !retiring.isDone() && !f.tasks.isEmpty(); i++) f.runOne();
            retiring.get(5, TimeUnit.SECONDS);
        }
    }
    @Test void cleanupCannotJoinItsOwnRetirement() throws Exception {
        try (var f = new Fixture()) {
            AtomicInteger callbacks = new AtomicInteger();
            var stage = f.manager.closeSharedRuntimeAsyncCompletion(() -> {
                var nested = f.manager.closeSharedRuntimeAsyncCompletion(() -> {}).toCompletableFuture();
                assertTrue(nested.isDone()); assertTrue(nested.isCompletedExceptionally()); callbacks.incrementAndGet();
            }).toCompletableFuture();
            for (int i = 0; i < 12 && !stage.isDone() && !f.tasks.isEmpty(); i++) f.runOne();
            stage.get(5, TimeUnit.SECONDS); assertEquals(1, callbacks.get());
        }
    }
    @Test void admittedReceiptCannotBeLostToNotificationQueueSaturation() throws Exception {
        try (var f = new Fixture()) {
            // Use a mock transaction runtime so this tests result delivery, not SQL durability.
            var runtime = mock(SharedUserDataRuntime.class); when(runtime.backend()).thenReturn(f.backend);
            set(f.manager, "sharedRuntime", runtime);
            when(runtime.transaction(any(UUID.class), eq(UserStorage.SQLITE), anyMap(), any())).thenReturn("accepted");
            var receipt = f.manager.submitSharedReplayTransaction(UUID.randomUUID(), UserStorage.SQLITE, "operation", Map.of(), tx -> "unused");
            Runnable storage = f.tasks.remove();
            CountDownLatch entered = new CountDownLatch(4), release = new CountDownLatch(1);
            try {
                for (int i = 0; i < 772; i++) {
                    f.manager.dispatchSharedMutationNotification(() -> {
                        entered.countDown(); try { assertTrue(release.await(5, TimeUnit.SECONDS)); }
                        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    });
                    while (!f.tasks.isEmpty()) f.runOne();
                }
                assertTrue(entered.await(5, TimeUnit.SECONDS)); storage.run(); release.countDown();
                assertEquals("accepted", receipt.toCompletableFuture().get(5, TimeUnit.SECONDS));
                f.manager.afterSharedMutationFinalization(receipt).toCompletableFuture().get(5, TimeUnit.SECONDS);
            } finally { release.countDown(); }
        }
    }
    @Test void ordinaryRuntimeRetirementStillClosesOnce() throws Exception {
        try (var f = new Fixture()) {
            f.runtime.closeAsync(Runnable::run).toCompletableFuture().get(5, TimeUnit.SECONDS);
            f.runtime.closeAsync(Runnable::run).toCompletableFuture().get(5, TimeUnit.SECONDS);
            verify(f.backend, times(1)).close();
        }
    }
    static void await(java.util.function.BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(1);
        assertTrue(condition.getAsBoolean());
    }
    static int credits(Fixture f, String kind) throws Exception {
        Object budget = field(f.manager, "completionBudget");
        synchronized (budget) { return (Integer) field(budget, kind); }
    }
    static SharedUserDataRuntime mockTransactions(Fixture f) throws Exception {
        var runtime = mock(SharedUserDataRuntime.class); when(runtime.backend()).thenReturn(f.backend);
        when(runtime.transaction(any(UUID.class), eq(UserStorage.SQLITE), anyMap(), any())).thenReturn("confirmed");
        set(f.manager, "sharedRuntime", runtime); return runtime;
    }
    @Test void admissionQueryTracksOnlyTheCallingThread() throws Exception {
        try (var f = new Fixture()) {
            assertFalse(f.runtime.holdsLifecycleAdmission());
            f.runtime.withStorageReadAdmission(() -> { assertTrue(f.runtime.holdsLifecycleAdmission()); return null; });
            assertFalse(f.runtime.holdsLifecycleAdmission());
        }
    }
    @Test void reentrantCloseCannotJoinAnExistingDrainWait() throws Exception {
        try (var f = new Fixture()) {
            UUID id = UUID.randomUUID(); f.manager.acquireSharedReplayCheckpointLease(id, "lease");
            var waiting = f.manager.closeSharedRuntimeAsyncCompletion(() -> {}).toCompletableFuture();
            assertFalse(waiting.isDone());
            f.runtime.withStorageReadAdmission(() -> {
                var refused = f.manager.closeSharedRuntimeAsyncCompletion(() -> {}).toCompletableFuture();
                assertTrue(refused.isDone()); assertTrue(refused.isCompletedExceptionally()); return null;
            });
            assertFalse((Boolean) field(f.manager, "sharedNotificationsClosed"));
            assertThrows(RejectedExecutionException.class, () -> f.manager.acquireSharedReplayLease(id, "late"));
            f.manager.cancelSharedReplayCheckpointReservation(id, "lease");
            while (!f.tasks.isEmpty()) f.runOne(); waiting.get(5, TimeUnit.SECONDS);
        }
    }
    @Test void queuedNotificationBridgesAreRevokedWithoutTimerEntry() throws Exception {
        try (var f = new Fixture()) {
            AtomicInteger delivered = new AtomicInteger();
            for (int i = 0; i < 20; i++) f.manager.dispatchSharedMutationNotification(delivered::incrementAndGet);
            assertEquals(20, credits(f, "general"));
            var removed = new ArrayList<>(f.tasks); f.tasks.clear();
            f.manager.closeSharedUserDataNotifications(); assertEquals(0, credits(f, "general"));
            removed.forEach(Runnable::run);
            assertEquals(0, delivered.get()); assertEquals(0, credits(f, "general"));
        }
    }
    @Test void physicallyQueuedNotificationKeepsCreditUntilStaleWrapperExits() throws Exception {
        try (var f = new Fixture()) {
            CountDownLatch entered = new CountDownLatch(4), release = new CountDownLatch(1);
            AtomicInteger delivered = new AtomicInteger();
            try {
                for (int i = 0; i < 4; i++) {
                    f.manager.dispatchSharedMutationNotification(() -> {
                        entered.countDown(); try { assertTrue(release.await(5, TimeUnit.SECONDS)); }
                        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    }); f.runOne();
                }
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                f.manager.dispatchSharedMutationNotification(delivered::incrementAndGet); f.runOne();
                f.manager.closeSharedUserDataNotifications(); assertEquals(5, credits(f, "general"));
                release.countDown(); await(() -> { try { return credits(f, "general") == 0; } catch (Exception e) { throw new AssertionError(e); } });
                assertEquals(0, delivered.get());
            } finally { release.countDown(); }
        }
    }
    @Test void firstRevokedStorageSubmissionFailsWithoutLateProviderEntry() throws Exception {
        try (var f = new Fixture()) {
            var runtime = mockTransactions(f);
            doAnswer(c -> { f.tasks.add(c.getArgument(0)); throw new IllegalStateException("submission"); }).when(f.timer).execute(any(Runnable.class));
            var receipt = f.manager.submitSharedReplayTransaction(UUID.randomUUID(), UserStorage.SQLITE, "first", Map.of(), tx -> null);
            assertThrows(ExecutionException.class, () -> receipt.toCompletableFuture().get(5, TimeUnit.SECONDS));
            f.runOne(); verify(runtime, never()).transaction(any(UUID.class), any(), anyMap(), any());
            await(() -> { try { return credits(f, "general") == 0; } catch (Exception e) { throw new AssertionError(e); } });
        }
    }
    @Test void revokedRetryAfterUncertainTransactionRetainsOriginalFence() throws Exception {
        try (var f = new Fixture()) {
            var runtime = mockTransactions(f);
            when(runtime.transaction(any(UUID.class), eq(UserStorage.SQLITE), anyMap(), any())).thenThrow(new IllegalStateException("unknown transaction outcome"));
            when(f.timer.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class))).thenAnswer(c -> {
                f.tasks.add(c.getArgument(0)); throw new RejectedExecutionException();
            });
            var receipt = f.manager.submitSharedReplayTransaction(UUID.randomUUID(), UserStorage.SQLITE, "retry", Map.of(), tx -> null);
            f.runOne(); assertFalse(receipt.toCompletableFuture().isDone()); assertEquals(1, f.manager.indeterminateSharedReplays().size());
            f.runOne(); verify(runtime, times(1)).transaction(any(UUID.class), any(), anyMap(), any());
            assertFalse(receipt.toCompletableFuture().isDone()); assertEquals(1, credits(f, "general"));
        }
    }
    @Test void protectedLeaseCreditCannotReturnToARecreatedIdentity() throws Exception {
        try (var f = new Fixture()) {
            mockTransactions(f); UUID id = UUID.randomUUID(); String key = "same-key";
            f.manager.acquireSharedReplayCheckpointLease(id, key);
            var receipt = f.manager.submitSharedReplayCheckpoint(id, UserStorage.SQLITE, key, Map.of(), tx -> null);
            CountDownLatch finalized = new CountDownLatch(1), release = new CountDownLatch(1);
            var dependent = f.manager.afterSharedMutationFinalization(receipt).thenRun(() -> {
                finalized.countDown(); try { assertTrue(release.await(5, TimeUnit.SECONDS)); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            });
            try {
                f.runOne(); assertTrue(finalized.await(5, TimeUnit.SECONDS));
                assertEquals(1, field(f.manager, "cleanupHeadroom")); assertEquals(1, credits(f, "cleanup"));
                f.manager.settleSharedReplayLease(id, key, false, true);
                f.manager.acquireSharedReplayCheckpointLease(id, key); assertEquals(2, credits(f, "cleanup"));
                release.countDown(); dependent.toCompletableFuture().get(5, TimeUnit.SECONDS);
                await(() -> { try { return credits(f, "cleanup") == 1; } catch (Exception e) { throw new AssertionError(e); } });
                f.manager.cancelSharedReplayCheckpointReservation(id, key); assertEquals(0, credits(f, "cleanup"));
            } finally { release.countDown(); }
        }
    }
    @Test void allLeaseCleanupPromisesSurviveMixedCapacitySaturation() throws Exception {
        try (var f = new Fixture()) {
            mockTransactions(f);
            var table = mock(com.bencodez.advancedcore.api.user.userstorage.sql.UserTable.class);
            var nativeOwner = new AdvancedCorePlugin.UserStorageOwner(UserStorage.SQLITE, null, table);
            when(f.plugin.getNativeUserStorageOwner()).thenReturn(nativeOwner);
            when(f.plugin.getStorageType()).thenReturn(UserStorage.SQLITE); when(f.plugin.getSQLiteUserTable()).thenReturn(table);
            var users = mock(com.bencodez.advancedcore.api.user.UserManager.class);
            var user = mock(com.bencodez.advancedcore.api.user.AdvancedCoreUser.class);
            var data = mock(com.bencodez.advancedcore.api.user.UserData.class);
            when(f.plugin.getUserManager()).thenReturn(users); when(users.getUser(any(UUID.class), eq(false))).thenReturn(user);
            when(user.getUserData()).thenReturn(data);
            var legacy = new ArrayList<CompletionStage<?>>(); var legacyWork = new ArrayList<Runnable>();
            for (int i = 0; i < 256; i++) {
                legacy.add(f.manager.submitLegacyIntegerMutation(UUID.randomUUID(), Map.of("Points", 1),
                        com.bencodez.advancedcore.api.user.usercache.UserDataCache.IntegerMutation.ADD, null));
                legacyWork.add(f.tasks.remove());
            }
            UUID id = UUID.randomUUID();
            for (int i = 0; i < 256; i++) { String key = "lease-" + i; f.manager.acquireSharedReplayLease(id, key); f.manager.settleSharedReplayLease(id, key, true, false); }
            var checkpoints = new ArrayList<CompletionStage<?>>();
            for (int i = 0; i < 256; i++) {
                String key = "lease-" + i; f.manager.acquireSharedReplayCheckpointLease(id, key);
                checkpoints.add(f.manager.submitSharedReplayCheckpoint(id, UserStorage.SQLITE, key, Map.of(), tx -> null));
            }
            assertEquals(512, field(f.manager, "sharedIntegerAdmissions")); assertEquals(0, field(f.manager, "cleanupHeadroom"));
            Runnable first = f.tasks.remove();
            CountDownLatch entered = new CountDownLatch(4), release = new CountDownLatch(1);
            try {
                for (int i = 0; i < 256; i++) {
                    f.manager.dispatchSharedMutationNotification(() -> {
                        entered.countDown(); try { assertTrue(release.await(5, TimeUnit.SECONDS)); }
                        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    }); f.runOne();
                }
                assertTrue(entered.await(5, TimeUnit.SECONDS)); assertEquals(512, credits(f, "general")); assertEquals(256, credits(f, "cleanup"));
                first.run(); while (!f.tasks.isEmpty()) f.runOne(); legacyWork.forEach(Runnable::run);
                release.countDown();
                for (CompletionStage<?> checkpoint : checkpoints) {
                    checkpoint.toCompletableFuture().get(5, TimeUnit.SECONDS);
                    f.manager.afterSharedMutationFinalization(checkpoint).toCompletableFuture().get(5, TimeUnit.SECONDS);
                }
                // Unsupported legacy readers fail before provider work but still deliver every accepted result.
                for (CompletionStage<?> receipt : legacy) assertThrows(ExecutionException.class, () -> receipt.toCompletableFuture().get(5, TimeUnit.SECONDS));
                for (int i = 0; i < 256; i++) f.manager.settleSharedReplayLease(id, "lease-" + i, false, true);
                await(() -> { try { return credits(f, "general") == 0 && credits(f, "cleanup") == 0; } catch (Exception e) { throw new AssertionError(e); } });
                assertEquals(0, field(f.manager, "sharedIntegerAdmissions"));
            } finally { release.countDown(); }
        }
    }
    @Test void failedReloadDoesNotPoisonIndependentSuccessfulShutdown() throws Exception {
        try (var f = new Fixture()) {
            var next = mock(SqlUserBackend.class); when(next.isOpen()).thenReturn(true);
            var failure = new IllegalStateException("reload rejected");
            var reload = f.manager.replaceSharedSqlBackendAsync(next, () -> { throw failure; }).toCompletableFuture();
            f.runOne(); assertSame(failure, assertThrows(ExecutionException.class, () -> reload.get(5, TimeUnit.SECONDS)).getCause());
            AtomicInteger cleanup = new AtomicInteger();
            var shutdown = f.manager.closeSharedRuntimeAsyncCompletion(cleanup::incrementAndGet).toCompletableFuture();
            while (!f.tasks.isEmpty()) f.runOne(); shutdown.get(5, TimeUnit.SECONDS);
            assertEquals(1, cleanup.get()); verify(f.backend, times(1)).close();
        }
    }
    @Test void runningReplacementSubmissionThrowKeepsPrivateOwnerUntilPublicationExit() throws Exception {
        try (var f = new Fixture()) {
            var next = mock(SqlUserBackend.class); when(next.isOpen()).thenReturn(true);
            CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
            Thread[] worker = new Thread[1];
            doAnswer(c -> {
                worker[0] = new Thread((Runnable) c.getArgument(0)); worker[0].start();
                assertTrue(entered.await(5, TimeUnit.SECONDS)); throw new IllegalStateException("after entry");
            }).when(f.timer).execute(any(Runnable.class));
            try {
                var publicView = f.manager.replaceSharedSqlBackendAsync(next, () -> {
                    entered.countDown(); try { assertTrue(release.await(5, TimeUnit.SECONDS)); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
                }).toCompletableFuture();
                publicView.cancel(false);
                Object attempt = field(f.manager, "replacementAttempt");
                assertNotNull(attempt); assertFalse(((CompletableFuture<?>) field(attempt, "physical")).isDone());
                assertTrue((Boolean) field(f.manager, "sharedRuntimeReplacing"));
                doAnswer(c -> { f.tasks.add(c.getArgument(0)); return null; }).when(f.timer).execute(any(Runnable.class));
                var retirement = f.manager.closeSharedRuntimeAsyncCompletion(() -> {}).toCompletableFuture();
                assertFalse(retirement.isDone()); release.countDown(); worker[0].join(6000); assertFalse(worker[0].isAlive());
                while (!f.tasks.isEmpty()) f.runOne(); retirement.get(5, TimeUnit.SECONDS);
                verify(next, times(1)).close();
            } finally { release.countDown(); if (worker[0] != null) worker[0].join(6000); }
        }
    }
    @Test void overlappingReplacementFailureIsPrimaryOnlyAfterRetirement() throws Exception {
        try (var f = new Fixture()) {
            var next = mock(SqlUserBackend.class); when(next.isOpen()).thenReturn(true);
            var replacementFailure = new IllegalStateException("replacement");
            var physicalFailure = new IllegalStateException("retirement");
            var reload = f.manager.replaceSharedSqlBackendAsync(next, () -> { throw replacementFailure; });
            var retirement = f.manager.closeSharedRuntimeAsyncCompletion(() -> {}).toCompletableFuture();
            assertFalse(retirement.isDone());
            f.runOne(); assertTrue(reload.toCompletableFuture().isCompletedExceptionally()); assertFalse(retirement.isDone());
            doThrow(physicalFailure).when(f.owner).beginRetirement();
            while (!f.tasks.isEmpty()) f.runOne();
            assertSame(replacementFailure, assertThrows(ExecutionException.class, () -> retirement.get(5, TimeUnit.SECONDS)).getCause());
            assertTrue(Arrays.asList(replacementFailure.getSuppressed()).contains(physicalFailure));
        }
    }
    @Test void competingAdmissionCannotScheduleClaimedSuccessorTwice() throws Exception {
        try (var f = new Fixture()) {
            var runtime = mockTransactions(f); UUID id = UUID.randomUUID();
            var first = f.manager.submitSharedReplayTransaction(id, UserStorage.SQLITE, "one", Map.of(), tx -> null);
            var second = f.manager.submitSharedReplayTransaction(id, UserStorage.SQLITE, "two", Map.of(), tx -> null);
            Runnable firstWork = f.tasks.remove();
            CountDownLatch claimed = new CountDownLatch(1), release = new CountDownLatch(1);
            AtomicInteger submits = new AtomicInteger();
            doAnswer(c -> {
                if (submits.incrementAndGet() == 1) { claimed.countDown(); assertTrue(release.await(5, TimeUnit.SECONDS)); }
                f.tasks.add(c.getArgument(0)); return null;
            }).when(f.timer).execute(any(Runnable.class));
            var worker = new Thread(firstWork);
            try {
                worker.start(); assertTrue(claimed.await(5, TimeUnit.SECONDS));
                var third = f.manager.submitSharedReplayTransaction(id, UserStorage.SQLITE, "three", Map.of(), tx -> null);
                assertEquals(1, submits.get()); release.countDown(); worker.join(6000); assertFalse(worker.isAlive());
                while (!f.tasks.isEmpty()) f.runOne();
                first.toCompletableFuture().get(5, TimeUnit.SECONDS); second.toCompletableFuture().get(5, TimeUnit.SECONDS); third.toCompletableFuture().get(5, TimeUnit.SECONDS);
                assertEquals(2, submits.get()); verify(runtime, times(3)).transaction(eq(id), eq(UserStorage.SQLITE), anyMap(), any());
            } finally { release.countDown(); worker.join(6000); }
        }
    }
    @Test void lastDrainSettlementAlwaysWakesRetirement() throws Exception {
        try (var f = new Fixture()) {
            UUID id = UUID.randomUUID(); f.manager.acquireSharedReplayCheckpointLease(id, "last");
            CountDownLatch zero = new CountDownLatch(1), release = new CountDownLatch(1);
            CompletableFuture<Void> drain = new CompletableFuture<>() {
                @Override public boolean complete(Void value) {
                    assertFalse(Thread.holdsLock(f.manager)); zero.countDown();
                    try { assertTrue(release.await(5, TimeUnit.SECONDS)); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
                    return super.complete(value);
                }
            };
            set(f.manager, "sharedIntegerDrain", drain);
            Thread cancel = new Thread(() -> f.manager.cancelSharedReplayCheckpointReservation(id, "last"));
            try {
                cancel.start(); assertTrue(zero.await(5, TimeUnit.SECONDS));
                var retirement = f.manager.closeSharedRuntimeAsyncCompletion(() -> {}).toCompletableFuture();
                assertFalse(retirement.isDone()); release.countDown(); cancel.join(6000); assertFalse(cancel.isAlive());
                assertFalse(f.tasks.isEmpty(), "the exact drain settlement must wake the owner coordinator");
                while (!f.tasks.isEmpty()) f.runOne(); retirement.get(5, TimeUnit.SECONDS);
            } finally { release.countDown(); cancel.join(6000); }
        }
    }
    @Test void maintenanceAndRemovalRefuseBeforeEnteringPendingRetirementOwner() throws Exception {
        try (var f = new Fixture()) {
            var retirement = f.manager.closeSharedRuntimeAsyncCompletion(() -> {}).toCompletableFuture();
            assertFalse(retirement.isDone()); clearInvocations(f.owner, f.backend);
            assertThrows(IllegalStateException.class, () -> f.manager.runStorageMaintenance(() -> fail("maintenance entered")));
            assertThrows(IllegalStateException.class, () -> f.manager.removeUserData(UUID.randomUUID(), UserStorage.SQLITE));
            verify(f.owner, never()).requireBlockingAllowed(); verify(f.backend, never()).storageType();
            while (!f.tasks.isEmpty()) f.runOne(); retirement.get(5, TimeUnit.SECONDS);
        }
    }
    @Test void returningCheckpointRejectsSameLeaseRetryWithoutConsumingReservation() throws Exception {
        try (var f = new Fixture()) {
            var runtime = mockTransactions(f); UUID id = UUID.randomUUID(); String identity = "returning";
            f.manager.acquireSharedReplayCheckpointLease(id, identity);
            var receipt = f.manager.submitSharedReplayCheckpoint(id, UserStorage.SQLITE, identity, Map.of(), tx -> null);
            CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
            var finalized = f.manager.afterSharedMutationFinalization(receipt).thenRun(() -> {
                entered.countDown(); try { assertTrue(release.await(5, TimeUnit.SECONDS)); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            });
            try {
                f.runOne(); assertTrue(entered.await(5, TimeUnit.SECONDS));
                f.manager.acquireSharedReplayCheckpointLease(id, identity);
                assertEquals(1, field(f.manager, "cleanupHeadroom")); assertEquals(0, credits(f, "general"));
                assertThrows(RejectedExecutionException.class, () -> f.manager.submitSharedReplayCheckpoint(id, UserStorage.SQLITE, identity, Map.of(), tx -> null));
                assertEquals(0, credits(f, "general")); assertTrue(f.tasks.isEmpty());
                release.countDown(); finalized.toCompletableFuture().get(5, TimeUnit.SECONDS);
                await(() -> { try {
                    Object state = ((Map<?, ?>) field(f.manager, "sharedReplayLeases")).values().iterator().next();
                    synchronized (f.manager) { return !(Boolean) field(state, "returning"); }
                } catch (Exception e) { throw new AssertionError(e); } });
                // The failed admission did not consume the second reservation.
                var retry = f.manager.submitSharedReplayCheckpoint(id, UserStorage.SQLITE, identity, Map.of(), tx -> null);
                f.runOne(); retry.toCompletableFuture().get(5, TimeUnit.SECONDS);
                f.manager.afterSharedMutationFinalization(retry).toCompletableFuture().get(5, TimeUnit.SECONDS);
                f.manager.settleSharedReplayLease(id, identity, false, true); f.manager.settleSharedReplayLease(id, identity, false, true);
                verify(runtime, times(2)).transaction(eq(id), eq(UserStorage.SQLITE), anyMap(), any());
            } finally { release.countDown(); }
        }
    }
    @Test void failedDiagnosticCannotReplaceRunningRetirementAuthority() throws Exception {
        try (var f = new Fixture()) {
            System.Logger logger = mock(System.Logger.class);
            doThrow(new AssertionError("diagnostic failed")).when(logger).log(any(System.Logger.Level.class), anyString());
            set(f.runtime, "submissionLogger", logger);
            CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
            doAnswer(c -> { entered.countDown(); assertTrue(release.await(5, TimeUnit.SECONDS)); return null; }).when(f.owner).beginRetirement();
            Thread[] worker = new Thread[1];
            try {
                var retirement = assertDoesNotThrow(() -> f.runtime.closeAsync(task -> {
                    worker[0] = new Thread(task); worker[0].start();
                    try { assertTrue(entered.await(5, TimeUnit.SECONDS)); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
                    throw new RejectedExecutionException();
                })).toCompletableFuture();
                assertFalse(retirement.isDone()); release.countDown(); retirement.get(5, TimeUnit.SECONDS);
                verify(logger).log(any(System.Logger.Level.class), anyString()); verify(f.backend, times(1)).close();
            } finally { release.countDown(); if (worker[0] != null) worker[0].join(6000); }
        }
    }
    @Test void failedCapacityDiagnosticCannotEscapeNotificationSubmission() throws Exception {
        try (var f = new Fixture()) {
            System.Logger logger = mock(System.Logger.class);
            doThrow(new AssertionError("diagnostic failed")).when(logger).log(any(System.Logger.Level.class), anyString());
            set(f.manager, "completionLogger", logger);
            for (int i = 0; i < 512; i++) f.manager.dispatchSharedMutationNotification(() -> {});
            assertDoesNotThrow(() -> f.manager.dispatchSharedMutationNotification(() -> {}));
            assertEquals(512, credits(f, "general")); assertInstanceOf(RejectedExecutionException.class, f.manager.getLastDeferredStorageFailure());
            f.manager.closeSharedUserDataNotifications(); assertEquals(0, credits(f, "general"));
        }
    }
}
