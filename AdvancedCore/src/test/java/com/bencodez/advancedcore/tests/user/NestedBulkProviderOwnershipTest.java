package com.bencodez.advancedcore.tests.user;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.advancedcore.api.user.UserData;
import com.bencodez.advancedcore.api.user.UserManager;
import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.advancedcore.api.user.usercache.UserDataCache;
import com.bencodez.advancedcore.api.user.usercache.UserDataManager;
import com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt;
import com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL;
import com.bencodez.advancedcore.api.user.userstorage.sql.UserTable;
import com.bencodez.advancedcore.bukkit.user.storage.BukkitSqlUserStorage;
import com.bencodez.simpleapi.sql.Column;
import com.bencodez.simpleapi.sql.data.DataValue;
import com.bencodez.simpleapi.sql.data.DataValueInt;

@Timeout(10)
class NestedBulkProviderOwnershipTest {
    @Test void nestedMysqlBulkRetainsAdmittedProvider() { nested(UserStorage.MYSQL, true); }
    @Test void nestedSqliteBulkRetainsAdmittedProvider() { nested(UserStorage.SQLITE, true); }
    @Test void nestedMysqlScalarStillRetainsAdmittedProvider() { nested(UserStorage.MYSQL, false); }
    @Test void nestedSqliteScalarStillRetainsAdmittedProvider() { nested(UserStorage.SQLITE, false); }

    @Test void nestedMysqlBulkRetainsProviderWhenLiveProviderIsCleared() { nested(UserStorage.MYSQL, true, true); }
    @Test void nestedSqliteBulkRetainsProviderWhenLiveProviderIsCleared() { nested(UserStorage.SQLITE, true, true); }

    @Test void ordinaryMysqlBulkUsesCurrentProvider() {
        var plugin = mock(AdvancedCorePlugin.class);
        var table = mock(MySQL.class);
        when(plugin.getMysql()).thenReturn(table);
        var id = UUID.randomUUID();
        new BukkitSqlUserStorage(() -> plugin, id::toString).writeValues(UserStorage.MYSQL,
                new HashMap<>(Map.of("X", new DataValueInt(9))));
        verify(table).update(eq(id.toString()), anyList(), eq(false));
    }

    @Test void ordinarySqliteBulkUsesCurrentProvider() {
        var plugin = mock(AdvancedCorePlugin.class);
        var table = mock(UserTable.class);
        when(plugin.getSQLiteUserTable()).thenReturn(table);
        new BukkitSqlUserStorage(() -> plugin, UUID.randomUUID()::toString).writeValues(UserStorage.SQLITE,
                new HashMap<>(Map.of("X", new DataValueInt(9))));
        verify(table).update(any(Column.class), anyList());
    }

    @Test void ordinaryMissingMysqlProviderKeepsLegacyNoOp() {
        var plugin = mock(AdvancedCorePlugin.class);
        assertDoesNotThrow(() -> new BukkitSqlUserStorage(() -> plugin, UUID.randomUUID()::toString)
                .writeValues(UserStorage.MYSQL, new HashMap<>(Map.of("X", new DataValueInt(9)))));
    }

    private void nested(UserStorage storage, boolean bulk) { nested(storage, bulk, false); }

    private void nested(UserStorage storage, boolean bulk, boolean cleared) {
        var plugin = mock(AdvancedCorePlugin.class);
        var users = mock(UserManager.class);
        var user = mock(AdvancedCoreUser.class);
        var manager = new UserDataManager(plugin);
        var id = UUID.randomUUID();
        var mysql = mock(MySQL.class);
        var nextMysql = mock(MySQL.class);
        var sqlite = mock(UserTable.class);
        var nextSqlite = mock(UserTable.class);
        when(sqlite.legacyDirectProviderIdentity()).thenReturn(sqlite);
        when(mysql.caseInsensitiveColumnNames()).thenReturn(true);
        var liveMysql = new AtomicReference<>(mysql);
        var liveSqlite = new AtomicReference<>(sqlite);
        when(plugin.getStorageType()).thenReturn(storage);
        when(plugin.getUserManager()).thenReturn(users);
        when(users.getDataManager()).thenReturn(manager);
        when(users.getUser(id, false)).thenReturn(user);
        when(user.getPlugin()).thenReturn(plugin);
        when(user.getUUID()).thenReturn(id.toString());
        when(plugin.getMysql()).thenAnswer(call -> liveMysql.get());
        when(plugin.getSQLiteUserTable()).thenAnswer(call -> liveSqlite.get());
        var adapter = new BukkitSqlUserStorage(() -> plugin, id::toString);
        var first = new AtomicBoolean(true);
        var nestedOriginalWrites = new AtomicInteger();
        var replacementWrites = new AtomicInteger();
        var insideNested = new AtomicBoolean();
        Runnable providerWrite = () -> {
            if (insideNested.get()) { nestedOriginalWrites.incrementAndGet(); return; }
            if (!first.compareAndSet(true, false)) return;
            assertSame(storage == UserStorage.MYSQL ? mysql : sqlite,
                    storage == UserStorage.MYSQL ? UserDataCache.capturedNestedMysql() : UserDataCache.capturedNestedSqlite(),
                    "actual nested direct admission must capture the outer batch provider");
            liveMysql.set(cleared ? null : nextMysql); liveSqlite.set(cleared ? null : nextSqlite); insideNested.set(true);
            try {
                if (bulk) adapter.writeValues(storage, new HashMap<>(Map.of("X", new DataValueInt(9))));
                else adapter.write(storage, "X", new DataValueInt(9));
            } finally {
                insideNested.set(false); liveMysql.set(mysql); liveSqlite.set(sqlite);
            }
            UserDataCache.recordLegacyProviderCommit(id.toString(), List.of("X"), storage, true);
        };
        doAnswer(call -> { providerWrite.run(); return null; }).when(mysql).update(eq(id.toString()), anyString(), any(DataValue.class));
        doAnswer(call -> { if (insideNested.get()) nestedOriginalWrites.incrementAndGet(); return null; }).when(mysql).update(eq(id.toString()), anyList(), eq(false));
        doAnswer(call -> { providerWrite.run(); return null; }).when(sqlite).update(any(Column.class), anyList());
        doAnswer(call -> { replacementWrites.incrementAndGet(); return null; }).when(nextMysql).update(anyString(), anyList(), eq(false));
        doAnswer(call -> { replacementWrites.incrementAndGet(); return null; }).when(nextMysql).update(anyString(), anyString(), any(DataValue.class));
        doAnswer(call -> { replacementWrites.incrementAndGet(); return null; }).when(nextSqlite).update(any(Column.class), anyList());
        var data = new UserData(user) {
            @Override public void setValues(HashMap<String, DataValue> values) {
                setInt("X", 7, false);
                super.setValues(values);
            }
        };
        when(user.getUserData()).thenReturn(data);
        var cache = new UserDataCache(manager, id);
        cache.updateCompleteCachePreservingPending(new HashMap<>(Map.of("X", new DataValueInt(10))));
        manager.getUserDataCache().put(id, cache);
        when(user.isCached()).thenReturn(true); when(user.getCache()).thenReturn(cache);
        try {
            cache.addChange(new UserDataChangeInt("X", 20), true);
            cache.processChanges();
            assertFalse(first.get(), "nested provider path must execute");
            assertEquals(0, replacementWrites.get(), "nested write must not reach a replacement provider");
            assertEquals(1, nestedOriginalWrites.get());
        } finally { manager.getTimer().shutdownNow(); }
    }
}
