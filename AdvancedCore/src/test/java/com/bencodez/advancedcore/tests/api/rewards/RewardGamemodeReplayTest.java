package com.bencodez.advancedcore.tests.api.rewards;

import com.bencodez.advancedcore.api.rewards.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledExecutorService;
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
import com.bencodez.simpleapi.array.ArrayUtils;

class RewardGamemodeReplayTest {
    @TempDir File directory;

    @Test void immediateTransitionKeepsCompletedPrefix() { replayScenario(false, false); }
    @Test void immediateTransitionSurvivesHandlerReplacement() { replayScenario(false, true); }
    @Test void yieldedTransitionKeepsCompletedPrefix() { replayScenario(true, false); }
    @Test void yieldedTransitionSurvivesHandlerReplacement() { replayScenario(true, true); }

    @Test void approvedChanceSurvivesDeferralBeforeFirstEffect() throws Exception {
        firstEffectChanceScenario(false);
    }

    @Test void approvedChanceSurvivesFirstEffectDeferralAndHandlerReplacement() throws Exception {
        firstEffectChanceScenario(true);
    }

    private void firstEffectChanceScenario(boolean restart) throws Exception {
        firstEffectChanceScenario(restart, false, false);
    }

    @Test void defaultModesGuardFirstEffectAndRetainChanceOnReplay() throws Exception {
        firstEffectChanceScenario(false, true, false);
    }

    @Test void removingDefaultAfterDeferralKeepsApprovedChance() throws Exception {
        firstEffectChanceScenario(true, true, true);
    }

    @Test void realGamemodeRequirementRetainsChanceWhenItDefersAfterTheRoll() throws Exception {
        requirementOrderChanceScenario(false);
    }

    @Test void inheritedDefaultRetainsChanceWhenRealGamemodeRequirementDefers() throws Exception {
        requirementOrderChanceScenario(true);
    }

    private void requirementOrderChanceScenario(boolean defaults) throws Exception {
        try (NestedFixture fixture = new NestedFixture();
                var utility = mockStatic(com.bencodez.advancedcore.api.misc.MiscUtils.class, CALLS_REAL_METHODS)) {
            if (defaults) fixture.defaults.set("DefaultRewardGameModes", List.of("SURVIVAL"));
            Reward reward = fixture.reward("RequirementOrderChance", !defaults);
            reward.getConfig().getConfigData().set("Chance", 50);
            AtomicInteger rolls = new AtomicInteger(), effects = new AtomicInteger();
            var random = mock(com.bencodez.advancedcore.api.misc.MiscUtils.class, CALLS_REAL_METHODS);
            utility.when(com.bencodez.advancedcore.api.misc.MiscUtils::getInstance).thenReturn(random);
            doAnswer(call -> {
                fixture.mode.set(GameMode.CREATIVE);
                return rolls.incrementAndGet() == 1;
            }).when(random).checkChance(50, 100);
            com.bencodez.advancedcore.api.rewards.builtin.requirements.RequirementChance.register(fixture.handler, fixture.plugin);
            com.bencodez.advancedcore.api.rewards.builtin.requirements.RequirementGameMode.register(fixture.handler, fixture.plugin);
            fixture.handler.sortInjectedRequirements();
            assertEquals(List.of("Chance", "GameModes"), fixture.handler.getInjectedRequirements().stream()
                    .map(com.bencodez.advancedcore.api.rewards.injectedrequirement.RequirementInject::getPath).toList());
            fixture.handler.addInjectedReward(new RewardInject("Effect") {
                @Override public boolean supportsAsyncRequest() { return true; }
                @Override public Object onRewardRequest(Reward current, AdvancedCoreUser user,
                        ConfigurationSection data, HashMap<String, String> placeholders) {
                    effects.incrementAndGet(); return null;
                }
            });
            String occurrence = UUID.randomUUID().toString();
            RewardOptions options = new RewardOptions().setCheckTimed(false).setGiveOffline(true);
            options.setAsyncReplayOccurrenceId(occurrence);
            reward.giveRewardAsync(fixture.user, options).toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertEquals(1, rolls.get());
            assertEquals(0, effects.get());
            assertEquals(1, fixture.stored("OfflineRewards").size());
            assertTrue(fixture.stored("OfflineRewards").get(0).contains(occurrence));
            fixture.mode.set(GameMode.SURVIVAL);
            fixture.handler.getRewardEligibility().signal(fixture.user).toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertEquals(1, rolls.get(), "A passed chance must survive a later reattemptable gamemode requirement");
            assertEquals(1, effects.get());
            assertTrue(fixture.stored("OfflineRewards").isEmpty());
            fixture.handler.getRewardEligibility().signal(fixture.user).toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertEquals(1, effects.get());
        }
    }

