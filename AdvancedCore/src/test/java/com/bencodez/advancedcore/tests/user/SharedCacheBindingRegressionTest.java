package com.bencodez.advancedcore.tests.user;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.bukkit.Bukkit;
import org.bukkit.Server;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.advancedcore.api.user.UserData;
import com.bencodez.advancedcore.api.user.UserDataFetchMode;
import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.advancedcore.api.user.usercache.UserDataCache;
import com.bencodez.advancedcore.api.user.usercache.UserDataManager;
import com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt;
import com.bencodez.advancedcore.bukkit.user.runtime.BukkitUserCacheOwner;
import com.bencodez.advancedcore.core.user.runtime.SharedUserDataRuntime;
import com.bencodez.advancedcore.core.user.runtime.UserCacheOwner;
import com.bencodez.advancedcore.core.user.storage.SqlUserStorage;
import com.bencodez.advancedcore.core.user.storage.sql.SqlUserBackend;
import com.bencodez.simpleapi.sql.Column;
import com.bencodez.simpleapi.sql.data.DataValue;
import com.bencodez.simpleapi.sql.data.DataValueInt;

@Timeout(15)
class SharedCacheBindingRegressionTest {
	@Test void sharedPopulationRetainsPersistedDynamicColumns() throws Exception {
		try (Fixture fixture = new Fixture()) {
			var data = fixture.plugin.getUserManager().getUser(fixture.uuid, false).getUserData();
			when(data.getKeys()).thenReturn(new ArrayList<>(List.of("VoteShopLimitDaily")));
			when(data.getValues()).thenReturn(new HashMap<>(Map.of("VoteShopLimitDaily", new DataValueInt(4))));
			SharedUserDataRuntime runtime = fixture.runtime();

			UserDataCache cache = fixture.manager.getCache(fixture.uuid);

			assertEquals(4, cache.snapshot().get("VoteShopLimitDaily").getInt());
			runtime.close();
		}
	}

    @Test void noDatabaseLookupMissDoesNotLoadOrPopulate() { cacheMiss(UserDataFetchMode.NO_DB_LOOKUP); }
    @Test void cacheOnlyMissDoesNotLoadOrPopulate() { cacheMiss(UserDataFetchMode.CACHE_ONLY); }
    @Test void temporaryOnlyMissDoesNotLoadOrPopulate() { cacheMiss(UserDataFetchMode.TEMP_ONLY); }

	@Test void userDataRemovalUsesTheSharedRuntimeExclusiveDeletePath() {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		var users = mock(com.bencodez.advancedcore.api.user.UserManager.class);
		AdvancedCoreUser user = mock(AdvancedCoreUser.class);
		UserDataManager manager = new UserDataManager(plugin);
		SharedUserDataRuntime runtime = mock(SharedUserDataRuntime.class);
		SqlUserBackend backend = mock(SqlUserBackend.class);
		UUID uuid = UUID.randomUUID();
		when(plugin.getUserManager()).thenReturn(users);
		when(plugin.getStorageType()).thenReturn(UserStorage.MYSQL);
		when(users.getDataManager()).thenReturn(manager);
		when(user.getPlugin()).thenReturn(plugin);
		when(user.getUUID()).thenReturn(uuid.toString());
		when(runtime.isClosed()).thenReturn(false);
		when(runtime.backend()).thenReturn(backend);
		when(backend.storageType()).thenReturn(UserStorage.MYSQL);
		manager.bindSharedRuntime(runtime);
		try {
			new UserData(user).remove();
			verify(runtime).remove(uuid);
			verify(user, never()).clearCache();
		} finally {
			manager.getTimer().shutdownNow();
		}
	}

	@Test void explicitAlternateStoreReadsCannotUseTheSharedCache() throws Exception {
		try (Fixture fixture = new Fixture()) {
			SharedUserDataRuntime runtime = fixture.runtime();
			fixture.manager.bindSharedRuntime(runtime);
			AdvancedCoreUser user = fixture.plugin.getUserManager().getUser(fixture.uuid, false);
			when(fixture.plugin.getUserManager().getDataManager()).thenReturn(fixture.manager);
			when(user.getPlugin()).thenReturn(fixture.plugin);
			when(user.getUUID()).thenReturn(fixture.uuid.toString());
			UserData data = new UserData(user);

			assertThrows(IllegalStateException.class,
					() -> data.getInt(UserStorage.SQLITE, "Points", 0, UserDataFetchMode.CACHE_ONLY));
			assertThrows(IllegalStateException.class,
					() -> data.getString(UserStorage.SQLITE, "PlayerName", UserDataFetchMode.CACHE_ONLY));
			verify(user, never()).getCache();
			runtime.close();
		}
	}

