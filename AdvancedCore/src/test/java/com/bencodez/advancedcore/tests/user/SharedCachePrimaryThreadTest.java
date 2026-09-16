package com.bencodez.advancedcore.tests.user;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Server;
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

class SharedCachePrimaryThreadTest {
    @Test
    void cachedReadsAndQueuedMutationsRemainAllowedOnThePrimaryThread() {
        UUID cached = UUID.randomUUID();
        UUID uncached = UUID.randomUUID();
        MemoryBackend backend = new MemoryBackend();
        MemoryCache cache = new MemoryCache();
        cache.populate(cached, new HashMap<>(Map.of("Points", new DataValueInt(1))));
        SharedUserDataRuntime runtime = new SharedUserDataRuntime(backend, cache);

        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getServer).thenReturn(mock(Server.class));
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            assertEquals(1, runtime.read(cached, "Points", UserDataFetchMode.CACHE_ONLY, null,
                    new DataValueInt(0)).getInt());
            assertDoesNotThrow(() -> runtime.queueChange(cached, "Points", new DataValueInt(7)));
            assertEquals(7, cache.getIfPresent(cached, "Points").getInt());
            assertThrows(IllegalStateException.class,
                    () -> runtime.queueChange(uncached, "Points", new DataValueInt(9)));
        }
    }

    private static final class MemoryCache implements UserCacheOwner {
        private final Map<UUID, HashMap<String, DataValue>> values = new HashMap<>();
        public boolean isCached(UUID uuid) { return values.containsKey(uuid); }
        public DataValue getIfPresent(UUID uuid, String key) {
            HashMap<String, DataValue> row = values.get(uuid);
            return row == null ? null : row.get(key);
        }
        public void populate(UUID uuid, HashMap<String, DataValue> row) { values.put(uuid, new HashMap<>(row)); }
        public void queueChange(UUID uuid, String key, DataValue value) { values.get(uuid).put(key, value); }
        public void flush(UUID uuid, SqlUserStorage storage) {}
        public Set<UUID> cachedUsers() { return Set.copyOf(values.keySet()); }
        public void remove(UUID uuid) { values.remove(uuid); }
        public void clearAfterFlush() { values.clear(); }
        public void shutdown() {}
        @Override public void requireBlockingAllowed() {
            if (Bukkit.getServer() != null && Bukkit.isPrimaryThread()) throw new IllegalStateException("blocking SQL");
        }
    }

    private static final class MemoryBackend implements SqlUserBackend {
        public UserStorage storageType() { return UserStorage.SQLITE; }
        public SqlUserStorage user(UUID uuid) {
            return new SqlUserStorage() {
                public List<Column> readRow(UserStorage storage) { return List.of(); }
                public boolean contains(UserStorage storage) { return false; }
                public void delete(UserStorage storage) {}
                public void write(UserStorage storage, String key, DataValue value) {}
                public void writeValues(UserStorage storage, HashMap<String, DataValue> values) {}
            };
        }
        public List<UUID> enumerateUsers() { return List.of(); }
        public boolean isOpen() { return true; }
        public void close() {}
    }
}
