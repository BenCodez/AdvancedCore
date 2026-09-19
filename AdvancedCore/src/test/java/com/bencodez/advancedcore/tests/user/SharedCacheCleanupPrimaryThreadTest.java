package com.bencodez.advancedcore.tests.user;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.profile.PlayerProfile;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.AdvancedCoreConfigOptions;
import com.bencodez.advancedcore.api.misc.PlayerManager;
import com.bencodez.advancedcore.api.player.UuidLookup;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.advancedcore.api.user.UserData;
import com.bencodez.advancedcore.api.user.UserManager;
import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.advancedcore.api.user.usercache.UserDataCache;
import com.bencodez.advancedcore.api.user.usercache.UserDataManager;
import com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL;
import com.bencodez.advancedcore.bukkit.user.runtime.BukkitUserCacheOwner;
import com.bencodez.advancedcore.core.user.storage.sql.SqlUserBackend;
import com.bencodez.simpleapi.sql.data.DataValueInt;

class SharedCacheCleanupPrimaryThreadTest {
	@Test
	void userStorageWorkerCannotRetainJvmDuringHungRetirement() throws Exception {
		UserDataManager manager = new UserDataManager(mock(AdvancedCorePlugin.class));
		try {
			Thread worker = manager.getTimer().submit(Thread::currentThread).get(2, TimeUnit.SECONDS);
			assertTrue(worker.isDaemon());
			assertTrue(worker.getName().startsWith("AdvancedCore-UserStorage-"));
		} finally {
			manager.getTimer().shutdownNow();
		}
	}

	@Test
	void primaryThreadIdentityLookupUsesOnlyKnownOnlineOfflineOrCachedIdentity() throws Exception {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		AdvancedCoreConfigOptions options = mock(AdvancedCoreConfigOptions.class);
		when(plugin.getOptions()).thenReturn(options);
		when(options.isOnlineMode()).thenReturn(true);
		try (var bukkit = mockStatic(Bukkit.class)) {
			Constructor<UuidLookup> constructor = UuidLookup.class.getDeclaredConstructor(AdvancedCorePlugin.class);
			constructor.setAccessible(true);
			UuidLookup lookup = constructor.newInstance(plugin);
			String unknown = "Unknown" + UUID.randomUUID().toString().replace("-", "");

			assertEquals("", lookup.getUUIDWithoutStorage(unknown));
			bukkit.verify(() -> Bukkit.getOfflinePlayer(unknown), never());
			verify(plugin, never()).getMysql();
			verify(plugin, never()).getSQLiteUserTable();

			UUID cached = UUID.randomUUID();
			lookup.cacheMapping(cached.toString(), "CachedName");
			assertEquals(cached.toString(), lookup.getUUIDWithoutStorage("cachedname"));
			bukkit.verify(() -> Bukkit.getOfflinePlayer("cachedname"), never());

			Player online = mock(Player.class);
			UUID onlineUuid = UUID.randomUUID();
			when(online.getUniqueId()).thenReturn(onlineUuid);
			when(online.getName()).thenReturn("OnlineName");
			bukkit.when(() -> Bukkit.getPlayerExact("OnlineName")).thenReturn(online);
			assertEquals(onlineUuid.toString(), lookup.getUUIDWithoutStorage("OnlineName"));
			bukkit.verify(() -> Bukkit.getOfflinePlayer("OnlineName"), never());

			when(options.isOnlineMode()).thenReturn(false);
			String offline = lookup.getUUIDWithoutStorage("OfflineName");
			assertEquals(UUID.nameUUIDFromBytes("OfflinePlayer:offlinename".getBytes(java.nio.charset.StandardCharsets.UTF_8))
					.toString(), offline);
			bukkit.verify(() -> Bukkit.getOfflinePlayer("OfflineName"), never());

			when(plugin.getStorageType()).thenReturn(UserStorage.MYSQL);
			MySQL mysql = mock(MySQL.class);
			when(plugin.getMysql()).thenReturn(mysql);
			UUID storedUuid = UUID.randomUUID();
			when(mysql.getUUIDOrThrow("StoredOnly")).thenReturn(storedUuid.toString());
			assertEquals(storedUuid.toString(), lookup.getUUIDFromStorage("StoredOnly"));
			bukkit.verify(() -> Bukkit.getPlayer(storedUuid), never());
			bukkit.verify(() -> Bukkit.getPlayerExact("StoredOnly"), never());
			bukkit.verify(() -> Bukkit.getOfflinePlayer("StoredOnly"), never());

			UUID storedNameUuid = UUID.randomUUID();
			AdvancedCoreUser storedUser = mock(AdvancedCoreUser.class);
			UserData storedData = mock(UserData.class);
			when(storedUser.getData()).thenReturn(storedData);
			when(storedData.getString(eq("PlayerName"), any())).thenReturn("PersistedName");
			assertEquals("PersistedName",
					lookup.getPlayerNameFromStorage(storedUser, storedNameUuid.toString(), true));
			bukkit.verify(() -> Bukkit.getPlayer(storedNameUuid), never());
		}
	}

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	void persistedUuidLookupUsesLifecycleAdmittedNativeOwner() throws Exception {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		AdvancedCoreConfigOptions options = mock(AdvancedCoreConfigOptions.class);
		when(plugin.getOptions()).thenReturn(options);
		when(options.isOnlineMode()).thenReturn(true);
		UserManager users = mock(UserManager.class);
		UserDataManager manager = mock(UserDataManager.class);
		when(plugin.getUserManager()).thenReturn(users);
		when(users.getDataManager()).thenReturn(manager);
		MySQL activeMysql = mock(MySQL.class);
		var staleTable = mock(com.bencodez.advancedcore.api.user.userstorage.sql.UserTable.class);
		when(plugin.getStorageType()).thenReturn(UserStorage.SQLITE);
		when(plugin.getSQLiteUserTable()).thenReturn(staleTable);
		UUID uuid = UUID.randomUUID();
		when(activeMysql.getUUIDOrThrow("PinnedUser")).thenReturn(uuid.toString());
		when(manager.withSharedNativeUserStorage(any())).thenAnswer(invocation ->
				invocation.getArgument(0, Function.class).apply(
						new AdvancedCorePlugin.UserStorageOwner(UserStorage.MYSQL, activeMysql, null)));
		Constructor<UuidLookup> constructor = UuidLookup.class.getDeclaredConstructor(AdvancedCorePlugin.class);
		constructor.setAccessible(true);
		UuidLookup lookup = constructor.newInstance(plugin);

		assertEquals(uuid.toString(), lookup.getUUIDFromStorage("PinnedUser"));
		verify(activeMysql).getUUIDOrThrow("PinnedUser");
		verify(staleTable, never()).getUUIDOrThrow(any(String.class));
	}

