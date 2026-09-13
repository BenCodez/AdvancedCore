package com.bencodez.advancedcore.tests.user;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
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

/** Memory providers plus real cache/queue code; not live database or game-server integration. */
@Timeout(15)
class SharedUserLifecycleRegressionTest {
    @Test
    void closeDrainsAdmittedQueueBeforeTeardownAndRejectsLateWrites() throws Exception {
        UUID uuid = UUID.randomUUID();
        UserCacheOwner owner = mock(UserCacheOwner.class);
        SqlUserBackend backend = mock(SqlUserBackend.class);
        SqlUserStorage storage = mock(SqlUserStorage.class);
        when(backend.isOpen()).thenReturn(true);
        when(backend.storageType()).thenReturn(UserStorage.SQLITE);
        when(backend.user(uuid)).thenReturn(storage);
        when(owner.isCached(uuid)).thenReturn(true);
        when(owner.cachedUsers()).thenReturn(Set.of(uuid));
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        doAnswer(call -> { entered.countDown(); await(release); return null; })
                .when(owner).queueChange(eq(uuid), anyString(), any());
        SharedUserDataRuntime runtime = new SharedUserDataRuntime(backend, owner);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<?> write = workers.submit(() -> runtime.queueChange(uuid, "Points", new DataValueInt(5)));
            await(entered);
            CompletableFuture<Void> closed = runtime.closeAsync(workers).toCompletableFuture();
            assertTrue(runtime.isRetiring());
            assertFalse(closed.isDone());
            assertThrows(IllegalStateException.class,
                    () -> runtime.queueChange(uuid, "Points", new DataValueInt(9)));
            verify(owner, never()).clearAfterFlush();
            release.countDown();
            write.get(5, TimeUnit.SECONDS);
            closed.get(5, TimeUnit.SECONDS);
            var order = inOrder(owner, backend);
            order.verify(owner).flush(uuid, UserStorage.SQLITE, storage);
            order.verify(owner).clearAfterFlush();
            order.verify(owner).shutdown();
            order.verify(backend).close();
        } finally {
            release.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void rejectedAsyncCloseKeepsPendingChangesForAnObservedRetry() {
        Fixture fixture = new Fixture();
        SharedUserDataRuntime runtime = fixture.runtime();
        runtime.queueChange(fixture.uuid, "Points", new DataValueInt(6));
        var result = runtime.closeAsync(task -> { throw new RejectedExecutionException("stopped"); });
        assertThrows(CompletionException.class, () -> result.toCompletableFuture().join());
        assertTrue(runtime.isRetiring());
        assertTrue(fixture.first.isOpen());
        assertTrue(fixture.caches.get(fixture.uuid).hasChangesToProcess());
        runtime.close();
        assertEquals(6, fixture.first.points(fixture.uuid));
    }

    @Test
    void failedFlushKeepsQueueAndProviderButDoesNotReopenAdmission() {
        Fixture fixture = new Fixture();
        SharedUserDataRuntime runtime = fixture.runtime();
        runtime.queueChange(fixture.uuid, "Points", new DataValueInt(6));
        fixture.first.beforeWrite = () -> { throw new IllegalStateException("write failed"); };
        assertThrows(IllegalStateException.class, runtime::close);
        assertTrue(fixture.first.isOpen());
        assertFalse(runtime.isClosed());
        assertTrue(fixture.caches.get(fixture.uuid).hasChangesToProcess());
        assertThrows(IllegalStateException.class,
                () -> runtime.queueChange(fixture.uuid, "Points", new DataValueInt(9)));
        fixture.first.beforeWrite = () -> {};
        runtime.close();
        assertEquals(6, fixture.first.points(fixture.uuid));
    }

    @Test
    void explicitAndScheduledFlushesFollowReplacementRatherThanPluginSettings() {
        Fixture fixture = new Fixture();
        SharedUserDataRuntime runtime = fixture.runtime();
        runtime.queueChange(fixture.uuid, "Points", new DataValueInt(5));
        fixture.tasks.get(0).run();
        assertEquals(5, fixture.first.points(fixture.uuid));
        UserDataCache oldCache = fixture.caches.get(fixture.uuid);
        runtime.queueChange(fixture.uuid, "Points", new DataValueInt(7));
        MemoryBackend second = new MemoryBackend(UserStorage.MYSQL);
        runtime.replaceBackend(second);
        assertEquals(7, fixture.first.points(fixture.uuid));
        assertFalse(fixture.first.isOpen());
        runtime.queueChange(fixture.uuid, "Points", new DataValueInt(11));
        for (Runnable task : List.copyOf(fixture.tasks)) task.run();
        assertEquals(11, second.points(fixture.uuid));
        assertEquals(7, fixture.first.points(fixture.uuid));
        assertThrows(IllegalStateException.class,
                () -> oldCache.addChange(new UserDataChangeInt("Points", 99), true));
        verify(fixture.plugin.getUserManager().getUser(fixture.uuid, false).getUserData(), never())
                .setValues(any(HashMap.class));
        runtime.close();
    }

    @Test
    void sameBackendReplacementDoesNotCloseItOrDiscardPendingWrites() {
        Fixture fixture = new Fixture();
        SharedUserDataRuntime runtime = fixture.runtime();
        runtime.queueChange(fixture.uuid, "Points", new DataValueInt(4));
        runtime.replaceBackend(fixture.first);
        assertTrue(fixture.first.isOpen());
        assertTrue(fixture.caches.get(fixture.uuid).hasChangesToProcess());
        runtime.close();
        assertEquals(4, fixture.first.points(fixture.uuid));
    }

    @Test
    void scheduledBatchAndNotificationAreInsideTheShutdownBarrier() throws Exception {
        Fixture fixture = new Fixture();
        SharedUserDataRuntime runtime = fixture.runtime();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicBoolean firstWrite = new AtomicBoolean(true), firstNotice = new AtomicBoolean(true);
        fixture.first.beforeWrite = () -> {
            if (firstWrite.getAndSet(false)) { entered.countDown(); await(release); }
        };
        var userManager = fixture.plugin.getUserManager();
        doAnswer(call -> {
            if (firstNotice.getAndSet(false)) runtime.queueChange(fixture.uuid, "Points", new DataValueInt(8));
            return null;
        }).when(userManager).onChange(any(AdvancedCoreUser.class), any(String[].class));
        runtime.queueChange(fixture.uuid, "Points", new DataValueInt(3));
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<?> scheduled = workers.submit(fixture.tasks.get(0));
            await(entered);
            CompletableFuture<Void> closed = runtime.closeAsync(workers).toCompletableFuture();
            assertFalse(closed.isDone());
            release.countDown();
            scheduled.get(5, TimeUnit.SECONDS);
            closed.get(5, TimeUnit.SECONDS);
            assertEquals(8, fixture.first.points(fixture.uuid));
            assertTrue(runtime.isClosed());
        } finally {
            release.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void blockingCloseIsRejectedOnServerThreadButAsyncCloseDoesNotWaitThere() {
        Fixture fixture = new Fixture();
        SharedUserDataRuntime runtime = fixture.runtime();
        List<Runnable> workerQueue = new ArrayList<>();
        CompletionStage<Void> closed;
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getServer).thenReturn(mock(Server.class));
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            assertThrows(IllegalStateException.class, runtime::close);
            assertFalse(runtime.isRetiring());
            closed = runtime.closeAsync(workerQueue::add);
            assertFalse(closed.toCompletableFuture().isDone());
            assertEquals(1, workerQueue.size());
        }
        workerQueue.get(0).run();
        closed.toCompletableFuture().join();
        assertTrue(runtime.isClosed());
    }

