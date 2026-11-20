package com.bencodez.advancedcore.tests.user;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.advancedcore.api.user.UserData;
import com.bencodez.advancedcore.api.user.UserManager;
import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.advancedcore.api.user.usercache.UserDataCache;
import com.bencodez.advancedcore.api.user.usercache.UserDataManager;
import com.bencodez.advancedcore.api.user.userstorage.sql.UserTable;
import com.bencodez.simpleapi.sql.data.DataValue;
import com.bencodez.simpleapi.sql.data.DataValueInt;

@Timeout(15)
class LegacyRejectedMutationFlushTest {
    @Test void definiteRejectionDoesNotSkipAnotherAcceptedMutationFlush() throws Exception {
        try (Fixture f = new Fixture()) {
            var rejected = f.submit(true);
            var accepted = f.submit(false);
            var retirement = f.manager.clearCacheAsyncCompletion().toCompletableFuture();
            assertFalse(retirement.isDone());
            f.runWorker();
            f.runWorker();
            assertThrows(ExecutionException.class, () -> rejected.get(2, TimeUnit.SECONDS));
            assertTrue(accepted.get(2, TimeUnit.SECONDS).applied());
            f.awaitFinalized(rejected, true);
            f.awaitFinalized(accepted, false);
            f.runFinalFlushIfScheduled();
            assertEquals(5, f.persisted.get(), "accepted queued value must reach the final writer despite another rejection");
            assertThrows(ExecutionException.class, () -> retirement.get(2, TimeUnit.SECONDS),
                    "flushing accepted changes must not hide the rejected mutation");
        }
    }

    @Test void failedFinalWriterRetainsAcceptedChangesAndBothFailures() throws Exception {
        try (Fixture f = new Fixture()) {
            var writeFailure = new IllegalStateException("final writer refused");
            doAnswer(call -> { f.writeAttempts.incrementAndGet(); throw writeFailure; }).when(f.data).setValues(any(HashMap.class));
            var rejected = f.submit(true);
            var accepted = f.submit(false);
            var retirement = f.manager.clearCacheAsyncCompletion().toCompletableFuture();
            f.runWorker(); f.runWorker();
            f.awaitFinalized(rejected, true); f.awaitFinalized(accepted, false);
            f.runFinalFlushIfScheduled();
            assertEquals(1, f.writeAttempts.get(), "final writer must be attempted after all definite outcomes settle");
            var failure = assertThrows(ExecutionException.class, () -> retirement.get(2, TimeUnit.SECONDS));
            assertSame(writeFailure, failure.getCause());
            assertTrue(writeFailure.getSuppressed().length > 0, "earlier rejection must remain available");
            assertSame(f.cache, f.manager.getUserDataCache().get(f.id));
            assertTrue(f.cache.hasChangesToProcess());
            assertEquals(1, f.persisted.get());
        }
    }

    @Test void successfulMutationRetirementStillFlushesAndCompletesNormally() throws Exception {
        try (Fixture f = new Fixture()) {
            var accepted = f.submit(false);
            var retirement = f.manager.clearCacheAsyncCompletion().toCompletableFuture();
            f.runWorker(); f.awaitFinalized(accepted, false); f.runWorker();
            retirement.get(2, TimeUnit.SECONDS);
            assertEquals(5, f.persisted.get());
            assertFalse(f.manager.getUserDataCache().containsKey(f.id));
        }
    }


    @Test void rejectedMutationRetirementSettlesWhenLoggingFails() throws Exception {
        try (var f = new Fixture()) {
            var rejected = f.submit(true); var accepted = f.submit(false);
            var retirement = f.manager.clearCacheAsyncCompletion().toCompletableFuture();
            f.runWorker(); f.runWorker(); f.awaitFinalized(rejected, true); f.awaitFinalized(accepted, false);
            failingLogger(f);
            assertDoesNotThrow(f::runFinalFlushIfScheduled);
            assertTrue(retirement.isDone()); assertThrows(ExecutionException.class, () -> retirement.get(2, TimeUnit.SECONDS));
            assertEquals(5, f.persisted.get());
        }
    }
    @Test void failedDependencyRetirementSettlesWhenLoggingFails() throws Exception { failedDrain(false); }
    @Test void rejectedSchedulingRetirementSettlesWhenLoggingFails() throws Exception { failedDrain(true); }
    private void failedDrain(boolean rejectScheduling) throws Exception {
        try (var f = new Fixture()) {
            Field count = UserDataManager.class.getDeclaredField("legacyDirectAdmissions"); count.setAccessible(true); count.set(f.manager, 1);
            Field drainField = UserDataManager.class.getDeclaredField("legacyDirectDrain"); drainField.setAccessible(true);
            var drain = new CompletableFuture<Void>(); drainField.set(f.manager, drain);
            var completion = f.manager.clearCacheAsyncCompletion().toCompletableFuture();
            var cause = new java.util.concurrent.RejectedExecutionException("retirement refused");
            failingLogger(f);
            if (rejectScheduling) {
                doThrow(cause).when(f.manager.getTimer()).execute(any(Runnable.class)); drain.complete(null);
            } else drain.completeExceptionally(cause);
            assertTrue(completion.isDone(), "diagnostics must not strand the retirement result");
            assertSame(cause, assertThrows(ExecutionException.class, () -> completion.get(2, TimeUnit.SECONDS)).getCause());
        }
    }
    private void failingLogger(Fixture f) {
        var logger = mock(java.util.logging.Logger.class); when(f.plugin.getLogger()).thenReturn(logger);
        doThrow(new AssertionError("logger failed")).when(logger).log(any(java.util.logging.Level.class), anyString(), any(Throwable.class));
    }