	@Test
	void persistedUuidLookupPropagatesNativeQueryFailure() throws Exception {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		UserManager users = mock(UserManager.class);
		UserDataManager manager = mock(UserDataManager.class);
		when(plugin.getUserManager()).thenReturn(users);
		when(users.getDataManager()).thenReturn(manager);
		MySQL mysql = mock(MySQL.class);
		when(mysql.getUUIDOrThrow("UnavailableUser")).thenThrow(new java.sql.SQLException("database unavailable"));
		when(manager.withSharedNativeUserStorage(any())).thenAnswer(invocation ->
				invocation.getArgument(0, Function.class).apply(
						new AdvancedCorePlugin.UserStorageOwner(UserStorage.MYSQL, mysql, null)));
		Constructor<UuidLookup> constructor = UuidLookup.class.getDeclaredConstructor(AdvancedCorePlugin.class);
		constructor.setAccessible(true);
		UuidLookup lookup = constructor.newInstance(plugin);

		IllegalStateException failure = assertThrows(IllegalStateException.class,
				() -> lookup.getUUIDFromStorage("UnavailableUser"));
		assertTrue(failure.getCause() instanceof java.sql.SQLException);
	}

	@Test
	void unseenOnlineNameUsesNonblockingProfileFallback() throws Exception {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		AdvancedCoreConfigOptions options = mock(AdvancedCoreConfigOptions.class);
		when(plugin.getOptions()).thenReturn(options);
		when(options.isOnlineMode()).thenReturn(true);
		UserManager users = new UserManager(plugin);
		when(plugin.getUserManager()).thenReturn(users);
		UserDataManager manager = users.getDataManager();
		manager.getTimer().shutdownNow();
		ScheduledExecutorService worker = mock(ScheduledExecutorService.class);
		Field timer = UserDataManager.class.getDeclaredField("timer");
		timer.setAccessible(true);
		timer.set(manager, worker);
		SqlUserBackend backend = mock(SqlUserBackend.class);
		manager.bindSharedSqlBackend(backend, (user, operation) -> operation.run());
		var scheduler = mock(com.bencodez.simpleapi.scheduler.BukkitScheduler.class);
		when(plugin.getBukkitScheduler()).thenReturn(scheduler);
		PlayerProfile pendingProfile = mock(PlayerProfile.class);
		PlayerProfile resolvedProfile = mock(PlayerProfile.class);
		CompletableFuture<PlayerProfile> profileResult = new CompletableFuture<>();
		when(pendingProfile.update()).thenReturn(profileResult);
		UUID profileUuid = UUID.randomUUID();
		when(resolvedProfile.getUniqueId()).thenReturn(profileUuid);
		AtomicReference<AdvancedCoreUser> delivered = new AtomicReference<>();
		AtomicReference<Throwable> failed = new AtomicReference<>();
		String playerName = "PreviouslyUnseen";
		try (var bukkit = mockStatic(Bukkit.class); var lookups = mockStatic(UuidLookup.class)) {
			bukkit.when(Bukkit::getServer).thenReturn(mock(Server.class));
			bukkit.when(Bukkit::isPrimaryThread).thenReturn(true, false, false);
			bukkit.when(() -> Bukkit.createPlayerProfile(playerName)).thenReturn(pendingProfile);
			UuidLookup lookup = mock(UuidLookup.class);
			lookups.when(UuidLookup::getInstance).thenReturn(lookup);
			when(lookup.getUUIDWithoutStorage(playerName)).thenReturn("");
			when(lookup.getUUIDFromStorage(playerName)).thenReturn("");

			users.getUserAsync(playerName, delivered::set, failed::set);
			bukkit.verify(() -> Bukkit.createPlayerProfile(playerName), never());
			ArgumentCaptor<Runnable> storageTask = ArgumentCaptor.forClass(Runnable.class);
			verify(worker).execute(storageTask.capture());
			storageTask.getValue().run();
			ArgumentCaptor<Runnable> callbacks = ArgumentCaptor.forClass(Runnable.class);
			verify(scheduler).runTask(eq(plugin), callbacks.capture());
			callbacks.getValue().run();
			bukkit.verify(() -> Bukkit.createPlayerProfile(playerName));
			assertFalse(profileResult.isDone());
			assertTrue(delivered.get() == null);

			profileResult.complete(resolvedProfile);
			verify(scheduler, times(2)).runTask(eq(plugin), callbacks.capture());
			callbacks.getAllValues().get(2).run();

			assertTrue(failed.get() == null);
			assertNotNull(delivered.get());
			assertEquals(profileUuid.toString(), delivered.get().getUUID());
			verify(lookup).cacheMapping(profileUuid.toString(), playerName);
			bukkit.verify(() -> Bukkit.getOfflinePlayer(playerName), never());
		}
	}

