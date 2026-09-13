package com.bencodez.advancedcore.tests.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.user.usercache.UserDataCache;
import com.bencodez.advancedcore.api.user.usercache.UserDataManager;
import com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt;
import com.bencodez.simpleapi.sql.data.DataValueInt;

class UserDataCachePopulationRaceTest {
    @Test
    void storageRefreshCannotOverwriteANewerQueuedCacheValue() {
        UserDataManager manager = mock(UserDataManager.class);
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        ScheduledThreadPoolExecutor timer = new ScheduledThreadPoolExecutor(1);
        when(manager.getPlugin()).thenReturn(plugin);
        when(manager.getTimer()).thenReturn(timer);
        UserDataCache cache = new UserDataCache(manager, UUID.randomUUID());
        HashMap<String, com.bencodez.simpleapi.sql.data.DataValue> initial = new HashMap<>();
        initial.put("Points", new DataValueInt(1));
        cache.updateCache(initial);
        cache.addChange(new UserDataChangeInt("Points", 7), true);
        HashMap<String, com.bencodez.simpleapi.sql.data.DataValue> staleStorage = new HashMap<>();
        staleStorage.put("Points", new DataValueInt(1));
        cache.updateCachePreservingPending(staleStorage);
        assertEquals(7, cache.getCache().get("Points").getInt());
        timer.shutdownNow();
    }

    @Test
    void unboundMutationVersionPreventsAnOlderRefreshFromWinning() {
        UserDataManager manager = mock(UserDataManager.class);
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        ScheduledThreadPoolExecutor timer = new ScheduledThreadPoolExecutor(1);
        when(manager.getPlugin()).thenReturn(plugin);
        when(manager.getTimer()).thenReturn(timer);
        UserDataCache cache = new UserDataCache(manager, UUID.randomUUID());
        HashMap<String, com.bencodez.simpleapi.sql.data.DataValue> initial = new HashMap<>();
        initial.put("Points", new DataValueInt(1));
        cache.updateCache(initial);
        long token = cache.getSharedSnapshotVersion();
        cache.addChange(new UserDataChangeInt("Points", 7), false);
        HashMap<String, com.bencodez.simpleapi.sql.data.DataValue> staleStorage = new HashMap<>();
        staleStorage.put("Points", new DataValueInt(1));
        cache.updateSharedSnapshot(staleStorage, token);
        assertEquals(7, cache.getCache().get("Points").getInt());
        timer.shutdownNow();
    }

    @Test
    void directMutationAtThePopulationTokenIsStillPreserved() {
        UserDataManager manager = mock(UserDataManager.class);
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        ScheduledThreadPoolExecutor timer = new ScheduledThreadPoolExecutor(1);
        when(manager.getPlugin()).thenReturn(plugin);
        when(manager.getTimer()).thenReturn(timer);
        UserDataCache cache = new UserDataCache(manager, UUID.randomUUID());
        HashMap<String, com.bencodez.simpleapi.sql.data.DataValue> initial = new HashMap<>();
        initial.put("Points", new DataValueInt(1));
        cache.updateCache(initial);
        cache.addChange(new UserDataChangeInt("Points", 7), false);
        long tokenAfterDirectMutation = cache.getSharedSnapshotVersion();
        HashMap<String, com.bencodez.simpleapi.sql.data.DataValue> staleStorage = new HashMap<>();
        staleStorage.put("Points", new DataValueInt(1));
        cache.updateSharedSnapshot(staleStorage, tokenAfterDirectMutation);
        assertEquals(7, cache.getCache().get("Points").getInt());
        timer.shutdownNow();
    }
}
