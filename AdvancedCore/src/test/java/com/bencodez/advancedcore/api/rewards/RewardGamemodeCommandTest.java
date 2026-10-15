package com.bencodez.advancedcore.api.rewards;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.bencodez.advancedcore.AdvancedCoreConfigOptions;
import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.item.ItemBuilder;
import com.bencodez.advancedcore.api.misc.MiscUtils;
import com.bencodez.advancedcore.api.rewards.builtin.RewardCommands;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.advancedcore.api.user.UserData;
import com.bencodez.advancedcore.api.user.UserDataFetchMode;
import com.bencodez.advancedcore.api.user.UserManager;
import com.bencodez.advancedcore.api.user.usercache.UserDataManager;

import me.clip.placeholderapi.PlaceholderAPI;

class RewardGamemodeCommandTest {
    @TempDir File directory;

    @Test void consoleListDefersSecondCommandAndResumesOnce() throws Exception {
        commandScenario("console", true);
    }

    @Test void playerListDefersSecondCommandAndResumesOnce() throws Exception {
        commandScenario("player", true);
    }

    @Test void mixedSectionCarriesGuardFromConsoleToPlayerCommands() throws Exception {
        commandScenario("mixed", true);
    }

    @Test void mixedSectionKeepsCompletedPlayerPrefixAfterConsoleHandoff() throws Exception {
        commandScenario("mixed-player", true);
    }

    @Test void absentPolicyPreservesConsoleSequenceAfterModeChange() throws Exception {
        commandScenario("console", false);
    }

    @Test void absentPolicyPreservesPlayerSequenceAfterModeChange() throws Exception {
        commandScenario("player", false);
    }

    @Test void forcedOfflineConsoleBypassesLivePlayerPolicy() throws Exception {
        commandScenario("forced-console", true);
    }

    @Test void disconnectedConsoleSuffixIsRetained() throws Exception {
        commandScenario("console", true, true);
    }

    @Test void disconnectedPlayerCommandSuffixIsRetained() throws Exception {
        commandScenario("player", true, true);
    }

    @Test void disconnectedMixedCommandSuffixPreservesBothPrefixes() throws Exception {
        commandScenario("mixed-player", true, true);
    }

    private void commandScenario(String kind, boolean policy) throws Exception {
        commandScenario(kind, policy, false);
    }

    @Test void immediatelyRetiredConsoleSuffixIsRetained() throws Exception { commandScenario("console", true, false, 1); }
    @Test void laterRetiredConsoleSuffixIsRetained() throws Exception { commandScenario("console", true, false, 2); }
    @Test void immediatelyRetiredPlayerSuffixIsRetained() throws Exception { commandScenario("player", true, false, 1); }
    @Test void laterRetiredPlayerSuffixIsRetained() throws Exception { commandScenario("player", true, false, 2); }
    @Test void immediatelyRetiredMixedSuffixIsRetained() throws Exception { commandScenario("mixed-player", true, false, 1); }
    @Test void laterRetiredMixedSuffixIsRetained() throws Exception { commandScenario("mixed-player", true, false, 2); }

    private void commandScenario(String kind, boolean policy, boolean disconnect) throws Exception {
        commandScenario(kind, policy, disconnect, 0);
    }