	@Test
	void primaryThreadStringUserConstructionSkipsNativeIdentityScans() {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		when(plugin.isLoadUserData()).thenReturn(true);
		UserManager users = spy(new UserManager(plugin));
		when(plugin.getUserManager()).thenReturn(users);
		SqlUserBackend backend = mock(SqlUserBackend.class);
		users.getDataManager().bindSharedSqlBackend(backend, (user, operation) -> operation.run());
		String name = "Uncached" + UUID.randomUUID().toString().replace("-", "");
		UUID uuid = UUID.randomUUID();
		try (var bukkit = mockStatic(Bukkit.class); var players = mockStatic(PlayerManager.class);
				var lookups = mockStatic(UuidLookup.class)) {
			bukkit.when(Bukkit::getServer).thenReturn(mock(Server.class));
			bukkit.when(Bukkit::isPrimaryThread).thenReturn(true, true, true, false);
			PlayerManager playerManager = mock(PlayerManager.class);
			players.when(PlayerManager::getInstance).thenReturn(playerManager);
			UuidLookup lookup = mock(UuidLookup.class);
			lookups.when(UuidLookup::getInstance).thenReturn(lookup);
			when(lookup.getCachedName(name)).thenReturn("");
			when(lookup.getCachedUUID(name)).thenReturn("");
			when(lookup.getUUIDWithoutStorage(name)).thenReturn(uuid.toString());

			AdvancedCoreUser user = assertDoesNotThrow(() -> users.getUser(name));

			assertEquals(name, user.getPlayerName());
			assertEquals(uuid.toString(), user.getUUID());
			verify(users, never()).getAllPlayerNames();
			verify(lookup).getUUIDWithoutStorage(name);
			verify(lookup, never()).getUUID(name);
			verify(playerManager, never()).getUUID(name);
		} finally {
			users.getDataManager().getTimer().shutdownNow();
		}
	}

	@Test
	void profileFallbackSchedulerRejectionDoesNotInvokeFailureFromProfileWorker() throws Exception {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		AdvancedCoreConfigOptions options = mock(AdvancedCoreConfigOptions.class);
		when(plugin.getOptions()).thenReturn(options);
		when(options.isOnlineMode()).thenReturn(true);
		UserManager users = new UserManager(plugin);
		when(plugin.getUserManager()).thenReturn(users);
		UserDataManager manager = users.getDataManager();
		manager.getTimer().shutdownNow();
		ScheduledExecutorService worker = mock(ScheduledExecutorService.class);
		Field timer = UserDataManager.class.getDeclaredField("timer");
		timer.setAccessible(true);
		timer.set(manager, worker);
		manager.bindSharedSqlBackend(mock(SqlUserBackend.class), (user, operation) -> operation.run());
		var scheduler = mock(com.bencodez.simpleapi.scheduler.BukkitScheduler.class);
		when(plugin.getBukkitScheduler()).thenReturn(scheduler);
		doNothing().doThrow(new RejectedExecutionException("scheduler stopped")).when(scheduler)
				.runTask(eq(plugin), any(Runnable.class));
		PlayerProfile pendingProfile = mock(PlayerProfile.class);
		CompletableFuture<PlayerProfile> profileResult = new CompletableFuture<>();
		when(pendingProfile.update()).thenReturn(profileResult);
		AtomicInteger failures = new AtomicInteger();
		AtomicBoolean succeeded = new AtomicBoolean();
		String playerName = "ProfileRejected";
		try (var bukkit = mockStatic(Bukkit.class); var lookups = mockStatic(UuidLookup.class)) {
			bukkit.when(Bukkit::getServer).thenReturn(mock(Server.class));
			bukkit.when(Bukkit::isPrimaryThread).thenReturn(true, false, false);
			bukkit.when(() -> Bukkit.createPlayerProfile(playerName)).thenReturn(pendingProfile);
			UuidLookup lookup = mock(UuidLookup.class);
			lookups.when(UuidLookup::getInstance).thenReturn(lookup);
			when(lookup.getUUIDWithoutStorage(playerName)).thenReturn("");
			when(lookup.getUUIDFromStorage(playerName)).thenReturn("");

			users.getUserAsync(playerName, ignored -> succeeded.set(true), ignored -> failures.incrementAndGet());
			ArgumentCaptor<Runnable> storageTask = ArgumentCaptor.forClass(Runnable.class);
			verify(worker).execute(storageTask.capture());
			storageTask.getValue().run();
			ArgumentCaptor<Runnable> storageCompletion = ArgumentCaptor.forClass(Runnable.class);
			verify(scheduler).runTask(eq(plugin), storageCompletion.capture());
			storageCompletion.getValue().run();
			profileResult.complete(mock(PlayerProfile.class));
		}
		assertFalse(succeeded.get());
		assertEquals(0, failures.get());
		manager.getTimer().shutdownNow();
	}

