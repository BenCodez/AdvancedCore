package com.bencodez.advancedcore.tests.user;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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
import com.bencodez.advancedcore.core.user.storage.SqlUserStorage;
import com.bencodez.advancedcore.core.user.storage.sql.SqlUserBackend;
import com.bencodez.simpleapi.sql.Column;
import com.bencodez.simpleapi.sql.data.DataValue;
import com.bencodez.simpleapi.sql.data.DataValueInt;

/** Real cache/queue and runtime; controlled in-memory provider, no live server/database. */
@Timeout(15)
class SharedCachePopulationRaceTest {
    @Test void pendingWriteWinsOverAnOlderDatabaseSnapshot() throws Exception { queuedWrite(false); }
    @Test void writeFlushedBeforePublicationStillWinsOverAnOlderSnapshot() throws Exception { queuedWrite(true); }

    private void queuedWrite(boolean flushFirst) throws Exception {
        try (Fixture fixture = new Fixture()) {
            var population = fixture.worker.submit(() -> fixture.runtime.populate(fixture.uuid));
            await(fixture.backend.snapshotRead);
            // This must finish while the storage load is still held on another thread.
            fixture.runtime.queueChange(fixture.uuid, "Points", new DataValueInt(7));
            if (flushFirst) {
                fixture.runtime.flush(fixture.uuid);
                assertFalse(fixture.cache.hasChangesToProcess());
                assertEquals(7, fixture.backend.rows.get("Points").getInt());
            }
            fixture.backend.releaseRead.countDown();
            HashMap<String, DataValue> published = population.get(5, TimeUnit.SECONDS);
            assertEquals(7, published.get("Points").getInt());
            assertEquals(2, published.get("Other").getInt(), "unmodified fields still refresh from storage");
            assertEquals(7, fixture.cached("Points"));
            assertEquals(2, fixture.cached("Other"));
            fixture.runtime.flush(fixture.uuid);
            assertEquals(7, fixture.backend.rows.get("Points").getInt());
            assertEquals(7, fixture.cached("Points"));
            fixture.noLegacyWrites();
        }
    }

    @Test void memoryOnlyMutationDuringALoadIsNotDiscarded() throws Exception {
        try (Fixture fixture = new Fixture()) {
            var population = fixture.worker.submit(() -> fixture.runtime.populate(fixture.uuid));
            await(fixture.backend.snapshotRead);
            fixture.cache.addChange(new UserDataChangeInt("Points", 8), false);
            fixture.backend.releaseRead.countDown();
            assertEquals(8, population.get(5, TimeUnit.SECONDS).get("Points").getInt());
            assertEquals(8, fixture.cached("Points"));
            assertFalse(fixture.cache.hasChangesToProcess());
            assertEquals(1, fixture.backend.rows.get("Points").getInt());
        }
    }

    @Test void aLaterFullCacheRefreshWinsOverAnOlderLoad() throws Exception {
        try (Fixture fixture = new Fixture()) {
            var population = fixture.worker.submit(() -> fixture.runtime.populate(fixture.uuid));
            await(fixture.backend.snapshotRead);
            fixture.cache.updateCache(values(9, 10));
            fixture.backend.releaseRead.countDown();
            assertEquals(9, population.get(5, TimeUnit.SECONDS).get("Points").getInt());
            assertEquals(9, fixture.cached("Points"));
            assertEquals(10, fixture.cached("Other"));
        }
    }

    @Test void aReplacedCacheDoesNotReceiveAnOlderInstancesLoad() throws Exception {
        try (Fixture fixture = new Fixture()) {
            var population = fixture.worker.submit(() -> fixture.runtime.populate(fixture.uuid));
            await(fixture.backend.snapshotRead);
            UserDataCache replacement = new UserDataCache(fixture.manager, fixture.uuid);
            replacement.updateCache(values(11, 12));
            fixture.caches.put(fixture.uuid, replacement);
            fixture.backend.releaseRead.countDown();
            assertInstanceOf(IllegalStateException.class,
                    assertThrows(ExecutionException.class, () -> population.get(5, TimeUnit.SECONDS)).getCause());
            assertSame(replacement, fixture.caches.get(fixture.uuid));
            assertEquals(11, fixture.cached("Points"));
            assertEquals(12, fixture.cached("Other"));
        }
    }

    @Test void failedPopulationRetainsQueuedChangesAndCanBeRetried() throws Exception {
        try (Fixture fixture = new Fixture()) {
            IllegalStateException failure = new IllegalStateException("storage read failed");
            fixture.backend.readFailure = failure;
            var population = fixture.worker.submit(() -> fixture.runtime.populate(fixture.uuid));
            await(fixture.backend.snapshotRead);
            fixture.runtime.queueChange(fixture.uuid, "Points", new DataValueInt(13));
            fixture.backend.releaseRead.countDown();
            assertSame(failure,
                    assertThrows(ExecutionException.class, () -> population.get(5, TimeUnit.SECONDS)).getCause());
            assertEquals(13, fixture.cached("Points"));
            assertTrue(fixture.cache.hasChangesToProcess());
            fixture.backend.readFailure = null;
            assertEquals(13, fixture.runtime.populate(fixture.uuid).get("Points").getInt());
            assertEquals(13, fixture.backend.rows.get("Points").getInt());
            assertFalse(fixture.cache.hasChangesToProcess());
        }
    }

