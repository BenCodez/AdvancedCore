package com.bencodez.advancedcore.tests.lifecycle;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.AdvancedCoreConfigOptions;
import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.item.FullInventoryHandler;
import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL;
import com.bencodez.advancedcore.bukkit.runtime.BukkitRuntimePlatform;
import com.bencodez.advancedcore.core.platform.RuntimePlatform;
import com.bencodez.advancedcore.core.platform.RuntimePlatform.Cleanup;
import com.bencodez.advancedcore.core.runtime.AdvancedCoreRuntime;
import com.bencodez.advancedcore.lifecycle.AdvancedCoreLifecycle;

class CoreRuntimeTest {
    private RuntimePlatform platform() {
        RuntimePlatform platform = mock(RuntimePlatform.class);
        when(platform.beforeExecutorShutdown()).thenReturn(List.of());
        when(platform.afterExecutorGrace()).thenReturn(List.of());
        when(platform.afterExecutorShutdown()).thenReturn(List.of());
        return platform;
    }

    @Test void constructionDoesNotStartOrReplaceExistingServices() {
        RuntimePlatform platform = platform();
        clearInvocations(platform);
        new AdvancedCoreRuntime(platform);
        verifyNoInteractions(platform);
    }

    @Test void preservesExecutorShutdownGraceAndRewardOrdering() throws Exception {
        RuntimePlatform platform = platform();
        var events = new ArrayList<String>();
        ScheduledExecutorService login = executor("login", events);
        ScheduledExecutorService timer = executor("timer", events);
        ScheduledExecutorService time = executor("time", events);
        ScheduledExecutorService inventory = executor("inventory", events);
        when(platform.getLoginTimer()).thenReturn(login);
        when(platform.getTimer()).thenReturn(timer);
        when(platform.getTimeTimer()).thenReturn(time);
        when(platform.getInventoryTimer()).thenReturn(inventory);
        when(platform.beforeExecutorShutdown()).thenReturn(List.of(new Cleanup("pre", () -> events.add("pre"))));
        when(platform.afterExecutorGrace()).thenReturn(List.of(new Cleanup("rewards", () -> events.add("rewards"))));
        when(platform.afterExecutorShutdown()).thenReturn(List.of(new Cleanup("post", () -> events.add("post"))));
        doAnswer(call -> { events.add("wait-log"); return null; }).when(platform).info(anyString());
        new AdvancedCoreRuntime(platform).shutdown();
        assertEquals(List.of("pre", "login-stop", "timer-stop", "time-stop", "inventory-stop", "wait-log",
                "login-wait", "timer-wait", "time-wait", "inventory-wait", "rewards",
				"login-force", "timer-force", "time-force", "inventory-force",
				"login-wait", "timer-wait", "time-wait", "inventory-wait", "post"), events);
        verify(login).awaitTermination(2, TimeUnit.SECONDS);
        verify(timer).awaitTermination(2, TimeUnit.SECONDS);
        verify(time).awaitTermination(2, TimeUnit.SECONDS);
        verify(inventory, times(2)).awaitTermination(1, TimeUnit.SECONDS);
        verify(platform, times(1)).getTimeTimer();
    }

    private ScheduledExecutorService executor(String name, List<String> events) throws Exception {
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        doAnswer(call -> { events.add(name + "-stop"); return null; }).when(executor).shutdown();
        when(executor.awaitTermination(anyLong(), any())).thenAnswer(call -> { events.add(name + "-wait"); return false; });
        when(executor.shutdownNow()).thenAnswer(call -> { events.add(name + "-force"); return List.of(); });
        return executor;
    }

    @Test void cleanupFailureIsReportedWithoutSkippingLaterComponents() {
        RuntimePlatform platform = platform();
        var events = new ArrayList<String>();
        var failure = new IllegalStateException("fixture");
        when(platform.beforeExecutorShutdown()).thenReturn(List.of(
                new Cleanup("failed", () -> { throw failure; }),
                new Cleanup("next", () -> events.add("next"))));
        when(platform.afterExecutorShutdown()).thenReturn(List.of(new Cleanup("last", () -> events.add("last"))));
        new AdvancedCoreRuntime(platform).shutdown();
        assertEquals(List.of("next", "last"), events);
        verify(platform).cleanupFailed("failed", failure);
    }