	@Test
	void uuidUserResolutionDoesNotReturnBeforePersistedNameCompletes() throws Exception {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		UserManager users = new UserManager(plugin);
		when(plugin.getUserManager()).thenReturn(users);
		UserDataManager manager = users.getDataManager();
		manager.getTimer().shutdownNow();
		ScheduledExecutorService worker = mock(ScheduledExecutorService.class);
		Field timer = UserDataManager.class.getDeclaredField("timer");
		timer.setAccessible(true);
		timer.set(manager, worker);
		SqlUserBackend backend = mock(SqlUserBackend.class);
		manager.bindSharedSqlBackend(backend, (user, operation) -> operation.run());
		var scheduler = mock(com.bencodez.simpleapi.scheduler.BukkitScheduler.class);
		when(plugin.getBukkitScheduler()).thenReturn(scheduler);
		UUID uuid = UUID.randomUUID();
		AtomicBoolean storageLane = new AtomicBoolean(false);
		try (var bukkit = mockStatic(Bukkit.class); var lookups = mockStatic(UuidLookup.class)) {
			bukkit.when(Bukkit::getServer).thenReturn(mock(Server.class));
			bukkit.when(Bukkit::isPrimaryThread).thenAnswer(ignored -> !storageLane.get());
			UuidLookup lookup = mock(UuidLookup.class);
			lookups.when(UuidLookup::getInstance).thenReturn(lookup);
			when(lookup.getCachedName(uuid.toString())).thenReturn("");
			when(lookup.getOnlinePlayerName(uuid.toString())).thenReturn("");
			when(lookup.getPlayerNameFromStorage(any(AdvancedCoreUser.class), eq(uuid.toString()), eq(false)))
					.thenReturn("StoredName");

			assertThrows(IllegalStateException.class, () -> users.getUser(uuid));
			AtomicReference<AdvancedCoreUser> resolved = new AtomicReference<>();
			AtomicReference<Throwable> failed = new AtomicReference<>();
			users.getUserAsync(uuid, resolved::set, failed::set);
			assertNull(resolved.get());
			assertNull(failed.get());
			verify(lookup, times(2)).getOnlinePlayerName(uuid.toString());
			verify(lookup, never()).getPlayerNameFromStorage(any(), any(), eq(false));
			ArgumentCaptor<Runnable> storageTask = ArgumentCaptor.forClass(Runnable.class);
			verify(worker).execute(storageTask.capture());
			storageLane.set(true);
			try {
				storageTask.getValue().run();
			} finally {
				storageLane.set(false);
			}
			verify(lookup).getPlayerNameFromStorage(any(), eq(uuid.toString()), eq(false));
			ArgumentCaptor<Runnable> callback = ArgumentCaptor.forClass(Runnable.class);
			verify(scheduler).runTask(eq(plugin), callback.capture());
			assertNull(resolved.get());
			callback.getValue().run();
			assertNotNull(resolved.get());
			assertEquals("StoredName", resolved.get().getPlayerName());
			assertNull(failed.get());
		}
	}

	@Test
	void workerStringUserResolutionCapturesIdentityOnPlatformThenReadsSharedStorageOnWorker() throws Exception {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		AdvancedCoreConfigOptions options = mock(AdvancedCoreConfigOptions.class);
		when(plugin.getOptions()).thenReturn(options);
		when(options.isOnlineMode()).thenReturn(true);
		UserManager users = new UserManager(plugin);
		when(plugin.getUserManager()).thenReturn(users);
		UserDataManager manager = users.getDataManager();
		manager.getTimer().shutdownNow();
		ScheduledExecutorService worker = mock(ScheduledExecutorService.class);
		Field timer = UserDataManager.class.getDeclaredField("timer");
		timer.setAccessible(true);
		timer.set(manager, worker);
		manager.bindSharedSqlBackend(mock(SqlUserBackend.class), (user, operation) -> operation.run());
		var scheduler = mock(com.bencodez.simpleapi.scheduler.BukkitScheduler.class);
		when(plugin.getBukkitScheduler()).thenReturn(scheduler);
		String playerName = "WorkerResolved";
		UUID uuid = UUID.randomUUID();
		AtomicReference<AdvancedCoreUser> resolved = new AtomicReference<>();
		AtomicReference<Throwable> failed = new AtomicReference<>();
		try (var bukkit = mockStatic(Bukkit.class); var lookups = mockStatic(UuidLookup.class)) {
			bukkit.when(Bukkit::getServer).thenReturn(mock(Server.class));
			bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
			UuidLookup lookup = mock(UuidLookup.class);
			lookups.when(UuidLookup::getInstance).thenReturn(lookup);
			when(lookup.getUUIDWithoutStorage(playerName)).thenReturn("");
			when(lookup.getUUIDFromStorage(playerName)).thenReturn(uuid.toString());

			users.getUserAsync(playerName, resolved::set, failed::set);
			verify(lookup, never()).getUUIDWithoutStorage(playerName);
			ArgumentCaptor<Runnable> platformIdentity = ArgumentCaptor.forClass(Runnable.class);
			verify(scheduler).runTask(eq(plugin), platformIdentity.capture());
			platformIdentity.getValue().run();
			verify(lookup).getUUIDWithoutStorage(playerName);
			verify(lookup, never()).getUUIDFromStorage(playerName);
			ArgumentCaptor<Runnable> storageTask = ArgumentCaptor.forClass(Runnable.class);
			verify(worker).execute(storageTask.capture());
			storageTask.getValue().run();
			verify(lookup).getUUIDFromStorage(playerName);
			ArgumentCaptor<Runnable> completion = ArgumentCaptor.forClass(Runnable.class);
			verify(scheduler, times(2)).runTask(eq(plugin), completion.capture());
			completion.getAllValues().get(1).run();

			assertNotNull(resolved.get());
			assertEquals(uuid.toString(), resolved.get().getUUID());
			assertNull(failed.get());
		}
		manager.getTimer().shutdownNow();
	}

	@Test
	void primaryThreadPersistedBulkReadsRequireWorkerDeferral() {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		UserManager users = mock(UserManager.class);
		when(plugin.getUserManager()).thenReturn(users);
		when(plugin.getStorageType()).thenReturn(UserStorage.MYSQL);
		UserDataManager manager = new UserDataManager(plugin);
		when(users.getDataManager()).thenReturn(manager);
		UUID uuid = UUID.randomUUID();
		SqlUserBackend backend = mock(SqlUserBackend.class);
		when(backend.storageType()).thenReturn(UserStorage.MYSQL);
		manager.bindSharedSqlBackend(backend, (user, operation) -> operation.run());
		UserDataCache cache = new UserDataCache(manager, uuid);
		cache.updateCachePreservingPending(new HashMap<>(Map.of("Points", new DataValueInt(7))));
		manager.getUserDataCache().put(uuid, cache);
		AdvancedCoreUser user = mock(AdvancedCoreUser.class);
		when(user.getPlugin()).thenReturn(plugin);
		when(user.getUUID()).thenReturn(uuid.toString());
		when(user.getCache()).thenReturn(cache);
		UserData data = new UserData(user);
		try (var bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(Bukkit::getServer).thenReturn(mock(Server.class));
			bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
			IllegalStateException keysFailure = assertThrows(IllegalStateException.class, data::getKeys);
			IllegalStateException valuesFailure = assertThrows(IllegalStateException.class, data::getValues);
			assertTrue(data.hasData());
			IllegalStateException intFailure = assertThrows(IllegalStateException.class,
					() -> data.getInt(UserStorage.MYSQL, "Points", -1,
							com.bencodez.advancedcore.api.user.UserDataFetchMode.NO_CACHE));
			IllegalStateException stringFailure = assertThrows(IllegalStateException.class,
					() -> data.getString(UserStorage.MYSQL, "PlayerName",
							com.bencodez.advancedcore.api.user.UserDataFetchMode.NO_CACHE));
			assertTrue(intFailure.getMessage().contains("defer"));
			assertTrue(stringFailure.getMessage().contains("defer"));
			assertTrue(keysFailure.getMessage().contains("defer"));
			assertTrue(valuesFailure.getMessage().contains("defer"));
			verify(backend, never()).user(any(UUID.class));
		}
		manager.getTimer().shutdownNow();
	}

