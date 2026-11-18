package com.bencodez.advancedcore.tests.user;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.advancedcore.api.user.usercache.UserDataManager;
import com.bencodez.advancedcore.core.user.runtime.SharedUserDataRuntime;
import com.bencodez.advancedcore.core.user.storage.sql.SqlUserBackend;

@Timeout(10)
class SharedReplayStorageAdmissionTest {
    @Test void sqliteOwnerRejectsMysqlReplayBeforeAdmission() throws Exception {
        rejectsWrongStore(UserStorage.SQLITE, UserStorage.MYSQL);
    }

    @Test void mysqlOwnerRejectsSqliteReplayBeforeAdmission() throws Exception {
        rejectsWrongStore(UserStorage.MYSQL, UserStorage.SQLITE);
    }

    private void rejectsWrongStore(UserStorage owned, UserStorage requested) throws Exception {
        try (Fixture f = new Fixture(owned)) {
            UUID id = UUID.randomUUID();
            AtomicBoolean invoked = new AtomicBoolean();
            assertThrows(IllegalStateException.class, () -> f.manager.submitSharedReplayTransaction(
                    id, requested, "wrong-store", Map.of(), scope -> { invoked.set(true); return null; }));
            assertTrue(f.tasks.isEmpty(), "definite rejection must not enqueue or retry work");
            assertTrue(f.manager.pendingSharedIntegerMutations(id).toCompletableFuture().isDone());
            assertFalse(invoked.get());
            verify(f.runtime, never()).transaction(any(UUID.class), any(), anyMap(), any());

            // A rejected request must not retain an admission that prevents retirement.
            f.manager.closeSharedRuntimeAsyncCompletion(() -> {}).toCompletableFuture().get(1, TimeUnit.SECONDS);
            verify(f.runtime).closeAsync(any());
        }
    }

    @Test void wrongStoreCheckpointPreservesItsReservationForTheCorrectStore() throws Exception {
        try (Fixture f = new Fixture(UserStorage.SQLITE)) {
            UUID id = UUID.randomUUID();
            String identity = "checkpoint-store-control";
            f.manager.acquireSharedReplayCheckpointLease(id, identity);
            assertThrows(IllegalStateException.class, () -> f.manager.submitSharedReplayCheckpoint(
                    id, UserStorage.MYSQL, identity, Map.of(), scope -> null));
            assertTrue(f.tasks.isEmpty());

            var receipt = f.manager.submitSharedReplayCheckpoint(id, UserStorage.SQLITE, identity,
                    Map.of(), scope -> null);
            assertEquals(1, f.tasks.size(), "correct retry must still own the checkpoint reservation");
            f.tasks.remove().run();
            receipt.toCompletableFuture().get(1, TimeUnit.SECONDS);
            f.manager.afterSharedMutationFinalization(receipt).toCompletableFuture().get(1, TimeUnit.SECONDS);
            f.manager.settleSharedReplayLease(id, identity, false, true);
            f.manager.closeSharedRuntimeAsyncCompletion(() -> {}).toCompletableFuture().get(1, TimeUnit.SECONDS);
            verify(f.runtime, times(1)).transaction(eq(id), eq(UserStorage.SQLITE), anyMap(), any());
        }
    }

    @Test void correctStoreReplayStillCompletesNormally() throws Exception {
        for (UserStorage owned : new UserStorage[] { UserStorage.SQLITE, UserStorage.MYSQL }) {
            try (Fixture f = new Fixture(owned)) {
                UUID id = UUID.randomUUID();
                when(f.runtime.transaction(eq(id), eq(owned), anyMap(), any())).thenReturn("confirmed");
                var receipt = f.manager.submitSharedReplayTransaction(id, owned, "matching-store",
                        Map.of(), scope -> "confirmed");
                assertEquals(1, f.tasks.size());
                f.tasks.remove().run();
                assertEquals("confirmed", receipt.toCompletableFuture().get(1, TimeUnit.SECONDS));
                f.manager.afterSharedMutationFinalization(receipt).toCompletableFuture().get(1, TimeUnit.SECONDS);
                f.manager.closeSharedRuntimeAsyncCompletion(() -> {}).toCompletableFuture().get(1, TimeUnit.SECONDS);
                verify(f.runtime).transaction(eq(id), eq(owned), anyMap(), any());
            }
        }
    }

    private static final class Fixture implements AutoCloseable {
        final UserDataManager manager = new UserDataManager(mock(AdvancedCorePlugin.class));
        final SharedUserDataRuntime runtime = mock(SharedUserDataRuntime.class);
        final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        final ScheduledExecutorService controlled = mock(ScheduledExecutorService.class);

        Fixture(UserStorage owned) throws Exception {
            SqlUserBackend backend = mock(SqlUserBackend.class);
            when(backend.storageType()).thenReturn(owned);
            when(backend.supportsAtomicUserTransactions()).thenReturn(true);
            when(runtime.backend()).thenReturn(backend);
            when(runtime.closeAsync(any())).thenReturn(CompletableFuture.completedFuture(null));
            manager.bindSharedRuntime(runtime);
            manager.getTimer().shutdownNow();
            Field timer = UserDataManager.class.getDeclaredField("timer");
            timer.setAccessible(true);
            timer.set(manager, controlled);
            doAnswer(call -> { tasks.add(call.getArgument(0)); return null; })
                    .when(controlled).execute(any(Runnable.class));
        }

        @Override public void close() throws Exception {
            tasks.clear();
            Field callbacks = UserDataManager.class.getDeclaredField("mutationCallbacks");
            callbacks.setAccessible(true);
            ThreadPoolExecutor executor = (ThreadPoolExecutor) callbacks.get(manager);
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(1, TimeUnit.SECONDS));
        }
    }
}
