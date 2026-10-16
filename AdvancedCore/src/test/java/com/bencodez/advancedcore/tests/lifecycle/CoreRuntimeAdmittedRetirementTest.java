package com.bencodez.advancedcore.tests.lifecycle;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.bukkit.Bukkit;
import org.bukkit.Server;

import com.bencodez.advancedcore.AdvancedCoreConfigOptions;
import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.item.FullInventoryHandler;
import com.bencodez.advancedcore.api.rewards.RewardHandler;
import com.bencodez.advancedcore.api.rewards.RewardOptions;
import com.bencodez.advancedcore.api.time.TimeChecker;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.advancedcore.api.user.PersistedQueueReference;
import com.bencodez.advancedcore.api.user.UserManager;
import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.advancedcore.api.user.usercache.UserDataManager;
import com.bencodez.advancedcore.api.user.UserData;
import com.bencodez.advancedcore.api.user.UserDataFetchMode;
import com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL;
import com.bencodez.advancedcore.bukkit.runtime.BukkitRuntimePlatform;
import com.bencodez.advancedcore.core.runtime.AdvancedCoreRuntime;
import com.bencodez.advancedcore.core.user.runtime.SharedUserDataRuntime;
import com.bencodez.advancedcore.core.user.runtime.UserCacheOwner;
import com.bencodez.advancedcore.core.user.storage.SqlUserStorage;
import com.bencodez.advancedcore.core.user.storage.sql.SqlUserBackend;

class CoreRuntimeAdmittedRetirementTest {
    @TempDir File directory;

    @Test void preparationJustBeforeDeadlineCannotDiscardAcceptedFinalRetirement() throws Exception {
        Fixture fixture = new Fixture();
        AtomicLong clock = new AtomicLong(TimeUnit.SECONDS.toNanos(14));
        CompletableFuture<Void> preparation = new CompletableFuture<>();
        FullInventoryHandler inventory = mock(FullInventoryHandler.class);
        when(fixture.plugin.getFullInventoryHandler()).thenReturn(inventory);
        when(inventory.shutdownAsync(any())).thenReturn(preparation);
        CountDownLatch occupied = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch preparationTimeout = new CountDownLatch(1);
        CountDownLatch retirementTimeout = new CountDownLatch(1);
        fixture.manager.getTimer().execute(() -> {
            assertTrue(Thread.currentThread().isDaemon());
            occupied.countDown();
            awaitIgnoringInterrupt(release);
        });
        assertTrue(occupied.await(2, TimeUnit.SECONDS));
        BukkitRuntimePlatform platform = fixture.platform();
        doReturn(1L).when(platform).deferredShutdownTimeoutMillis();
        doAnswer(call -> {
            Throwable failure = call.getArgument(1);
            if (failure instanceof TimeoutException) {
                if (failure.getMessage().startsWith("Consumer storage preparation")) preparationTimeout.countDown();
                if (failure.getMessage().startsWith("Deferred storage retirement")) retirementTimeout.countDown();
            }
            return null;
        }).when(platform).cleanupFailed(anyString(), any());
        try {
            new AdvancedCoreRuntime(platform, clock::get).shutdown(0L);
            assertTrue(preparationTimeout.await(2, TimeUnit.SECONDS));
            assertNull(platform.admittedStorageRetirementCompletion(), "pending preparation is not native-task admission");
            clock.set(TimeUnit.SECONDS.toNanos(15) - 1L);
            preparation.complete(null);
            assertNotNull(platform.admittedStorageRetirementCompletion());
            assertTrue(retirementTimeout.await(2, TimeUnit.SECONDS));
            assertFalse(platform.beforeExecutorShutdownCompletion().toCompletableFuture().isDone());
            assertFalse(fixture.shared.isClosed());
            verify(fixture.mysql, never()).close();
            release.countDown();
            assertDoesNotThrow(() -> platform.beforeExecutorShutdownCompletion().toCompletableFuture()
                    .get(2, TimeUnit.SECONDS), "accepted final retirement must survive the exhausted watchdog grace");
            assertTrue(fixture.manager.getTimer().awaitTermination(2, TimeUnit.SECONDS));
            verify(fixture.cache, times(1)).flush(fixture.uuid, UserStorage.MYSQL, fixture.storage);
            verify(fixture.backend, times(1)).close();
            verify(fixture.mysql, times(1)).close();
            assertTrue(fixture.shared.isClosed());
            assertEquals(TimeUnit.SECONDS.toNanos(15) - 1L, clock.get());
        } finally {
            release.countDown();
            fixture.manager.getTimer().shutdownNow();
        }
    }