	@Test
	void primaryThreadHasDataDoesNotCreateOrPopulateAnUncachedUser() {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		UserManager users = mock(UserManager.class);
		when(plugin.getUserManager()).thenReturn(users);
		when(plugin.getStorageType()).thenReturn(UserStorage.MYSQL);
		UserDataManager manager = new UserDataManager(plugin);
		when(users.getDataManager()).thenReturn(manager);
		SqlUserBackend backend = mock(SqlUserBackend.class);
		when(backend.storageType()).thenReturn(UserStorage.MYSQL);
		manager.bindSharedSqlBackend(backend, (user, operation) -> operation.run());
		UUID uuid = UUID.randomUUID();
		AdvancedCoreUser user = mock(AdvancedCoreUser.class);
		when(user.getPlugin()).thenReturn(plugin);
		when(user.getUUID()).thenReturn(uuid.toString());
		UserData data = new UserData(user);

		try (var bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(Bukkit::getServer).thenReturn(mock(Server.class));
			bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
			IllegalStateException failure = assertThrows(IllegalStateException.class, data::hasData);
			assertTrue(failure.getMessage().contains("defer"));
			assertFalse(manager.containsKey(uuid));
			verify(user, never()).getCache();
			verify(backend, never()).user(any(UUID.class));
		}
		manager.getTimer().shutdownNow();
	}

	@Test
	void primaryThreadNameUpdateDefersExistenceCheckBeforeReadingOrWriting() throws Exception {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		UserManager users = mock(UserManager.class);
		when(plugin.getUserManager()).thenReturn(users);
		UserDataManager manager = new UserDataManager(plugin);
		when(users.getDataManager()).thenReturn(manager);
		manager.getTimer().shutdownNow();
		ScheduledExecutorService worker = mock(ScheduledExecutorService.class);
		Field timer = UserDataManager.class.getDeclaredField("timer");
		timer.setAccessible(true);
		timer.set(manager, worker);
		SqlUserBackend backend = mock(SqlUserBackend.class);
		manager.bindSharedSqlBackend(backend, (user, operation) -> operation.run());
		when(plugin.getBukkitScheduler()).thenReturn(mock(com.bencodez.simpleapi.scheduler.BukkitScheduler.class));
		AdvancedCoreUser user = mock(AdvancedCoreUser.class);
		UserData data = mock(UserData.class);
		Field pluginField = AdvancedCoreUser.class.getDeclaredField("plugin");
		pluginField.setAccessible(true);
		pluginField.set(user, plugin);
		Field fetchMode = AdvancedCoreUser.class.getDeclaredField("userDataFetchMode");
		fetchMode.setAccessible(true);
		fetchMode.set(user, com.bencodez.advancedcore.api.user.UserDataFetchMode.DEFAULT);
		when(user.getPlugin()).thenReturn(plugin);
		when(user.getData()).thenReturn(data);
		when(user.getPlayerName()).thenReturn("CurrentName");
		when(data.hasData()).thenReturn(true);
		when(data.getString(eq("PlayerName"), any())).thenReturn("OldName");
		doCallRealMethod().when(user).updateName(false);
		Server server = mock(Server.class);
		try (var bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(Bukkit::getServer).thenReturn(server);
			bukkit.when(Bukkit::isPrimaryThread).thenReturn(true, false);
			ArgumentCaptor<Runnable> storageTask = ArgumentCaptor.forClass(Runnable.class);
			assertDoesNotThrow(() -> user.updateName(false));
			verify(worker).execute(storageTask.capture());
			verify(data, never()).hasData();
			verify(data, never()).getString(any(), any());
			storageTask.getValue().run();
			verify(data).hasData();
			verify(data).getString("PlayerName", com.bencodez.advancedcore.api.user.UserDataFetchMode.DEFAULT);
			ArgumentCaptor<Runnable> callback = ArgumentCaptor.forClass(Runnable.class);
			verify(plugin.getBukkitScheduler()).runTask(eq(plugin), callback.capture());
			callback.getValue().run();
			verify(data, times(1)).getString("PlayerName", com.bencodez.advancedcore.api.user.UserDataFetchMode.DEFAULT);
			verify(data).setString("PlayerName", "CurrentName", true);
		}
	}

