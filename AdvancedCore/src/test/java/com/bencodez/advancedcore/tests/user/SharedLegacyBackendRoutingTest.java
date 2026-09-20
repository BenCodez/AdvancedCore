package com.bencodez.advancedcore.tests.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.advancedcore.api.user.usercache.UserDataManager;
import com.bencodez.advancedcore.bukkit.user.storage.BukkitSqlUserStorage;
import com.bencodez.advancedcore.core.user.storage.SqlUserStorage;
import com.bencodez.advancedcore.core.user.storage.sql.SqlUserBackend;
import com.bencodez.simpleapi.sql.Column;
import com.bencodez.advancedcore.api.user.UserManager;
import com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL;
import com.bencodez.advancedcore.api.user.userstorage.sql.UserTable;
import com.bencodez.simpleapi.sql.data.DataValue;
import com.bencodez.simpleapi.sql.data.DataValueInt;
import com.bencodez.advancedcore.core.user.runtime.SharedUserDataRuntime;

class SharedLegacyBackendRoutingTest {
    @Test
    void directLegacyCallCannotSlipIntoAnActiveBindingTransition() {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        UserManager users = mock(UserManager.class);
        UserDataManager manager = new UserDataManager(plugin);
        MySQL legacy = mock(MySQL.class);
        when(plugin.getUserManager()).thenReturn(users);
        when(users.getDataManager()).thenReturn(manager);
        when(plugin.getMysql()).thenReturn(legacy);
        UUID uuid = UUID.randomUUID();
        BukkitSqlUserStorage adapter = new BukkitSqlUserStorage(() -> plugin, uuid::toString);
        manager.beginSharedBindingTransition();
        try {
            assertThrows(IllegalStateException.class, () -> adapter.contains(UserStorage.MYSQL));
            verifyNoInteractions(legacy);
        } finally {
            manager.endSharedBindingTransition();
            manager.getTimer().shutdownNow();
        }
    }

	@Test
	void managerPresentLegacyRoutePreservesOpaqueStringIdentifiers() {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		UserManager users = mock(UserManager.class);
		UserDataManager manager = new UserDataManager(plugin);
		MySQL legacy = mock(MySQL.class);
		when(plugin.getUserManager()).thenReturn(users);
		when(users.getDataManager()).thenReturn(manager);
		when(plugin.getMysql()).thenReturn(legacy);
		when(legacy.containsKey("legacy-user-key")).thenReturn(true);
		try {
			BukkitSqlUserStorage adapter = new BukkitSqlUserStorage(() -> plugin, () -> "legacy-user-key");
			assertTrue(adapter.contains(UserStorage.MYSQL));
			verify(legacy).containsKey("legacy-user-key");
		} finally {
			manager.getTimer().shutdownNow();
		}
	}

    @Test
    void directLegacyWriteIsAdmittedBeforeAReplacementCanBePublished() throws Exception {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        UserManager users = mock(UserManager.class);
        UserDataManager manager = new UserDataManager(plugin);
        MySQL legacy = mock(MySQL.class);
        when(plugin.getUserManager()).thenReturn(users);
        when(users.getDataManager()).thenReturn(manager);
        when(plugin.getMysql()).thenReturn(legacy);
        UUID uuid = UUID.randomUUID();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            entered.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return null;
        }).when(legacy).update(eq(uuid.toString()), eq("Points"), any(DataValue.class));
        BukkitSqlUserStorage adapter = new BukkitSqlUserStorage(() -> plugin, uuid::toString);
        var worker = Executors.newSingleThreadExecutor();
        try {
            var write = worker.submit(() -> adapter.write(UserStorage.MYSQL, "Points", new DataValueInt(1)));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertThrows(IllegalStateException.class, manager::beginSharedBindingTransition);
            release.countDown();
            write.get(5, TimeUnit.SECONDS);
            manager.beginSharedBindingTransition();
            manager.endSharedBindingTransition();
            verify(legacy).update(eq(uuid.toString()), eq("Points"), any(DataValue.class));
        } finally {
            release.countDown();
            worker.shutdownNow();
            manager.getTimer().shutdownNow();
        }
    }

    @Test
    void legacyAdapterRejectsCrossStoreWriteInsteadOfSilentlyWritingTheSharedStore() {
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
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> adapter.write(UserStorage.MYSQL, "Points", value));
        assertTrue(failure.getMessage().contains("MYSQL"));
        assertTrue(failure.getMessage().contains("SQLITE"));
        assertEquals(null, target.lastStorage);
        assertEquals(null, target.lastValue);
        manager.getTimer().shutdownNow();
    }

    @Test
    void legacyAdapterPreservesTheExplicitTargetWhenItMatchesTheSharedStore() {
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
        try {
            new BukkitSqlUserStorage(() -> plugin, uuid::toString)
                    .write(UserStorage.SQLITE, "Points", new DataValueInt(9));
            assertEquals(UserStorage.SQLITE, target.lastStorage);
            assertEquals(9, target.lastValue.getInt());
        } finally {
            manager.getTimer().shutdownNow();
        }
    }

    @Test
    void explicitRuntimeMaintenanceCanUseTheConverterTargetWithoutWeakeningNormalCrossStoreProtection() {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        UserManager users = mock(UserManager.class);
        UserDataManager manager = new UserDataManager(plugin);
        UserTable sqlite = mock(UserTable.class);
        UUID uuid = UUID.randomUUID();
        SqlUserBackend backend = mock(SqlUserBackend.class);
        SharedUserDataRuntime runtime = mock(SharedUserDataRuntime.class);
        when(plugin.getUserManager()).thenReturn(users);
        when(users.getDataManager()).thenReturn(manager);
        when(plugin.getSQLiteUserTable()).thenReturn(sqlite);
        when(backend.storageType()).thenReturn(UserStorage.MYSQL);
        when(backend.isOpen()).thenReturn(true);
        manager.bindSharedSqlBackend(backend, Runnable::run);
        manager.bindSharedRuntime(runtime);
        doAnswer(call -> {
            call.getArgument(0, Runnable.class).run();
            return null;
        }).when(runtime).runStorageMaintenance(any(Runnable.class));
        BukkitSqlUserStorage adapter = new BukkitSqlUserStorage(() -> plugin, uuid::toString);
        try {
            assertThrows(IllegalStateException.class,
                    () -> adapter.write(UserStorage.SQLITE, "Points", new DataValueInt(1)));

            manager.runStorageMaintenance(() ->
                    adapter.write(UserStorage.SQLITE, "Points", new DataValueInt(2)));

            verify(runtime).runStorageMaintenance(any(Runnable.class));
            verify(sqlite).update(any(Column.class), any());
        } finally {
            manager.getTimer().shutdownNow();
        }
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
