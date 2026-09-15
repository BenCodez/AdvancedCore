package com.bencodez.advancedcore.tests.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.api.user.UserDataFetchMode;
import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.advancedcore.core.user.runtime.SharedUserDataRuntime;
import com.bencodez.advancedcore.core.user.runtime.UserCacheOwner;
import com.bencodez.advancedcore.core.user.storage.SqlUserStorage;
import com.bencodez.advancedcore.core.user.storage.sql.SqlUserBackend;
import com.bencodez.simpleapi.sql.Column;
import com.bencodez.simpleapi.sql.data.DataValue;
import com.bencodez.simpleapi.sql.data.DataValueInt;
import com.bencodez.simpleapi.sql.data.DataValueString;

class SharedUserDataRuntimeTest {
    @Test
    void preservesTempCacheThenCacheThenStoragePrecedenceAcrossFetchModes() {
        UUID uuid = UUID.randomUUID();
        FakeBackend backend = new FakeBackend();
        backend.put(uuid, "value", new DataValueString("storage"));
        FakeCacheOwner cache = new FakeCacheOwner();
        cache.populate(uuid, values("value", new DataValueString("cache")));
        SharedUserDataRuntime runtime = new SharedUserDataRuntime(backend, cache);

        HashMap<String, DataValue> temp = values("value", new DataValueString("temp"));
        assertEquals("temp", runtime.read(uuid, "value", UserDataFetchMode.DEFAULT, temp,
                new DataValueString("default")).getString());
        assertEquals("cache", runtime.read(uuid, "value", UserDataFetchMode.CACHE_ONLY, null,
                new DataValueString("default")).getString());
        assertEquals("storage", runtime.read(uuid, "value", UserDataFetchMode.NO_CACHE, null,
                new DataValueString("default")).getString());
        assertEquals("default", runtime.read(uuid, "missing", UserDataFetchMode.TEMP_ONLY, temp,
                new DataValueString("default")).getString());
        assertEquals("default", runtime.read(uuid, "missing", UserDataFetchMode.NO_DB_LOOKUP, null,
                new DataValueString("default")).getString());

        UUID uncached = UUID.randomUUID();
        backend.put(uncached, "value", new DataValueString("direct"));
        int populations = cache.populateCalls;
        assertEquals("direct", runtime.read(uncached, "value", UserDataFetchMode.NO_WAIT, null,
                new DataValueString("default")).getString());
        assertEquals(populations, cache.populateCalls);
    }

    @Test
    void startupEnumerationCanPopulateExistingCacheOwner() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        FakeBackend backend = new FakeBackend();
        backend.put(first, "Points", new DataValueInt(2));
        backend.put(second, "Points", new DataValueInt(7));
        FakeCacheOwner cache = new FakeCacheOwner();
        SharedUserDataRuntime runtime = new SharedUserDataRuntime(backend, cache);
        Set<UUID> seen = new HashSet<>();

        int count = runtime.startupForEach((uuid, values) -> seen.add(uuid), true);