	@Test
	void primaryThreadStorageResultIsReadOnTheWorkerAndDeliveredBackToBukkit() throws Exception {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		UserDataManager manager = new UserDataManager(plugin);
		manager.getTimer().shutdownNow();
		ScheduledExecutorService worker = mock(ScheduledExecutorService.class);
		Field timer = UserDataManager.class.getDeclaredField("timer");
		timer.setAccessible(true);
		timer.set(manager, worker);
		SqlUserBackend backend = mock(SqlUserBackend.class);
		manager.bindSharedSqlBackend(backend, (user, operation) -> operation.run());
		var scheduler = mock(com.bencodez.simpleapi.scheduler.BukkitScheduler.class);
		when(plugin.getBukkitScheduler()).thenReturn(scheduler);
		Player callbackOwner = mock(Player.class);
		AtomicBoolean read = new AtomicBoolean();
		AtomicBoolean delivered = new AtomicBoolean();
		try (var bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(Bukkit::getServer).thenReturn(mock(Server.class));
			bukkit.when(Bukkit::isPrimaryThread).thenReturn(true, false, true, false);
			ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
			assertTrue(manager.deferSharedStorageResult(() -> {
				read.set(true);
				return "row";
			}, value -> {
				assertEquals("row", value);
				delivered.set(true);
			}, failure -> { throw new AssertionError(failure); }, callbackOwner));
			verify(worker).execute(task.capture());
			assertFalse(read.get());
			task.getValue().run();
			assertTrue(read.get());
			assertFalse(delivered.get());
			ArgumentCaptor<Runnable> callback = ArgumentCaptor.forClass(Runnable.class);
			verify(scheduler).runTask(eq(plugin), callback.capture(), same(callbackOwner));
			callback.getValue().run();
			assertTrue(delivered.get());

			AtomicBoolean failed = new AtomicBoolean();
			assertTrue(manager.deferSharedStorageResult(() -> {
				throw new IllegalStateException("read failed");
			}, value -> fail("unexpected success"), failure -> failed.set(true), callbackOwner));
			ArgumentCaptor<Runnable> failedTask = ArgumentCaptor.forClass(Runnable.class);
			verify(worker, times(2)).execute(failedTask.capture());
			failedTask.getAllValues().get(1).run();
			ArgumentCaptor<Runnable> failedCallback = ArgumentCaptor.forClass(Runnable.class);
			verify(scheduler, times(2)).runTask(eq(plugin), failedCallback.capture(), same(callbackOwner));
			failedCallback.getAllValues().get(1).run();
			assertTrue(failed.get());
		}
		manager.getTimer().shutdownNow();
	}

	@Test
	void rejectedStorageCompletionSchedulerRecordsUndeliverableCompletionWithoutWorkerCallback() throws Exception {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		UserDataManager manager = new UserDataManager(plugin);
		manager.getTimer().shutdownNow();
		ScheduledExecutorService worker = mock(ScheduledExecutorService.class);
		Field timer = UserDataManager.class.getDeclaredField("timer");
		timer.setAccessible(true);
		timer.set(manager, worker);
		manager.bindSharedSqlBackend(mock(SqlUserBackend.class), (user, operation) -> operation.run());
		var scheduler = mock(com.bencodez.simpleapi.scheduler.BukkitScheduler.class);
		when(plugin.getBukkitScheduler()).thenReturn(scheduler);
		doThrow(new RejectedExecutionException("scheduler stopped")).when(scheduler)
				.runTask(eq(plugin), any(Runnable.class));
		AtomicBoolean succeeded = new AtomicBoolean();
		AtomicReference<Throwable> failed = new AtomicReference<>();
		try (var bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(Bukkit::getServer).thenReturn(mock(Server.class));
			bukkit.when(Bukkit::isPrimaryThread).thenReturn(true, false);
			assertTrue(manager.deferSharedStorageResult(() -> "row", value -> succeeded.set(true), failed::set));
			ArgumentCaptor<Runnable> storage = ArgumentCaptor.forClass(Runnable.class);
			verify(worker).execute(storage.capture());
			storage.getValue().run();
		}
		assertFalse(succeeded.get());
		assertNull(failed.get());
		assertTrue(manager.getLastDeferredStorageFailure() instanceof RejectedExecutionException);
		manager.getTimer().shutdownNow();
	}

	@Test
	void acceptedPrimaryThreadCleanupFailureIsLoggedAndRetained() throws Exception {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		Logger logger = mock(Logger.class);
		when(plugin.getLogger()).thenReturn(logger);
		UserDataManager manager = new UserDataManager(plugin);
		manager.getTimer().shutdownNow();
		ScheduledExecutorService worker = mock(ScheduledExecutorService.class);
		Field timer = UserDataManager.class.getDeclaredField("timer");
		timer.setAccessible(true);
		timer.set(manager, worker);
		UUID uuid = UUID.randomUUID();
		UserDataCache cache = mock(UserDataCache.class);
		RuntimeException failure = new IllegalStateException("flush failed");
		org.mockito.Mockito.doThrow(failure).when(cache).clearCache();
		manager.getUserDataCache().put(uuid, cache);
		SqlUserBackend backend = mock(SqlUserBackend.class);
		when(backend.isOpen()).thenReturn(true);
		manager.bindSharedSqlBackend(backend, (user, operation) -> operation.run());
		Server server = mock(Server.class);
		try (var bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(Bukkit::getServer).thenReturn(server);
			bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
			ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
			manager.clearCache();
			verify(worker).execute(task.capture());
			assertSame(failure, assertThrows(RuntimeException.class, task.getValue()::run));
			assertSame(failure, manager.getLastDeferredStorageFailure());
			verify(logger).log(Level.SEVERE, "Deferred user-cache cleanup failed", failure);
			assertTrue(manager.getUserDataCache().containsKey(uuid));
		}
	}

