package com.bencodez.advancedcore.tests.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.advancedcore.api.user.usercache.UserDataManager;
import com.bencodez.advancedcore.bukkit.user.storage.BukkitSqlUserStorage;
import com.bencodez.advancedcore.core.user.storage.SqlUserStorage;
import com.bencodez.advancedcore.core.user.storage.sql.SqlUserBackend;
import com.bencodez.simpleapi.sql.Column;
import com.bencodez.simpleapi.sql.data.DataValue;
import com.bencodez.simpleapi.sql.data.DataValueInt;

class SharedLegacyBackendRoutingTest {
    @Test
    void legacyAdapterUsesBoundReplacementBackendRegardlessOfPluginStorageType() {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        UserDataManager manager = new UserDataManager(plugin);
        when(plugin.getUserManager()).thenReturn(mock(com.bencodez.advancedcore.api.user.UserManager.class));
        UUID uuid = UUID.randomUUID();
        RecordingStorage target = new RecordingStorage();
        SqlUserBackend backend = new SqlUserBackend() {
            public UserStorage storageType() { return UserStorage.SQLITE; }
            public SqlUserStorage user(UUID requested) { assertEquals(uuid, requested); return target; }
            public List<UUID> enumerateUsers() { return List.of(uuid); }
            public boolean isOpen() { return true; }
            public void close() {}
        };
        manager.bindSharedSqlBackend(backend, Runnable::run);
        when(plugin.getUserManager().getDataManager()).thenReturn(manager);
        BukkitSqlUserStorage adapter = new BukkitSqlUserStorage(() -> plugin, () -> uuid.toString());
        DataValueInt value = new DataValueInt(9);
        adapter.write(UserStorage.MYSQL, "Points", value);
        assertEquals(UserStorage.SQLITE, target.lastStorage);
        assertSame(value, target.lastValue);
        manager.getTimer().shutdownNow();
    }

    private static final class RecordingStorage implements SqlUserStorage {
        UserStorage lastStorage;
        DataValue lastValue;
        public List<Column> readRow(UserStorage storage) { lastStorage = storage; return List.of(); }
        public boolean contains(UserStorage storage) { lastStorage = storage; return true; }
        public void delete(UserStorage storage) { lastStorage = storage; }
        public void write(UserStorage storage, String key, DataValue value) { lastStorage = storage; lastValue = value; }
        public void writeValues(UserStorage storage, HashMap<String, DataValue> values) { lastStorage = storage; }
    }
}
