package com.bencodez.advancedcore.tests.user;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.advancedcore.api.user.usercache.UserDataCache;
import com.bencodez.advancedcore.api.user.usercache.UserDataManager;
import com.bencodez.advancedcore.core.user.runtime.SharedUserDataRuntime;
import com.bencodez.advancedcore.core.user.storage.sql.SqlUserBackend;
import com.bencodez.simpleapi.sql.data.DataValueInt;

@Timeout(10)
class SharedReloadSynchronousAdmissionTest {
    @Test void pendingReplacementRejectsSynchronousArithmeticUntilItSettles() throws Exception {
        try (Fixture f = new Fixture()) {
            var cache = f.cache();
            var replacement = f.manager.replaceSharedSqlBackendAsync(f.next);
            assertFalse(replacement.toCompletableFuture().isDone());
            assertEquals(1, f.tasks.size());

            assertNull(cache.tryMutateIntegers(Map.of("Points", 1), UserDataCache.IntegerMutation.ADD, null),
                    "a replacement already admitted must exclude new synchronous writers");
            assertEquals(7, cache.getIfPresent("Points").getInt());
            assertEquals(0, f.writes.get());

            f.tasks.remove().run();
            replacement.toCompletableFuture().get(1, TimeUnit.SECONDS);
            assertTrue(cache.tryMutateIntegers(Map.of("Points", 1), UserDataCache.IntegerMutation.ADD, null).applied());
            assertEquals(8, cache.getIfPresent("Points").getInt());
        }
    }

    @Test void rejectedSubmissionCannotClearRetryAdmission() throws Exception {
        try (Fixture f = new Fixture()) {
            var cache = f.cache();
            AtomicInteger submissions = new AtomicInteger();
            AtomicReference<CompletionStage<Void>> retry = new AtomicReference<>();
            Field pending = UserDataManager.class.getDeclaredField("sharedRuntimeReplacement");
            pending.setAccessible(true);
            doAnswer(call -> {
                if (submissions.getAndIncrement() == 0) {
                    // Observe completion before the submitting call returns, as a concurrent
                    // replacement caller can. Retry at that exact completion boundary.
                    ((CompletionStage<?>) pending.get(f.manager)).whenComplete((value, failure) ->
                            retry.set(f.manager.replaceSharedSqlBackendAsync(f.next)));
                    throw new RejectedExecutionException("submission refused");
                }
                f.tasks.add(call.getArgument(0));
                return null;
            }).when(f.timer).execute(any(Runnable.class));

            var rejected = f.manager.replaceSharedSqlBackendAsync(f.next);
            assertTrue(rejected.toCompletableFuture().isCompletedExceptionally());
            assertNotNull(retry.get());
            assertFalse(retry.get().toCompletableFuture().isDone());
            assertEquals(1, f.tasks.size());
            assertNull(cache.tryMutateIntegers(Map.of("Points", 1), UserDataCache.IntegerMutation.ADD, null),
                    "rejected attempt cleanup must not clear its pending retry's admission");
            assertEquals(7, cache.getIfPresent("Points").getInt());
            f.tasks.remove().run();
            retry.get().toCompletableFuture().get(1, TimeUnit.SECONDS);
            assertTrue(cache.tryMutateIntegers(Map.of("Points", 1), UserDataCache.IntegerMutation.ADD, null).applied());
        }
    }

    @Test void failedReplacementRestoresSynchronousAdmission() throws Exception {
        try (Fixture f = new Fixture()) {
            var cache = f.cache();
            doThrow(new IllegalStateException("replacement refused")).when(f.runtime).replaceBackend(eq(f.next), any());
            var replacement = f.manager.replaceSharedSqlBackendAsync(f.next);
            f.tasks.remove().run();
            assertTrue(replacement.toCompletableFuture().isCompletedExceptionally());
            assertTrue(cache.tryMutateIntegers(Map.of("Points", 1), UserDataCache.IntegerMutation.ADD, null).applied());
            assertEquals(8, cache.getIfPresent("Points").getInt());
        }
    }

    @Test void synchronousArithmeticStillWorksWithoutReplacement() throws Exception {
        try (Fixture f = new Fixture()) {
            var cache = f.cache();
            assertTrue(cache.tryMutateIntegers(Map.of("Points", 1), UserDataCache.IntegerMutation.ADD, null).applied());
            assertEquals(8, cache.getIfPresent("Points").getInt());
            verify(f.runtime, never()).replaceBackend(any(), any());
        }
    }

    private static final class Fixture implements AutoCloseable {
        final UserDataManager manager = new UserDataManager(mock(AdvancedCorePlugin.class));
        final SharedUserDataRuntime runtime = mock(SharedUserDataRuntime.class);
        final SqlUserBackend next = mock(SqlUserBackend.class);
        final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        final ScheduledExecutorService timer = mock(ScheduledExecutorService.class);
        final AtomicInteger writes = new AtomicInteger();

        Fixture() throws Exception {
            var backend = mock(SqlUserBackend.class);
            when(backend.storageType()).thenReturn(UserStorage.SQLITE);
            when(backend.isOpen()).thenReturn(true);
            when(runtime.backend()).thenReturn(backend);
            when(next.isOpen()).thenReturn(true);
            manager.bindSharedRuntime(runtime);
            manager.getTimer().shutdownNow();
            doAnswer(call -> { tasks.add(call.getArgument(0)); return null; }).when(timer).execute(any(Runnable.class));
            Field field = UserDataManager.class.getDeclaredField("timer");
            field.setAccessible(true);
            field.set(manager, timer);
        }

        UserDataCache cache() {
            UUID id = UUID.randomUUID();
            var cache = new UserDataCache(manager, id);
            cache.configureSharedStorage(values -> writes.incrementAndGet(), Runnable::run, Runnable::run, UserStorage.SQLITE);
            cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("Points", new DataValueInt(7))));
            manager.getUserDataCache().put(id, cache);
            return cache;
        }

        @Override public void close() throws Exception {
            tasks.clear();
            Field field = UserDataManager.class.getDeclaredField("mutationCallbacks");
            field.setAccessible(true);
            var callbacks = (ThreadPoolExecutor) field.get(manager);
            callbacks.shutdownNow();
            assertTrue(callbacks.awaitTermination(1, TimeUnit.SECONDS));
        }
    }
}