	@Test
	void completionAwareCacheClearWaitsForWorkerAndReportsItsFailure() throws Exception {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		Logger logger = mock(Logger.class);
		when(plugin.getLogger()).thenReturn(logger);
		UserDataManager manager = new UserDataManager(plugin);
		manager.getTimer().shutdownNow();
		ScheduledExecutorService worker = mock(ScheduledExecutorService.class);
		Field timer = UserDataManager.class.getDeclaredField("timer");
		timer.setAccessible(true);
		timer.set(manager, worker);
		UUID uuid = UUID.randomUUID();
		UserDataCache cache = mock(UserDataCache.class);
		RuntimeException failure = new IllegalStateException("flush failed");
		org.mockito.Mockito.doThrow(failure).when(cache).clearCache();
		manager.getUserDataCache().put(uuid, cache);
		SqlUserBackend backend = mock(SqlUserBackend.class);
		when(backend.isOpen()).thenReturn(true);
		manager.bindSharedSqlBackend(backend, (user, operation) -> operation.run());
		Server server = mock(Server.class);
		try (var bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(Bukkit::getServer).thenReturn(server);
			bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
			ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
			CompletableFuture<Void> completion = manager.clearCacheAsyncCompletion().toCompletableFuture();
			verify(worker).execute(task.capture());
			assertFalse(completion.isDone());
			task.getValue().run();
			CompletionException reported = assertThrows(CompletionException.class, completion::join);
			assertSame(failure, reported.getCause());
			assertSame(failure, manager.getLastDeferredStorageFailure());
			assertTrue(manager.getUserDataCache().containsKey(uuid));
		}
	}

	@Test
	void completionAwareCacheClearReturnsRejectedStage() throws Exception {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		when(plugin.getLogger()).thenReturn(mock(Logger.class));
		UserDataManager manager = new UserDataManager(plugin);
		manager.getTimer().shutdownNow();
		ScheduledExecutorService worker = mock(ScheduledExecutorService.class);
		Field timer = UserDataManager.class.getDeclaredField("timer");
		timer.setAccessible(true);
		timer.set(manager, worker);
		SqlUserBackend backend = mock(SqlUserBackend.class);
		when(backend.isOpen()).thenReturn(true);
		manager.bindSharedSqlBackend(backend, (user, operation) -> operation.run());
		RejectedExecutionException rejection = new RejectedExecutionException("stopped");
		org.mockito.Mockito.doThrow(rejection).when(worker).execute(any(Runnable.class));
		try (var bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(Bukkit::getServer).thenReturn(mock(Server.class));
			bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
			CompletableFuture<Void> completion = manager.clearCacheAsyncCompletion().toCompletableFuture();
			CompletionException reported = assertThrows(CompletionException.class, completion::join);
			assertSame(rejection, reported.getCause());
			assertSame(rejection, manager.getLastDeferredStorageFailure());
		}
	}

	@Test
	void failedPrimaryThreadPopulationIsRetainedBeforeNotificationDelivery() throws Exception {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class, RETURNS_DEEP_STUBS);
		when(plugin.getNativeUserStorageOwner()).thenReturn(null);
		UserDataManager manager = new UserDataManager(plugin);
		manager.getTimer().shutdownNow();
		ScheduledExecutorService worker = mock(ScheduledExecutorService.class);
		Field timer = UserDataManager.class.getDeclaredField("timer");
		timer.setAccessible(true);
		timer.set(manager, worker);
		UUID uuid = UUID.randomUUID();
		IllegalStateException failure = new IllegalStateException("storage unavailable");
		when(plugin.getUserManager().getDataManager()).thenReturn(manager);
		when(plugin.getUserManager().getUser(uuid, false).getUserData().getKeys()).thenThrow(failure);
		SqlUserBackend backend = mock(SqlUserBackend.class);
		when(backend.storageType()).thenReturn(UserStorage.MYSQL);
		manager.bindSharedSqlBackend(backend, (user, operation) -> operation.run());
		try (var bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(Bukkit::getServer).thenReturn(mock(Server.class));
			bukkit.when(Bukkit::isPrimaryThread).thenReturn(true, false);
			ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);

			manager.cacheUser(uuid, null);
			verify(worker).execute(task.capture());
			task.getValue().run();

