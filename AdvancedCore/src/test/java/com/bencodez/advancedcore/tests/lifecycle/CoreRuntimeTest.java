package com.bencodez.advancedcore.tests.lifecycle;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.AdvancedCoreConfigOptions;
import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.item.FullInventoryHandler;
import com.bencodez.advancedcore.api.time.TimeChecker;
import com.bencodez.advancedcore.api.user.UserManager;
import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.advancedcore.api.user.usercache.UserDataManager;
import com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL;
import com.bencodez.advancedcore.api.user.userstorage.sql.UserTable;
import com.bencodez.advancedcore.bukkit.runtime.BukkitRuntimePlatform;
import com.bencodez.advancedcore.core.platform.RuntimePlatform;
import com.bencodez.advancedcore.core.platform.RuntimePlatform.Cleanup;
import com.bencodez.advancedcore.core.runtime.AdvancedCoreRuntime;
import com.bencodez.advancedcore.lifecycle.AdvancedCoreLifecycle;
import com.bencodez.simpleapi.sql.sqlite.db.SQLite;

class CoreRuntimeTest {
	@Test void bukkitRuntimeSelectsManagerStorageWorkerRatherThanPluginTimer() {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		UserManager users = mock(UserManager.class);
		UserDataManager manager = mock(UserDataManager.class);
		ScheduledExecutorService pluginTimer = mock(ScheduledExecutorService.class);
		ScheduledExecutorService storageTimer = mock(ScheduledExecutorService.class);
		when(plugin.getTimer()).thenReturn(pluginTimer);
		when(plugin.getLoadedUserManager()).thenReturn(users);
		when(users.getDataManager()).thenReturn(manager);
		when(manager.getTimer()).thenReturn(storageTimer);
		BukkitRuntimePlatform platform = new BukkitRuntimePlatform(plugin);
		assertSame(pluginTimer, platform.getTimer());
		assertSame(storageTimer, platform.getUserStorageTimer());
	}

	@Test void deferredShutdownWaitsForActualStorageWorkerBeforeClosingOwner() throws Exception {
		RuntimePlatform platform = platform();
		var pluginTimer = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
		var storageTimer = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
		CountDownLatch started = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		CountDownLatch terminal = new CountDownLatch(1);
		CompletableFuture<Void> retirement = new CompletableFuture<>();
		storageTimer.execute(() -> {
			started.countDown();
			while (release.getCount() > 0) {
				try { release.await(1, TimeUnit.SECONDS); }
				catch (InterruptedException ignored) { /* Simulate JDBC ignoring interruption. */ }
			}
		});
		assertTrue(started.await(2, TimeUnit.SECONDS));
		storageTimer.execute(() -> retirement.complete(null));
		when(platform.getTimer()).thenReturn(pluginTimer);
		when(platform.getUserStorageTimer()).thenReturn(storageTimer);
		when(platform.beforeExecutorShutdownCompletion()).thenReturn(retirement);
		when(platform.canBlockForPreExecutorShutdown()).thenReturn(false);
		when(platform.deferredShutdownTimeoutMillis()).thenReturn(20L);
		when(platform.afterStorageExecutorShutdown()).thenReturn(List.of(
				new Cleanup("native owner", terminal::countDown)));
		try {
			new AdvancedCoreRuntime(platform).shutdown();
			assertTrue(pluginTimer.isTerminated());
			long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
			while (!storageTimer.isShutdown() && System.nanoTime() < deadline) Thread.yield();
			assertTrue(storageTimer.isShutdown(), "watchdog must stop the manager worker");
			assertEquals(1, terminal.getCount(), "native owner must remain open during a running JDBC call");
		} finally {
			release.countDown();
			pluginTimer.shutdownNow();
			storageTimer.shutdownNow();
		}
		assertTrue(terminal.await(3, TimeUnit.SECONDS));
	}