    @Test void admittedTimedRewardFinishesBeforeNativeRetirementEvenAfterCallerDeadline() throws Exception {
        assertAdmittedTimedRewardFinishesBeforeNativeRetirement("normal");
    }

    @Test void failedPreparationStillWaitsForAdmittedTimedReward() throws Exception {
        assertAdmittedTimedRewardFinishesBeforeNativeRetirement("failed");
    }

    @Test void cancelledPreparationStillWaitsForAdmittedTimedReward() throws Exception {
        assertAdmittedTimedRewardFinishesBeforeNativeRetirement("cancelled");
    }

    @Test void failedTimeRetirementStillWaitsForAdmittedTimedReward() throws Exception {
        assertAdmittedTimedRewardFinishesBeforeNativeRetirement("time-failed");
    }

    @Test void cancelledTimeRetirementStillWaitsForAdmittedTimedReward() throws Exception {
        assertAdmittedTimedRewardFinishesBeforeNativeRetirement("time-cancelled");
    }

    @Test void admittedExternalTimedCheckDrainsBeforeReplaySnapshot() throws Exception {
        assertAdmittedTimedRewardFinishesBeforeNativeRetirement("external");
    }

    private void assertAdmittedTimedRewardFinishesBeforeNativeRetirement(String preparationOutcome)
            throws Exception {
        Fixture fixture = new Fixture();
        AtomicLong clock = new AtomicLong(TimeUnit.SECONDS.toNanos(15));
        RewardHandler rewards = spy(new RewardHandler(fixture.plugin));
        when(fixture.plugin.getRewardHandler()).thenReturn(rewards);
        AdvancedCoreUser user = new AdvancedCoreUser(fixture.plugin, fixture.uuid, "shutdown-fixture");
        UserData data = mock(UserData.class);
        user.setData(data);
        CountDownLatch reading = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean nativeClosed = new AtomicBoolean();
        doAnswer(call -> { nativeClosed.set(true); return null; }).when(fixture.mysql).close();
        ArrayList<String> persisted = new ArrayList<>(List.of("ShutdownReward%ExecutionTime/%1"));
        when(data.getStringList("TimedRewards", UserDataFetchMode.DEFAULT)).thenAnswer(call -> {
            reading.countDown();
            while (release.getCount() != 0L) {
                try { release.await(); }
                catch (InterruptedException ignored) { interrupted.countDown(); }
            }
            assertFalse(nativeClosed.get(), "admitted timed reward cannot read after its native owner closes");
            return new ArrayList<>(persisted);
        });
        doAnswer(call -> {
            assertFalse(nativeClosed.get(), "claim/checkpoint/acknowledgement retain native storage ownership");
            persisted.clear();
            persisted.addAll(call.getArgument(1));
            return null;
        }).when(data).setStringList(eq("TimedRewards"), any(), anyBoolean());
        doAnswer(call -> {
            assertFalse(nativeClosed.get(), "timed acknowledgement retains native storage ownership");
            persisted.clear();
            persisted.addAll(call.getArgument(1));
            return null;
        }).when(data).setStringList(eq("TimedRewards"), any());
        doReturn(CompletableFuture.completedFuture(null)).when(rewards)
                .givePersistedQueueRewardAsync(eq(user), any(PersistedQueueReference.class), any(RewardOptions.class));
        CompletableFuture<Void> hook = new CompletableFuture<>();
        if (preparationOutcome.equals("normal") || preparationOutcome.equals("external") || preparationOutcome.startsWith("time-")) hook.complete(null);
        else if (preparationOutcome.equals("failed")) hook.completeExceptionally(new IllegalStateException("consumer failed"));
        else hook.cancel(false);
        when(fixture.plugin.onBeforeStorageShutdown()).thenReturn(hook);
        if (preparationOutcome.startsWith("time-")) {
            TimeChecker checker = mock(TimeChecker.class);
            CompletableFuture<Void> timeRetirement = new CompletableFuture<>();
            if (preparationOutcome.equals("time-failed")) {
                timeRetirement.completeExceptionally(new IllegalStateException("time retirement failed"));
            } else timeRetirement.cancel(false);
            when(fixture.plugin.getTimeChecker()).thenReturn(checker);
            when(checker.beginShutdown()).thenReturn(timeRetirement);
        }
        var external = java.util.concurrent.Executors.newSingleThreadExecutor(
                AdvancedCoreRuntime.daemonThreads("external-admitted-replay"));
        var execution = preparationOutcome.equals("external")
                ? external.submit(user::checkDelayedTimedRewards)
                : rewards.getDelayedTimer().submit(user::checkDelayedTimedRewards);
        assertTrue(reading.await(2, TimeUnit.SECONDS));
        BukkitRuntimePlatform platform = fixture.platform();
        try {
            new AdvancedCoreRuntime(platform, clock::get).shutdown(0L);
            if (!preparationOutcome.equals("external")) {
                assertTrue(interrupted.await(2, TimeUnit.SECONDS), "zero caller budget still cancels queued timer work");
            }
            // A real manager-worker barrier removes the race with the old native
            // retirement task. The fixed path keeps admission open for the held reward.
            fixture.manager.getTimer().submit(() -> null).get(2, TimeUnit.SECONDS);
            assertFalse(fixture.shared.isClosed());
            assertFalse(platform.beforeExecutorShutdownCompletion().toCompletableFuture().isDone());
            verify(fixture.mysql, never()).close();
            release.countDown();
            execution.get(2, TimeUnit.SECONDS);
            if (preparationOutcome.equals("normal") || preparationOutcome.equals("external")) {
                platform.beforeExecutorShutdownCompletion().toCompletableFuture().get(2, TimeUnit.SECONDS);
            } else {
                assertThrows(java.util.concurrent.ExecutionException.class,
                        () -> platform.beforeExecutorShutdownCompletion().toCompletableFuture().get(2, TimeUnit.SECONDS));
            }
            assertTrue(fixture.manager.getTimer().awaitTermination(2, TimeUnit.SECONDS));
            verify(rewards, times(1)).givePersistedQueueRewardAsync(eq(user),
                    any(PersistedQueueReference.class), any(RewardOptions.class));
            assertTrue(persisted.isEmpty(), "the actual timed queue acknowledgement finishes before final flush");
            verify(fixture.cache, times(1)).flush(fixture.uuid, UserStorage.MYSQL, fixture.storage);
            verify(fixture.mysql, times(1)).close();
            assertEquals(TimeUnit.SECONDS.toNanos(15), clock.get(), "accepted work does not renew caller waits");
        } finally {
            release.countDown();
            external.shutdownNow();
            rewards.shutdown(0, TimeUnit.NANOSECONDS);
            fixture.manager.getTimer().shutdownNow();
        }
    }