			assertSame(failure, manager.getLastDeferredStorageFailure());
		}
	}

	@Test
	void synchronousPopulationRethrowsStorageFailure() {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class, RETURNS_DEEP_STUBS);
		when(plugin.getNativeUserStorageOwner()).thenReturn(null);
		UserDataManager manager = new UserDataManager(plugin);
		UUID uuid = UUID.randomUUID();
		IllegalStateException failure = new IllegalStateException("storage unavailable");
		when(plugin.getUserManager().getUser(uuid, false).getUserData().getKeys()).thenThrow(failure);
		SqlUserBackend backend = mock(SqlUserBackend.class);
		when(backend.storageType()).thenReturn(UserStorage.MYSQL);
		manager.bindSharedSqlBackend(backend, (user, operation) -> operation.run());
		try {
			assertSame(failure, assertThrows(IllegalStateException.class,
					() -> manager.cacheUser(uuid, null)));
			assertFalse(manager.containsKey(uuid));
		} finally {
			manager.getTimer().shutdownNow();
		}
	}

	@Test
	void primaryThreadCachePopulationIsQueuedAndGetCacheReturnsAPopulationPlaceholder() throws Exception {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		UserDataManager manager = new UserDataManager(plugin);
		manager.getTimer().shutdownNow();
		ScheduledExecutorService worker = mock(ScheduledExecutorService.class);
		Field timer = UserDataManager.class.getDeclaredField("timer");
		timer.setAccessible(true);
		timer.set(manager, worker);
		UUID uuid = UUID.randomUUID();
		SqlUserBackend backend = mock(SqlUserBackend.class);
		when(backend.isOpen()).thenReturn(true);
		manager.bindSharedSqlBackend(backend, (user, operation) -> {
			throw new AssertionError("primary thread must not admit cache storage work");
		});
		Server server = mock(Server.class);
		try (var bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(Bukkit::getServer).thenReturn(server);
			bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
			assertDoesNotThrow(() -> manager.cacheUser(uuid, null));
			verify(worker).execute(any(Runnable.class));
			assertNotNull(manager.getCache(uuid));
			verify(worker, times(1)).execute(any(Runnable.class));
		}
	}

	@Test
	void removingCompletedPlaceholderAllowsAReplacementPopulation() throws Exception {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		UserDataManager manager = new UserDataManager(plugin);
		manager.getTimer().shutdownNow();
		ScheduledExecutorService worker = mock(ScheduledExecutorService.class);
		Field timer = UserDataManager.class.getDeclaredField("timer");
		timer.setAccessible(true);
		timer.set(manager, worker);
		UUID uuid = UUID.randomUUID();
		UserDataCache cache = mock(UserDataCache.class);
		manager.getUserDataCache().put(uuid, cache);
		Field completed = UserDataManager.class.getDeclaredField("completedSharedCachePopulations");
		completed.setAccessible(true);
		@SuppressWarnings("unchecked")
		Set<UUID> completedPopulations = (Set<UUID>) completed.get(manager);
		completedPopulations.add(uuid);
		SqlUserBackend backend = mock(SqlUserBackend.class);
		when(backend.isOpen()).thenReturn(true);
		manager.bindSharedSqlBackend(backend, (user, operation) -> operation.run());
		Server server = mock(Server.class);
		try (var bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(Bukkit::getServer).thenReturn(server);
			bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
			ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
			manager.removeCache(uuid, null);
			verify(worker).execute(task.capture());
			task.getValue().run();
			assertFalse(completedPopulations.contains(uuid));
			assertNotNull(manager.getCache(uuid));
			verify(worker, times(2)).execute(any(Runnable.class));
		}
	}

	@Test
	void runtimeOwnerRetirementClearsPopulationMarkersAndFencesAnOldWorker() throws Exception {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		UserDataManager manager = new UserDataManager(plugin);
		manager.getTimer().shutdownNow();
		ScheduledExecutorService worker = mock(ScheduledExecutorService.class);
		Field timer = UserDataManager.class.getDeclaredField("timer");
		timer.setAccessible(true);
		timer.set(manager, worker);
		UUID uuid = UUID.randomUUID();
		UserDataCache cache = mock(UserDataCache.class);
		manager.getUserDataCache().put(uuid, cache);
		SqlUserBackend backend = mock(SqlUserBackend.class);
		when(backend.isOpen()).thenReturn(true);
		manager.bindSharedSqlBackend(backend, (user, operation) -> operation.run());
		Field populations = UserDataManager.class.getDeclaredField("sharedCachePopulations");
		populations.setAccessible(true);
		Field completed = UserDataManager.class.getDeclaredField("completedSharedCachePopulations");
		completed.setAccessible(true);
		@SuppressWarnings("unchecked")
		Set<UUID> inFlight = (Set<UUID>) populations.get(manager);
		@SuppressWarnings("unchecked")
		Set<UUID> completedPopulations = (Set<UUID>) completed.get(manager);
		Server server = mock(Server.class);
		try (var bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(Bukkit::getServer).thenReturn(server);
			bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
			ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
			manager.cacheUser(uuid, null);
			verify(worker).execute(task.capture());
			assertTrue(inFlight.contains(uuid));

			new BukkitUserCacheOwner(manager).clearAfterFlush();
			assertFalse(manager.getUserDataCache().containsKey(uuid));
			assertFalse(inFlight.contains(uuid));
			assertFalse(completedPopulations.contains(uuid));

			task.getValue().run();
			assertFalse(manager.getUserDataCache().containsKey(uuid));
			assertFalse(completedPopulations.contains(uuid),
					"a retired population must not republish completion for a replacement cache");
			verify(cache).retireAfterSharedFlush();
			verifyNoMoreInteractions(cache);
		}
	}

    @Test
    void rejectedPrimaryThreadCleanupIsReportedWithoutRunningStorageWork() throws Exception {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        UserDataManager manager = new UserDataManager(plugin);
        manager.getTimer().shutdownNow();
        ScheduledExecutorService worker = mock(ScheduledExecutorService.class);
        Field timer = UserDataManager.class.getDeclaredField("timer");
        timer.setAccessible(true);
        timer.set(manager, worker);
        UUID uuid = UUID.randomUUID();
        UserDataCache cache = mock(UserDataCache.class);
        manager.getUserDataCache().put(uuid, cache);
        SqlUserBackend backend = mock(SqlUserBackend.class);
        when(backend.isOpen()).thenReturn(true);
        manager.bindSharedSqlBackend(backend, (user, operation) -> operation.run());
        RejectedExecutionException rejection = new RejectedExecutionException("stopped");
        org.mockito.Mockito.doThrow(rejection).when(worker).execute(any(Runnable.class));
        Server server = mock(Server.class);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getServer).thenReturn(server);
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            assertSame(rejection, assertThrows(RejectedExecutionException.class, manager::clearCache));
            verify(cache, never()).clearCache();
            assertTrue(manager.getUserDataCache().containsKey(uuid));
        }
    }

    @Test
    void sharedCacheClearMovesTheWholeFlushAndRemovalSequenceOffThePrimaryThread() throws Exception {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        UserDataManager manager = new UserDataManager(plugin);
        manager.getTimer().shutdownNow();
        ScheduledExecutorService worker = mock(ScheduledExecutorService.class);
        Field timer = UserDataManager.class.getDeclaredField("timer");
        timer.setAccessible(true);
        timer.set(manager, worker);

        UUID uuid = UUID.randomUUID();
        UserDataCache cache = mock(UserDataCache.class);
        manager.getUserDataCache().put(uuid, cache);
        SqlUserBackend backend = mock(SqlUserBackend.class);
        when(backend.isOpen()).thenReturn(true);
        when(backend.storageType()).thenReturn(UserStorage.SQLITE);
        manager.bindSharedSqlBackend(backend, (user, operation) -> operation.run());

        Server server = mock(Server.class);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getServer).thenReturn(server);
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
            manager.clearCache();
            verify(worker).execute(task.capture());
            verify(cache, never()).clearCache();
            assertTrue(manager.getUserDataCache().containsKey(uuid));
            task.getValue().run();
            verify(cache).clearCache();
            verify(cache).dump();
            assertFalse(manager.getUserDataCache().containsKey(uuid));
        }
    }
}