    @Test void preservesInterruptAndSkipsAlreadyFinishedExecutors() throws Exception {
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        when(executor.awaitTermination(2, TimeUnit.SECONDS)).thenThrow(new InterruptedException("fixture"));
        try {
            AdvancedCoreRuntime.await(executor, 2, TimeUnit.SECONDS);
            assertTrue(Thread.currentThread().isInterrupted());
        } finally { Thread.interrupted(); }
        when(executor.isShutdown()).thenReturn(true);
        when(executor.isTerminated()).thenReturn(true);
        AdvancedCoreRuntime.shutdown(executor);
        AdvancedCoreRuntime.shutdownNow(executor);
        verify(executor, never()).shutdown();
        verify(executor, never()).shutdownNow();
        assertDoesNotThrow(() -> AdvancedCoreRuntime.shutdown(null));
        assertDoesNotThrow(() -> AdvancedCoreRuntime.shutdownNow(null));
        assertDoesNotThrow(() -> AdvancedCoreRuntime.await(null, 1, TimeUnit.SECONDS));
    }

    @Test void bukkitAdapterRetainsExecutorIdentityAndLegacyNullShutdown() {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        ScheduledExecutorService timer = mock(ScheduledExecutorService.class);
        when(plugin.getTimer()).thenReturn(timer);
        var platform = new BukkitRuntimePlatform(plugin);
        assertSame(timer, platform.getTimer());
        assertNull(platform.getTimeTimer());
        assertDoesNotThrow(() -> new AdvancedCoreLifecycle(null).shutdown());
    }

	@Test void bukkitAdapterFlushesFullInventoryBeforeExecutorShutdown() {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		FullInventoryHandler handler = mock(FullInventoryHandler.class);
		MySQL mysql = mock(MySQL.class);
		AdvancedCoreConfigOptions options = mock(AdvancedCoreConfigOptions.class);
		when(plugin.getFullInventoryHandler()).thenReturn(handler);
		when(plugin.isLoadUserData()).thenReturn(true);
		when(plugin.getOptions()).thenReturn(options);
		when(options.getStorageType()).thenReturn(UserStorage.MYSQL);
		when(plugin.getMysql()).thenReturn(mysql);
		BukkitRuntimePlatform platform = new BukkitRuntimePlatform(plugin);

		platform.beforeExecutorShutdown().stream()
				.filter(cleanup -> cleanup.name().equals("full inventory handler"))
				.findFirst().orElseThrow().action().run();

		verify(handler).shutdown();
		assertTrue(platform.beforeExecutorShutdown().stream()
				.noneMatch(cleanup -> cleanup.name().equals("MySQL")));
		platform.afterExecutorShutdown().stream()
				.filter(cleanup -> cleanup.name().equals("MySQL"))
				.findFirst().orElseThrow().action().run();
		verify(mysql).close();
		assertTrue(platform.afterExecutorShutdown().stream()
				.noneMatch(cleanup -> cleanup.name().equals("full inventory handler")));
	}

	@Test void bukkitAdapterDoesNotCloseMysqlWhileCheckpointTasksRemainActive() {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		MySQL mysql = mock(MySQL.class);
		AdvancedCoreConfigOptions options = mock(AdvancedCoreConfigOptions.class);
		ScheduledExecutorService timer = mock(ScheduledExecutorService.class);
		when(plugin.isLoadUserData()).thenReturn(true);
		when(plugin.getOptions()).thenReturn(options);
		when(options.getStorageType()).thenReturn(UserStorage.MYSQL);
		when(plugin.getMysql()).thenReturn(mysql);
		when(plugin.getLogger()).thenReturn(mock(java.util.logging.Logger.class));
		when(plugin.getTimer()).thenReturn(timer);
		when(timer.isTerminated()).thenReturn(false);
		BukkitRuntimePlatform platform = new BukkitRuntimePlatform(plugin);

		platform.afterExecutorShutdown().stream()
				.filter(cleanup -> cleanup.name().equals("MySQL"))
				.findFirst().orElseThrow().action().run();

		verify(mysql, never()).close();
	}
}
