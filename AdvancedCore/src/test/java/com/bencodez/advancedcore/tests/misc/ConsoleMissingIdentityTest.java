package com.bencodez.advancedcore.tests.misc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.command.ConsoleCommandSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.misc.MiscUtils;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.tests.BaseTest;
import com.bencodez.simpleapi.scheduler.BukkitScheduler;

class ConsoleMissingIdentityTest {
    private AdvancedCorePlugin plugin;
    private MiscUtils misc;
    private Server server;
    private ConsoleCommandSender console;
    @BeforeEach void setup() throws Exception {
        plugin = BaseTest.getInstance().plugin;
        misc = MiscUtils.getInstance();
        Field field = MiscUtils.class.getDeclaredField("plugin");
        field.setAccessible(true);
        field.set(misc, plugin);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(plugin.getBukkitScheduler()).thenReturn(scheduler);
        doAnswer(call -> { call.getArgument(1, Runnable.class).run(); return null; })
                .when(scheduler).executeOrScheduleSync(eq(plugin), any(Runnable.class));
        doAnswer(call -> { call.getArgument(1, Runnable.class).run(); return null; })
                .when(scheduler).runTask(eq(plugin), any(Runnable.class));
        doAnswer(call -> { call.getArgument(1, Runnable.class).run(); return null; })
                .when(scheduler).runTaskLater(eq(plugin), any(Runnable.class), anyLong());
        server = mock(Server.class);
        console = mock(ConsoleCommandSender.class);
        when(plugin.isPlaceHolderAPIEnabled()).thenReturn(false);
        when(BaseTest.getInstance().options.isJavascriptEngineEnabled()).thenReturn(false);
    }
    @Test void literalSingleAndListDoNotLookUpMissingIdentity() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getServer).thenReturn(server);
            bukkit.when(Bukkit::getConsoleSender).thenReturn(console);
            HashMap<String, String> values = new HashMap<>(); values.put("value", "hello");
            misc.executeConsoleCommandsAsync("", "say %value%", values).toCompletableFuture().join();
            misc.executeConsoleCommandsAsync(null, new ArrayList<>(List.of("say one", "say two")), values, true)
                    .toCompletableFuture().join();
            verify(server).dispatchCommand(console, "say hello");
            verify(server).dispatchCommand(console, "say one");
            verify(server).dispatchCommand(console, "say two");
            bukkit.verify(() -> Bukkit.getOfflinePlayer(anyString()), never());
        }
    }
    @Test void playerAndEnabledExternalOrScriptTokensFailWithoutDispatch() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getServer).thenReturn(server);
            assertThrows(CompletionException.class, () -> misc.executeConsoleCommandsAsync(" ", "give %player% stone", new HashMap<>()).toCompletableFuture().join());
            assertThrows(CompletionException.class, () -> misc.executeConsoleCommandsAsync(null, new ArrayList<>(List.of("give {player} stone")), new HashMap<>(), true).toCompletableFuture().join());
            assertThrows(IllegalStateException.class, () -> misc.executeConsoleCommands("", "give %player% stone", new HashMap<>()));
            assertThrows(IllegalStateException.class, () -> misc.executeConsoleCommands("", new ArrayList<>(List.of("give %player% stone")), new HashMap<>(), true));
            for (String alias : List.of("{@p}", "%@p%", "{@P}", "%@P%")) {
                HashMap<String, String> builtins = new HashMap<>(); builtins.put("@p", "");
                assertThrows(CompletionException.class, () -> misc.executeConsoleCommandsAsync("", "give " + alias + " stone", builtins).toCompletableFuture().join());
                assertThrows(CompletionException.class, () -> misc.executeConsoleCommandsAsync("", new ArrayList<>(List.of("give " + alias + " stone")), builtins, true).toCompletableFuture().join());
                assertThrows(IllegalStateException.class, () -> misc.executeConsoleCommands("", "give " + alias + " stone", builtins));
                assertThrows(IllegalStateException.class, () -> misc.executeConsoleCommands("", new ArrayList<>(List.of("give " + alias + " stone")), builtins, true));
            }
            for (boolean aliasFirst : List.of(false, true)) {
                HashMap<String, String> mapped = new java.util.LinkedHashMap<>();
                if (aliasFirst) mapped.put("@p", "");
                mapped.put("target", "{@p}");
                if (!aliasFirst) mapped.put("@p", "");
                assertThrows(CompletionException.class, () -> misc.executeConsoleCommandsAsync("", "give %target% stone", mapped).toCompletableFuture().join());
            }
            for (HashMap<String, String> mapped : List.of(new HashMap<String, String>(), new java.util.LinkedHashMap<String, String>())) {
                mapped.put("targetName", "%name%");
                mapped.put("name", "%player%");
                mapped.put("player", "");
                assertEquals("give  stone", com.bencodez.advancedcore.api.messages.PlaceholderUtils
                        .replacePlaceHolder("give %targetName% stone", mapped));
                assertThrows(CompletionException.class, () -> misc.executeConsoleCommandsAsync("", "give %targetName% stone", mapped).toCompletableFuture().join());
            }
            HashMap<String, String> injected = new HashMap<>(); injected.put("target", "%player%");
            assertThrows(CompletionException.class, () -> misc.executeConsoleCommandsAsync("", "say %target%", injected).toCompletableFuture().join());
            when(plugin.isPlaceHolderAPIEnabled()).thenReturn(true);
            assertThrows(CompletionException.class, () -> misc.executeConsoleCommandsAsync("", "say %some_token%", new HashMap<>()).toCompletableFuture().join());
            injected.put("target", "%some_token%");
            assertThrows(CompletionException.class, () -> misc.executeConsoleCommandsAsync("", "say %target%", injected).toCompletableFuture().join());
            when(BaseTest.getInstance().options.isJavascriptEngineEnabled()).thenReturn(true);
            assertThrows(CompletionException.class, () -> misc.executeConsoleCommandsAsync("", "say [Javascript=player.getName()]", new HashMap<>()).toCompletableFuture().join());
            for (String script : List.of("say [Javaſcript=player.getName()]", "say [Javascrıpt=player.getName()]")) {
                assertThrows(CompletionException.class, () -> misc.executeConsoleCommandsAsync("", script, new HashMap<>()).toCompletableFuture().join());
                assertThrows(CompletionException.class, () -> misc.executeConsoleCommandsAsync("", new ArrayList<>(List.of(script)), new HashMap<>(), true).toCompletableFuture().join());
            }
            bukkit.verify(() -> Bukkit.getOfflinePlayer(anyString()), never());
            verifyNoInteractions(server);
        }
    }
    @Test void missingNameUsesActualPlaceholderApiTokenBoundaries() {
        when(plugin.isPlaceHolderAPIEnabled()).thenReturn(true);
        var replacer = new me.clip.placeholderapi.replacer.CharsReplacer(
                me.clip.placeholderapi.replacer.Replacer.Closure.PERCENT);
        List<String> commands = List.of("say 50% complete, 100% ready", "say %literal%", "say %%foo_bar%",
                "say %foo_bar%", "say %foo_bar with spaces%", "say \\%foo_bar%", "say %foo bar_baz%",
                "say %foo bar_baz%foo_bar%", "say %foo_", "say %_x%", "say %foo\t_bar%",
                "say [Javascript='%some_token%']", "say %foo[Javascript=x]_bar%",
                "say [Javascript=x] %some_token%", "say %some_token% [Javascript=x]");
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getServer).thenReturn(server);
            bukkit.when(Bukkit::getConsoleSender).thenReturn(console);
            for (String command : commands) {
                clearInvocations(server);
                var called = new java.util.concurrent.atomic.AtomicBoolean();
                com.bencodez.advancedcore.api.messages.PlaceholderUtils.replaceExternalPlaceholders(command,
                        text -> replacer.apply(text, null, ignored -> { called.set(true); return null; }), false);
                if (called.get()) {
                    assertThrows(CompletionException.class,
                            () -> misc.executeConsoleCommandsAsync("", command, new HashMap<>()).toCompletableFuture().join(), command);
                    verifyNoInteractions(server);
                } else {
                    misc.executeConsoleCommandsAsync("", command, new HashMap<>()).toCompletableFuture().join();
                    verify(server).dispatchCommand(console, command);
                }
            }
            bukkit.verify(() -> Bukkit.getOfflinePlayer(anyString()), never());
        }
    }
    @Test void mappedTextHonorsEnabledAndDisabledExpansionBoundaries() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getServer).thenReturn(server);
            bukkit.when(Bukkit::getConsoleSender).thenReturn(console);
            HashMap<String, String> values = new HashMap<>();
            values.put("value", "%external_token%");
            when(plugin.isPlaceHolderAPIEnabled()).thenReturn(false);
            misc.executeConsoleCommandsAsync("", "say %value%", values).toCompletableFuture().join();
            verify(server).dispatchCommand(console, "say %external_token%");
            when(plugin.isPlaceHolderAPIEnabled()).thenReturn(true);
            assertThrows(CompletionException.class,
                    () -> misc.executeConsoleCommandsAsync("", "say %value%", values).toCompletableFuture().join());
            values.put("value", "[Javascript=1+1]");
            when(BaseTest.getInstance().options.isJavascriptEngineEnabled()).thenReturn(true);
            misc.executeConsoleCommandsAsync("", "say %value%", values).toCompletableFuture().join();
            verify(server).dispatchCommand(console, "say [Javascript =1+1]");
            assertThrows(CompletionException.class, () -> misc.executeConsoleCommandsAsync("",
                    "say [Javascript=1+1]", new HashMap<>()).toCompletableFuture().join());
            bukkit.verify(() -> Bukkit.getOfflinePlayer(anyString()), never());
        }
    }
    @Test void scriptResultCannotCreateASecondExecutableSegment() {
        var offline = mock(OfflinePlayer.class);
        when(offline.getUniqueId()).thenReturn(java.util.UUID.randomUUID());
        when(plugin.getUserManager()).thenReturn(mock(com.bencodez.advancedcore.api.user.UserManager.class));
        when(BaseTest.getInstance().options.isJavascriptEngineEnabled()).thenReturn(true);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             var engines = mockConstruction(com.bencodez.advancedcore.api.javascript.JavascriptEngine.class,
                     (engine, context) -> {
                         when(engine.addPlayer(any(OfflinePlayer.class))).thenReturn(engine);
                         when(engine.getStringValue(anyString())).thenReturn("[Javascript=2+2]");
                     })) {
            bukkit.when(() -> Bukkit.getOfflinePlayer("Known")).thenReturn(offline);
            bukkit.when(Bukkit::getServer).thenReturn(server);
            bukkit.when(Bukkit::getConsoleSender).thenReturn(console);
            misc.executeConsoleCommandsAsync("Known", "say [Javascript=1+1]", new HashMap<>()).toCompletableFuture().join();
            verify(server).dispatchCommand(console, "say [Javascript =2+2]");
            assertEquals(1, engines.constructed().size());
            verify(engines.constructed().get(0)).getStringValue("1+1");
        }
    }
    @Test void synchronousAdaptersDispatchLiteralCommandsWithoutAName() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getServer).thenReturn(server);
            bukkit.when(Bukkit::getConsoleSender).thenReturn(console);
            misc.executeConsoleCommands("", "say single", new HashMap<>());
            misc.executeConsoleCommands((String) null, new ArrayList<>(List.of("say list")), new HashMap<>(), true);
            verify(server).dispatchCommand(console, "say single");
            verify(server).dispatchCommand(console, "say list");
            bukkit.verify(() -> Bukkit.getOfflinePlayer(anyString()), never());
        }
    }
    @Test void offlineModeMissingNameNeverSelectsAnUnrelatedPlayerForMixedRewards() throws Exception {
        when(BaseTest.getInstance().options.isOnlineMode()).thenReturn(true);
        when(plugin.isEnabled()).thenReturn(true);
        var user = new com.bencodez.advancedcore.api.user.AdvancedCoreUser(plugin, java.util.UUID.randomUUID(), "");
        when(BaseTest.getInstance().options.isOnlineMode()).thenReturn(false);
        var unrelated = mock(org.bukkit.entity.Player.class);
        var handler = mock(com.bencodez.advancedcore.api.rewards.RewardHandler.class);
        var injections = new java.util.concurrent.CopyOnWriteArrayList<com.bencodez.advancedcore.api.rewards.injected.RewardInject>();
        when(handler.getInjectedRewards()).thenReturn(injections);
        com.bencodez.advancedcore.api.rewards.builtin.RewardCommands.registerCommands(handler, plugin);
        var sectionHandler = (com.bencodez.advancedcore.api.rewards.injected.RewardInjectConfigurationSection) injections.get(1);
        var section = new org.bukkit.configuration.file.YamlConfiguration();
        section.set("Console", new ArrayList<>(List.of("say console")));
        section.set("Player", new ArrayList<>(List.of("say player")));
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer("")).thenReturn(unrelated);
            bukkit.when(Bukkit::getServer).thenReturn(server);
            bukkit.when(Bukkit::getConsoleSender).thenReturn(console);
            assertNull(user.getPlayer());
            assertNull(user.getOfflinePlayer());
            var unnamed = spy(user);
            doReturn(null).when(unnamed).getPlayerName();
            assertNull(unnamed.getPlayer());
            assertNull(unnamed.getOfflinePlayer());
            assertThrows(CompletionException.class, () -> sectionHandler
                    .onRewardRequestedAsync(null, user, section, new HashMap<>()).toCompletableFuture().join());
            verifyNoInteractions(server, unrelated);
            bukkit.verify(() -> Bukkit.getPlayer(""), never());
            bukkit.verify(() -> Bukkit.getOfflinePlayer(""), never());
            bukkit.verify(() -> Bukkit.getPlayer((String) null), never());
            bukkit.verify(() -> Bukkit.getOfflinePlayer((String) null), never());
            var reward = new Reward("test", new org.bukkit.configuration.file.YamlConfiguration());
            var prepare = Reward.class.getDeclaredMethod("prepareRewardUser",
                    com.bencodez.advancedcore.api.user.AdvancedCoreUser.class, HashMap.class);
            prepare.setAccessible(true);
            assertNull(prepare.invoke(reward, user, new HashMap<>()));
            bukkit.verify(() -> Bukkit.getPlayer(""), never());
        }
    }
    @Test void playerLookupPreservesValidNameAndUuidPaths() {
        var id = java.util.UUID.randomUUID();
        when(BaseTest.getInstance().options.isOnlineMode()).thenReturn(true);
        var user = new com.bencodez.advancedcore.api.user.AdvancedCoreUser(plugin, id, "Known");
        var player = mock(org.bukkit.entity.Player.class);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            when(BaseTest.getInstance().options.isOnlineMode()).thenReturn(false);
            bukkit.when(() -> Bukkit.getPlayer("Known")).thenReturn(player);
            assertSame(player, user.getPlayer());
            when(BaseTest.getInstance().options.isOnlineMode()).thenReturn(true);
            bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player);
            assertSame(player, user.getPlayer());
        }
    }
    @Test void permissionFallbackNeverPerformsBlankNameLookup() {
        var manager = com.bencodez.advancedcore.api.misc.PlayerManager.getInstance();
        manager.setPlugin(plugin);
        var id = java.util.UUID.randomUUID();
        var unrelated = mock(org.bukkit.entity.Player.class);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer("")).thenReturn(unrelated);
            assertFalse(manager.hasPermission("", "test"));
            assertFalse(manager.hasServerPermission(" ", "test"));
            assertFalse(manager.hasServerPermission(id, "", "test"));
            bukkit.verify(() -> Bukkit.getPlayer(""), never());
            bukkit.verify(() -> Bukkit.getPlayer(" "), never());
            verifyNoInteractions(unrelated);
        }
    }
    @Test void validNamesStillExpandForOfflineAndOnlinePlayers() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            OfflinePlayer offline = mock(OfflinePlayer.class);
            OfflinePlayer online = mock(OfflinePlayer.class);
            org.bukkit.entity.Player live = mock(org.bukkit.entity.Player.class);
            when(plugin.getUserManager()).thenReturn(mock(com.bencodez.advancedcore.api.user.UserManager.class));
            when(offline.getUniqueId()).thenReturn(java.util.UUID.randomUUID());
            when(live.getUniqueId()).thenReturn(java.util.UUID.randomUUID());
            when(BaseTest.getInstance().options.isJavascriptEngineEnabled()).thenReturn(true);
            when(online.isOnline()).thenReturn(true);
            when(online.getPlayer()).thenReturn(live);
            bukkit.when(() -> Bukkit.getOfflinePlayer("Offline")).thenReturn(offline);
            bukkit.when(() -> Bukkit.getOfflinePlayer("Online")).thenReturn(online);
            bukkit.when(Bukkit::getServer).thenReturn(server);
            bukkit.when(Bukkit::getConsoleSender).thenReturn(console);
            misc.executeConsoleCommandsAsync("Offline", new ArrayList<>(List.of("say %player%")), new HashMap<>(), false).toCompletableFuture().join();
            HashMap<String, String> values = new HashMap<>(); values.put("player", "Online");
            misc.executeConsoleCommandsAsync("Online", "say %player%", values).toCompletableFuture().join();
            verify(server).dispatchCommand(console, "say Offline");
            verify(server).dispatchCommand(console, "say Online");
            verify(offline, atLeastOnce()).isOnline();
            verify(online).getPlayer();
        }
    }
    @Test void capturedSnapshotSkipsCompletedPrefixWithMissingName() throws Exception {
        var ctor = Reward.ReplayState.class.getDeclaredConstructor(Map.class, Map.class, boolean.class);
        ctor.setAccessible(true);
        Reward.ReplayState state = ctor.newInstance(new HashMap<>(), new HashMap<>(), false);
        HashMap<String, String> values = new HashMap<>();
        List<String> dispatched = new ArrayList<>();
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getOfflinePlayer("Known")).thenReturn(null);
            bukkit.when(Bukkit::getServer).thenReturn(server);
            bukkit.when(Bukkit::getConsoleSender).thenReturn(console);
            doAnswer(call -> { String command = call.getArgument(1); dispatched.add(command);
                if (command.equals("second") && dispatched.size() == 2) throw new IllegalStateException("temporary");
                return true; }).when(server).dispatchCommand(eq(console), anyString());
            assertThrows(CompletionException.class, () -> misc.executeConsoleCommandsAsync("Known", new ArrayList<>(List.of("first", "second")), values, false, null, state, "reward/0").toCompletableFuture().join());
            misc.executeConsoleCommandsAsync("", new ArrayList<>(List.of("changed %player%")), values, false, null, state, "reward/0").toCompletableFuture().join();
            assertEquals(List.of("first", "second", "second"), dispatched);
            bukkit.verify(() -> Bukkit.getOfflinePlayer(""), never());
        }
    }
    @Test void configuredCommandFormsDispatchLiteralsAndRejectPlayerTargets() {
        var handler = mock(com.bencodez.advancedcore.api.rewards.RewardHandler.class);
        var injections = new java.util.concurrent.CopyOnWriteArrayList<com.bencodez.advancedcore.api.rewards.injected.RewardInject>();
        when(handler.getInjectedRewards()).thenReturn(injections);
        com.bencodez.advancedcore.api.rewards.builtin.RewardCommands.register(handler, plugin);
        var user = mock(com.bencodez.advancedcore.api.user.AdvancedCoreUser.class);
        when(user.getPlayerName()).thenReturn("");
        when(user.preformCommandAsync(any(), any())).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));
        var reward = mock(Reward.class);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getServer).thenReturn(server);
            bukkit.when(Bukkit::getConsoleSender).thenReturn(console);
            var number = new org.bukkit.configuration.file.YamlConfiguration();
            number.set("NumberCommand.Min", 1); number.set("NumberCommand.Max", 2);
            number.set("NumberCommand.Command", "say number");
            var single = new org.bukkit.configuration.file.YamlConfiguration(); single.set("Command", "say single");
            var list = new org.bukkit.configuration.file.YamlConfiguration(); list.set("Commands", new ArrayList<>(List.of("say list")));
            var mixed = new org.bukkit.configuration.file.YamlConfiguration(); mixed.set("Commands.Console", new ArrayList<>(List.of("say mixed")));
            var random = new org.bukkit.configuration.file.YamlConfiguration(); random.set("RandomCommand", new ArrayList<>(List.of("say random")));
            var configs = List.of(number, single, list, mixed, random);
            for (int i = 0; i < injections.size(); i++) {
                injections.get(i).onRewardRequestAsync(reward, user, configs.get(i), new HashMap<>()).toCompletableFuture().join();
            }
            for (String command : List.of("say number", "say single", "say list", "say mixed", "say random")) {
                verify(server).dispatchCommand(console, command);
            }
            number.set("NumberCommand.Command", "give %player% stone");
            single.set("Command", "give %player% stone");
            list.set("Commands", new ArrayList<>(List.of("give %player% stone")));
            mixed.set("Commands.Console", new ArrayList<>(List.of("give %player% stone")));
            random.set("RandomCommand", new ArrayList<>(List.of("give %player% stone")));
            for (int i = 0; i < injections.size(); i++) {
                int index = i;
                assertThrows(CompletionException.class, () -> injections.get(index)
                        .onRewardRequestAsync(reward, user, configs.get(index), new HashMap<>()).toCompletableFuture().join());
            }
            number.set("NumberCommand.Command", "give {@p} stone");
            single.set("Command", "give {@p} stone");
            list.set("Commands", new ArrayList<>(List.of("give {@p} stone")));
            mixed.set("Commands.Console", new ArrayList<>(List.of("give {@p} stone")));
            random.set("RandomCommand", new ArrayList<>(List.of("give {@p} stone")));
            for (int i = 0; i < injections.size(); i++) {
                int index = i;
                HashMap<String, String> builtins = new HashMap<>(); builtins.put("@p", "");
                assertThrows(CompletionException.class, () -> injections.get(index)
                        .onRewardRequestAsync(reward, user, configs.get(index), builtins).toCompletableFuture().join());
            }
            bukkit.verify(() -> Bukkit.getOfflinePlayer(anyString()), never());
            verify(server, never()).dispatchCommand(eq(console), contains("%player%"));
        }
    }
    @Test void configuredListReplayResumesOnlyFailedCommandWithMissingIdentity() throws Exception {
        var ctor = Reward.ReplayState.class.getDeclaredConstructor(Map.class, Map.class, boolean.class);
        ctor.setAccessible(true);
        Reward.ReplayState state = ctor.newInstance(new HashMap<>(), new HashMap<>(), false);
        var handler = mock(com.bencodez.advancedcore.api.rewards.RewardHandler.class);
        var injections = new java.util.concurrent.CopyOnWriteArrayList<com.bencodez.advancedcore.api.rewards.injected.RewardInject>();
        when(handler.getInjectedRewards()).thenReturn(injections);
        com.bencodez.advancedcore.api.rewards.builtin.RewardCommands.registerCommands(handler, plugin);
        var user = mock(com.bencodez.advancedcore.api.user.AdvancedCoreUser.class);
        when(user.getPlayerName()).thenReturn("Known", "");
        var config = new org.bukkit.configuration.file.YamlConfiguration();
        config.set("Commands", new ArrayList<>(List.of("first", "second")));
        var placeholders = new HashMap<String, String>();
        var reward = mock(Reward.class);
        List<String> dispatches = new ArrayList<>();
        Field stateField = Reward.class.getDeclaredField("ACTIVE_REPLAY_STATE"); stateField.setAccessible(true);
        Field keyField = Reward.class.getDeclaredField("ACTIVE_REPLAY_KEY"); keyField.setAccessible(true);
        @SuppressWarnings("unchecked") ThreadLocal<Reward.ReplayState> stateLocal = (ThreadLocal<Reward.ReplayState>) stateField.get(null);
        @SuppressWarnings("unchecked") ThreadLocal<String> keyLocal = (ThreadLocal<String>) keyField.get(null);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getOfflinePlayer("Known")).thenReturn(null);
            bukkit.when(Bukkit::getServer).thenReturn(server);
            bukkit.when(Bukkit::getConsoleSender).thenReturn(console);
            doAnswer(call -> { String command = call.getArgument(1); dispatches.add(command);
                if (command.equals("second") && dispatches.size() == 2) throw new IllegalStateException("temporary");
                return true; }).when(server).dispatchCommand(eq(console), anyString());
            stateLocal.set(state); keyLocal.set("reward/0");
            assertThrows(CompletionException.class, () -> injections.get(0)
                    .onRewardRequestAsync(reward, user, config, placeholders).toCompletableFuture().join());
            config.set("Commands", new ArrayList<>(List.of("changed %player%")));
            injections.get(0).onRewardRequestAsync(reward, user, config, placeholders).toCompletableFuture().join();
            assertEquals(List.of("first", "second", "second"), dispatches);
            bukkit.verify(() -> Bukkit.getOfflinePlayer(""), never());
        } finally { stateLocal.remove(); keyLocal.remove(); }
    }
    @Test void configuredSingleRandomAndLegacySectionUseSavedCommandsWithoutIdentity() throws Exception {
        var ctor = Reward.ReplayState.class.getDeclaredConstructor(Map.class, Map.class, boolean.class);
        ctor.setAccessible(true);
        var handler = mock(com.bencodez.advancedcore.api.rewards.RewardHandler.class);
        var injections = new java.util.concurrent.CopyOnWriteArrayList<com.bencodez.advancedcore.api.rewards.injected.RewardInject>();
        when(handler.getInjectedRewards()).thenReturn(injections);
        com.bencodez.advancedcore.api.rewards.builtin.RewardCommands.register(handler, plugin);
        var user = mock(com.bencodez.advancedcore.api.user.AdvancedCoreUser.class);
        when(user.getPlayerName()).thenReturn("");
        when(user.preformCommandAsync(any(), any(), isNull(), any(), anyString()))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));
        var reward = mock(Reward.class);
        Field stateField = Reward.class.getDeclaredField("ACTIVE_REPLAY_STATE"); stateField.setAccessible(true);
        Field keyField = Reward.class.getDeclaredField("ACTIVE_REPLAY_KEY"); keyField.setAccessible(true);
        @SuppressWarnings("unchecked") ThreadLocal<Reward.ReplayState> stateLocal = (ThreadLocal<Reward.ReplayState>) stateField.get(null);
        @SuppressWarnings("unchecked") ThreadLocal<String> keyLocal = (ThreadLocal<String>) keyField.get(null);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getServer).thenReturn(server);
            bukkit.when(Bukkit::getConsoleSender).thenReturn(console);
            for (int index : List.of(1, 3, 4)) {
                Reward.ReplayState state = ctor.newInstance(new HashMap<>(), new HashMap<>(), false);
                HashMap<String, String> values = new HashMap<>();
                assertThrows(CompletionException.class, () -> Reward.replayCommandSequence(plugin, values, "console",
                        List.of("saved"), List.of("saved"), state, "reward/0",
                        (command, position) -> java.util.concurrent.CompletableFuture.failedFuture(new IllegalStateException("temporary")))
                        .toCompletableFuture().join());
                var config = new org.bukkit.configuration.file.YamlConfiguration();
                if (index == 1) config.set("Command", "give %player% stone");
                if (index == 3) config.set("Commands.Console", new ArrayList<>(List.of("give %player% stone")));
                if (index == 4) config.set("RandomCommand", new ArrayList<>(List.of("give %player% stone")));
                stateLocal.set(state); keyLocal.set("reward/0");
                injections.get(index).onRewardRequestAsync(reward, user, config, values).toCompletableFuture().join();
                stateLocal.remove(); keyLocal.remove();
            }
            verify(server, times(3)).dispatchCommand(console, "saved");
            bukkit.verify(() -> Bukkit.getOfflinePlayer(anyString()), never());
        } finally { stateLocal.remove(); keyLocal.remove(); }
    }
    @Test void mixedSectionCarriesNullGuardAndReplayAcrossAvailabilityThread() throws Exception {
        var ctor = Reward.ReplayState.class.getDeclaredConstructor(Map.class, Map.class, boolean.class);
        ctor.setAccessible(true);
        Reward.ReplayState state = ctor.newInstance(new HashMap<>(), new HashMap<>(), false);
        HashMap<String, String> values = new HashMap<>();
        assertThrows(CompletionException.class, () -> Reward.replayCommandSequence(plugin, values, "console",
                List.of("first", "second"), List.of("first", "second"), state, "reward/0",
                (command, index) -> index == 1
                        ? java.util.concurrent.CompletableFuture.failedFuture(new IllegalStateException("temporary"))
                        : java.util.concurrent.CompletableFuture.completedFuture(null)).toCompletableFuture().join());
        var handler = mock(com.bencodez.advancedcore.api.rewards.RewardHandler.class);
        var injections = new java.util.concurrent.CopyOnWriteArrayList<com.bencodez.advancedcore.api.rewards.injected.RewardInject>();
        when(handler.getInjectedRewards()).thenReturn(injections);
        com.bencodez.advancedcore.api.rewards.builtin.RewardCommands.registerCommands(handler, plugin);
        var sectionHandler = (com.bencodez.advancedcore.api.rewards.injected.RewardInjectConfigurationSection) injections.get(1);
        var section = new org.bukkit.configuration.file.YamlConfiguration();
        section.set("Player", new ArrayList<>(List.of("player-command")));
        var user = mock(com.bencodez.advancedcore.api.user.AdvancedCoreUser.class);
        var available = new java.util.concurrent.CompletableFuture<Void>();
        when(user.validatePlayerCommandAvailabilityAsync()).thenReturn(available);
        when(user.getPlayerName()).thenReturn("");
        when(user.preformCommandAsync(any(), any(), isNull(), same(state), eq("reward/0")))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));
        MiscUtils fake = mock(MiscUtils.class);
        when(fake.executeConsoleCommandsAsync(eq(""), any(ArrayList.class), same(values), eq(true), isNull(), same(state), eq("reward/0")))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));
        Field instance = MiscUtils.class.getDeclaredField("instance"); instance.setAccessible(true);
        Object old = instance.get(null);
        Field stateField = Reward.class.getDeclaredField("ACTIVE_REPLAY_STATE"); stateField.setAccessible(true);
        Field keyField = Reward.class.getDeclaredField("ACTIVE_REPLAY_KEY"); keyField.setAccessible(true);
        @SuppressWarnings("unchecked") ThreadLocal<Reward.ReplayState> stateLocal = (ThreadLocal<Reward.ReplayState>) stateField.get(null);
        @SuppressWarnings("unchecked") ThreadLocal<String> keyLocal = (ThreadLocal<String>) keyField.get(null);
        try {
            instance.set(null, fake); stateLocal.set(state); keyLocal.set("reward/0");
            var result = sectionHandler.onRewardRequestedAsync(null, user, section, values);
            stateLocal.remove(); keyLocal.remove();
            java.util.concurrent.CompletableFuture.runAsync(() -> available.complete(null)).get(5, java.util.concurrent.TimeUnit.SECONDS);
            result.toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS);
            verify(fake).executeConsoleCommandsAsync(eq(""), any(ArrayList.class), same(values), eq(true), isNull(), same(state), eq("reward/0"));
            verify(user).preformCommandAsync(any(), same(values), isNull(), same(state), eq("reward/0"));
        } finally { instance.set(null, old); stateLocal.remove(); keyLocal.remove(); }
    }
}