    @Test void legacyCachePopulationPublishesOnlyAfterSharedAdmission() throws Exception {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class, RETURNS_DEEP_STUBS);
        when(plugin.getNativeUserStorageOwner()).thenReturn(null);
        UserDataManager manager = new UserDataManager(plugin);
        manager.getTimer().shutdownNow();
        UUID uuid = UUID.randomUUID();
        var data = plugin.getUserManager().getUser(uuid, false).getUserData();
        when(data.getKeys()).thenReturn(new ArrayList<>());
        when(data.getValues()).thenReturn(new HashMap<>());
        SqlUserBackend backend = mock(SqlUserBackend.class);
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        manager.bindSharedSqlBackend(backend, (id, operation) -> {
            entered.countDown();
            try {
                await(release);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError(interrupted);
            }
            operation.run();
        });
        var worker = Executors.newSingleThreadExecutor();
        try {
            var population = worker.submit(() -> manager.cacheUser(uuid, null));
            await(entered);
            assertFalse(manager.containsKey(uuid), "a detached snapshot must not be published before admission");
            release.countDown();
            population.get(5, TimeUnit.SECONDS);
            assertTrue(manager.containsKey(uuid));
        } finally {
            release.countDown();
            worker.shutdownNow();
            assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test void preBindingCachePopulationBlocksSharedTransitionAdmission() throws Exception {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class, RETURNS_DEEP_STUBS);
        when(plugin.getNativeUserStorageOwner()).thenReturn(null);
        UserDataManager manager = new UserDataManager(plugin);
        manager.getTimer().shutdownNow();
        UUID uuid = UUID.randomUUID();
        CountDownLatch loading = new CountDownLatch(1), release = new CountDownLatch(1);
        var data = plugin.getUserManager().getUser(uuid, false).getUserData();
        when(data.getKeys()).thenReturn(new ArrayList<>());
        when(data.getValues()).thenAnswer(call -> {
            loading.countDown();
            await(release);
            return new HashMap<String, DataValue>();
        });
        var worker = Executors.newSingleThreadExecutor();
        try {
            var population = worker.submit(() -> manager.cacheUser(uuid, null));
            await(loading);
            assertThrows(IllegalStateException.class, manager::beginSharedBindingTransition);
            release.countDown();
            population.get(5, TimeUnit.SECONDS);
            assertDoesNotThrow(manager::beginSharedBindingTransition);
            manager.endSharedBindingTransition();
        } finally {
            release.countDown();
            worker.shutdownNow();
            assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test void cachePopulationCannotPublishAfterBackendReplacementStarts() throws Exception {
        try (Fixture fixture = new Fixture()) {
            SharedUserDataRuntime runtime = fixture.runtime();
            CountDownLatch loading = new CountDownLatch(1), release = new CountDownLatch(1);
            var data = fixture.plugin.getUserManager().getUser(fixture.uuid, false).getUserData();
            doAnswer(call -> {
                loading.countDown();
                try {
                    await(release);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(interrupted);
                }
                return new HashMap<String, DataValue>();
            }).when(data).getValues();
            MemoryBackend replacement = new MemoryBackend(UserStorage.SQLITE);
            var workers = Executors.newFixedThreadPool(2);
            try {
                var population = workers.submit(() -> fixture.manager.cacheUser(fixture.uuid, null));
                await(loading);
                var replacementTask = workers.submit(() -> runtime.replaceBackend(replacement));
                assertThrows(java.util.concurrent.TimeoutException.class,
                        () -> replacementTask.get(200, TimeUnit.MILLISECONDS));
                release.countDown();
                population.get(5, TimeUnit.SECONDS);
                replacementTask.get(5, TimeUnit.SECONDS);
                assertFalse(fixture.manager.containsKey(fixture.uuid));
                assertFalse(fixture.first.isOpen());
            } finally {
                release.countDown();
                workers.shutdownNow();
                assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
            }
            runtime.close();
        }
    }

    @Test void managerClearDoesNotDeadlockWithSameUserPopulation() throws Exception {
        try (Fixture fixture = new Fixture()) {
            SharedUserDataRuntime runtime = fixture.runtime();
            UserDataCache cache = fixture.manager.getCache(fixture.uuid);
            CountDownLatch writing = new CountDownLatch(1), releaseWrite = new CountDownLatch(1);
            cache.setSharedStorageWriter(values -> {
                writing.countDown();
                try {
                    await(releaseWrite);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(interrupted);
                }
            });
            cache.addChange(new UserDataChangeInt("Points", 1), true);
            var workers = Executors.newFixedThreadPool(2);
            try {
                var clear = workers.submit(fixture.manager::clearCache);
                await(writing);
                var population = workers.submit(() -> fixture.manager.cacheUser(fixture.uuid, null));
                releaseWrite.countDown();
                clear.get(5, TimeUnit.SECONDS);
                population.get(5, TimeUnit.SECONDS);
                assertTrue(fixture.manager.containsKey(fixture.uuid));
            } finally {
                releaseWrite.countDown();
                workers.shutdownNow();
                assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
            }
            runtime.close();
        }
    }

    private void cacheMiss(UserDataFetchMode mode) {
        SqlUserBackend backend = mock(SqlUserBackend.class);
        UserCacheOwner owner = mock(UserCacheOwner.class);
        when(backend.isOpen()).thenReturn(true);
        SharedUserDataRuntime runtime = new SharedUserDataRuntime(backend, owner);
        UUID uuid = UUID.randomUUID();
        DataValue fallback = new DataValueInt(17);
        assertSame(fallback, runtime.read(uuid, "Points", mode, null, fallback));
        assertSame(fallback, runtime.read(uuid, "Points", mode, new HashMap<>(), fallback));
        verify(backend, never()).user(any(UUID.class));
        verify(owner, never()).populate(any(UUID.class), any());
        verify(owner, never()).queueChange(any(), any(), any());
        verify(owner, never()).flush(any(), any(UserStorage.class), any());
    }

    @Test void noDatabaseModesRetainTemporaryAndUserCachePrecedence() {
        SqlUserBackend backend = mock(SqlUserBackend.class);
        UserCacheOwner owner = mock(UserCacheOwner.class);
        when(backend.isOpen()).thenReturn(true);
        UUID uuid = UUID.randomUUID();
        DataValue cached = new DataValueInt(2), temporary = new DataValueInt(3);
        when(owner.getIfPresent(uuid, "Points")).thenReturn(cached);
        SharedUserDataRuntime runtime = new SharedUserDataRuntime(backend, owner);
        HashMap<String, DataValue> temp = new HashMap<>(Map.of("Points", temporary));
        assertSame(temporary, runtime.read(uuid, "Points", UserDataFetchMode.NO_DB_LOOKUP, temp, null));
        assertSame(cached, runtime.read(uuid, "Points", UserDataFetchMode.CACHE_ONLY, temp, null));
        assertSame(temporary, runtime.read(uuid, "Points", UserDataFetchMode.TEMP_ONLY, temp, null));
        verify(backend, never()).user(any(UUID.class));
    }

    @Test void managerGetCacheAfterRuntimeStartupUsesTheSelectedWriter() throws Exception {
        try (Fixture fixture = new Fixture()) {
            SharedUserDataRuntime runtime = fixture.runtime();
            UserDataCache cache = fixture.manager.getCache(fixture.uuid);
            cache.addChange(new UserDataChangeInt("Points", 7), true);
            fixture.tasks.get(0).run();
            assertEquals(7, fixture.first.points(fixture.uuid));
            assertFalse(cache.hasChangesToProcess());
            fixture.assertNoLegacyWrites();
            runtime.close();
        }
    }

    @Test void completedBatchDoesNotOverwriteANewerQueuedCacheValue() throws Exception {
        try (Fixture fixture = new Fixture()) {
            SharedUserDataRuntime runtime = fixture.runtime();
            UserDataCache cache = fixture.manager.getCache(fixture.uuid);
            CountDownLatch writeStarted = new CountDownLatch(1), releaseWrite = new CountDownLatch(1);
            fixture.first.blockWrites(writeStarted, releaseWrite);
            cache.addChange(new UserDataChangeInt("Points", 7), true);
            var worker = Executors.newSingleThreadExecutor();
            try {
                var firstBatch = worker.submit(fixture.tasks.get(0));
                await(writeStarted);
                cache.addChange(new UserDataChangeInt("Points", 9), true);
                assertEquals(9, cache.getCache().get("Points").getInt());
                releaseWrite.countDown();
                firstBatch.get(5, TimeUnit.SECONDS);
                assertEquals(9, cache.getCache().get("Points").getInt(),
                        "the older completed batch must not replace the newer queued value");
                fixture.first.blockWrites(null, null);
                fixture.tasks.get(1).run();
                assertEquals(9, fixture.first.points(fixture.uuid));
            } finally {
                releaseWrite.countDown();
                worker.shutdownNow();
                assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
            }
            runtime.close();
        }
    }

    @Test void immediateWriteIsPersistedAfterAnOlderQueuedBatch() throws Exception {
        try (Fixture fixture = new Fixture()) {
            SharedUserDataRuntime runtime = fixture.runtime();
            UserDataCache cache = fixture.manager.getCache(fixture.uuid);
            AdvancedCoreUser user = fixture.plugin.getUserManager().getUser(fixture.uuid, false);
            when(fixture.plugin.getUserManager().getDataManager()).thenReturn(fixture.manager);
            when(user.getPlugin()).thenReturn(fixture.plugin);
            when(user.getUUID()).thenReturn(fixture.uuid.toString());
            when(user.isCached()).thenReturn(true);
            when(user.getCache()).thenReturn(cache);
            UserData data = new UserData(user);
            CountDownLatch writeStarted = new CountDownLatch(1), releaseWrite = new CountDownLatch(1);
            fixture.first.blockWrites(writeStarted, releaseWrite);
            cache.addChange(new UserDataChangeInt("Points", 7), true);
            var workers = Executors.newFixedThreadPool(2);
            try {
                var queued = workers.submit(fixture.tasks.get(0));
                await(writeStarted);
                var immediate = workers.submit(() -> data.setInt(UserStorage.MYSQL, "Points", 9, false, false));
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (cache.getCache().get("Points").getInt() != 9 && System.nanoTime() < deadline) {
                    Thread.yield();
                }
                assertEquals(9, cache.getCache().get("Points").getInt());
                releaseWrite.countDown();
                queued.get(5, TimeUnit.SECONDS);
                immediate.get(5, TimeUnit.SECONDS);
                assertEquals(9, fixture.first.points(fixture.uuid),
                        "the immediate write must reach storage after the older queued batch");
            } finally {
                releaseWrite.countDown();
                workers.shutdownNow();
                assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
            }
            runtime.close();
        }
    }

    @Test void immediateSharedWriteReportsOneChangeAfterPersistence() throws Exception {
        try (Fixture fixture = new Fixture()) {
            SharedUserDataRuntime runtime = fixture.runtime();
            UserDataCache cache = fixture.manager.getCache(fixture.uuid);
            AdvancedCoreUser user = fixture.plugin.getUserManager().getUser(fixture.uuid, false);
            when(fixture.plugin.getUserManager().getDataManager()).thenReturn(fixture.manager);
            when(user.getPlugin()).thenReturn(fixture.plugin);
            when(user.getUUID()).thenReturn(fixture.uuid.toString());
            when(user.isCached()).thenReturn(true);
            when(user.getCache()).thenReturn(cache);
            var userManager = fixture.plugin.getUserManager();
            clearInvocations(userManager);

            new UserData(user).setInt(UserStorage.MYSQL, "Points", 9, false, false);

            assertEquals(9, fixture.first.points(fixture.uuid));
            verify(userManager, times(1)).onChange(eq(user), any(String[].class));
            runtime.close();
        }
    }

    @Test void primaryThreadImmediateSharedWriteDefersWithoutLosingOrdering() throws Exception {
        try (Fixture fixture = new Fixture(); var bukkit = mockStatic(Bukkit.class)) {
            SharedUserDataRuntime runtime = fixture.runtime();
            UserDataCache cache = fixture.manager.getCache(fixture.uuid);
            AdvancedCoreUser user = fixture.plugin.getUserManager().getUser(fixture.uuid, false);
            when(fixture.plugin.getUserManager().getDataManager()).thenReturn(fixture.manager);
            when(user.getPlugin()).thenReturn(fixture.plugin);
            when(user.getUUID()).thenReturn(fixture.uuid.toString());
            when(user.isCached()).thenReturn(true);
            when(user.getCache()).thenReturn(cache);
            var userManager = fixture.plugin.getUserManager();
            clearInvocations(userManager);
            bukkit.when(Bukkit::getServer).thenReturn(mock(Server.class));
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true, true, false);

			UserData data = new UserData(user);
			assertDoesNotThrow(() -> data.setInt(UserStorage.MYSQL, "Points", 9, false, false));
			assertFalse(fixture.first.rows.containsKey(fixture.uuid));
			assertEquals(9, cache.snapshot().get("Points").getInt(),
					"the setter must publish read-after-write state before returning");
			data.setInt(UserStorage.MYSQL, "Points",
					data.getInt(UserStorage.MYSQL, "Points", 0, UserDataFetchMode.DEFAULT) + 1, false, false);
			assertEquals(10, cache.snapshot().get("Points").getInt(),
					"same-tick read-modify-write must observe the preceding setter");
			fixture.tasks.get(fixture.tasks.size() - 1).run();
			assertEquals(10, fixture.first.points(fixture.uuid));
            var callback = org.mockito.ArgumentCaptor.forClass(Runnable.class);
            verify(fixture.plugin.getBukkitScheduler()).runTask(eq(fixture.plugin), callback.capture());
            verify(userManager, never()).onChange(any(), any(String[].class));
            callback.getValue().run();
            verify(userManager, times(1)).onChange(eq(user), any(String[].class));
            runtime.close();
        }
    }

    @Test void immediateWriteToAnotherStoreDoesNotUseTheSharedWriter() throws Exception {
        try (Fixture fixture = new Fixture()) {
            SharedUserDataRuntime runtime = fixture.runtime();
            UserDataCache cache = fixture.manager.getCache(fixture.uuid);
            AdvancedCoreUser user = fixture.plugin.getUserManager().getUser(fixture.uuid, false);
            when(fixture.plugin.getUserManager().getDataManager()).thenReturn(fixture.manager);
            when(user.getPlugin()).thenReturn(fixture.plugin);
            when(user.getUUID()).thenReturn(fixture.uuid.toString());
            when(user.isCached()).thenReturn(true);
            when(user.getCache()).thenReturn(cache);

            assertThrows(IllegalStateException.class,
                    () -> new UserData(user).setInt(UserStorage.SQLITE, "Points", 9, false, false));
            assertFalse(fixture.first.rows.containsKey(fixture.uuid),
                    "an explicit alternate-store write must not be redirected to the shared backend");
            assertFalse(cache.getCache().containsKey("Points"),
                    "a rejected cross-store write must not leave an unpersisted shared-cache value behind");

            assertThrows(IllegalStateException.class,
                    () -> new UserData(user).setInt(UserStorage.SQLITE, "Queued", 11, true, false));
            assertFalse(cache.hasChangesToProcess(),
                    "a queued cross-store write must be rejected before it reaches the active shared writer");
            runtime.close();
        }
    }

	@Test void primaryThreadPendingPopulationDefersMutationIntoTheBoundGeneration() throws Exception {
        try (Fixture fixture = new Fixture(); var bukkit = mockStatic(Bukkit.class)) {
            SharedUserDataRuntime runtime = fixture.runtime();
            AdvancedCoreUser user = fixture.plugin.getUserManager().getUser(fixture.uuid, false);
            when(fixture.plugin.getUserManager().getDataManager()).thenReturn(fixture.manager);
            when(user.getPlugin()).thenReturn(fixture.plugin);
            when(user.getUUID()).thenReturn(fixture.uuid.toString());
            when(user.isCached()).thenAnswer(ignored -> fixture.manager.isCached(fixture.uuid));
            when(user.getCache()).thenAnswer(ignored -> fixture.manager.getCache(fixture.uuid));
            bukkit.when(Bukkit::getServer).thenReturn(mock(Server.class));
			bukkit.when(Bukkit::isPrimaryThread).thenReturn(true, true, true, true, false);

			assertDoesNotThrow(() -> new UserData(user).setInt(UserStorage.MYSQL, "Points", 9, true, false));
			assertFalse(fixture.first.rows.containsKey(fixture.uuid), "the primary thread must not write SQL");
			assertEquals(2, fixture.tasks.size(), "population must be queued before the mutation");
			assertFalse(fixture.manager.getUserDataCache().get(fixture.uuid).snapshot().containsKey("Points"),
					"an unbound placeholder must not accept a gate-bypassing mutation");

			fixture.tasks.get(0).run();
			assertFalse(fixture.manager.getUserDataCache().get(fixture.uuid).snapshot().containsKey("Points"));
			fixture.tasks.get(1).run();
			assertEquals(9, fixture.manager.getUserDataCache().get(fixture.uuid).getCache().get("Points").getInt(),
					"the admitted worker mutation must publish into the populated cache");
			assertEquals(3, fixture.tasks.size(), "the queued mutation must schedule its shared flush");
			fixture.tasks.get(2).run();
			assertEquals(9, fixture.first.points(fixture.uuid));
			fixture.assertNoLegacyWrites();
			runtime.close();
		}
	}

	@Test void implicitFacadeUsesTheRuntimeStorageAfterOptionsReload() throws Exception {
		try (Fixture fixture = new Fixture()) {
			SharedUserDataRuntime runtime = fixture.runtime();
			UserDataCache cache = fixture.manager.getCache(fixture.uuid);
			AdvancedCoreUser user = fixture.plugin.getUserManager().getUser(fixture.uuid, false);
			when(fixture.plugin.getUserManager().getDataManager()).thenReturn(fixture.manager);
			when(user.getPlugin()).thenReturn(fixture.plugin);
			when(user.getUUID()).thenReturn(fixture.uuid.toString());
			when(user.getCache()).thenReturn(cache);

			UserData data = new UserData(user);
			data.setInt("Points", 14, false);

			assertEquals(UserStorage.MYSQL,
					fixture.manager.effectiveStorageType(fixture.plugin.getStorageType()));
			assertEquals(14, fixture.first.points(fixture.uuid));
			assertEquals(14, data.getInt("Points", UserDataFetchMode.DEFAULT));
			assertEquals(14, data.getValues().get("Points").getInt());
			runtime.close();
		}
	}

	@Test void primaryThreadBulkSetterSnapshotsAndPublishesBeforeReturning() throws Exception {
		try (Fixture fixture = new Fixture(); var bukkit = mockStatic(Bukkit.class)) {
			SharedUserDataRuntime runtime = fixture.runtime();
			UserDataCache cache = fixture.manager.getCache(fixture.uuid);
			AdvancedCoreUser user = fixture.plugin.getUserManager().getUser(fixture.uuid, false);
			when(fixture.plugin.getUserManager().getDataManager()).thenReturn(fixture.manager);
			when(user.getPlugin()).thenReturn(fixture.plugin);
			when(user.getUUID()).thenReturn(fixture.uuid.toString());
			when(user.getCache()).thenReturn(cache);
			bukkit.when(Bukkit::getServer).thenReturn(mock(Server.class));
			bukkit.when(Bukkit::isPrimaryThread).thenReturn(true, false);
			HashMap<String, DataValue> submitted = new HashMap<>(Map.of("Points", new DataValueInt(18)));

			new UserData(user).setValues(submitted);

			assertEquals(18, cache.snapshot().get("Points").getInt());
			submitted.clear();
			fixture.tasks.get(fixture.tasks.size() - 1).run();
			assertEquals(18, fixture.first.points(fixture.uuid));
			runtime.close();
		}
	}

	@Test void workerMutationCannotBypassAPendingPrimaryThreadPopulation() throws Exception {
		try (Fixture fixture = new Fixture(); var bukkit = mockStatic(Bukkit.class)) {
			SharedUserDataRuntime runtime = fixture.runtime();
			AdvancedCoreUser user = fixture.plugin.getUserManager().getUser(fixture.uuid, false);
			when(fixture.plugin.getUserManager().getDataManager()).thenReturn(fixture.manager);
			when(user.getPlugin()).thenReturn(fixture.plugin);
			when(user.getUUID()).thenReturn(fixture.uuid.toString());
			when(user.getCache()).thenAnswer(ignored -> fixture.manager.getCache(fixture.uuid));
			bukkit.when(Bukkit::getServer).thenReturn(mock(Server.class));
			bukkit.when(Bukkit::isPrimaryThread).thenReturn(true, true, false);

			fixture.manager.getCache(fixture.uuid);
			new UserData(user).setInt(UserStorage.MYSQL, "Points", 12, true, false);

			assertFalse(fixture.first.rows.containsKey(fixture.uuid));
			fixture.tasks.get(0).run();
			assertEquals(12, fixture.first.points(fixture.uuid));
			assertEquals(12, fixture.manager.getUserDataCache().get(fixture.uuid).snapshot().get("Points").getInt());
			runtime.close();
		}
	}

    @Test void completedBatchReconcilesATemporaryCacheSnapshotWithoutPersistingTheSnapshot() throws Exception {
        try (Fixture fixture = new Fixture()) {
            SharedUserDataRuntime runtime = fixture.runtime();
            UserDataCache cache = fixture.manager.getCache(fixture.uuid);
            CountDownLatch writeStarted = new CountDownLatch(1), releaseWrite = new CountDownLatch(1);
            fixture.first.blockWrites(writeStarted, releaseWrite);
            cache.addChange(new UserDataChangeInt("Points", 7), true);
            AdvancedCoreUser user = fixture.plugin.getUserManager().getUser(fixture.uuid, false);
            when(user.isCached()).thenReturn(true);
            when(user.getCache()).thenReturn(cache);
            UserData data = new UserData(user);
            data.updateTempCacheWithColumns(new ArrayList<>(List.of(new Column("Points", new DataValueInt(3)))));
            var worker = Executors.newSingleThreadExecutor();
            try {
                var firstBatch = worker.submit(fixture.tasks.get(0));
                await(writeStarted);

                data.updateCacheWithTemp();
                assertEquals(3, cache.getCache().get("Points").getInt(),
                        "the temporary storage snapshot is visible while the earlier write is in flight");

                releaseWrite.countDown();
                firstBatch.get(5, TimeUnit.SECONDS);

                assertEquals(7, fixture.first.points(fixture.uuid));
                assertEquals(7, cache.getCache().get("Points").getInt(),
                        "the completed queued write must reconcile the stale temporary snapshot");
                assertFalse(cache.hasChangesToProcess(),
                        "a read snapshot must not become an automatic persistence request");
            } finally {
                releaseWrite.countDown();
                worker.shutdownNow();
                assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
            }
            runtime.close();
        }
    }

    @Test void temporarySnapshotCannotEraseANewerQueuedMutationFence() throws Exception {
        try (Fixture fixture = new Fixture()) {
            SharedUserDataRuntime runtime = fixture.runtime();
            UserDataCache cache = fixture.manager.getCache(fixture.uuid);
            CountDownLatch writeStarted = new CountDownLatch(1), releaseWrite = new CountDownLatch(1);
            fixture.first.blockWrites(writeStarted, releaseWrite);
            cache.addChange(new UserDataChangeInt("Points", 7), true);
            AdvancedCoreUser user = fixture.plugin.getUserManager().getUser(fixture.uuid, false);
            when(user.isCached()).thenReturn(true);
            when(user.getCache()).thenReturn(cache);
            UserData data = new UserData(user);
            data.updateTempCacheWithColumns(new ArrayList<>(List.of(new Column("Points", new DataValueInt(3)))));
            var worker = Executors.newSingleThreadExecutor();
            try {
                var firstBatch = worker.submit(fixture.tasks.get(0));
                await(writeStarted);
                cache.addChange(new UserDataChangeInt("Points", 9), true);
                data.updateCacheWithTemp();
                releaseWrite.countDown();
                firstBatch.get(5, TimeUnit.SECONDS);

                assertEquals(9, cache.getCache().get("Points").getInt(),
                        "the queued mutation must remain visible after the older batch completes");
                fixture.first.blockWrites(null, null);
                fixture.tasks.get(1).run();
                assertEquals(9, fixture.first.points(fixture.uuid));
            } finally {
                releaseWrite.countDown();
                worker.shutdownNow();
                assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
            }
            runtime.close();
        }
    }

    @Test void joinStyleCacheCreationAfterStartupUsesTheSelectedWriter() throws Exception {
        try (Fixture fixture = new Fixture()) {
            SharedUserDataRuntime runtime = fixture.runtime();
            fixture.manager.cacheUser(fixture.uuid, null);
            fixture.manager.getCache(fixture.uuid).addChange(new UserDataChangeInt("Points", 8), true);
            fixture.tasks.get(0).run();
            assertEquals(8, fixture.first.points(fixture.uuid));
            fixture.assertNoLegacyWrites();
            runtime.close();
        }
    }

    @Test void directCacheInsertionAfterStartupAlsoGetsTheGateAndWriter() throws Exception {
        try (Fixture fixture = new Fixture()) {
            SharedUserDataRuntime runtime = fixture.runtime();
            UserDataCache cache = new UserDataCache(fixture.manager, fixture.uuid);
            fixture.manager.getUserDataCache().put(fixture.uuid, cache);
            cache.addChange(new UserDataChangeInt("Points", 9), true);
            cache.processChanges();
            assertEquals(9, fixture.first.points(fixture.uuid));
            fixture.assertNoLegacyWrites();
            runtime.close();
        }
    }

    @Test void aWriterWithoutALifecycleGateDoesNotPreventSharedAttachment() throws Exception {
        try (Fixture fixture = new Fixture()) {
            UserDataCache cache = new UserDataCache(fixture.manager, fixture.uuid);
            cache.setSharedStorageWriter(values -> fail("ungated writer was reused"));
            SharedUserDataRuntime runtime = fixture.runtime();
            fixture.manager.getUserDataCache().put(fixture.uuid, cache);
            cache.addChange(new UserDataChangeInt("Points", 16), true);
            runtime.close();
            assertEquals(16, fixture.first.points(fixture.uuid));
            fixture.assertNoLegacyWrites();
        }
    }

    @Test void anAlreadyConstructedCacheCannotBypassBindingWhenInsertedLater() throws Exception {
        try (Fixture fixture = new Fixture()) {
            UserDataCache cache = new UserDataCache(fixture.manager, fixture.uuid);
            cache.addChange(new UserDataChangeInt("Points", 10), true);
            SharedUserDataRuntime runtime = fixture.runtime();
            fixture.manager.getUserDataCache().put(fixture.uuid, cache);
            fixture.tasks.get(0).run();
            assertEquals(10, fixture.first.points(fixture.uuid));
            fixture.assertNoLegacyWrites();
            runtime.close();
        }
    }

    @Test void cachesCreatedAfterReplacementDoNotReuseTheOldWriter() throws Exception {
        try (Fixture fixture = new Fixture()) {
            SharedUserDataRuntime runtime = fixture.runtime();
            UserDataCache old = fixture.manager.getCache(fixture.uuid);
            old.addChange(new UserDataChangeInt("Points", 11), true);
            MemoryBackend replacement = new MemoryBackend(UserStorage.SQLITE);
            runtime.replaceBackend(replacement);
            assertEquals(11, fixture.first.points(fixture.uuid));
            UserDataCache fresh = fixture.manager.getCache(fixture.uuid);
            assertNotSame(old, fresh);
            fresh.addChange(new UserDataChangeInt("Points", 12), true);
            fresh.processChanges();
            assertEquals(12, replacement.points(fixture.uuid));
            assertEquals(11, fixture.first.points(fixture.uuid));
            assertThrows(IllegalStateException.class, () -> old.addChange(new UserDataChangeInt("Points", 99), true));
            fixture.assertNoLegacyWrites();
            runtime.close();
        }
    }

    @Test void newlyInsertedCacheCannotQueueAfterRetirementStarts() throws Exception {
        try (Fixture fixture = new Fixture()) {
            SharedUserDataRuntime runtime = fixture.runtime();
            List<Runnable> worker = new ArrayList<>();
            CompletableFuture<Void> closing = runtime.closeAsync(worker::add).toCompletableFuture();
            UserDataCache late = new UserDataCache(fixture.manager, fixture.uuid);
            fixture.manager.getUserDataCache().put(fixture.uuid, late);
            assertThrows(IllegalStateException.class, () -> late.addChange(new UserDataChangeInt("Points", 99), true));
            worker.get(0).run();
            closing.join();
            assertThrows(IllegalStateException.class, () -> late.addChange(new UserDataChangeInt("Points", 99), true));
        }
    }

    @Test void legacyMonitorHeldDuringLazyAttachmentFailsWithoutDeadlocking() throws Exception {
        try (Fixture fixture = new Fixture()) {
            UserDataCache cache = new UserDataCache(fixture.manager, fixture.uuid);
            cache.addChange(new UserDataChangeInt("Points", 15), true);
            SharedUserDataRuntime runtime = fixture.runtime();
            fixture.manager.getUserDataCache().put(fixture.uuid, cache);
            synchronized (cache) { assertThrows(IllegalStateException.class, cache::processChanges); }
            assertTrue(cache.hasChangesToProcess());
            runtime.close();
            assertEquals(15, fixture.first.points(fixture.uuid));
        }
    }

    @Test void aFailedAttachmentLeavesTheSameOwnerReusable() throws Exception {
        try (Fixture fixture = new Fixture()) {
            IllegalStateException unavailable = new IllegalStateException("registration unavailable");
            doThrow(unavailable).doCallRealMethod().when(fixture.manager).bindSharedCacheInitializer(any());
            assertSame(unavailable, assertThrows(IllegalStateException.class, fixture::runtime));
            SharedUserDataRuntime retry = fixture.runtime();
            UserDataCache cache = fixture.manager.getCache(fixture.uuid);
            cache.addChange(new UserDataChangeInt("Points", 13), true);
            retry.close();
            assertEquals(13, fixture.first.points(fixture.uuid));
            fixture.assertNoLegacyWrites();
        }
    }

    @Test void activeLegacyBatchRejectsAttachmentWithoutPublishingAndRetrySucceeds() throws Exception {
        try (Fixture fixture = new Fixture()) {
            UserDataCache cache = fixture.manager.getCache(fixture.uuid);
            cache.addChange(new UserDataChangeInt("Points", 1), true);
            CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
            var legacyData = cache.getUser().getUserData();
            doAnswer(call -> { entered.countDown(); await(release); return null; }).when(legacyData).setValues(any(HashMap.class));
            var legacyWorker = Executors.newSingleThreadExecutor();
            try {
                var legacy = legacyWorker.submit(cache::processChanges);
                await(entered);
                assertThrows(IllegalStateException.class, fixture::runtime);
                release.countDown();
                legacy.get(5, TimeUnit.SECONDS);
                SharedUserDataRuntime runtime = fixture.runtime();
                cache.addChange(new UserDataChangeInt("Points", 14), true);
                runtime.close();
                assertEquals(14, fixture.first.points(fixture.uuid));
            } finally {
                release.countDown();
                legacyWorker.shutdownNow();
                assertTrue(legacyWorker.awaitTermination(5, TimeUnit.SECONDS));
            }
        }
    }

    @Test void legacyRemovalUsesTheExclusiveSharedAdmission() {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        UserDataManager manager = new UserDataManager(plugin);
        manager.getTimer().shutdownNow();
        UUID uuid = UUID.randomUUID();
        UserDataCache cache = new UserDataCache(manager, uuid);
        cache.updateCache(new HashMap<>(Map.of("Points", new DataValueInt(1))));
        manager.getUserDataCache().put(uuid, cache);
        SqlUserBackend backend = mock(SqlUserBackend.class);
        int[] exclusiveCalls = { 0 };
        manager.bindSharedSqlBackend(backend,
                (id, operation) -> fail("legacy removal must not use shared read admission"),
                (id, operation) -> { exclusiveCalls[0]++; operation.run(); });

        manager.removeCache(uuid, null);

        assertEquals(1, exclusiveCalls[0]);
        assertFalse(manager.containsKey(uuid));
    }

    @Test void legacyRemovalRetiresTheSharedCacheBeforeDetachingIt() throws Exception {
        try (Fixture fixture = new Fixture()) {
            SharedUserDataRuntime runtime = fixture.runtime();
            UserDataCache cache = fixture.manager.getCache(fixture.uuid);

            fixture.manager.removeCache(fixture.uuid, null);

            assertFalse(fixture.manager.containsKey(fixture.uuid));
            assertThrows(IllegalStateException.class,
                    () -> cache.addChange(new UserDataChangeInt("Points", 2), true));
            runtime.close();
        }
    }

    @Test void managerDrivenRemovalEvictsTheOwnersPerUserGate() throws Exception {
        try (Fixture fixture = new Fixture()) {
            SharedUserDataRuntime runtime = fixture.runtime();
            fixture.manager.getCache(fixture.uuid);
            Field gatesField = BukkitUserCacheOwner.class.getDeclaredField("cacheGates");
            gatesField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<UUID, ?> gates = (Map<UUID, ?>) gatesField.get(fixture.owner);
            assertTrue(gates.containsKey(fixture.uuid));

            fixture.manager.removeCache(fixture.uuid, null);

            assertFalse(gates.containsKey(fixture.uuid));
            runtime.close();
        }
    }

    @Test void managerWideClearDoesNotHoldMapAdmissionWhileSharedFlushWaits() throws Exception {
        try (Fixture fixture = new Fixture()) {
            SharedUserDataRuntime runtime = fixture.runtime();
            UserDataCache cache = fixture.manager.getCache(fixture.uuid);
            CountDownLatch writeStarted = new CountDownLatch(1), releaseWrite = new CountDownLatch(1);
            fixture.first.blockWrites(writeStarted, releaseWrite);
            cache.addChange(new UserDataChangeInt("Points", 18), true);
            var workers = Executors.newFixedThreadPool(2);
            try {
                var clearing = workers.submit(fixture.manager::clearCache);
                await(writeStarted);
                // A shared cache flush is in progress. A population already admitted by
                // the runtime must still be able to take the cache-map read admission.
                assertTrue(workers.submit(() -> fixture.manager.withCacheMapReadAdmission(() -> Boolean.TRUE))
                        .get(200, TimeUnit.MILLISECONDS));
                releaseWrite.countDown();
                clearing.get(5, TimeUnit.SECONDS);
            } finally {
                releaseWrite.countDown();
                workers.shutdownNow();
                assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
            }
            runtime.close();
        }
    }

    @Test void managerWideClearExcludesQueuedWritesUntilTheCacheIsDetached() throws Exception {
        try (Fixture fixture = new Fixture()) {
            SharedUserDataRuntime runtime = fixture.runtime();
            UserDataCache cache = fixture.manager.getCache(fixture.uuid);
            CountDownLatch writeStarted = new CountDownLatch(1), releaseWrite = new CountDownLatch(1);
            fixture.first.blockWrites(writeStarted, releaseWrite);
            cache.addChange(new UserDataChangeInt("Points", 18), true);
            var workers = Executors.newFixedThreadPool(2);
            try {
                var clearing = workers.submit(fixture.manager::clearCache);
                await(writeStarted);
                var concurrentWrite = workers.submit(() ->
                        cache.addChange(new UserDataChangeInt("Points", 19), true));
                assertThrows(java.util.concurrent.TimeoutException.class,
                        () -> concurrentWrite.get(200, TimeUnit.MILLISECONDS),
                        "a write must wait for the exclusive retirement instead of being silently dropped");
                releaseWrite.countDown();
                clearing.get(5, TimeUnit.SECONDS);
                assertThrows(java.util.concurrent.ExecutionException.class,
                        () -> concurrentWrite.get(5, TimeUnit.SECONDS));
                assertFalse(fixture.manager.containsKey(fixture.uuid));
                assertEquals(18, fixture.first.points(fixture.uuid));
            } finally {
                releaseWrite.countDown();
                workers.shutdownNow();
                assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
            }
            runtime.close();
        }
    }

    @Test void failedManagerWideClearReopensEveryStillMappedCache() {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class, RETURNS_DEEP_STUBS);
        when(plugin.getNativeUserStorageOwner()).thenReturn(null);
        UserDataManager manager = new UserDataManager(plugin);
        manager.getTimer().shutdownNow();
        try {
            UserDataCache first = failingCache(manager, UUID.randomUUID());
            UserDataCache second = failingCache(manager, UUID.randomUUID());
            manager.getUserDataCache().put(first.getUuid(), first);
            manager.getUserDataCache().put(second.getUuid(), second);
            manager.bindSharedSqlBackend(mock(SqlUserBackend.class), (uuid, operation) -> operation.run());

            assertThrows(IllegalStateException.class, manager::clearCache);

            first.setSharedStorageWriter(values -> {});
            second.setSharedStorageWriter(values -> {});
            first.addChange(new UserDataChangeInt("Retry", 1), true);
            second.addChange(new UserDataChangeInt("Retry", 1), true);
            assertTrue(first.getCache().containsKey("Retry"));
            assertTrue(second.getCache().containsKey("Retry"));
        } finally {
            manager.getTimer().shutdownNow();
        }
    }

