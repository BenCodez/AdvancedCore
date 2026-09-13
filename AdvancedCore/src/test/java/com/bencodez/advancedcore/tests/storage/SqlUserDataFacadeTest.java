package com.bencodez.advancedcore.tests.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.advancedcore.api.user.UserData;
import com.bencodez.advancedcore.api.user.UserDataFetchMode;
import com.bencodez.advancedcore.api.user.UserManager;
import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.advancedcore.api.user.usercache.UserDataCache;
import com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt;
import com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL;
import com.bencodez.simpleapi.sql.Column;
import com.bencodez.simpleapi.sql.data.DataValue;
import com.bencodez.simpleapi.sql.data.DataValueInt;

class SqlUserDataFacadeTest {
    @Test void legacyRowAndConvertOverridesRemainVirtualWithoutPluginAccess() {
        AdvancedCoreUser user = mock(AdvancedCoreUser.class);
        ArrayList<Column> row = new ArrayList<>(List.of(new Column("Points", new DataValueInt(17))));
        HashMap<String, DataValue> converted = new HashMap<>();
        UserData data = new UserData(user) {
            @Override public List<Column> getMySqlRow() { return row; }
            @Override public List<Column> getSQLiteRow() { return row; }
            @Override public HashMap<String, DataValue> convert(List<Column> columns) { assertSame(row, columns); return converted; }
        };
        for (UserStorage storage : UserStorage.values()) {
            assertEquals(17, data.getInt(storage, "Points", 0, UserDataFetchMode.NO_CACHE));
            assertEquals(List.of("Points"), data.getKeys(storage));
            assertSame(converted, data.getValues(storage));
        }
        verifyNoInteractions(user);
    }

    @Test void everyFetchModeRetainsTempUserCacheAndStoragePrecedence() {
        for (UserDataFetchMode mode : UserDataFetchMode.values()) {
            Fixture f = new Fixture();
            UserDataCache cache = mock(UserDataCache.class);
            when(f.user.getCache()).thenReturn(cache);
            when(cache.isCached("Points")).thenReturn(true);
            HashMap<String, DataValue> userValues = new HashMap<>();
            userValues.put("Points", new DataValueInt(2));
            when(cache.getCache()).thenReturn(userValues);
            HashMap<String, DataValue> temp = new HashMap<>();
            temp.put("Points", new DataValueInt(3));
            f.data.setTempCache(temp);
            assertEquals(mode.allowTempCache() ? 3 : 2, f.data.getInt("Points", -1, mode));
            verifyNoInteractions(f.table);
            temp.clear();
            when(cache.isCached("Points")).thenReturn(false);
            when(f.table.getExact("initial-id")).thenReturn(new ArrayList<>(List.of(new Column("Points", new DataValueInt(4)))));
            assertEquals(mode.allowStorageLookup() ? 4 : -1, f.data.getInt("Points", -1, mode));
            verify(f.table, times(mode.allowStorageLookup() ? 1 : 0)).getExact("initial-id");
        }
    }

    @Test void queuedCachedWriteDoesNotReachSqlOrCreateAnotherQueue() {
        Fixture f = new Fixture();
        UserDataCache cache = mock(UserDataCache.class);
        when(f.user.isCached()).thenReturn(true);
        when(f.user.getCache()).thenReturn(cache);
        f.data.setInt("Points", 19, true, true);
        var order = inOrder(cache, f.manager);
        order.verify(cache).addChange(any(UserDataChangeInt.class), eq(true));
        order.verify(f.manager).onChange(f.user, "Points");
        verifyNoInteractions(f.table, f.timer);
    }

    @Test void asyncWriteResolvesUuidAtExecutionAndNotifiesOnlyAfterSql() {
        Fixture f = new Fixture();
        f.data.setInt("Points", 19, false, true);
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(f.timer).execute(task.capture());
        verifyNoInteractions(f.table, f.manager);
        when(f.user.getUUID()).thenReturn("current-id");
        task.getValue().run();
        var order = inOrder(f.table, f.manager);
        order.verify(f.table).update(eq("current-id"), eq("Points"), any(DataValue.class));
        order.verify(f.manager).onChange(f.user, "Points");
    }

    @Test void storageFailureDoesNotClearCacheAndSchedulerRejectionDoesNotWrite() {
        Fixture f = new Fixture();
        IllegalStateException failure = new IllegalStateException("delete failed");
        doThrow(failure).when(f.table).deletePlayer("initial-id");
        assertSame(failure, assertThrows(IllegalStateException.class, f.data::remove));
        verify(f.user, never()).clearCache();
        clearInvocations(f.table, f.manager);
        RejectedExecutionException rejected = new RejectedExecutionException("stopped");
        doThrow(rejected).when(f.timer).execute(any(Runnable.class));
        assertSame(rejected, assertThrows(RejectedExecutionException.class, () -> f.data.setInt("Points", 1, false, true)));
        verifyNoInteractions(f.table, f.manager);
    }

    private static final class Fixture {
        final AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        final AdvancedCoreUser user = mock(AdvancedCoreUser.class);
        final MySQL table = mock(MySQL.class);
        final UserManager manager = mock(UserManager.class);
        final ScheduledExecutorService timer = mock(ScheduledExecutorService.class);
        final UserData data = new UserData(user);
        Fixture() {
            when(user.getPlugin()).thenReturn(plugin);
            when(user.getUUID()).thenReturn("initial-id");
            when(user.getPlayerName()).thenReturn("Ben");
            when(plugin.getStorageType()).thenReturn(UserStorage.MYSQL);
            when(plugin.getMysql()).thenReturn(table);
            when(plugin.getUserManager()).thenReturn(manager);
            when(plugin.getTimer()).thenReturn(timer);
        }
    }
}
