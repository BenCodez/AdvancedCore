package com.bencodez.advancedcore.tests.lifecycle;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.rewards.RewardHandler;
import com.bencodez.advancedcore.api.time.TimeChecker;
import com.bencodez.advancedcore.api.item.FullInventoryHandler;
import com.bencodez.advancedcore.api.user.UserManager;
import com.bencodez.advancedcore.api.user.usercache.UserDataManager;
import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL;
import com.bencodez.advancedcore.bukkit.runtime.BukkitRuntimePlatform;
import com.bencodez.advancedcore.core.platform.RuntimePlatform;
import com.bencodez.advancedcore.core.platform.RuntimePlatform.Cleanup;
import com.bencodez.advancedcore.core.runtime.AdvancedCoreRuntime;
import com.bencodez.advancedcore.core.user.runtime.SharedUserDataRuntime;
import com.bencodez.advancedcore.core.user.runtime.UserCacheOwner;
import com.bencodez.advancedcore.core.user.storage.SqlUserStorage;
import com.bencodez.advancedcore.core.user.storage.sql.SqlUserBackend;

class CoreRuntimeDeadlineTest {
    @org.junit.jupiter.api.io.TempDir java.io.File directory;
    private AdvancedCorePlugin rewardPlugin() {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        when(plugin.getDataFolder()).thenReturn(directory);
        return plugin;
    }

    @Test void overlappingExecutorAndActualRewardWaitsShareOneDisableDeadline() throws Exception {
        AtomicLong clock = new AtomicLong();
        RuntimePlatform platform = platform();
        ScheduledExecutorService login = timedExecutor(clock);
        ScheduledExecutorService storage = timedExecutor(clock);
        ScheduledExecutorService time = timedExecutor(clock);
        ScheduledExecutorService inventory = timedExecutor(clock);
        ScheduledExecutorService separateStorage = timedExecutor(clock);
        ScheduledExecutorService delayedRewards = timedExecutor(clock);
        RewardHandler rewards = mock(RewardHandler.class, withSettings().useConstructor(rewardPlugin()).defaultAnswer(CALLS_REAL_METHODS));
        var timerField = RewardHandler.class.getDeclaredField("delayedTimer");
        timerField.setAccessible(true);
        timerField.set(rewards, delayedRewards);
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        when(plugin.getRewardHandler()).thenReturn(rewards);
        BukkitRuntimePlatform bukkit = new BukkitRuntimePlatform(plugin);
        doAnswer(call -> { bukkit.beginShutdown(call.getArgument(0)); return null; })
                .when(platform).beginShutdown(any());
        when(platform.beforeExecutorShutdown()).thenReturn(List.of(
                new Cleanup("disable-entry cleanup", () -> clock.addAndGet(TimeUnit.SECONDS.toNanos(3)))));
        when(platform.getLoginTimer()).thenReturn(login);
        when(platform.getTimer()).thenReturn(storage);
        when(platform.getTimeTimer()).thenReturn(time);
        when(platform.getInventoryTimer()).thenReturn(inventory);
        when(platform.getUserStorageTimer()).thenReturn(separateStorage);
        when(platform.afterExecutorGrace()).thenReturn(bukkit.afterExecutorGrace());

        new AdvancedCoreRuntime(platform, clock::get).shutdown();

        assertEquals(TimeUnit.SECONDS.toNanos(15), clock.get());
        verify(login).awaitTermination(2, TimeUnit.SECONDS);
        verify(storage).awaitTermination(2, TimeUnit.SECONDS);
        verify(time).awaitTermination(2, TimeUnit.SECONDS);
        verify(inventory).awaitTermination(1, TimeUnit.SECONDS);
        verify(delayedRewards).awaitTermination(TimeUnit.SECONDS.toNanos(5), TimeUnit.NANOSECONDS);
        verify(delayedRewards).shutdownNow();
        verify(separateStorage, never()).awaitTermination(anyLong(), any());
        verify(separateStorage).shutdownNow();
    }

