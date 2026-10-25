package com.bencodez.advancedcore.tests.user;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.user.usercache.UserDataManager;
import com.bencodez.advancedcore.bukkit.user.runtime.BukkitUserCacheOwner;
import com.bencodez.advancedcore.core.user.runtime.SharedUserDataRuntime;
import com.bencodez.advancedcore.core.user.storage.sql.SqlUserBackend;

class SpigotShutdownStorageWorkerTest {
    @Test void actualStorageWorkerCanRetireWhileSpigotReportsEveryThreadPrimary() throws Exception {
        UserDataManager manager = new UserDataManager(mock(AdvancedCorePlugin.class));
        SqlUserBackend backend = mock(SqlUserBackend.class);
        Server server = mock(Server.class);
        BukkitUserCacheOwner owner = new BukkitUserCacheOwner(manager);
        SharedUserDataRuntime runtime = new SharedUserDataRuntime(backend, owner);
        try {
            manager.getTimer().submit(() -> {
                // Mockito's static scope is thread-local: install it on the real storage worker.
                try (var bukkit = mockStatic(Bukkit.class)) {
                    bukkit.when(Bukkit::getServer).thenReturn(server);
                    bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
                    assertTrue(Bukkit.isPrimaryThread());
                    assertFalse(manager.isPlatformOwnedThread());
                    runtime.closeAsync(Runnable::run).toCompletableFuture().join();
                    assertTrue(runtime.isClosed());
                }
            }).get(5, TimeUnit.SECONDS);
            verify(backend).close();
            assertNull(manager.getLastDeferredStorageFailure());
        } finally {
            manager.getTimer().shutdownNow();
        }
    }

    @Test void primaryCallerStillCannotCloseStorageSynchronously() {
        UserDataManager manager = new UserDataManager(mock(AdvancedCorePlugin.class));
        SqlUserBackend backend = mock(SqlUserBackend.class);
        Server server = mock(Server.class);
        BukkitUserCacheOwner owner = new BukkitUserCacheOwner(manager);
        SharedUserDataRuntime runtime = new SharedUserDataRuntime(backend, owner);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getServer).thenReturn(server);
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            assertTrue(manager.isPlatformOwnedThread());
            assertThrows(IllegalStateException.class, runtime::close);
            verify(backend, never()).close();
        } finally {
            manager.getTimer().shutdownNow();
        }
    }

    @Test void matchingWorkerNameDoesNotBypassPlatformGuard() throws Exception {
        UserDataManager manager = new UserDataManager(mock(AdvancedCorePlugin.class));
        Server server = mock(Server.class);
        BukkitUserCacheOwner owner = new BukkitUserCacheOwner(manager);
        String name = manager.getTimer().submit(() -> Thread.currentThread().getName()).get(5, TimeUnit.SECONDS);
        var external = Executors.newSingleThreadExecutor(task -> new Thread(task, name));
        try {
            external.submit(() -> {
                try (var bukkit = mockStatic(Bukkit.class)) {
                    bukkit.when(Bukkit::getServer).thenReturn(server);
                    bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
                    assertTrue(manager.isPlatformOwnedThread());
                    assertThrows(IllegalStateException.class, owner::requireBlockingAllowed);
                }
            }).get(5, TimeUnit.SECONDS);
        } finally {
            external.shutdownNow();
            manager.getTimer().shutdownNow();
        }
    }
}