    @Test void failedPerUserRemovalReopensTheMappedCache() {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class, RETURNS_DEEP_STUBS);
        when(plugin.getNativeUserStorageOwner()).thenReturn(null);
        UserDataManager manager = new UserDataManager(plugin);
        manager.getTimer().shutdownNow();
        try {
            UserDataCache cache = failingCache(manager, UUID.randomUUID());
            manager.getUserDataCache().put(cache.getUuid(), cache);
            manager.bindSharedSqlBackend(mock(SqlUserBackend.class), (uuid, operation) -> operation.run());

            assertThrows(IllegalStateException.class, () -> manager.removeCache(cache.getUuid(), null));

            cache.setSharedStorageWriter(values -> {});
            cache.addChange(new UserDataChangeInt("Retry", 1), true);
            assertTrue(cache.getCache().containsKey("Retry"));
        } finally {
            manager.getTimer().shutdownNow();
        }
    }

    @Test void refreshFlushCallbackCanRemoveTheSameUserAfterSharedAdmission() throws Exception {
        try (Fixture fixture = new Fixture()) {
            SharedUserDataRuntime runtime = fixture.runtime();
            UserDataCache cache = fixture.manager.getCache(fixture.uuid);
            cache.addChange(new UserDataChangeInt("Points", 21), true);
            var userManager = fixture.plugin.getUserManager();
            doAnswer(call -> {
                fixture.manager.removeCache(fixture.uuid, null);
                return null;
            }).when(userManager).onChange(any(AdvancedCoreUser.class), any(String[].class));

            assertDoesNotThrow(() -> fixture.manager.cacheUser(fixture.uuid, null));

            assertEquals(21, fixture.first.points(fixture.uuid));
            assertFalse(fixture.manager.containsKey(fixture.uuid));
            Field completedField = UserDataManager.class.getDeclaredField("completedSharedCachePopulations");
            completedField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Set<UUID> completed = (Set<UUID>) completedField.get(fixture.manager);
            assertFalse(completed.contains(fixture.uuid),
                    "a callback-driven removal must not be followed by a stale completed-population marker");
            runtime.close();
        }
    }

    private UserDataCache failingCache(UserDataManager manager, UUID uuid) {
        UserDataCache cache = new UserDataCache(manager, uuid);
        cache.updateCache(new HashMap<>(Map.of("Points", new DataValueInt(1))));
        cache.setSharedStorageWriter(values -> { throw new IllegalStateException("write failed"); });
        cache.addChange(new UserDataChangeInt("Points", 2), true);
        return cache;
    }

    private static void await(CountDownLatch latch) throws InterruptedException { assertTrue(latch.await(5, TimeUnit.SECONDS)); }

    private static final class Fixture implements AutoCloseable {
        final UUID uuid = UUID.randomUUID();
        final AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class, RETURNS_DEEP_STUBS);
        final UserDataManager manager;
        final BukkitUserCacheOwner owner;
        final MemoryBackend first = new MemoryBackend(UserStorage.MYSQL);
        final List<Runnable> tasks = new CopyOnWriteArrayList<>();
        final ScheduledExecutorService timer = mock(ScheduledExecutorService.class);

        Fixture() throws Exception {
			when(plugin.isEnabled()).thenReturn(true);
            when(plugin.getNativeUserStorageOwner()).thenReturn(null);
            when(plugin.getStorageType()).thenReturn(UserStorage.SQLITE);
            manager = spy(new UserDataManager(plugin));
            manager.getTimer().shutdownNow();
            Field field = UserDataManager.class.getDeclaredField("timer");
            field.setAccessible(true);
            field.set(manager, timer);
            when(timer.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class))).thenAnswer(call -> {
                tasks.add(call.getArgument(0, Runnable.class));
                return mock(ScheduledFuture.class);
            });
            doAnswer(call -> {
                tasks.add(call.getArgument(0, Runnable.class));
                return null;
            }).when(timer).execute(any(Runnable.class));
            when(plugin.getTimer()).thenReturn(timer);
            var data = plugin.getUserManager().getUser(uuid, false).getUserData();
            when(data.getKeys()).thenAnswer(ignored -> new ArrayList<String>());
            when(data.getValues()).thenAnswer(ignored -> new HashMap<String, DataValue>());
            owner = new BukkitUserCacheOwner(manager);
        }

        SharedUserDataRuntime runtime() { return new SharedUserDataRuntime(first, owner); }
        void assertNoLegacyWrites() { verify(plugin.getUserManager().getUser(uuid, false).getUserData(), never()).setValues(any(HashMap.class)); }
        public void close() { manager.getTimer().shutdownNow(); }
    }

    private static final class MemoryBackend implements SqlUserBackend {
        final UserStorage type;
        final Map<UUID, HashMap<String, DataValue>> rows = new ConcurrentHashMap<>();
        volatile CountDownLatch writeStarted;
        volatile CountDownLatch releaseWrite;
        boolean open = true;
        MemoryBackend(UserStorage type) { this.type = type; }
        void blockWrites(CountDownLatch started, CountDownLatch release) {
            writeStarted = started;
            releaseWrite = release;
        }
        int points(UUID uuid) { return rows.get(uuid).get("Points").getInt(); }
        public UserStorage storageType() { return type; }
        public boolean isOpen() { return open; }
        public void close() { open = false; }
        public List<UUID> enumerateUsers() { return new ArrayList<>(rows.keySet()); }
        public SqlUserStorage user(UUID uuid) {
            if (!open) throw new IllegalStateException("closed backend");
            return new SqlUserStorage() {
                public List<Column> readRow(UserStorage requested) {
                    List<Column> columns = new ArrayList<>();
                    rows.getOrDefault(uuid, new HashMap<>()).forEach((key, value) -> columns.add(new Column(key, value)));
                    return columns;
                }
                public boolean contains(UserStorage requested) { return rows.containsKey(uuid); }
                public void delete(UserStorage requested) { rows.remove(uuid); }
                public void write(UserStorage requested, String key, DataValue value) { writeValues(requested, new HashMap<>(Map.of(key, value))); }
                public void writeValues(UserStorage requested, HashMap<String, DataValue> values) {
                    assertEquals(type, requested);
                    if (!open) throw new IllegalStateException("closed backend");
                    CountDownLatch started = writeStarted;
                    CountDownLatch release = releaseWrite;
                    if (started != null && release != null) {
                        started.countDown();
                        try { await(release); }
                        catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(interrupted);
                        }
                    }
                    rows.computeIfAbsent(uuid, ignored -> new HashMap<>()).putAll(values);
                }
            };
        }
    }
}
