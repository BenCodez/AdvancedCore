package com.bencodez.advancedcore.tests.user;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

import com.bencodez.advancedcore.AdvancedCorePlugin;
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
    @Test void noDatabaseLookupMissDoesNotLoadOrPopulate() { cacheMiss(UserDataFetchMode.NO_DB_LOOKUP); }
    @Test void cacheOnlyMissDoesNotLoadOrPopulate() { cacheMiss(UserDataFetchMode.CACHE_ONLY); }
    @Test void temporaryOnlyMissDoesNotLoadOrPopulate() { cacheMiss(UserDataFetchMode.TEMP_ONLY); }

    @Test void legacyCachePopulationPublishesOnlyAfterSharedAdmission() throws Exception {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class, RETURNS_DEEP_STUBS);
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

    private static void await(CountDownLatch latch) throws InterruptedException { assertTrue(latch.await(5, TimeUnit.SECONDS)); }

    private static final class Fixture implements AutoCloseable {
        final UUID uuid = UUID.randomUUID();
        final AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class, RETURNS_DEEP_STUBS);
        final UserDataManager manager;
        final BukkitUserCacheOwner owner;
        final MemoryBackend first = new MemoryBackend(UserStorage.MYSQL);
        final List<Runnable> tasks = new CopyOnWriteArrayList<>();

        Fixture() throws Exception {
            when(plugin.getStorageType()).thenReturn(UserStorage.SQLITE);
            manager = spy(new UserDataManager(plugin));
            manager.getTimer().shutdownNow();
            ScheduledExecutorService timer = mock(ScheduledExecutorService.class);
            Field field = UserDataManager.class.getDeclaredField("timer");
            field.setAccessible(true);
            field.set(manager, timer);
            when(timer.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class))).thenAnswer(call -> {
                tasks.add(call.getArgument(0, Runnable.class));
                return mock(ScheduledFuture.class);
            });
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
        boolean open = true;
        MemoryBackend(UserStorage type) { this.type = type; }
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
                    rows.computeIfAbsent(uuid, ignored -> new HashMap<>()).putAll(values);
                }
            };
        }
    }
}