    private static void awaitIgnoringInterrupt(CountDownLatch release) {
        while (release.getCount() != 0L) {
            try { release.await(); }
            catch (InterruptedException ignored) { /* Model an admitted noncooperative storage call. */ }
        }
    }

    @Test void detachedTimedAcknowledgementRetainsStorageAfterDelayedWorkerExits() throws Exception {
        assertDetachedTimedAcknowledgementRetainsStorage(false, false);
    }

    @Test void spigotDeferredTimedCheckRetainsItsDetachedAcknowledgement() throws Exception {
        assertDetachedTimedAcknowledgementRetainsStorage(true, false);
    }

    @Test void failedDetachedTimedDispatchRestoresItsQueueBeforeNativeRetirement() throws Exception {
        assertDetachedTimedAcknowledgementRetainsStorage(false, true);
    }

    private void assertDetachedTimedAcknowledgementRetainsStorage(boolean deferredCheck, boolean failedDispatch)
            throws Exception {
        Fixture fixture = new Fixture();
        AtomicLong clock = new AtomicLong(TimeUnit.SECONDS.toNanos(15));
        RewardHandler rewards = spy(new RewardHandler(fixture.plugin));
        when(fixture.plugin.getRewardHandler()).thenReturn(rewards);
        AdvancedCoreUser user = new AdvancedCoreUser(fixture.plugin, fixture.uuid, "async-shutdown-fixture");
        UserData data = mock(UserData.class);
        user.setData(data);
        ArrayList<String> persisted = new ArrayList<>(List.of("ShutdownReward%ExecutionTime/%1"));
        AtomicBoolean nativeClosed = new AtomicBoolean();
        doAnswer(call -> { nativeClosed.set(true); return null; }).when(fixture.mysql).close();
        when(data.getStringList("TimedRewards", UserDataFetchMode.DEFAULT))
                .thenAnswer(call -> new ArrayList<>(persisted));
        CountDownLatch acknowledging = new CountDownLatch(1);
        CountDownLatch releaseAcknowledgement = new CountDownLatch(1);
        doAnswer(call -> {
            persisted.clear();
            persisted.addAll(call.getArgument(1));
            return null;
        }).when(data).setStringList(eq("TimedRewards"), any(), anyBoolean());
        doAnswer(call -> {
            acknowledging.countDown();
            awaitIgnoringInterrupt(releaseAcknowledgement);
            assertFalse(nativeClosed.get(), "detached timed callback retains its native storage owner");
            persisted.clear();
            persisted.addAll(call.getArgument(1));
            return null;
        }).when(data).setStringList(eq("TimedRewards"), any());
        CompletableFuture<Void> dispatch = new CompletableFuture<>();
        CountDownLatch dispatched = new CountDownLatch(1);
        doAnswer(call -> { dispatched.countDown(); return dispatch; }).when(rewards)
                .givePersistedQueueRewardAsync(eq(user), any(PersistedQueueReference.class), any(RewardOptions.class));
        var checking = rewards.getDelayedTimer().submit(() -> {
            if (deferredCheck) {
                // Exercise Spigot's caller classification on the actual delayed
                // worker; the manager's own worker remains the storage owner.
                try (var bukkit = mockStatic(Bukkit.class)) {
                    bukkit.when(Bukkit::getServer).thenReturn(mock(Server.class));
                    bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
                    assertTrue(fixture.manager.mustDeferSharedStorageAccess());
                    user.checkDelayedTimedRewards();
                }
            } else user.checkDelayedTimedRewards();
        });
        checking.get(2, TimeUnit.SECONDS);
        assertTrue(dispatched.await(2, TimeUnit.SECONDS));
        BukkitRuntimePlatform platform = fixture.platform();
        var callback = java.util.concurrent.Executors.newSingleThreadExecutor(
                AdvancedCoreRuntime.daemonThreads("held-timed-acknowledgement"));
        try {
            new AdvancedCoreRuntime(platform, clock::get).shutdown(0L);
            fixture.manager.getTimer().submit(() -> null).get(2, TimeUnit.SECONDS);
            assertTrue(rewards.getDelayedTimer().isTerminated(), "worker exit alone does not finish replay");
            assertFalse(platform.beforeExecutorShutdownCompletion().toCompletableFuture().isDone());
            verify(fixture.mysql, never()).close();
            var acknowledgingCallback = callback.submit(() -> {
                if (failedDispatch) dispatch.completeExceptionally(new IllegalStateException("dispatch failed"));
                else dispatch.complete(null);
            });
            assertTrue(acknowledging.await(2, TimeUnit.SECONDS));
            assertFalse(platform.beforeExecutorShutdownCompletion().toCompletableFuture().isDone(),
                    "completed dispatch future is not a completed queue acknowledgement");
            verify(fixture.mysql, never()).close();
            releaseAcknowledgement.countDown();
            acknowledgingCallback.get(2, TimeUnit.SECONDS);
            platform.beforeExecutorShutdownCompletion().toCompletableFuture().get(2, TimeUnit.SECONDS);
            assertTrue(fixture.manager.getTimer().awaitTermination(2, TimeUnit.SECONDS));
            if (failedDispatch) {
                assertEquals(1, persisted.size(), "failed dispatch restores a durable retry instead of acknowledging");
                assertTrue(persisted.get(0).contains("%asyncretry%"));
            } else assertTrue(persisted.isEmpty());
            verify(fixture.cache, times(1)).flush(fixture.uuid, UserStorage.MYSQL, fixture.storage);
            verify(fixture.mysql, times(1)).close();
            assertEquals(TimeUnit.SECONDS.toNanos(15), clock.get());
        } finally {
            releaseAcknowledgement.countDown();
            callback.shutdownNow();
            rewards.shutdown(0, TimeUnit.NANOSECONDS);
            fixture.manager.getTimer().shutdownNow();
        }
    }

