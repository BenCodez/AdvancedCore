package com.bencodez.advancedcore.tests.api.rewards;

import com.bencodez.advancedcore.api.rewards.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import org.bukkit.GameMode;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;

class RewardEligibilityTest {
    @Test
    void allowlistAndDenylistUseTheOnlinePlayerMode() {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        AdvancedCoreUser user = mock(AdvancedCoreUser.class);
        Player player = mock(Player.class);
        when(user.getPlugin()).thenReturn(plugin);
        when(user.getPlayer()).thenReturn(player);
        when(player.isOnline()).thenReturn(true);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        RewardEligibility eligibility = EligibilityAccess.create(plugin, mock(RewardHandler.class));
        YamlConfiguration data = new YamlConfiguration();
        data.set("GameModes", List.of("SURVIVAL"));
        assertTrue(eligibility.isEligible(data, user));
        data.set("BlackListedGameModes", List.of("survival"));
        assertFalse(eligibility.isEligible(data, user));
    }

    @Test
    void missingConfigurationAndMalformedValuesAreHandledSafely() {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        AdvancedCoreUser user = mock(AdvancedCoreUser.class);
        RewardEligibility eligibility = EligibilityAccess.create(plugin, mock(RewardHandler.class));
        YamlConfiguration data = new YamlConfiguration();
        assertTrue(eligibility.isEligible(data, user));
        data.set("GameModes", List.of("UNKNOWN"));
        assertFalse(eligibility.isEligible(data, user));
        data.set("GameModes", "SURVIVAL");
        assertFalse(eligibility.isEligible(data, user));
        verify(user, never()).getPlayer();
    }

