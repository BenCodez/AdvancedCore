package com.bencodez.advancedcore.api.user.userstorage.mysql;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.core.user.storage.sql.MysqlUserBackend;
import com.bencodez.advancedcore.core.user.storage.sql.SqlBackendLogger;
import com.bencodez.advancedcore.core.user.storage.sql.SqlUserSchema;
import com.bencodez.simpleapi.sql.mysql.ConnectionManager;

class MySQLInitializationTest {
    @Test void rejectedAuthenticationAbortsBeforeUninitializedPluginHooksAndReleasesWorkers() throws Exception {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class, RETURNS_DEEP_STUBS);
        when(plugin.getOptions().getDebug().isDebug()).thenReturn(true);
        SQLException authentication = new SQLException("Access denied for test account", "28000", 1045);
        ArrayList<ExecutorService> workers = new ArrayList<>();
        try (var executors = mockStatic(Executors.class);
             var managers = mockConstruction(ConnectionManager.class, (manager, context) -> {
                 when(manager.open()).thenReturn(false);
                 when(manager.getLastOpenFailure()).thenReturn(authentication);
             })) {
            executors.when(() -> Executors.newFixedThreadPool(anyInt())).thenAnswer(call -> {
                ExecutorService worker = mock(ExecutorService.class); workers.add(worker); return worker;
            });
            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> new MySQL(plugin, "Users", config()));
            assertSame(authentication, failure.getCause());
            verify(plugin, never()).getLogger();
            verify(plugin, never()).getUserManager();
            assertEquals(2, workers.size());
            workers.forEach(worker -> verify(worker).shutdown());
            verify(managers.constructed().get(0)).close();
            verify(managers.constructed().get(0), never()).getConnectionChecked();
        }
    }

    @Test void sharedSqlBackendAlsoRejectsRatherThanPublishingAnUnusableProvider() throws Exception {
        SQLException authentication = new SQLException("controlled authentication rejection", "28000");
        try (var managers = mockConstruction(ConnectionManager.class, (manager, context) -> {
            when(manager.open()).thenReturn(false);
            when(manager.getLastOpenFailure()).thenReturn(authentication);
        })) {
            var settings = new com.bencodez.simpleapi.sql.mysql.config.MysqlConfigSpigot(config());
            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> new MysqlUserBackend("Users", settings, SqlUserSchema.builder().build(), SqlBackendLogger.NO_OP));
            assertSame(authentication, failure.getCause());
            verify(managers.constructed().get(0)).close();
            verify(managers.constructed().get(0), never()).getConnectionChecked();
        }
    }

    @Test void subclassSetupFailureClosesAlreadyOpenedPoolAndPreservesPrimaryFailure() throws Exception {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class, RETURNS_DEEP_STUBS);
        RuntimeException setup = new IllegalStateException("controlled schema setup failure");
        when(plugin.getUserManager()).thenThrow(setup);
        try (var managers = mockConstruction(ConnectionManager.class, (manager, context) -> {
            when(manager.open()).thenReturn(true);
            var connection = mock(java.sql.Connection.class);
            when(manager.getConnectionChecked()).thenReturn(connection);
            when(connection.prepareStatement(anyString())).thenReturn(mock(java.sql.PreparedStatement.class));
        })) {
            assertSame(setup, assertThrows(IllegalStateException.class, () -> new MySQL(plugin, "Users", config())));
            verify(managers.constructed().get(0)).close();
        }
    }

    @Test void globalSqlConstructorDoesNotCallUninitializedDiagnosticsAfterRejection() throws Exception {
        SQLException authentication = new SQLException("controlled global authentication rejection", "28000");
        try (var managers = mockConstruction(ConnectionManager.class, (manager, context) -> {
            when(manager.open()).thenReturn(false);
            when(manager.getLastOpenFailure()).thenReturn(authentication);
        })) {
            IllegalStateException failure = assertThrows(IllegalStateException.class, () ->
                new com.bencodez.advancedcore.bungeeapi.globaldata.GlobalMySQL("Global",
                        new com.bencodez.simpleapi.sql.mysql.config.MysqlConfigSpigot(config())) {
                    public void debugEx(Exception e) { fail("constructor callback"); }
                    public void debugLog(String text) { fail("constructor callback"); }
                    public void info(String text) { fail("constructor callback"); }
                    public void logSevere(String text) { fail("constructor callback"); }
                    public void warning(String text) { fail("constructor callback"); }
                });
            assertSame(authentication, failure.getCause());
            verify(managers.constructed().get(0)).close();
            verify(managers.constructed().get(0), never()).getConnectionChecked();
        }
    }

    @Test void successfulConstructionInitializesCachesAfterPluginAssignmentAndClosesNormally() throws Exception {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class, RETURNS_DEEP_STUBS);
        when(plugin.getOptions().getDebug().isDebug()).thenReturn(true);
        when(plugin.getUserManager().getDataManager().getKeys()).thenReturn(new ArrayList<>());
        try (var managers = mockConstruction(ConnectionManager.class, (manager, context) -> {
            when(manager.open()).thenReturn(true);
            when(manager.getDbType()).thenReturn(com.bencodez.simpleapi.sql.mysql.DbType.MYSQL);
            var connection = mock(java.sql.Connection.class);
            var statement = mock(java.sql.PreparedStatement.class);
            when(manager.getConnectionChecked()).thenReturn(connection);
            when(connection.prepareStatement(anyString())).thenReturn(statement);
            when(statement.executeQuery()).thenAnswer(call -> mock(java.sql.ResultSet.class));
            var metadata = mock(java.sql.DatabaseMetaData.class);
            when(connection.getMetaData()).thenReturn(metadata);
            when(metadata.supportsBatchUpdates()).thenReturn(true);
        })) {
            MySQL table = new MySQL(plugin, "Users", config());
            try {
                assertTrue(table.isUseBatchUpdates());
                assertEquals("Users", table.getTableName());
                assertNotNull(table.getUuids());
                verify(plugin, atLeastOnce()).getUserManager();
                verify(managers.constructed().get(0), atLeastOnce()).getConnectionChecked();
            } finally { table.close(); }
            assertTrue(table.getMysql().getThreadPool().isShutdown());
            verify(managers.constructed().get(0)).close();
        }
    }

    @Test void globalConnectionAcquisitionPreservesUncheckedFailures() throws Exception {
        assertGlobalSelectionFailures(0);
    }

    @Test void globalStatementPreparationPreservesUncheckedFailures() throws Exception {
        assertGlobalSelectionFailures(1);
    }

    @Test void globalStatementExecutionPreservesUncheckedFailures() throws Exception {
        assertGlobalSelectionFailures(2);
    }

    private void assertGlobalSelectionFailures(int phase) throws Exception {
        for (Throwable original : new Throwable[] { new IllegalArgumentException("driver failure"),
                new LinkageError("driver linkage failure"), new SQLException("database selection failure") }) {
            RuntimeException cleanup = new IllegalStateException("pool cleanup failure");
            try (var managers = mockConstruction(ConnectionManager.class, (manager, context) -> {
                when(manager.open()).thenReturn(true);
                when(manager.getDbType()).thenReturn(com.bencodez.simpleapi.sql.mysql.DbType.MYSQL);
                var connection = mock(java.sql.Connection.class);
                var statement = mock(java.sql.PreparedStatement.class);
                if (phase == 0) when(manager.getConnectionChecked()).thenThrow(original);
                else {
                    when(manager.getConnectionChecked()).thenReturn(connection);
                    if (phase == 1) when(connection.prepareStatement(anyString())).thenThrow(original);
                    else {
                        when(connection.prepareStatement(anyString())).thenReturn(statement);
                        when(statement.executeUpdate()).thenThrow(original);
                    }
                }
                doThrow(cleanup).when(manager).close();
            })) {
                Throwable actual = assertThrows(Throwable.class, () ->
                    new com.bencodez.advancedcore.bungeeapi.globaldata.GlobalMySQL("Global",
                            new com.bencodez.simpleapi.sql.mysql.config.MysqlConfigSpigot(config())) {
                        public void debugEx(Exception e) { fail("constructor callback"); }
                        public void debugLog(String text) { fail("constructor callback"); }
                        public void info(String text) { fail("constructor callback"); }
                        public void logSevere(String text) { fail("constructor callback"); }
                        public void warning(String text) { fail("constructor callback"); }
                    });
                if (original instanceof SQLException) {
                    assertInstanceOf(IllegalStateException.class, actual);
                    assertSame(original, actual.getCause());
                } else assertSame(original, actual);
                assertArrayEquals(new Throwable[] { cleanup }, original.getSuppressed());
                verify(managers.constructed().get(0)).close();
            }
        }
    }

    private static YamlConfiguration config() {
        YamlConfiguration result = new YamlConfiguration();
        result.set("Host", "localhost"); result.set("Port", 3306);
        result.set("Database", "test"); result.set("Username", "test");
        result.set("Password", "fixture"); result.set("MaxConnections", 1);
        result.set("Debug", true);
        return result;
    }
}