    @Test void consumerCallbackCannotStartNewTimedReplayAfterShutdownFence() throws Exception {
        assertConsumerCannotStartLateReplay("timed");
    }

    @Test void consumerCallbackCannotStartNewOfflineReplayAfterShutdownFence() throws Exception {
        assertConsumerCannotStartLateReplay("offline");
    }

    @Test void consumerCallbackCannotForceNewOfflineReplayAfterShutdownFence() throws Exception {
        assertConsumerCannotStartLateReplay("forced");
    }

    private void assertConsumerCannotStartLateReplay(String kind) throws Exception {
        Fixture fixture = new Fixture();
        when(fixture.backend.isOpen()).thenReturn(true);
        RewardHandler rewards = spy(new RewardHandler(fixture.plugin));
        when(fixture.plugin.getRewardHandler()).thenReturn(rewards);
        AdvancedCoreUser user = new AdvancedCoreUser(fixture.plugin, fixture.uuid, "late-replay-fixture");
        UserData data = mock(UserData.class);
        user.setData(data);
        when(fixture.plugin.getOptions().isProcessRewards()).thenReturn(true);
        when(fixture.users.getOfflineRewardsPath()).thenReturn("OfflineRewards");
        String key = kind.equals("timed") ? "TimedRewards" : "OfflineRewards";
        String entry = kind.equals("timed") ? "ShutdownReward%ExecutionTime/%1" : "ShutdownReward";
        ArrayList<String> persisted = new ArrayList<>(List.of(entry));
        when(data.getStringList(key, UserDataFetchMode.DEFAULT))
                .thenAnswer(call -> new ArrayList<>(persisted));
        doAnswer(call -> {
            persisted.clear();
            persisted.addAll(call.getArgument(1));
            return null;
        }).when(data).setStringList(eq(key), any(), anyBoolean());
        CompletableFuture<Void> dispatch = new CompletableFuture<>();
        doReturn(dispatch).when(rewards).givePersistedQueueRewardAsync(eq(user),
                any(PersistedQueueReference.class), any(RewardOptions.class));
        CompletableFuture<Void> hook = new CompletableFuture<>();
        when(fixture.plugin.onBeforeStorageShutdown()).thenReturn(hook);
        CountDownLatch admitted = new CountDownLatch(1);
        CountDownLatch finishHook = new CountDownLatch(1);
        var callback = java.util.concurrent.Executors.newSingleThreadExecutor(
                AdvancedCoreRuntime.daemonThreads("late-replay-consumer"));
        BukkitRuntimePlatform platform = fixture.platform();
        try {
            var execution = callback.submit(() -> fixture.shared.withStorageReadAdmission(() -> {
                admitted.countDown();
                awaitIgnoringInterrupt(finishHook);
                hook.complete(null);
                // The completion callback is still admitted to storage, but
                // shutdown has already fenced admission of fresh replay work.
                if (kind.equals("timed")) user.checkDelayedTimedRewards();
                else if (kind.equals("offline")) user.checkOfflineRewards();
                else user.forceRunOfflineRewardsAsync().toCompletableFuture().join();
                return null;
            }));
            assertTrue(admitted.await(2, TimeUnit.SECONDS));
            new AdvancedCoreRuntime(platform, () -> TimeUnit.SECONDS.toNanos(15)).shutdown(0L);
            finishHook.countDown();
            execution.get(2, TimeUnit.SECONDS);
            verify(rewards, never()).givePersistedQueueRewardAsync(eq(user),
                    any(PersistedQueueReference.class), any(RewardOptions.class));
            assertEquals(List.of(entry), persisted,
                    "late replay remains durable for the next enable instead of extending a snapshotted tail");
            platform.beforeExecutorShutdownCompletion().toCompletableFuture().get(2, TimeUnit.SECONDS);
            verify(fixture.mysql, times(1)).close();
        } finally {
            finishHook.countDown();
            dispatch.complete(null);
            callback.shutdownNow();
            rewards.shutdown(0, TimeUnit.NANOSECONDS);
            fixture.manager.getTimer().shutdownNow();
        }
    }