    @Test void freshFailedChanceRemainsTerminalAndDoesNotAcquireApproval() throws Exception {
        try (NestedFixture fixture = new NestedFixture();
                var utility = mockStatic(com.bencodez.advancedcore.api.misc.MiscUtils.class, CALLS_REAL_METHODS)) {
            fixture.defaults.set("DefaultRewardGameModes", List.of("SURVIVAL"));
            Reward reward = fixture.reward("FreshFailedChance", false);
            reward.getConfig().getConfigData().set("Chance", 50);
            var random = mock(com.bencodez.advancedcore.api.misc.MiscUtils.class);
            utility.when(com.bencodez.advancedcore.api.misc.MiscUtils::getInstance).thenReturn(random);
            com.bencodez.advancedcore.api.rewards.builtin.requirements.RequirementChance.register(fixture.handler, fixture.plugin);
            com.bencodez.advancedcore.api.rewards.builtin.requirements.RequirementGameMode.register(fixture.handler, fixture.plugin);
            fixture.handler.sortInjectedRequirements();
            AtomicInteger effects = new AtomicInteger();
            fixture.handler.addInjectedReward(new RewardInject("Effect") {
                @Override public boolean supportsAsyncRequest() { return true; }
                @Override public Object onRewardRequest(Reward current, AdvancedCoreUser user,
                        ConfigurationSection data, HashMap<String, String> placeholders) {
                    effects.incrementAndGet(); return null;
                }
            });
            RewardOptions options = new RewardOptions().setCheckTimed(false).setGiveOffline(true);
            reward.giveRewardAsync(fixture.user, options).toCompletableFuture().get(5, TimeUnit.SECONDS);
            verify(random).checkChance(50, 100);
            assertEquals(0, effects.get());
            assertTrue(fixture.stored("OfflineRewards").isEmpty());
            assertTrue(options.getPlaceholders().keySet().stream()
                    .noneMatch(key -> key.startsWith("__advancedcore_replay_chance_")));
        }
    }

    @Test void timedRequirementDeferralPersistsApprovedChanceBeforeFirstEffect() throws Exception {
        timedRequirementChanceScenario(false);
    }

    @Test void timedRequirementApprovalSurvivesHandlerReplacement() throws Exception {
        timedRequirementChanceScenario(true);
    }

    private void timedRequirementChanceScenario(boolean restart) throws Exception {
        queuedRequirementChanceScenario(restart, QueuedDeferral.MODE, true);
    }

    @Test void timedPauseAfterPassedChancePreservesApproval() throws Exception {
        queuedRequirementChanceScenario(false, QueuedDeferral.PAUSE, true);
    }

    @Test void timedRetryableRequirementAfterChancePreservesApproval() throws Exception {
        queuedRequirementChanceScenario(false, QueuedDeferral.REQUIREMENT, true);
    }

    @Test void timedRequirementExceptionAfterPassedChancePreservesApproval() throws Exception {
        queuedRequirementChanceScenario(false, QueuedDeferral.REQUIREMENT_FAILURE, true);
    }

    @Test void timedRejectedNativeHandoffAfterPassedChancePreservesApproval() throws Exception {
        queuedRequirementChanceScenario(false, QueuedDeferral.NATIVE_REJECTION, true);
    }

    @Test void offlineRequirementExceptionAfterPassedChancePreservesApproval() throws Exception {
        queuedRequirementChanceScenario(false, QueuedDeferral.REQUIREMENT_FAILURE, false);
    }

    private enum QueuedDeferral { MODE, PAUSE, REQUIREMENT, REQUIREMENT_FAILURE, NATIVE_REJECTION }

