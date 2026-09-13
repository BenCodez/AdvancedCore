package com.bencodez.advancedcore.tests.user;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.advancedcore.api.user.usercache.UserDataCache;
import com.bencodez.advancedcore.api.user.usercache.UserDataManager;
import com.bencodez.advancedcore.core.user.storage.sql.SqlUserBackend;

class SharedCacheCleanupPrimaryThreadTest {
    @Test
    void sharedCacheClearMovesTheWholeFlushAndRemovalSequenceOffThePrimaryThread() throws Exception {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        UserDataManager manager = new UserDataManager(plugin);
        manager.getTimer().shutdownNow();
        ScheduledExecutorService worker = mock(ScheduledExecutorService.class);
        Field timer = UserDataManager.class.getDeclaredField("timer");
        timer.setAccessible(true);
        timer.set(manager, worker);

        UUID uuid = UUID.randomUUID();
        UserDataCache cache = mock(UserDataCache.class);
        manager.getUserDataCache().put(uuid, cache);
        SqlUserBackend backend = mock(SqlUserBackend.class);
        when(backend.isOpen()).thenReturn(true);
        when(backend.storageType()).thenReturn(UserStorage.SQLITE);
        manager.bindSharedSqlBackend(backend, (user, operation) -> operation.run());

        Server server = mock(Server.class);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getServer).thenReturn(server);
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
            manager.clearCache();
            verify(worker).execute(task.capture());
            verify(cache, never()).clearCache();
            assertTrue(manager.getUserDataCache().containsKey(uuid));
            task.getValue().run();
            verify(cache).clearCache();
            verify(cache).dump();
            assertFalse(manager.getUserDataCache().containsKey(uuid));
        }
    }
}
