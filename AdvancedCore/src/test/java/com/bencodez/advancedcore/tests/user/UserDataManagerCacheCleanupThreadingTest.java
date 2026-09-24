package com.bencodez.advancedcore.tests.user;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.user.usercache.UserDataCache;
import com.bencodez.advancedcore.api.user.usercache.UserDataManager;
import com.bencodez.advancedcore.core.user.storage.sql.SqlUserBackend;
import com.bencodez.simpleapi.scheduler.BukkitScheduler;
import com.bencodez.simpleapi.sql.data.DataValueInt;

class UserDataManagerCacheCleanupThreadingTest {
	@Test
	void noServerCaptureSkipsBukkitOnlinePlayerLookup() throws Exception {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class, RETURNS_DEEP_STUBS);
		UserDataManager manager = new UserDataManager(plugin);
		manager.getTimer().shutdownNow();
		java.util.concurrent.ScheduledExecutorService worker = mock(java.util.concurrent.ScheduledExecutorService.class);
		java.lang.reflect.Field timerField = UserDataManager.class.getDeclaredField("timer");
		timerField.setAccessible(true);
		timerField.set(manager, worker);
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(Bukkit::getServer).thenReturn(null);
			manager.clearNonNeededCachedUsers();
			bukkit.verify(Bukkit::getOnlinePlayers, never());
			verify(worker).execute(any(Runnable.class));
		}
	}

	@Test
	void failedSharedRetirementReopensMappedCache() throws Exception {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		when(plugin.isEnabled()).thenReturn(true);
		UserDataManager manager = new UserDataManager(plugin);
		manager.getTimer().shutdownNow();
		java.util.concurrent.ScheduledExecutorService worker = mock(java.util.concurrent.ScheduledExecutorService.class);
		java.lang.reflect.Field timerField = UserDataManager.class.getDeclaredField("timer");
		timerField.setAccessible(true);
		timerField.set(manager, worker);
		SqlUserBackend backend = mock(SqlUserBackend.class);
		manager.bindSharedSqlBackend(backend, (uuid, operation) -> operation.run());
		UUID uuid = UUID.randomUUID();
		UserDataCache cache = mock(UserDataCache.class);
		when(cache.getSharedSnapshotVersion()).thenReturn(1L);
		doThrow(new IllegalStateException("unflushed work")).when(cache).retireAfterSharedFlush();
		manager.getUserDataCache().put(uuid, cache);
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(Bukkit::getServer).thenReturn(null);
			ArgumentCaptor<Runnable> storage = ArgumentCaptor.forClass(Runnable.class);
			manager.clearNonNeededCachedUsers();
			verify(worker).execute(storage.capture());
			assertThrows(IllegalStateException.class, storage.getValue()::run);
			verify(cache).cancelRemoval();
			assertTrue(manager.containsKey(uuid));
		}
	}

	@Test
	void platformSchedulerRejectionDoesNotStopLaterCleanupAttempts() {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class, RETURNS_DEEP_STUBS);
		BukkitScheduler scheduler = mock(BukkitScheduler.class);
		when(plugin.getBukkitScheduler()).thenReturn(scheduler);
		when(plugin.isEnabled()).thenReturn(true);
		doThrow(new IllegalStateException("scheduler unavailable"))
				.doNothing().when(scheduler).runTask(eq(plugin), any(Runnable.class));
		UserDataManager manager = new UserDataManager(plugin);
		Server server = mock(Server.class);
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(Bukkit::getServer).thenReturn(server);
			assertDoesNotThrow(manager::clearNonNeededCachedUsers);
			assertDoesNotThrow(manager::clearNonNeededCachedUsers);
			verify(scheduler, times(2)).runTask(eq(plugin), any(Runnable.class));
			assertTrue(manager.getLastDeferredStorageFailure() instanceof IllegalStateException);
		} finally { manager.getTimer().shutdownNow(); }
	}

	@Test
	void capturesOnlinePlayersOnlyFromPlatformTask() {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class, RETURNS_DEEP_STUBS);
		BukkitScheduler scheduler = mock(BukkitScheduler.class);
		when(plugin.getBukkitScheduler()).thenReturn(scheduler);
		when(plugin.isEnabled()).thenReturn(true);
		when(plugin.getOptions().isOnlineMode()).thenReturn(true);
		UserDataManager manager = new UserDataManager(plugin);
		Server server = mock(Server.class);
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(Bukkit::getServer).thenReturn(server);
			bukkit.when(Bukkit::getOnlinePlayers).thenReturn(java.util.List.of());
			manager.clearNonNeededCachedUsers();
			bukkit.verify(Bukkit::getOnlinePlayers, never());
			ArgumentCaptor<Runnable> platform = ArgumentCaptor.forClass(Runnable.class);
			verify(scheduler).runTask(eq(plugin), platform.capture());
			platform.getValue().run();
			bukkit.verify(Bukkit::getOnlinePlayers);
		} finally { manager.getTimer().shutdownNow(); }
	}

	@Test
	void joinAfterPlatformSnapshotPreventsWorkerEviction() throws Exception {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class, RETURNS_DEEP_STUBS);
		BukkitScheduler scheduler = mock(BukkitScheduler.class);
		when(plugin.getBukkitScheduler()).thenReturn(scheduler);
		when(plugin.isEnabled()).thenReturn(true);
		when(plugin.getOptions().isOnlineMode()).thenReturn(true);
		UserDataManager manager = new UserDataManager(plugin);
		manager.getTimer().shutdownNow();
		java.util.concurrent.ScheduledExecutorService worker = mock(java.util.concurrent.ScheduledExecutorService.class);
		java.lang.reflect.Field timerField = UserDataManager.class.getDeclaredField("timer");
		timerField.setAccessible(true);
		timerField.set(manager, worker);
		UUID uuid = UUID.randomUUID();
		UserDataCache cache = new UserDataCache(manager, uuid);
		cache.updateCache(new java.util.HashMap<>(java.util.Map.of("Points", new DataValueInt(1))));
		manager.getUserDataCache().put(uuid, cache);
		Server server = mock(Server.class);
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(Bukkit::getServer).thenReturn(server);
			bukkit.when(Bukkit::getOnlinePlayers).thenReturn(java.util.List.of());
			ArgumentCaptor<Runnable> platform = ArgumentCaptor.forClass(Runnable.class);
			ArgumentCaptor<Runnable> storage = ArgumentCaptor.forClass(Runnable.class);
			manager.clearNonNeededCachedUsers();
			verify(scheduler).runTask(eq(plugin), platform.capture());
			platform.getValue().run();
			verify(worker).execute(storage.capture());
			org.bukkit.entity.Player player = mock(org.bukkit.entity.Player.class);
			when(player.getUniqueId()).thenReturn(uuid);
			manager.markUserOnline(player);
			storage.getValue().run();
			assertTrue(manager.containsKey(uuid));
		}
	}

	@Test
	void concurrentQuitCannotBeOverwrittenByPlatformSnapshot() throws Exception {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class, RETURNS_DEEP_STUBS);
		BukkitScheduler scheduler = mock(BukkitScheduler.class);
		when(plugin.getBukkitScheduler()).thenReturn(scheduler);
		when(plugin.isEnabled()).thenReturn(true);
		when(plugin.getOptions().isOnlineMode()).thenReturn(true);
		UserDataManager manager = new UserDataManager(plugin);
		manager.getTimer().shutdownNow();
		java.util.concurrent.ScheduledExecutorService worker = mock(java.util.concurrent.ScheduledExecutorService.class);
		java.lang.reflect.Field timerField = UserDataManager.class.getDeclaredField("timer");
		timerField.setAccessible(true);
		timerField.set(manager, worker);
		UUID uuid = UUID.randomUUID();
		UserDataCache cache = new UserDataCache(manager, uuid);
		cache.updateCache(new java.util.HashMap<>(java.util.Map.of("Points", new DataValueInt(1))));
		manager.getUserDataCache().put(uuid, cache);
		org.bukkit.entity.Player player = mock(org.bukkit.entity.Player.class);
		when(player.getUniqueId()).thenReturn(uuid);
		Server server = mock(Server.class);
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(Bukkit::getServer).thenReturn(server);
			bukkit.when(Bukkit::getOnlinePlayers).thenReturn(java.util.List.of(player));
			ArgumentCaptor<Runnable> platform = ArgumentCaptor.forClass(Runnable.class);
			ArgumentCaptor<Runnable> storage = ArgumentCaptor.forClass(Runnable.class);
			manager.markUserOnline(player);
			manager.clearNonNeededCachedUsers();
			verify(scheduler).runTask(eq(plugin), platform.capture());
			manager.markUserOffline(player);
			platform.getValue().run();
			verify(worker).execute(storage.capture());
			storage.getValue().run();
			assertTrue(!manager.containsKey(uuid));
		}
	}

	@Test
	void joinDuringBlockedFlushPreventsRetirementWithoutBlockingJoin() throws Exception {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class, RETURNS_DEEP_STUBS);
		when(plugin.isEnabled()).thenReturn(true);
		UserDataManager manager = new UserDataManager(plugin);
		UUID uuid = UUID.randomUUID();
		UserDataCache cache = mock(UserDataCache.class);
		when(cache.getSharedSnapshotVersion()).thenReturn(1L);
		CountDownLatch flushStarted = new CountDownLatch(1);
		CountDownLatch releaseFlush = new CountDownLatch(1);
		doAnswer(invocation -> {
			flushStarted.countDown();
			assertTrue(releaseFlush.await(5, TimeUnit.SECONDS));
			return null;
		}).when(cache).clearCache();
		manager.getUserDataCache().put(uuid, cache);
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(Bukkit::getServer).thenReturn(null);
			manager.clearNonNeededCachedUsers();
			assertTrue(flushStarted.await(5, TimeUnit.SECONDS));
			long started = System.nanoTime();
			manager.markUserOnline(uuid);
			assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 500);
			releaseFlush.countDown();
			manager.getTimer().shutdown();
			assertTrue(manager.getTimer().awaitTermination(5, TimeUnit.SECONDS));
			assertTrue(manager.containsKey(uuid));
			verify(cache).cancelRemoval();
			verify(cache, never()).retireAfterSharedFlush();
		} finally { manager.getTimer().shutdownNow(); }
	}
}