    private void queuedRequirementChanceScenario(boolean restart, QueuedDeferral reason, boolean timed) throws Exception {
        boolean pause = reason == QueuedDeferral.PAUSE;
        boolean retryable = reason == QueuedDeferral.REQUIREMENT;
        boolean providerFailure = reason == QueuedDeferral.REQUIREMENT_FAILURE;
        boolean nativeRejection = reason == QueuedDeferral.NATIVE_REJECTION;
        try (NestedFixture fixture = new NestedFixture();
                var utility = mockStatic(com.bencodez.advancedcore.api.misc.MiscUtils.class, CALLS_REAL_METHODS)) {
            fixture.defaults.set("DefaultRewardGameModes", List.of("SURVIVAL"));
            Reward reward = fixture.reward("TimedRequirementChance", false);
            reward.getConfig().getConfigData().set("Chance", 50);
            AtomicInteger rolls = new AtomicInteger(), effects = new AtomicInteger();
            var paused = new java.util.concurrent.atomic.AtomicBoolean();
            var allowRequirement = new java.util.concurrent.atomic.AtomicBoolean(!(retryable || providerFailure || nativeRejection));
            var rejectNative = new java.util.concurrent.atomic.AtomicBoolean();
            when(fixture.plugin.getOptions().isPauseRewards()).thenAnswer(call -> paused.get());
            var nativeScheduler = fixture.plugin.getBukkitScheduler();
            doAnswer(call -> {
                if (rejectNative.getAndSet(false))
                    throw new java.util.concurrent.RejectedExecutionException("Synthetic native handoff rejection");
                call.getArgument(1, Runnable.class).run(); return null;
            }).when(nativeScheduler).executeOrScheduleSync(eq(fixture.plugin), any(Runnable.class), any(Player.class));
            var random = mock(com.bencodez.advancedcore.api.misc.MiscUtils.class, CALLS_REAL_METHODS);
            utility.when(com.bencodez.advancedcore.api.misc.MiscUtils::getInstance).thenReturn(random);
            doAnswer(call -> {
                if (pause) paused.set(true);
                else if (reason == QueuedDeferral.MODE) fixture.mode.set(GameMode.CREATIVE);
                return rolls.incrementAndGet() == 1;
            }).when(random).checkChance(50, 100);
            com.bencodez.advancedcore.api.rewards.builtin.requirements.RequirementChance.register(fixture.handler, fixture.plugin);
            com.bencodez.advancedcore.api.rewards.builtin.requirements.RequirementGameMode.register(fixture.handler, fixture.plugin);
            if (retryable || providerFailure || nativeRejection) fixture.handler.getInjectedRequirements().add(
                    new com.bencodez.advancedcore.api.rewards.injectedrequirement.RequirementInject("AfterChance") {
                @Override public boolean onRequirementRequest(Reward current, AdvancedCoreUser user,
                        ConfigurationSection data, RewardOptions options) {
                    if (providerFailure && !allowRequirement.get()) throw new IllegalStateException("Synthetic requirement failure");
                    if (nativeRejection && !allowRequirement.get()) rejectNative.set(true);
                    return !retryable || allowRequirement.get();
                }
            }.alwaysForceNoData().allowReattempt().priority(100));
            fixture.handler.sortInjectedRequirements();
            fixture.handler.addInjectedReward(new RewardInject("Effect") {
                @Override public boolean supportsAsyncRequest() { return true; }
                @Override public Object onRewardRequest(Reward current, AdvancedCoreUser user,
                        ConfigurationSection data, HashMap<String, String> placeholders) {
                    effects.incrementAndGet(); return null;
                }
            });
            HashMap<String, String> placeholders = new HashMap<>();
            placeholders.put("__AdvancedCoreAsyncReplayKey", "Scoped/TimedChance");
            placeholders.put("payload", "retained");
            if (timed) {
                fixture.user.addTimedReward(reward, placeholders, System.currentTimeMillis() - 1_000);
                fixture.user.checkDelayedTimedRewards();
            } else {
                RewardOptions options = new RewardOptions().setCheckTimed(false).setGiveOffline(true).withPlaceHolder(placeholders);
                options.setAsyncReplayOccurrenceId(UUID.randomUUID().toString());
                fixture.user.addOfflineRewards(reward, placeholders, options);
                fixture.user.checkOfflineRewardsAsync().toCompletableFuture().get(5, TimeUnit.SECONDS);
            }
            String queue = timed ? "TimedRewards" : "OfflineRewards";
            assertEquals(1, rolls.get());
            assertEquals(0, effects.get());
            assertEquals(1, fixture.stored(queue).size());
            String retained = fixture.stored(queue).get(0);
            HashMap<String, String> snapshot = ArrayUtils.fromString(retained.split("%placeholders%", 2)[1]
                    .split("%ExecutionTime/%", 2)[0]);
            assertTrue(snapshot.entrySet().stream().anyMatch(entry ->
                    entry.getKey().startsWith("__advancedcore_replay_chance_") && "1".equals(entry.getValue())),
                    "A metadata-only queued deferral must persist its approved chance");
            assertFalse(retained.split("%placeholders%", 2)[0].contains("%asyncprogress%"),
                    "Retaining metadata must not invent completed effect stages");
            assertEquals("Scoped/TimedChance", snapshot.get("__AdvancedCoreAsyncReplayKey"));
            assertEquals("retained", snapshot.get("payload"));
            if (restart) {
                fixture.restart();
                com.bencodez.advancedcore.api.rewards.builtin.requirements.RequirementChance.register(fixture.handler, fixture.plugin);
                com.bencodez.advancedcore.api.rewards.builtin.requirements.RequirementGameMode.register(fixture.handler, fixture.plugin);
                fixture.handler.sortInjectedRequirements();
            }
            fixture.mode.set(GameMode.SURVIVAL);
            paused.set(false);
            allowRequirement.set(true);
            if (timed && reason != QueuedDeferral.MODE) {
                HashMap<String, Long> queuedTimes = fixture.user.getTimedRewards();
                assertTrue(queuedTimes.values().stream().allMatch(time -> time > System.currentTimeMillis()),
                        "Ordinary deferrals must retain the existing future retry deadline");
                queuedTimes.replaceAll((entry, time) -> System.currentTimeMillis() - 1_000);
                fixture.user.setTimedRewards(queuedTimes);
            }
            if (timed) fixture.user.checkDelayedTimedRewards();
            else fixture.user.checkOfflineRewardsAsync().toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertEquals(1, rolls.get(), "Timed replay must not reroll the approved occurrence");
            assertEquals(1, effects.get());
            assertTrue(fixture.stored(queue).isEmpty());
            if (timed) fixture.user.checkDelayedTimedRewards();
            else fixture.user.checkOfflineRewardsAsync().toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertEquals(1, effects.get());
        }
    }