    @Test
    void directWritesToTheBoundCacheAlsoRejectRetirement() {
        Fixture fixture = new Fixture();
        SharedUserDataRuntime runtime = fixture.runtime();
        runtime.queueChange(fixture.uuid, "Points", new DataValueInt(3));
        UserDataCache cache = fixture.caches.get(fixture.uuid);
        List<Runnable> workers = new ArrayList<>();
        var closed = runtime.closeAsync(workers::add).toCompletableFuture();
        assertThrows(IllegalStateException.class,
                () -> cache.addChange(new UserDataChangeInt("Points", 9), true));
        workers.get(0).run();
        closed.join();
        assertEquals(3, fixture.first.points(fixture.uuid));
    }

    @Test
    void shutdownCancelsDelayedTimerWorkWithoutAwaitingIt() throws Exception {
        Fixture fixture = new Fixture();
        ScheduledThreadPoolExecutor timer = new ScheduledThreadPoolExecutor(1);
        when(fixture.manager.getTimer()).thenReturn(timer);
        try {
            ScheduledFuture<?> delayed = timer.schedule((Runnable) () -> fail("redundant delayed task ran"), 1, TimeUnit.HOURS);
            fixture.owner.shutdown();
            assertFalse(timer.getExecuteExistingDelayedTasksAfterShutdownPolicy());
            assertTrue(delayed.isCancelled());
            assertTrue(timer.awaitTermination(2, TimeUnit.SECONDS));
        } finally { timer.shutdownNow(); }
    }

