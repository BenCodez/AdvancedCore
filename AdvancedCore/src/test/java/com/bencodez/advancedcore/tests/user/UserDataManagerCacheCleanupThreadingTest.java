package com.bencodez.advancedcore.tests.user;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.user.usercache.UserDataCache;
import com.bencodez.advancedcore.api.user.usercache.UserDataManager;
import com.bencodez.simpleapi.scheduler.BukkitScheduler;
import com.bencodez.simpleapi.sql.data.DataValueInt;

class UserDataManagerCacheCleanupThreadingTest {
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
			manager.markUserOnline(uuid);
			storage.getValue().run();
			assertTrue(manager.containsKey(uuid));
		}
	}
}
