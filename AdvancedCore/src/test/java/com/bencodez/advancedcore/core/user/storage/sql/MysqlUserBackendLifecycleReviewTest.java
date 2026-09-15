package com.bencodez.advancedcore.core.user.storage.sql;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import com.bencodez.simpleapi.sql.DataType;
import com.bencodez.simpleapi.sql.mysql.ConnectionManager;
import com.bencodez.simpleapi.sql.mysql.DbType;
import com.bencodez.simpleapi.sql.mysql.config.MysqlConfig;

class MysqlUserBackendLifecycleReviewTest {
    @Test
    void closeRejectsNewWorkAndWaitsForActiveEnumerationCallbacks() throws Exception {
        UUID enumerated = UUID.randomUUID();
        Connection connection = mock(Connection.class);
        when(connection.prepareStatement(anyString())).thenAnswer(call -> {
            String sql = call.getArgument(0, String.class);
            PreparedStatement statement = mock(PreparedStatement.class);
            when(statement.executeUpdate()).thenReturn(0);
            ResultSet result = mock(ResultSet.class);
            if (sql.endsWith("WHERE 1=0")) {
                ResultSetMetaData metadata = mock(ResultSetMetaData.class);
                when(result.getMetaData()).thenReturn(metadata);
                when(metadata.getColumnCount()).thenReturn(2);
                when(metadata.getColumnName(1)).thenReturn("uuid");
                when(metadata.getColumnName(2)).thenReturn("Points");
            } else if (sql.startsWith("SELECT `uuid` FROM")) {
                when(result.next()).thenReturn(true, false);
                when(result.getString(1)).thenReturn(enumerated.toString());
            }
            when(statement.executeQuery()).thenReturn(result);
            return statement;
        });

        MysqlConfig config = new MysqlConfig();
        config.setDbType(DbType.MYSQL);
        config.setDatabase("test_database");
        config.setTablePrefix("");
        config.setTableName("Users");
        config.setMaxThreads(1);
        SqlUserSchema schema = SqlUserSchema.builder().column("Points", "INT DEFAULT '0'", DataType.INTEGER).build();

        try (MockedConstruction<ConnectionManager> managers = mockConstruction(ConnectionManager.class,
                (manager, context) -> {
                    when(manager.open()).thenReturn(true);
                    when(manager.getDbType()).thenReturn(DbType.MYSQL);
                    when(manager.getConnection()).thenReturn(connection);
                })) {
            MysqlUserBackend backend = new MysqlUserBackend("Users", config, schema, SqlBackendLogger.NO_OP);
            CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
            var pool = Executors.newFixedThreadPool(2);
            try {
                var enumeration = pool.submit(() -> backend.forEachUser(uuid -> {
                    entered.countDown();
                    try { assertTrue(release.await(5, TimeUnit.SECONDS)); }
                    catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
                }));
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                var closing = pool.submit(backend::close);
                for (int i = 0; i < 100 && backend.isOpen(); i++) Thread.sleep(5);
                assertFalse(backend.isOpen());
                assertThrows(IllegalStateException.class, () -> backend.user(UUID.randomUUID()));
                assertThrows(TimeoutException.class, () -> closing.get(150, TimeUnit.MILLISECONDS));
                release.countDown();
                enumeration.get(5, TimeUnit.SECONDS);
                closing.get(5, TimeUnit.SECONDS);
                verify(connection).prepareStatement(org.mockito.ArgumentMatchers.contains(
                        "WHERE `uuid` IS NOT NULL"));
                verify(managers.constructed().get(0)).close();
            } finally {
                release.countDown();
                pool.shutdownNow();
                assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
                backend.close();
            }
        }
    }
}