    private final class Fixture {
        final AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        final UserManager users = mock(UserManager.class);
        final UserDataManager manager = new UserDataManager(plugin);
        final SqlUserBackend backend = mock(SqlUserBackend.class);
        final SqlUserStorage storage = mock(SqlUserStorage.class);
        final UserCacheOwner cache = mock(UserCacheOwner.class);
        final MySQL mysql = mock(MySQL.class);
        final UUID uuid = UUID.randomUUID();
        final SharedUserDataRuntime shared;

        Fixture() {
            when(plugin.getLogger()).thenReturn(mock(Logger.class));
            when(plugin.getDataFolder()).thenReturn(directory);
            when(plugin.getOptions()).thenReturn(mock(AdvancedCoreConfigOptions.class));
            when(plugin.getOptions().isOnlineMode()).thenReturn(true);
            when(plugin.isLoadUserData()).thenReturn(true);
            when(plugin.getLoadedUserManager()).thenReturn(users);
            when(plugin.getUserManager()).thenReturn(users);
            when(users.getDataManager()).thenReturn(manager);
            when(plugin.getNativeUserStorageOwner()).thenReturn(
                    new AdvancedCorePlugin.UserStorageOwner(UserStorage.MYSQL, mysql, null));
            when(cache.cachedUsers()).thenReturn(java.util.Set.of(uuid));
            when(backend.storageType()).thenReturn(UserStorage.MYSQL);
            when(backend.user(uuid)).thenReturn(storage);
            shared = new SharedUserDataRuntime(backend, cache);
            manager.bindSharedRuntime(shared);
            manager.bindSharedSqlBackend(backend, operation -> shared.withStorageReadAdmission(() -> {
                operation.run();
                return null;
            }));
        }

        BukkitRuntimePlatform platform() {
            BukkitRuntimePlatform platform = spy(new BukkitRuntimePlatform(plugin));
            doReturn(false).when(platform).canBlockForPreExecutorShutdown();
            return platform;
        }
    }
}
