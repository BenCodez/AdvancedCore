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
            assertTrue(cache.tryMutateIntegers(Map.of("a", 1), IntegerMutation.ADD, null).applied());
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
            assertTrue(cache.tryMutateIntegers(Map.of("a", -1), IntegerMutation.DEBIT_POSITIVE, null).applied());
            replace.countDown(); assertTrue(replaced.await(5, TimeUnit.SECONDS));
            assertFalse(cache.tryMutateIntegers(Map.of("a", -1), IntegerMutation.DEBIT_POSITIVE, null).applied());
            finish.countDown(); checkpoint.get(5, TimeUnit.SECONDS);
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
            assertTrue(cache.tryMutateIntegers(Map.of("a", -1, "b", -1), IntegerMutation.DEBIT_POSITIVE,
                    keys -> notices.incrementAndGet()).applied());
            replace.countDown();
            assertThrows(ExecutionException.class, () -> checkpoint.get(5, TimeUnit.SECONDS));
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
    @Test void exclusiveFlushStagesOneBatchAndDelaysNotification() throws Exception {
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
            assertTrue(result.applied()); assertEquals(0,cache.getIfPresent("a").getInt());
            assertEquals(1,cache.getIfPresent("b").getInt()); assertEquals(0,notices.get());
            release.countDown(); flush.get(5,TimeUnit.SECONDS); assertEquals(1,notices.get());
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