        assertEquals(2, count);
        assertEquals(Set.of(first, second), seen);
        assertEquals(2, cache.getIfPresent(first, "Points").getInt());
        assertEquals(7, cache.getIfPresent(second, "Points").getInt());
    }

    @Test
    void queuedChangesFlushBeforeShutdownAndBackendReplacement() {
        UUID uuid = UUID.randomUUID();
        FakeBackend first = new FakeBackend();
        first.put(uuid, "Points", new DataValueInt(1));
        FakeCacheOwner cache = new FakeCacheOwner();
        SharedUserDataRuntime runtime = new SharedUserDataRuntime(first, cache);

        runtime.queueChange(uuid, "Points", new DataValueInt(5));
        runtime.flush(uuid);
        assertEquals(5, first.value(uuid, "Points").getInt());

        FakeBackend second = new FakeBackend();
        runtime.queueChange(uuid, "Points", new DataValueInt(9));
        runtime.replaceBackend(second);
        assertEquals(9, first.value(uuid, "Points").getInt());
        assertFalse(first.isOpen());
        assertSame(second, runtime.backend());
        assertTrue(cache.cachedUsers().isEmpty());

        runtime.queueChange(uuid, "Points", new DataValueInt(11));
        runtime.close();
        assertEquals(11, second.value(uuid, "Points").getInt());
        assertFalse(second.isOpen());
        assertTrue(cache.shutdown);
        assertTrue(runtime.isClosed());
    }

	@Test
	void failedOldBackendCloseDoesNotTearDownThePublishedReplacement() {
		SqlUserBackend old = mock(SqlUserBackend.class);
		SqlUserBackend replacement = mock(SqlUserBackend.class);
		SqlUserBackend laterReplacement = mock(SqlUserBackend.class);
		when(old.isOpen()).thenReturn(true);
		when(replacement.isOpen()).thenReturn(true);
		when(laterReplacement.isOpen()).thenReturn(true);
		when(old.storageType()).thenReturn(UserStorage.SQLITE);
		when(replacement.storageType()).thenReturn(UserStorage.MYSQL);
		when(laterReplacement.storageType()).thenReturn(UserStorage.SQLITE);
		java.util.concurrent.atomic.AtomicBoolean firstClose = new java.util.concurrent.atomic.AtomicBoolean(true);
		doAnswer(ignored -> {
			if (firstClose.getAndSet(false)) throw new IllegalStateException("old close failed");
			return null;
		}).when(old).close();
		SharedUserDataRuntime runtime = new SharedUserDataRuntime(old, new FakeCacheOwner());

		assertDoesNotThrow(() -> runtime.replaceBackend(replacement));
		assertSame(replacement, runtime.backend());
		assertDoesNotThrow(() -> runtime.replaceBackend(laterReplacement));
		assertSame(laterReplacement, runtime.backend());
		verify(old, times(2)).close();
	}

	@Test
	void successfulShutdownDiscardsFinalFlushNotifications() {
		UUID uuid = UUID.randomUUID();
		FakeBackend backend = new FakeBackend();
		backend.put(uuid, "Points", new DataValueInt(1));
		FakeCacheOwner cache = new FakeCacheOwner();
		SharedUserDataRuntime runtime = new SharedUserDataRuntime(backend, cache);
		runtime.queueChange(uuid, "Points", new DataValueInt(2));
		boolean[] notified = { false };
		cache.notifyAfterFlush(uuid, () -> notified[0] = true);

		runtime.close();

		assertEquals(2, backend.value(uuid, "Points").getInt());
		assertFalse(notified[0]);
		assertTrue(cache.notifications.isEmpty());
	}

	@Test
	void failedFlushDoesNotDiscardQueueOrCloseBackend() {
        UUID uuid = UUID.randomUUID();
        FakeBackend backend = new FakeBackend();
        backend.put(uuid, "Points", new DataValueInt(1));
        FakeCacheOwner cache = new FakeCacheOwner();
        SharedUserDataRuntime runtime = new SharedUserDataRuntime(backend, cache);
        runtime.queueChange(uuid, "Points", new DataValueInt(3));
        backend.failWrites = true;

        assertThrows(IllegalStateException.class, runtime::close);
        assertTrue(backend.isOpen());
        assertFalse(runtime.isClosed());
        assertTrue(cache.hasPending(uuid));

        backend.failWrites = false;
        runtime.close();
		assertEquals(3, backend.value(uuid, "Points").getInt());
	}

	@Test
	void changeNotificationCanRequestExclusiveUserWorkAfterFlushAdmissionIsReleased() throws Exception {
		UUID uuid = UUID.randomUUID();
		FakeBackend backend = new FakeBackend();
		backend.put(uuid, "Points", new DataValueInt(1));
		FakeCacheOwner cache = new FakeCacheOwner();
		SharedUserDataRuntime runtime = new SharedUserDataRuntime(backend, cache);
		runtime.queueChange(uuid, "Points", new DataValueInt(2));
		cache.notifyAfterFlush(uuid, () -> runtime.remove(uuid));
		ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
			Thread thread = new Thread(task, "shared-user-notification-test");
			thread.setDaemon(true);
			return thread;
		});
		try {
			Future<?> flush = worker.submit(() -> runtime.flush(uuid));
			flush.get(2, TimeUnit.SECONDS);
			assertFalse(backend.rows.containsKey(uuid));
		} finally {
			worker.shutdownNow();
		}
	}

    private static HashMap<String, DataValue> values(String key, DataValue value) {
        HashMap<String, DataValue> result = new HashMap<>();
        result.put(key, value);
        return result;
    }

    private static final class FakeCacheOwner implements UserCacheOwner {
        private final Map<UUID, HashMap<String, DataValue>> cache = new HashMap<>();
        private final Map<UUID, HashMap<String, DataValue>> pending = new HashMap<>();
		private final Map<UUID, Runnable> notifications = new HashMap<>();
        private int populateCalls;
        private boolean shutdown;

        @Override
        public boolean isCached(UUID uuid) {
            return cache.containsKey(uuid);
        }

        @Override
        public DataValue getIfPresent(UUID uuid, String key) {
            HashMap<String, DataValue> values = cache.get(uuid);
            return values == null ? null : values.get(key);
        }

        @Override
        public void populate(UUID uuid, HashMap<String, DataValue> values) {
            populateCalls++;
            cache.put(uuid, new HashMap<>(values));
        }

        @Override
        public void queueChange(UUID uuid, String key, DataValue value) {
            cache.computeIfAbsent(uuid, ignored -> new HashMap<>()).put(key, value);
            pending.computeIfAbsent(uuid, ignored -> new HashMap<>()).put(key, value);
        }

        @Override
        public void flush(UUID uuid, SqlUserStorage storage) {
            HashMap<String, DataValue> changes = pending.get(uuid);
            if (changes == null || changes.isEmpty()) {
                return;
            }
            storage.writeValues(UserStorage.SQLITE, new HashMap<>(changes));
            pending.remove(uuid);
        }

		void notifyAfterFlush(UUID uuid, Runnable notification) {
			notifications.put(uuid, notification);
		}

		@Override
		public void dispatchNotifications(UUID uuid) {
			Runnable notification = notifications.remove(uuid);
			if (notification != null) notification.run();
		}

		@Override
		public void dispatchAllNotifications() {
			for (UUID uuid : Set.copyOf(notifications.keySet())) dispatchNotifications(uuid);
		}

		@Override
		public void discardAllNotifications() { notifications.clear(); }

        @Override
        public Set<UUID> cachedUsers() {
            return new HashSet<>(cache.keySet());
        }

        @Override
        public void remove(UUID uuid) {
            cache.remove(uuid);
            pending.remove(uuid);
        }

        @Override
        public void clearAfterFlush() {
            cache.clear();
        }

        @Override
        public void shutdown() {
            shutdown = true;
        }

        boolean hasPending(UUID uuid) {
            return pending.containsKey(uuid) && !pending.get(uuid).isEmpty();
        }
    }

    private static final class FakeBackend implements SqlUserBackend {
        private final Map<UUID, LinkedHashMap<String, DataValue>> rows = new LinkedHashMap<>();
        private boolean open = true;
        private boolean failWrites;

        void put(UUID uuid, String key, DataValue value) {
            rows.computeIfAbsent(uuid, ignored -> new LinkedHashMap<>()).put(key, value);
        }

        DataValue value(UUID uuid, String key) {
            Map<String, DataValue> row = rows.get(uuid);
            return row == null ? null : row.get(key);
        }

        @Override
        public UserStorage storageType() {
            return UserStorage.SQLITE;
        }

        @Override
        public SqlUserStorage user(UUID uuid) {
            if (!open) {
                throw new IllegalStateException("closed");
            }
            return new SqlUserStorage() {
                @Override
                public List<Column> readRow(UserStorage storage) {
                    ArrayList<Column> result = new ArrayList<>();
                    Map<String, DataValue> row = rows.get(uuid);
                    if (row != null) {
                        for (Map.Entry<String, DataValue> entry : row.entrySet()) {
                            result.add(new Column(entry.getKey(), entry.getValue()));
                        }
                    }
                    return result;
                }

                @Override
                public boolean contains(UserStorage storage) {
                    return rows.containsKey(uuid);
                }

                @Override
                public void delete(UserStorage storage) {
                    rows.remove(uuid);
                }

                @Override
                public void write(UserStorage storage, String key, DataValue value) {
                    if (failWrites) {
                        throw new IllegalStateException("write failed");
                    }
                    put(uuid, key, value);
                }

                @Override
                public void writeValues(UserStorage storage, HashMap<String, DataValue> values) {
                    if (failWrites) {
                        throw new IllegalStateException("write failed");
                    }
                    rows.computeIfAbsent(uuid, ignored -> new LinkedHashMap<>()).putAll(values);
                }
            };
        }

        @Override
        public List<UUID> enumerateUsers() {
            return new ArrayList<>(rows.keySet());
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public void close() {
            open = false;
        }
    }
}
