package com.bencodez.advancedcore.tests.storage;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.user.UserManager;
import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.advancedcore.api.user.usercache.UserDataManager;
import com.bencodez.advancedcore.api.user.usercache.keys.UserDataKey;
import com.bencodez.advancedcore.api.user.usercache.keys.UserDataKeyString;
import com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL;
import com.bencodez.advancedcore.bukkit.user.storage.BukkitSqlUserBackend;
import com.bencodez.advancedcore.core.user.storage.sql.MysqlUserBackend;
import com.bencodez.advancedcore.core.user.storage.sql.SqlBackendLogger;
import com.bencodez.advancedcore.core.user.storage.sql.SqlUserBackendFactory;
import com.bencodez.simpleapi.sql.data.DataValueString;
import com.bencodez.simpleapi.sql.mysql.ConnectionManager;
import com.bencodez.simpleapi.sql.mysql.DbType;

class BukkitSqlUserBackendSchemaReconciliationTest {
    /**
     * Verifies that schema reconciliation failures prevent user data writes from proceeding,
     * ensuring database integrity when DDL operations fail.
     */
    @Test
    void registeredSchemaFailureStopsBulkWriteBeforeSharedSqlIsAdmitted() {
        Fixture fixture = new Fixture();
        IllegalStateException expected = new IllegalStateException("schema fixture");

        try (MockedStatic<MysqlUserBackend> reconciler = mockStatic(MysqlUserBackend.class);
                MockedStatic<SqlUserBackendFactory> factory = mockStatic(SqlUserBackendFactory.class)) {
            reconciler.when(() -> MysqlUserBackend.reconcileExistingTable(
                    eq("VotingPlugin_Users"), same(fixture.pooled),
                    argThat(schema -> schema.contains("LastVotes") && schema.contains("VoteRemindersMap")),
                    any(SqlBackendLogger.class))).thenThrow(expected);

            HashMap<String, com.bencodez.simpleapi.sql.data.DataValue> values = new HashMap<>();
            values.put("LastVotes", new DataValueString("{}"));

            IllegalStateException actual = assertThrows(IllegalStateException.class,
                    () -> fixture.backend().user(UUID.randomUUID()).writeValues(UserStorage.MYSQL, values));

            assertSame(expected, actual);
            factory.verifyNoInteractions();
            verify(fixture.mysql, never()).checkColumn(anyString(), any());
        }
    }

    /**
     * Verifies that schema reconciliation is cached for identical schemas but triggers
     * again when new keys are registered, balancing performance with correctness.
     */
    @Test
    void unchangedSchemaIsCachedButLateRegisteredKeyForcesReconciliation() {
        Fixture fixture = new Fixture();
        UUID uuid = UUID.randomUUID();
        BukkitSqlUserBackend backend = fixture.backend();

        try (MockedStatic<MysqlUserBackend> reconciler = mockStatic(MysqlUserBackend.class)) {
            backend.user(uuid).write(UserStorage.MYSQL, "LastVotes", new DataValueString("{}"));
            backend.user(uuid).write(UserStorage.MYSQL, "LastVotes", new DataValueString("{site:1}"));

            reconciler.verify(() -> MysqlUserBackend.reconcileExistingTable(
                    eq("VotingPlugin_Users"), same(fixture.pooled),
                    argThat(schema -> schema.contains("LastVotes") && schema.contains("VoteRemindersMap")
                            && !schema.contains("VoteStreakProgress_daily")),
                    any(SqlBackendLogger.class)), times(1));

            fixture.keys.add(new UserDataKeyString("VoteStreakProgress_daily").setColumnType("LONGTEXT"));
            backend.user(uuid).write(UserStorage.MYSQL, "VoteStreakProgress_daily", new DataValueString("2"));

            reconciler.verify(() -> MysqlUserBackend.reconcileExistingTable(
                    eq("VotingPlugin_Users"), same(fixture.pooled),
                    argThat(schema -> schema.contains("LastVotes") && schema.contains("VoteRemindersMap")
                            && schema.contains("VoteStreakProgress_daily")),
                    any(SqlBackendLogger.class)), times(1));
        }

        verify(fixture.mysql, times(2)).update(eq(uuid.toString()), eq("LastVotes"), any());
        verify(fixture.mysql).update(eq(uuid.toString()), eq("VoteStreakProgress_daily"), any());
    }

    /**
     * Verifies that the registered schema snapshot is captured under the data manager's
     * synchronization lock to prevent concurrent modification races.
     */
    @Test
    void registeredSchemaCopiesKeysUnderTheRegistrationLock() {
        Fixture fixture = new Fixture();
        AtomicBoolean accessedUnderLock = new AtomicBoolean();
        when(fixture.dataManager.getKeys()).thenAnswer(ignored -> {
            accessedUnderLock.set(Thread.holdsLock(fixture.dataManager));
            return fixture.keys;
        });

        try (MockedStatic<MysqlUserBackend> reconciler = mockStatic(MysqlUserBackend.class)) {
            fixture.backend().user(UUID.randomUUID()).write(UserStorage.MYSQL, "LastVotes", new DataValueString("{}"));
        }

        assertTrue(accessedUnderLock.get());
    }

    private static final class Fixture {
        final AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        final UserManager userManager = mock(UserManager.class);
        final UserDataManager dataManager = mock(UserDataManager.class);
        final MySQL mysql = mock(MySQL.class);
        final com.bencodez.simpleapi.sql.mysql.MySQL pooled =
                mock(com.bencodez.simpleapi.sql.mysql.MySQL.class);
        final ConnectionManager connectionManager = mock(ConnectionManager.class);
        final Logger logger = mock(Logger.class);
        final ArrayList<UserDataKey> keys = new ArrayList<>();

        /**
         * Initializes test fixtures with mocked plugin and storage infrastructure.
         */
        Fixture() {
            keys.add(new UserDataKeyString("LastVotes"));
            keys.add(new UserDataKeyString("VoteRemindersMap").setColumnType("LONGTEXT"));
            when(plugin.getStorageType()).thenReturn(UserStorage.MYSQL);
            when(plugin.getMysql()).thenReturn(mysql);
            when(plugin.getUserManager()).thenReturn(userManager);
            when(plugin.getLogger()).thenReturn(logger);
            when(userManager.getDataManager()).thenReturn(dataManager);
            when(dataManager.getKeys()).thenReturn(keys);
            when(mysql.getTableName()).thenReturn("VotingPlugin_Users");
            when(mysql.getMysql()).thenReturn(pooled);
            when(pooled.getConnectionManager()).thenReturn(connectionManager);
            when(connectionManager.getDbType()).thenReturn(DbType.MARIADB);
        }

        /**
         * Creates a backend instance using the mocked plugin infrastructure.
         *
         * @return a new BukkitSqlUserBackend for testing
         */
        BukkitSqlUserBackend backend() {
            return new BukkitSqlUserBackend(plugin);
        }
    }
}
