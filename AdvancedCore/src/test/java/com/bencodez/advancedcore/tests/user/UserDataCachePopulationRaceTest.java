package com.bencodez.advancedcore.tests.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.advancedcore.api.user.UserData;
import com.bencodez.advancedcore.api.user.UserManager;
import com.bencodez.advancedcore.api.user.usercache.UserDataCache;
import com.bencodez.advancedcore.api.user.usercache.UserDataManager;
import com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt;
import com.bencodez.simpleapi.sql.data.DataValueInt;

class UserDataCachePopulationRaceTest {
    @Test
    void retiredCacheMakesAnInFlightLegacyLoadANoop() throws Exception {
        UserDataManager manager = mock(UserDataManager.class);
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        UserManager users = mock(UserManager.class);
        AdvancedCoreUser user = mock(AdvancedCoreUser.class);
        UserData data = mock(UserData.class);
        ScheduledThreadPoolExecutor timer = new ScheduledThreadPoolExecutor(1);
        UUID uuid = UUID.randomUUID();
        CountDownLatch storageRead = new CountDownLatch(1);
        CountDownLatch releaseRead = new CountDownLatch(1);
        when(manager.getPlugin()).thenReturn(plugin);
        when(manager.getTimer()).thenReturn(timer);
        when(manager.getKeys()).thenReturn(new ArrayList<>());
        when(plugin.getUserManager()).thenReturn(users);
        when(users.getUser(uuid, false)).thenReturn(user);
        when(user.getUserData()).thenReturn(data);
        when(data.getKeys()).thenReturn(new ArrayList<>());
        when(data.getValues()).thenAnswer(ignored -> {
            storageRead.countDown();
            if (!releaseRead.await(5, TimeUnit.SECONDS)) throw new AssertionError("timed out waiting to retire cache");
            return new HashMap<>();
        });
        UserDataCache cache = new UserDataCache(manager, uuid);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread loader = new Thread(() -> {
            try {
                cache.cache();
            } catch (Throwable thrown) {
                failure.set(thrown);
            }
        });
        loader.start();
        try {
            org.junit.jupiter.api.Assertions.assertTrue(storageRead.await(5, TimeUnit.SECONDS));
            cache.dump();
            releaseRead.countDown();
            loader.join(5000);
            assertDoesNotThrow(() -> {
                if (loader.isAlive()) throw new AssertionError("cache load did not finish");
            });
            org.junit.jupiter.api.Assertions.assertNull(failure.get());
        } finally {
            releaseRead.countDown();
            loader.join(5000);
            timer.shutdownNow();
        }
    }

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
