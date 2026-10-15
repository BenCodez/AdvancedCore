package com.bencodez.advancedcore.api.rewards;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.bencodez.advancedcore.AdvancedCoreConfigOptions;
import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.rewards.injected.RewardInject;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.advancedcore.api.user.UserData;
import com.bencodez.advancedcore.api.user.UserDataFetchMode;
import com.bencodez.advancedcore.api.user.UserManager;
import com.bencodez.advancedcore.api.user.usercache.UserDataManager;
import com.bencodez.advancedcore.listeners.AdvancedCoreLoginEvent;
import com.bencodez.advancedcore.listeners.RewardEligibilityListener;

class RewardGamemodeTimedTest {
    @TempDir File directory;

    @Test void initiallyDeniedTimedRewardKeepsItsDueTimeAndBoundedRetry() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.mode.set(GameMode.CREATIVE);
            long due = System.currentTimeMillis() - 10_000;
            Reward reward = fixture.reward("Due", true);
            fixture.user.addTimedReward(reward, new HashMap<>(), due);
            String occurrence = occurrence(fixture.onlyTimedKey());
            fixture.user.checkDelayedTimedRewards();
            fixture.drain();
            assertEquals(due, fixture.user.getTimedRewards().get(fixture.onlyTimedKey()));
            assertEquals(occurrence, occurrence(fixture.onlyTimedKey()));
            assertEquals(0, fixture.prefix.get());
            assertEquals(1, fixture.scheduledMillis.size(), "eligibility must reuse the fallback instead of adding a retry timer");
            verify(fixture.delayed, times(1)).scheduleWithFixedDelay(any(Runnable.class), eq(10L), eq(10L), eq(TimeUnit.SECONDS));
            verify(fixture.logger, never()).warning(anyString());
        }
    }

    @Test void repeatedDeniedSignalsAndMultipleDueEntriesShareOneFallback() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.mode.set(GameMode.CREATIVE);
            Reward due = fixture.reward("Due", true);
            Reward future = fixture.reward("Future", true);
            long now = System.currentTimeMillis();
            fixture.user.addTimedReward(due, new HashMap<>(), now - 10_000);
            fixture.user.addTimedReward(due, new HashMap<>(), now - 9_000);
            fixture.user.addTimedReward(future, new HashMap<>(), now + TimeUnit.HOURS.toMillis(1));
            HashMap<String, Long> expected = timesByOccurrence(fixture.user.getTimedRewards());
            assertEquals(3, expected.size());
            fixture.user.checkDelayedTimedRewards();
            fixture.drain();
            for (int attempt = 0; attempt < 4; attempt++) {
                fixture.handler.getRewardEligibility().signal(fixture.user).toCompletableFuture().get(2, TimeUnit.SECONDS);
                fixture.drain();
                assertEquals(expected, timesByOccurrence(fixture.user.getTimedRewards()));
            }
            assertEquals(3, fixture.scheduledMillis.size(), "only the three original timers may be scheduled");
            verify(fixture.delayed, times(1)).scheduleWithFixedDelay(any(Runnable.class), eq(10L), eq(10L), eq(TimeUnit.SECONDS));
            assertEquals(0, fixture.prefix.get());
            assertEquals(0, fixture.futurePrefix.get());
            verify(fixture.player, never()).giveExp(anyInt());
            assertTrue(fixture.stored("OfflineRewards").isEmpty());
        }
    }

    @Test void fullHintBudgetStillWakesAnOrdinaryOfflineRewardAfterModeChange() throws Exception {
        fullHintBudgetScenario(false);
    }

    @Test void fullTimedHintBudgetStillWakesAnOrdinaryOfflineRewardAfterModeChange() throws Exception {
        fullHintBudgetScenario(true);
    }

    private void fullHintBudgetScenario(boolean protectedTimedHints) throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.handler.getRewardEligibility().signalLogin(fixture.user, fixture.player)
                    .toCompletableFuture().join();
            for (int i = 0; i < RewardEligibility.MAX_PENDING_PLAYERS; i++) {
                AdvancedCoreUser retained = mock(AdvancedCoreUser.class, withSettings().stubOnly());
                when(retained.getUUID()).thenReturn("synthetic-retained-" + i);
                if (protectedTimedHints) {
                    when(retained.getPlugin()).thenReturn(fixture.plugin);
                    assertTrue(fixture.handler.getRewardEligibility().retainTimedRetry(retained));
                } else fixture.handler.getRewardEligibility().queued(retained);
            }
            fixture.mode.set(GameMode.CREATIVE);
            Reward reward = fixture.reward("Offline", true);
            String id = UUID.randomUUID().toString();
            RewardOptions options = new RewardOptions().setCheckTimed(false).setGiveOffline(true);
            options.setAsyncReplayOccurrenceId(id);
            reward.giveRewardAsync(fixture.user, options).toCompletableFuture().join();
            fixture.drain();
            assertEquals(1, fixture.stored("OfflineRewards").size());
            assertTrue(fixture.stored("OfflineRewards").get(0).contains(id));
            verify(fixture.player, never()).giveExp(3);
            fixture.mode.set(GameMode.SURVIVAL);
            fixture.onOwner(() -> fixture.handler.getRewardEligibility().signal(fixture.player));
            fixture.drainQueuedWork();
            verify(fixture.player).giveExp(3);
            assertEquals(1, fixture.offlinePrefix.get());
            assertTrue(fixture.stored("OfflineRewards").isEmpty());
            fixture.onOwner(() -> fixture.handler.getRewardEligibility().signal(fixture.player));
            fixture.drainQueuedWork();
            verify(fixture.player, times(1)).giveExp(3);
        }
    }

    @Test void closedFallbackRetainsFutureTimerInsteadOfStrandingDueWork() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.mode.set(GameMode.CREATIVE);
            Reward due = fixture.reward("Due", true);
            fixture.user.addTimedReward(due, new HashMap<>(), System.currentTimeMillis() - 10_000);
            String originalOccurrence = occurrence(fixture.onlyTimedKey());
            fixture.handler.getRewardEligibility().close();
            fixture.user.checkDelayedTimedRewards();
            fixture.drain();
            assertEquals(originalOccurrence, occurrence(fixture.onlyTimedKey()));
            assertTrue(fixture.user.getTimedRewards().get(fixture.onlyTimedKey()) > System.currentTimeMillis(),
                    "an unavailable bounded fallback requires a future durable retry deadline");
            assertEquals(2, fixture.scheduledMillis.size(), "one original timer and one ordinary retry must remain");
            verify(fixture.delayed, never()).scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));
            assertEquals(0, fixture.prefix.get());
            verify(fixture.player, never()).giveExp(anyInt());
        }
    }

    @Test void acceptedAuthenticatedLoginResumesDueTimedWorkAfterQuit() throws Exception {
        authenticatedLoginScenario(false);
    }

    @Test void staleAsyncLoginCannotWakeUnauthenticatedReplacementSession() throws Exception {
        authenticatedLoginScenario(true);
    }

    private void authenticatedLoginScenario(boolean staleEvent) throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.mode.set(GameMode.CREATIVE);
            Reward due = fixture.reward("Due", true);
            Reward future = fixture.reward("Future", true);
            long dueTime = System.currentTimeMillis() - 10_000;
            long futureTime = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1);
            fixture.user.addTimedReward(due, new HashMap<>(), dueTime);
            String dueOccurrence = occurrence(fixture.onlyTimedKey());
            fixture.user.addTimedReward(future, new HashMap<>(), futureTime);
            String futureKey = fixture.user.getTimedRewards().entrySet().stream()
                    .filter(entry -> entry.getValue() == futureTime).findFirst().orElseThrow().getKey();
            fixture.user.checkDelayedTimedRewards();
            fixture.drain();
            assertEquals(0, fixture.prefix.get());
            assertEquals(2, fixture.user.getTimedRewards().size());
            RewardEligibilityListener listener = new RewardEligibilityListener(fixture.plugin);
            listener.onQuit(new PlayerQuitEvent(fixture.player, ""));
            doReturn(false).when(fixture.player).isOnline();
            doReturn(null).when(fixture.user).getPlayer();
            doReturn(false).when(fixture.user).isOnline();
            java.lang.reflect.Field pending = RewardEligibility.class.getDeclaredField("pending");
            pending.setAccessible(true);
            for (Object hint : ((java.util.Map<?, ?>) pending.get(fixture.handler.getRewardEligibility())).values()) {
                java.lang.reflect.Field nativePlayer = hint.getClass().getDeclaredField("player");
                nativePlayer.setAccessible(true);
                assertNull(nativePlayer.get(hint), "quit must release cached native player references");
            }
            clearInvocations(fixture.data);
            assertNotNull(fixture.fallbackSweep.get());
            fixture.fallbackSweep.get().run();
            fixture.drain();
            verifyNoInteractions(fixture.data);
            Player replacement = mock(Player.class);
            installPlayerMetadata(replacement);
            when(replacement.getUniqueId()).thenReturn(fixture.id);
            when(replacement.isOnline()).thenReturn(true);
            when(replacement.getGameMode()).thenAnswer(call -> {
                assertTrue(fixture.playerOwner.get(), "replacement gamemode reads require the owning scheduler");
                return GameMode.SURVIVAL;
            });
            fixture.bukkit.when(() -> Bukkit.getPlayer(fixture.id)).thenReturn(replacement);
            AdvancedCoreUser resumed = spy(new AdvancedCoreUser(fixture.plugin, fixture.id, "TimedPlayer"));
            resumed.setData(fixture.data);
            doReturn(replacement).when(resumed).getPlayer();
            doReturn(true).when(resumed).isOnline();
            if (staleEvent) {
                // A cached logical user now resolves the new physical connection,
                // but the queued authentication event still belongs to the quit player.
                doReturn(replacement).when(fixture.user).getPlayer();
                doReturn(true).when(fixture.user).isOnline();
                clearInvocations(fixture.data);
                dispatchLogin(listener, new AdvancedCoreLoginEvent(fixture.user, fixture.user.getUUID(), fixture.player));
                fixture.drain();
                verify(replacement, never()).giveExp(anyInt());
                verify(fixture.player, never()).giveExp(anyInt());
                verifyNoInteractions(fixture.data);
                assertEquals(0, fixture.prefix.get());
                assertEquals(0, fixture.futurePrefix.get());
                assertEquals(dueTime, timesByOccurrence(fixture.user.getTimedRewards()).get(dueOccurrence));
                assertEquals(futureTime, fixture.user.getTimedRewards().get(futureKey));
            }
            AdvancedCoreLoginEvent cancelled = new AdvancedCoreLoginEvent(resumed, resumed.getUUID(), replacement);
            cancelled.setCancelled(true);
            clearInvocations(fixture.data);
            dispatchLogin(listener, cancelled);
            fixture.drain();
            verifyNoInteractions(fixture.data);
            verify(replacement, never()).giveExp(anyInt());
            assertEquals(0, fixture.prefix.get());
            assertEquals(dueTime, timesByOccurrence(fixture.user.getTimedRewards()).get(dueOccurrence));
            dispatchLogin(listener, new AdvancedCoreLoginEvent(resumed, resumed.getUUID(), replacement));
            fixture.drain();
            verify(replacement, times(1)).giveExp(3);
            verify(fixture.player, never()).giveExp(anyInt());
            assertEquals(1, fixture.prefix.get());
            assertEquals(0, fixture.futurePrefix.get());
            assertEquals(new HashMap<>(java.util.Map.of(futureKey, futureTime)), fixture.user.getTimedRewards());
            dispatchLogin(listener, new AdvancedCoreLoginEvent(resumed, resumed.getUUID(), replacement));
            fixture.drain();
            verify(replacement, times(1)).giveExp(3);
            assertEquals(1, fixture.prefix.get());
            assertEquals(0, fixture.futurePrefix.get());
        }
    }

    @Test void quitAfterAcceptedLoginBeforeQueuedWorkerCannotRewardReplacement() throws Exception {
        loginWorkerRaceScenario(false);
    }

    @Test void quitDuringReplayOwnershipProbeCannotRewardReplacement() throws Exception {
        loginWorkerRaceScenario(true);
    }

    private void loginWorkerRaceScenario(boolean quitDuringProbe) throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.mode.set(GameMode.CREATIVE);
            Reward due = fixture.reward("Due", true);
            Reward future = fixture.reward("Future", true);
            Reward offline = fixture.reward("Offline", true);
            long dueTime = System.currentTimeMillis() - 10_000;
            long futureTime = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1);
            fixture.user.addTimedReward(due, new HashMap<>(), dueTime);
            String dueOccurrence = occurrence(fixture.onlyTimedKey());
            fixture.user.addTimedReward(future, new HashMap<>(), futureTime);
            String futureKey = fixture.user.getTimedRewards().entrySet().stream()
                    .filter(entry -> entry.getValue() == futureTime).findFirst().orElseThrow().getKey();
            RewardOptions offlineOptions = new RewardOptions().setCheckTimed(false).setGiveOffline(true);
            String offlineOccurrence = UUID.randomUUID().toString();
            offlineOptions.setAsyncReplayOccurrenceId(offlineOccurrence);
            fixture.user.addOfflineRewards(offline, new HashMap<>(), offlineOptions);
            fixture.user.checkDelayedTimedRewards();
            fixture.drain();
            assertEquals(0, fixture.prefix.get());
            AtomicReference<Player> current = new AtomicReference<>(fixture.player);
            doAnswer(call -> current.get()).when(fixture.user).getPlayer();
            doAnswer(call -> current.get() != null).when(fixture.user).isOnline();
            doAnswer(call -> current.get() == fixture.player).when(fixture.player).isOnline();
            Player replacement = mock(Player.class);
            installPlayerMetadata(replacement);
            when(replacement.getUniqueId()).thenReturn(fixture.id);
            when(replacement.isOnline()).thenAnswer(call -> current.get() == replacement);
            when(replacement.getGameMode()).thenAnswer(call -> {
                assertTrue(fixture.playerOwner.get(), "replacement mode reads require the owning scheduler");
                return GameMode.SURVIVAL;
            });
            fixture.bukkit.when(() -> Bukkit.getPlayer(fixture.id)).thenAnswer(call -> current.get());
            RewardEligibilityListener listener = new RewardEligibilityListener(fixture.plugin);
            fixture.mode.set(GameMode.SURVIVAL);
            fixture.queueWorkers.set(true);
            dispatchLogin(listener, new AdvancedCoreLoginEvent(fixture.user, fixture.user.getUUID(), fixture.player));
            assertFalse(fixture.workers.isEmpty(), "accepted login must validate before handing replay to its worker");
            clearInvocations(fixture.data, fixture.user);
            Runnable quitAndReplace = () -> {
                fixture.onOwner(() -> listener.onQuit(new PlayerQuitEvent(fixture.player, "")));
                current.set(replacement);
            };
            java.util.concurrent.atomic.AtomicBoolean quitFromProbe = new java.util.concurrent.atomic.AtomicBoolean();
            if (quitDuringProbe) {
                java.util.concurrent.atomic.AtomicBoolean armed = new java.util.concurrent.atomic.AtomicBoolean(true);
                doAnswer(call -> {
                    boolean claimed = (boolean) call.callRealMethod();
                    if (armed.compareAndSet(true, false)) {
                        quitFromProbe.set(true);
                        quitAndReplace.run();
                    }
                    return claimed;
                }).when(fixture.user).hasPendingRewardReplay();
            } else quitAndReplace.run();
            fixture.drainQueuedWork();
            if (quitDuringProbe) assertTrue(quitFromProbe.get(), "quit must happen during the real ownership probe");
            verify(fixture.player, never()).giveExp(anyInt());
            verify(replacement, never()).giveExp(anyInt());
            assertEquals(0, fixture.prefix.get());
            assertEquals(0, fixture.futurePrefix.get());
            assertEquals(0, fixture.offlinePrefix.get());
            if (!quitDuringProbe) {
                verify(fixture.user, never()).hasPendingRewardReplay();
                verifyNoInteractions(fixture.data);
            }
            assertFalse(fixture.user.hasPendingRewardReplay());
            assertEquals(dueTime, timesByOccurrence(fixture.user.getTimedRewards()).get(dueOccurrence));
            assertEquals(futureTime, fixture.user.getTimedRewards().get(futureKey));
            assertEquals(1, fixture.stored("OfflineRewards").size());
            assertEquals(offlineOccurrence, occurrence(fixture.stored("OfflineRewards").get(0)));
            dispatchLogin(listener, new AdvancedCoreLoginEvent(fixture.user, fixture.user.getUUID(), replacement));
            fixture.drainQueuedWork();
            verify(replacement, times(2)).giveExp(3);
            verify(fixture.player, never()).giveExp(anyInt());
            assertEquals(1, fixture.prefix.get());
            assertEquals(1, fixture.offlinePrefix.get());
            assertEquals(0, fixture.futurePrefix.get());
            assertTrue(fixture.stored("OfflineRewards").isEmpty());
            assertEquals(new HashMap<>(java.util.Map.of(futureKey, futureTime)), fixture.user.getTimedRewards());
            dispatchLogin(listener, new AdvancedCoreLoginEvent(fixture.user, fixture.user.getUUID(), replacement));
            fixture.drainQueuedWork();
            verify(replacement, times(2)).giveExp(3);
            assertEquals(1, fixture.prefix.get());
            assertEquals(1, fixture.offlinePrefix.get());
            assertEquals(0, fixture.futurePrefix.get());
            assertTrue(fixture.stored("OfflineRewards").isEmpty());
        }
    }

    @Test void lateNativeRetirementWaitsForReplacementAcceptedLogin() throws Exception {
        lateRetirementScenario(false);
    }

    @Test void replacementLoginBeforeLateHintPublicationRemainsProvable() throws Exception {
        lateRetirementScenario(true);
    }

    private void lateRetirementScenario(boolean loginBeforePublication) throws Exception {
        java.util.concurrent.atomic.AtomicLong nanos = new java.util.concurrent.atomic.AtomicLong(1);
        try (Fixture fixture = new Fixture(nanos::get)) {
            fixture.mode.set(GameMode.SURVIVAL);
            AtomicReference<Player> current = new AtomicReference<>(fixture.player);
            doAnswer(call -> current.get()).when(fixture.user).getPlayer();
            doAnswer(call -> current.get() != null).when(fixture.user).isOnline();
            doAnswer(call -> current.get() == fixture.player).when(fixture.player).isOnline();
            fixture.bukkit.when(() -> Bukkit.getPlayer(fixture.id)).thenAnswer(call -> current.get());
            RewardEligibilityListener listener = new RewardEligibilityListener(fixture.plugin);
            clearInvocations(fixture.data);
            dispatchLogin(listener, new AdvancedCoreLoginEvent(fixture.user, fixture.user.getUUID(), fixture.player));
            verifyNoInteractions(fixture.data);
            Reward due = fixture.reward("Due", true);
            Reward future = fixture.reward("Future", true);
            long dueTime = System.currentTimeMillis() - 10_000;
            long futureTime = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1);
            fixture.user.addTimedReward(due, new HashMap<>(), dueTime);
            String dueOccurrence = occurrence(fixture.onlyTimedKey());
            fixture.user.addTimedReward(future, new HashMap<>(), futureTime);
            String futureKey = fixture.user.getTimedRewards().entrySet().stream()
                    .filter(entry -> entry.getValue() == futureTime).findFirst().orElseThrow().getKey();
            RetiringOwnerScheduler retiring = new RetiringOwnerScheduler(fixture.plugin, fixture.plugin.getBukkitScheduler(),
                    task -> fixture.callbacks.add(() -> fixture.onOwner(task)), fixture.workers::add);
            retiring.retireNext(false);
            fixture.user.checkDelayedTimedRewards();
            assertEquals(1, fixture.prefix.get());
            assertFalse(fixture.workers.isEmpty(), "the real captured native action must await its retirement callback");
            java.lang.reflect.Field pending = RewardEligibility.class.getDeclaredField("pending");
            pending.setAccessible(true);
            assertTrue(((java.util.Map<?, ?>) pending.get(fixture.handler.getRewardEligibility())).isEmpty(),
                    "initially eligible work must reproduce quit before any wake hint exists");
            fixture.queueWorkers.set(true);
            fixture.onOwner(() -> listener.onQuit(new PlayerQuitEvent(fixture.player, "")));
            assertTrue(fixture.nativeMetadata.values().stream().flatMap(List::stream)
                    .noneMatch(value -> value.getOwningPlugin() == fixture.plugin), "quit must clear this plugin's receipt");
            Player replacement = mock(Player.class);
            HashMap<String, ArrayList<MetadataValue>> replacementMetadata = installPlayerMetadata(replacement);
            when(replacement.getUniqueId()).thenReturn(fixture.id);
            when(replacement.isOnline()).thenAnswer(call -> current.get() == replacement);
            when(replacement.getGameMode()).thenAnswer(call -> {
                assertTrue(fixture.playerOwner.get(), "replacement mode reads require the owning scheduler");
                return GameMode.SURVIVAL;
            });
            current.set(replacement);
            if (loginBeforePublication) {
                clearInvocations(fixture.data);
                dispatchLogin(listener, new AdvancedCoreLoginEvent(fixture.user, fixture.user.getUUID(), replacement));
                assertFalse(fixture.workers.isEmpty(), "the original retirement callback is still pending");
                verifyNoInteractions(fixture.data);
                verify(replacement).setMetadata(anyString(), argThat(value -> value.getOwningPlugin() == fixture.plugin
                        && Boolean.TRUE.equals(value.value())));
            }
            fixture.drainQueuedWork();
            verify(fixture.player, never()).giveExp(anyInt());
            verify(replacement, never()).giveExp(anyInt());
            assertEquals(dueTime, timesByOccurrence(fixture.user.getTimedRewards()).get(dueOccurrence));
            assertEquals(1, fixture.prefix.get());
            assertEquals(0, fixture.futurePrefix.get());
            clearInvocations(fixture.data);
            nanos.addAndGet(TimeUnit.SECONDS.toNanos(10));
            assertNotNull(fixture.fallbackSweep.get());
            fixture.fallbackSweep.get().run();
            fixture.drainQueuedWork();
            if (!loginBeforePublication) {
                verify(replacement, never()).giveExp(anyInt());
                verifyNoInteractions(fixture.data);
                assertEquals(dueTime, timesByOccurrence(fixture.user.getTimedRewards()).get(dueOccurrence));
                assertEquals(futureTime, fixture.user.getTimedRewards().get(futureKey));
                assertEquals(1, fixture.prefix.get());
                assertEquals(0, fixture.futurePrefix.get());
                dispatchLogin(listener, new AdvancedCoreLoginEvent(fixture.user, fixture.user.getUUID(), replacement));
                fixture.drainQueuedWork();
            }
            verify(replacement, times(1)).giveExp(3);
            verify(fixture.player, never()).giveExp(anyInt());
            assertEquals(1, fixture.prefix.get());
            assertEquals(0, fixture.futurePrefix.get());
            assertEquals(new HashMap<>(java.util.Map.of(futureKey, futureTime)), fixture.user.getTimedRewards());
            dispatchLogin(listener, new AdvancedCoreLoginEvent(fixture.user, fixture.user.getUUID(), replacement));
            fixture.drainQueuedWork();
            verify(replacement, times(1)).giveExp(3);
            assertEquals(1, fixture.prefix.get());
            retiring.enqueueStaleOwnerCallback();
            fixture.drainQueuedWork();
            retiring.repeatRetirementSignal();
            verify(replacement, times(1)).giveExp(3);
            assertEquals(1, fixture.prefix.get());
            fixture.onOwner(() -> listener.onQuit(new PlayerQuitEvent(replacement, "")));
            assertTrue(replacementMetadata.values().stream().flatMap(List::stream)
                    .noneMatch(value -> value.getOwningPlugin() == fixture.plugin));
        }
    }

    private static HashMap<String, ArrayList<MetadataValue>> installPlayerMetadata(Player player) {
        HashMap<String, ArrayList<MetadataValue>> metadata = new HashMap<>();
        when(player.getMetadata(anyString())).thenAnswer(call ->
                new ArrayList<>(metadata.getOrDefault(call.getArgument(0), new ArrayList<>())));
        when(player.hasMetadata(anyString())).thenAnswer(call ->
                !metadata.getOrDefault(call.getArgument(0), new ArrayList<>()).isEmpty());
        doAnswer(call -> {
            String key = call.getArgument(0);
            MetadataValue value = call.getArgument(1);
            ArrayList<MetadataValue> values = metadata.computeIfAbsent(key, ignored -> new ArrayList<>());
            values.removeIf(existing -> existing.getOwningPlugin() == value.getOwningPlugin());
            values.add(value);
            return null;
        }).when(player).setMetadata(anyString(), any(MetadataValue.class));
        doAnswer(call -> {
            String key = call.getArgument(0);
            org.bukkit.plugin.Plugin owner = call.getArgument(1);
            ArrayList<MetadataValue> values = metadata.get(key);
            if (values != null) {
                values.removeIf(existing -> existing.getOwningPlugin() == owner);
                if (values.isEmpty()) metadata.remove(key);
            }
            return null;
        }).when(player).removeMetadata(anyString(), any(org.bukkit.plugin.Plugin.class));
        return metadata;
    }

    private static void dispatchLogin(RewardEligibilityListener listener, AdvancedCoreLoginEvent event) throws Exception {
        // Match Bukkit's annotated registration shape while allowing the negative
        // regression to run against the version that has no login handler yet.
        for (java.lang.reflect.Method method : listener.getClass().getDeclaredMethods()) {
            if (method.isAnnotationPresent(EventHandler.class) && method.getParameterCount() == 1
                    && method.getParameterTypes()[0].isInstance(event)) method.invoke(listener, event);
        }
    }

    @Test void nativeModeTransitionWakesInitiallyDeniedDueTimedReward() throws Exception {
        timedWakeScenario(false);
    }

    @Test void nativeModeTransitionKeepsCompletedTimedPrefixAndOccurrence() throws Exception {
        timedWakeScenario(true);
    }

    @Test void ordinaryFailureRetainsFutureRetryDeadline() throws Exception {
        try (Fixture fixture = new Fixture()) {
            Reward reward = fixture.reward("Due", false);
            fixture.failNative.set(true);
            fixture.user.addTimedReward(reward, new HashMap<>(), System.currentTimeMillis() - 10_000);
            fixture.user.checkDelayedTimedRewards();
            fixture.drain();
            assertEquals(1, fixture.attempts.get());
            String retryKey = fixture.onlyTimedKey();
            long retryTime = fixture.user.getTimedRewards().get(retryKey);
            assertTrue(retryTime > System.currentTimeMillis(), "ordinary failures must retain their retry delay");
            fixture.user.checkDelayedTimedRewards();
            fixture.handler.getRewardEligibility().signal(fixture.user).toCompletableFuture().get(2, TimeUnit.SECONDS);
            fixture.drain();
            assertEquals(1, fixture.attempts.get());
            assertEquals(retryTime, fixture.user.getTimedRewards().get(retryKey));
            assertEquals(2, fixture.scheduledMillis.size(), "ordinary failure keeps one original and one retry timer");
            verify(fixture.logger).warning(startsWith("Could not deliver queued timed reward"));
        }
    }

    private void timedWakeScenario(boolean completedPrefix) throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.mode.set(completedPrefix ? GameMode.SURVIVAL : GameMode.CREATIVE);
            Reward due = fixture.reward("Due", true);
            Reward future = fixture.reward("Future", true);
            long dueTime = System.currentTimeMillis() - 10_000;
            long futureTime = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1);
            fixture.user.addTimedReward(due, new HashMap<>(), dueTime);
            String dueKey = fixture.onlyTimedKey();
            String dueOccurrence = occurrence(dueKey);
            fixture.user.addTimedReward(future, new HashMap<>(), futureTime);
            String futureKey = fixture.user.getTimedRewards().entrySet().stream()
                    .filter(entry -> entry.getValue() == futureTime).findFirst().orElseThrow().getKey();
            fixture.user.checkDelayedTimedRewards();
            if (completedPrefix) {
                assertEquals(1, fixture.prefix.get());
                assertFalse(fixture.callbacks.isEmpty(), "the final native action must be queued");
                fixture.mode.set(GameMode.CREATIVE);
            }
            fixture.drain();
            assertEquals(completedPrefix ? 1 : 0, fixture.prefix.get());
            verify(fixture.player, never()).giveExp(3);
            String retained = fixture.user.getTimedRewards().keySet().stream()
                    .filter(key -> occurrence(key).equals(dueOccurrence)).findFirst().orElseThrow();
            assertEquals(dueOccurrence, occurrence(retained));
            assertTrue(fixture.stored("OfflineRewards").isEmpty());
            fixture.mode.set(GameMode.SURVIVAL);
            fixture.onOwner(() -> fixture.handler.getRewardEligibility().signal(fixture.player));
            fixture.drain();
            verify(fixture.player, times(1)).giveExp(3);
            assertEquals(1, fixture.prefix.get());
            assertEquals(0, fixture.futurePrefix.get());
            assertEquals(new HashMap<>(java.util.Map.of(futureKey, futureTime)), fixture.user.getTimedRewards());
            assertFalse(fixture.user.hasPendingRewardReplay());
            fixture.onOwner(() -> fixture.handler.getRewardEligibility().signal(fixture.player));
            fixture.drain();
            verify(fixture.player, times(1)).giveExp(3);
            assertEquals(1, fixture.prefix.get());
            assertEquals(0, fixture.futurePrefix.get());
        }
    }

    private static HashMap<String, Long> timesByOccurrence(HashMap<String, Long> timed) {
        HashMap<String, Long> times = new HashMap<>();
        timed.forEach((entry, time) -> times.put(occurrence(entry), time));
        return times;
    }

    private static String occurrence(String entry) {
        return entry.split("%asyncoccurrence%", 2)[1].split("%", 2)[0];
    }

    private final class Fixture implements AutoCloseable {
        final AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        final Logger logger = mock(Logger.class);
        final UserData data = mock(UserData.class);
        final HashMap<String, ArrayList<String>> persisted = new HashMap<>();
        final Player player = mock(Player.class);
        final HashMap<String, ArrayList<MetadataValue>> nativeMetadata = installPlayerMetadata(player);
        final UUID id = UUID.randomUUID();
        final AtomicReference<GameMode> mode = new AtomicReference<>(GameMode.SURVIVAL);
        final java.util.concurrent.atomic.AtomicBoolean failNative = new java.util.concurrent.atomic.AtomicBoolean();
        final AtomicInteger prefix = new AtomicInteger(), futurePrefix = new AtomicInteger(),
                offlinePrefix = new AtomicInteger(), attempts = new AtomicInteger();
        final BlockingQueue<Runnable> callbacks = new LinkedBlockingQueue<>();
        final BlockingQueue<Runnable> workers = new LinkedBlockingQueue<>();
        final java.util.concurrent.atomic.AtomicBoolean queueWorkers = new java.util.concurrent.atomic.AtomicBoolean();
        final ThreadLocal<Boolean> playerOwner = ThreadLocal.withInitial(() -> false);
        final List<Long> scheduledMillis = new ArrayList<>();
        final AtomicReference<Runnable> fallbackSweep = new AtomicReference<>();
        final ScheduledExecutorService delayed = mock(ScheduledExecutorService.class);
        final org.mockito.MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
        final RewardHandler handler;
        final AdvancedCoreUser user;
        RewardEligibility customEligibility;

        Fixture() { this(null); }

        Fixture(java.util.function.LongSupplier nanoClock) {
            when(plugin.isEnabled()).thenReturn(true);
            when(plugin.getDataFolder()).thenReturn(directory);
            when(plugin.getLogger()).thenReturn(logger);
            AdvancedCoreConfigOptions config = mock(AdvancedCoreConfigOptions.class);
            when(config.isProcessRewards()).thenReturn(true);
            when(config.isOnlineMode()).thenReturn(true);
            when(config.getFormatRewardTimeFormat()).thenReturn("yyyy-MM-dd");
            when(plugin.getOptions()).thenReturn(config);
            UserManager users = mock(UserManager.class);
            when(plugin.getUserManager()).thenReturn(users);
            when(users.getDataManager()).thenReturn(mock(UserDataManager.class));
            when(users.getOfflineRewardsPath()).thenReturn("OfflineRewards");
            when(data.getStringList(anyString(), eq(UserDataFetchMode.DEFAULT)))
                    .thenAnswer(call -> new ArrayList<>(stored(call.getArgument(0))));
            doAnswer(call -> { persisted.put(call.getArgument(0), new ArrayList<>(call.getArgument(1))); return null; })
                    .when(data).setStringList(anyString(), any());
            doAnswer(call -> { persisted.put(call.getArgument(0), new ArrayList<>(call.getArgument(1))); return null; })
                    .when(data).setStringList(anyString(), any(), eq(false));
            var scheduler = mock(com.bencodez.simpleapi.scheduler.BukkitScheduler.class);
            when(plugin.getBukkitScheduler()).thenReturn(scheduler);
            doAnswer(call -> { call.getArgument(1, Runnable.class).run(); return null; })
                    .when(scheduler).executeOrScheduleSync(eq(plugin), any(Runnable.class));
            doAnswer(call -> { onOwner(call.getArgument(1)); return null; })
                    .when(scheduler).executeOrScheduleSync(eq(plugin), any(Runnable.class), any(Player.class));
            doAnswer(call -> { call.getArgument(1, Runnable.class).run(); return null; })
                    .when(scheduler).runTaskAsynchronously(eq(plugin), any(Runnable.class));
            doAnswer(call -> { Runnable task = call.getArgument(1); callbacks.add(() -> onOwner(task)); return null; })
                    .when(scheduler).runTask(eq(plugin), any(Runnable.class), any(Player.class));
            ScheduledExecutorService timer = mock(ScheduledExecutorService.class);
            when(plugin.getTimer()).thenReturn(timer);
            doAnswer(call -> {
                Runnable task = call.getArgument(0);
                if (queueWorkers.get()) workers.add(task);
                else task.run();
                return null;
            }).when(timer).execute(any());
            when(delayed.schedule(any(Runnable.class), anyLong(), eq(TimeUnit.MILLISECONDS))).thenAnswer(call -> {
                scheduledMillis.add(call.getArgument(1));
                return mock(ScheduledFuture.class);
            });
            when(delayed.scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(), eq(TimeUnit.SECONDS)))
                    .thenAnswer(call -> {
                        fallbackSweep.set(call.getArgument(0));
                        return mock(ScheduledFuture.class);
                    });
            when(player.getUniqueId()).thenReturn(id);
            when(player.getName()).thenReturn("TimedPlayer");
            when(player.isOnline()).thenReturn(true);
            when(player.getGameMode()).thenAnswer(call -> {
                assertTrue(playerOwner.get(), "gamemode reads require the owning scheduler");
                return mode.get();
            });
            bukkit.when(Bukkit::getPluginManager).thenReturn(mock(org.bukkit.plugin.PluginManager.class));
            bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player);
            AdvancedCorePlugin.setInstance(plugin);
            handler = new RewardHandler(plugin) {
                @Override public ScheduledExecutorService getDelayedTimer() { return delayed; }
                @Override public RewardEligibility getRewardEligibility() {
                    return customEligibility == null ? super.getRewardEligibility() : customEligibility;
                }
            };
            if (nanoClock != null) {
                handler.getRewardEligibility().close();
                customEligibility = new RewardEligibility(plugin, handler, nanoClock);
            }
            when(plugin.getRewardHandler()).thenReturn(handler);
            user = spy(new AdvancedCoreUser(plugin, id, "TimedPlayer"));
            user.setData(data);
            doReturn(player).when(user).getPlayer();
            doReturn(true).when(user).isOnline();
            doAnswer(call -> {
                call.getArgument(1, java.util.function.Consumer.class).accept(user);
                return null;
            }).when(users).getUserAsync(eq("TimedPlayer"), any(java.util.function.Consumer.class),
                    any(java.util.function.Consumer.class));
            handler.addInjectedReward(new RewardInject("Prefix") {
                @Override public Object onRewardRequest(Reward reward, AdvancedCoreUser target,
                        ConfigurationSection ignoredData, HashMap<String, String> placeholders) {
                    if (reward.getName().equals("Due")) prefix.incrementAndGet();
                    else if (reward.getName().equals("Offline")) offlinePrefix.incrementAndGet();
                    else futurePrefix.incrementAndGet();
                    return null;
                }
            });
            handler.addInjectedReward(new RewardInject("Native") {
                @Override public Object onRewardRequest(Reward reward, AdvancedCoreUser target,
                        ConfigurationSection ignoredData, HashMap<String, String> placeholders) {
                    attempts.incrementAndGet();
                    if (failNative.get()) throw new IllegalStateException("Synthetic ordinary native failure");
                    target.giveExp(3);
                    return null;
                }
            });
        }

        Reward reward(String name, boolean policy) {
            YamlConfiguration definition = new YamlConfiguration();
            if (policy) definition.set("GameModes", List.of("SURVIVAL"));
            Reward reward = new Reward(name, definition).needsRewardFile(false);
            reward.plugin = plugin;
            reward.getConfig().plugin = plugin;
            handler.getRewards().add(reward);
            return reward;
        }

        ArrayList<String> stored(String path) { return persisted.getOrDefault(path, new ArrayList<>()); }
        String onlyTimedKey() {
            assertEquals(1, user.getTimedRewards().size());
            return user.getTimedRewards().keySet().iterator().next();
        }
        void onOwner(Runnable task) {
            boolean previous = playerOwner.get();
            playerOwner.set(true);
            try { task.run(); } finally { playerOwner.set(previous); }
        }
        void drainQueuedWork() {
            for (int attempt = 0; attempt < 100 && (!workers.isEmpty() || !callbacks.isEmpty()); attempt++) {
                Runnable worker = workers.poll();
                if (worker != null) {
                    boolean previous = playerOwner.get();
                    playerOwner.set(false);
                    try { worker.run(); } finally { playerOwner.set(previous); }
                } else callbacks.remove().run();
            }
            assertTrue(workers.isEmpty());
            assertTrue(callbacks.isEmpty());
        }
        void drain() throws Exception {
            for (int attempt = 0; attempt < 30 && !callbacks.isEmpty(); attempt++) callbacks.remove().run();
            assertTrue(callbacks.isEmpty());
            assertFalse(user.hasPendingRewardReplay(), "queued replay must release its claims after completion");
        }
        @Override public void close() {
            handler.getRewardEligibility().close();
            delayed.shutdownNow();
            AdvancedCorePlugin.setInstance(null);
            bukkit.close();
        }
    }
}
