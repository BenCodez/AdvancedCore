package com.bencodez.advancedcore.tests.user.presence;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.io.TempDir;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.FixedValue;
import net.bytebuddy.implementation.InvocationHandlerAdapter;
import net.bytebuddy.implementation.StubMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;
import org.junit.jupiter.api.Test;
import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.user.presence.PlayerPresence;
import com.bencodez.advancedcore.api.user.presence.PlayerPresenceService;
import com.bencodez.advancedcore.api.user.presence.BukkitPlayerPresenceLookup;
import com.bencodez.advancedcore.lifecycle.AdvancedCoreLifecycle;

class AdvancedCorePlayerPresenceTest {
    private static final UUID USER = UUID.fromString("d4519fc4-a782-4d11-8235-617209238441");
    @TempDir Path temporary;

    public static class StorageFixture {
        private final java.io.File directory;
        public StorageFixture(Path directory) { this.directory = directory.toFile(); }
        public java.io.File getPlayerDir() { return directory; }
    }

    public static class PlayerListFixture {
        public final StorageFixture playerIo;
        public PlayerListFixture(Path directory) { playerIo = new StorageFixture(directory); }
    }

    private Server levelServer(Path level) throws Exception {
        return levelServer(level, false);
    }

    private Server levelServer(Path level, boolean savingDisabled) throws Exception {
        var config = new org.bukkit.configuration.file.YamlConfiguration();
        config.set("players.disable-saving", savingDisabled);
        var spigot = mock(Server.Spigot.class); when(spigot.getConfig()).thenReturn(config);
        return (Server) new ByteBuddy().subclass(Object.class).implement(Server.class)
                .name("org.bukkit.craftbukkit.CraftServer")
                .method(net.bytebuddy.matcher.ElementMatchers.any()).intercept(StubMethod.INSTANCE)
                .defineMethod("getLevelDirectory", Path.class, java.lang.reflect.Modifier.PUBLIC)
                .intercept(FixedValue.value(level))
                .method(named("getOnlinePlayers")).intercept(FixedValue.value(java.util.List.of()))
                .method(named("spigot")).intercept(FixedValue.value(spigot))
                .make().load(getClass().getClassLoader(), ClassLoadingStrategy.Default.WRAPPER)
                .getLoaded().getDeclaredConstructor().newInstance();
    }

    private final java.util.List<AdvancedCorePlugin> plugins = new java.util.ArrayList<>();

