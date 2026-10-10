package com.bencodez.advancedcore.tests.user;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import com.bencodez.advancedcore.core.runtime.AdvancedCoreRuntime;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.user.UserManager;
import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.advancedcore.api.user.usercache.UserDataManager;
import com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL;
import com.bencodez.advancedcore.bukkit.runtime.BukkitRuntimePlatform;
import com.bencodez.advancedcore.core.user.runtime.SharedUserDataRuntime;
import com.bencodez.advancedcore.core.user.runtime.UserCacheOwner;
import com.bencodez.advancedcore.core.user.storage.SqlUserStorage;
import com.bencodez.advancedcore.core.user.storage.sql.SqlUserBackend;

class PreStorageHookAdmissionTest {
    @Test void successfulHookInsideAdmissionFlushesBeforeNativeClose() throws Exception {
        completeInsideReadAdmission("success");
    }
    @Test void failedHookInsideAdmissionFlushesBeforeReportingFailure() throws Exception {
        completeInsideReadAdmission("failure");
    }
    @Test void cancelledHookInsideAdmissionStillRetiresStorage() throws Exception {
        completeInsideReadAdmission("cancelled");
    }

    @Test void successfulHookAfterWatchdogStillFlushesBeforeNativeClose() throws Exception {
        completeInsideReadAdmission("success", true);
    }
    @Test void failedHookAfterWatchdogStillFlushesBeforeReportingFailure() throws Exception {
        completeInsideReadAdmission("failure", true);
    }
    @Test void cancelledHookAfterWatchdogStillRetiresStorage() throws Exception {
        completeInsideReadAdmission("cancelled", true);
    }

    private void completeInsideReadAdmission(String mode) throws Exception {
        completeInsideReadAdmission(mode, false);
    }

    private void completeInsideReadAdmission(String mode, boolean runWatchdog) throws Exception {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        UserManager users = mock(UserManager.class);
        UserDataManager manager = new UserDataManager(plugin);
        MySQL nativeOwner = mock(MySQL.class);
        SqlUserBackend backend = mock(SqlUserBackend.class);
        SqlUserStorage storage = mock(SqlUserStorage.class);
        UserCacheOwner cache = mock(UserCacheOwner.class);
        UUID uuid = UUID.randomUUID();
        when(cache.cachedUsers()).thenReturn(Set.of(uuid));
        when(backend.storageType()).thenReturn(UserStorage.MYSQL);
        when(backend.isOpen()).thenReturn(true);
        when(backend.user(uuid)).thenReturn(storage);
        when(plugin.getNativeUserStorageOwner()).thenReturn(
                new AdvancedCorePlugin.UserStorageOwner(UserStorage.MYSQL, nativeOwner, null));
        when(plugin.isLoadUserData()).thenReturn(true);
        when(plugin.getLoadedUserManager()).thenReturn(users);
        when(users.getDataManager()).thenReturn(manager);
        var hook = new CompletableFuture<Void>();
        var preparationFailure = new IllegalStateException("preparation failed");
        when(plugin.onBeforeStorageShutdown()).thenReturn(hook);
        SharedUserDataRuntime runtime = new SharedUserDataRuntime(backend, cache);
        manager.bindSharedRuntime(runtime);
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
        BukkitRuntimePlatform platform = spy(new BukkitRuntimePlatform(plugin));
        var timedOut = new CountDownLatch(1);
        if (runWatchdog) {
            doReturn(false).when(platform).canBlockForPreExecutorShutdown();
            doReturn(20L).when(platform).deferredShutdownTimeoutMillis();
            doAnswer(call -> { timedOut.countDown(); return null; })
                    .when(platform).cleanupFailed(eq("pre-executor shutdown"),
                            any(java.util.concurrent.TimeoutException.class));
        }
        Runnable shutdown = platform.beforeExecutorShutdown().stream()
                .filter(cleanup -> cleanup.name().equals("user storage")).findFirst().orElseThrow().action();
        try {
            if (runWatchdog) {
                new AdvancedCoreRuntime(platform).shutdown();
                assertTrue(timedOut.await(2, TimeUnit.SECONDS));
                assertFalse(manager.getTimer().isShutdown(), "pending consumer preparation retains admission");
                verify(nativeOwner, never()).close();
                assertFalse(runtime.isRetiring());
            } else shutdown.run();
            var retirement = platform.beforeExecutorShutdownCompletion().toCompletableFuture();
            manager.getTimer().submit(() -> runtime.withStorageReadAdmission(() -> {
                if (mode.equals("success")) hook.complete(null);
                else if (mode.equals("failure")) hook.completeExceptionally(preparationFailure);
                else hook.cancel(false);
                assertFalse(runtime.isRetiring(), "retirement must wait for this callback to unwind");
                assertFalse(retirement.isDone());
                verify(nativeOwner, never()).close();
                // The callback still owns admission and can finish accepted work.
                runtime.withStorageReadAdmission(() -> null);
                return null;
            })).get(5, TimeUnit.SECONDS);

            if (mode.equals("success")) retirement.get(5, TimeUnit.SECONDS);
            else {
                assertThrows(java.util.concurrent.ExecutionException.class, () -> retirement.get(5, TimeUnit.SECONDS));
                CompletionException reported = assertThrows(CompletionException.class, retirement::join);
                if (mode.equals("failure")) assertSame(preparationFailure, reported.getCause());
                else assertInstanceOf(java.util.concurrent.CancellationException.class, reported.getCause());
            }
            var order = inOrder(cache, backend, nativeOwner);
            order.verify(cache).beginRetirement();
            order.verify(cache).flush(uuid, UserStorage.MYSQL, storage);
            order.verify(cache).clearAfterFlush();
            order.verify(cache).shutdown();
            order.verify(backend).close();
            order.verify(nativeOwner).close();
            shutdown.run();
            verify(backend, times(1)).close();
            verify(nativeOwner, times(1)).close();
            assertTrue(runtime.isClosed());
            assertFalse(manager.hasSharedRuntimeLifecycle());
            assertNull(manager.getLastDeferredStorageFailure());
            if (runWatchdog) assertTrue(manager.getTimer().awaitTermination(5, TimeUnit.SECONDS));
        } finally { manager.getTimer().shutdownNow(); }
    }