    @Test void preShutdownWaitIncludesTimeAlreadySpentSincePluginDisableEntry() {
        AtomicLong clock = new AtomicLong(TimeUnit.SECONDS.toNanos(14));
        AtomicLong requested = new AtomicLong();
        CompletableFuture<Void> pending = new CompletableFuture<>() {
            @Override public Void get(long timeout, TimeUnit unit) throws TimeoutException {
                requested.set(unit.toNanos(timeout));
                clock.addAndGet(unit.toNanos(timeout));
                throw new TimeoutException("still running");
            }
        };
        RuntimePlatform platform = platform();
        when(platform.beforeExecutorShutdownCompletion()).thenReturn(pending);

        new AdvancedCoreRuntime(platform, clock::get).shutdown(0L);

        assertEquals(TimeUnit.SECONDS.toNanos(1), requested.get());
        assertEquals(TimeUnit.SECONDS.toNanos(15), clock.get());
        assertFalse(pending.isDone(), "a deadline must not fabricate storage completion");
    }

    @Test void expiredDeferredDeadlineInterruptsBlockedDaemonWorkerWithoutClosingItsOwner() throws Exception {
        RuntimePlatform platform = platform();
        AtomicLong clock = new AtomicLong(TimeUnit.SECONDS.toNanos(15));
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch ownerClosed = new CountDownLatch(1);
        ScheduledExecutorService storage = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(
                AdvancedCoreRuntime.daemonThreads("deadline-storage-fixture"));
        CompletableFuture<Void> retirement = new CompletableFuture<>();
        storage.execute(() -> {
            assertTrue(Thread.currentThread().isDaemon());
            started.countDown();
            while (release.getCount() != 0) {
                try { release.await(); }
                catch (InterruptedException ignored) { interrupted.countDown(); }
            }
        });
        assertTrue(started.await(2, TimeUnit.SECONDS));
        storage.execute(() -> retirement.complete(null));
        when(platform.getUserStorageTimer()).thenReturn(storage);
        when(platform.beforeExecutorShutdownCompletion()).thenReturn(retirement);
        when(platform.canBlockForPreExecutorShutdown()).thenReturn(false);
        when(platform.afterStorageExecutorShutdown()).thenReturn(List.of(new Cleanup("native owner", ownerClosed::countDown)));
        try {
            new AdvancedCoreRuntime(platform, clock::get).shutdown(0L);
            assertTrue(interrupted.await(2, TimeUnit.SECONDS), "watchdog must not start a fresh five-second grace");
            assertFalse(storage.isTerminated());
            assertFalse(retirement.isDone());
            assertEquals(1L, ownerClosed.getCount(), "running JDBC retains its native owner");
        } finally {
            release.countDown();
            storage.shutdownNow();
        }
        assertTrue(ownerClosed.await(2, TimeUnit.SECONDS));
        assertFalse(retirement.isDone(), "discarded queued retirement remains observable as pending");
    }

    @Test void everySharedRuntimeExecutorUsesAnOwnedDaemonThread() throws Exception {
        var group = AdvancedCoreRuntime.createExecutors();
        try {
            for (ScheduledExecutorService executor : List.of(group.timer(), group.loginTimer(), group.inventoryTimer())) {
                assertTrue(executor.submit(() -> Thread.currentThread().isDaemon()).get(2, TimeUnit.SECONDS));
            }
        } finally {
            group.timer().shutdownNow(); group.loginTimer().shutdownNow(); group.inventoryTimer().shutdownNow();
        }
    }

