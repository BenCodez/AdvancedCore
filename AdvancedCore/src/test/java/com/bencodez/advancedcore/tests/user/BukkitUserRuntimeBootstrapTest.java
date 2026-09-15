package com.bencodez.advancedcore.tests.user;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.bukkit.Bukkit;
import org.bukkit.Server;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.advancedcore.api.user.usercache.UserDataManager;
import com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL;
import com.bencodez.advancedcore.bukkit.user.runtime.BukkitUserRuntimeBootstrap;

class BukkitUserRuntimeBootstrapTest {
    @Test void sharedAdapterRejectsPrimaryThreadStorageAccess() throws Exception {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        MySQL mysql = mock(MySQL.class);
        when(plugin.getStorageType()).thenReturn(UserStorage.MYSQL);
        when(plugin.getMysql()).thenReturn(mysql);
        UserDataManager manager = new UserDataManager(plugin);
        try {
            BukkitUserRuntimeBootstrap.bindAfterStorageInitialization(plugin, manager);
            try (var bukkit = mockStatic(Bukkit.class)) {
                bukkit.when(Bukkit::getServer).thenReturn(mock(Server.class));
                bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
                assertThrows(IllegalStateException.class, () -> manager.withSharedSqlBackend(UUID.randomUUID(),
                        (storage, user) -> user.readRow(storage)));
            }
            verify(mysql, never()).getExact(anyString());
        } finally {
            CountDownLatch retired = new CountDownLatch(1);
            assertTrue(manager.closeSharedRuntimeAsync(retired::countDown));
            assertTrue(retired.await(5, TimeUnit.SECONDS));
            manager.getTimer().shutdownNow();
        }
    }

    @Test void bindsOnlyAfterExistingMysqlStorageIsAvailableAndDoesNotOwnItsClose() throws Exception {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        MySQL mysql = mock(MySQL.class);
        when(plugin.getStorageType()).thenReturn(UserStorage.MYSQL);
        when(plugin.getMysql()).thenReturn(mysql);
        when(mysql.getExact(anyString())).thenReturn(new ArrayList<>());
        UserDataManager manager = new UserDataManager(plugin);
        try {
            BukkitUserRuntimeBootstrap.bindAfterStorageInitialization(plugin, manager);
            assertTrue(manager.hasSharedRuntime());
            UUID uuid = UUID.randomUUID();
            manager.withSharedSqlBackend(uuid, (storage, user) -> user.readRow(storage));
            verify(mysql).getExact(uuid.toString());

            CountDownLatch retired = new CountDownLatch(1);
            assertTrue(manager.closeSharedRuntimeAsync(retired::countDown));
            assertTrue(retired.await(5, TimeUnit.SECONDS));
            verify(mysql, never()).close();
        } finally {
            manager.getTimer().shutdownNow();
        }
    }

    @Test void refusesToBindBeforeNativeStorageInitialization() {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        when(plugin.getStorageType()).thenReturn(UserStorage.MYSQL);
        UserDataManager manager = new UserDataManager(plugin);
        try {
            assertThrows(IllegalStateException.class,
                    () -> BukkitUserRuntimeBootstrap.bindAfterStorageInitialization(plugin, manager));
            assertFalse(manager.hasSharedSqlBackend());
        } finally {
            manager.getTimer().shutdownNow();
        }
    }

    @Test void boundBackendKeepsItsInitializedStorageTypeAfterOptionsChange() throws Exception {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        MySQL mysql = mock(MySQL.class);
        AtomicReference<UserStorage> configured = new AtomicReference<>(UserStorage.MYSQL);
        when(plugin.getStorageType()).thenAnswer(ignored -> configured.get());
        when(plugin.getMysql()).thenReturn(mysql);
        when(mysql.getExact(anyString())).thenReturn(new ArrayList<>());
        UserDataManager manager = new UserDataManager(plugin);
        try {
            BukkitUserRuntimeBootstrap.bindAfterStorageInitialization(plugin, manager);
            configured.set(UserStorage.SQLITE);
            UUID uuid = UUID.randomUUID();

            manager.withSharedSqlBackend(uuid, (storage, user) -> {
                assertTrue(storage == UserStorage.MYSQL);
                return user.readRow(storage);
            });

            verify(mysql).getExact(uuid.toString());
            verify(plugin, never()).getSQLiteUserTable();
        } finally {
            CountDownLatch retired = new CountDownLatch(1);
            assertTrue(manager.closeSharedRuntimeAsync(retired::countDown));
            assertTrue(retired.await(5, TimeUnit.SECONDS));
            manager.getTimer().shutdownNow();
        }
    }
}