    private static void await(CountDownLatch latch) {
        try { assertTrue(latch.await(5, TimeUnit.SECONDS)); }
        catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
    }

    private static final class Fixture {
        final UUID uuid = UUID.randomUUID();
        final AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class, RETURNS_DEEP_STUBS);
        final UserDataManager manager = mock(UserDataManager.class);
        final ConcurrentHashMap<UUID, UserDataCache> caches = new ConcurrentHashMap<>();
        final List<Runnable> tasks = new CopyOnWriteArrayList<>();
        final MemoryBackend first = new MemoryBackend(UserStorage.SQLITE);
        final BukkitUserCacheOwner owner;
        Fixture() {
            when(manager.getPlugin()).thenReturn(plugin);
            when(plugin.getStorageType()).thenReturn(UserStorage.SQLITE);
            when(manager.getUserDataCache()).thenReturn(caches);
            when(manager.isCached(any(UUID.class))).thenAnswer(call -> {
                UserDataCache cache = caches.get(call.getArgument(0));
                return cache != null && cache.hasCache();
            });
            ScheduledExecutorService timer = mock(ScheduledExecutorService.class);
            when(manager.getTimer()).thenReturn(timer);
            when(timer.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class))).thenAnswer(call -> {
                tasks.add(call.getArgument(0, Runnable.class));
                return mock(ScheduledFuture.class);
            });
            first.rows.put(uuid, new HashMap<>(Map.of("Points", new DataValueInt(1))));
            owner = new BukkitUserCacheOwner(manager);
        }
        SharedUserDataRuntime runtime() { return new SharedUserDataRuntime(first, owner); }
    }

    private static final class MemoryBackend implements SqlUserBackend {
        final UserStorage type;
        final Map<UUID, HashMap<String, DataValue>> rows = new ConcurrentHashMap<>();
        volatile boolean open = true;
        volatile Runnable beforeWrite = () -> {};
        MemoryBackend(UserStorage type) { this.type = type; }
        int points(UUID uuid) { return rows.get(uuid).get("Points").getInt(); }
        public UserStorage storageType() { return type; }
        public boolean isOpen() { return open; }
        public void close() { open = false; }
        public List<UUID> enumerateUsers() { return new ArrayList<>(rows.keySet()); }
        public SqlUserStorage user(UUID uuid) {
            if (!open) throw new IllegalStateException("closed");
            return new SqlUserStorage() {
                public List<Column> readRow(UserStorage requested) {
                    List<Column> result = new ArrayList<>();
                    rows.getOrDefault(uuid, new HashMap<>()).forEach((key, value) -> result.add(new Column(key, value)));
                    return result;
                }
                public boolean contains(UserStorage requested) { return rows.containsKey(uuid); }
                public void delete(UserStorage requested) { rows.remove(uuid); }
                public void write(UserStorage requested, String key, DataValue value) {
                    writeValues(requested, new HashMap<>(Map.of(key, value)));
                }
                public void writeValues(UserStorage requested, HashMap<String, DataValue> values) {
                    assertEquals(type, requested);
                    if (!open) throw new IllegalStateException("closed");
                    beforeWrite.run();
                    rows.computeIfAbsent(uuid, ignored -> new HashMap<>()).putAll(values);
                }
            };
        }
    }
}