    private static final class Fixture implements AutoCloseable {
        final AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        final UserDataManager manager;
        final UserData data = mock(UserData.class);
        final UserDataCache cache;
        final UUID id = UUID.randomUUID();
        final LinkedBlockingQueue<Runnable> tasks = new LinkedBlockingQueue<>();
        final AtomicInteger persisted = new AtomicInteger(1);
        final AtomicInteger writeAttempts = new AtomicInteger();

        Fixture() throws Exception {
            var users = mock(UserManager.class); var user = mock(AdvancedCoreUser.class);
            var table = mock(UserTable.class);
            when(plugin.getStorageType()).thenReturn(UserStorage.SQLITE);
            when(plugin.getSQLiteUserTable()).thenReturn(table);
            when(plugin.getNativeUserStorageOwner()).thenReturn(new AdvancedCorePlugin.UserStorageOwner(UserStorage.SQLITE, null, table));
            when(plugin.getUserManager()).thenReturn(users);
            manager = new UserDataManager(plugin);
            when(users.getDataManager()).thenReturn(manager); when(users.getUser(id, false)).thenReturn(user);
            when(user.getUserData()).thenReturn(data);
            when(data.supportsStrictCompleteValues()).thenReturn(true);
            when(data.getValuesIfStrictComplete()).thenAnswer(call -> new HashMap<>(Map.of("Points", new DataValueInt(persisted.get()))));
            doAnswer(call -> { writeAttempts.incrementAndGet(); HashMap<String, DataValue> values = call.getArgument(0);
                persisted.set(values.get("Points").getInt()); return null; }).when(data).setValues(any(HashMap.class));
            cache = new UserDataCache(manager, id);
            cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("Points", new DataValueInt(1))));
            manager.getUserDataCache().put(id, cache);
            manager.getTimer().shutdownNow();
            var timer = mock(ScheduledExecutorService.class);
            doAnswer(call -> { tasks.add(call.getArgument(0)); return null; }).when(timer).execute(any(Runnable.class));
            doAnswer(call -> { tasks.add(call.getArgument(0)); return null; }).when(timer).schedule(any(Runnable.class), eq(0L), eq(TimeUnit.MILLISECONDS));
            Field field = UserDataManager.class.getDeclaredField("timer"); field.setAccessible(true); field.set(manager, timer);
        }

        CompletableFuture<UserDataCache.IntegerMutationResult> submit(boolean invalid) {
            return manager.submitLegacyIntegerMutation(id, Map.of(invalid ? "" : "Points", 4),
                    UserDataCache.IntegerMutation.ADD, null).toCompletableFuture();
        }
        void runWorker() throws Exception { var task = tasks.poll(2, TimeUnit.SECONDS); assertNotNull(task); task.run(); }
        void runFinalFlushIfScheduled() throws Exception { var task = tasks.poll(1, TimeUnit.SECONDS); if (task != null) task.run(); }
        void awaitFinalized(CompletableFuture<?> receipt, boolean failed) throws Exception {
            Field field = receipt.getClass().getDeclaredField("finalized"); field.setAccessible(true);
            var finalized = (CompletableFuture<?>) field.get(receipt);
            if (failed) assertThrows(ExecutionException.class, () -> finalized.get(2, TimeUnit.SECONDS));
            else finalized.get(2, TimeUnit.SECONDS);
        }
        public void close() throws Exception {
            tasks.clear();
            Field field = UserDataManager.class.getDeclaredField("mutationCallbacks"); field.setAccessible(true);
            var callbacks = (ThreadPoolExecutor) field.get(manager); callbacks.shutdownNow();
            assertTrue(callbacks.awaitTermination(2, TimeUnit.SECONDS));
        }
    }
}