    private void commandScenario(String kind, boolean policy, boolean disconnect, int retirement) throws Exception {
        boolean forcedOffline = kind.equals("forced-console");
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
        when(plugin.getUserManager()).thenReturn(users);
        when(users.getDataManager()).thenReturn(mock(UserDataManager.class));
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
        ThreadLocal<Boolean> playerOwner = ThreadLocal.withInitial(() -> false);
        BlockingQueue<Runnable> callbacks = new LinkedBlockingQueue<>();
        doAnswer(call -> { call.getArgument(1, Runnable.class).run(); return null; })
                .when(scheduler).executeOrScheduleSync(eq(plugin), any(Runnable.class));
        doAnswer(call -> { onOwner(playerOwner, call.getArgument(1)); return null; })
                .when(scheduler).executeOrScheduleSync(eq(plugin), any(Runnable.class), any(Player.class));
        doAnswer(call -> { call.getArgument(1, Runnable.class).run(); return null; })
                .when(scheduler).runTaskAsynchronously(eq(plugin), any(Runnable.class));
        doAnswer(call -> { callbacks.add(call.getArgument(1)); return null; })
                .when(scheduler).runTask(eq(plugin), any(Runnable.class));
        doAnswer(call -> { callbacks.add(call.getArgument(1)); return null; })
                .when(scheduler).runTaskLater(eq(plugin), any(Runnable.class), anyLong());
        doAnswer(call -> { Runnable task = call.getArgument(1); callbacks.add(() -> onOwner(playerOwner, task)); return null; })
                .when(scheduler).runTask(eq(plugin), any(Runnable.class), any(Player.class));
        RetiringOwnerScheduler retiringScheduler = retirement == 0 ? null
                : new RetiringOwnerScheduler(plugin, scheduler,
                        task -> callbacks.add(() -> onOwner(playerOwner, task)), callbacks::add);
        ScheduledExecutorService timer = mock(ScheduledExecutorService.class);
        when(plugin.getTimer()).thenReturn(timer);
        doAnswer(call -> { call.getArgument(0, Runnable.class).run(); return null; }).when(timer).execute(any());
        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        when(player.isOnline()).thenReturn(true);
        AtomicReference<GameMode> mode = new AtomicReference<>(GameMode.SURVIVAL);
        when(player.getGameMode()).thenAnswer(call -> {
            assertTrue(playerOwner.get(), "gamemode must only be read on the player owner scheduler");
            return mode.get();
        });
        AdvancedCoreUser user = spy(new AdvancedCoreUser(plugin, id, "CommandPlayer"));
        user.setData(data);
        doReturn(forcedOffline ? null : player).when(user).getPlayer();
        doReturn(!forcedOffline).when(user).isOnline();
        AdvancedCorePlugin.setInstance(plugin);
        RewardHandler handler = new RewardHandler(plugin);
        when(plugin.getRewardHandler()).thenReturn(handler);
        MiscUtils misc = MiscUtils.getInstance();
        Field miscPlugin = MiscUtils.class.getDeclaredField("plugin");
        miscPlugin.setAccessible(true);
        Object previousPlugin = miscPlugin.get(misc);
        miscPlugin.set(misc, plugin);
        List<String> dispatched = new ArrayList<>();
        Server server = mock(Server.class);
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);
        when(server.dispatchCommand(eq(console), anyString())).thenAnswer(call -> {
            String command = call.getArgument(1);
            dispatched.add(command);
            if (command.equals("first")) {
                if (retiringScheduler != null) retiringScheduler.retireNext(retirement == 1);
                else if (disconnect) when(player.isOnline()).thenReturn(false);
                else mode.set(GameMode.CREATIVE);
            }
            return true;
        });
        doAnswer(call -> {
            String command = call.getArgument(0, String.class).substring(1);
            dispatched.add(command);
            if (command.equals("first")) {
                if (retiringScheduler != null) retiringScheduler.retireNext(retirement == 1);
                else if (disconnect) when(player.isOnline()).thenReturn(false);
                else mode.set(GameMode.CREATIVE);
            }
            return null;
        }).when(player).chat(anyString());
        try (var bukkit = mockStatic(Bukkit.class);
                var placeholderProvider = mockStatic(PlaceholderAPI.class);
                var builders = mockConstruction(ItemBuilder.class, withSettings().defaultAnswer(RETURNS_SELF))) {
            // Isolate the optional provider, preserving the real placeholder pipeline.
            placeholderProvider.when(() -> PlaceholderAPI.setPlaceholders(any(OfflinePlayer.class), anyString()))
                    .thenAnswer(call -> call.getArgument(1, String.class));
            bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(forcedOffline ? null : player);
            bukkit.when(Bukkit::getPluginManager).thenReturn(mock(org.bukkit.plugin.PluginManager.class));
            bukkit.when(Bukkit::getServer).thenReturn(server);
            bukkit.when(Bukkit::getConsoleSender).thenReturn(console);
            RewardCommands.registerCommands(handler, plugin);
            YamlConfiguration definition = new YamlConfiguration();
            if (policy) definition.set("GameModes", List.of("SURVIVAL"));
            if (forcedOffline) definition.set("ForceOffline", true);
            if (kind.equals("console") || forcedOffline) definition.set("Commands", List.of("first", "second"));
            else if (kind.equals("player")) definition.set("Commands.Player", new ArrayList<>(List.of("first", "second")));
            else if (kind.equals("mixed-player")) {
                definition.set("Commands.Console", new ArrayList<>(List.of("console-prefix")));
                definition.set("Commands.Player", new ArrayList<>(List.of("first", "second")));
            } else {
                definition.set("Commands.Console", new ArrayList<>(List.of("first")));
                definition.set("Commands.Player", new ArrayList<>(List.of("second")));
            }
            Reward reward = new Reward("GamemodeCommand", definition).needsRewardFile(false);
            // Bind the real reward explicitly; legacy BaseTest retains a static singleton mock.
            reward.plugin = plugin;
            reward.getConfig().plugin = plugin;
            handler.getRewards().add(reward);
            RewardOptions options = new RewardOptions().setCheckTimed(false).setGiveOffline(true);
            options.setAsyncReplayOccurrenceId("command-occurrence");
            CompletableFuture<Void> delivery = reward.giveRewardAsync(user, options).toCompletableFuture();
            drainUntilDone(callbacks, delivery);
            if (policy && !forcedOffline) {
                assertEquals(kind.equals("mixed-player") ? List.of("console-prefix", "first") : List.of("first"),
                        dispatched, "second command must wait for an allowed gamemode");
                assertEquals(1, persisted.get().size());
                mode.set(GameMode.SURVIVAL);
                when(player.isOnline()).thenReturn(true);
                var replay = handler.getRewardEligibility().signal(user).toCompletableFuture();
                drainUntilDone(callbacks, replay);
            }
            assertEquals(kind.equals("mixed-player") ? List.of("console-prefix", "first", "second")
                    : List.of("first", "second"), dispatched);
            assertTrue(persisted.get().isEmpty());
            var repeatedSignal = handler.getRewardEligibility().signal(user).toCompletableFuture();
            drainUntilDone(callbacks, repeatedSignal);
            assertEquals(kind.equals("mixed-player") ? List.of("console-prefix", "first", "second")
                    : List.of("first", "second"), dispatched);
            if (retiringScheduler != null) {
                retiringScheduler.enqueueStaleOwnerCallback();
                Runnable stale = callbacks.poll(2, TimeUnit.SECONDS);
                assertNotNull(stale);
                stale.run();
                retiringScheduler.repeatRetirementSignal();
                assertEquals(kind.equals("mixed-player") ? List.of("console-prefix", "first", "second")
                        : List.of("first", "second"), dispatched);
                assertTrue(persisted.get().isEmpty());
            }
            assertTrue(callbacks.isEmpty());
        } finally {
            miscPlugin.set(misc, previousPlugin);
            handler.getRewardEligibility().close();
            handler.getDelayedTimer().shutdownNow();
            AdvancedCorePlugin.setInstance(null);
        }
    }

    private void drainUntilDone(BlockingQueue<Runnable> callbacks, CompletableFuture<?> completion) throws Exception {
        for (int attempts = 0; !completion.isDone() && attempts < 30; attempts++) {
            Runnable task = callbacks.poll(2, TimeUnit.SECONDS);
            assertNotNull(task, "command callback was not scheduled");
            task.run();
        }
        completion.get(2, TimeUnit.SECONDS);
    }

    private void onOwner(ThreadLocal<Boolean> owner, Runnable task) {
        boolean previous = owner.get();
        owner.set(true);
        try { task.run(); } finally { owner.set(previous); }
    }
}