    @Test void globalDefaultRetainsAndResumesDueTimedOccurrence() throws Exception {
        try (NestedFixture fixture = new NestedFixture()) {
            fixture.defaults.set("DefaultRewardGameModes", List.of("SURVIVAL"));
            fixture.mode.set(GameMode.CREATIVE);
            Reward reward = fixture.reward("DefaultTimed", false);
            AtomicInteger effects = new AtomicInteger();
            fixture.handler.addInjectedReward(new RewardInject("Effect") {
                @Override public boolean supportsAsyncRequest() { return true; }
                @Override public Object onRewardRequest(Reward current, AdvancedCoreUser user,
                        ConfigurationSection data, HashMap<String, String> placeholders) {
                    effects.incrementAndGet(); return null;
                }
            });
            fixture.user.addTimedReward(reward, new HashMap<>(), System.currentTimeMillis() - 1_000);
            fixture.user.checkDelayedTimedRewards();
            assertEquals(0, effects.get());
            assertEquals(1, fixture.stored("TimedRewards").size());
            String retained = fixture.stored("TimedRewards").get(0);
            fixture.user.checkDelayedTimedRewards();
            assertEquals(1, fixture.stored("TimedRewards").size());
            assertEquals(retained.replaceAll("%asyncretry%[0-9]+", "%asyncretry%<attempt>"),
                    fixture.stored("TimedRewards").get(0).replaceAll("%asyncretry%[0-9]+", "%asyncretry%<attempt>"),
                    "Retry attempts may advance; occurrence identity, due time and payload must remain unchanged");
            assertEquals(0, effects.get());
            fixture.mode.set(GameMode.SURVIVAL);
            fixture.handler.getRewardEligibility().signal(fixture.user).toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertEquals(1, effects.get());
            assertTrue(fixture.stored("TimedRewards").isEmpty());
            fixture.user.checkDelayedTimedRewards();
            assertEquals(1, effects.get());
        }
    }

    private void firstEffectChanceScenario(boolean restart, boolean defaults, boolean removeDefault) throws Exception {
        try (NestedFixture fixture = new NestedFixture()) {
            if (defaults) fixture.defaults.set("DefaultRewardGameModes", List.of("SURVIVAL"));
            Reward reward = fixture.reward("FirstEffectChance", !defaults);
            AtomicInteger rolls = new AtomicInteger(), effects = new AtomicInteger();
            var chance = new com.bencodez.advancedcore.api.rewards.injectedrequirement.RequirementInject("Chance") {
                @Override public boolean onRequirementRequest(Reward current, AdvancedCoreUser user,
                        ConfigurationSection data, RewardOptions options) {
                    fixture.mode.set(GameMode.CREATIVE);
                    return rolls.incrementAndGet() == 1;
                }
            }.alwaysForceNoData();
            fixture.handler.getInjectedRequirements().add(chance);
            fixture.handler.addInjectedReward(new RewardInject("Effect") {
                @Override public boolean supportsAsyncRequest() { return true; }
                @Override public Object onRewardRequest(Reward current, AdvancedCoreUser user,
                        ConfigurationSection data, HashMap<String, String> placeholders) {
                    effects.incrementAndGet(); return null;
                }
            });
            String occurrence = UUID.randomUUID().toString();
            RewardOptions options = new RewardOptions().setCheckTimed(false).setGiveOffline(true);
            options.setAsyncReplayOccurrenceId(occurrence);
            reward.giveRewardAsync(fixture.user, options).toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertEquals(1, rolls.get());
            assertEquals(0, effects.get());
            assertEquals(1, fixture.stored("OfflineRewards").size());
            assertTrue(fixture.stored("OfflineRewards").get(0).contains(occurrence));
            assertTrue(options.getAsyncReplayProgress().values().stream().allMatch(count -> count == 0),
                    "No effect has begun when the approved occurrence is deferred");
            if (restart) {
                fixture.restart();
                fixture.handler.getInjectedRequirements().add(chance);
            }
            fixture.mode.set(GameMode.SURVIVAL);
            if (removeDefault) fixture.defaults.set("DefaultRewardGameModes", null);
            fixture.handler.getRewardEligibility().signal(fixture.user).toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertEquals(1, rolls.get(), "Persisted approval must prevent a second chance roll");
            assertEquals(1, effects.get());
            assertTrue(fixture.stored("OfflineRewards").isEmpty());
            fixture.handler.getRewardEligibility().signal(fixture.user).toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertEquals(1, effects.get(), "A duplicate signal must not repeat the effect");
        }
    }

