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
    private static final UUID ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

    @Test void failedReadCannotPublishSnapshotAndRetryCanReadEmptyRowMysql() throws Exception { failedReadCannotPublishSnapshotAndRetryCanReadEmptyRow(DbType.MYSQL); }
    @Test void failedReadCannotPublishSnapshotAndRetryCanReadEmptyRowPostgres() throws Exception { failedReadCannotPublishSnapshotAndRetryCanReadEmptyRow(DbType.POSTGRESQL); }

    private void failedReadCannotPublishSnapshotAndRetryCanReadEmptyRow(DbType dialect) throws Exception {
        Fixture f = new Fixture(dialect);
        SQLException failure = new SQLTransientConnectionException("pool exhausted", "08001");
        when(f.manager.getConnectionChecked()).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> f.runtime.populate(ID)).getCause());
        verify(f.cache, never()).completePopulation(any(), any(), any());
        verify(f.cache, never()).populate(any(), any());
        f.connect();
        when(f.statement.executeQuery()).thenReturn(f.result);
        when(f.result.next()).thenReturn(false);
        doAnswer(call -> call.getArgument(1)).when(f.cache).completePopulation(eq(ID), any(), any());
        assertTrue(f.runtime.populate(ID).isEmpty());
        verify(f.cache).completePopulation(eq(ID), argThat(java.util.Map::isEmpty), any());
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
        final MySQL table = mock(MySQL.class, CALLS_REAL_METHODS);
        final AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        final ConnectionManager manager = mock(ConnectionManager.class);
        final Connection connection = mock(Connection.class);
        final PreparedStatement statement = mock(PreparedStatement.class);
        final ResultSet result = mock(ResultSet.class);
        final UserCacheOwner cache = mock(UserCacheOwner.class);
        final Set<String> uuids = ConcurrentHashMap.newKeySet();
        final Set<String> names = ConcurrentHashMap.newKeySet();
        final SharedUserDataRuntime runtime;

        Fixture(DbType dialect) throws Exception {
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
