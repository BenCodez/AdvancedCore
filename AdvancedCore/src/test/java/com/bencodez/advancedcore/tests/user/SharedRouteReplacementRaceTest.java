package com.bencodez.advancedcore.tests.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.advancedcore.api.user.usercache.UserDataCache;
import com.bencodez.advancedcore.api.user.usercache.UserDataManager;
import com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt;
import com.bencodez.advancedcore.bukkit.user.runtime.BukkitUserCacheOwner;
import com.bencodez.advancedcore.core.user.storage.SqlUserStorage;
import com.bencodez.advancedcore.core.user.storage.sql.SqlUserBackend;

class SharedRouteReplacementRaceTest {
    @Test
    void legacyOperationResolvesTheBackendAfterLifecycleAdmission() throws Exception {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        UserDataManager manager = new UserDataManager(plugin);
        manager.getTimer().shutdownNow();
        UUID uuid = UUID.randomUUID();
        SqlUserBackend oldBackend = mock(SqlUserBackend.class);
        SqlUserBackend newBackend = mock(SqlUserBackend.class);
        SqlUserStorage newStorage = mock(SqlUserStorage.class);
        when(newBackend.isOpen()).thenReturn(true);
        when(newBackend.storageType()).thenReturn(UserStorage.SQLITE);
        when(newBackend.user(uuid)).thenReturn(newStorage);

        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        BiConsumer<UUID, Runnable> gate = (id, operation) -> {
            entered.countDown();
            await(release);
            operation.run();
        };
        manager.bindSharedSqlBackend(oldBackend, gate);

        var worker = Executors.newSingleThreadExecutor();
        try {
            var result = worker.submit(() -> manager.withSharedSqlBackend(uuid, (type, storage) -> {
                storage.contains(type);
                return type;
            }));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            manager.bindSharedSqlBackend(newBackend, gate);
            release.countDown();
            assertEquals(UserStorage.SQLITE, result.get(5, TimeUnit.SECONDS));
            verify(newBackend).user(uuid);
            verify(newStorage).contains(UserStorage.SQLITE);
            verify(oldBackend, never()).user(any());
        } finally {
            release.countDown();
            worker.shutdownNow();
            assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void detachedCacheWriterFollowsBackendReplacement() {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class, RETURNS_DEEP_STUBS);
        UserDataManager manager = new UserDataManager(plugin);
        UUID uuid = UUID.randomUUID();
        BukkitUserCacheOwner owner = new BukkitUserCacheOwner(manager);
        SqlUserBackend oldBackend = mock(SqlUserBackend.class);
        SqlUserBackend newBackend = mock(SqlUserBackend.class);
        SqlUserStorage oldStorage = mock(SqlUserStorage.class);
        SqlUserStorage newStorage = mock(SqlUserStorage.class);
        when(oldBackend.isOpen()).thenReturn(true);
        when(oldBackend.storageType()).thenReturn(UserStorage.MYSQL);
        when(oldBackend.user(uuid)).thenReturn(oldStorage);
        when(newBackend.isOpen()).thenReturn(true);
        when(newBackend.storageType()).thenReturn(UserStorage.SQLITE);
        when(newBackend.user(uuid)).thenReturn(newStorage);

        BiConsumer<UUID, Runnable> perUser = (id, operation) -> operation.run();
        owner.bindLifecycle(oldBackend, Runnable::run, perUser);
        UserDataCache detached = new UserDataCache(manager, uuid);
        detached.addChange(new UserDataChangeInt("Points", 1), false);
        owner.bindBackend(newBackend);
        manager.getUserDataCache().put(uuid, detached);
        detached.addChange(new UserDataChangeInt("Points", 2), true);
        detached.processChanges();

        verify(newStorage).writeValues(any(UserStorage.class), any(HashMap.class));
        verify(oldStorage, never()).writeValues(any(UserStorage.class), any(HashMap.class));
        manager.getTimer().shutdownNow();
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("timed out waiting for lifecycle test latch");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }
}
