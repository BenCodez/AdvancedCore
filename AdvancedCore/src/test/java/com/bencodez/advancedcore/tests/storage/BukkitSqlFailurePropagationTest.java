package com.bencodez.advancedcore.tests.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.sql.*;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL;
import com.bencodez.advancedcore.bukkit.user.storage.BukkitSqlUserBackend;
import com.bencodez.advancedcore.core.user.runtime.SharedUserDataRuntime;
import com.bencodez.advancedcore.core.user.runtime.UserCacheOwner;
import com.bencodez.simpleapi.sql.mysql.AbstractSqlTable;
import com.bencodez.simpleapi.sql.mysql.ConnectionManager;
import com.bencodez.simpleapi.sql.mysql.DbType;
import com.bencodez.advancedcore.api.player.UuidLookup;

class BukkitSqlFailurePropagationTest {
    private static class CustomMysql extends MySQL { CustomMysql() { super(null, null, null); } }
    private static class ParticipatingMysql extends CustomMysql { @Override public boolean supportsAtomicReplayTransactions() { return true; } }
    @Test void replayTransactionsRequireExplicitSubclassParticipation() {
        var plugin = mock(AdvancedCorePlugin.class);
        var primary = new com.bencodez.simpleapi.sql.Column("uuid", com.bencodez.simpleapi.sql.DataType.STRING);
        var nativeTable = new com.bencodez.advancedcore.api.user.userstorage.sql.UserTable(plugin, "Users", java.util.List.of(), primary);
        var customTable = new com.bencodez.advancedcore.api.user.userstorage.sql.UserTable(plugin, "Users", java.util.List.of(), primary) { };
        var participating = new com.bencodez.advancedcore.api.user.userstorage.sql.UserTable(plugin, "Users", java.util.List.of(), primary) {
            @Override public boolean supportsAtomicReplayTransactions() { return true; }
        };
        assertTrue(new BukkitSqlUserBackend(plugin, UserStorage.SQLITE, null, nativeTable).supportsAtomicUserTransactions());
        assertFalse(new BukkitSqlUserBackend(plugin, UserStorage.SQLITE, null, customTable).supportsAtomicUserTransactions());
        assertTrue(new BukkitSqlUserBackend(plugin, UserStorage.SQLITE, null, participating).supportsAtomicUserTransactions());
        var nativeMysql = mock(MySQL.class, CALLS_REAL_METHODS); var customMysql = mock(CustomMysql.class, CALLS_REAL_METHODS);
        var optedInMysql = mock(ParticipatingMysql.class, CALLS_REAL_METHODS);
        assertTrue(new BukkitSqlUserBackend(plugin, UserStorage.MYSQL, nativeMysql, null).supportsAtomicUserTransactions());
        assertFalse(new BukkitSqlUserBackend(plugin, UserStorage.MYSQL, customMysql, null).supportsAtomicUserTransactions());
        assertTrue(new BukkitSqlUserBackend(plugin, UserStorage.MYSQL, optedInMysql, null).supportsAtomicUserTransactions());
        var user = mock(com.bencodez.advancedcore.api.user.AdvancedCoreUser.class);
        assertTrue(new com.bencodez.advancedcore.api.user.UserData(user).supportsAtomicReplayTransactions());
        assertFalse(new com.bencodez.advancedcore.api.user.UserData(user) { }.supportsAtomicReplayTransactions());
        assertTrue(new com.bencodez.advancedcore.api.user.UserData(user) {
            @Override public boolean supportsAtomicReplayTransactions() { return true; }
        }.supportsAtomicReplayTransactions());
    }
    @Test void nativeMysqlAsyncNestedSameKeyDrainsBeforeDeferredWrite() throws Exception { nativeMysqlAsyncNestedSameKey(false, false, false); }
    @Test void nativeMysqlAsyncNestedSameKeyDrainsAfterDeferredWrite() throws Exception { nativeMysqlAsyncNestedSameKey(true, false, false); }
    @Test void nativeMysqlAsyncNestedSameKeyKeepsCapturedProviderAfterReplacement() throws Exception { nativeMysqlAsyncNestedSameKey(false, true, false); }
    @Test void nativeMysqlAsyncNestedSameKeyRetainsFailedDeferredWrite() throws Exception { nativeMysqlAsyncNestedSameKey(false, false, true); }
    @Test void nativeMysqlAsyncNestedCaseAliasDrainsBeforeDeferredWrite() throws Exception { nativeMysqlAsyncNestedSameKey(false, false, false, "x"); }
    @Test void nativeMysqlAsyncNestedCaseAliasDrainsAfterDeferredWrite() throws Exception { nativeMysqlAsyncNestedSameKey(true, false, false, "x"); }
    private void nativeMysqlAsyncNestedSameKey(boolean completeFirst, boolean replaceProvider, boolean failDeferred) throws Exception {
        nativeMysqlAsyncNestedSameKey(completeFirst, replaceProvider, failDeferred, "X");
    }
    private void nativeMysqlAsyncNestedSameKey(boolean completeFirst, boolean replaceProvider, boolean failDeferred, String nestedKey) throws Exception {
        Fixture f = new Fixture(DbType.MYSQL); f.set(AbstractSqlTable.class, "updateLock", new Object()); f.connect();
        Fixture replacement = new Fixture(DbType.MYSQL); replacement.set(AbstractSqlTable.class, "updateLock", new Object()); replacement.connect();
        var users = mock(com.bencodez.advancedcore.api.user.UserManager.class);
        var user = mock(com.bencodez.advancedcore.api.user.AdvancedCoreUser.class);
        var timer = mock(java.util.concurrent.ScheduledExecutorService.class);
        var manager = new com.bencodez.advancedcore.api.user.usercache.UserDataManager(f.plugin);
        when(f.plugin.getUserManager()).thenReturn(users); when(users.getDataManager()).thenReturn(manager);
        when(users.getUser(ID, false)).thenReturn(user); when(user.getPlugin()).thenReturn(f.plugin);
        when(user.getUUID()).thenReturn(ID.toString()); when(f.plugin.getStorageType()).thenReturn(UserStorage.MYSQL);
        when(f.plugin.getMysql()).thenReturn(f.table); when(f.plugin.getTimer()).thenReturn(timer);
        doNothing().when(f.table).checkColumn(anyString(), any());
        var tasks = new java.util.ArrayList<Runnable>();
        var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        doAnswer(call -> { Runnable task = call.getArgument(0); if (completeFirst) executor.submit(task).get(5, java.util.concurrent.TimeUnit.SECONDS); else tasks.add(task); return null; }).when(timer).execute(any(Runnable.class));
        var parameter = new java.util.concurrent.atomic.AtomicInteger(); var stored = new java.util.concurrent.atomic.AtomicInteger(1);
        doAnswer(call -> { if (((Integer) call.getArgument(0)) == 1) parameter.set(((Number) call.getArgument(1)).intValue()); return null; }).when(f.statement).setObject(anyInt(), any());
        when(f.statement.executeUpdate()).thenAnswer(call -> { stored.set(parameter.get()); return 1; });
        var data = new com.bencodez.advancedcore.api.user.UserData(user) {
            @Override public void setValues(java.util.HashMap<String, com.bencodez.simpleapi.sql.data.DataValue> values) {
                setInt(nestedKey, 7, false, true); super.setValues(values);
            }
        };
        when(user.getUserData()).thenReturn(data);
        var cache = new com.bencodez.advancedcore.api.user.usercache.UserDataCache(manager, ID);
        cache.updateCompleteCachePreservingPending(new java.util.HashMap<>(java.util.Map.of("X", new com.bencodez.simpleapi.sql.data.DataValueInt(1))));
        manager.getUserDataCache().put(ID, cache); when(user.isCached()).thenReturn(true); when(user.getCache()).thenReturn(cache);
        try {
            cache.addChange(new com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt("X", 20), true);
            cache.processChanges(); assertFalse(cache.hasChangesToProcess());
            if (completeFirst) { assertEquals(7, stored.get()); assertTrue(tasks.isEmpty()); }
            else {
                assertEquals(20, stored.get()); assertEquals(1, tasks.size());
                if (replaceProvider) when(f.plugin.getMysql()).thenReturn(replacement.table);
                if (failDeferred) doThrow(new SQLException("synthetic deferred failure")).when(f.statement).executeUpdate();
                tasks.remove(0).run();
                if (failDeferred) { assertEquals(20, stored.get()); assertTrue(cache.hasChangesToProcess()); }
                else assertEquals(7, stored.get());
                if (replaceProvider) verify(replacement.connection, never()).prepareStatement(anyString());
            }
            assertEquals(7, cache.getIfPresent("X").getInt());
        } finally { while (!tasks.isEmpty()) tasks.remove(0).run(); executor.shutdownNow(); manager.getTimer().shutdownNow(); }
    }
    @Test void nativeMysqlIndependentWriteAfterNestedConfirmationSurvivesBatch() throws Exception { nativeMysqlIndependentAfterNested("X"); }
    @Test void nativeMysqlIndependentWriteAfterNestedAliasSurvivesBatch() throws Exception { nativeMysqlIndependentAfterNested("x"); }
    private void nativeMysqlIndependentAfterNested(String nestedKey) throws Exception {
        Fixture f = new Fixture(DbType.MYSQL); f.set(AbstractSqlTable.class, "updateLock", new Object()); f.connect();
        var users = mock(com.bencodez.advancedcore.api.user.UserManager.class);
        var user = mock(com.bencodez.advancedcore.api.user.AdvancedCoreUser.class);
        var manager = new com.bencodez.advancedcore.api.user.usercache.UserDataManager(f.plugin);
        when(f.plugin.getUserManager()).thenReturn(users); when(users.getDataManager()).thenReturn(manager);
        when(users.getUser(ID, false)).thenReturn(user); when(user.getPlugin()).thenReturn(f.plugin);
        when(user.getUUID()).thenReturn(ID.toString()); when(f.plugin.getStorageType()).thenReturn(UserStorage.MYSQL);
        when(f.plugin.getMysql()).thenReturn(f.table); doNothing().when(f.table).checkColumn(anyString(), any());
        var parameter = new java.util.concurrent.atomic.AtomicInteger(); var stored = new java.util.concurrent.atomic.AtomicInteger(1);
        doAnswer(call -> { if (((Integer) call.getArgument(0)) == 1) parameter.set(((Number) call.getArgument(1)).intValue()); return null; }).when(f.statement).setObject(anyInt(), any());
        when(f.statement.executeUpdate()).thenAnswer(call -> { stored.set(parameter.get()); return 1; });
        var entered = new java.util.concurrent.CountDownLatch(1); var release = new java.util.concurrent.CountDownLatch(1);
        var first = new java.util.concurrent.atomic.AtomicBoolean(true);
        var data = new com.bencodez.advancedcore.api.user.UserData(user) {
            @Override public void setValues(java.util.HashMap<String, com.bencodez.simpleapi.sql.data.DataValue> values) {
                setInt(nestedKey, 7, false);
                if (first.compareAndSet(true, false)) {
                    entered.countDown();
                    try { if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("fixture timeout"); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                }
                super.setValues(values);
            }
        };
        when(user.getUserData()).thenReturn(data);
        var cache = new com.bencodez.advancedcore.api.user.usercache.UserDataCache(manager, ID);
        cache.updateCompleteCachePreservingPending(new java.util.HashMap<>(java.util.Map.of("X", new com.bencodez.simpleapi.sql.data.DataValueInt(1))));
        manager.getUserDataCache().put(ID, cache); when(user.isCached()).thenReturn(true); when(user.getCache()).thenReturn(cache);
        var worker = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            cache.addChange(new com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt("X", 20), true);
            var flush = worker.submit(cache::processChanges); assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
            new com.bencodez.advancedcore.api.user.UserData(user).setInt("X", 99, false); release.countDown();
            flush.get(5, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(99, stored.get()); assertEquals(99, cache.getIfPresent("X").getInt());
        } finally { release.countDown(); worker.shutdownNow(); manager.getTimer().shutdownNow(); }
    }
    @Test void nativeMysqlFailedNestedSameKeyCannotAcknowledgeOlderBatch() throws Exception {
        Fixture f = new Fixture(DbType.MYSQL); f.set(AbstractSqlTable.class, "updateLock", new Object()); f.connect();
        var users = mock(com.bencodez.advancedcore.api.user.UserManager.class);
        var user = mock(com.bencodez.advancedcore.api.user.AdvancedCoreUser.class);
        var manager = new com.bencodez.advancedcore.api.user.usercache.UserDataManager(f.plugin);
        when(f.plugin.getUserManager()).thenReturn(users); when(users.getDataManager()).thenReturn(manager);
        when(users.getUser(ID, false)).thenReturn(user); when(user.getPlugin()).thenReturn(f.plugin);
        when(user.getUUID()).thenReturn(ID.toString()); when(f.plugin.getStorageType()).thenReturn(UserStorage.MYSQL);
        when(f.plugin.getMysql()).thenReturn(f.table); doNothing().when(f.table).checkColumn(anyString(), any());
        when(f.statement.executeUpdate()).thenThrow(new SQLException("synthetic nested write failure"));
        var data = new com.bencodez.advancedcore.api.user.UserData(user) {
            @Override public void setValues(java.util.HashMap<String, com.bencodez.simpleapi.sql.data.DataValue> values) {
                setInt("X", 7, false); super.setValues(values);
            }
        };
        when(user.getUserData()).thenReturn(data);
        var cache = new com.bencodez.advancedcore.api.user.usercache.UserDataCache(manager, ID);
        cache.updateCompleteCachePreservingPending(new java.util.HashMap<>(java.util.Map.of("X", new com.bencodez.simpleapi.sql.data.DataValueInt(10))));
        manager.getUserDataCache().put(ID, cache); when(user.isCached()).thenReturn(true); when(user.getCache()).thenReturn(cache);
        try {
            cache.addChange(new com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt("X", 20), true);
            assertThrows(IllegalStateException.class, cache::processChanges);
            assertTrue(cache.hasChangesToProcess());
            var queries = org.mockito.ArgumentCaptor.forClass(String.class);
            verify(f.connection, atLeastOnce()).prepareStatement(queries.capture());
            assertTrue(queries.getAllValues().stream().allMatch(query -> query.contains("X")));
            doReturn(1).when(f.statement).executeUpdate();
            cache.processChanges();
            assertFalse(cache.hasChangesToProcess()); assertEquals(7, cache.getIfPresent("X").getInt());
        } finally { manager.getTimer().shutdownNow(); }
    }
    @Test void nativeMysqlConfirmedNestedSameKeyDoesNotReplayAndFlushesOtherKeys() throws Exception {
        Fixture f = new Fixture(DbType.MYSQL); f.set(AbstractSqlTable.class, "updateLock", new Object()); f.connect();
        var users = mock(com.bencodez.advancedcore.api.user.UserManager.class);
        var user = mock(com.bencodez.advancedcore.api.user.AdvancedCoreUser.class);
        var manager = new com.bencodez.advancedcore.api.user.usercache.UserDataManager(f.plugin);
        when(f.plugin.getUserManager()).thenReturn(users); when(users.getDataManager()).thenReturn(manager);
        when(users.getUser(ID, false)).thenReturn(user); when(user.getPlugin()).thenReturn(f.plugin);
        when(user.getUUID()).thenReturn(ID.toString()); when(f.plugin.getStorageType()).thenReturn(UserStorage.MYSQL);
        when(f.plugin.getMysql()).thenReturn(f.table); doNothing().when(f.table).checkColumn(anyString(), any());
        when(f.statement.executeUpdate()).thenReturn(1);
        var data = new com.bencodez.advancedcore.api.user.UserData(user) {
            @Override public void setValues(java.util.HashMap<String, com.bencodez.simpleapi.sql.data.DataValue> values) {
                setInt("X", 7, false); super.setValues(values);
            }
        };
        when(user.getUserData()).thenReturn(data);
        var cache = new com.bencodez.advancedcore.api.user.usercache.UserDataCache(manager, ID);
        cache.updateCompleteCachePreservingPending(new java.util.HashMap<>(java.util.Map.of("X", new com.bencodez.simpleapi.sql.data.DataValueInt(10))));
        manager.getUserDataCache().put(ID, cache); when(user.isCached()).thenReturn(true); when(user.getCache()).thenReturn(cache);
        try {
            cache.addChange(new com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt("X", 20), true);
            cache.addChange(new com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt("Z", 30), true);
            cache.processChanges();
            var queries = org.mockito.ArgumentCaptor.forClass(String.class);
            verify(f.connection, times(2)).prepareStatement(queries.capture());
            assertTrue(queries.getAllValues().get(0).contains("X"));
            assertTrue(queries.getAllValues().get(1).contains("Z"));
            assertFalse(queries.getAllValues().get(1).contains("X"));
            assertEquals(7, cache.getIfPresent("X").getInt()); assertEquals(30, cache.getIfPresent("Z").getInt());
            assertFalse(cache.hasChangesToProcess());
        } finally { manager.getTimer().shutdownNow(); }
    }
    @Test void mysqlOrderedBulkAliasesReconcileLastAssignment() throws Exception {
        Fixture f = new Fixture(DbType.MYSQL); f.set(AbstractSqlTable.class, "updateLock", new Object()); f.connect();
        var users = mock(com.bencodez.advancedcore.api.user.UserManager.class); var user = mock(com.bencodez.advancedcore.api.user.AdvancedCoreUser.class);
        var manager = new com.bencodez.advancedcore.api.user.usercache.UserDataManager(f.plugin);
        when(f.plugin.getUserManager()).thenReturn(users); when(users.getDataManager()).thenReturn(manager); when(users.getUser(ID, false)).thenReturn(user);
        when(user.getPlugin()).thenReturn(f.plugin); when(user.getUUID()).thenReturn(ID.toString()); when(f.plugin.getStorageType()).thenReturn(UserStorage.MYSQL); when(f.plugin.getMysql()).thenReturn(f.table);
        doNothing().when(f.table).checkColumn(anyString(), any());
        var cache = new com.bencodez.advancedcore.api.user.usercache.UserDataCache(manager, ID);
        cache.updateCompleteCachePreservingPending(new java.util.HashMap<>(java.util.Map.of("Activations", new com.bencodez.simpleapi.sql.data.DataValueInt(1)))); manager.getUserDataCache().put(ID, cache);
        when(user.isCached()).thenReturn(true); when(user.getCache()).thenReturn(cache); var data = new com.bencodez.advancedcore.api.user.UserData(user); when(user.getUserData()).thenReturn(data);
        var lastValue = new java.util.concurrent.atomic.AtomicInteger(); var stored = new java.util.concurrent.atomic.AtomicInteger(1);
        doAnswer(call -> { Object argument = call.getArgument(1); if (argument instanceof Number number) lastValue.set(number.intValue()); return null; }).when(f.statement).setObject(anyInt(), any());
        when(f.statement.executeUpdate()).thenAnswer(call -> { stored.set(lastValue.get()); return 1; });
        try {
            cache.addChange(new com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt("Activations", 2), true);
            var values = new java.util.LinkedHashMap<String, com.bencodez.simpleapi.sql.data.DataValue>();
            values.put("Activations", new com.bencodez.simpleapi.sql.data.DataValueInt(10)); values.put("activations", new com.bencodez.simpleapi.sql.data.DataValueInt(20));
            data.setValues(values); assertEquals(20, cache.getIfPresent("Activations").getInt()); cache.processChanges(); assertEquals(20, stored.get());
            values.clear(); values.put("activations", new com.bencodez.simpleapi.sql.data.DataValueInt(30)); values.put("Activations", new com.bencodez.simpleapi.sql.data.DataValueInt(40));
            data.setValues(values); assertEquals(40, cache.getIfPresent("Activations").getInt()); assertEquals(40, stored.get());
        } finally { manager.getTimer().shutdownNow(); }
    }
    @Test void mysqlScalarAfterBulkOverrideSurvivesOlderQueuedWrite() throws Exception {
        Fixture f = new Fixture(DbType.MYSQL); f.set(AbstractSqlTable.class, "updateLock", new Object()); f.connect();
        var users = mock(com.bencodez.advancedcore.api.user.UserManager.class); var user = mock(com.bencodez.advancedcore.api.user.AdvancedCoreUser.class);
        var manager = new com.bencodez.advancedcore.api.user.usercache.UserDataManager(f.plugin);
        when(f.plugin.getUserManager()).thenReturn(users); when(users.getDataManager()).thenReturn(manager); when(users.getUser(ID, false)).thenReturn(user);
        when(user.getPlugin()).thenReturn(f.plugin); when(user.getUUID()).thenReturn(ID.toString()); when(f.plugin.getStorageType()).thenReturn(UserStorage.MYSQL); when(f.plugin.getMysql()).thenReturn(f.table);
        doNothing().when(f.table).checkColumn(anyString(), any());
        var cache = new com.bencodez.advancedcore.api.user.usercache.UserDataCache(manager, ID);
        cache.updateCompleteCachePreservingPending(new java.util.HashMap<>(java.util.Map.of("Activations", new com.bencodez.simpleapi.sql.data.DataValueInt(1)))); manager.getUserDataCache().put(ID, cache);
        when(user.isCached()).thenReturn(true); when(user.getCache()).thenReturn(cache); var data = new com.bencodez.advancedcore.api.user.UserData(user); when(user.getUserData()).thenReturn(data);
        var value = new java.util.concurrent.atomic.AtomicInteger(); var stored = new java.util.concurrent.atomic.AtomicInteger(1);
        doAnswer(call -> { if (call.<Integer>getArgument(0) == 1) value.set(((Number) call.getArgument(1)).intValue()); return null; }).when(f.statement).setObject(anyInt(), any());
        when(f.statement.executeUpdate()).thenAnswer(call -> { stored.set(value.get()); return 1; });
        try {
            data.setInt("Activations", 2, true); var values = new java.util.HashMap<String, com.bencodez.simpleapi.sql.data.DataValue>();
            values.put("Activations", new com.bencodez.simpleapi.sql.data.DataValueInt(10)); data.setValues(values); data.setInt("Activations", 20, false);
            assertEquals(20, stored.get()); cache.processChanges(); assertEquals(20, stored.get());
        } finally { manager.getTimer().shutdownNow(); }
    }
    @Test void snapshotReplacementPreservesNewerQueuedMysqlWrite() throws Exception {
        Fixture f = new Fixture(DbType.MYSQL); f.set(AbstractSqlTable.class, "updateLock", new Object()); f.connect();
        var users = mock(com.bencodez.advancedcore.api.user.UserManager.class); var user = mock(com.bencodez.advancedcore.api.user.AdvancedCoreUser.class);
        var manager = new com.bencodez.advancedcore.api.user.usercache.UserDataManager(f.plugin);
        when(f.plugin.getUserManager()).thenReturn(users); when(users.getDataManager()).thenReturn(manager); when(users.getUser(ID, false)).thenReturn(user);
        when(user.getPlugin()).thenReturn(f.plugin); when(user.getUUID()).thenReturn(ID.toString()); when(f.plugin.getStorageType()).thenReturn(UserStorage.MYSQL); when(f.plugin.getMysql()).thenReturn(f.table);
        doNothing().when(f.table).checkColumn(anyString(), any());
        var cache = new com.bencodez.advancedcore.api.user.usercache.UserDataCache(manager, ID);
        cache.updateCompleteCachePreservingPending(new java.util.HashMap<>(java.util.Map.of("Activations", new com.bencodez.simpleapi.sql.data.DataValueInt(1)))); manager.getUserDataCache().put(ID, cache);
        when(user.isCached()).thenReturn(true); when(user.getCache()).thenReturn(cache); var data = new com.bencodez.advancedcore.api.user.UserData(user); when(user.getUserData()).thenReturn(data);
        var value = new java.util.concurrent.atomic.AtomicInteger(); var stored = new java.util.concurrent.atomic.AtomicInteger(1);
        doAnswer(call -> { if (call.<Integer>getArgument(0) == 1) value.set(((Number) call.getArgument(1)).intValue()); return null; }).when(f.statement).setObject(anyInt(), any());
        when(f.statement.executeUpdate()).thenAnswer(call -> { stored.set(value.get()); return 1; });
        try {
            var values = new java.util.HashMap<String, com.bencodez.simpleapi.sql.data.DataValue>(); values.put("Activations", new com.bencodez.simpleapi.sql.data.DataValueInt(5));
            data.setValues(values); data.setInt("Activations", 10, true);
            cache.updateCache(new java.util.HashMap<>(java.util.Map.of("Activations", new com.bencodez.simpleapi.sql.data.DataValueInt(1))));
            assertEquals(10, cache.getIfPresent("Activations").getInt()); cache.processChanges(); assertEquals(10, stored.get());
            data.setValues(values); data.setInt("Activations", 12, true);
            data.setTempCache(new java.util.HashMap<>(java.util.Map.of("Activations", new com.bencodez.simpleapi.sql.data.DataValueInt(1))));
            data.updateCacheWithTemp(); assertEquals(12, cache.getIfPresent("Activations").getInt()); cache.processChanges(); assertEquals(12, stored.get());
            data.setInt("Activations", 20, true); values.put("Activations", new com.bencodez.simpleapi.sql.data.DataValueInt(15)); data.setValues(values);
            cache.updateCache(new java.util.HashMap<>(java.util.Map.of("Activations", new com.bencodez.simpleapi.sql.data.DataValueInt(1))));
            assertEquals(15, cache.getIfPresent("Activations").getInt()); cache.processChanges(); assertEquals(15, stored.get());
        } finally { manager.getTimer().shutdownNow(); }
    }
    @Test void mysqlAliasesRemainUsableAfterQueuedFlush() throws Exception {
        Fixture f = new Fixture(DbType.MYSQL); f.set(AbstractSqlTable.class, "updateLock", new Object()); f.connect();
        var users = mock(com.bencodez.advancedcore.api.user.UserManager.class); var user = mock(com.bencodez.advancedcore.api.user.AdvancedCoreUser.class);
        var manager = new com.bencodez.advancedcore.api.user.usercache.UserDataManager(f.plugin);
        when(f.plugin.getUserManager()).thenReturn(users); when(users.getDataManager()).thenReturn(manager); when(users.getUser(ID, false)).thenReturn(user);
        when(user.getPlugin()).thenReturn(f.plugin); when(user.getUUID()).thenReturn(ID.toString()); when(f.plugin.getStorageType()).thenReturn(UserStorage.MYSQL); when(f.plugin.getMysql()).thenReturn(f.table);
        doNothing().when(f.table).checkColumn(anyString(), any()); when(f.statement.executeUpdate()).thenReturn(1);
        var cache = new com.bencodez.advancedcore.api.user.usercache.UserDataCache(manager, ID);
        cache.updateCompleteCachePreservingPending(new java.util.HashMap<>(java.util.Map.of("Activations", new com.bencodez.simpleapi.sql.data.DataValueInt(1)))); manager.getUserDataCache().put(ID, cache);
        when(user.isCached()).thenReturn(true); when(user.getCache()).thenReturn(cache); var data = new com.bencodez.advancedcore.api.user.UserData(user); when(user.getUserData()).thenReturn(data);
        try {
            cache.addChange(new com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt("activations", 10), true);
            cache.updateCache(new java.util.HashMap<>(java.util.Map.of("Activations", new com.bencodez.simpleapi.sql.data.DataValueInt(1))));
            assertEquals(10, cache.getIfPresent("Activations").getInt()); cache.processChanges();
            assertDoesNotThrow(() -> data.setInt("activations", 11, false));
            assertEquals(11, cache.getIfPresent("Activations").getInt());
            assertDoesNotThrow(() -> data.setInt("Activations", 12, true)); cache.processChanges();
            assertEquals(12, cache.getIfPresent("activations").getInt());
        } finally { manager.getTimer().shutdownNow(); }
    }
    @Test void mysqlAliasDirectWriteSupersedesOlderQueuedArithmetic() throws Exception {
        Fixture f = new Fixture(DbType.MYSQL); f.set(AbstractSqlTable.class, "updateLock", new Object()); f.connect();
        var users = mock(com.bencodez.advancedcore.api.user.UserManager.class); var user = mock(com.bencodez.advancedcore.api.user.AdvancedCoreUser.class);
        var manager = new com.bencodez.advancedcore.api.user.usercache.UserDataManager(f.plugin);
        when(f.plugin.getUserManager()).thenReturn(users); when(users.getDataManager()).thenReturn(manager);
        when(users.getUser(ID, false)).thenReturn(user); when(user.getPlugin()).thenReturn(f.plugin); when(user.getUUID()).thenReturn(ID.toString()); when(f.plugin.getStorageType()).thenReturn(UserStorage.MYSQL); when(f.plugin.getMysql()).thenReturn(f.table);
        doNothing().when(f.table).checkColumn(anyString(), any());
        var cache = new com.bencodez.advancedcore.api.user.usercache.UserDataCache(manager, ID);
        cache.updateCompleteCachePreservingPending(new java.util.HashMap<>(java.util.Map.of("Activations", new com.bencodez.simpleapi.sql.data.DataValueInt(1)))); manager.getUserDataCache().put(ID, cache);
        when(user.isCached()).thenReturn(true); when(user.getCache()).thenReturn(cache);
        var data = new com.bencodez.advancedcore.api.user.UserData(user); when(user.getUserData()).thenReturn(data);
        var value = new java.util.concurrent.atomic.AtomicInteger(); var stored = new java.util.concurrent.atomic.AtomicInteger(1);
        doAnswer(call -> { if (call.<Integer>getArgument(0) == 1) value.set(((Number) call.getArgument(1)).intValue()); return null; }).when(f.statement).setObject(anyInt(), any());
        when(f.statement.executeUpdate()).thenAnswer(call -> { stored.set(value.get()); return 1; });
        try {
            assertTrue(manager.tryMutateLegacyIntegers(ID, java.util.Map.of("Activations", 1), com.bencodez.advancedcore.api.user.usercache.UserDataCache.IntegerMutation.ADD, null).applied());
            data.setInt("activations", 10, false); cache.processChanges();
            assertEquals(10, stored.get()); assertEquals(10, cache.getIfPresent("Activations").getInt());
        } finally { manager.getTimer().shutdownNow(); }
    }
    @Test void mysqlAliasBulkWriteSupersedesOlderQueuedArithmetic() throws Exception {
        Fixture f = new Fixture(DbType.MYSQL); f.set(AbstractSqlTable.class, "updateLock", new Object()); f.connect();
        var users = mock(com.bencodez.advancedcore.api.user.UserManager.class); var user = mock(com.bencodez.advancedcore.api.user.AdvancedCoreUser.class);
        var manager = new com.bencodez.advancedcore.api.user.usercache.UserDataManager(f.plugin);
        when(f.plugin.getUserManager()).thenReturn(users); when(users.getDataManager()).thenReturn(manager);
        when(users.getUser(ID, false)).thenReturn(user); when(user.getPlugin()).thenReturn(f.plugin); when(user.getUUID()).thenReturn(ID.toString()); when(f.plugin.getStorageType()).thenReturn(UserStorage.MYSQL); when(f.plugin.getMysql()).thenReturn(f.table);
        doNothing().when(f.table).checkColumn(anyString(), any());
        var cache = new com.bencodez.advancedcore.api.user.usercache.UserDataCache(manager, ID);
        cache.updateCompleteCachePreservingPending(new java.util.HashMap<>(java.util.Map.of("Activations", new com.bencodez.simpleapi.sql.data.DataValueInt(1)))); manager.getUserDataCache().put(ID, cache);
        when(user.isCached()).thenReturn(true); when(user.getCache()).thenReturn(cache);
        var data = new com.bencodez.advancedcore.api.user.UserData(user); when(user.getUserData()).thenReturn(data);
        var value = new java.util.concurrent.atomic.AtomicInteger(); var stored = new java.util.concurrent.atomic.AtomicInteger(1);
        doAnswer(call -> { if (call.<Integer>getArgument(0) == 1) value.set(((Number) call.getArgument(1)).intValue()); return null; }).when(f.statement).setObject(anyInt(), any());
        when(f.statement.executeUpdate()).thenAnswer(call -> { stored.set(value.get()); return 1; });
        try {
            assertTrue(manager.tryMutateLegacyIntegers(ID, java.util.Map.of("Activations", 1), com.bencodez.advancedcore.api.user.usercache.UserDataCache.IntegerMutation.ADD, null).applied());
            var values = new java.util.HashMap<String, com.bencodez.simpleapi.sql.data.DataValue>(); values.put("activations", new com.bencodez.simpleapi.sql.data.DataValueInt(10));
            data.setValues(values); cache.processChanges();
            assertEquals(10, stored.get()); assertEquals(10, cache.getIfPresent("Activations").getInt());
        } finally { manager.getTimer().shutdownNow(); }
    }
    @Test void legacyMysqlArithmeticUsesExistingColumnAlias() throws Exception {
        Fixture f = new Fixture(DbType.MYSQL); f.set(AbstractSqlTable.class, "updateLock", new Object()); f.connect();
        var users = mock(com.bencodez.advancedcore.api.user.UserManager.class); var user = mock(com.bencodez.advancedcore.api.user.AdvancedCoreUser.class);
        var manager = new com.bencodez.advancedcore.api.user.usercache.UserDataManager(f.plugin);
        when(f.plugin.getUserManager()).thenReturn(users); when(users.getDataManager()).thenReturn(manager);
        when(users.getUser(ID, false)).thenReturn(user); when(user.getPlugin()).thenReturn(f.plugin); when(user.getUUID()).thenReturn(ID.toString()); when(f.plugin.getStorageType()).thenReturn(UserStorage.MYSQL); when(f.plugin.getMysql()).thenReturn(f.table);
        var cache = new com.bencodez.advancedcore.api.user.usercache.UserDataCache(manager, ID);
        cache.updateCompleteCachePreservingPending(new java.util.HashMap<>(java.util.Map.of("Activations", new com.bencodez.simpleapi.sql.data.DataValueInt(5)))); manager.getUserDataCache().put(ID, cache);
        try {
            var result = manager.tryMutateLegacyIntegers(ID, java.util.Map.of("activations", 1), com.bencodez.advancedcore.api.user.usercache.UserDataCache.IntegerMutation.ADD, null);
            assertNotNull(result); assertEquals(5, result.previous().get("activations")); assertEquals(6, cache.getIfPresent("Activations").getInt());
        } finally { manager.getTimer().shutdownNow(); }
    }
    @Test void legacyPostgresArithmeticKeepsDistinctCaseColumns() throws Exception {
        Fixture f = new Fixture(DbType.POSTGRESQL);
        var manager = new com.bencodez.advancedcore.api.user.usercache.UserDataManager(f.plugin);
        when(f.plugin.getStorageType()).thenReturn(UserStorage.MYSQL); when(f.plugin.getMysql()).thenReturn(f.table);
        var cache = new com.bencodez.advancedcore.api.user.usercache.UserDataCache(manager, ID);
        cache.updateCompleteCachePreservingPending(new java.util.HashMap<>(java.util.Map.of("Activations", new com.bencodez.simpleapi.sql.data.DataValueInt(5)))); manager.getUserDataCache().put(ID, cache);
        try {
            var result = manager.tryMutateLegacyIntegers(ID, java.util.Map.of("activations", 1), com.bencodez.advancedcore.api.user.usercache.UserDataCache.IntegerMutation.ADD, null);
            assertNotNull(result); assertEquals(0, result.previous().get("activations")); assertEquals(5, cache.getIfPresent("Activations").getInt());
        } finally { manager.getTimer().shutdownNow(); }
    }
    @Test void newerMysqlDirectWriteSupersedesDelayedOlderWrite() throws Exception {
        Fixture f = new Fixture(DbType.MYSQL); f.set(AbstractSqlTable.class, "updateLock", new Object()); f.connect();
        var users = mock(com.bencodez.advancedcore.api.user.UserManager.class);
        var user = mock(com.bencodez.advancedcore.api.user.AdvancedCoreUser.class);
        var manager = new com.bencodez.advancedcore.api.user.usercache.UserDataManager(f.plugin);
        when(f.plugin.getUserManager()).thenReturn(users); when(users.getDataManager()).thenReturn(manager);
        when(user.getPlugin()).thenReturn(f.plugin); when(user.getUUID()).thenReturn(ID.toString());
        when(f.plugin.getStorageType()).thenReturn(UserStorage.MYSQL); when(f.plugin.getMysql()).thenReturn(f.table);
        doNothing().when(f.table).checkColumn(anyString(), any());
        var task = new java.util.concurrent.atomic.AtomicReference<Runnable>(); var timer = mock(java.util.concurrent.ScheduledExecutorService.class);
        doAnswer(call -> { task.set(call.getArgument(0)); return null; }).when(timer).execute(any(Runnable.class)); when(f.plugin.getTimer()).thenReturn(timer);
        var value = new java.util.concurrent.atomic.AtomicInteger(); var stored = new java.util.concurrent.atomic.AtomicInteger(1);
        doAnswer(call -> { if (call.<Integer>getArgument(0) == 1) value.set(((Number) call.getArgument(1)).intValue()); return null; }).when(f.statement).setObject(anyInt(), any());
        when(f.statement.executeUpdate()).thenAnswer(call -> { stored.set(value.get()); return 1; });
        var data = new com.bencodez.advancedcore.api.user.UserData(user);
        try {
            data.setInt("Activations", 2, false, true); data.setInt("Activations", 10, false); task.get().run();
            assertEquals(10, stored.get());
            manager.clearCacheAsyncCompletion().toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS);
        } finally { manager.getTimer().shutdownNow(); }
    }
    @Test void newerMysqlColumnAliasSupersedesDelayedOlderWrite() throws Exception {
        Fixture f = new Fixture(DbType.MYSQL); f.set(AbstractSqlTable.class, "updateLock", new Object()); f.connect();
        var users = mock(com.bencodez.advancedcore.api.user.UserManager.class); var user = mock(com.bencodez.advancedcore.api.user.AdvancedCoreUser.class);
        var manager = new com.bencodez.advancedcore.api.user.usercache.UserDataManager(f.plugin);
        when(f.plugin.getUserManager()).thenReturn(users); when(users.getDataManager()).thenReturn(manager);
        when(user.getPlugin()).thenReturn(f.plugin); when(user.getUUID()).thenReturn(ID.toString()); when(f.plugin.getStorageType()).thenReturn(UserStorage.MYSQL); when(f.plugin.getMysql()).thenReturn(f.table);
        doNothing().when(f.table).checkColumn(anyString(), any());
        var task = new java.util.concurrent.atomic.AtomicReference<Runnable>(); var timer = mock(java.util.concurrent.ScheduledExecutorService.class);
        doAnswer(call -> { task.set(call.getArgument(0)); return null; }).when(timer).execute(any(Runnable.class)); when(f.plugin.getTimer()).thenReturn(timer);
        var value = new java.util.concurrent.atomic.AtomicInteger(); var stored = new java.util.concurrent.atomic.AtomicInteger(1);
        doAnswer(call -> { if (call.<Integer>getArgument(0) == 1) value.set(((Number) call.getArgument(1)).intValue()); return null; }).when(f.statement).setObject(anyInt(), any());
        when(f.statement.executeUpdate()).thenAnswer(call -> { stored.set(value.get()); return 1; });
        var data = new com.bencodez.advancedcore.api.user.UserData(user);
        try {
            data.setInt("activations", 2, false, true); data.setInt("Activations", 10, false); task.get().run();
            assertEquals(10, stored.get());
        } finally { manager.getTimer().shutdownNow(); }
    }
    @Test void mysqlFailureCanRecoverThroughColumnAliasRetry() throws Exception {
        Fixture f = new Fixture(DbType.MYSQL); f.set(AbstractSqlTable.class, "updateLock", new Object()); f.connect();
        var users = mock(com.bencodez.advancedcore.api.user.UserManager.class); var user = mock(com.bencodez.advancedcore.api.user.AdvancedCoreUser.class);
        var manager = new com.bencodez.advancedcore.api.user.usercache.UserDataManager(f.plugin);
        when(f.plugin.getUserManager()).thenReturn(users); when(users.getDataManager()).thenReturn(manager);
        when(user.getPlugin()).thenReturn(f.plugin); when(user.getUUID()).thenReturn(ID.toString()); when(f.plugin.getStorageType()).thenReturn(UserStorage.MYSQL); when(f.plugin.getMysql()).thenReturn(f.table);
        doNothing().when(f.table).checkColumn(anyString(), any()); when(f.statement.executeUpdate()).thenThrow(new SQLException("synthetic failure")).thenReturn(1);
        var data = new com.bencodez.advancedcore.api.user.UserData(user);
        try {
            data.setInt("activations", 4, false); data.setInt("Activations", 4, false);
            manager.clearCacheAsyncCompletion().toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS);
        } finally { manager.getTimer().shutdownNow(); }
    }
    @Test void postgresDistinctCaseColumnsAreNotCoalesced() throws Exception {
        Fixture f = new Fixture(DbType.POSTGRESQL); f.set(AbstractSqlTable.class, "updateLock", new Object()); f.connect();
        var users = mock(com.bencodez.advancedcore.api.user.UserManager.class); var user = mock(com.bencodez.advancedcore.api.user.AdvancedCoreUser.class);
        var manager = new com.bencodez.advancedcore.api.user.usercache.UserDataManager(f.plugin);
        when(f.plugin.getUserManager()).thenReturn(users); when(users.getDataManager()).thenReturn(manager);
        when(user.getPlugin()).thenReturn(f.plugin); when(user.getUUID()).thenReturn(ID.toString()); when(f.plugin.getStorageType()).thenReturn(UserStorage.MYSQL); when(f.plugin.getMysql()).thenReturn(f.table);
        doNothing().when(f.table).checkColumn(anyString(), any());
        var task = new java.util.concurrent.atomic.AtomicReference<Runnable>(); var timer = mock(java.util.concurrent.ScheduledExecutorService.class);
        doAnswer(call -> { task.set(call.getArgument(0)); return null; }).when(timer).execute(any(Runnable.class)); when(f.plugin.getTimer()).thenReturn(timer);
        var sql = new java.util.concurrent.atomic.AtomicReference<String>(); var value = new java.util.concurrent.atomic.AtomicInteger();
        var lower = new java.util.concurrent.atomic.AtomicInteger(); var upper = new java.util.concurrent.atomic.AtomicInteger();
        when(f.connection.prepareStatement(anyString())).thenAnswer(call -> { sql.set(call.getArgument(0)); return f.statement; });
        doAnswer(call -> { if (call.<Integer>getArgument(0) == 1) value.set(((Number) call.getArgument(1)).intValue()); return null; }).when(f.statement).setObject(anyInt(), any());
        when(f.statement.executeUpdate()).thenAnswer(call -> { if (sql.get().contains("\"activations\"")) lower.set(value.get()); else upper.set(value.get()); return 1; });
        var data = new com.bencodez.advancedcore.api.user.UserData(user);
        try {
            data.setInt("activations", 2, false, true); data.setInt("Activations", 10, false); task.get().run();
            assertEquals(2, lower.get()); assertEquals(10, upper.get());
        } finally { manager.getTimer().shutdownNow(); }
    }
    @Test void mysqlCallbackCheckpointAliasSupersedesOuterDirectWrite() throws Exception {
        Fixture f = new Fixture(DbType.MYSQL); f.set(AbstractSqlTable.class, "updateLock", new Object()); f.connect();
        var users = mock(com.bencodez.advancedcore.api.user.UserManager.class); var user = mock(com.bencodez.advancedcore.api.user.AdvancedCoreUser.class);
        var manager = new com.bencodez.advancedcore.api.user.usercache.UserDataManager(f.plugin);
        when(f.plugin.getUserManager()).thenReturn(users); when(users.getDataManager()).thenReturn(manager);
        when(users.getUser(ID, false)).thenReturn(user); when(user.getPlugin()).thenReturn(f.plugin); when(user.getUUID()).thenReturn(ID.toString()); when(f.plugin.getStorageType()).thenReturn(UserStorage.MYSQL); when(f.plugin.getMysql()).thenReturn(f.table);
        doNothing().when(f.table).checkColumn(anyString(), any());
        var cache = new com.bencodez.advancedcore.api.user.usercache.UserDataCache(manager, ID);
        cache.updateCompleteCachePreservingPending(new java.util.HashMap<>(java.util.Map.of("Activations", new com.bencodez.simpleapi.sql.data.DataValueInt(1))));
        manager.getUserDataCache().put(ID, cache); when(user.isCached()).thenReturn(true); when(user.getCache()).thenReturn(cache);
        var data = new com.bencodez.advancedcore.api.user.UserData(user); when(user.getUserData()).thenReturn(data);
        var value = new java.util.concurrent.atomic.AtomicInteger(); var stored = new java.util.concurrent.atomic.AtomicInteger(1); var called = new java.util.concurrent.atomic.AtomicBoolean();
        doAnswer(call -> { if (call.<Integer>getArgument(0) == 1) value.set(((Number) call.getArgument(1)).intValue()); return null; }).when(f.statement).setObject(anyInt(), any());
        when(f.statement.executeUpdate()).thenAnswer(call -> { stored.set(value.get()); return 1; });
        doAnswer(call -> { if (called.compareAndSet(false, true)) { cache.addChange(new com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt("Activations", 20), true); cache.flushChangesAndRun(() -> {}); } return null; }).when(users).onChange(eq(user), eq("activations"));
        try { data.setInt("activations", 10, false); assertEquals(20, stored.get()); }
        finally { manager.getTimer().shutdownNow(); }
    }
    @Test void mysqlFirstRowCallbackCheckpointSupersedesOuterDirectWrite() throws Exception {
        Fixture f = new Fixture(DbType.MYSQL); f.set(AbstractSqlTable.class, "updateLock", new Object()); f.connect(); f.uuids.remove(ID.toString());
		doReturn(f.uuids).when(f.table).getUuids(); doReturn(false).when(f.table).containsKeyQuery(anyString());
        var users = mock(com.bencodez.advancedcore.api.user.UserManager.class); var user = mock(com.bencodez.advancedcore.api.user.AdvancedCoreUser.class);
        var manager = new com.bencodez.advancedcore.api.user.usercache.UserDataManager(f.plugin);
        when(f.plugin.getUserManager()).thenReturn(users); when(users.getDataManager()).thenReturn(manager);
        when(users.getUser(ID, false)).thenReturn(user); when(user.getPlugin()).thenReturn(f.plugin); when(user.getUUID()).thenReturn(ID.toString()); when(f.plugin.getStorageType()).thenReturn(UserStorage.MYSQL); when(f.plugin.getMysql()).thenReturn(f.table);
        doNothing().when(f.table).checkColumn(anyString(), any());
        var cache = new com.bencodez.advancedcore.api.user.usercache.UserDataCache(manager, ID);
        cache.updateCompleteCachePreservingPending(new java.util.HashMap<>(java.util.Map.of("Activations", new com.bencodez.simpleapi.sql.data.DataValueInt(1))));
        manager.getUserDataCache().put(ID, cache); when(user.isCached()).thenReturn(true); when(user.getCache()).thenReturn(cache);
        var data = new com.bencodez.advancedcore.api.user.UserData(user); when(user.getUserData()).thenReturn(data);
        var value = new java.util.concurrent.atomic.AtomicInteger(); var stored = new java.util.concurrent.atomic.AtomicInteger(); var called = new java.util.concurrent.atomic.AtomicBoolean();
        doAnswer(call -> { Object v = call.getArgument(1); if (v instanceof Number n) value.set(n.intValue()); return null; }).when(f.statement).setObject(anyInt(), any());
        when(f.statement.executeUpdate()).thenAnswer(call -> { stored.set(value.get()); f.uuids.add(ID.toString()); return 1; });
        doAnswer(call -> { if (called.compareAndSet(false, true)) { cache.addChange(new com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeInt("Activations", 20), true); cache.flushChangesAndRun(() -> {}); } return null; }).when(users).onChange(eq(user), eq("activations"));
        try (var lookup = mockStatic(UuidLookup.class)) {
            var resolver = mock(UuidLookup.class); lookup.when(UuidLookup::getInstance).thenReturn(resolver);
            when(resolver.getPlayerName(any(), anyString(), eq(false))).thenReturn("Player");
            data.setInt("activations", 10, false); assertEquals(20, stored.get());
        }
        finally { manager.getTimer().shutdownNow(); }
    }
    @Test void olderMysqlBulkSkipsSupersededKeyButWritesOtherKey() throws Exception {
        Fixture f = new Fixture(DbType.MYSQL); f.set(AbstractSqlTable.class, "updateLock", new Object()); f.connect();
        var users = mock(com.bencodez.advancedcore.api.user.UserManager.class); var user = mock(com.bencodez.advancedcore.api.user.AdvancedCoreUser.class);
        var manager = new com.bencodez.advancedcore.api.user.usercache.UserDataManager(f.plugin);
        when(f.plugin.getUserManager()).thenReturn(users); when(users.getDataManager()).thenReturn(manager);
        when(user.getPlugin()).thenReturn(f.plugin); when(user.getUUID()).thenReturn(ID.toString()); when(f.plugin.getStorageType()).thenReturn(UserStorage.MYSQL); when(f.plugin.getMysql()).thenReturn(f.table);
        doNothing().when(f.table).checkColumn(anyString(), any()); when(f.statement.executeUpdate()).thenReturn(1);
        var old = manager.beginLegacyDirectWrite(ID, Set.of("MYSQL:Activations", "MYSQL:Balance"));
        try {
            new com.bencodez.advancedcore.api.user.UserData(user).setInt("Activations", 10, false);
            var columns = new java.util.ArrayList<com.bencodez.simpleapi.sql.Column>();
            columns.add(new com.bencodez.simpleapi.sql.Column("Activations", new com.bencodez.simpleapi.sql.data.DataValueInt(2)));
            columns.add(new com.bencodez.simpleapi.sql.Column("Balance", new com.bencodez.simpleapi.sql.data.DataValueInt(3)));
            com.bencodez.advancedcore.api.user.usercache.UserDataCache.runWithLegacyDirectWrite(manager, old, null, () -> f.table.update(ID.toString(), columns, false));
            verify(f.statement, never()).setObject(eq(1), eq(2)); verify(f.statement).setObject(eq(1), eq(3));
            manager.markLegacyDirectWriteSucceeded(old);
        } finally { manager.endLegacyDirectWrite(old); manager.getTimer().shutdownNow(); }
    }
    @Test void failedNewerMysqlWriteDoesNotSuppressOlderSuccessfulWrite() throws Exception {
        Fixture f = new Fixture(DbType.MYSQL); f.set(AbstractSqlTable.class, "updateLock", new Object()); f.connect();
        var users = mock(com.bencodez.advancedcore.api.user.UserManager.class); var user = mock(com.bencodez.advancedcore.api.user.AdvancedCoreUser.class);
        var manager = new com.bencodez.advancedcore.api.user.usercache.UserDataManager(f.plugin);
        when(f.plugin.getUserManager()).thenReturn(users); when(users.getDataManager()).thenReturn(manager);
        when(user.getPlugin()).thenReturn(f.plugin); when(user.getUUID()).thenReturn(ID.toString()); when(f.plugin.getStorageType()).thenReturn(UserStorage.MYSQL); when(f.plugin.getMysql()).thenReturn(f.table);
        doNothing().when(f.table).checkColumn(anyString(), any());
        var task = new java.util.concurrent.atomic.AtomicReference<Runnable>(); var timer = mock(java.util.concurrent.ScheduledExecutorService.class);
        doAnswer(call -> { task.set(call.getArgument(0)); return null; }).when(timer).execute(any(Runnable.class)); when(f.plugin.getTimer()).thenReturn(timer);
        var value = new java.util.concurrent.atomic.AtomicInteger(); var stored = new java.util.concurrent.atomic.AtomicInteger(1); var calls = new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(call -> { if (call.<Integer>getArgument(0) == 1) value.set(((Number) call.getArgument(1)).intValue()); return null; }).when(f.statement).setObject(anyInt(), any());
        when(f.statement.executeUpdate()).thenAnswer(call -> { if (calls.getAndIncrement() == 0) throw new SQLException("newer failed"); stored.set(value.get()); return 1; });
        var data = new com.bencodez.advancedcore.api.user.UserData(user);
        try {
            data.setInt("Activations", 2, false, true); data.setInt("Activations", 10, false); task.get().run();
            assertEquals(2, stored.get());
            assertThrows(java.util.concurrent.ExecutionException.class, () -> manager.clearCacheAsyncCompletion().toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS));
        } finally { manager.getTimer().shutdownNow(); }
    }
    @Test void zeroRowMysqlDirectUpdateCannotAcknowledgeMissingRow() throws Exception {
        Fixture f = new Fixture(DbType.MYSQL); f.set(AbstractSqlTable.class, "updateLock", new Object()); f.connect();
        doReturn(false).when(f.table).containsKeyQuery(ID.toString());
        var users = mock(com.bencodez.advancedcore.api.user.UserManager.class);
        var user = mock(com.bencodez.advancedcore.api.user.AdvancedCoreUser.class);
        var manager = new com.bencodez.advancedcore.api.user.usercache.UserDataManager(f.plugin);
        when(f.plugin.getUserManager()).thenReturn(users); when(users.getDataManager()).thenReturn(manager);
        when(user.getPlugin()).thenReturn(f.plugin); when(user.getUUID()).thenReturn(ID.toString());
        when(f.plugin.getStorageType()).thenReturn(UserStorage.MYSQL); when(f.plugin.getMysql()).thenReturn(f.table);
        doNothing().when(f.table).checkColumn(anyString(), any()); when(f.statement.executeUpdate()).thenReturn(0);
        when(f.statement.executeQuery()).thenReturn(f.result); when(f.result.next()).thenReturn(false);
        try {
            new com.bencodez.advancedcore.api.user.UserData(user).setInt("Activations", 4, false);
            assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> manager.clearCacheAsyncCompletion().toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS));
        } finally { manager.getTimer().shutdownNow(); }
    }
    @Test void zeroChangedRowsForExistingMysqlRowCanSucceed() throws Exception {
        Fixture f = new Fixture(DbType.MYSQL); f.set(AbstractSqlTable.class, "updateLock", new Object()); f.connect();
        var users = mock(com.bencodez.advancedcore.api.user.UserManager.class);
        var user = mock(com.bencodez.advancedcore.api.user.AdvancedCoreUser.class);
        var manager = new com.bencodez.advancedcore.api.user.usercache.UserDataManager(f.plugin);
        when(f.plugin.getUserManager()).thenReturn(users); when(users.getDataManager()).thenReturn(manager);
        when(user.getPlugin()).thenReturn(f.plugin); when(user.getUUID()).thenReturn(ID.toString());
        when(f.plugin.getStorageType()).thenReturn(UserStorage.MYSQL); when(f.plugin.getMysql()).thenReturn(f.table);
        doNothing().when(f.table).checkColumn(anyString(), any()); when(f.statement.executeUpdate()).thenReturn(0);
        when(f.statement.executeQuery()).thenReturn(f.result); when(f.result.next()).thenReturn(true);
        try {
            new com.bencodez.advancedcore.api.user.UserData(user).setInt("Activations", 4, false);
            assertDoesNotThrow(() -> manager.clearCacheAsyncCompletion().toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS));
        } finally { manager.getTimer().shutdownNow(); }
    }
    @Test void zeroRowMysqlBatchRetainsChangesForMissingTarget() throws Exception {
        Fixture f = new Fixture(DbType.MYSQL); f.set(AbstractSqlTable.class, "updateLock", new Object()); f.connect();
        var users = mock(com.bencodez.advancedcore.api.user.UserManager.class);
        var user = mock(com.bencodez.advancedcore.api.user.AdvancedCoreUser.class);
        var manager = new com.bencodez.advancedcore.api.user.usercache.UserDataManager(f.plugin);
        when(f.plugin.getUserManager()).thenReturn(users); when(users.getDataManager()).thenReturn(manager);
        when(users.getUser(ID, false)).thenReturn(user); when(user.getPlugin()).thenReturn(f.plugin); when(user.getUUID()).thenReturn(ID.toString());
        when(f.plugin.getStorageType()).thenReturn(UserStorage.MYSQL); when(f.plugin.getMysql()).thenReturn(f.table);
        var data = new com.bencodez.advancedcore.api.user.UserData(user); when(user.getUserData()).thenReturn(data);
        var cache = new com.bencodez.advancedcore.api.user.usercache.UserDataCache(manager, ID);
        cache.updateCompleteCachePreservingPending(new java.util.HashMap<>(java.util.Map.of("Activations", new com.bencodez.simpleapi.sql.data.DataValueInt(1))));
        manager.getUserDataCache().put(ID, cache); doNothing().when(f.table).checkColumn(anyString(), any());
        when(f.statement.executeUpdate()).thenReturn(0); when(f.statement.executeQuery()).thenReturn(f.result); when(f.result.next()).thenReturn(false);
        try {
            assertTrue(manager.tryMutateLegacyIntegers(ID, java.util.Map.of("Activations", 1), com.bencodez.advancedcore.api.user.usercache.UserDataCache.IntegerMutation.ADD, null).applied());
            assertThrows(IllegalStateException.class, cache::processChanges);
            assertTrue(cache.hasChangesToProcess());
            assertEquals(2, cache.getIfPresent("Activations").getInt());
        } finally { manager.getTimer().shutdownNow(); }
    }
    @Test void mysqlDirectFailureDoesNotAcknowledgeRetirement() throws Exception {
        Fixture f = new Fixture(DbType.MYSQL); f.set(AbstractSqlTable.class, "updateLock", new Object()); f.connect();
        var users = mock(com.bencodez.advancedcore.api.user.UserManager.class);
        var user = mock(com.bencodez.advancedcore.api.user.AdvancedCoreUser.class);
        var manager = new com.bencodez.advancedcore.api.user.usercache.UserDataManager(f.plugin);
        when(f.plugin.getUserManager()).thenReturn(users); when(users.getDataManager()).thenReturn(manager);
        when(user.getPlugin()).thenReturn(f.plugin); when(user.getUUID()).thenReturn(ID.toString());
        when(f.plugin.getStorageType()).thenReturn(UserStorage.MYSQL); when(f.plugin.getMysql()).thenReturn(f.table);
        doNothing().when(f.table).checkColumn(anyString(), any());
        when(f.statement.executeUpdate()).thenThrow(new SQLException("synthetic write failure")).thenReturn(1);
        var data = new com.bencodez.advancedcore.api.user.UserData(user);
        try {
            assertDoesNotThrow(() -> data.setInt("Activations", 4, false));
            assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> manager.clearCacheAsyncCompletion().toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS));
            assertDoesNotThrow(() -> data.setInt("Activations", 4, false));
            assertDoesNotThrow(() -> manager.clearCacheAsyncCompletion().toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS));
        } finally { manager.getTimer().shutdownNow(); }
    }
    @Test void mysqlMissingRowDirectFailureDoesNotPublishIdentity() throws Exception { missingRowDirectFailure(DbType.MYSQL); }
    @Test void postgresMissingRowDirectFailureDoesNotPublishIdentity() throws Exception { missingRowDirectFailure(DbType.POSTGRESQL); }
    @Test void mysqlBulkDirectFailureRequiresSuccessfulRetry() throws Exception {
        Fixture f = new Fixture(DbType.MYSQL); f.set(AbstractSqlTable.class, "updateLock", new Object()); f.connect();
        var users = mock(com.bencodez.advancedcore.api.user.UserManager.class);
        var user = mock(com.bencodez.advancedcore.api.user.AdvancedCoreUser.class);
        var manager = new com.bencodez.advancedcore.api.user.usercache.UserDataManager(f.plugin);
        when(f.plugin.getUserManager()).thenReturn(users); when(users.getDataManager()).thenReturn(manager);
        when(user.getPlugin()).thenReturn(f.plugin); when(user.getUUID()).thenReturn(ID.toString());
        when(f.plugin.getStorageType()).thenReturn(UserStorage.MYSQL); when(f.plugin.getMysql()).thenReturn(f.table);
        doNothing().when(f.table).checkColumn(anyString(), any());
        when(f.statement.executeUpdate()).thenThrow(new SQLException("synthetic write failure")).thenReturn(1);
        var data = new com.bencodez.advancedcore.api.user.UserData(user);
        var values = new java.util.HashMap<String, com.bencodez.simpleapi.sql.data.DataValue>();
        values.put("Activations", new com.bencodez.simpleapi.sql.data.DataValueInt(4));
        values.put("Balance", new com.bencodez.simpleapi.sql.data.DataValueInt(7));
        try {
            assertDoesNotThrow(() -> data.setValues(values));
            assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> manager.clearCacheAsyncCompletion().toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS));
            assertDoesNotThrow(() -> data.setValues(values));
            assertDoesNotThrow(() -> manager.clearCacheAsyncCompletion().toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS));
        } finally { manager.getTimer().shutdownNow(); }
    }
    private void missingRowDirectFailure(DbType dialect) throws Exception {
        Fixture f = new Fixture(dialect); f.set(AbstractSqlTable.class, "updateLock", new Object()); f.connect(); f.uuids.clear();
        doReturn(f.uuids).when(f.table).getUuids();
        doReturn(false).when(f.table).containsKeyQuery(anyString());
        var users = mock(com.bencodez.advancedcore.api.user.UserManager.class);
        var user = mock(com.bencodez.advancedcore.api.user.AdvancedCoreUser.class);
        var manager = new com.bencodez.advancedcore.api.user.usercache.UserDataManager(f.plugin);
        when(f.plugin.getUserManager()).thenReturn(users); when(users.getDataManager()).thenReturn(manager);
        when(user.getPlugin()).thenReturn(f.plugin); when(user.getUUID()).thenReturn(ID.toString());
        when(f.plugin.getStorageType()).thenReturn(UserStorage.MYSQL); when(f.plugin.getMysql()).thenReturn(f.table);
        doNothing().when(f.table).checkColumn(anyString(), any());
        when(f.statement.executeUpdate()).thenThrow(new SQLException("synthetic insert failure"));
        var data = new com.bencodez.advancedcore.api.user.UserData(user);
        try {
            assertDoesNotThrow(() -> data.setInt("Activations", 4, false));
            assertFalse(f.uuids.contains(ID.toString()));
            assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> manager.clearCacheAsyncCompletion().toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS));
        } finally { manager.getTimer().shutdownNow(); }
    }
    @Test void mysqlBatchWriteFailureRetainsCreditButDirectWriteKeepsLegacyFailureBehavior() throws Exception {
        Fixture f = new Fixture(DbType.MYSQL); f.set(AbstractSqlTable.class, "updateLock", new Object()); f.connect();
        var users = mock(com.bencodez.advancedcore.api.user.UserManager.class);
        var user = mock(com.bencodez.advancedcore.api.user.AdvancedCoreUser.class);
        var manager = new com.bencodez.advancedcore.api.user.usercache.UserDataManager(f.plugin);
        when(f.plugin.getUserManager()).thenReturn(users); when(users.getDataManager()).thenReturn(manager);
        when(users.getUser(ID, false)).thenReturn(user); when(user.getPlugin()).thenReturn(f.plugin);
        when(user.getUUID()).thenReturn(ID.toString()); when(f.plugin.getStorageType()).thenReturn(UserStorage.MYSQL);
        when(f.plugin.getMysql()).thenReturn(f.table);
        var data = new com.bencodez.advancedcore.api.user.UserData(user); when(user.getUserData()).thenReturn(data);
        var cache = new com.bencodez.advancedcore.api.user.usercache.UserDataCache(manager, ID);
        cache.updateCompleteCachePreservingPending(new java.util.HashMap<>(java.util.Map.of("Activations", new com.bencodez.simpleapi.sql.data.DataValueInt(1))));
        manager.getUserDataCache().put(ID, cache);
        doNothing().when(f.table).checkColumn(anyString(), any());
        when(f.statement.executeUpdate()).thenThrow(new SQLException("synthetic write failure"));
        try {
            assertTrue(manager.tryMutateLegacyIntegers(ID, java.util.Map.of("Activations", 1),
                    com.bencodez.advancedcore.api.user.usercache.UserDataCache.IntegerMutation.ADD, null).applied());
            assertThrows(IllegalStateException.class, cache::processChanges);
            assertTrue(cache.hasChangesToProcess()); assertEquals(2, cache.getIfPresent("Activations").getInt());
            assertDoesNotThrow(() -> f.table.update(ID.toString(), "Activations", new com.bencodez.simpleapi.sql.data.DataValueInt(4)));
        } finally { manager.getTimer().shutdownNow(); }
    }
    @Test void mysqlExistingRowSkipsAnOlderBatchAfterDirectWrite() throws Exception {
        Fixture f = new Fixture(DbType.MYSQL);
        f.set(AbstractSqlTable.class, "updateLock", new Object());
        f.connect();
        var users = mock(com.bencodez.advancedcore.api.user.UserManager.class);
        var user = mock(com.bencodez.advancedcore.api.user.AdvancedCoreUser.class);
        var manager = new com.bencodez.advancedcore.api.user.usercache.UserDataManager(f.plugin);
        when(f.plugin.getUserManager()).thenReturn(users); when(users.getDataManager()).thenReturn(manager);
        when(users.getUser(ID, false)).thenReturn(user); when(user.getPlugin()).thenReturn(f.plugin);
        when(user.getUUID()).thenReturn(ID.toString()); when(f.plugin.getStorageType()).thenReturn(UserStorage.MYSQL);
        when(f.plugin.getMysql()).thenReturn(f.table);
        var data = new com.bencodez.advancedcore.api.user.UserData(user); when(user.getUserData()).thenReturn(data);
        var cache = new com.bencodez.advancedcore.api.user.usercache.UserDataCache(manager, ID);
        cache.updateCompleteCachePreservingPending(new java.util.HashMap<>(java.util.Map.of("Activations", new com.bencodez.simpleapi.sql.data.DataValueInt(1))));
        manager.getUserDataCache().put(ID, cache); when(user.isCached()).thenReturn(true); when(user.getCache()).thenReturn(cache);
        var entered = new java.util.concurrent.CountDownLatch(1); var release = new java.util.concurrent.CountDownLatch(1);
        var calls = new java.util.concurrent.atomic.AtomicInteger(); var parameter = new java.util.concurrent.atomic.AtomicInteger();
        var stored = new java.util.concurrent.atomic.AtomicInteger(1);
        doAnswer(call -> { if (calls.getAndIncrement() == 0) { entered.countDown();
            if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("fixture timeout"); } return null; })
                .when(f.table).checkColumn(anyString(), any());
        doAnswer(call -> { if (((Integer) call.getArgument(0)) == 1) parameter.set(((Number) call.getArgument(1)).intValue()); return null; })
                .when(f.statement).setObject(anyInt(), any());
        when(f.statement.executeUpdate()).thenAnswer(call -> { stored.set(parameter.get()); return 1; });
        var worker = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            assertTrue(manager.tryMutateLegacyIntegers(ID, java.util.Map.of("Activations", 1),
                    com.bencodez.advancedcore.api.user.usercache.UserDataCache.IntegerMutation.ADD, null).applied());
            var older = worker.submit(cache::processChanges); assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
            data.setInt("Activations", 10, false);
            assertEquals(10, stored.get());
            release.countDown(); older.get(5, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(10, stored.get());
        } finally { release.countDown(); worker.shutdownNow(); manager.getTimer().shutdownNow(); }
    }
    @Test void legacyStrictReadDoesNotTreatAQueryFailureAsAnEmptyRow() {
        var plugin = mock(AdvancedCorePlugin.class);
        var table = mock(com.bencodez.advancedcore.api.user.userstorage.sql.UserTable.class);
        when(plugin.getSQLiteUserTable()).thenReturn(table);
        when(table.getExactStrict(any())).thenThrow(new IllegalStateException("synthetic read failure"))
                .thenReturn(new java.util.ArrayList<>());
        var storage = new com.bencodez.advancedcore.bukkit.user.storage.BukkitSqlUserStorage(() -> plugin, ID::toString);
        assertThrows(IllegalStateException.class, () -> storage.readRowStrict(UserStorage.SQLITE));
        assertTrue(storage.readRowStrict(UserStorage.SQLITE).isEmpty());
        verify(table, times(2)).getExactStrict(any());
        verify(table, never()).getExact(any());
    }

    @Test void nestedStrictOwnerScopesRestoreAfterFailure() {
        var plugin = mock(AdvancedCorePlugin.class);
        var outerTable = mock(com.bencodez.advancedcore.api.user.userstorage.sql.UserTable.class);
        var innerTable = mock(com.bencodez.advancedcore.api.user.userstorage.sql.UserTable.class);
        var outer = new AdvancedCorePlugin.UserStorageOwner(UserStorage.SQLITE, null, outerTable);
        var inner = new AdvancedCorePlugin.UserStorageOwner(UserStorage.SQLITE, null, innerTable);
        when(plugin.getSQLiteUserTable()).thenReturn(innerTable);
        when(outerTable.getExactStrict(any())).thenReturn(new java.util.ArrayList<>());
        when(innerTable.getExactStrict(any())).thenReturn(new java.util.ArrayList<>());
        var storage = new com.bencodez.advancedcore.bukkit.user.storage.BukkitSqlUserStorage(() -> plugin, ID::toString);
        com.bencodez.advancedcore.bukkit.user.storage.BukkitSqlUserStorage.withStrictOwner(plugin, outer, () -> {
            assertThrows(IllegalStateException.class, () -> com.bencodez.advancedcore.bukkit.user.storage.BukkitSqlUserStorage.withStrictOwner(plugin, inner, () -> {
                storage.readRowStrict(UserStorage.SQLITE); throw new IllegalStateException("synthetic failure");
            }));
            storage.readRowStrict(UserStorage.SQLITE); return null;
        });
        storage.readRowStrict(UserStorage.SQLITE);
        verify(outerTable).getExactStrict(any()); verify(innerTable, times(2)).getExactStrict(any());
    }

    @Test void nestedUnboundStrictScopeDoesNotInheritOuterProvider() {
        var plugin = mock(AdvancedCorePlugin.class);
        var outerTable = mock(com.bencodez.advancedcore.api.user.userstorage.sql.UserTable.class);
        var liveTable = mock(com.bencodez.advancedcore.api.user.userstorage.sql.UserTable.class);
        var outer = new AdvancedCorePlugin.UserStorageOwner(UserStorage.SQLITE, null, outerTable);
        when(plugin.getSQLiteUserTable()).thenReturn(liveTable);
        when(outerTable.getExactStrict(any())).thenReturn(new java.util.ArrayList<>());
        when(liveTable.getExactStrict(any())).thenReturn(new java.util.ArrayList<>());
        var storage = new com.bencodez.advancedcore.bukkit.user.storage.BukkitSqlUserStorage(() -> plugin, ID::toString);
        com.bencodez.advancedcore.bukkit.user.storage.BukkitSqlUserStorage.withStrictOwner(plugin, outer, () -> {
            com.bencodez.advancedcore.bukkit.user.storage.BukkitSqlUserStorage.withStrictOwner(plugin, null, () -> {
                storage.readRowStrict(UserStorage.SQLITE); return null;
            });
            storage.readRowStrict(UserStorage.SQLITE); return null;
        });
        verify(liveTable).getExactStrict(any()); verify(outerTable).getExactStrict(any());
    }

    static class DelegatingMysqlReader extends MySQL {
        DelegatingMysqlReader() { super(null, null, null); }
        @Override public java.util.ArrayList<com.bencodez.simpleapi.sql.Column> getExact(String uuid) { return super.getExact(uuid); }
    }
    static class DelegatingMysqlQueryReader extends MySQL {
        DelegatingMysqlQueryReader() { super(null, null, null); }
        @Override public java.util.ArrayList<com.bencodez.simpleapi.sql.Column> getExactQuery(com.bencodez.simpleapi.sql.Column column) { return super.getExactQuery(column); }
    }
    @Test void mysqlDelegatingOverridesKeepStrictFailure() throws Exception {
        for (MySQL table : java.util.List.of(mock(DelegatingMysqlReader.class, CALLS_REAL_METHODS), mock(DelegatingMysqlQueryReader.class, CALLS_REAL_METHODS))) {
            Fixture f = new Fixture(DbType.MYSQL, table);
            doReturn(java.util.List.of("Points")).when(table).getColumns();
            when(f.manager.getConnectionChecked()).thenThrow(new SQLTransientConnectionException("pool exhausted"));
            assertThrows(IllegalStateException.class, () -> f.runtime.populate(ID));
            verify(f.cache, never()).completePopulation(any(), any(), any(), anyBoolean());
            assertDoesNotThrow(() -> table.getExact(ID.toString()));
        }
    }
    static class CustomMysqlReader extends MySQL {
        CustomMysqlReader() { super(null, null, null); }
        @Override public java.util.ArrayList<com.bencodez.simpleapi.sql.Column> getExact(String uuid) {
            return new java.util.ArrayList<>(java.util.List.of(new com.bencodez.simpleapi.sql.Column("Points", new com.bencodez.simpleapi.sql.data.DataValueInt(17))));
        }
    }
    static class CustomMysqlQueryReader extends MySQL {
        CustomMysqlQueryReader() { super(null, null, null); }
        @Override public java.util.ArrayList<com.bencodez.simpleapi.sql.Column> getExactQuery(com.bencodez.simpleapi.sql.Column column) {
            return new java.util.ArrayList<>(java.util.List.of(new com.bencodez.simpleapi.sql.Column("Points", new com.bencodez.simpleapi.sql.data.DataValueInt(18))));
        }
    }
    static class CustomStrictMysqlReader extends MySQL {
        CustomStrictMysqlReader() { super(null, null, null); }
        @Override public java.util.ArrayList<com.bencodez.simpleapi.sql.Column> getExactStrict(String uuid) { return new java.util.ArrayList<>(); }
    }
    @Test void customStrictProviderCannotImplicitlyCertifyAnEmptyRow() {
        var plugin = mock(AdvancedCorePlugin.class);
        var table = mock(CustomStrictMysqlReader.class, CALLS_REAL_METHODS);
        when(plugin.getMysql()).thenReturn(table);
        when(plugin.getNativeUserStorageOwner()).thenReturn(new AdvancedCorePlugin.UserStorageOwner(UserStorage.MYSQL, table, null));
        var storage = new com.bencodez.advancedcore.bukkit.user.storage.BukkitSqlUserStorage(() -> plugin, ID::toString);
        assertFalse(storage.supportsStrictLegacyRowReads(UserStorage.MYSQL));
        verify(table, never()).getExactStrict(anyString());
        assertNull(storage.readRowStrict(UserStorage.MYSQL));
    }
    @Test void customLegacyReaderIsNotCertifiedAsACompleteRow() {
        var plugin = mock(AdvancedCorePlugin.class);
        var table = mock(CustomMysqlReader.class, CALLS_REAL_METHODS);
        when(plugin.getMysql()).thenReturn(table);
        when(plugin.getNativeUserStorageOwner()).thenReturn(new AdvancedCorePlugin.UserStorageOwner(UserStorage.MYSQL, table, null));
        var storage = new com.bencodez.advancedcore.bukkit.user.storage.BukkitSqlUserStorage(() -> plugin, ID::toString);
        assertFalse(storage.supportsStrictLegacyRowReads(UserStorage.MYSQL));
        verify(table, never()).getExact(anyString());
        assertNull(storage.readRowStrict(UserStorage.MYSQL));
        assertEquals(17, storage.readRow(UserStorage.MYSQL).get(0).getValue().getInt());
    }
    @Test void unavailableNativeReaderCapabilityIsFailureRatherThanUnsupported() {
        var plugin = mock(AdvancedCorePlugin.class);
        var storage = new com.bencodez.advancedcore.bukkit.user.storage.BukkitSqlUserStorage(() -> plugin, ID::toString);
        assertThrows(IllegalStateException.class, () -> storage.supportsStrictLegacyRowReads(UserStorage.MYSQL));
        verify(plugin, never()).getMysql();
        verify(plugin, never()).getSQLiteUserTable();
    }
    @Test void mysqlSharedReadHonorsExistingReaderOverrides() {
        var plugin = mock(AdvancedCorePlugin.class);
        var direct = mock(CustomMysqlReader.class, CALLS_REAL_METHODS);
        var query = mock(CustomMysqlQueryReader.class, CALLS_REAL_METHODS);
        assertEquals(17, new BukkitSqlUserBackend(plugin, UserStorage.MYSQL, direct, null).user(ID).readRow(UserStorage.MYSQL).get(0).getValue().getInt());
        assertEquals(18, new BukkitSqlUserBackend(plugin, UserStorage.MYSQL, query, null).user(ID).readRow(UserStorage.MYSQL).get(0).getValue().getInt());
    }
    @Test void sqliteSharedReadHonorsExistingTableReaderOverride() {
        var table = new com.bencodez.advancedcore.api.user.userstorage.sql.UserTable(null, "Users", java.util.List.of(new com.bencodez.simpleapi.sql.Column("uuid", new com.bencodez.simpleapi.sql.data.DataValueString("id")))) {
            @Override public java.util.ArrayList<com.bencodez.simpleapi.sql.Column> getExact(com.bencodez.simpleapi.sql.Column column) {
                return new java.util.ArrayList<>(java.util.List.of(new com.bencodez.simpleapi.sql.Column("Points", new com.bencodez.simpleapi.sql.data.DataValueInt(17))));
            }
        };
        var backend = new BukkitSqlUserBackend(mock(AdvancedCorePlugin.class), UserStorage.SQLITE, null, table);
        assertEquals(17, backend.user(ID).readRow(UserStorage.SQLITE).get(0).getValue().getInt());
    }
    private static final UUID ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

    @Test void completenessSignalPreservesLegacyOwnerImplementation() {
        var cache = mock(UserCacheOwner.class, CALLS_REAL_METHODS);
        var values = new java.util.HashMap<String, com.bencodez.simpleapi.sql.data.DataValue>();
        doReturn(values).when(cache).completePopulation(eq(ID), same(values), isNull());
        assertSame(values, cache.completePopulation(ID, values, null, true));
        verify(cache).completePopulation(ID, values, null);
    }

    @Test void nullProviderRowCannotCertifyCompleteSnapshotButEmptyRowCan() {
        var backend = mock(com.bencodez.advancedcore.core.user.storage.sql.SqlUserBackend.class);
        var user = mock(com.bencodez.advancedcore.core.user.storage.SqlUserStorage.class);
        var cache = mock(UserCacheOwner.class);
        when(backend.storageType()).thenReturn(UserStorage.SQLITE); when(backend.user(ID)).thenReturn(user);
		when(backend.hasAuthoritativeRowReads()).thenReturn(true);
        when(backend.isOpen()).thenReturn(true);
        when(user.readRow(UserStorage.SQLITE)).thenReturn(null, new java.util.ArrayList<>());
        doAnswer(call -> call.getArgument(1)).when(cache).completePopulation(eq(ID), any(), any(), anyBoolean());
        var runtime = new SharedUserDataRuntime(backend, cache);
        assertTrue(runtime.populate(ID).isEmpty());
        verify(cache).completePopulation(eq(ID), argThat(java.util.Map::isEmpty), any(), eq(false));
        assertTrue(runtime.populate(ID).isEmpty());
        verify(cache).completePopulation(eq(ID), argThat(java.util.Map::isEmpty), any(), eq(true));
    }

    @Test void sqliteSharedBackendPropagatesStrictReadFailureAndAllowsRetry() {
        var plugin = mock(AdvancedCorePlugin.class);
        var table = mock(com.bencodez.advancedcore.api.user.userstorage.sql.UserTable.class);
        when(table.getExactStrict(any())).thenThrow(new IllegalStateException("synthetic read failure"))
                .thenReturn(new java.util.ArrayList<>());
        var backend = new BukkitSqlUserBackend(plugin, UserStorage.SQLITE, null, table);
        var cache = mock(UserCacheOwner.class);
        var runtime = new SharedUserDataRuntime(backend, cache);
        assertThrows(IllegalStateException.class, () -> runtime.populate(ID));
        verify(cache, never()).completePopulation(any(), any(), any());
        verify(cache, never()).completePopulation(any(), any(), any(), anyBoolean());
        doAnswer(call -> call.getArgument(1)).when(cache).completePopulation(eq(ID), any(), any(), eq(true));
        assertTrue(runtime.populate(ID).isEmpty());
        verify(cache).completePopulation(eq(ID), argThat(java.util.Map::isEmpty), any(), eq(true));
        verify(table, times(2)).getExactStrict(any());
        verify(table, never()).getExact(any());
    }

    @Test void failedReadCannotPublishSnapshotAndRetryCanReadEmptyRowMysql() throws Exception { failedReadCannotPublishSnapshotAndRetryCanReadEmptyRow(DbType.MYSQL); }
    @Test void failedReadCannotPublishSnapshotAndRetryCanReadEmptyRowPostgres() throws Exception { failedReadCannotPublishSnapshotAndRetryCanReadEmptyRow(DbType.POSTGRESQL); }

    private void failedReadCannotPublishSnapshotAndRetryCanReadEmptyRow(DbType dialect) throws Exception {
        Fixture f = new Fixture(dialect);
        SQLException failure = new SQLTransientConnectionException("pool exhausted", "08001");
        when(f.manager.getConnectionChecked()).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> f.runtime.populate(ID)).getCause());
        verify(f.cache, never()).completePopulation(any(), any(), any());
        verify(f.cache, never()).completePopulation(any(), any(), any(), anyBoolean());
        verify(f.cache, never()).populate(any(), any());
        f.connect();
        when(f.statement.executeQuery()).thenReturn(f.result);
        when(f.result.next()).thenReturn(false);
        doAnswer(call -> call.getArgument(1)).when(f.cache).completePopulation(eq(ID), any(), any(), eq(true));
        assertTrue(f.runtime.populate(ID).isEmpty());
        verify(f.cache).completePopulation(eq(ID), argThat(java.util.Map::isEmpty), any(), eq(true));
        verify(f.connection).close();
        verify(f.statement).close();
        verify(f.result).close();
        verify(f.manager, never()).close();
    }

    @Test void failedDeleteKeepsIdentityAndCacheAndRetryCompletesMysql() throws Exception { failedDeleteKeepsIdentityAndCacheAndRetryCompletes(DbType.MYSQL); }
    @Test void failedDeleteKeepsIdentityAndCacheAndRetryCompletesPostgres() throws Exception { failedDeleteKeepsIdentityAndCacheAndRetryCompletes(DbType.POSTGRESQL); }

    private void failedDeleteKeepsIdentityAndCacheAndRetryCompletes(DbType dialect) throws Exception {
        Fixture f = new Fixture(dialect);
        SQLException failure = new SQLTransientConnectionException("pool exhausted", "08001");
        when(f.manager.getConnectionChecked()).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> f.runtime.remove(ID)).getCause());
        assertTrue(f.uuids.contains(ID.toString()));
        assertTrue(f.names.contains("Player"));
        verify(f.cache, never()).remove(ID);
        verify(f.cache).cancelRemoval(ID);
        verify(f.table, never()).clearCacheBasic();
        f.connect();
        doNothing().when(f.table).clearCacheBasic();
        try (var lookup = mockStatic(UuidLookup.class)) {
            UuidLookup resolver = mock(UuidLookup.class);
            lookup.when(UuidLookup::getInstance).thenReturn(resolver);
            when(resolver.getCachedName(ID.toString())).thenReturn("Player");
            f.runtime.remove(ID);
        }
        verify(f.statement).executeUpdate();
        verify(f.cache).remove(ID);
        assertFalse(f.uuids.contains(ID.toString()));
        assertFalse(f.names.contains("Player"));
        verify(f.connection).close();
        verify(f.statement).close();
        verify(f.manager, never()).close();
    }

    @Test void queryFailuresAlsoAbortReadAndDeleteMysql() throws Exception { queryFailuresAlsoAbortReadAndDelete(DbType.MYSQL); }
    @Test void queryFailuresAlsoAbortReadAndDeletePostgres() throws Exception { queryFailuresAlsoAbortReadAndDelete(DbType.POSTGRESQL); }

    private void queryFailuresAlsoAbortReadAndDelete(DbType dialect) throws Exception {
        Fixture f = new Fixture(dialect);
        f.connect();
        SQLException failure = new SQLException("query failed", "08006");
        when(f.statement.executeQuery()).thenThrow(failure);
        when(f.statement.executeUpdate()).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> f.runtime.populate(ID)).getCause());
        assertSame(failure, assertThrows(IllegalStateException.class, () -> f.runtime.remove(ID)).getCause());
        verify(f.cache, never()).completePopulation(any(), any(), any());
        verify(f.cache, never()).completePopulation(any(), any(), any(), anyBoolean());
        verify(f.cache, never()).remove(ID);
        assertTrue(f.uuids.contains(ID.toString()));
        assertTrue(f.names.contains("Player"));
        verify(f.connection, times(2)).close();
        verify(f.statement, times(2)).close();
    }

    @Test void legacyReadFallbackRemainsAndFailedLegacyDeleteKeepsIdentity() throws Exception {
        Fixture f = new Fixture(DbType.MYSQL);
        when(f.manager.getConnectionChecked()).thenThrow(new SQLTransientConnectionException("pool exhausted"));
        doReturn(java.util.List.of("PlayerName")).when(f.table).getColumns();
        var row = f.table.getExact(ID.toString());
        assertEquals(1, row.size());
        assertEquals("PlayerName", row.get(0).getName());
        f.table.deletePlayer(ID.toString());
        assertTrue(f.uuids.contains(ID.toString()));
        assertTrue(f.names.contains("Player"));
        verify(f.table, never()).clearCacheBasic();
    }

    private static final class Fixture {
        final MySQL table;
        final AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        final ConnectionManager manager = mock(ConnectionManager.class);
        final Connection connection = mock(Connection.class);
        final PreparedStatement statement = mock(PreparedStatement.class);
        final ResultSet result = mock(ResultSet.class);
        final UserCacheOwner cache = mock(UserCacheOwner.class);
        final Set<String> uuids = ConcurrentHashMap.newKeySet();
        final Set<String> names = ConcurrentHashMap.newKeySet();
        final SharedUserDataRuntime runtime;

        Fixture(DbType dialect) throws Exception { this(dialect, mock(MySQL.class, CALLS_REAL_METHODS)); }
        Fixture(DbType dialect, MySQL table) throws Exception {
            this.table = table;
            var wrapper = mock(com.bencodez.simpleapi.sql.mysql.MySQL.class);
            when(wrapper.getConnectionManager()).thenReturn(manager);
            set(AbstractSqlTable.class, "mysql", wrapper);
            set(AbstractSqlTable.class, "dbType", dialect);
            set(AbstractSqlTable.class, "tableName", "Users");
            set(MySQL.class, "plugin", plugin);
            set(MySQL.class, "uuids", uuids);
            set(MySQL.class, "names", names);
            uuids.add(ID.toString());
            names.add("Player");
            runtime = new SharedUserDataRuntime(new BukkitSqlUserBackend(plugin, UserStorage.MYSQL, table, null), cache);
        }

        void connect() throws Exception {
            doReturn(connection).when(manager).getConnectionChecked();
            when(connection.prepareStatement(anyString())).thenReturn(statement);
        }

        void set(Class<?> owner, String name, Object value) throws Exception {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            field.set(table, value);
        }
    }
}
