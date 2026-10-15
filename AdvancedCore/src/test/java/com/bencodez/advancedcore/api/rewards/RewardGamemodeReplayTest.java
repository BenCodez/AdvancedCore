package com.bencodez.advancedcore.api.rewards;

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
            reward.plugin = plugin;
            reward.getConfig().plugin = plugin;
            handler.getRewards().add(reward);
            handler.addInjectedReward(effect("Prefix", prefix, mode, GameMode.CREATIVE, false, yield ? boundary : null));
            handler.addInjectedReward(effect("Remainder", remainder, mode, GameMode.CREATIVE, true, null));
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
        final Player player = mock(Player.class);
        final UUID id = UUID.randomUUID();
        final AtomicReference<GameMode> mode = new AtomicReference<>(GameMode.SURVIVAL);
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
            bukkit.when(Bukkit::getPluginManager).thenReturn(mock(org.bukkit.plugin.PluginManager.class));
            AdvancedCorePlugin.setInstance(plugin);
            handler = new RewardHandler(plugin);
            when(plugin.getRewardHandler()).thenReturn(handler);
            user = newUser();
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
            reward.plugin = plugin;
            reward.getConfig().plugin = plugin;
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