    private RuntimePlatform platform() {
        RuntimePlatform platform = mock(RuntimePlatform.class);
        when(platform.beforeExecutorShutdown()).thenReturn(List.of());
        when(platform.afterExecutorGrace()).thenReturn(List.of());
		when(platform.afterStorageExecutorShutdown()).thenReturn(List.of());
        when(platform.afterExecutorShutdown()).thenReturn(List.of());
        when(platform.canBlockForPreExecutorShutdown()).thenReturn(true);
		when(platform.deferredShutdownTimeoutMillis()).thenReturn(5_000L);
        return platform;
    }

    @Test void constructionDoesNotStartOrReplaceExistingServices() {
        RuntimePlatform platform = platform();
        clearInvocations(platform);
        new AdvancedCoreRuntime(platform);
        verifyNoInteractions(platform);
    }

	@Test void sharedStorageExecutorIsDaemonWhenAnInterruptIgnoringTaskOutlivesShutdown() throws Exception {
		AdvancedCoreRuntime.ExecutorGroup group = AdvancedCoreRuntime.createExecutors();
		CountDownLatch started = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		AtomicReference<Thread> worker = new AtomicReference<>();
		try {
			group.timer().execute(() -> {
				worker.set(Thread.currentThread());
				started.countDown();
				while (true) {
					try {
						if (release.await(5, TimeUnit.SECONDS)) return;
					} catch (InterruptedException ignored) {
						// JDBC drivers may ignore an interrupt while a query is in progress.
					}
				}
			});
			assertTrue(started.await(2, TimeUnit.SECONDS));
			group.timer().shutdownNow();
			assertTrue(worker.get().isAlive());
			assertTrue(worker.get().isDaemon(), "an uninterruptible storage operation must not hold the JVM open");
		} finally {
			release.countDown();
			group.timer().shutdownNow();
			group.loginTimer().shutdownNow();
			group.inventoryTimer().shutdownNow();
			assertTrue(group.timer().awaitTermination(2, TimeUnit.SECONDS));
		}
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

	@Test void waitsForAsyncPreShutdownWorkBeforeRetiringExecutors() throws Exception {
		RuntimePlatform platform = platform();
		List<String> events = new java.util.concurrent.CopyOnWriteArrayList<>();
		ScheduledExecutorService timer = executor("timer", events);
		CompletableFuture<Void> retiring = new CompletableFuture<>();
		when(platform.beforeExecutorShutdown()).thenReturn(List.of(new Cleanup("pre", () -> events.add("pre"))));
		when(platform.beforeExecutorShutdownCompletion()).thenReturn(retiring);
		when(platform.getTimer()).thenReturn(timer);
		var worker = java.util.concurrent.Executors.newSingleThreadExecutor();
		try {
			var shutdown = worker.submit(() -> new AdvancedCoreRuntime(platform).shutdown());
			long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
			while (!events.contains("pre") && System.nanoTime() < deadline) Thread.yield();
			assertEquals(List.of("pre"), events, "executor shutdown must wait for storage retirement");
			retiring.complete(null);
			shutdown.get(5, TimeUnit.SECONDS);
			assertTrue(events.indexOf("timer-stop") > events.indexOf("pre"));
		} finally {
			retiring.complete(null);
			worker.shutdownNow();
			assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
		}
	}

	@Test void nonBlockingPlatformLeavesStorageWorkerAliveUntilRetirementCompletes() throws Exception {
		RuntimePlatform platform = platform();
		ScheduledExecutorService timer = mock(ScheduledExecutorService.class);
		CompletableFuture<Void> retiring = new CompletableFuture<>();
		List<String> events = new java.util.concurrent.CopyOnWriteArrayList<>();
		when(platform.beforeExecutorShutdownCompletion()).thenReturn(retiring);
		when(platform.canBlockForPreExecutorShutdown()).thenReturn(false);
		when(platform.getTimer()).thenReturn(timer);
		java.util.concurrent.atomic.AtomicReference<Thread> cleanupThread = new java.util.concurrent.atomic.AtomicReference<>();
		when(platform.afterExecutorGrace()).thenReturn(List.of(
				new Cleanup("reward", () -> events.add("reward"))));
		when(platform.afterExecutorShutdown()).thenReturn(List.of(
				new Cleanup("unload", () -> { cleanupThread.set(Thread.currentThread()); events.add("unload"); })));

		Thread lifecycleThread = Thread.currentThread();
		assertDoesNotThrow(() -> new AdvancedCoreRuntime(platform).shutdown());
		verify(timer, never()).shutdown();
		verify(timer, never()).shutdownNow();
		verify(timer, never()).awaitTermination(anyLong(), any());
		assertEquals(List.of("reward", "unload"), events);
		assertSame(lifecycleThread, cleanupThread.get(),
				"Bukkit-facing cleanup must finish on the lifecycle thread before disable returns");
		retiring.complete(null);
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
		while (mockingDetails(timer).getInvocations().stream()
				.noneMatch(invocation -> invocation.getMethod().getName().equals("shutdown"))
				&& System.nanoTime() < deadline) Thread.yield();
		verify(timer).shutdown();
		assertEquals(List.of("reward", "unload"), events, "deferred completion must not repeat cleanup");
	}

	@Test void admittedTimeTransitionIsCancelledBeforeLifecycleThreadTeardown() throws Exception {
		RuntimePlatform platform = platform();
		CompletableFuture<Void> retiring = new CompletableFuture<>();
		List<String> events = new java.util.concurrent.CopyOnWriteArrayList<>();
		when(platform.beforeExecutorShutdownCompletion()).thenReturn(retiring);
		when(platform.canBlockForPreExecutorShutdown()).thenReturn(false);
		when(platform.holdTimeTimerUntilPreExecutorShutdownCompletion()).thenReturn(true);
		doAnswer(call -> { events.add("cancel"); return null; }).when(platform).beforeDeferredPlatformCleanup();
		when(platform.afterExecutorGrace()).thenReturn(List.of(
				new Cleanup("reward", () -> events.add("reward"))));
		AtomicReference<Thread> unloadThread = new AtomicReference<>();
		when(platform.afterExecutorShutdown()).thenReturn(List.of(
				new Cleanup("unload", () -> { unloadThread.set(Thread.currentThread()); events.add("unload"); })));

		Thread lifecycleThread = Thread.currentThread();
		new AdvancedCoreRuntime(platform).shutdown();

		assertEquals(List.of("cancel", "reward", "unload"), events);
		assertSame(lifecycleThread, unloadThread.get());
		retiring.complete(null);
		assertEquals(List.of("cancel", "reward", "unload"), events,
				"deferred completion must not repeat lifecycle cleanup");
	}

	@Test void deferredRetirementTimeoutForcesStorageWorkerWithoutRepeatingPlatformCleanup() {
		RuntimePlatform platform = platform();
		ScheduledExecutorService timer = mock(ScheduledExecutorService.class);
		CompletableFuture<Void> retiring = new CompletableFuture<>();
		List<String> events = new java.util.concurrent.CopyOnWriteArrayList<>();
		when(platform.beforeExecutorShutdownCompletion()).thenReturn(retiring);
		when(platform.canBlockForPreExecutorShutdown()).thenReturn(false);
		when(platform.deferredShutdownTimeoutMillis()).thenReturn(20L);
		when(platform.getTimer()).thenReturn(timer);
		when(platform.afterExecutorShutdown()).thenReturn(List.of(
				new Cleanup("unload", () -> events.add("unload"))));

		new AdvancedCoreRuntime(platform).shutdown();
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
		while (mockingDetails(timer).getInvocations().stream()
				.noneMatch(invocation -> invocation.getMethod().getName().equals("shutdownNow"))
				&& System.nanoTime() < deadline) Thread.yield();

		verify(timer).shutdownNow();
		verify(platform).cleanupFailed(eq("pre-executor shutdown"), any(java.util.concurrent.TimeoutException.class));
		assertEquals(List.of("unload"), events);
		retiring.complete(null);
		assertEquals(List.of("unload"), events);
	}

	@Test void watchdogLetsQueuedRetirementFlushBeforeForcingStorageWorker() throws Exception {
		RuntimePlatform platform = platform();
		var timer = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
		CountDownLatch occupied = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		CompletableFuture<Void> retirement = new CompletableFuture<>();
		timer.execute(() -> {
			occupied.countDown();
			try { release.await(); }
			catch (InterruptedException interruption) { Thread.currentThread().interrupt(); }
		});
		assertTrue(occupied.await(2, TimeUnit.SECONDS));
		timer.execute(() -> retirement.complete(null));
		when(platform.beforeExecutorShutdownCompletion()).thenReturn(retirement);
		when(platform.canBlockForPreExecutorShutdown()).thenReturn(false);
		when(platform.deferredShutdownTimeoutMillis()).thenReturn(20L);
		when(platform.getTimer()).thenReturn(timer);
		try {
			new AdvancedCoreRuntime(platform).shutdown();
			long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
			while (!timer.isShutdown() && System.nanoTime() < deadline) Thread.yield();
			assertTrue(timer.isShutdown());
			release.countDown();

			retirement.get(2, TimeUnit.SECONDS);
			assertTrue(timer.awaitTermination(2, TimeUnit.SECONDS));
			verify(platform).cleanupFailed(eq("pre-executor shutdown"),
					any(java.util.concurrent.TimeoutException.class));
		} finally {
			release.countDown();
			timer.shutdownNow();
		}
	}

	@Test void watchdogRunsTerminalCleanupWhenQueuedRetirementIsCancelled() throws Exception {
		RuntimePlatform platform = platform();
		var timer = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
		CountDownLatch occupied = new CountDownLatch(1);
		CountDownLatch terminal = new CountDownLatch(1);
		CompletableFuture<Void> retirement = new CompletableFuture<>();
		timer.execute(() -> {
			occupied.countDown();
			try { new CountDownLatch(1).await(); }
			catch (InterruptedException expected) { Thread.currentThread().interrupt(); }
		});
		assertTrue(occupied.await(2, TimeUnit.SECONDS));
		timer.execute(() -> retirement.complete(null));
		when(platform.beforeExecutorShutdownCompletion()).thenReturn(retirement);
		when(platform.canBlockForPreExecutorShutdown()).thenReturn(false);
		when(platform.deferredShutdownTimeoutMillis()).thenReturn(20L);
		when(platform.getTimer()).thenReturn(timer);
		when(platform.afterStorageExecutorShutdown()).thenReturn(List.of(
				new Cleanup("terminal storage", terminal::countDown)));
		try {
			new AdvancedCoreRuntime(platform).shutdown();
			assertTrue(terminal.await(3, TimeUnit.SECONDS),
					"cancelled retirement cannot complete its stage, but native cleanup must finish");
			assertFalse(retirement.isDone());
			verify(platform).cleanupFailed(eq("pre-executor shutdown"), any(java.util.concurrent.TimeoutException.class));
			assertTrue(timer.isTerminated());
		} finally { timer.shutdownNow(); }
	}

	@Test void deferredRetirementFailureTerminatesItsWorkerAfterReportingTheFailure() {
		RuntimePlatform platform = platform();
		ScheduledExecutorService timer = mock(ScheduledExecutorService.class);
		java.util.concurrent.atomic.AtomicBoolean terminated = new java.util.concurrent.atomic.AtomicBoolean();
		when(timer.isTerminated()).thenAnswer(ignored -> terminated.get());
		when(timer.shutdownNow()).thenAnswer(ignored -> {
			terminated.set(true);
			return List.of();
		});
		CompletableFuture<Void> retiring = new CompletableFuture<>();
		when(platform.beforeExecutorShutdownCompletion()).thenReturn(retiring);
		when(platform.canBlockForPreExecutorShutdown()).thenReturn(false);
		when(platform.getTimer()).thenReturn(timer);
		java.util.concurrent.atomic.AtomicBoolean terminalCleanup = new java.util.concurrent.atomic.AtomicBoolean();
		when(platform.afterStorageExecutorShutdown()).thenReturn(List.of(
				new Cleanup("terminal storage", () -> terminalCleanup.set(true))));

		new AdvancedCoreRuntime(platform).shutdown();
		verify(timer, never()).shutdown();
		verify(timer, never()).shutdownNow();
		IllegalStateException failure = new IllegalStateException("write failed");
		retiring.completeExceptionally(failure);
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
		while (mockingDetails(timer).getInvocations().stream()
				.noneMatch(invocation -> invocation.getMethod().getName().equals("shutdownNow"))
				&& System.nanoTime() < deadline) Thread.yield();
		while (!terminalCleanup.get() && System.nanoTime() < deadline) Thread.yield();
		verify(platform).cleanupFailed("pre-executor shutdown", failure);
		verify(timer).shutdownNow();
		assertTrue(terminalCleanup.get(), "native storage cleanup must follow forced worker retirement");
	}

	@Test void failedRetirementTerminatesStorageWorkerAfterReportingTheFailure() {
		RuntimePlatform platform = platform();
		ScheduledExecutorService timer = mock(ScheduledExecutorService.class);
		CompletableFuture<Void> retiring = new CompletableFuture<>();
		retiring.completeExceptionally(new IllegalStateException("write failed"));
		when(platform.beforeExecutorShutdownCompletion()).thenReturn(retiring);
		when(platform.getTimer()).thenReturn(timer);
		when(platform.afterStorageExecutorShutdown()).thenReturn(List.of(
				new Cleanup("terminal storage", () -> { })));

		new AdvancedCoreRuntime(platform).shutdown();
		verify(timer, never()).shutdown();
		verify(timer).shutdownNow();
		verify(platform).cleanupFailed(eq("pre-executor shutdown"), any(IllegalStateException.class));
		verify(platform).afterStorageExecutorShutdown();
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
		platform.beforeExecutorShutdown().stream()
				.filter(cleanup -> cleanup.name().equals("user storage"))
				.findFirst().orElseThrow().action().run();
		verify(mysql).close();
		assertTrue(platform.afterExecutorShutdown().stream()
				.noneMatch(cleanup -> cleanup.name().equals("full inventory handler")
						|| cleanup.name().equals("MySQL")));
	}

	@Test void bukkitAdapterDefersMysqlCloseUntilSharedStorageRetires() {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		MySQL mysql = mock(MySQL.class);
		UserManager users = mock(UserManager.class);
		UserDataManager dataManager = mock(UserDataManager.class);
		AdvancedCoreConfigOptions options = mock(AdvancedCoreConfigOptions.class);
		when(plugin.isLoadUserData()).thenReturn(true);
		when(plugin.getOptions()).thenReturn(options);
		when(options.getStorageType()).thenReturn(UserStorage.MYSQL);
		when(plugin.getMysql()).thenReturn(mysql);
		when(plugin.getLoadedUserManager()).thenReturn(users);
		when(users.getDataManager()).thenReturn(dataManager);
		Runnable[] afterRetirement = new Runnable[1];
		CompletableFuture<Void> retired = new CompletableFuture<>();
		when(dataManager.closeSharedRuntimeAsyncCompletion(any(Runnable.class))).thenAnswer(call -> {
			afterRetirement[0] = call.getArgument(0, Runnable.class);
			return retired;
		});
		BukkitRuntimePlatform platform = new BukkitRuntimePlatform(plugin);

		platform.beforeExecutorShutdown().stream()
				.filter(cleanup -> cleanup.name().equals("user storage"))
				.findFirst().orElseThrow().action().run();
		verify(mysql, never()).close();
		assertSame(retired, platform.beforeExecutorShutdownCompletion());
		assertNotNull(afterRetirement[0]);
		afterRetirement[0].run();
		retired.complete(null);
		verify(mysql).close();
	}

	@Test void bukkitAdapterDoesNotRetireStorageBeforeAnAdmittedTimeTransitionDrains() {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		TimeChecker checker = mock(TimeChecker.class);
		UserManager users = mock(UserManager.class);
		UserDataManager dataManager = mock(UserDataManager.class);
		CompletableFuture<Void> transitionDrain = new CompletableFuture<>();
		CompletableFuture<Void> storageRetirement = new CompletableFuture<>();
		when(plugin.getTimeChecker()).thenReturn(checker);
		when(checker.beginShutdown()).thenReturn(transitionDrain);
		when(plugin.isLoadUserData()).thenReturn(true);
		when(plugin.getLoadedUserManager()).thenReturn(users);
		when(users.getDataManager()).thenReturn(dataManager);
		when(dataManager.closeSharedRuntimeAsyncCompletion(any(Runnable.class))).thenReturn(storageRetirement);
		BukkitRuntimePlatform platform = new BukkitRuntimePlatform(plugin);

		platform.beforeExecutorShutdown().stream()
				.filter(cleanup -> cleanup.name().equals("time change admission"))
				.findFirst().orElseThrow().action().run();
		platform.beforeExecutorShutdown().stream()
				.filter(cleanup -> cleanup.name().equals("user storage"))
				.findFirst().orElseThrow().action().run();

		verify(dataManager, never()).closeSharedRuntimeAsyncCompletion(any(Runnable.class));
		assertTrue(platform.holdTimeTimerUntilPreExecutorShutdownCompletion());
		platform.beforeDeferredPlatformCleanup();
		verify(checker).cancelActiveTransitions();
		transitionDrain.complete(null);
		verify(dataManager).closeSharedRuntimeAsyncCompletion(any(Runnable.class));
		assertFalse(platform.beforeExecutorShutdownCompletion().toCompletableFuture().isDone());
		storageRetirement.complete(null);
		assertTrue(platform.beforeExecutorShutdownCompletion().toCompletableFuture().isDone());
	}

	@Test void bukkitAdapterStartsStorageRetirementWhenTimeTransitionWatchdogFires() {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		TimeChecker checker = mock(TimeChecker.class);
		UserManager users = mock(UserManager.class);
		UserDataManager dataManager = mock(UserDataManager.class);
		CompletableFuture<Void> transitionDrain = new CompletableFuture<>();
		CompletableFuture<Void> storageRetirement = new CompletableFuture<>();
		when(plugin.getTimeChecker()).thenReturn(checker);
		when(checker.beginShutdown()).thenReturn(transitionDrain);
		when(plugin.isLoadUserData()).thenReturn(true);
		when(plugin.getLoadedUserManager()).thenReturn(users);
		when(users.getDataManager()).thenReturn(dataManager);
		when(dataManager.closeSharedRuntimeAsyncCompletion(any(Runnable.class))).thenReturn(storageRetirement);
		BukkitRuntimePlatform platform = new BukkitRuntimePlatform(plugin);

		platform.beforeExecutorShutdown().stream()
				.filter(cleanup -> cleanup.name().equals("time change admission"))
				.findFirst().orElseThrow().action().run();
		platform.beforeExecutorShutdown().stream()
				.filter(cleanup -> cleanup.name().equals("user storage"))
				.findFirst().orElseThrow().action().run();
		verify(dataManager, never()).closeSharedRuntimeAsyncCompletion(any(Runnable.class));

		platform.beforeForcedTimeTimerShutdown();

		verify(checker).abortActiveTransitions();
		verify(dataManager).closeSharedRuntimeAsyncCompletion(any(Runnable.class));
		assertFalse(platform.beforeExecutorShutdownCompletion().toCompletableFuture().isDone());
	}

	@Test void bukkitAdapterClosesTheCapturedMysqlOwnerAfterConfigurationChanges() {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		MySQL mysql = mock(MySQL.class);
		UserManager users = mock(UserManager.class);
		UserDataManager dataManager = mock(UserDataManager.class);
		AdvancedCoreConfigOptions options = mock(AdvancedCoreConfigOptions.class);
		when(plugin.isLoadUserData()).thenReturn(true);
		when(plugin.getOptions()).thenReturn(options);
		when(options.getStorageType()).thenReturn(UserStorage.SQLITE);
		when(plugin.getMysql()).thenReturn(mysql);
		when(plugin.getLoadedUserManager()).thenReturn(users);
		when(users.getDataManager()).thenReturn(dataManager);
		when(dataManager.hasSharedSqlBackend()).thenReturn(true);
		when(dataManager.usesSharedSqlStorage(UserStorage.MYSQL)).thenReturn(true);
		when(dataManager.closeSharedRuntimeAsyncCompletion(any(Runnable.class))).thenAnswer(call -> {
			call.getArgument(0, Runnable.class).run();
			return CompletableFuture.completedFuture(null);
		});

		BukkitRuntimePlatform platform = new BukkitRuntimePlatform(plugin);
		platform.beforeExecutorShutdown().stream()
				.filter(cleanup -> cleanup.name().equals("user storage"))
				.findFirst().orElseThrow().action().run();

		verify(mysql).close();
	}

	@Test void bukkitAdapterClosesMysqlAndPendingOwnersAfterTerminalFlushFailure() {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		MySQL mysql = mock(MySQL.class);
		UserManager users = mock(UserManager.class);
		UserDataManager dataManager = mock(UserDataManager.class);
		when(plugin.isLoadUserData()).thenReturn(true);
		when(plugin.getLoadedUserManager()).thenReturn(users);
		when(plugin.getNativeUserStorageOwner()).thenReturn(
				new AdvancedCorePlugin.UserStorageOwner(UserStorage.MYSQL, mysql, null));
		when(users.getDataManager()).thenReturn(dataManager);
		CompletableFuture<Void> retirement = new CompletableFuture<>();
		when(dataManager.closeSharedRuntimeAsyncCompletion(any(Runnable.class))).thenReturn(retirement);
		BukkitRuntimePlatform platform = new BukkitRuntimePlatform(plugin);

		platform.beforeExecutorShutdown().stream()
				.filter(cleanup -> cleanup.name().equals("user storage"))
				.findFirst().orElseThrow().action().run();
		IllegalStateException flushFailure = new IllegalStateException("final flush failed");
		retirement.completeExceptionally(flushFailure);
		CompletionException reported = assertThrows(CompletionException.class,
				() -> platform.beforeExecutorShutdownCompletion().toCompletableFuture().join());
		assertSame(flushFailure, reported.getCause());
		verify(mysql, never()).close();

		platform.afterStorageExecutorShutdown().get(0).action().run();
		verify(mysql).close();
		verify(plugin).closePendingNativeUserStorageOwners();
	}

	@Test void bukkitAdapterClosesOwnerAfterWatchdogCancelsQueuedRetirement() {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		MySQL mysql = mock(MySQL.class);
		UserManager users = mock(UserManager.class);
		UserDataManager dataManager = mock(UserDataManager.class);
		when(plugin.isLoadUserData()).thenReturn(true);
		when(plugin.getLoadedUserManager()).thenReturn(users);
		when(plugin.getNativeUserStorageOwner()).thenReturn(
				new AdvancedCorePlugin.UserStorageOwner(UserStorage.MYSQL, mysql, null));
		when(users.getDataManager()).thenReturn(dataManager);
		CompletableFuture<Void> retirement = new CompletableFuture<>();
		AtomicReference<Runnable> lateCompletion = new AtomicReference<>();
		when(dataManager.closeSharedRuntimeAsyncCompletion(any(Runnable.class))).thenAnswer(call -> {
			lateCompletion.set(call.getArgument(0, Runnable.class));
			return retirement;
		});
		BukkitRuntimePlatform platform = new BukkitRuntimePlatform(plugin);
		platform.beforeExecutorShutdown().stream()
				.filter(cleanup -> cleanup.name().equals("user storage"))
				.findFirst().orElseThrow().action().run();

		platform.afterStorageExecutorShutdown().get(0).action().run();
		verify(mysql).close();
		verify(plugin).closePendingNativeUserStorageOwners();
		assertFalse(retirement.isDone());
		lateCompletion.get().run();
		verify(mysql, times(1)).close();
	}

	@Test void bukkitAdapterClosesSqliteOwnerAfterTerminalFlushFailure() {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		UserManager users = mock(UserManager.class);
		UserDataManager dataManager = mock(UserDataManager.class);
		UserTable table = mock(UserTable.class);
		SQLite sqlite = mock(SQLite.class);
		when(plugin.isLoadUserData()).thenReturn(true);
		when(plugin.getLoadedUserManager()).thenReturn(users);
		when(plugin.getNativeUserStorageOwner()).thenReturn(
				new AdvancedCorePlugin.UserStorageOwner(UserStorage.SQLITE, null, table));
		when(users.getDataManager()).thenReturn(dataManager);
		when(table.getSqLite()).thenReturn(sqlite);
		CompletableFuture<Void> retirement = new CompletableFuture<>();
		when(dataManager.closeSharedRuntimeAsyncCompletion(any(Runnable.class))).thenReturn(retirement);
		BukkitRuntimePlatform platform = new BukkitRuntimePlatform(plugin);

		platform.beforeExecutorShutdown().stream()
				.filter(cleanup -> cleanup.name().equals("user storage"))
				.findFirst().orElseThrow().action().run();
		retirement.completeExceptionally(new IllegalStateException("final flush failed"));
		platform.afterStorageExecutorShutdown().get(0).action().run();

		verify(sqlite).closeConnection();
		verify(plugin).closePendingNativeUserStorageOwners();
	}

	@Test void bukkitAdapterClosesMysqlOwnerInstalledWhileSharedReplacementCompletes() {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		MySQL oldMysql = mock(MySQL.class);
		MySQL replacementMysql = mock(MySQL.class);
		UserManager users = mock(UserManager.class);
		UserDataManager dataManager = mock(UserDataManager.class);
		AtomicReference<AdvancedCorePlugin.UserStorageOwner> owner = new AtomicReference<>(
				new AdvancedCorePlugin.UserStorageOwner(UserStorage.MYSQL, oldMysql, null));
		when(plugin.isLoadUserData()).thenReturn(true);
		when(plugin.getNativeUserStorageOwner()).thenAnswer(ignored -> owner.get());
		when(plugin.getLoadedUserManager()).thenReturn(users);
		when(users.getDataManager()).thenReturn(dataManager);
		CompletableFuture<Void> retirement = new CompletableFuture<>();
		Runnable[] afterRetirement = new Runnable[1];
		when(dataManager.closeSharedRuntimeAsyncCompletion(any(Runnable.class))).thenAnswer(call -> {
			afterRetirement[0] = call.getArgument(0, Runnable.class);
			return retirement;
		});

		BukkitRuntimePlatform platform = new BukkitRuntimePlatform(plugin);
		platform.beforeExecutorShutdown().stream()
				.filter(cleanup -> cleanup.name().equals("user storage"))
				.findFirst().orElseThrow().action().run();
		owner.set(new AdvancedCorePlugin.UserStorageOwner(UserStorage.MYSQL, replacementMysql, null));
		afterRetirement[0].run();
		retirement.complete(null);

		verify(replacementMysql).close();
		verify(oldMysql, never()).close();
	}
}
