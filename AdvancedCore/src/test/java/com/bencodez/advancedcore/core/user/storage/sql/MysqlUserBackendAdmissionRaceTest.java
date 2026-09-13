package com.bencodez.advancedcore.core.user.storage.sql;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.advancedcore.core.user.storage.SqlUserStorage;
import com.bencodez.simpleapi.sql.mysql.ConnectionManager;
import com.bencodez.simpleapi.sql.mysql.DbType;
import com.bencodez.simpleapi.sql.mysql.config.MysqlConfig;

class MysqlUserBackendAdmissionRaceTest {
    @Test void callerQueuedBeforeCloseCannotStartAfterCloseReturns() throws Exception {
        Connection connection = mock(Connection.class);
        when(connection.prepareStatement(anyString())).thenAnswer(call -> {
            PreparedStatement statement = mock(PreparedStatement.class);
            when(statement.executeUpdate()).thenReturn(0);
            when(statement.executeQuery()).thenReturn(mock(ResultSet.class));
            return statement;
        });
        MysqlConfig config = new MysqlConfig();
        config.setDbType(DbType.MYSQL); config.setDatabase("test_database"); config.setTablePrefix(""); config.setTableName("Users"); config.setMaxThreads(1);
        try (MockedConstruction<ConnectionManager> managers = mockConstruction(ConnectionManager.class, (manager, context) -> {
            when(manager.open()).thenReturn(true); when(manager.getDbType()).thenReturn(DbType.MYSQL); when(manager.getConnection()).thenReturn(connection);
        })) {
            MysqlUserBackend backend = new MysqlUserBackend("Users", config, SqlUserSchema.builder().build(), SqlBackendLogger.NO_OP);
            SqlUserStorage retained = backend.user(UUID.randomUUID());
            Field operationsField = MysqlUserBackend.class.getDeclaredField("operations"); operationsField.setAccessible(true);
            ReentrantReadWriteLock operations = (ReentrantReadWriteLock) operationsField.get(backend);
            AtomicReference<Throwable> outcome = new AtomicReference<>();
            operations.writeLock().lock();
            Thread caller = new Thread(() -> {
                try { retained.contains(UserStorage.MYSQL); }
                catch (Throwable failure) { outcome.set(failure); }
            }, "mysql-admission-race");
            try {
                caller.start();
                for (int i = 0; i < 200 && !operations.hasQueuedThreads(); i++) Thread.sleep(5);
                assertTrue(operations.hasQueuedThreads(), "storage caller did not reach the read-lock admission boundary");
                backend.close();
            } finally { operations.writeLock().unlock(); }
            caller.join(5000);
            assertTrue(!caller.isAlive(), "delayed storage caller did not finish");
            assertInstanceOf(IllegalStateException.class, outcome.get());
        }
    }
}
