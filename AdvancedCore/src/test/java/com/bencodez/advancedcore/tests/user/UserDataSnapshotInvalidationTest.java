package com.bencodez.advancedcore.tests.user;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.advancedcore.api.user.UserData;
import com.bencodez.advancedcore.api.user.UserDataFetchMode;
import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.advancedcore.api.user.usercache.UserDataCache;
import com.bencodez.advancedcore.api.user.usercache.UserDataManager;
import com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt;
import com.bencodez.simpleapi.sql.data.DataValue;
import com.bencodez.simpleapi.sql.data.DataValueInt;

class UserDataSnapshotInvalidationTest {
    private HashMap<String, DataValue> points(int amount) {
        return new HashMap<>(Map.of("Points", new DataValueInt(amount), "Other", new DataValueInt(7)));
    }

    @Test void invalidatedPublishedValueIsUnavailableInsteadOfFalseZeroOnPlatformRead() {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class, RETURNS_DEEP_STUBS);
        UserDataManager manager = mock(UserDataManager.class);
        when(plugin.getUserManager().getDataManager()).thenReturn(manager);
        when(manager.mustDeferSharedStorageAccess()).thenReturn(true);
        AdvancedCoreUser user = mock(AdvancedCoreUser.class);
        when(user.getPlugin()).thenReturn(plugin);
        UserDataCache cache = new UserDataCache(null, UUID.randomUUID());
        when(user.getCache()).thenReturn(cache);
        UserData data = new UserData(user);
        cache.updateCache(points(38));
        assertEquals(38, data.getInt(UserStorage.MYSQL, "Points", 0, UserDataFetchMode.DEFAULT));
        cache.invalidateStorageSnapshot("Points");
        assertFalse(cache.hasPublishedStorageSnapshot());
        assertNull(cache.snapshotIfPublished());
        assertThrows(IllegalStateException.class,
                () -> data.getInt(UserStorage.MYSQL, "Points", 0, UserDataFetchMode.DEFAULT));
        assertEquals(7, data.getInt(UserStorage.MYSQL, "Other", 0, UserDataFetchMode.DEFAULT));
        cache.updateSharedSnapshot(points(2), cache.getSharedSnapshotVersion());
        assertEquals(2, data.getInt(UserStorage.MYSQL, "Points", 0, UserDataFetchMode.DEFAULT));
        verify(plugin, never()).getMysql();
    }

    @Test void preMutationReadCannotRepublishStalePoints() {
        UserDataCache cache = new UserDataCache(null, UUID.randomUUID());
        cache.updateCache(points(38));
        long oldRead = cache.getSharedSnapshotVersion();
        cache.invalidateStorageSnapshot("Points");
        cache.updateSharedSnapshot(points(38), oldRead);
        assertFalse(cache.hasPublishedStorageSnapshot());
        assertFalse(cache.snapshot().containsKey("Points"));
        cache.updateSharedSnapshot(points(2), cache.getSharedSnapshotVersion());
        cache.updateSharedSnapshot(points(38), oldRead);
        assertEquals(2, cache.snapshotIfPublished().get("Points").getInt());
    }

    @Test void invalidationPreservesUnrelatedPendingWritesAndDoesNotFlushStorage() {
        UserDataManager manager = mock(UserDataManager.class, RETURNS_DEEP_STUBS);
        UserDataCache cache = new UserDataCache(manager, UUID.randomUUID());
        cache.updateCache(points(38));
        cache.addChange(new UserDataChangeInt("Other", 9), true);
        cache.invalidateStorageSnapshot("Points");
        assertTrue(cache.hasChangesToProcess());
        assertEquals(9, cache.snapshot().get("Other").getInt());
        cache.updateSharedSnapshot(points(2), cache.getSharedSnapshotVersion());
        assertEquals(9, cache.snapshotIfPublished().get("Other").getInt());
    }

    @Test void eachConcurrentMutationInvalidatesEarlierRefreshGeneration() {
        UserDataCache cache = new UserDataCache(null, UUID.randomUUID());
        cache.updateCache(points(1));
        cache.invalidateStorageSnapshot("Points");
        long firstRefresh = cache.getSharedSnapshotVersion();
        cache.invalidateStorageSnapshot("Points");
        long secondRefresh = cache.getSharedSnapshotVersion();
        cache.updateSharedSnapshot(points(2), firstRefresh);
        assertFalse(cache.hasPublishedStorageSnapshot());
        cache.updateSharedSnapshot(points(3), secondRefresh);
        assertEquals(3, cache.snapshotIfPublished().get("Points").getInt());
    }

    @Test void emptyInvalidationAndRetiredCacheAreSafe() {
        UserDataCache cache = new UserDataCache(null, UUID.randomUUID());
        cache.updateCache(points(2));
        cache.invalidateStorageSnapshot();
        cache.invalidateStorageSnapshot((String[]) null);
        cache.invalidateStorageSnapshot((String) null);
        assertTrue(cache.hasPublishedStorageSnapshot());
        cache.dump();
        assertDoesNotThrow(() -> cache.invalidateStorageSnapshot("Points"));
    }
}
