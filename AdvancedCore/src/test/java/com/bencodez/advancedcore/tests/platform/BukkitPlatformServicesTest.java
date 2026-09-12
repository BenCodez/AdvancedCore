package com.bencodez.advancedcore.tests.platform;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.bukkit.platform.BukkitPlatformServices;
import com.bencodez.advancedcore.core.platform.PlatformPlayer;
import com.bencodez.simpleapi.scheduler.BukkitScheduler;

class BukkitPlatformServicesTest {
    @Test
    void constructionAndSchedulerAccessDoNotResolvePlugin() {
        BukkitPlatformServices services = new BukkitPlatformServices(() -> { throw new AssertionError("eager"); });
        assertSame(services.scheduler(), services.scheduler());
        assertThrows(NullPointerException.class, () -> new BukkitPlatformServices(null));
    }

    @Test
    void serverSchedulingUsesExistingOwnerAndSecondsWithoutNewExecutors() {
        AdvancedCorePlugin first = mock(AdvancedCorePlugin.class);
        AdvancedCorePlugin second = mock(AdvancedCorePlugin.class);
        BukkitScheduler firstScheduler = mock(BukkitScheduler.class);
        BukkitScheduler secondScheduler = mock(BukkitScheduler.class);
        when(first.getBukkitScheduler()).thenReturn(firstScheduler);
        when(second.getBukkitScheduler()).thenReturn(secondScheduler);
        AtomicReference<AdvancedCorePlugin> owner = new AtomicReference<>(first);
        BukkitPlatformServices services = new BukkitPlatformServices(owner::get);
        Runnable task = mock(Runnable.class);
        services.scheduler().runServer(task);
        verify(firstScheduler).runTask(first, task);
        owner.set(second);
        services.scheduler().runServerLater(task, 7);
        verify(secondScheduler).runTaskLater(second, task, 7L);
        verifyNoInteractions(task);
        assertThrows(IllegalArgumentException.class, () -> services.scheduler().runServerLater(task, -1));
        verifyNoMoreInteractions(firstScheduler, secondScheduler);
    }