    @Test void nativeProviderCloseWithoutManagerCannotBlockLifecycleCaller() throws Exception {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        MySQL mysql = mock(MySQL.class);
        when(plugin.isLoadUserData()).thenReturn(true);
        when(plugin.getNativeUserStorageOwner()).thenReturn(
                new AdvancedCorePlugin.UserStorageOwner(UserStorage.MYSQL, mysql, null));
        CountDownLatch closing = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(call -> {
            assertTrue(Thread.currentThread().isDaemon()); closing.countDown();
            assertTrue(release.await(2, TimeUnit.SECONDS)); return null;
        }).when(mysql).close();
        BukkitRuntimePlatform platform = new BukkitRuntimePlatform(plugin);
        try {
            platform.beforeExecutorShutdown().stream().filter(cleanup -> cleanup.name().equals("user storage"))
                    .findFirst().orElseThrow().action().run();
            assertTrue(closing.await(2, TimeUnit.SECONDS));
            assertFalse(platform.beforeExecutorShutdownCompletion().toCompletableFuture().isDone());
            release.countDown();
            platform.beforeExecutorShutdownCompletion().toCompletableFuture().get(2, TimeUnit.SECONDS);
            verify(plugin).closePendingNativeUserStorageOwners();
        } finally { release.countDown(); }
    }