    @Test
    void ownerSignalDoesNotWaitForStorageOwnershipMonitor() throws Exception {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        AdvancedCoreUser user = mock(AdvancedCoreUser.class);
        RewardHandler handler = mock(RewardHandler.class);
        var storage = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
        var owner = java.util.concurrent.Executors.newSingleThreadExecutor();
        when(plugin.isEnabled()).thenReturn(true);
        when(plugin.getTimer()).thenReturn(storage);
        when(handler.getDelayedTimer()).thenReturn(storage);
        when(user.getPlugin()).thenReturn(plugin);
        when(user.getUUID()).thenReturn("synthetic-user");
        Player player = mock(Player.class);
        when(user.getPlayer()).thenReturn(player);
        when(player.isOnline()).thenReturn(true);
        when(user.hasPendingRewardReplay()).thenAnswer(call -> {
            synchronized (plugin) { return false; }
        });
        when(user.checkOfflineRewardsAsync()).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));
        org.bukkit.metadata.MetadataValue accepted = mock(org.bukkit.metadata.MetadataValue.class);
        when(accepted.getOwningPlugin()).thenReturn(plugin);
        when(accepted.value()).thenReturn(Boolean.TRUE);
        when(player.getMetadata(anyString())).thenReturn(List.of(accepted));
        RewardEligibility eligibility = EligibilityAccess.create(plugin, handler);
        EligibilityAccess.deferred(eligibility, user);
        try {
            java.util.concurrent.CompletionStage<Void> completion;
            synchronized (plugin) {
                completion = owner.submit(() -> eligibility.signal(user)).get(1, java.util.concurrent.TimeUnit.SECONDS);
                assertFalse(completion.toCompletableFuture().isDone(),
                        "Ownership observation must wait on the worker while the player owner returns");
            }
            completion.toCompletableFuture().get(2, java.util.concurrent.TimeUnit.SECONDS);
        } finally {
            eligibility.close();
            owner.shutdownNow();
            storage.shutdownNow();
        }
    }

    @Test
    void rejectedWorkerCompletesSignalWithoutTouchingStorageOnCaller() {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        AdvancedCoreUser user = mock(AdvancedCoreUser.class);
        RewardHandler handler = mock(RewardHandler.class);
        var worker = mock(java.util.concurrent.ScheduledExecutorService.class);
        when(plugin.isEnabled()).thenReturn(true);
        when(plugin.getTimer()).thenReturn(worker);
        when(handler.getDelayedTimer()).thenReturn(worker);
        when(user.getPlugin()).thenReturn(plugin);
        when(user.getUUID()).thenReturn("synthetic-rejected-user");
        doThrow(new java.util.concurrent.RejectedExecutionException()).when(worker).execute(any());
        RewardEligibility eligibility = EligibilityAccess.create(plugin, handler);
        try {
            assertTrue(eligibility.signal(user).toCompletableFuture().isCompletedExceptionally());
            verify(user, never()).hasPendingRewardReplay();
            verify(user, never()).checkOfflineRewardsAsync();
        } finally { eligibility.close(); }
    }

    @Test
    void closedSignalCannotResumeItsDiscardedWorkerCallback() {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        AdvancedCoreUser user = mock(AdvancedCoreUser.class);
        RewardHandler handler = mock(RewardHandler.class);
        var worker = mock(java.util.concurrent.ScheduledExecutorService.class);
        var queued = new java.util.concurrent.atomic.AtomicReference<Runnable>();
        when(plugin.isEnabled()).thenReturn(true);
        when(plugin.getTimer()).thenReturn(worker);
        when(handler.getDelayedTimer()).thenReturn(worker);
        when(user.getPlugin()).thenReturn(plugin);
        when(user.getUUID()).thenReturn("synthetic-closed-user");
        doAnswer(call -> { queued.set(call.getArgument(0)); return null; }).when(worker).execute(any());
        RewardEligibility eligibility = EligibilityAccess.create(plugin, handler);
        var completion = eligibility.signal(user).toCompletableFuture();
        assertFalse(completion.isDone());
        eligibility.close();
        assertTrue(completion.isCompletedExceptionally());
        queued.get().run();
        verify(user, never()).hasPendingRewardReplay();
        verify(user, never()).checkOfflineRewardsAsync();
    }
    @Test
    void fallbackDoesNotReplayBeforeItsFirstDeadline() {
        try (SignalFixture fixture = new SignalFixture()) {
            EligibilityAccess.queued(fixture.eligibility, fixture.user);
            fixture.sweep.get().run();
            verify(fixture.user, never()).checkOfflineRewardsAsync();
        }
    }

    @Test
    void explicitSignalChecksDueTimedRewardsAlongsideOfflineReplay() {
        try (SignalFixture fixture = new SignalFixture()) {
            fixture.eligibility.signal(fixture.user).toCompletableFuture().join();
            verify(fixture.user).checkDelayedTimedRewards();
            verify(fixture.user).checkOfflineRewardsAsync();
        }
    }

    @Test
    void fallbackBacksOffToFiveMinutesDespiteRepeatedDeferralHints() {
        try (SignalFixture fixture = new SignalFixture()) {
            EligibilityAccess.queued(fixture.eligibility, fixture.user);
            long due = 10;
            long delay = 10;
            for (int attempts = 1; attempts <= 9; attempts++) {
                fixture.clock.set(java.util.concurrent.TimeUnit.SECONDS.toNanos(due) - 1);
                fixture.sweep.get().run();
                verify(fixture.user, times(attempts - 1)).checkOfflineRewardsAsync();
                fixture.clock.incrementAndGet();
                fixture.sweep.get().run();
                verify(fixture.user, times(attempts)).checkOfflineRewardsAsync();
                delay = Math.min(300, delay * 2);
                due += delay;
            }
        }
    }

    @Test
    void explicitSignalBypassesAndResetsBackoffAcrossMonotonicWrap() {
        try (SignalFixture fixture = new SignalFixture()) {
            fixture.clock.set(Long.MAX_VALUE - java.util.concurrent.TimeUnit.SECONDS.toNanos(5));
            EligibilityAccess.queued(fixture.eligibility, fixture.user);
            fixture.sweep.get().run();
            verify(fixture.user, never()).checkOfflineRewardsAsync();
            fixture.clock.addAndGet(java.util.concurrent.TimeUnit.SECONDS.toNanos(10));
            fixture.sweep.get().run();
            verify(fixture.user).checkOfflineRewardsAsync();
            fixture.eligibility.signal(fixture.user).toCompletableFuture().join();
            verify(fixture.user, times(2)).checkOfflineRewardsAsync();
            fixture.clock.addAndGet(java.util.concurrent.TimeUnit.SECONDS.toNanos(10) - 1);
            fixture.sweep.get().run();
            verify(fixture.user, times(2)).checkOfflineRewardsAsync();
            fixture.clock.incrementAndGet();
            fixture.sweep.get().run();
            verify(fixture.user, times(3)).checkOfflineRewardsAsync();
        }
    }

    @Test
    void boundedSweepRotatesPastEntriesWhoseNextAttemptIsNotDue() {
        try (SignalFixture fixture = new SignalFixture()) {
            java.util.List<AdvancedCoreUser> users = new java.util.ArrayList<>();
            for (int i = 0; i < 129; i++) {
                AdvancedCoreUser user = mock(AdvancedCoreUser.class);
                when(user.getPlugin()).thenReturn(fixture.plugin);
                when(user.getUUID()).thenReturn("synthetic-rotation-" + i);
                // All entries belong to the already accepted live fixture session.
                when(user.getPlayer()).thenReturn(fixture.player);
                when(user.checkOfflineRewardsAsync()).thenAnswer(call -> {
                    EligibilityAccess.queued(fixture.eligibility, user);
                    return java.util.concurrent.CompletableFuture.completedFuture(null);
                });
                users.add(user);
                EligibilityAccess.queued(fixture.eligibility, user);
            }
            fixture.clock.set(java.util.concurrent.TimeUnit.SECONDS.toNanos(10));
            fixture.sweep.get().run();
            verify(users.get(128), never()).checkOfflineRewardsAsync();
            fixture.sweep.get().run();
            fixture.sweep.get().run();
            for (AdvancedCoreUser user : users) verify(user).checkOfflineRewardsAsync();
        }
    }

    @Test
    void explicitSignalCoalescesRunningReplayAndResetsItsNextFallback() {
        try (SignalFixture fixture = new SignalFixture()) {
            var replay = new java.util.concurrent.CompletableFuture<Void>();
            doReturn(replay).when(fixture.user).checkOfflineRewardsAsync();
            EligibilityAccess.queued(fixture.eligibility, fixture.user);
            fixture.clock.set(java.util.concurrent.TimeUnit.SECONDS.toNanos(10));
            fixture.sweep.get().run();
            verify(fixture.user).checkOfflineRewardsAsync();
            fixture.clock.addAndGet(java.util.concurrent.TimeUnit.SECONDS.toNanos(1));
            var signal = fixture.eligibility.signal(fixture.user).toCompletableFuture();
            assertFalse(signal.isDone());
            fixture.sweep.get().run();
            verify(fixture.user).checkOfflineRewardsAsync();
            EligibilityAccess.queued(fixture.eligibility, fixture.user);
            replay.complete(null);
            signal.join();
            fixture.clock.addAndGet(java.util.concurrent.TimeUnit.SECONDS.toNanos(10) - 1);
            fixture.sweep.get().run();
            verify(fixture.user).checkOfflineRewardsAsync();
            fixture.clock.incrementAndGet();
            fixture.sweep.get().run();
            verify(fixture.user, times(2)).checkOfflineRewardsAsync();
            fixture.eligibility.close();
            fixture.sweep.get().run();
            verify(fixture.user, times(2)).checkOfflineRewardsAsync();
        }
    }

    @Test
    void timedRetryHintsRejectForeignClosedAndUnavailableSchedulers() {
        try (SignalFixture fixture = new SignalFixture()) {
            AdvancedCoreUser foreign = mock(AdvancedCoreUser.class);
            when(foreign.getPlugin()).thenReturn(mock(AdvancedCorePlugin.class));
            assertThrows(IllegalArgumentException.class, () -> fixture.eligibility.retainTimedRetry(foreign));
            doThrow(new java.util.concurrent.RejectedExecutionException()).when(fixture.worker)
                    .scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(),
                            any(java.util.concurrent.TimeUnit.class));
            assertFalse(fixture.eligibility.retainTimedRetry(fixture.user));
            fixture.eligibility.close();
            assertFalse(fixture.eligibility.retainTimedRetry(fixture.user));
            verify(fixture.user, never()).checkOfflineRewardsAsync();
            verify(fixture.user, never()).checkDelayedTimedRewards();
        }
    }

    @Test
    void timedRetryHintCapacityPreservesAlreadyAdmittedUsers() {
        try (SignalFixture fixture = new SignalFixture()) {
            assertTrue(fixture.eligibility.retainTimedRetry(fixture.user));
            for (int i = 1; i < RewardEligibility.MAX_PENDING_PLAYERS; i++) {
                AdvancedCoreUser user = mock(AdvancedCoreUser.class, withSettings().stubOnly());
                when(user.getPlugin()).thenReturn(fixture.plugin);
                when(user.getUUID()).thenReturn("synthetic-capacity-" + i);
                assertTrue(fixture.eligibility.retainTimedRetry(user));
            }
            AdvancedCoreUser excess = mock(AdvancedCoreUser.class);
            when(excess.getPlugin()).thenReturn(fixture.plugin);
            when(excess.getUUID()).thenReturn("synthetic-capacity-excess");
            assertFalse(fixture.eligibility.retainTimedRetry(excess));
            assertTrue(fixture.eligibility.retainTimedRetry(fixture.user));
            verify(excess, never()).checkOfflineRewardsAsync();
            verify(excess, never()).checkDelayedTimedRewards();
        }
    }

    @Test
    void quitWaitsForResolvedLoginWithoutPollingAndRebindsLogicalIdentity() {
        try (SignalFixture fixture = new SignalFixture()) {
            Player previous = mock(Player.class);
            when(previous.isOnline()).thenReturn(true);
            when(fixture.user.getPlayer()).thenReturn(previous);
            EligibilityAccess.deferred(fixture.eligibility, fixture.user);
            fixture.eligibility.disconnected(previous);
            fixture.clock.set(java.util.concurrent.TimeUnit.HOURS.toNanos(2));
            fixture.sweep.get().run();
            fixture.eligibility.signal(fixture.user).toCompletableFuture().join();
            verify(fixture.user, never()).checkDelayedTimedRewards();
            verify(fixture.user, never()).checkOfflineRewardsAsync();
            AdvancedCoreUser rebound = mock(AdvancedCoreUser.class);
            when(rebound.getPlugin()).thenReturn(fixture.plugin);
            String logicalId = fixture.user.getUUID();
            when(rebound.getUUID()).thenReturn(logicalId);
            when(rebound.checkOfflineRewardsAsync()).thenAnswer(call -> {
                EligibilityAccess.queued(fixture.eligibility, rebound);
                return java.util.concurrent.CompletableFuture.completedFuture(null);
            });
            Player replacement = mock(Player.class);
            when(rebound.getPlayer()).thenReturn(replacement);
            when(replacement.isOnline()).thenReturn(true);
            fixture.eligibility.signalLogin(rebound, replacement).toCompletableFuture().join();
            fixture.eligibility.signal(replacement);
            verify(rebound, times(2)).checkDelayedTimedRewards();
            verify(rebound, times(2)).checkOfflineRewardsAsync();
            verify(fixture.user, never()).checkOfflineRewardsAsync();
            verify(previous, never()).getUniqueId();
            verify(replacement, never()).getUniqueId();
        }
    }

    @Test
    void unknownHintRequiresAnOwnPluginBooleanLoginReceipt() {
        for (int invalidReceipt = 0; invalidReceipt < 3; invalidReceipt++) {
            try (SignalFixture fixture = new SignalFixture()) {
                fixture.receipts.clear();
                org.bukkit.plugin.Plugin owner = invalidReceipt == 0
                        ? mock(org.bukkit.plugin.Plugin.class) : fixture.plugin;
                Object value = invalidReceipt == 1 ? "true" : invalidReceipt == 0;
                fixture.receipts.add(new org.bukkit.metadata.FixedMetadataValue(owner, value));
                EligibilityAccess.queued(fixture.eligibility, fixture.user);
                fixture.eligibility.signal(fixture.user).toCompletableFuture().join();
                verify(fixture.user, never()).hasPendingRewardReplay();
                verify(fixture.user, never()).checkDelayedTimedRewards();
                verify(fixture.user, never()).checkOfflineRewardsAsync();
                fixture.eligibility.signalLogin(fixture.user, fixture.player).toCompletableFuture().join();
                verify(fixture.user).checkDelayedTimedRewards();
                verify(fixture.user).checkOfflineRewardsAsync();
            }
        }
    }

    @Test
    void acceptedLoginWithoutWorkRecordsOnlyItsSessionReceiptAndQuitRemovesOnlyOwnValue() {
        try (SignalFixture fixture = new SignalFixture()) {
            fixture.receipts.clear();
            org.bukkit.plugin.Plugin other = mock(org.bukkit.plugin.Plugin.class);
            var foreign = new org.bukkit.metadata.FixedMetadataValue(other, true);
            fixture.receipts.add(foreign);
            fixture.eligibility.signalLogin(fixture.user, fixture.player).toCompletableFuture().join();
            assertTrue(fixture.receipts.stream().anyMatch(value ->
                    value.getOwningPlugin() == fixture.plugin && Boolean.TRUE.equals(value.value())));
            verify(fixture.user, never()).hasPendingRewardReplay();
            verify(fixture.user, never()).checkDelayedTimedRewards();
            verify(fixture.user, never()).checkOfflineRewardsAsync();
            assertNull(fixture.sweep.get());
            fixture.eligibility.disconnected(fixture.player);
            assertEquals(List.of(foreign), fixture.receipts);
        }
    }

    @Test
    void unknownHintDoesNotReadAReplacementFromThePreviousOwnerCallback() {
        try (SignalFixture fixture = new SignalFixture()) {
            EligibilityAccess.queued(fixture.eligibility, fixture.user);
            var callback = new java.util.concurrent.atomic.AtomicReference<Runnable>();
            doAnswer(call -> { callback.set(call.getArgument(1)); return null; })
                    .when(fixture.scheduler).executeOrScheduleSync(eq(fixture.plugin), any(Runnable.class), eq(fixture.player));
            clearInvocations(fixture.player);
            var result = fixture.eligibility.signal(fixture.user).toCompletableFuture();
            assertFalse(result.isDone());
            Player replacement = mock(Player.class);
            doReturn(replacement).when(fixture.user).getPlayer();
            callback.get().run();
            result.join();
            verify(fixture.player, never()).getMetadata(anyString());
            verify(replacement, never()).isOnline();
            verify(replacement, never()).getMetadata(anyString());
            verify(fixture.user, never()).hasPendingRewardReplay();
            verify(fixture.user, never()).checkDelayedTimedRewards();
            verify(fixture.user, never()).checkOfflineRewardsAsync();
        }
    }

    @Test
    void missingReceiptCannotAuthenticateAFreshDeferredHint() {
        try (SignalFixture fixture = new SignalFixture(false)) {
            EligibilityAccess.deferred(fixture.eligibility, fixture.user);
            fixture.eligibility.signal(fixture.user).toCompletableFuture().join();
            verify(fixture.user, never()).checkDelayedTimedRewards();
            verify(fixture.user, never()).checkOfflineRewardsAsync();
            fixture.eligibility.signalLogin(fixture.user, fixture.player).toCompletableFuture().join();
            verify(fixture.user).checkDelayedTimedRewards();
            verify(fixture.user).checkOfflineRewardsAsync();
        }
    }

    @Test
    void freshDeferredHintCannotWakeEitherQueueBeforeAcceptedLogin() {
        try (SignalFixture fixture = new SignalFixture(false)) {
            fixture.eligibility.loginPending(fixture.player);
            YamlConfiguration definition = new YamlConfiguration();
            definition.set("GameModes", List.of("SURVIVAL"));
            assertFalse(fixture.eligibility.isEligible(definition, fixture.user),
                    "the native pending-login receipt must deny even an allowed mode");
            EligibilityAccess.deferred(fixture.eligibility, fixture.user);
            assertFalse(fixture.eligibility.isEligible(definition, fixture.user),
                    "a new deferred hint is not an accepted-login receipt");
            fixture.eligibility.signal(fixture.user).toCompletableFuture().join();
            verify(fixture.user, never()).checkDelayedTimedRewards();
            verify(fixture.user, never()).checkOfflineRewardsAsync();
            fixture.eligibility.signalLogin(fixture.user, fixture.player).toCompletableFuture().join();
            assertTrue(fixture.eligibility.isEligible(definition, fixture.user));
            verify(fixture.user).checkDelayedTimedRewards();
            verify(fixture.user).checkOfflineRewardsAsync();
        }
    }

    @Test
    void allowedModeCannotBypassTheQuitGateBeforeAcceptedLogin() {
        try (SignalFixture fixture = new SignalFixture()) {
            Player previous = mock(Player.class);
            when(previous.isOnline()).thenReturn(true);
            when(fixture.user.getPlayer()).thenReturn(previous);
            EligibilityAccess.deferred(fixture.eligibility, fixture.user);
            fixture.eligibility.disconnected(previous);
            Player replacement = mock(Player.class);
            doReturn(replacement).when(fixture.user).getPlayer();
            when(replacement.isOnline()).thenReturn(true);
            when(replacement.getGameMode()).thenReturn(GameMode.SURVIVAL);
            YamlConfiguration definition = new YamlConfiguration();
            definition.set("GameModes", List.of("SURVIVAL"));
            assertFalse(fixture.eligibility.isEligible(definition, fixture.user));
            EligibilityAccess.deferred(fixture.eligibility, fixture.user);
            assertFalse(fixture.eligibility.isEligible(definition, fixture.user),
                    "retaining unfinished work must not authenticate the replacement session");
            fixture.eligibility.signalLogin(fixture.user, replacement).toCompletableFuture().join();
            assertTrue(fixture.eligibility.isEligible(definition, fixture.user));
            verify(replacement, never()).getUniqueId();
        }
    }

    @Test
    void loginChecksTheExactEventSessionOnlyOnItsOwner() {
        try (SignalFixture fixture = new SignalFixture()) {
            Player player = mock(Player.class);
            when(fixture.user.getPlayer()).thenReturn(player);
            EligibilityAccess.queued(fixture.eligibility, fixture.user);
            var callback = new java.util.concurrent.atomic.AtomicReference<Runnable>();
            var onOwner = new java.util.concurrent.atomic.AtomicBoolean();
            when(player.isOnline()).thenAnswer(call -> {
                assertTrue(onOwner.get(), "login must inspect the exact event player's owner");
                return true;
            });
            doAnswer(call -> { callback.set(call.getArgument(1)); return null; })
                    .when(fixture.scheduler).executeOrScheduleSync(eq(fixture.plugin), any(Runnable.class), eq(player));
            var result = fixture.eligibility.signalLogin(fixture.user, player).toCompletableFuture();
            assertFalse(result.isDone());
            verify(player, never()).isOnline();
            verify(fixture.user, never()).checkOfflineRewardsAsync();
            onOwner.set(true);
            callback.get().run();
            result.join();
            verify(fixture.user).checkDelayedTimedRewards();
            verify(fixture.user).checkOfflineRewardsAsync();
            verify(player, never()).getUniqueId();
        }
    }

    @Test
    void quitBeforeQueuedLoginCallbackRetainsTheAwaitingLoginHint() {
        try (SignalFixture fixture = new SignalFixture()) {
            Player player = mock(Player.class);
            when(player.isOnline()).thenReturn(true);
            when(fixture.user.getPlayer()).thenReturn(player);
            EligibilityAccess.deferred(fixture.eligibility, fixture.user);
            var callback = new java.util.concurrent.atomic.AtomicReference<Runnable>();
            doAnswer(call -> { callback.set(call.getArgument(1)); return null; })
                    .when(fixture.scheduler).executeOrScheduleSync(eq(fixture.plugin), any(Runnable.class), eq(player));
            var result = fixture.eligibility.signalLogin(fixture.user, player).toCompletableFuture();
            fixture.eligibility.disconnected(player);
            doReturn(false).when(player).isOnline();
            callback.get().run();
            result.join();
            fixture.clock.set(java.util.concurrent.TimeUnit.HOURS.toNanos(2));
            fixture.sweep.get().run();
            verify(fixture.user, never()).checkDelayedTimedRewards();
            verify(fixture.user, never()).checkOfflineRewardsAsync();
            assertNotNull(fixture.sweep.get(), "quit must retain the only timed wake-up hint");
        }
    }

    @Test
    void loginWithoutKnownHintDoesNotReadQueuesOrCreatePendingWork() {
        try (SignalFixture fixture = new SignalFixture()) {
            fixture.eligibility.signalLogin(fixture.user, mock(Player.class)).toCompletableFuture().join();
            verify(fixture.user, never()).checkDelayedTimedRewards();
            verify(fixture.user, never()).checkOfflineRewardsAsync();
            assertNull(fixture.sweep.get());
        }
    }

    private static final class SignalFixture implements AutoCloseable {
        final AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        final RewardHandler handler = mock(RewardHandler.class);
        final com.bencodez.simpleapi.scheduler.BukkitScheduler scheduler =
                mock(com.bencodez.simpleapi.scheduler.BukkitScheduler.class);
        final AdvancedCoreUser user = mock(AdvancedCoreUser.class);
        final Player player = mock(Player.class);
        final java.util.ArrayList<org.bukkit.metadata.MetadataValue> receipts = new java.util.ArrayList<>();
        final java.util.concurrent.ScheduledExecutorService worker =
                mock(java.util.concurrent.ScheduledExecutorService.class);
        final java.util.concurrent.atomic.AtomicReference<Runnable> sweep =
                new java.util.concurrent.atomic.AtomicReference<>();
        final RewardEligibility eligibility;
        final java.util.concurrent.atomic.AtomicLong clock = new java.util.concurrent.atomic.AtomicLong();

        SignalFixture() { this(true); }

        SignalFixture(boolean acceptedLogin) {
            when(plugin.isEnabled()).thenReturn(true);
            when(plugin.getTimer()).thenReturn(worker);
            when(plugin.getBukkitScheduler()).thenReturn(scheduler);
            doAnswer(call -> { ((Runnable) call.getArgument(1)).run(); return null; })
                    .when(scheduler).executeOrScheduleSync(eq(plugin), any(Runnable.class));
            doAnswer(call -> { ((Runnable) call.getArgument(1)).run(); return null; })
                    .when(scheduler).executeOrScheduleSync(eq(plugin), any(Runnable.class), any(Player.class));
            when(handler.getDelayedTimer()).thenReturn(worker);
            when(user.getPlugin()).thenReturn(plugin);
            when(user.getUUID()).thenReturn("synthetic-backoff-user");
            when(user.getPlayer()).thenReturn(player);
            when(player.isOnline()).thenReturn(true);
            when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
            when(player.getMetadata(anyString())).thenAnswer(call -> new java.util.ArrayList<>(receipts));
            doAnswer(call -> {
                org.bukkit.metadata.MetadataValue value = call.getArgument(1);
                receipts.removeIf(previous -> previous.getOwningPlugin() == value.getOwningPlugin());
                receipts.add(value);
                return null;
            }).when(player).setMetadata(anyString(), any(org.bukkit.metadata.MetadataValue.class));
            doAnswer(call -> { receipts.removeIf(value -> value.getOwningPlugin() == call.getArgument(1)); return null; })
                    .when(player).removeMetadata(anyString(), any(org.bukkit.plugin.Plugin.class));
            doAnswer(call -> { ((Runnable) call.getArgument(0)).run(); return null; })
                    .when(worker).execute(any(Runnable.class));
            doAnswer(call -> {
                sweep.set(call.getArgument(0));
                return mock(java.util.concurrent.ScheduledFuture.class);
            }).when(worker).scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(),
                    any(java.util.concurrent.TimeUnit.class));
            eligibility = EligibilityAccess.create(plugin, handler, clock::get);
            when(user.checkOfflineRewardsAsync()).thenAnswer(call -> {
                EligibilityAccess.queued(eligibility, user);
                return java.util.concurrent.CompletableFuture.completedFuture(null);
            });
            if (acceptedLogin) eligibility.signalLogin(user, player).toCompletableFuture().join();
        }

        @Override public void close() { eligibility.close(); }
    }

}