    @Test void twoOverlappingPopulationsCannotPublishInReverseOrder() throws Exception {
        try (Fixture fixture = new Fixture()) {
            var first = fixture.owner.beginPopulation(fixture.uuid);
            var second = fixture.owner.beginPopulation(fixture.uuid);
            fixture.owner.completePopulation(fixture.uuid, values(14, 15), second);
            var result = fixture.owner.completePopulation(fixture.uuid, values(1, 2), first);
            assertEquals(14, result.get("Points").getInt());
            assertEquals(15, fixture.cached("Other"));
        }
    }

    @Test void populationTokenCannotBeAppliedToAnotherUser() throws Exception {
        try (Fixture fixture = new Fixture()) {
            var token = fixture.owner.beginPopulation(fixture.uuid);
            assertThrows(IllegalArgumentException.class, () ->
                    fixture.owner.completePopulation(UUID.randomUUID(), values(99, 99), token));
            assertEquals(1, fixture.cached("Points"));
        }
    }

    private static HashMap<String, DataValue> values(int points, int other) {
        return new HashMap<>(Map.of("Points", new DataValueInt(points), "Other", new DataValueInt(other)));
    }

    private static void await(CountDownLatch latch) throws InterruptedException {
        assertTrue(latch.await(5, TimeUnit.SECONDS), "storage snapshot did not reach the expected boundary");
    }

    private static final class Fixture implements AutoCloseable {
        final UUID uuid = UUID.randomUUID();
        final AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class, RETURNS_DEEP_STUBS);
        final UserDataManager manager = mock(UserDataManager.class, RETURNS_DEEP_STUBS);
        final ConcurrentHashMap<UUID, UserDataCache> caches = new ConcurrentHashMap<>();
        final UserDataCache cache = new UserDataCache(manager, uuid);
        final MemoryBackend backend = new MemoryBackend(uuid);
        final BukkitUserCacheOwner owner;
        final SharedUserDataRuntime runtime;
        final ExecutorService worker = Executors.newSingleThreadExecutor();

        Fixture() {
            when(manager.getPlugin()).thenReturn(plugin);
            when(manager.getUserDataCache()).thenReturn(caches);
            when(manager.isCached(any(UUID.class))).thenAnswer(call -> {
                UserDataCache current = caches.get(call.getArgument(0, UUID.class));
                return current != null && current.hasCache();
            });
            cache.updateCache(values(1, 1));
            caches.put(uuid, cache);
            owner = new BukkitUserCacheOwner(manager);
            runtime = new SharedUserDataRuntime(backend, owner);
        }
        int cached(String key) {
            return runtime.read(uuid, key, UserDataFetchMode.CACHE_ONLY, null, null).getInt();
        }
        void noLegacyWrites() {
            verify(plugin.getUserManager().getUser(uuid, false).getUserData(), never()).setValues(any(HashMap.class));
        }
        public void close() throws Exception {
            backend.releaseRead.countDown();
            worker.shutdownNow();
            assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
            runtime.close();
        }
    }

    private static final class MemoryBackend implements SqlUserBackend {
        final UUID uuid;
        final Map<String, DataValue> rows = new ConcurrentHashMap<>(values(1, 2));
        final CountDownLatch snapshotRead = new CountDownLatch(1), releaseRead = new CountDownLatch(1);
        final AtomicBoolean holdFirstRead = new AtomicBoolean(true);
        volatile RuntimeException readFailure;
        boolean open = true;
        MemoryBackend(UUID uuid) { this.uuid = uuid; }
        public UserStorage storageType() { return UserStorage.SQLITE; }
        public boolean isOpen() { return open; }
        public void close() { open = false; }
        public List<UUID> enumerateUsers() { return List.of(uuid); }
        public SqlUserStorage user(UUID id) {
            assertEquals(uuid, id);
            return new SqlUserStorage() {
                public List<Column> readRow(UserStorage type) {
                    List<Column> snapshot = new ArrayList<>();
                    rows.forEach((key, value) -> snapshot.add(new Column(key, value)));
                    if (holdFirstRead.compareAndSet(true, false)) {
                        snapshotRead.countDown();
                        try { await(releaseRead); }
                        catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(interrupted);
                        }
                    }
                    if (readFailure != null) throw readFailure;
                    return snapshot;
                }
                public boolean contains(UserStorage type) { return !rows.isEmpty(); }
                public void delete(UserStorage type) { rows.clear(); }
                public void write(UserStorage type, String key, DataValue value) { rows.put(key, value); }
                public void writeValues(UserStorage type, HashMap<String, DataValue> values) {
                    assertTrue(open, "write after provider close");
                    rows.putAll(values);
                }
            };
        }
    }
}