    @Test void actualTimeAndDelayedRewardTimersAreDaemon() throws Exception {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class, RETURNS_DEEP_STUBS);
        when(plugin.getDataFolder()).thenReturn(directory);
        RewardHandler rewards = new RewardHandler(plugin);
        TimeChecker checker = new TimeChecker(plugin);
        try {
            checker.loadTimer();
            assertTrue(checker.getTimer().submit(() -> Thread.currentThread().isDaemon()).get(2, TimeUnit.SECONDS));
            assertTrue(rewards.getDelayedTimer().submit(() -> Thread.currentThread().isDaemon()).get(2, TimeUnit.SECONDS));
        } finally {
            if (checker.getTimer() != null) checker.getTimer().shutdownNow();
            rewards.shutdown(0, TimeUnit.NANOSECONDS);
        }
    }

    @Test void standaloneRewardShutdownKeepsItsExistingTenSecondGrace() throws Exception {
        RewardHandler rewards = mock(RewardHandler.class, withSettings().useConstructor(rewardPlugin()).defaultAnswer(CALLS_REAL_METHODS));
        ScheduledExecutorService timer = mock(ScheduledExecutorService.class);
        var timerField = RewardHandler.class.getDeclaredField("delayedTimer");
        timerField.setAccessible(true); timerField.set(rewards, timer);
        rewards.shutdown();
        verify(timer).shutdown();
        verify(timer).awaitTermination(10, TimeUnit.SECONDS);
        verify(timer).shutdownNow();
    }

    @Test void inventoryCompletionAfterDeadlineStillRunsTheActualRetirementFlushOnce() throws Exception {
        AtomicLong clock = new AtomicLong(TimeUnit.SECONDS.toNanos(15));
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        when(plugin.getLogger()).thenReturn(mock(java.util.logging.Logger.class));
        UserManager users = mock(UserManager.class);
        UserDataManager manager = new UserDataManager(plugin);
        when(plugin.isLoadUserData()).thenReturn(true);
        when(plugin.getLoadedUserManager()).thenReturn(users);
        when(users.getDataManager()).thenReturn(manager);
        FullInventoryHandler inventory = mock(FullInventoryHandler.class);
        CompletableFuture<Void> inventoryPersistence = new CompletableFuture<>();
        when(plugin.getFullInventoryHandler()).thenReturn(inventory);
        when(inventory.shutdownAsync(any())).thenReturn(inventoryPersistence);
        MySQL mysql = mock(MySQL.class);
        when(plugin.getNativeUserStorageOwner()).thenReturn(
                new AdvancedCorePlugin.UserStorageOwner(UserStorage.MYSQL, mysql, null));
        SqlUserBackend backend = mock(SqlUserBackend.class);
        SqlUserStorage storage = mock(SqlUserStorage.class);
        UserCacheOwner cache = mock(UserCacheOwner.class);
        java.util.UUID uuid = java.util.UUID.randomUUID();
        when(cache.cachedUsers()).thenReturn(java.util.Set.of(uuid));
        when(backend.storageType()).thenReturn(UserStorage.MYSQL);
        when(backend.user(uuid)).thenReturn(storage);
        SharedUserDataRuntime shared = new SharedUserDataRuntime(backend, cache);
        manager.bindSharedRuntime(shared);
        CountDownLatch preparationDeadline = new CountDownLatch(1);
        CountDownLatch flushing = new CountDownLatch(1);
        CountDownLatch releaseFlush = new CountDownLatch(1);
        doAnswer(call -> {
            assertTrue(Thread.currentThread().isDaemon()); flushing.countDown();
            assertTrue(releaseFlush.await(2, TimeUnit.SECONDS)); return null;
        }).when(cache).flush(uuid, UserStorage.MYSQL, storage);
        BukkitRuntimePlatform platform = spy(new BukkitRuntimePlatform(plugin));
        doReturn(false).when(platform).canBlockForPreExecutorShutdown();
        doAnswer(call -> {
            Throwable failure = call.getArgument(1);
            if (failure instanceof TimeoutException && failure.getMessage().startsWith("Consumer storage preparation")) {
                preparationDeadline.countDown();
            }
            return call.callRealMethod();
        }).when(platform).cleanupFailed(anyString(), any());
        try {
            new AdvancedCoreRuntime(platform, clock::get).shutdown(0L);
            assertTrue(preparationDeadline.await(2, TimeUnit.SECONDS));
            assertFalse(manager.getTimer().isShutdown());
            verify(cache, never()).flush(any(), any(UserStorage.class), any());
            inventoryPersistence.complete(null);
            assertTrue(flushing.await(2, TimeUnit.SECONDS), "late preparation must admit the real final cache flush");
            assertFalse(platform.beforeExecutorShutdownCompletion().toCompletableFuture().isDone());
            assertFalse(manager.getTimer().isShutdown(), "expired caller budget must not cancel the new checkpoint");
            verify(mysql, never()).close();
            assertEquals(TimeUnit.SECONDS.toNanos(15), clock.get(), "completion must not renew the caller's wait budget");
            releaseFlush.countDown();
            platform.beforeExecutorShutdownCompletion().toCompletableFuture().get(2, TimeUnit.SECONDS);
            assertTrue(manager.getTimer().awaitTermination(2, TimeUnit.SECONDS));
            verify(cache, times(1)).flush(uuid, UserStorage.MYSQL, storage);
            verify(backend, times(1)).close();
            verify(mysql, times(1)).close();
            assertTrue(shared.isClosed());
        } finally { releaseFlush.countDown(); manager.getTimer().shutdownNow(); }
    }

    private RuntimePlatform platform() {
        RuntimePlatform platform = mock(RuntimePlatform.class);
        // This optional native-admission signal defaults to null on real platforms.
        when(platform.admittedStorageRetirementCompletion()).thenReturn(null);
        when(platform.beforeExecutorShutdown()).thenReturn(List.of());
        when(platform.afterExecutorGrace()).thenReturn(List.of());
        when(platform.afterExecutorShutdown()).thenReturn(List.of());
        when(platform.afterStorageExecutorShutdown()).thenReturn(List.of());
        when(platform.canBlockForPreExecutorShutdown()).thenReturn(true);
        when(platform.deferredShutdownTimeoutMillis()).thenReturn(5_000L);
        return platform;
    }

    private ScheduledExecutorService timedExecutor(AtomicLong clock) throws Exception {
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        AtomicBoolean terminated = new AtomicBoolean();
        when(executor.awaitTermination(anyLong(), any())).thenAnswer(call -> {
            long duration = call.getArgument(0);
            TimeUnit unit = call.getArgument(1);
            clock.addAndGet(unit.toNanos(duration));
            return false;
        });
        when(executor.isTerminated()).thenAnswer(call -> terminated.get());
        when(executor.shutdownNow()).thenAnswer(call -> { terminated.set(true); return List.of(); });
        return executor;
    }
}
