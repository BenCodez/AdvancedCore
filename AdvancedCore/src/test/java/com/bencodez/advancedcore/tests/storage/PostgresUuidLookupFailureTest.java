package com.bencodez.advancedcore.tests.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL;
import com.bencodez.advancedcore.bukkit.user.storage.BukkitSqlUserStorage;
import com.bencodez.simpleapi.sql.mysql.AbstractSqlTable;
import com.bencodez.simpleapi.sql.mysql.ConnectionManager;
import com.bencodez.simpleapi.sql.mysql.DbType;

class PostgresUuidLookupFailureTest {
    private static final String UUID = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";

    @Test void legacyFacadePreservesAcquisitionCauseAndRecovers() throws Exception {
        Fixture f = new Fixture();
        SQLException timeout = new SQLTransientConnectionException("pool exhausted", "08001");
        when(f.manager.getConnectionChecked()).thenThrow(timeout);
        assertSame(timeout, assertThrows(IllegalStateException.class,
                () -> f.storage.contains(UserStorage.MYSQL)).getCause());
        verify(f.plugin).debug(timeout);

        f.successfulLookup(false);
        assertFalse(f.storage.contains(UserStorage.MYSQL));
        when(f.result.next()).thenReturn(true);
        assertTrue(f.storage.contains(UserStorage.MYSQL));
        verify(f.statement, times(2)).setString(1, UUID);
        verify(f.connection, times(2)).close();
        verify(f.statement, times(2)).close();
        verify(f.result, times(2)).close();
        verify(f.manager, never()).close();
    }

    @Test void queryFailurePreservesCauseAndClosesBorrowedResources() throws Exception {
        Fixture f = new Fixture();
        f.successfulLookup(false);
        SQLException failure = new SQLException("query failed", "08006");
        when(f.statement.executeQuery()).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> f.table.containsKeyQuery(UUID)).getCause());
        verify(f.connection).close();
        verify(f.statement).close();
        verify(f.plugin).debug(failure);
    }

    @Test void malformedUuidAndCachedIdentityDoNotAcquireConnection() throws Exception {
        Fixture f = new Fixture();
        assertFalse(f.table.containsKeyQuery("invalid-uuid"));
        f.uuids.add(UUID);
        assertTrue(f.storage.contains(UserStorage.MYSQL));
        verifyNoInteractions(f.manager);
    }

    private static final class Fixture {
        final MySQL table = mock(MySQL.class, CALLS_REAL_METHODS);
        final AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        final ConnectionManager manager = mock(ConnectionManager.class);
        final Connection connection = mock(Connection.class);
        final PreparedStatement statement = mock(PreparedStatement.class);
        final ResultSet result = mock(ResultSet.class);
        final Set<String> uuids = ConcurrentHashMap.newKeySet();
        final BukkitSqlUserStorage storage = new BukkitSqlUserStorage(() -> plugin, () -> UUID);

        Fixture() throws Exception {
            var wrapper = mock(com.bencodez.simpleapi.sql.mysql.MySQL.class);
            when(wrapper.getConnectionManager()).thenReturn(manager);
            set(AbstractSqlTable.class, "mysql", wrapper);
            set(AbstractSqlTable.class, "dbType", DbType.POSTGRESQL);
            set(AbstractSqlTable.class, "tableName", "Users");
            set(MySQL.class, "plugin", plugin);
            set(MySQL.class, "uuids", uuids);
            // An existing cache entry avoids the separate whole-table refresh path.
            uuids.add("11111111-2222-3333-4444-555555555555");
            when(plugin.getMysql()).thenReturn(table);
        }

        void successfulLookup(boolean exists) throws Exception {
            doReturn(connection).when(manager).getConnectionChecked();
            when(connection.prepareStatement("SELECT 1 FROM \"Users\" WHERE \"uuid\" = ?::uuid LIMIT 1;"))
                    .thenReturn(statement);
            when(statement.executeQuery()).thenReturn(result);
            when(result.next()).thenReturn(exists);
        }

        void set(Class<?> owner, String name, Object value) throws Exception {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            field.set(table, value);
        }
    }
}