    @Test void rejectedContinuationDoesNotDetachRuntime() {
        rejectContinuation(false);
    }
    @Test void rejectedContinuationPreservesPreparationFailure() {
        rejectContinuation(true);
    }

    private void rejectContinuation(boolean failedPreparation) {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        UserManager users = mock(UserManager.class);
        UserDataManager manager = new UserDataManager(plugin);
        SqlUserBackend backend = mock(SqlUserBackend.class);
        SharedUserDataRuntime runtime = new SharedUserDataRuntime(backend, mock(UserCacheOwner.class));
        manager.bindSharedRuntime(runtime);
        when(plugin.isLoadUserData()).thenReturn(true);
        when(plugin.getLoadedUserManager()).thenReturn(users);
        when(users.getDataManager()).thenReturn(manager);
        var hook = new CompletableFuture<Void>();
        var preparationFailure = new IllegalStateException("preparation failed");
        when(plugin.onBeforeStorageShutdown()).thenReturn(hook);
        BukkitRuntimePlatform platform = new BukkitRuntimePlatform(plugin);
        try {
            platform.beforeExecutorShutdown().stream().filter(cleanup -> cleanup.name().equals("user storage"))
                    .findFirst().orElseThrow().action().run();
            manager.getTimer().shutdownNow();
            if (failedPreparation) hook.completeExceptionally(preparationFailure); else hook.complete(null);
            CompletionException reported = assertThrows(CompletionException.class,
                    () -> platform.beforeExecutorShutdownCompletion().toCompletableFuture().join());
            if (failedPreparation) {
                assertSame(preparationFailure, reported.getCause());
                assertEquals(1, reported.getSuppressed().length);
                assertInstanceOf(RejectedExecutionException.class, reported.getSuppressed()[0]);
            } else assertInstanceOf(RejectedExecutionException.class, reported.getCause());
            assertTrue(manager.hasSharedRuntime());
            assertFalse(runtime.isRetiring());
            verify(backend, never()).close();
        } finally { manager.getTimer().shutdownNow(); }
    }

    @Test void completedHookDoesNotSignalPreparationUntilRetirementIsAdmitted() throws Exception {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        UserManager users = mock(UserManager.class);
        UserDataManager manager = new UserDataManager(plugin);
        var admission = mock(java.util.concurrent.ScheduledExecutorService.class);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(plugin.isLoadUserData()).thenReturn(true);
        when(plugin.getLoadedUserManager()).thenReturn(users);
        when(users.getDataManager()).thenReturn(manager);
        var hook = new CompletableFuture<Void>();
        when(plugin.onBeforeStorageShutdown()).thenReturn(hook);
        BukkitRuntimePlatform platform = spy(new BukkitRuntimePlatform(plugin));
        doReturn(admission).when(platform).getUserStorageTimer();
        doAnswer(call -> {
            entered.countDown();
            assertTrue(release.await(2, TimeUnit.SECONDS));
            manager.getTimer().execute(call.getArgument(0, Runnable.class));
            return null;
        }).when(admission).execute(any(Runnable.class));
        var callback = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            platform.beforeExecutorShutdown().stream().filter(cleanup -> cleanup.name().equals("user storage"))
                    .findFirst().orElseThrow().action().run();
            var preparation = platform.storageRetirementPreparationCompletion().toCompletableFuture();
            var completing = callback.submit(() -> hook.complete(null));
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            assertTrue(hook.isDone(), "CompletableFuture becomes done before its callback unwinds");
            assertFalse(preparation.isDone(), "watchdog must retain admission during callback dispatch");
            assertFalse(manager.getTimer().isShutdown());
            release.countDown();
            completing.get(2, TimeUnit.SECONDS);
            preparation.get(2, TimeUnit.SECONDS);
            platform.beforeExecutorShutdownCompletion().toCompletableFuture().get(2, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            callback.shutdownNow();
            manager.getTimer().shutdownNow();
        }
    }

    @Test void delayedHookWithNoUserStorageRemainsSupported() {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        var hook = new CompletableFuture<Void>();
        when(plugin.onBeforeStorageShutdown()).thenReturn(hook);
        BukkitRuntimePlatform platform = new BukkitRuntimePlatform(plugin);
        platform.beforeExecutorShutdown().stream().filter(cleanup -> cleanup.name().equals("user storage"))
                .findFirst().orElseThrow().action().run();
        hook.complete(null);
        assertDoesNotThrow(() -> platform.beforeExecutorShutdownCompletion().toCompletableFuture().join());
        verify(plugin, never()).getLoadedUserManager();
    }
}