    private void replayScenario(boolean yield, boolean restart) {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        when(plugin.isEnabled()).thenReturn(true);
        when(plugin.getDataFolder()).thenReturn(directory);
        when(plugin.getLogger()).thenReturn(mock(Logger.class));
        AdvancedCoreConfigOptions config = mock(AdvancedCoreConfigOptions.class);
        when(config.isProcessRewards()).thenReturn(true);
        when(config.isOnlineMode()).thenReturn(true);
        when(config.getFormatRewardTimeFormat()).thenReturn("yyyy-MM-dd");
        when(plugin.getOptions()).thenReturn(config);
        UserManager users = mock(UserManager.class);
        UserDataManager storage = mock(UserDataManager.class);
        when(plugin.getUserManager()).thenReturn(users);
        when(users.getDataManager()).thenReturn(storage);
        when(users.getOfflineRewardsPath()).thenReturn("OfflineRewards");
        UserData data = mock(UserData.class);
        AtomicReference<ArrayList<String>> persisted = new AtomicReference<>(new ArrayList<>());
        when(data.getStringList("OfflineRewards", UserDataFetchMode.DEFAULT))
                .thenAnswer(call -> new ArrayList<>(persisted.get()));
        doAnswer(call -> { persisted.set(new ArrayList<>(call.getArgument(1))); return null; })
                .when(data).setStringList(eq("OfflineRewards"), any());
        doAnswer(call -> { persisted.set(new ArrayList<>(call.getArgument(1))); return null; })
                .when(data).setStringList(eq("OfflineRewards"), any(), eq(false));
        var scheduler = mock(com.bencodez.simpleapi.scheduler.BukkitScheduler.class);
        when(plugin.getBukkitScheduler()).thenReturn(scheduler);
        doAnswer(call -> { call.getArgument(1, Runnable.class).run(); return null; })
                .when(scheduler).executeOrScheduleSync(eq(plugin), any(Runnable.class));
        doAnswer(call -> { call.getArgument(1, Runnable.class).run(); return null; })
                .when(scheduler).executeOrScheduleSync(eq(plugin), any(Runnable.class), any(Player.class));
        doAnswer(call -> { call.getArgument(1, Runnable.class).run(); return null; })
                .when(scheduler).runTaskAsynchronously(eq(plugin), any(Runnable.class));
        doAnswer(call -> { call.getArgument(1, Runnable.class).run(); return null; })
                .when(scheduler).runTask(eq(plugin), any(Runnable.class), any(Player.class));
        ScheduledExecutorService timer = mock(ScheduledExecutorService.class);
        when(plugin.getTimer()).thenReturn(timer);
        doAnswer(call -> { call.getArgument(0, Runnable.class).run(); return null; }).when(timer).execute(any());
        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        when(player.isOnline()).thenReturn(true);
        HashMap<org.bukkit.plugin.Plugin, MetadataValue> loginReceipt = new HashMap<>();
        when(player.getMetadata("AdvancedCoreRewardEligibilityLogin"))
                .thenAnswer(call -> loginReceipt.isEmpty() ? List.of() : List.copyOf(loginReceipt.values()));
        doAnswer(call -> { MetadataValue value = call.getArgument(1);
            loginReceipt.put(value.getOwningPlugin(), value); return null; })
                .when(player).setMetadata(eq("AdvancedCoreRewardEligibilityLogin"), any(MetadataValue.class));
        doAnswer(call -> { loginReceipt.remove(call.getArgument(1)); return null; })
                .when(player).removeMetadata(eq("AdvancedCoreRewardEligibilityLogin"), any());
        AtomicReference<GameMode> mode = new AtomicReference<>(GameMode.SURVIVAL);
        when(player.getGameMode()).thenAnswer(call -> mode.get());
        AdvancedCoreUser user = spy(new AdvancedCoreUser(plugin, id, "GamemodePlayer"));
        user.setData(data);
        doReturn(player).when(user).getPlayer();
        doReturn(true).when(user).isOnline();
        AdvancedCorePlugin.setInstance(plugin);
        RewardHandler handler = new RewardHandler(plugin);
        when(plugin.getRewardHandler()).thenReturn(handler);
        AtomicInteger prefix = new AtomicInteger(), remainder = new AtomicInteger();
        java.util.List<String> debug = new java.util.ArrayList<>();
        doAnswer(call -> { debug.add(call.getArgument(0)); return null; }).when(plugin).debug(anyString());
        var boundary = new java.util.concurrent.CompletableFuture<Object>();
        RewardHandler recovered = null;
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(mock(org.bukkit.plugin.PluginManager.class));
            YamlConfiguration definition = new YamlConfiguration();
            definition.set("GameModes", List.of("SURVIVAL"));
            Reward reward = new Reward("GamemodeReplay", definition).needsRewardFile(false);
            // Legacy BaseTest can retain a static singleton mock across classes. Bind this
            // real reward and its configuration to the fixture, as runtime construction does.
            EligibilityAccess.bind(reward, plugin);
            EligibilityAccess.bind(reward.getConfig(), plugin);
            handler.getRewards().add(reward);
            handler.addInjectedReward(effect("Prefix", prefix, mode, GameMode.CREATIVE, false, yield ? boundary : null));
            handler.addInjectedReward(effect("Remainder", remainder, mode, GameMode.CREATIVE, true, null));
            handler.getRewardEligibility().signalLogin(user, player).toCompletableFuture().join();
            RewardOptions options = new RewardOptions().setCheckTimed(false).setGiveOffline(true);
            options.setAsyncReplayOccurrenceId("gamemode-occurrence");
            options.setAsyncReplayKey("GamemodeParent/GamemodeChild");
            var delivery = reward.giveRewardAsync(user, options).toCompletableFuture();
            if (yield) {
                assertFalse(delivery.isDone(), () -> "Unexpected early completion: " + debug);
                assertEquals(1, prefix.get(), () -> "Reward did not execute prefix: " + debug);
                assertEquals(0, remainder.get());
                mode.set(GameMode.CREATIVE);
                boundary.complete(null);
            }
            delivery.join();
            assertEquals(1, prefix.get(), () -> "Reward did not execute prefix: " + debug);
            assertEquals(0, remainder.get());
            assertEquals(1, persisted.get().size());
            assertTrue(persisted.get().get(0).contains("gamemode-occurrence"));
            AdvancedCoreUser resumed = user;
            RewardHandler current = handler;
            if (restart) {
                handler.getRewardEligibility().close();
                recovered = new RewardHandler(plugin);
                recovered.getInjectedRewards().addAll(handler.getInjectedRewards());
                recovered.getRewards().add(reward);
                when(plugin.getRewardHandler()).thenReturn(recovered);
                current = recovered;
                resumed = spy(new AdvancedCoreUser(plugin, id, "GamemodePlayer"));
                resumed.setData(data);
                doReturn(player).when(resumed).getPlayer();
                doReturn(true).when(resumed).isOnline();
            }
            mode.set(GameMode.SURVIVAL);
            current.getRewardEligibility().signal(resumed).toCompletableFuture().join();
            assertEquals(1, prefix.get(), () -> "Reward did not execute prefix: " + debug);
            assertEquals(1, remainder.get());
            current.getRewardEligibility().signal(resumed).toCompletableFuture().join();
            assertEquals(1, remainder.get());
            assertTrue(persisted.get().isEmpty());
        } finally {
            handler.getRewardEligibility().close();
            handler.getDelayedTimer().shutdownNow();
            if (recovered != null) { recovered.getRewardEligibility().close(); recovered.getDelayedTimer().shutdownNow(); }
            AdvancedCorePlugin.setInstance(null);
        }
    }

    @Test void offlineParentKeepsItsKeyWhenChildDefers() throws Exception {
        nestedReplayScenario(false, false, false);
    }

    @Test void offlineParentKeepsItsKeyAcrossHandlerReplacement() throws Exception {
        nestedReplayScenario(false, false, true);
    }

    @Test void timedParentKeepsItsKeyWhenChildDefers() throws Exception {
        nestedReplayScenario(true, false, false);
    }

    @Test void timedParentKeepsItsKeyAcrossHandlerReplacement() throws Exception {
        nestedReplayScenario(true, false, true);
    }

    @Test void independentlyQueuedChildKeepsItsOwnKey() throws Exception {
        nestedReplayScenario(false, true, false);
    }

    @Test void independentlyQueuedChildKeepsItsOwnKeyAcrossHandlerReplacement() throws Exception {
        nestedReplayScenario(false, true, true);
    }

    private void nestedReplayScenario(boolean timed, boolean independentChild, boolean restart) throws Exception {
        try (NestedFixture fixture = new NestedFixture()) {
            Reward parent = fixture.reward("Parent", !independentChild);
            Reward child = fixture.reward("Child", true);
            AtomicInteger parentPrefix = new AtomicInteger(), childPrefix = new AtomicInteger();
            AtomicInteger parentRemainder = new AtomicInteger(), childRemainder = new AtomicInteger();
            CompletableFuture<Object> childBoundary = new CompletableFuture<>();
            fixture.handler.addInjectedReward(new RewardInject("Prefix") {
                @Override public boolean supportsAsyncRequest() { return true; }
                @Override public Object onRewardRequest(Reward reward, AdvancedCoreUser user,
                        ConfigurationSection data, HashMap<String, String> placeholders) {
                    (reward == parent ? parentPrefix : childPrefix).incrementAndGet();
                    return null;
                }
            });
            fixture.handler.addInjectedReward(new RewardInject("Nested") {
                @Override public boolean supportsAsyncRequest() { return true; }
                @Override public Object onRewardRequest(Reward reward, AdvancedCoreUser user,
                        ConfigurationSection data, HashMap<String, String> placeholders) { return null; }
                @Override public CompletionStage<Object> onRewardRequestAsync(Reward reward, AdvancedCoreUser user,
                        ConfigurationSection data, HashMap<String, String> placeholders) {
                    if (reward != parent) return CompletableFuture.completedFuture(null);
                    RewardOptions options = Reward.withReplayState(new RewardOptions().setCheckTimed(false)
                            .withPlaceHolder(placeholders), Reward.currentReplayState(), Reward.currentReplayKey(),
                            "only", Reward.currentReplayOccurrenceId());
                    return child.giveRewardAsync(user, options).thenApply(ignored -> null);
                }
            });
            fixture.handler.addInjectedReward(new RewardInject("Transition") {
                @Override public boolean supportsAsyncRequest() { return true; }
                @Override public CompletionStage<Object> onRewardRequestAsync(Reward reward, AdvancedCoreUser user,
                        ConfigurationSection data, HashMap<String, String> placeholders) {
                    Object result = onRewardRequest(reward, user, data, placeholders);
                    return reward == child && !independentChild ? childBoundary
                            : CompletableFuture.completedFuture(result);
                }
                @Override public Object onRewardRequest(Reward reward, AdvancedCoreUser user,
                        ConfigurationSection data, HashMap<String, String> placeholders) {
                    if (reward == child) fixture.mode.set(GameMode.CREATIVE);
                    return null;
                }
            });
            fixture.handler.addInjectedReward(new RewardInject("Remainder") {
                @Override public boolean supportsAsyncRequest() { return true; }
                @Override public Object onRewardRequest(Reward reward, AdvancedCoreUser user,
                        ConfigurationSection data, HashMap<String, String> placeholders) {
                    (reward == parent ? parentRemainder : childRemainder).incrementAndGet();
                    return null;
                }
            });
            String parentKey = "Original/Parent";
            String childKey = parentKey + "/1/only";
            RewardOptions parentOptions = new RewardOptions().setCheckTimed(false);
            parentOptions.setAsyncReplayKey(parentKey);
            parentOptions.setAsyncReplayOccurrenceId("nested-occurrence");
            CompletionStage<Void> delivery = null;
            if (independentChild) {
                parent.giveRewardAsync(fixture.user, parentOptions).toCompletableFuture().get(5, TimeUnit.SECONDS);
            } else if (timed) {
                HashMap<String, String> placeholders = new HashMap<>();
                placeholders.put("__AdvancedCoreAsyncReplayKey", parentKey);
                fixture.user.setTimedRewards(new HashMap<>(java.util.Map.of("Parent%placeholders%"
                        + ArrayUtils.makeString(placeholders), System.currentTimeMillis() - 1_000)));
                fixture.user.checkDelayedTimedRewards();
            } else {
                fixture.user.addOfflineRewards(parent, parentOptions.getPlaceholders(), parentOptions);
                delivery = fixture.user.checkOfflineRewardsAsync();
            }
            if (!independentChild) {
                try {
                    assertFalse(childBoundary.isDone());
                    assertEquals(1, childPrefix.get());
                    ArrayList<String> checkpointed = fixture.stored(timed ? "TimedRewards" : "OfflineRewards");
                    assertEquals(1, checkpointed.size());
                    String snapshot = checkpointed.get(0).split("%placeholders%", 2)[1]
                            .split("%ExecutionTime/%", 2)[0];
                    assertEquals(parentKey, ArrayUtils.fromString(snapshot).get("__AdvancedCoreAsyncReplayKey"),
                            "An in-flight child's checkpoint belongs to the durable parent");
                } finally {
                    childBoundary.complete(null);
                }
                if (delivery != null) delivery.toCompletableFuture().get(5, TimeUnit.SECONDS);
            }
            assertEquals(1, parentPrefix.get());
            assertEquals(1, childPrefix.get());
            assertEquals(0, childRemainder.get());
            assertEquals(independentChild ? 1 : 0, parentRemainder.get());
            ArrayList<String> pending = fixture.stored(timed ? "TimedRewards" : "OfflineRewards");
            assertEquals(1, pending.size());
            String placeholderText = pending.get(0).split("%placeholders%", 2)[1]
                    .split("%ExecutionTime/%", 2)[0];
            assertEquals(independentChild ? childKey : parentKey,
                    ArrayUtils.fromString(placeholderText).get("__AdvancedCoreAsyncReplayKey"),
                    "A checkpoint must retain the execution key of its own queue entry");
            if (restart) fixture.restart();
            fixture.mode.set(GameMode.SURVIVAL);
            if (timed) {
                HashMap<String, Long> due = fixture.user.getTimedRewards();
                due.replaceAll((entry, time) -> System.currentTimeMillis() - 1_000);
                fixture.user.setTimedRewards(due);
                fixture.user.checkDelayedTimedRewards();
                fixture.user.checkDelayedTimedRewards();
            } else {
                fixture.handler.getRewardEligibility().signal(fixture.user).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                fixture.handler.getRewardEligibility().signal(fixture.user).toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
            }
            assertEquals(1, parentPrefix.get(), "The parent's completed prefix must not run twice");
            assertEquals(1, childPrefix.get(), "The child's completed prefix must not run twice");
            assertEquals(1, childRemainder.get(), "The child's unfinished work must resume");
            assertEquals(1, parentRemainder.get(), "The parent must finish after its child");
            assertTrue(fixture.stored("OfflineRewards").isEmpty());
            assertTrue(fixture.stored("TimedRewards").isEmpty());
        }
    }

    private final class NestedFixture implements AutoCloseable {
        final AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        final UserData data = mock(UserData.class);
        final HashMap<String, ArrayList<String>> persisted = new HashMap<>();
        final YamlConfiguration defaults = new YamlConfiguration();
        final Player player = mock(Player.class);
        final UUID id = UUID.randomUUID();
        final AtomicReference<GameMode> mode = new AtomicReference<>(GameMode.SURVIVAL);
        final HashMap<org.bukkit.plugin.Plugin, MetadataValue> loginReceipt = new HashMap<>();
        final org.mockito.MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
        RewardHandler handler;
        AdvancedCoreUser user;

        NestedFixture() {
            when(plugin.isEnabled()).thenReturn(true);
            when(plugin.getDataFolder()).thenReturn(directory);
            when(plugin.getLogger()).thenReturn(mock(Logger.class));
            AdvancedCoreConfigOptions config = mock(AdvancedCoreConfigOptions.class);
            when(config.isProcessRewards()).thenReturn(true);
            when(config.isOnlineMode()).thenReturn(true);
            when(config.getFormatRewardTimeFormat()).thenReturn("yyyy-MM-dd");
            when(plugin.getOptions()).thenReturn(config);
            var yaml = mock(com.bencodez.simpleapi.file.YMLConfig.class);
            when(yaml.getData()).thenReturn(defaults);
            when(config.getYmlConfig()).thenReturn(yaml);
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
            doAnswer(call -> { call.getArgument(1, Runnable.class).run(); return null; })
                    .when(scheduler).executeOrScheduleSync(eq(plugin), any(Runnable.class), any(Player.class));
            doAnswer(call -> { call.getArgument(1, Runnable.class).run(); return null; })
                    .when(scheduler).runTaskAsynchronously(eq(plugin), any(Runnable.class));
            doAnswer(call -> { call.getArgument(1, Runnable.class).run(); return null; })
                    .when(scheduler).runTask(eq(plugin), any(Runnable.class), any(Player.class));
            ScheduledExecutorService timer = mock(ScheduledExecutorService.class);
            when(plugin.getTimer()).thenReturn(timer);
            doAnswer(call -> { call.getArgument(0, Runnable.class).run(); return null; }).when(timer).execute(any());
            when(player.getUniqueId()).thenReturn(id);
            when(player.isOnline()).thenReturn(true);
            when(player.getGameMode()).thenAnswer(call -> mode.get());
            when(player.getMetadata("AdvancedCoreRewardEligibilityLogin"))
                    .thenAnswer(call -> loginReceipt.isEmpty() ? List.of() : List.copyOf(loginReceipt.values()));
            doAnswer(call -> { MetadataValue value = call.getArgument(1);
            loginReceipt.put(value.getOwningPlugin(), value); return null; })
                    .when(player).setMetadata(eq("AdvancedCoreRewardEligibilityLogin"), any(MetadataValue.class));
            doAnswer(call -> { loginReceipt.remove(call.getArgument(1)); return null; })
                    .when(player).removeMetadata(eq("AdvancedCoreRewardEligibilityLogin"), any());
            bukkit.when(Bukkit::getPluginManager).thenReturn(mock(org.bukkit.plugin.PluginManager.class));
            AdvancedCorePlugin.setInstance(plugin);
            handler = new RewardHandler(plugin);
            when(plugin.getRewardHandler()).thenReturn(handler);
            user = newUser();
            handler.getRewardEligibility().signalLogin(user, player).toCompletableFuture().join();
        }

        ArrayList<String> stored(String path) { return persisted.getOrDefault(path, new ArrayList<>()); }

        AdvancedCoreUser newUser() {
            AdvancedCoreUser created = spy(new AdvancedCoreUser(plugin, id, "NestedPlayer"));
            created.setData(data);
            doReturn(player).when(created).getPlayer();
            doReturn(true).when(created).isOnline();
            return created;
        }

        Reward reward(String name, boolean configured) {
            YamlConfiguration definition = new YamlConfiguration();
            if (configured) definition.set("GameModes", List.of("SURVIVAL"));
            Reward reward = new Reward(name, definition).needsRewardFile(false);
            EligibilityAccess.bind(reward, plugin);
            EligibilityAccess.bind(reward.getConfig(), plugin);
            handler.getRewards().add(reward);
            return reward;
        }

        void restart() {
            RewardHandler previous = handler;
            previous.getRewardEligibility().close();
            previous.getDelayedTimer().shutdownNow();
            handler = new RewardHandler(plugin);
            handler.getRewards().addAll(previous.getRewards());
            handler.getInjectedRewards().addAll(previous.getInjectedRewards());
            when(plugin.getRewardHandler()).thenReturn(handler);
            user = newUser();
        }

        @Override public void close() {
            handler.getRewardEligibility().close();
            handler.getDelayedTimer().shutdownNow();
            AdvancedCorePlugin.setInstance(null);
            bukkit.close();
        }
    }

    private RewardInject effect(String path, AtomicInteger count, AtomicReference<GameMode> mode,
            GameMode transition, boolean remainder, java.util.concurrent.CompletableFuture<Object> boundary) {
        return new RewardInject(path) {
            @Override public boolean supportsAsyncRequest() { return true; }
            @Override public java.util.concurrent.CompletionStage<Object> onRewardRequestAsync(Reward reward,
                    AdvancedCoreUser user, ConfigurationSection data, HashMap<String, String> placeholders) {
                if (boundary != null) { count.incrementAndGet(); return boundary; }
                return java.util.concurrent.CompletableFuture.completedFuture(onRewardRequest(reward, user, data, placeholders));
            }
            @Override public Object onRewardRequest(Reward reward, AdvancedCoreUser user,
                    ConfigurationSection data, HashMap<String, String> placeholders) {
                count.incrementAndGet();
                if (!remainder) mode.set(transition);
                return null;
            }
        };
    }
}
