package com.bencodez.advancedcore.tests.user;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.user.usercache.UserDataCache;
import com.bencodez.advancedcore.api.user.usercache.UserDataCache.IntegerMutation;
import com.bencodez.advancedcore.api.user.usercache.UserDataManager;
import com.bencodez.simpleapi.sql.data.DataValue;
import com.bencodez.simpleapi.sql.data.DataValueInt;

class AtomicIntegerMutationTest {
    @Test void legacyBatchProviderGuardDoesNotAffectAnotherUser() {
        var plugin = mock(AdvancedCorePlugin.class); var users = mock(com.bencodez.advancedcore.api.user.UserManager.class);
        var user = mock(com.bencodez.advancedcore.api.user.AdvancedCoreUser.class);
        var data = mock(com.bencodez.advancedcore.api.user.UserData.class);
        var manager = new UserDataManager(plugin); UUID id = UUID.randomUUID(), other = UUID.randomUUID();
        when(plugin.getUserManager()).thenReturn(users); when(users.getUser(id, false)).thenReturn(user);
        when(user.getUserData()).thenReturn(data);
        var cache = new UserDataCache(manager, id);
        cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("a", new DataValueInt(1))));
        manager.getUserDataCache().put(id, cache);
        doAnswer(call -> {
            var write = manager.beginLegacyDirectWrite(id);
            try {
                assertTrue(UserDataCache.hasLegacyBatchPersistence(id.toString()));
                assertFalse(UserDataCache.hasLegacyBatchPersistence(other.toString()));
				UserDataCache.runOutsideLegacyBatch(() -> assertFalse(UserDataCache.hasLegacyBatchPersistence(id.toString())));
				assertTrue(UserDataCache.hasLegacyBatchPersistence(id.toString()));
                assertDoesNotThrow(() -> UserDataCache.rejectStaleLegacyBatch(other.toString()));
                assertDoesNotThrow(() -> UserDataCache.propagateLegacyBatchWriteFailure(other.toString(), new java.sql.SQLException("synthetic")));
                assertThrows(UserDataCache.StaleLegacyBatchException.class, () -> UserDataCache.rejectStaleLegacyBatch(id.toString()));
            } finally { manager.endLegacyDirectWrite(write); }
            return null;
        }).when(data).setValues(any(HashMap.class));
        try {
            assertTrue(manager.tryMutateLegacyIntegers(id, Map.of("a", 1), IntegerMutation.ADD, null).applied());
            cache.processChanges();
        } finally { manager.getTimer().shutdownNow(); }
    }
    @Test void failedLegacyFlushDoesNotReplaceNewerConcurrentMutation() throws Exception {
        var plugin = mock(AdvancedCorePlugin.class); var manager = mock(UserDataManager.class);
        var users = mock(com.bencodez.advancedcore.api.user.UserManager.class);
        var user = mock(com.bencodez.advancedcore.api.user.AdvancedCoreUser.class);
        var data = mock(com.bencodez.advancedcore.api.user.UserData.class);
        when(manager.getPlugin()).thenReturn(plugin); when(manager.getTimer()).thenReturn(mock(ScheduledExecutorService.class));
        when(plugin.getUserManager()).thenReturn(users); when(users.getUser(any(UUID.class), eq(false))).thenReturn(user);
        when(user.getUserData()).thenReturn(data);
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        doAnswer(call -> { entered.countDown(); await(release); throw new IllegalStateException("synthetic failure"); }).when(data).setValues(any(HashMap.class));
        var cache = new UserDataCache(manager, UUID.randomUUID());
        cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("a", new DataValueInt(1))));
        var worker = Executors.newSingleThreadExecutor();
        try {
            assertTrue(cache.tryMutateLegacyIntegers(Map.of("a", 1), IntegerMutation.ADD, null).applied());
            var flush = worker.submit(cache::processChanges); assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertTrue(cache.tryMutateLegacyIntegers(Map.of("a", 1), IntegerMutation.ADD, null).applied());
            release.countDown(); assertThrows(ExecutionException.class, () -> flush.get(5, TimeUnit.SECONDS));
            assertEquals(3, cache.getIfPresent("a").getInt());
        } finally { release.countDown(); worker.shutdownNow(); }
    }
    @Test void failedLegacyFlushRetainsLaterDirectValueForRetry() {
        var plugin = mock(AdvancedCorePlugin.class); var manager = mock(UserDataManager.class);
        var users = mock(com.bencodez.advancedcore.api.user.UserManager.class);
        var user = mock(com.bencodez.advancedcore.api.user.AdvancedCoreUser.class);
        var data = mock(com.bencodez.advancedcore.api.user.UserData.class);
        when(manager.getPlugin()).thenReturn(plugin); when(manager.getTimer()).thenReturn(mock(ScheduledExecutorService.class));
        when(plugin.getUserManager()).thenReturn(users); when(users.getUser(any(UUID.class), eq(false))).thenReturn(user);
        when(user.getUserData()).thenReturn(data);
        var stored = new AtomicInteger(); var calls = new AtomicInteger();
        doAnswer(call -> { if (calls.getAndIncrement() == 0) throw new IllegalStateException("synthetic failure");
            stored.set(call.<HashMap<String, DataValue>>getArgument(0).get("a").getInt()); return null; }).when(data).setValues(any(HashMap.class));
        var cache = new UserDataCache(manager, UUID.randomUUID());
        cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("a", new DataValueInt(1))));
        assertTrue(cache.tryMutateLegacyIntegers(Map.of("a", 1), IntegerMutation.ADD, null).applied());
        cache.addChange(new com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt("a", 10), false);
        assertThrows(IllegalStateException.class, cache::processChanges);
        assertEquals(10, cache.getIfPresent("a").getInt());
        cache.processChanges();
        assertEquals(10, stored.get());
    }
    @Test void stagedLegacySetterIsFlushedBeforeNextCheckpoint() throws Exception {
        var plugin = mock(AdvancedCorePlugin.class); var manager = mock(UserDataManager.class);
        var users = mock(com.bencodez.advancedcore.api.user.UserManager.class);
        var user = mock(com.bencodez.advancedcore.api.user.AdvancedCoreUser.class);
        var data = mock(com.bencodez.advancedcore.api.user.UserData.class);
        when(manager.getPlugin()).thenReturn(plugin); when(manager.getTimer()).thenReturn(mock(ScheduledExecutorService.class));
        when(plugin.getUserManager()).thenReturn(users); when(users.getUser(any(UUID.class), eq(false))).thenReturn(user);
        when(user.getUserData()).thenReturn(data);
        var stored = new AtomicInteger(1);
        doAnswer(call -> { stored.set(((DataValue) call.<HashMap<String, DataValue>>getArgument(0).get("a")).getInt()); return null; }).when(data).setValues(any(HashMap.class));
        var cache = new UserDataCache(manager, UUID.randomUUID());
        cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("a", new DataValueInt(1))));
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> cache.flushChangesAndRun(() -> { entered.countDown(); await(release); }));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            cache.addChange(new com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt("a", 10), true);
            var next = pool.submit(() -> cache.flushChangesAndRun(() -> assertEquals(10, stored.get())));
            release.countDown(); first.get(5, TimeUnit.SECONDS); next.get(5, TimeUnit.SECONDS);
            assertEquals(10, stored.get());
        } finally { release.countDown(); pool.shutdownNow(); }
    }
    @Test void stagedLegacySetterIsRetainedWhenRetirementStartsDuringCheckpoint() throws Exception {
        var plugin = mock(AdvancedCorePlugin.class); var manager = mock(UserDataManager.class);
        when(manager.getPlugin()).thenReturn(plugin); when(manager.getTimer()).thenReturn(mock(ScheduledExecutorService.class));
        var cache = new UserDataCache(manager, UUID.randomUUID());
        cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("a", new DataValueInt(1))));
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var worker = Executors.newSingleThreadExecutor();
        try {
            var checkpoint = worker.submit(() -> cache.flushChangesAndRun(() -> { entered.countDown(); await(release); }));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            cache.addChange(new com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt("a", 10), true);
            cache.beginRemoval();
            assertThrows(IllegalStateException.class, () -> cache.addChange(new com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt("a", 11), true));
            release.countDown(); checkpoint.get(5, TimeUnit.SECONDS);
            assertEquals(10, cache.getIfPresent("a").getInt());
            assertTrue(cache.hasChangesToProcess());
        } finally { release.countDown(); worker.shutdownNow(); }
    }
    @Test void queuedLegacySetterSurvivesCheckpointReplacementAndFailure() throws Exception {
        var plugin = mock(AdvancedCorePlugin.class); var manager = mock(UserDataManager.class);
        when(manager.getPlugin()).thenReturn(plugin); when(manager.getTimer()).thenReturn(mock(ScheduledExecutorService.class));
        var cache = new UserDataCache(manager, UUID.randomUUID());
        cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("a", new DataValueInt(1))));
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var worker = Executors.newSingleThreadExecutor();
        try {
            var checkpoint = worker.submit(() -> cache.flushChangesAndRun(() -> {
                entered.countDown(); await(release);
                cache.updateCache(new HashMap<>(Map.of("a", new DataValueInt(5))));
                throw new IllegalStateException("synthetic checkpoint failure");
            }));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            cache.addChange(new com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt("a", 10), true);
            assertEquals(10, cache.getIfPresent("a").getInt());
            release.countDown();
            assertInstanceOf(IllegalStateException.class, assertThrows(ExecutionException.class, () -> checkpoint.get(5, TimeUnit.SECONDS)).getCause());
            assertEquals(10, cache.getIfPresent("a").getInt());
            assertTrue(cache.hasChangesToProcess());
        } finally { release.countDown(); worker.shutdownNow(); }
    }
    @Test void legacySetterMustNotWaitBehindStorageCheckpoint() throws Exception {
        var plugin = mock(AdvancedCorePlugin.class); var manager = mock(UserDataManager.class);
        when(manager.getPlugin()).thenReturn(plugin); when(manager.getTimer()).thenReturn(mock(ScheduledExecutorService.class));
        var cache = new UserDataCache(manager, UUID.randomUUID());
        cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("a", new DataValueInt(1))));
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var checkpoint = pool.submit(() -> cache.flushChangesAndRun(() -> { entered.countDown(); await(release); }));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            var setter = pool.submit(() -> cache.addChange(new com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt("a", 10), true));
            try { setter.get(1, TimeUnit.SECONDS); }
            finally { release.countDown(); }
            checkpoint.get(5, TimeUnit.SECONDS);
            assertEquals(10, cache.getIfPresent("a").getInt());
        } finally { release.countDown(); pool.shutdownNow(); }
    }
    @Test void strictLegacyReadPreservesNewerMutationsAndRejectsRetirement() {
        var plugin = mock(AdvancedCorePlugin.class); var manager = mock(UserDataManager.class);
        when(manager.getPlugin()).thenReturn(plugin); when(manager.getTimer()).thenReturn(mock(ScheduledExecutorService.class));
        var cache = new UserDataCache(manager, UUID.randomUUID());
        cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("a", new DataValueInt(1))));
        long version = cache.getSharedSnapshotVersion();
        assertTrue(cache.tryMutateLegacyIntegers(Map.of("a", 1), IntegerMutation.ADD, null).applied());
        assertTrue(cache.tryPublishCompleteLegacySnapshot(new HashMap<>(Map.of("a", new DataValueInt(1))), version));
        assertEquals(2, cache.getIfPresent("a").getInt());
        long retiringVersion = cache.getSharedSnapshotVersion();
        cache.beginRemoval();
        assertFalse(cache.tryPublishCompleteLegacySnapshot(new HashMap<>(), retiringVersion));
        assertEquals(2, cache.getIfPresent("a").getInt());
    }
    @Test void refreshStartedAfterInvalidatedFlushUsesFreshStorageValue() throws Exception {
        for (String invalidated : new String[] { "a", "other" }) {
        var plugin = mock(AdvancedCorePlugin.class); var manager = mock(UserDataManager.class);
        when(plugin.getUserManager()).thenReturn(mock(com.bencodez.advancedcore.api.user.UserManager.class));
        when(manager.getPlugin()).thenReturn(plugin); when(manager.getTimer()).thenReturn(mock(ScheduledExecutorService.class));
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var cache = new UserDataCache(manager, UUID.randomUUID());
        cache.configureSharedStorage(values -> { entered.countDown(); await(release); }, Runnable::run);
        cache.updateCache(new HashMap<>(Map.of("a", new DataValueInt(1))));
        var executor = Executors.newSingleThreadExecutor();
        try {
            assertTrue(cache.tryMutateIntegers(Map.of("a", -1), IntegerMutation.DEBIT_POSITIVE, null).applied());
            var flush = executor.submit(cache::processChangesForSharedRuntime);
            assertTrue(entered.await(5, TimeUnit.SECONDS)); cache.invalidateStorageSnapshot(invalidated);
            release.countDown(); flush.get(5, TimeUnit.SECONDS);
            long version = cache.getSharedSnapshotVersion();
            cache.updateSharedSnapshot(new HashMap<>(Map.of("a", new DataValueInt(9))), version);
            assertEquals(9, cache.getIfPresent("a").getInt());
        } finally { release.countDown(); executor.shutdownNow(); }
        }
    }
    @Test void invalidatedInFlightDebitSurvivesAnOlderRefresh() throws Exception {
        var plugin = mock(AdvancedCorePlugin.class); var manager = mock(UserDataManager.class);
        when(plugin.getUserManager()).thenReturn(mock(com.bencodez.advancedcore.api.user.UserManager.class));
        when(manager.getPlugin()).thenReturn(plugin); when(manager.getTimer()).thenReturn(mock(ScheduledExecutorService.class));
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var cache = new UserDataCache(manager, UUID.randomUUID());
        cache.configureSharedStorage(values -> { entered.countDown(); await(release); }, Runnable::run);
        cache.updateCache(new HashMap<>(Map.of("a", new DataValueInt(1))));
        var executor = Executors.newSingleThreadExecutor();
        try {
            assertTrue(cache.tryMutateIntegers(Map.of("a", -1), IntegerMutation.DEBIT_POSITIVE, null).applied());
            var flush = executor.submit(cache::processChangesForSharedRuntime);
            assertTrue(entered.await(5, TimeUnit.SECONDS)); cache.invalidateStorageSnapshot("a");
            long version = cache.getSharedSnapshotVersion();
            release.countDown(); flush.get(5, TimeUnit.SECONDS);
            assertNull(cache.getIfPresent("a"));
            cache.updateSharedSnapshot(new HashMap<>(Map.of("a", new DataValueInt(1))), version);
            assertEquals(0, cache.getIfPresent("a").getInt());
            assertFalse(cache.tryMutateIntegers(Map.of("a", -1), IntegerMutation.DEBIT_POSITIVE, null).applied());
        } finally { release.countDown(); executor.shutdownNow(); }
    }

    @Test void arithmeticRetriesAfterCheckpointChangesBaseline() throws Exception {
        var cache = cache(); var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        try {
            var checkpoint = executor.submit(() -> cache.flushChangesAndRun(() -> {
                entered.countDown(); await(release);
                cache.updateCache(new HashMap<>(Map.of("a", new DataValueInt(5))));
            }));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertNull(cache.tryMutateIntegers(Map.of("a", 1), IntegerMutation.ADD, null));
            release.countDown(); checkpoint.get(5, TimeUnit.SECONDS);
            assertTrue(cache.tryMutateIntegers(Map.of("a", 1), IntegerMutation.ADD, null).applied());
            assertEquals(6, cache.getIfPresent("a").getInt());
        } finally { release.countDown(); executor.shutdownNow(); }
    }
    @Test void legacyClearingDoesNotBlockCachedArithmeticOnStorage() throws Exception {
        var plugin = mock(AdvancedCorePlugin.class); var manager = mock(UserDataManager.class);
        var users = mock(com.bencodez.advancedcore.api.user.UserManager.class);
        var user = mock(com.bencodez.advancedcore.api.user.AdvancedCoreUser.class);
        var data = mock(com.bencodez.advancedcore.api.user.UserData.class);
        when(manager.getPlugin()).thenReturn(plugin); when(manager.getTimer()).thenReturn(mock(ScheduledExecutorService.class));
        when(plugin.getUserManager()).thenReturn(users); when(users.getUser(any(UUID.class), eq(false))).thenReturn(user);
        when(user.getUserData()).thenReturn(data);
        var cache = new UserDataCache(manager, UUID.randomUUID());
        cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("a", new DataValueInt(1))));
        cache.tryMutateLegacyIntegers(Map.of("a", 1), IntegerMutation.ADD, null);
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        doAnswer(call -> { entered.countDown(); await(release); return null; }).when(data).setValues(any(HashMap.class));
        var pool = Executors.newFixedThreadPool(2);
        try {
            var clear = pool.submit(cache::clearCache);
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            var mutation = pool.submit(() -> cache.tryMutateLegacyIntegers(Map.of("a", 1), IntegerMutation.ADD, null));
            assertNull(mutation.get(1, TimeUnit.SECONDS));
            release.countDown(); clear.get(5, TimeUnit.SECONDS);
        } finally { release.countDown(); pool.shutdownNow(); }
    }

    @Test void overlappingLegacyFlushesCannotLoseAnInFlightDebit() throws Exception {
        var plugin = mock(AdvancedCorePlugin.class); var manager = mock(UserDataManager.class);
        var users = mock(com.bencodez.advancedcore.api.user.UserManager.class);
        var user = mock(com.bencodez.advancedcore.api.user.AdvancedCoreUser.class);
        var data = mock(com.bencodez.advancedcore.api.user.UserData.class);
        when(manager.getPlugin()).thenReturn(plugin); when(manager.getTimer()).thenReturn(mock(ScheduledExecutorService.class));
        when(plugin.getUserManager()).thenReturn(users); when(users.getUser(any(UUID.class), eq(false))).thenReturn(user);
        when(user.getUserData()).thenReturn(data);
        var cache = new UserDataCache(manager, UUID.randomUUID());
        cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("a", new DataValueInt(1), "b", new DataValueInt(0))));
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var secondEntered = new CountDownLatch(1);
        var writes = new AtomicInteger();
        doAnswer(call -> { if (writes.getAndIncrement() == 0) { entered.countDown(); await(release); } else secondEntered.countDown(); return null; }).when(data).setValues(any(HashMap.class));
        var pool = Executors.newFixedThreadPool(2);
        try {
            assertTrue(cache.tryMutateLegacyIntegers(Map.of("a", -1), IntegerMutation.DEBIT_POSITIVE, null).applied());
            var first = pool.submit(cache::processChanges);
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertTrue(cache.tryMutateLegacyIntegers(Map.of("b", 1), IntegerMutation.ADD, null).applied());
            var second = pool.submit(cache::processChanges);
            assertFalse(secondEntered.await(300, TimeUnit.MILLISECONDS), "second batch must wait for the first");
            cache.updateCache(new HashMap<>(Map.of("a", new DataValueInt(1), "b", new DataValueInt(0))));
            assertFalse(cache.tryMutateLegacyIntegers(Map.of("a", -1), IntegerMutation.DEBIT_POSITIVE, null).applied());
            release.countDown(); first.get(5, TimeUnit.SECONDS); second.get(5, TimeUnit.SECONDS);
        } finally { release.countDown(); pool.shutdownNow(); }
    }
    @Test void clearingCompleteSnapshotDoesNotInventAZeroBalance() {
        var plugin = mock(AdvancedCorePlugin.class); var manager = mock(UserDataManager.class);
        when(manager.getPlugin()).thenReturn(plugin); when(manager.getTimer()).thenReturn(mock(ScheduledExecutorService.class));
        var cache = new UserDataCache(manager, UUID.randomUUID()); cache.configureSharedStorage(values -> {}, Runnable::run);
        cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("a", new DataValueInt(25))));
        cache.clearCache();
        assertFalse(cache.seedMissingIntegerFromCompleteSnapshot("a"));
        assertFalse(cache.hasPublishedStorageSnapshot());
    }
    @Test void sharedAwardDoesNotReportFailureAfterSchedulingThrows() {
        var plugin = mock(AdvancedCorePlugin.class); var manager = mock(UserDataManager.class);
        when(manager.getPlugin()).thenReturn(plugin); when(manager.getTimer()).thenReturn(mock(ScheduledExecutorService.class));
        when(plugin.getUserManager()).thenReturn(mock(com.bencodez.advancedcore.api.user.UserManager.class));
        doThrow(new IllegalStateException("schedule failed")).when(plugin).debug("Schedule changes");
        var written = new AtomicReference<HashMap<String, DataValue>>();
        var cache = new UserDataCache(manager, UUID.randomUUID()); cache.configureSharedStorage(written::set, Runnable::run);
        cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("a", new DataValueInt(1), "b", new DataValueInt(2))));
        var result = cache.tryMutateIntegers(Map.of("a", 1, "b", 1), IntegerMutation.ADD, null);
        assertTrue(result.applied()); assertEquals(2, cache.getIfPresent("a").getInt());
        assertEquals(3, cache.getIfPresent("b").getInt()); assertTrue(cache.hasChangesToProcess());
        cache.processChangesForSharedRuntime();
        assertEquals(2, written.get().get("a").getInt()); assertEquals(3, written.get().get("b").getInt());
    }
    @Test void sharedAwardDoesNotReportFailureAfterNotificationDispatchThrows() {
        var plugin = mock(AdvancedCorePlugin.class); var manager = mock(UserDataManager.class);
        when(manager.getPlugin()).thenReturn(plugin); when(manager.getTimer()).thenReturn(mock(ScheduledExecutorService.class));
        doThrow(new IllegalStateException("notification failed")).when(manager).dispatchSharedUserDataNotification(any(Runnable.class));
        var cache = new UserDataCache(manager, UUID.randomUUID()); cache.configureSharedStorage(values -> {}, Runnable::run);
        cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("a", new DataValueInt(1))));
        assertTrue(cache.tryMutateIntegers(Map.of("a", 1), IntegerMutation.ADD, keys -> {}).applied());
        assertEquals(2, cache.getIfPresent("a").getInt()); assertTrue(cache.hasChangesToProcess());
    }
    @Test void sharedOverflowRejectsTheEntireBatch() { overflowRejectsTheEntireBatch(true); }
    @Test void legacyOverflowRejectsTheEntireBatch() { overflowRejectsTheEntireBatch(false); }
    private void overflowRejectsTheEntireBatch(boolean shared) {
        var plugin = mock(AdvancedCorePlugin.class); var manager = mock(UserDataManager.class);
        when(manager.getPlugin()).thenReturn(plugin); when(manager.getTimer()).thenReturn(mock(ScheduledExecutorService.class));
        var cache = new UserDataCache(manager, UUID.randomUUID());
        if (shared) cache.configureSharedStorage(values -> {}, Runnable::run);
        cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("safe", new DataValueInt(3), "overflow", new DataValueInt(Integer.MAX_VALUE))));
        var operands = new LinkedHashMap<String, Integer>(); operands.put("safe", 1); operands.put("overflow", 1);
        var notifications = new AtomicInteger();
        assertThrows(ArithmeticException.class, () -> {
            if (shared) cache.tryMutateIntegers(operands, IntegerMutation.ADD, keys -> notifications.incrementAndGet());
            else cache.tryMutateLegacyIntegers(operands, IntegerMutation.ADD, keys -> notifications.incrementAndGet());
        });
        assertEquals(3, cache.getIfPresent("safe").getInt());
        assertEquals(Integer.MAX_VALUE, cache.getIfPresent("overflow").getInt());
        assertFalse(cache.hasChangesToProcess()); assertEquals(0, notifications.get());
    }

    @Test void sharedUnderflowFailsAndOrdinaryFloorStillClamps() { underflowAndFloor(true); }
    @Test void legacyUnderflowFailsAndOrdinaryFloorStillClamps() { underflowAndFloor(false); }
    private void underflowAndFloor(boolean shared) {
        var plugin = mock(AdvancedCorePlugin.class); var manager = mock(UserDataManager.class);
        when(manager.getPlugin()).thenReturn(plugin); when(manager.getTimer()).thenReturn(mock(ScheduledExecutorService.class));
        var cache = new UserDataCache(manager, UUID.randomUUID());
        if (shared) cache.configureSharedStorage(values -> {}, Runnable::run);
        cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("low", new DataValueInt(Integer.MIN_VALUE), "normal", new DataValueInt(1))));
        assertThrows(ArithmeticException.class, () -> {
            if (shared) cache.tryMutateIntegers(Map.of("low", -1), IntegerMutation.ADD_FLOOR_ZERO, null);
            else cache.tryMutateLegacyIntegers(Map.of("low", -1), IntegerMutation.ADD_FLOOR_ZERO, null);
        });
        assertEquals(Integer.MIN_VALUE, cache.getIfPresent("low").getInt());
        assertFalse(cache.hasChangesToProcess());
        if (shared) assertTrue(cache.tryMutateIntegers(Map.of("normal", -2), IntegerMutation.ADD_FLOOR_ZERO, null).applied());
        else assertTrue(cache.tryMutateLegacyIntegers(Map.of("normal", -2), IntegerMutation.ADD_FLOOR_ZERO, null).applied());
        assertEquals(0, cache.getIfPresent("normal").getInt());
    }

    @Test void completeRowReadDistinguishesNullFromEmpty() throws Exception {
        var user = mock(com.bencodez.advancedcore.api.user.AdvancedCoreUser.class);
        var plugin = mock(AdvancedCorePlugin.class); var users = mock(com.bencodez.advancedcore.api.user.UserManager.class);
        var manager = mock(UserDataManager.class);
        when(user.getPlugin()).thenReturn(plugin); when(plugin.getUserManager()).thenReturn(users);
        when(users.getDataManager()).thenReturn(manager);
        when(plugin.getStorageType()).thenReturn(com.bencodez.advancedcore.api.user.UserStorage.MYSQL);
        when(manager.effectiveStorageType(any())).thenAnswer(call -> call.getArgument(0));
        var data = mock(com.bencodez.advancedcore.api.user.UserData.class, CALLS_REAL_METHODS);
        var sql = mock(com.bencodez.advancedcore.core.user.storage.SqlUserDataAccess.class);
        var userField = data.getClass().getDeclaredField("user"); userField.setAccessible(true); userField.set(data, user);
        var sqlField = data.getClass().getDeclaredField("sqlData"); sqlField.setAccessible(true); sqlField.set(data, sql);
        when(sql.readRow(com.bencodez.advancedcore.api.user.UserStorage.MYSQL)).thenReturn(null, new java.util.ArrayList<>());
        assertNull(data.getValuesIfComplete()); assertTrue(data.getValuesIfComplete().isEmpty());
    }

    @Test void legacyCachedArithmeticSerializesConcurrentUpdates() throws Exception {
        var plugin = mock(AdvancedCorePlugin.class); var manager = mock(UserDataManager.class);
        when(manager.getPlugin()).thenReturn(plugin); when(manager.getTimer()).thenReturn(mock(ScheduledExecutorService.class));
        var cache = new UserDataCache(manager, UUID.randomUUID());
        cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("a", new DataValueInt(1))));
        var pool = Executors.newFixedThreadPool(2); var start = new CountDownLatch(1);
        try {
            var first = pool.submit(() -> { start.await(); return cache.tryMutateLegacyIntegers(Map.of("a", -1), IntegerMutation.ADD, null); });
            var second = pool.submit(() -> { start.await(); return cache.tryMutateLegacyIntegers(Map.of("a", 1), IntegerMutation.ADD, null); });
            start.countDown(); assertNotNull(first.get(5, TimeUnit.SECONDS)); assertNotNull(second.get(5, TimeUnit.SECONDS));
            assertEquals(1, cache.getIfPresent("a").getInt());
        } finally { pool.shutdownNow(); }
    }

    @Test void legacyArithmeticNeverWaitsBehindAnExclusiveStorageOperation() throws Exception {
        var plugin = mock(AdvancedCorePlugin.class); var manager = mock(UserDataManager.class);
        when(manager.getPlugin()).thenReturn(plugin); when(manager.getTimer()).thenReturn(mock(ScheduledExecutorService.class));
        var cache = new UserDataCache(manager, UUID.randomUUID());
        cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("a", new DataValueInt(1))));
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var pool = Executors.newSingleThreadExecutor();
        try {
            var holder = pool.submit(() -> cache.flushChangesAndRun(() -> { entered.countDown(); await(release); }));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertNull(cache.tryMutateLegacyIntegers(Map.of("a", 1), IntegerMutation.ADD, null));
            assertEquals(1, cache.getIfPresent("a").getInt());
            release.countDown(); holder.get(5, TimeUnit.SECONDS);
            assertNotNull(cache.tryMutateLegacyIntegers(Map.of("a", 1), IntegerMutation.ADD, null));
            assertEquals(2, cache.getIfPresent("a").getInt());
        } finally { release.countDown(); pool.shutdownNow(); }
    }

    @Test void missingIntegerDefaultsOnlyFromACompleteCurrentRow() {
        var plugin = mock(AdvancedCorePlugin.class); var manager = mock(UserDataManager.class);
        when(manager.getPlugin()).thenReturn(plugin); when(manager.getTimer()).thenReturn(mock(ScheduledExecutorService.class));
        var cache = new UserDataCache(manager, UUID.randomUUID()); cache.configureSharedStorage(values -> {}, Runnable::run);
        cache.updateCache(new HashMap<>());
        assertFalse(cache.seedMissingIntegerFromCompleteSnapshot("dynamic"));
        cache.updateCompleteCachePreservingPending(new HashMap<>());
        assertTrue(cache.seedMissingIntegerFromCompleteSnapshot("dynamic"));
        assertTrue(cache.tryMutateIntegers(Map.of("dynamic", 1), IntegerMutation.ADD, null).applied());
        assertEquals(1, cache.getIfPresent("dynamic").getInt());
        cache.invalidateStorageSnapshot("other");
        assertFalse(cache.seedMissingIntegerFromCompleteSnapshot("second"));
        cache.updateCachePreservingPending(new HashMap<>());
        assertFalse(cache.seedMissingIntegerFromCompleteSnapshot("second"));
    }

    @Test void directSnapshotReplacementCannotAuthorizeASecondInFlightDebit() throws Exception {
        for (boolean failFirst : new boolean[] { false, true }) {
            var plugin = mock(AdvancedCorePlugin.class); var manager = mock(UserDataManager.class);
            when(plugin.getUserManager()).thenReturn(mock(com.bencodez.advancedcore.api.user.UserManager.class));
            when(manager.getPlugin()).thenReturn(plugin); when(manager.getTimer()).thenReturn(mock(ScheduledExecutorService.class));
            var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var attempts = new AtomicInteger();
            var stored = new ConcurrentHashMap<String, DataValue>(); stored.put("a", new DataValueInt(1));
            var cache = new UserDataCache(manager, UUID.randomUUID());
            cache.configureSharedStorage(values -> {
                if (attempts.getAndIncrement() == 0) { entered.countDown(); await(release); if (failFirst) throw new IllegalStateException("write failure"); }
                stored.putAll(values);
            }, Runnable::run);
            cache.updateCache(new HashMap<>(stored)); var executor = Executors.newSingleThreadExecutor();
            try {
                assertTrue(cache.tryMutateIntegers(Map.of("a", -1), IntegerMutation.DEBIT_POSITIVE, null).applied());
                var flush = executor.submit(cache::processChangesForSharedRuntime);
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                var user = mock(com.bencodez.advancedcore.api.user.AdvancedCoreUser.class);
                when(user.isCached()).thenReturn(true); when(user.getCache()).thenReturn(cache);
                var data = new com.bencodez.advancedcore.api.user.UserData(user);
                data.setTempCache(new HashMap<>(Map.of("a", new DataValueInt(1)))); data.updateCacheWithTemp();
                assertFalse(cache.tryMutateIntegers(Map.of("a", -1), IntegerMutation.DEBIT_POSITIVE, null).applied());
                release.countDown();
                if (failFirst) { assertThrows(ExecutionException.class, () -> flush.get(5, TimeUnit.SECONDS)); cache.processChangesForSharedRuntime(); }
                else flush.get(5, TimeUnit.SECONDS);
                assertEquals(0, stored.get("a").getInt()); assertEquals(0, cache.getIfPresent("a").getInt());
            } finally { release.countDown(); executor.shutdownNow(); }
        }
    }

    @Test void newerQueuedAwardTakesPrecedenceOverInFlightDebitAfterReplacement() throws Exception {
        var plugin = mock(AdvancedCorePlugin.class); var manager = mock(UserDataManager.class);
        when(plugin.getUserManager()).thenReturn(mock(com.bencodez.advancedcore.api.user.UserManager.class));
        when(manager.getPlugin()).thenReturn(plugin); when(manager.getTimer()).thenReturn(mock(ScheduledExecutorService.class));
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var cache = new UserDataCache(manager, UUID.randomUUID());
        cache.configureSharedStorage(values -> { entered.countDown(); await(release); }, Runnable::run);
        cache.updateCache(new HashMap<>(Map.of("a", new DataValueInt(1)))); var executor = Executors.newSingleThreadExecutor();
        try {
            cache.tryMutateIntegers(Map.of("a", -1), IntegerMutation.DEBIT_POSITIVE, null);
            var flush = executor.submit(cache::processChangesForSharedRuntime);
            assertTrue(entered.await(5, TimeUnit.SECONDS)); cache.updateCache(new HashMap<>(Map.of("a", new DataValueInt(1))));
            var award = cache.tryMutateIntegers(Map.of("a", 1), IntegerMutation.ADD, null);
            assertEquals(0, award.previous().get("a")); assertEquals(1, cache.getIfPresent("a").getInt());
            assertTrue(cache.tryMutateIntegers(Map.of("a", -1), IntegerMutation.DEBIT_POSITIVE, null).applied());
            assertEquals(0, cache.getIfPresent("a").getInt()); release.countDown(); flush.get(5, TimeUnit.SECONDS);
            assertEquals(0, cache.getIfPresent("a").getInt());
        } finally { release.countDown(); executor.shutdownNow(); }
    }

    @Test void failedWriteCannotExposeOldBalanceBeforeRetry() throws Exception {
        var plugin = mock(AdvancedCorePlugin.class); var manager = mock(UserDataManager.class);
        when(plugin.getUserManager()).thenReturn(mock(com.bencodez.advancedcore.api.user.UserManager.class));
        when(manager.getPlugin()).thenReturn(plugin); when(manager.getTimer()).thenReturn(mock(ScheduledExecutorService.class));
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var attempts = new AtomicInteger();
        var stored = new ConcurrentHashMap<String, DataValue>(); stored.put("a", new DataValueInt(1));
        var cache = new UserDataCache(manager, UUID.randomUUID());
        cache.configureSharedStorage(values -> {
            if (attempts.getAndIncrement() == 0) { entered.countDown(); await(release); throw new IllegalStateException("write failed"); }
            stored.putAll(values);
        }, Runnable::run);
        cache.updateCache(new HashMap<>(stored)); var executor = Executors.newSingleThreadExecutor();
        try {
            cache.tryMutateIntegers(Map.of("a", -1), IntegerMutation.DEBIT_POSITIVE, null);
            var flush = executor.submit(cache::processChangesForSharedRuntime);
            assertTrue(entered.await(5, TimeUnit.SECONDS)); cache.updateCache(new HashMap<>(stored));
            release.countDown(); assertThrows(ExecutionException.class, () -> flush.get(5, TimeUnit.SECONDS));
            assertFalse(cache.tryMutateIntegers(Map.of("a", -1), IntegerMutation.DEBIT_POSITIVE, null).applied());
            cache.processChangesForSharedRuntime(); assertEquals(0, stored.get("a").getInt());
        } finally { release.countDown(); executor.shutdownNow(); }
    }

    @Test void failedRequeueRebuildPreservesOriginalErrorAndFencesCache() {
        var plugin = mock(AdvancedCorePlugin.class); var manager = mock(UserDataManager.class);
        when(plugin.getUserManager()).thenReturn(mock(com.bencodez.advancedcore.api.user.UserManager.class));
        when(manager.getPlugin()).thenReturn(plugin); when(manager.getTimer()).thenReturn(mock(ScheduledExecutorService.class));
        var cache = new UserDataCache(manager, UUID.randomUUID());
        cache.configureSharedStorage(values -> { throw new IllegalStateException("storage failed"); }, Runnable::run);
        cache.updateCache(new HashMap<>(Map.of("a", new DataValueInt(1))));
        var change = mock(com.bencodez.advancedcore.api.user.usercache.change.UserDataChange.class);
        when(change.getKey()).thenReturn("a"); var calls = new AtomicInteger();
        when(change.toUserDataValue()).thenAnswer(call -> {
            if (calls.incrementAndGet() >= 4) throw new IllegalStateException("rebuild failed");
            return new DataValueInt(2);
        });
        cache.addChange(change, true);
        var failure = assertThrows(IllegalStateException.class, cache::processChangesForSharedRuntime);
        assertEquals("storage failed", failure.getMessage());
        assertEquals(1, failure.getSuppressed().length); assertEquals("rebuild failed", failure.getSuppressed()[0].getMessage());
        assertFalse(cache.hasPublishedStorageSnapshot()); assertNull(cache.getIfPresent("a"));
        assertTrue(cache.hasChangesToProcess());
    }
    @Test void invalidationKeepsQueuedValueUnavailableUntilRefresh() {
        var cache = cache();
        assertTrue(cache.tryMutateIntegers(Map.of("a", 1), IntegerMutation.ADD, null).applied());
        cache.invalidateStorageSnapshot("a");
        assertNull(cache.getIfPresent("a")); assertTrue(cache.hasChangesToProcess());
        cache.updateSharedSnapshot(new HashMap<>(Map.of("a", new DataValueInt(4), "b", new DataValueInt(2))),
                cache.getSharedSnapshotVersion());
        assertEquals(2, cache.getIfPresent("a").getInt());
    }

    @Test void platformReadCannotObserveInvalidatedQueuedValue() {
        var plugin = mock(AdvancedCorePlugin.class, RETURNS_DEEP_STUBS); var manager = mock(UserDataManager.class);
        when(plugin.getUserManager().getDataManager()).thenReturn(manager);
        when(manager.getPlugin()).thenReturn(plugin); when(manager.getTimer()).thenReturn(mock(ScheduledExecutorService.class));
        when(manager.mustDeferSharedStorageAccess()).thenReturn(true);
        var cache = new UserDataCache(manager, UUID.randomUUID()); cache.configureSharedStorage(v -> {}, Runnable::run);
        cache.updateCache(new HashMap<>(Map.of("a", new DataValueInt(1))));
        var user = mock(com.bencodez.advancedcore.api.user.AdvancedCoreUser.class);
        when(user.getPlugin()).thenReturn(plugin); when(user.getCache()).thenReturn(cache);
        var data = new com.bencodez.advancedcore.api.user.UserData(user);
        cache.tryMutateIntegers(Map.of("a", 1), IntegerMutation.ADD, null); cache.invalidateStorageSnapshot("a");
        assertThrows(IllegalStateException.class, () -> data.getInt(
                com.bencodez.advancedcore.api.user.UserStorage.MYSQL, "a", 0,
                com.bencodez.advancedcore.api.user.UserDataFetchMode.DEFAULT));
    }

    @Test void invalidationKeepsStagedValueUnavailableThroughCheckpointDrain() throws Exception {
        var cache = cache(); var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        try {
            var checkpoint = executor.submit(() -> cache.flushChangesAndRun(() -> { entered.countDown(); await(release); }));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertTrue(cache.tryAddChangeBeforeDeferredSharedFlush(new com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt("a", 2)));
            cache.invalidateStorageSnapshot("a"); assertNull(cache.getIfPresent("a"));
            release.countDown(); checkpoint.get(5, TimeUnit.SECONDS);
            assertNull(cache.getIfPresent("a")); assertTrue(cache.hasChangesToProcess());
        } finally { release.countDown(); executor.shutdownNow(); }
    }

    @Test void invalidationKeepsInFlightValueUnavailableAfterWriteCompletes() throws Exception {
        var plugin = mock(AdvancedCorePlugin.class); var manager = mock(UserDataManager.class);
        when(plugin.getUserManager()).thenReturn(mock(com.bencodez.advancedcore.api.user.UserManager.class));
        when(manager.getPlugin()).thenReturn(plugin); when(manager.getTimer()).thenReturn(mock(ScheduledExecutorService.class));
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var written = new AtomicReference<HashMap<String, DataValue>>();
        var cache = new UserDataCache(manager, UUID.randomUUID());
        cache.configureSharedStorage(values -> { entered.countDown(); await(release); written.set(values); }, Runnable::run);
        cache.updateCache(new HashMap<>(Map.of("a", new DataValueInt(1))));
        var executor = Executors.newSingleThreadExecutor();
        try {
            cache.tryMutateIntegers(Map.of("a", 1), IntegerMutation.ADD, null);
            var flush = executor.submit(cache::processChangesForSharedRuntime);
            assertTrue(entered.await(5, TimeUnit.SECONDS)); cache.invalidateStorageSnapshot("a");
            assertNull(cache.getIfPresent("a")); release.countDown(); flush.get(5, TimeUnit.SECONDS);
            assertNull(cache.getIfPresent("a")); assertEquals(2, written.get().get("a").getInt());
        } finally { release.countDown(); executor.shutdownNow(); }
    }
    @Test void concurrentOperandGrowthCannotBypassBatchLimit() throws Exception {
        var checked = new CountDownLatch(1); var grown = new CountDownLatch(1);
        var operands = new ConcurrentHashMap<String, Integer>() {
            @Override public int size() {
                int before = super.size(); checked.countDown(); await(grown); return before;
            }
        };
        var values = new HashMap<String, DataValue>();
        for (int i = 0; i < 65; i++) {
            values.put("key" + i, new DataValueInt(1));
            if (i < 64) operands.put("key" + i, 1);
        }
        var plugin = mock(AdvancedCorePlugin.class); var manager = mock(UserDataManager.class);
        when(manager.getPlugin()).thenReturn(plugin); when(manager.getTimer()).thenReturn(mock(ScheduledExecutorService.class));
        var cache = new UserDataCache(manager, UUID.randomUUID()); cache.configureSharedStorage(v -> {}, Runnable::run);
        cache.updateCache(values); var executor = Executors.newSingleThreadExecutor();
        try {
            var mutation = executor.submit(() -> cache.tryMutateIntegers(operands, IntegerMutation.ADD, null));
            assertTrue(checked.await(5, TimeUnit.SECONDS)); operands.put("key64", 1); grown.countDown();
            var failure = assertThrows(ExecutionException.class, () -> mutation.get(5, TimeUnit.SECONDS));
            assertInstanceOf(IllegalArgumentException.class, failure.getCause());
            for (int i = 0; i < 65; i++) assertEquals(1, cache.getIfPresent("key" + i).getInt());
            assertFalse(cache.hasChangesToProcess());
        } finally { grown.countDown(); executor.shutdownNow(); }
    }
    @Test void notificationRejectionDoesNotHideAnAcceptedDebit() {
        var plugin = mock(AdvancedCorePlugin.class); var manager = mock(UserDataManager.class);
        when(manager.getPlugin()).thenReturn(plugin); when(manager.getTimer()).thenReturn(mock(ScheduledExecutorService.class));
        doThrow(new RejectedExecutionException("notification executor unavailable"))
                .when(manager).dispatchSharedUserDataNotification(any());
        var cache = new UserDataCache(manager, UUID.randomUUID());
        cache.configureSharedStorage(values -> {}, Runnable::run);
        cache.updateCache(new HashMap<>(Map.of("a", new DataValueInt(1))));
        var result = cache.tryMutateIntegers(Map.of("a", -1), IntegerMutation.DEBIT_POSITIVE, keys -> {});
        assertTrue(result.applied()); assertEquals(1, result.previous().get("a"));
        assertEquals(0, cache.getIfPresent("a").getInt()); assertTrue(cache.hasChangesToProcess());
    }
    @Test void checkpointReplacementDoesNotPermitDoubleDebit() throws Exception {
        var cache = cache(); var entered = new CountDownLatch(1); var replace = new CountDownLatch(1);
        var replaced = new CountDownLatch(1); var finish = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        try {
            var checkpoint = executor.submit(() -> cache.flushChangesAndRun(() -> {
                entered.countDown(); await(replace);
                cache.updateCache(new HashMap<>(Map.of("a", new DataValueInt(1), "b", new DataValueInt(2))));
                replaced.countDown(); await(finish);
            }));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertNull(cache.tryMutateIntegers(Map.of("a", -1), IntegerMutation.DEBIT_POSITIVE, null));
            replace.countDown(); assertTrue(replaced.await(5, TimeUnit.SECONDS));
            assertNull(cache.tryMutateIntegers(Map.of("a", -1), IntegerMutation.DEBIT_POSITIVE, null));
            finish.countDown(); checkpoint.get(5, TimeUnit.SECONDS);
            assertTrue(cache.tryMutateIntegers(Map.of("a", -1), IntegerMutation.DEBIT_POSITIVE, null).applied());
            assertFalse(cache.tryMutateIntegers(Map.of("a", -1), IntegerMutation.DEBIT_POSITIVE, null).applied());
            assertEquals(0, cache.getIfPresent("a").getInt());
        } finally { replace.countDown(); finish.countDown(); executor.shutdownNow(); }
    }

    @Test void refreshDuringInFlightWriteRetainsPriorIncrement() throws Exception {
        var plugin = mock(AdvancedCorePlugin.class); var manager = mock(UserDataManager.class);
        var users = mock(com.bencodez.advancedcore.api.user.UserManager.class);
        when(plugin.getUserManager()).thenReturn(users); when(manager.getPlugin()).thenReturn(plugin);
        when(manager.getTimer()).thenReturn(mock(ScheduledExecutorService.class));
        var entered = new CountDownLatch(1); var finish = new CountDownLatch(1);
        var stored = new ConcurrentHashMap<String, DataValue>();
        stored.put("a", new DataValueInt(1)); stored.put("b", new DataValueInt(2));
        var cache = new UserDataCache(manager, UUID.randomUUID());
        cache.configureSharedStorage(values -> { entered.countDown(); await(finish); stored.putAll(values); }, Runnable::run);
        cache.updateCache(new HashMap<>(stored));
        var executor = Executors.newSingleThreadExecutor();
        try {
            cache.tryMutateIntegers(Map.of("a", 1), IntegerMutation.ADD, null);
            cache.tryMutateIntegers(Map.of("b", 1), IntegerMutation.ADD, null);
            long version = cache.getSharedSnapshotVersion(); var stale = new HashMap<>(stored);
            var flush = executor.submit(cache::processChangesForSharedRuntime);
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            cache.updateSharedSnapshot(stale, version);
            assertEquals(2, cache.getIfPresent("a").getInt());
            cache.tryMutateIntegers(Map.of("a", 1), IntegerMutation.ADD, null);
            finish.countDown(); flush.get(5, TimeUnit.SECONDS);
            cache.processChangesForSharedRuntime();
            assertEquals(3, cache.getIfPresent("a").getInt());
            assertEquals(3, stored.get("a").getInt());
        } finally { finish.countDown(); executor.shutdownNow(); }
    }

    @Test void refreshCannotRestoreSpentCreditAfterInFlightWriteCompletes() throws Exception {
        var plugin = mock(AdvancedCorePlugin.class); var manager = mock(UserDataManager.class);
        when(plugin.getUserManager()).thenReturn(mock(com.bencodez.advancedcore.api.user.UserManager.class));
        when(manager.getPlugin()).thenReturn(plugin); when(manager.getTimer()).thenReturn(mock(ScheduledExecutorService.class));
        var entered = new CountDownLatch(1); var finish = new CountDownLatch(1);
        var stored = new ConcurrentHashMap<String, DataValue>();
        stored.put("a", new DataValueInt(1)); stored.put("b", new DataValueInt(2));
        var cache = new UserDataCache(manager, UUID.randomUUID());
        cache.configureSharedStorage(values -> { entered.countDown(); await(finish); stored.putAll(values); }, Runnable::run);
        cache.updateCache(new HashMap<>(stored)); var executor = Executors.newSingleThreadExecutor();
        try {
            assertTrue(cache.tryMutateIntegers(Map.of("a", -1), IntegerMutation.DEBIT_POSITIVE, null).applied());
            cache.tryMutateIntegers(Map.of("b", 1), IntegerMutation.ADD, null);
            var flush = executor.submit(cache::processChangesForSharedRuntime);
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            long version = cache.getSharedSnapshotVersion(); var stale = new HashMap<>(stored);
            finish.countDown(); flush.get(5, TimeUnit.SECONDS);
            cache.updateSharedSnapshot(stale, version);
            assertEquals(0, cache.getIfPresent("a").getInt());
            assertFalse(cache.tryMutateIntegers(Map.of("a", -1), IntegerMutation.DEBIT_POSITIVE, null).applied());
            assertEquals(0, stored.get("a").getInt());
        } finally { finish.countDown(); executor.shutdownNow(); }
    }

    @Test void refreshStartedAfterCompletedWriteCanObserveLaterStorageValue() {
        var plugin = mock(AdvancedCorePlugin.class); var manager = mock(UserDataManager.class);
        when(plugin.getUserManager()).thenReturn(mock(com.bencodez.advancedcore.api.user.UserManager.class));
        when(manager.getPlugin()).thenReturn(plugin); when(manager.getTimer()).thenReturn(mock(ScheduledExecutorService.class));
        var cache = new UserDataCache(manager, UUID.randomUUID());
        cache.configureSharedStorage(values -> {}, Runnable::run);
        cache.updateCache(new HashMap<>(Map.of("a", new DataValueInt(1))));
        cache.tryMutateIntegers(Map.of("a", 1), IntegerMutation.ADD, null);
        cache.processChangesForSharedRuntime();
        long version = cache.getSharedSnapshotVersion();
        cache.updateSharedSnapshot(new HashMap<>(Map.of("a", new DataValueInt(9))), version);
        assertEquals(9, cache.getIfPresent("a").getInt());
    }

    @Test void successfulRetryFencesRefreshAfterMutationMarkersAreCleared() throws Exception {
        var plugin = mock(AdvancedCorePlugin.class); var manager = mock(UserDataManager.class);
        when(plugin.getUserManager()).thenReturn(mock(com.bencodez.advancedcore.api.user.UserManager.class));
        when(manager.getPlugin()).thenReturn(plugin); when(manager.getTimer()).thenReturn(mock(ScheduledExecutorService.class));
        var entered = new CountDownLatch(1); var fail = new CountDownLatch(1);
        var retryEntered = new CountDownLatch(1); var retryFinish = new CountDownLatch(1);
        var attempts = new AtomicInteger(); var stored = new ConcurrentHashMap<String, DataValue>();
        stored.put("a", new DataValueInt(1)); stored.put("b", new DataValueInt(2));
        var cache = new UserDataCache(manager, UUID.randomUUID());
        cache.configureSharedStorage(values -> {
            if (attempts.getAndIncrement() == 0) { entered.countDown(); await(fail); throw new IllegalStateException("write failed"); }
            retryEntered.countDown(); await(retryFinish); stored.putAll(values);
        }, Runnable::run);
        cache.updateCache(new HashMap<>(stored)); var executor = Executors.newSingleThreadExecutor();
        try {
            assertTrue(cache.tryMutateIntegers(Map.of("a", -1, "b", -1), IntegerMutation.DEBIT_POSITIVE, null).applied());
            var first = executor.submit(cache::processChangesForSharedRuntime);
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            cache.updateSharedSnapshot(new HashMap<>(stored), cache.getSharedSnapshotVersion());
            fail.countDown(); assertThrows(ExecutionException.class, () -> first.get(5, TimeUnit.SECONDS));
            var retry = executor.submit(cache::processChangesForSharedRuntime);
            assertTrue(retryEntered.await(5, TimeUnit.SECONDS));
            long version = cache.getSharedSnapshotVersion(); var stale = new HashMap<>(stored);
            retryFinish.countDown(); retry.get(5, TimeUnit.SECONDS);
            cache.updateSharedSnapshot(stale, version);
            assertEquals(0, cache.getIfPresent("a").getInt());
            assertEquals(1, cache.getIfPresent("b").getInt());
            assertFalse(cache.tryMutateIntegers(Map.of("a", -1), IntegerMutation.DEBIT_POSITIVE, null).applied());
            assertEquals(0, stored.get("a").getInt());
        } finally { fail.countDown(); retryFinish.countDown(); executor.shutdownNow(); }
    }

    @Test void checkpointFailureStillPreservesStagedValuesAndNotification() throws Exception {
        var cache = cache(); var entered = new CountDownLatch(1); var replace = new CountDownLatch(1);
        var notices = new AtomicInteger(); var executor = Executors.newSingleThreadExecutor();
        try {
            var checkpoint = executor.submit(() -> cache.flushChangesAndRun(() -> {
                entered.countDown(); await(replace);
                cache.updateCachePreservingPending(new HashMap<>(Map.of("a", new DataValueInt(1), "b", new DataValueInt(2))));
                throw new IllegalStateException("checkpoint failure");
            }));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertNull(cache.tryMutateIntegers(Map.of("a", -1, "b", -1), IntegerMutation.DEBIT_POSITIVE,
                    keys -> notices.incrementAndGet()));
            replace.countDown();
            assertThrows(ExecutionException.class, () -> checkpoint.get(5, TimeUnit.SECONDS));
            assertEquals(0, notices.get());
            assertTrue(cache.tryMutateIntegers(Map.of("a", -1, "b", -1), IntegerMutation.DEBIT_POSITIVE,
                    keys -> notices.incrementAndGet()).applied());
            assertEquals(0, cache.getIfPresent("a").getInt());
            assertEquals(1, cache.getIfPresent("b").getInt());
            assertEquals(1, notices.get()); assertTrue(cache.hasChangesToProcess());
        } finally { replace.countDown(); executor.shutdownNow(); }
    }

    @Test void failedInFlightWriteRequeuesNewerMutationsAfterRefresh() throws Exception {
        var plugin = mock(AdvancedCorePlugin.class); var manager = mock(UserDataManager.class);
        when(plugin.getUserManager()).thenReturn(mock(com.bencodez.advancedcore.api.user.UserManager.class));
        when(manager.getPlugin()).thenReturn(plugin); when(manager.getTimer()).thenReturn(mock(ScheduledExecutorService.class));
        var entered = new CountDownLatch(1); var finish = new CountDownLatch(1); var attempts = new AtomicInteger();
        var stored = new ConcurrentHashMap<String, DataValue>();
        stored.put("a", new DataValueInt(1)); stored.put("b", new DataValueInt(2));
        var cache = new UserDataCache(manager, UUID.randomUUID());
        cache.configureSharedStorage(values -> {
            if (attempts.getAndIncrement() == 0) { entered.countDown(); await(finish); throw new IllegalStateException("write failed"); }
            stored.putAll(values);
        }, Runnable::run);
        cache.updateCache(new HashMap<>(stored)); var executor = Executors.newSingleThreadExecutor();
        try {
            cache.tryMutateIntegers(Map.of("a", 1), IntegerMutation.ADD, null);
            cache.tryMutateIntegers(Map.of("b", 1), IntegerMutation.ADD, null);
            long version = cache.getSharedSnapshotVersion(); var stale = new HashMap<>(stored);
            var flush = executor.submit(cache::processChangesForSharedRuntime);
            assertTrue(entered.await(5, TimeUnit.SECONDS)); cache.updateSharedSnapshot(stale, version);
            cache.tryMutateIntegers(Map.of("a", 1), IntegerMutation.ADD, null);
            finish.countDown(); assertThrows(ExecutionException.class, () -> flush.get(5, TimeUnit.SECONDS));
            assertEquals(3, cache.getIfPresent("a").getInt());
            cache.processChangesForSharedRuntime(); assertEquals(3, stored.get("a").getInt());
        } finally { finish.countDown(); executor.shutdownNow(); }
    }

    private static void await(CountDownLatch latch) {
        try { if (!latch.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("fixture timeout"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
    }
    private UserDataCache cache() {
        var plugin = mock(AdvancedCorePlugin.class); var manager = mock(UserDataManager.class);
        var timer = mock(ScheduledExecutorService.class);
        when(manager.getPlugin()).thenReturn(plugin); when(manager.getTimer()).thenReturn(timer);
        doAnswer(call -> { call.<Runnable>getArgument(0).run(); return null; }).when(manager).dispatchSharedUserDataNotification(any());
        var cache = new UserDataCache(manager, UUID.randomUUID());
        cache.configureSharedStorage(values -> {}, Runnable::run);
        var values = new HashMap<String,DataValue>(); values.put("a", new DataValueInt(1)); values.put("b", new DataValueInt(2));
        cache.updateCache(values); return cache;
    }
    @Test void concurrentUpdatesCannotLoseCredits() throws Exception {
        var cache = cache(); var executor = Executors.newFixedThreadPool(2); var start = new CountDownLatch(1);
        try {
            var first = executor.submit(() -> { start.await(); for (int i=0;i<500;i++) assertNotNull(cache.tryMutateIntegers(Map.of("a",1), IntegerMutation.ADD,null)); return null; });
            var second = executor.submit(() -> { start.await(); for (int i=0;i<500;i++) assertNotNull(cache.tryMutateIntegers(Map.of("a",1), IntegerMutation.ADD,null)); return null; });
            start.countDown(); first.get(5,TimeUnit.SECONDS); second.get(5,TimeUnit.SECONDS);
            assertEquals(1001,cache.getIfPresent("a").getInt());
        } finally { executor.shutdownNow(); }
    }
    @Test void dualDebitReturnsOriginalBalancesAndSkipsEmptyPool() {
        var cache=cache(); cache.tryMutateIntegers(Map.of("a",0),IntegerMutation.SET,null);
        var result=cache.tryMutateIntegers(Map.of("a",-1,"b",-1),IntegerMutation.DEBIT_POSITIVE,null);
        assertTrue(result.applied()); assertEquals(Map.of("a",0,"b",2),result.previous());
        assertEquals(0,cache.getIfPresent("a").getInt()); assertEquals(1,cache.getIfPresent("b").getInt());
    }
    @Test void coldRetiredAndMissingKeysFailWithoutMutation() {
        var cache=cache(); cache.invalidateStorageSnapshot("a");
        assertNull(cache.tryMutateIntegers(Map.of("a",1),IntegerMutation.ADD,null));
        assertEquals(2,cache.getIfPresent("b").getInt());
        cache.beginRemoval(); assertNull(cache.tryMutateIntegers(Map.of("b",1),IntegerMutation.ADD,null));
    }
    @Test void callbackRunsOutsideMonitorAndCanReenter() {
        var cache=cache(); var called=new AtomicInteger();
        var result=cache.tryMutateIntegers(Map.of("a",1),IntegerMutation.ADD,keys -> {
            assertFalse(Thread.holdsLock(cache)); assertEquals(Set.of("a"),keys); called.incrementAndGet();
            cache.tryMutateIntegers(Map.of("b",1),IntegerMutation.ADD,null);
        });
        assertTrue(result.applied()); assertEquals(1,called.get()); assertEquals(3,cache.getIfPresent("b").getInt());
    }
    @Test void exclusiveFlushDefersArithmeticUntilAfterCheckpoint() throws Exception {
        var cache = cache(); var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var notices = new AtomicInteger(); var executor = Executors.newSingleThreadExecutor();
        try {
            var flush = executor.submit(() -> cache.flushChangesAndRun(() -> {
                entered.countDown();
                try { if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("fixture timeout"); }
                catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
            }));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            var result = cache.tryMutateIntegers(Map.of("a",-1,"b",-1),IntegerMutation.DEBIT_POSITIVE,
                    keys -> { assertFalse(Thread.holdsLock(cache)); notices.incrementAndGet(); });
            assertNull(result); assertEquals(1,cache.getIfPresent("a").getInt());
            assertEquals(2,cache.getIfPresent("b").getInt()); assertEquals(0,notices.get());
            release.countDown(); flush.get(5,TimeUnit.SECONDS);
            result = cache.tryMutateIntegers(Map.of("a",-1,"b",-1),IntegerMutation.DEBIT_POSITIVE,
                    keys -> { assertFalse(Thread.holdsLock(cache)); notices.incrementAndGet(); });
            assertTrue(result.applied()); assertEquals(0,cache.getIfPresent("a").getInt());
            assertEquals(1,cache.getIfPresent("b").getInt()); assertEquals(1,notices.get());
        } finally { release.countDown(); executor.shutdownNow(); }
    }
    @Test void missingOneFieldDoesNotMutateAnyField() {
        var cache=cache(); assertNull(cache.tryMutateIntegers(Map.of("a",1,"missing",1),IntegerMutation.ADD,null));
        assertEquals(1,cache.getIfPresent("a").getInt());
    }
    @Test void emptyDebitChangesNothingAndAbsoluteSetAndClampRemainAtomic() {
        var cache=cache(); cache.tryMutateIntegers(Map.of("a",0,"b",0),IntegerMutation.SET,null);
        var result=cache.tryMutateIntegers(Map.of("a",-1,"b",-1),IntegerMutation.DEBIT_POSITIVE,null);
        assertFalse(result.applied()); assertEquals(Map.of("a",0,"b",0),result.previous());
        cache.tryMutateIntegers(Map.of("a",-2),IntegerMutation.ADD_FLOOR_ZERO,null);
        assertEquals(0,cache.getIfPresent("a").getInt());
    }

    @Test void overflowRejectsWholeBatchBeforeCacheQueueOrNotification() {
        for (var operation : List.of(IntegerMutation.ADD, IntegerMutation.ADD_FLOOR_ZERO)) {
            for (var boundary : List.of(Integer.MAX_VALUE, Integer.MIN_VALUE)) {
                var cache = cache();
                cache.updateCache(new HashMap<>(Map.of("a", new DataValueInt(2), "b", new DataValueInt(boundary))));
                var operands = new LinkedHashMap<String, Integer>();
                operands.put("a", 1);
                operands.put("b", boundary == Integer.MAX_VALUE ? 1 : -1);
                var notifications = new AtomicInteger();
                assertFalse(cache.hasChangesToProcess());
                assertThrows(ArithmeticException.class, () -> cache.tryMutateIntegers(operands, operation,
                        keys -> notifications.incrementAndGet()));
                assertEquals(2, cache.getIfPresent("a").getInt());
                assertEquals(boundary, cache.getIfPresent("b").getInt());
                assertFalse(cache.hasChangesToProcess());
                assertEquals(0, notifications.get());
            }
        }
    }

    @Test void validBoundarySetDebitAndFloorOperationsRemainSupported() {
        var cache = cache();
        assertTrue(cache.tryMutateIntegers(Map.of("a", Integer.MAX_VALUE), IntegerMutation.SET, null).applied());
        assertTrue(cache.tryMutateIntegers(Map.of("a", -1), IntegerMutation.DEBIT_POSITIVE, null).applied());
        assertEquals(Integer.MAX_VALUE - 1, cache.getIfPresent("a").getInt());
        assertTrue(cache.tryMutateIntegers(Map.of("a", 1), IntegerMutation.ADD, null).applied());
        assertEquals(Integer.MAX_VALUE, cache.getIfPresent("a").getInt());
        assertTrue(cache.tryMutateIntegers(Map.of("b", -3), IntegerMutation.ADD_FLOOR_ZERO, null).applied());
        assertEquals(0, cache.getIfPresent("b").getInt());
    }

    @Test void conditionalDebitsAndAwardsAcrossBothFieldsCannotLoseUpdates() throws Exception {
        var cache=cache(); var executor=Executors.newFixedThreadPool(2); var start=new CountDownLatch(1);
        try {
            var debit=executor.submit(() -> { start.await(); return cache.tryMutateIntegers(Map.of("a",-1,"b",-1),IntegerMutation.DEBIT_POSITIVE,null); });
            var award=executor.submit(() -> { start.await(); return cache.tryMutateIntegers(Map.of("a",1,"b",1),IntegerMutation.ADD,null); });
            start.countDown(); assertTrue(debit.get(5,TimeUnit.SECONDS).applied()); assertTrue(award.get(5,TimeUnit.SECONDS).applied());
            assertEquals(1,cache.getIfPresent("a").getInt()); assertEquals(2,cache.getIfPresent("b").getInt());
        } finally { executor.shutdownNow(); }
    }
    @Test void absoluteSetAndIncrementAreSerializedWithoutLosingEitherOperation() throws Exception {
        var cache=cache(); var executor=Executors.newFixedThreadPool(2); var start=new CountDownLatch(1);
        try {
            var set=executor.submit(() -> { start.await(); return cache.tryMutateIntegers(Map.of("a",10),IntegerMutation.SET,null); });
            var add=executor.submit(() -> { start.await(); return cache.tryMutateIntegers(Map.of("a",1),IntegerMutation.ADD,null); });
            start.countDown(); assertTrue(set.get(5,TimeUnit.SECONDS).applied()); assertTrue(add.get(5,TimeUnit.SECONDS).applied());
            int actual=cache.getIfPresent("a").getInt(); assertTrue(actual==10 || actual==11, "result must match a serial ordering");
        } finally { executor.shutdownNow(); }
    }
    @Test void rejectedSchedulingStillLeavesTheWholeAcceptedBatchQueued() {
        var plugin=mock(AdvancedCorePlugin.class); var manager=mock(UserDataManager.class); var timer=mock(ScheduledExecutorService.class);
        when(manager.getPlugin()).thenReturn(plugin); when(manager.getTimer()).thenReturn(timer);
        when(timer.schedule(any(Runnable.class),anyLong(),any(TimeUnit.class))).thenThrow(new RejectedExecutionException("synthetic"));
        var cache=new UserDataCache(manager,UUID.randomUUID()); cache.configureSharedStorage(values -> {},Runnable::run);
        var values=new HashMap<String,DataValue>(); values.put("a",new DataValueInt(2)); values.put("b",new DataValueInt(2)); cache.updateCache(values);
        var result=cache.tryMutateIntegers(Map.of("a",-1,"b",-1),IntegerMutation.DEBIT_POSITIVE,null);
        assertTrue(result.applied()); assertEquals(1,cache.getIfPresent("a").getInt()); assertEquals(1,cache.getIfPresent("b").getInt());
        assertTrue(cache.hasChangesToProcess(),"accepted values must remain queued after rejection");
    }

    @Test void negativeDebitMustNotCreateCredits() {
        var cache=cache(); assertThrows(IllegalArgumentException.class,
                () -> cache.tryMutateIntegers(Map.of("a",1),IntegerMutation.DEBIT_POSITIVE,null));
        assertEquals(1,cache.getIfPresent("a").getInt());
    }
}