    @Test
    void consoleDispatchPreservesNativeResultTextAndException() {
        BukkitPlatformServices services = new BukkitPlatformServices(() -> null);
        Server server = mock(Server.class);
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getServer).thenReturn(server);
            bukkit.when(Bukkit::getConsoleSender).thenReturn(console);
            when(server.dispatchCommand(console, "literal %player%")).thenReturn(true);
            assertTrue(services.dispatchConsoleCommand("literal %player%"));
            assertFalse(services.dispatchConsoleCommand("unknown"));
            services.sendConsoleMessage("&a[Javascript=literal]");
            verify(console).sendMessage("&a[Javascript=literal]");
            RuntimeException failure = new IllegalStateException("dispatch failed");
            when(server.dispatchCommand(console, "bad")).thenThrow(failure);
            assertSame(failure, assertThrows(IllegalStateException.class, () -> services.dispatchConsoleCommand("bad")));
        }
    }

    @Test
    void missingPlayerDoesNotCreateOfflineUserOrFallBackToGlobalTask() {
        UUID id = UUID.randomUUID();
        BukkitPlatformServices services = new BukkitPlatformServices(() -> { throw new AssertionError("owner lookup"); });
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            assertTrue(services.findOnlinePlayer(id).isEmpty());
            assertFalse(services.scheduler().runPlayer(id, p -> fail("offline callback")));
            bukkit.verify(() -> Bukkit.getPlayer(id), times(2));
            bukkit.verifyNoMoreInteractions();
        }
    }

    @Test
    void playerHandoffUsesEntityOverloadAndDoesNotRunEarly() {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(plugin.getBukkitScheduler()).thenReturn(scheduler);
        when(plugin.isEnabled()).thenReturn(true);
        Player nativePlayer = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(nativePlayer.isOnline()).thenReturn(true);
        when(nativePlayer.getName()).thenReturn("Ben");
        when(nativePlayer.hasPermission("allowed")).thenReturn(true);
        when(nativePlayer.performCommand("say hi")).thenReturn(true);
        BukkitPlatformServices services = new BukkitPlatformServices(() -> plugin);
        AtomicInteger calls = new AtomicInteger();
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(nativePlayer);
            assertTrue(services.scheduler().runPlayer(id, player -> {
                calls.incrementAndGet();
                assertEquals(id, player.getUniqueId());
                assertEquals("Ben", player.getName());
                assertTrue(player.isOnline());
                assertTrue(player.hasPermission("allowed"));
                assertFalse(player.hasPermission("denied"));
                player.sendMessage("&a%literal%");
                assertTrue(player.performCommand("say hi"));
                assertFalse(player.performCommand("unknown"));
            }));
            assertEquals(0, calls.get());
            ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
            verify(scheduler).runTask(eq(plugin), task.capture(), same(nativePlayer));
            verify(scheduler, never()).runTask(any(), any(Runnable.class));
            task.getValue().run();
            assertEquals(1, calls.get());
            verify(nativePlayer).sendMessage("&a%literal%");
            verify(nativePlayer, never()).setOp(anyBoolean());
        }
    }

    @Test
    void asyncPlayerHandoffDoesNotReadEntityStateOnCallerThread() throws Exception {
        Thread entityThread = Thread.currentThread();
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(plugin.getBukkitScheduler()).thenReturn(scheduler);
        when(plugin.isEnabled()).thenReturn(true);
        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.isOnline()).thenAnswer(invocation -> {
            assertSame(entityThread, Thread.currentThread(), "Player state read before entity handoff");
            return true;
        });
        BukkitPlatformServices services = new BukkitPlatformServices(() -> plugin);
        AtomicInteger calls = new AtomicInteger();
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            assertTrue(worker.submit(() -> {
                assertNotSame(entityThread, Thread.currentThread());
                // Static mocks are thread-scoped: install lookup on the submitting thread.
                try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                    bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player);
                    return services.scheduler().runPlayer(id, session -> {
                        assertSame(entityThread, Thread.currentThread());
                        assertEquals(id, session.getUniqueId());
                        calls.incrementAndGet();
                    });
                }
            }).get(5, TimeUnit.SECONDS));
            verifyNoInteractions(player);
            assertEquals(0, calls.get());
            ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
            verify(scheduler).runTask(eq(plugin), task.capture(), same(player));
            verify(scheduler, never()).runTask(any(), any(Runnable.class));
            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player);
                task.getValue().run();
            }
            verify(player).isOnline();
            assertEquals(1, calls.get());
        } finally {
            worker.shutdownNow();
            assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS), "Worker did not terminate");
        }
    }

    @Test
    void alreadyDisconnectedPlayerIsCheckedOnlyInsideEntityCallback() {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(plugin.getBukkitScheduler()).thenReturn(scheduler);
        when(plugin.isEnabled()).thenReturn(true);
        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(false);
        UUID id = UUID.randomUUID();
        BukkitPlatformServices services = new BukkitPlatformServices(() -> plugin);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            // Lookup can still find the entity while its login is being retired.
            bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player);
            assertTrue(services.scheduler().runPlayer(id, session -> fail("disconnected callback")));
            verifyNoInteractions(player);
            ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
            verify(scheduler).runTask(eq(plugin), task.capture(), same(player));
            task.getValue().run();
            verify(player).isOnline();
            verifyNoMoreInteractions(player);
            verify(scheduler, never()).runTask(any(), any(Runnable.class));
        }
    }

    @Test
    void disconnectReplacementAndDisableRetireQueuedPlayerWork() {
        for (String reason : new String[] {"disconnect", "replacement", "disable"}) {
            AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
            BukkitScheduler scheduler = mock(BukkitScheduler.class);
            when(plugin.getBukkitScheduler()).thenReturn(scheduler);
            when(plugin.isEnabled()).thenReturn(true);
            Player player = mock(Player.class);
            when(player.isOnline()).thenReturn(true);
            UUID id = UUID.randomUUID();
            BukkitPlatformServices services = new BukkitPlatformServices(() -> plugin);
            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player);
                assertTrue(services.scheduler().runPlayer(id, p -> fail(reason)));
                ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
                verify(scheduler).runTask(eq(plugin), task.capture(), same(player));
                if (reason.equals("disconnect")) when(player.isOnline()).thenReturn(false);
                if (reason.equals("replacement")) bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(mock(Player.class));
                if (reason.equals("disable")) when(plugin.isEnabled()).thenReturn(false);
                task.getValue().run();
                verify(scheduler, never()).runTask(any(), any(Runnable.class));
            }
        }
    }

    @Test
    void retainedHandleNeverRedirectsActionsToNewLogin() {
        UUID id = UUID.randomUUID();
        Player first = mock(Player.class);
        Player replacement = mock(Player.class);
        when(first.isOnline()).thenReturn(true);
        BukkitPlatformServices services = new BukkitPlatformServices(() -> null);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(first);
            PlatformPlayer player = services.findOnlinePlayer(id).orElseThrow();
            bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(replacement);
            assertFalse(player.isOnline());
            assertFalse(player.hasPermission("admin"));
            assertThrows(IllegalStateException.class, () -> player.sendMessage("old"));
            assertThrows(IllegalStateException.class, () -> player.performCommand("old"));
            verify(first, never()).hasPermission(anyString());
            verifyNoInteractions(replacement);
        }
    }

    @Test
    void schedulerAndCallbackFailuresAreNotSwallowed() {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(plugin.getBukkitScheduler()).thenReturn(scheduler);
        when(plugin.isEnabled()).thenReturn(true);
        BukkitPlatformServices services = new BukkitPlatformServices(() -> plugin);
        IllegalStateException failure = new IllegalStateException("stopped");
        doThrow(failure).when(scheduler).runTask(eq(plugin), any(Runnable.class));
        assertSame(failure, assertThrows(IllegalStateException.class, () -> services.scheduler().runServer(() -> { })));
        UUID id = UUID.randomUUID();
        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player);
            doThrow(failure).when(scheduler).runTask(eq(plugin), any(Runnable.class), any(Entity.class));
            assertSame(failure, assertThrows(IllegalStateException.class,
                    () -> services.scheduler().runPlayer(id, p -> { })));
            reset(scheduler);
            services.scheduler().runPlayer(id, p -> { throw failure; });
            ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
            verify(scheduler).runTask(eq(plugin), task.capture(), same(player));
            assertSame(failure, assertThrows(IllegalStateException.class, task.getValue()::run));
        }
    }
}