    private AdvancedCorePlugin lifecyclePlugin() {
        var plugin = mock(AdvancedCorePlugin.class, CALLS_REAL_METHODS);
        try {
            Field lock = AdvancedCorePlugin.class.getDeclaredField("lifecycleLock");
            lock.setAccessible(true); lock.set(plugin, new Object());
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        doReturn(temporary.toFile()).when(plugin).getDataFolder();
        doReturn(mock(java.util.logging.Logger.class)).when(plugin).getLogger();
        plugins.add(plugin);
        return plugin;
    }

    @org.junit.jupiter.api.AfterEach void releaseFixtureOwnership() {
        for (var plugin : plugins) plugin.releaseServerDataOwnershipAfter(
                java.util.concurrent.CompletableFuture.completedFuture(null));
    }

    @Test void olderApiCapturesActualPlayerStorageWithoutWorldDirectoryAssumptions() throws Exception {
        Path history = Files.createDirectory(temporary.resolve("legacy-playerdata"));
        Server server = (Server) new ByteBuddy().subclass(Object.class).implement(Server.class)
                .name("org.bukkit.craftbukkit.v1_21_R1.CraftServer")
                .method(net.bytebuddy.matcher.ElementMatchers.any()).intercept(StubMethod.INSTANCE)
                .defineMethod("getHandle", PlayerListFixture.class, java.lang.reflect.Modifier.PUBLIC)
                .intercept(FixedValue.value(new PlayerListFixture(history)))
                .make().load(getClass().getClassLoader(), ClassLoadingStrategy.Default.WRAPPER)
                .getLoaded().getDeclaredConstructor().newInstance();
        assertEquals(history, BukkitPlayerPresenceLookup.capturePrimaryHistoryDirectory(server));
        assertEquals(PlayerPresence.NEVER_JOINED, new BukkitPlayerPresenceLookup(history).check(USER));
    }

    @Test void actualEnableRegistersJoinEvidenceAndFailedStartupRetiresIt() throws Exception {
        Path level = Files.createDirectory(temporary.resolve("runtime-level"));
        Path history = Files.createDirectory(level.resolve("playerdata"));
        Server server = levelServer(level);
        AdvancedCorePlugin plugin = lifecyclePlugin();
        doReturn(server).when(plugin).getServer();
        var worker = mock(java.util.concurrent.ScheduledExecutorService.class);
        var tasks = new ArrayDeque<Runnable>();
        doAnswer(call -> { tasks.add(call.getArgument(0)); return null; }).when(worker).execute(any(Runnable.class));
        var executors = mock(AdvancedCoreLifecycle.RuntimeExecutors.class);
        when(executors.getTimer()).thenReturn(worker);
        var registered = new java.util.concurrent.atomic.AtomicReference<BukkitPlayerPresenceLookup>();
        doAnswer(call -> {
            registered.set(call.getArgument(0));
            assertEquals(PlayerPresence.UNKNOWN, value(plugin.checkPlayerPresenceAsync(USER)));
            return null;
        }).when(plugin).registerEvents(any(BukkitPlayerPresenceLookup.class));
        doAnswer(call -> {
            var result = plugin.checkPlayerPresenceAsync(USER);
            assertFalse(result.toCompletableFuture().isDone());
            while (!tasks.isEmpty()) tasks.remove().run();
            assertEquals(PlayerPresence.NEVER_JOINED, value(result));
            throw new IllegalStateException("stop after presence initialization");
        }).when(plugin).onPreLoad();
        try (var runtime = mockStatic(AdvancedCoreLifecycle.class);
                var lifecycle = mockConstruction(AdvancedCoreLifecycle.class)) {
            runtime.when(() -> AdvancedCoreLifecycle.createRuntimeExecutors(plugin)).thenReturn(executors);
            assertThrows(IllegalStateException.class, plugin::onEnable);
            plugin.onDisable();
            assertEquals(1, lifecycle.constructed().size(), "failed startup cleanup and repeated disable run once");
        }
        assertNotNull(registered.get());
        assertEquals(PlayerPresence.UNKNOWN, value(plugin.checkPlayerPresenceAsync(USER)));
        assertEquals(PlayerPresence.UNKNOWN, registered.get().check(USER));
        verify(worker, times(2)).execute(any(Runnable.class)); verifyNoMoreInteractions(worker);
        try (var files = Files.list(history)) { assertEquals(0, files.count()); }
    }

    @Test void actualEnableWithPlayerSavingDisabledKeepsAbsentHistoryUnknownAndLiveJoinsPositive() throws Exception {
        Path level = Files.createDirectory(temporary.resolve("saving-disabled"));
        Path history = Files.createDirectory(level.resolve("playerdata"));
        Server server = levelServer(level, true);
        AdvancedCorePlugin plugin = lifecyclePlugin();
        doReturn(server).when(plugin).getServer();
        var worker = mock(java.util.concurrent.ScheduledExecutorService.class); var tasks = new ArrayDeque<Runnable>();
        doAnswer(call -> { tasks.add(call.getArgument(0)); return null; }).when(worker).execute(any(Runnable.class));
        var executors = mock(AdvancedCoreLifecycle.RuntimeExecutors.class); when(executors.getTimer()).thenReturn(worker);
        var registered = new java.util.concurrent.atomic.AtomicReference<BukkitPlayerPresenceLookup>();
        doAnswer(call -> { registered.set(call.getArgument(0)); return null; })
                .when(plugin).registerEvents(any(BukkitPlayerPresenceLookup.class));
        doAnswer(call -> {
            var result = plugin.checkPlayerPresenceAsync(USER); while (!tasks.isEmpty()) tasks.remove().run();
            assertEquals(PlayerPresence.UNKNOWN, value(result), "No saved record cannot prove absence when saving is disabled");
            var player = mock(org.bukkit.entity.Player.class); when(player.getUniqueId()).thenReturn(USER);
            registered.get().onJoin(new org.bukkit.event.player.PlayerJoinEvent(player, null));
            result = plugin.checkPlayerPresenceAsync(USER); while (!tasks.isEmpty()) tasks.remove().run();
            assertEquals(PlayerPresence.JOINED, value(result));
            throw new IllegalStateException("stop after presence initialization");
        }).when(plugin).onPreLoad();
        try (var runtime = mockStatic(AdvancedCoreLifecycle.class);
                var lifecycle = mockConstruction(AdvancedCoreLifecycle.class)) {
            runtime.when(() -> AdvancedCoreLifecycle.createRuntimeExecutors(plugin)).thenReturn(executors);
            assertThrows(IllegalStateException.class, plugin::onEnable);
        }
        assertEquals(PlayerPresence.UNKNOWN, value(plugin.checkPlayerPresenceAsync(USER)));
        try (var files = Files.list(history)) { assertEquals(0, files.count()); }
    }

    private static void attach(AdvancedCorePlugin plugin, PlayerPresenceService service) throws Exception {
        Field field = AdvancedCorePlugin.class.getDeclaredField("playerPresenceService");
        field.setAccessible(true); field.set(plugin, service);
    }

    private static PlayerPresence value(CompletionStage<PlayerPresence> result) {
        return result.toCompletableFuture().join();
    }

    @Test void partialStartupHasUnknownHistoryInsteadOfCreatingAnIdentity() {
        AdvancedCorePlugin plugin = lifecyclePlugin();
        assertEquals(PlayerPresence.UNKNOWN, value(plugin.checkPlayerPresenceAsync(USER)));
        assertEquals(PlayerPresence.UNKNOWN, value(plugin.checkPlayerPresenceAsync(null)));
        verify(plugin, never()).getServer();
        verify(plugin, never()).getUserManager();
    }

    @Test void publicApiKeepsUuidLookupAndHistoryOffRequestingThread() throws Exception {
        var worker = new ArrayDeque<Runnable>();
        AdvancedCorePlugin plugin = lifecyclePlugin();
        try (var service = new PlayerPresenceService(worker::add, id -> {
            assertEquals(USER, id); return PlayerPresence.JOINED;
        })) {
            attach(plugin, service);
            var result = plugin.checkPlayerPresenceAsync(USER);
            assertFalse(result.toCompletableFuture().isDone());
            worker.remove().run();
            assertEquals(PlayerPresence.JOINED, value(result));
            verify(plugin, never()).getServer();
            verify(plugin, never()).getUserManager();
        }
    }

    @Test void actualDisableRetiresRequestsBeforeExistingLifecycleRuns() throws Exception {
        var worker = new ArrayDeque<Runnable>();
        AdvancedCorePlugin plugin = lifecyclePlugin();
        var reads = new java.util.concurrent.atomic.AtomicInteger();
        var service = new PlayerPresenceService(worker::add, id -> {
            reads.incrementAndGet(); return PlayerPresence.JOINED;
        });
        attach(plugin, service);
        var pending = plugin.checkPlayerPresenceAsync(USER);
        try (var lifecycle = mockConstruction(AdvancedCoreLifecycle.class, (instance, context) ->
                doAnswer(call -> {
                    assertEquals(PlayerPresence.UNKNOWN, value(pending));
                    assertEquals(PlayerPresence.UNKNOWN, value(plugin.checkPlayerPresenceAsync(USER)));
                    return null;
                }).when(instance).shutdown(anyLong()))) {
            plugin.onDisable();
            assertEquals(1, lifecycle.constructed().size());
            verify(lifecycle.constructed().getFirst()).shutdown(anyLong());
        }
        worker.remove().run();
        assertEquals(0, reads.get());
        assertEquals(PlayerPresence.UNKNOWN, value(pending));
    }

    @Test void presenceCleanupFailureStillRunsCoreCleanupOnce() throws Exception {
        var plugin = lifecyclePlugin();
        var service = mock(PlayerPresenceService.class);
        attach(plugin, service);
        doThrow(new IllegalStateException("synthetic cleanup failure")).when(service).close();
        try (var lifecycle = mockConstruction(AdvancedCoreLifecycle.class)) {
            assertThrows(IllegalStateException.class, plugin::onDisable);
            plugin.onDisable();
            assertEquals(1, lifecycle.constructed().size());
            verify(lifecycle.constructed().getFirst()).shutdown(anyLong());
        }
    }

    @Test void completeCapturedScopeAndPublicApiReadHistoryOnActualWorker() throws Exception {
        Path levelDirectory = Files.createDirectory(temporary.resolve("server-level"));
        Path worldDirectory = Files.createDirectory(temporary.resolve("dimension"));
        Path historyDirectory = Files.createDirectory(levelDirectory.resolve("playerdata"));
        World world = mock(World.class);
        when(world.getEnvironment()).thenReturn(World.Environment.NORMAL);
        when(world.getWorldFolder()).thenReturn(worldDirectory.toFile());
        // A complete synthetic CraftServer-shaped scope, isolated in a child loader.
        Server server = (Server) new ByteBuddy().subclass(Object.class).implement(Server.class)
                .name("org.bukkit.craftbukkit.CraftServer").method(net.bytebuddy.matcher.ElementMatchers.any())
                .intercept(StubMethod.INSTANCE)
                .defineMethod("getLevelDirectory", Path.class, java.lang.reflect.Modifier.PUBLIC)
                .intercept(FixedValue.value(levelDirectory))
                .method(named("getWorlds")).intercept(FixedValue.value(java.util.List.of(world)))
                .method(named("getOfflinePlayer").and(takesArguments(UUID.class)))
                .intercept(InvocationHandlerAdapter.of((proxy, method, arguments) -> {
                    fail("Checked reads must never invoke the server history loader"); return null;
                })).make().load(getClass().getClassLoader(), ClassLoadingStrategy.Default.WRAPPER)
                .getLoaded().getDeclaredConstructor().newInstance();
        Path captured = BukkitPlayerPresenceLookup.capturePrimaryHistoryDirectory(server);
        assertEquals(historyDirectory, captured);
        long requester = Thread.currentThread().threadId();
        AtomicLong historyThread = new AtomicLong(-1);
        var actualLookup = new BukkitPlayerPresenceLookup(captured);
        AdvancedCorePlugin plugin = lifecyclePlugin();
        try (var worker = Executors.newSingleThreadExecutor();
                var service = new PlayerPresenceService(worker, id -> {
                    historyThread.set(Thread.currentThread().threadId()); return actualLookup.check(id);
                })) {
            attach(plugin, service);
            assertEquals(PlayerPresence.NEVER_JOINED,
                    plugin.checkPlayerPresenceAsync(USER).toCompletableFuture().get(5, TimeUnit.SECONDS));
            assertNotEquals(requester, historyThread.get());
            verifyNoInteractions(world);
            verify(plugin, never()).getUserManager();
            try (var contents = Files.list(historyDirectory)) { assertEquals(0, contents.count()); }
        }
    }
}
