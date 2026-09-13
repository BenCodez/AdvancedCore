package com.bencodez.advancedcore.core.user.storage.sql;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.simpleapi.sql.DataType;
import com.bencodez.simpleapi.sql.data.DataValue;
import com.bencodez.simpleapi.sql.data.DataValueInt;
import com.bencodez.simpleapi.sql.data.DataValueString;

/** Failure-injection JDBC contracts; these mocks are not live database tests. */
class JdbcSqlUserStorageWriteOutcomeTest {
    private static final UUID USER = UUID.fromString("c17d7784-00ce-421f-a38b-a30ed419e1a4");

    @Test
    void committedWriteSurvivesAutoCommitRestorationFailure() throws Exception {
        Jdbc jdbc = new Jdbc();
        SQLException restore = new SQLException("restore failed");
        doThrow(restore).when(jdbc.connection).setAutoCommit(true);

        assertDoesNotThrow(() -> jdbc.write());
        verify(jdbc.connection).commit();
        verify(jdbc.connection, never()).rollback();
        verify(jdbc.connection).close();
        verify(jdbc.logger).warn(anyString(), same(restore));
    }

    @Test
    void committedWriteSurvivesConnectionCloseFailure() throws Exception {
        Jdbc jdbc = new Jdbc();
        SQLException close = new SQLException("close failed");
        doThrow(close).when(jdbc.connection).close();

        assertDoesNotThrow(() -> jdbc.write());
        verify(jdbc.connection).commit();
        verify(jdbc.connection, never()).rollback();
        verify(jdbc.connection).setAutoCommit(true);
        verify(jdbc.logger).warn(anyString(), same(close));
    }

    @Test
    void bothPostCommitCleanupFailuresAreReportedWithoutRetry() throws Exception {
        Jdbc jdbc = new Jdbc();
        SQLException restore = new SQLException("restore failed");
        IllegalStateException close = new IllegalStateException("driver close failed");
        doThrow(restore).when(jdbc.connection).setAutoCommit(true);
        doThrow(close).when(jdbc.connection).close();

        assertDoesNotThrow(() -> jdbc.write());
        verify(jdbc.connection).commit();
        verify(jdbc.connection).close();
        verify(jdbc.logger).warn(anyString(), same(restore));
        verify(jdbc.logger).warn(anyString(), same(close));
    }

    @Test
    void failedWriteRetainsSqlCauseAndSuppressedCleanupFailures() throws Exception {
        Jdbc jdbc = new Jdbc();
        SQLException write = new SQLException("update failed");
        SQLException restore = new SQLException("restore failed");
        SQLException close = new SQLException("close failed");
        when(jdbc.update.executeUpdate()).thenThrow(write);
        doThrow(restore).when(jdbc.connection).setAutoCommit(true);
        doThrow(close).when(jdbc.connection).close();

        IllegalStateException result = assertThrows(IllegalStateException.class, () -> jdbc.write());
        assertSame(write, result.getCause());
        assertEquals(List.of(restore, close), Arrays.asList(write.getSuppressed()));
        verify(jdbc.connection).rollback();
        verify(jdbc.connection, never()).commit();
        verify(jdbc.connection).close();
    }

    @Test
    void runtimeFailureIsNotReplacedByRestorationFailure() throws Exception {
        Jdbc jdbc = new Jdbc();
        IllegalArgumentException write = new IllegalArgumentException("value conversion failed");
        SQLException restore = new SQLException("restore failed");
        when(jdbc.update.executeUpdate()).thenThrow(write);
        doThrow(restore).when(jdbc.connection).setAutoCommit(true);

        assertSame(write, assertThrows(IllegalArgumentException.class, () -> jdbc.write()));
        assertEquals(List.of(restore), Arrays.asList(write.getSuppressed()));
        verify(jdbc.connection).rollback();
        verify(jdbc.connection, never()).commit();
        verify(jdbc.connection).close();
    }

    @Test
    void failedRollbackNeverEnablesAutoCommitOnPartialBatch() throws Exception {
        Jdbc jdbc = new Jdbc();
        SQLException write = new SQLException("update failed");
        SQLException rollback = new SQLException("rollback failed");
        when(jdbc.update.executeUpdate()).thenThrow(write);
        doThrow(rollback).when(jdbc.connection).rollback();

        IllegalStateException result = assertThrows(IllegalStateException.class, () -> jdbc.write());
        assertSame(write, result.getCause());
        assertEquals(List.of(rollback), Arrays.asList(write.getSuppressed()));
        verify(jdbc.connection, never()).setAutoCommit(true);
        verify(jdbc.connection, never()).commit();
        verify(jdbc.connection).close();
    }

    @Test
    void commitFailureRemainsAFailureAndAttemptsRollback() throws Exception {
        Jdbc jdbc = new Jdbc();
        SQLException commit = new SQLException("commit failed");
        SQLException restore = new SQLException("restore failed");
        doThrow(commit).when(jdbc.connection).commit();
        doThrow(restore).when(jdbc.connection).setAutoCommit(true);

        IllegalStateException result = assertThrows(IllegalStateException.class, () -> jdbc.write());
        assertSame(commit, result.getCause());
        assertEquals(List.of(restore), Arrays.asList(commit.getSuppressed()));
        verify(jdbc.connection).rollback();
        verify(jdbc.connection).close();
    }

    @Test
    void setupFailureStillClosesConnectionAndKeepsOriginalCause() throws Exception {
        Jdbc jdbc = new Jdbc();
        SQLException setup = new SQLException("cannot inspect auto-commit");
        SQLException close = new SQLException("close failed");
        when(jdbc.connection.getAutoCommit()).thenThrow(setup);
        doThrow(close).when(jdbc.connection).close();

        IllegalStateException result = assertThrows(IllegalStateException.class, () -> jdbc.write());
        assertSame(setup, result.getCause());
        assertEquals(List.of(close), Arrays.asList(setup.getSuppressed()));
        verify(jdbc.connection, never()).commit();
        verify(jdbc.connection, never()).rollback();
        verify(jdbc.connection).close();
    }

    @Test
    void throwingLoggerCannotMakeCommittedCleanupRetryable() throws Exception {
        Jdbc jdbc = new Jdbc();
        SQLException restore = new SQLException("restore failed");
        IllegalStateException logging = new IllegalStateException("logger failed");
        doThrow(restore).when(jdbc.connection).setAutoCommit(true);
        doThrow(logging).when(jdbc.logger).warn(anyString(), same(restore));

        assertDoesNotThrow(() -> jdbc.write());
        assertEquals(List.of(logging), Arrays.asList(restore.getSuppressed()));
        verify(jdbc.connection).commit();
        verify(jdbc.connection).close();
    }

    @Test
    void uuidMetadataCannotChangeBoundIdentityInAnyDialect() throws Exception {
        for (JdbcSqlUserStorage.Dialect dialect : JdbcSqlUserStorage.Dialect.values()) {
            Jdbc jdbc = new Jdbc();
            UserStorage type = dialect == JdbcSqlUserStorage.Dialect.SQLITE ? UserStorage.SQLITE : UserStorage.MYSQL;
            JdbcSqlUserStorage user = jdbc.user(type, dialect);
            HashMap<String, DataValue> values = new HashMap<>();
            values.put("uuid", new DataValueString(UUID.randomUUID().toString()));
            values.put("UUID", null);
            values.put("UuId", new DataValueString("not an identity"));
            values.put("Points", new DataValueInt(17));
            HashMap<String, DataValue> before = new HashMap<>(values);
            user.writeValues(type, values);
            assertEquals(before, values);
            assertEquals(2, jdbc.sql.size());
            assertFalse(jdbc.sql.get(1).contains("SET " + dialect.quote("uuid")));
            if (dialect == JdbcSqlUserStorage.Dialect.POSTGRESQL) {
                verify(jdbc.insert).setObject(1, USER);
                verify(jdbc.update).setObject(2, USER);
            } else {
                verify(jdbc.insert).setString(1, USER.toString());
                verify(jdbc.update).setString(2, USER.toString());
            }
            verify(jdbc.update).setInt(1, 17);
            verify(jdbc.connection).commit();
        }
    }

    @Test
    void uuidOnlyBulkIsNoOpButExplicitIdentityMutationIsRejected() throws Exception {
        Jdbc jdbc = new Jdbc();
        JdbcSqlUserStorage user = jdbc.user(UserStorage.SQLITE, JdbcSqlUserStorage.Dialect.SQLITE);
        HashMap<String, DataValue> values = new HashMap<>();
        values.put("UUID", new DataValueString(UUID.randomUUID().toString()));
        user.writeValues(UserStorage.SQLITE, values);
        user.writeValues(UserStorage.SQLITE, new HashMap<>());
        assertThrows(IllegalArgumentException.class,
                () -> user.write(UserStorage.SQLITE, "uuid", values.get("UUID")));
        assertThrows(IllegalArgumentException.class, () -> user.writeValues(UserStorage.MYSQL, values));
        assertTrue(jdbc.sql.isEmpty());
        verify(jdbc.connection, never()).getAutoCommit();
        verify(jdbc.connection, never()).commit();
    }

    private static final class Jdbc {
        final Connection connection = mock(Connection.class);
        final PreparedStatement insert = mock(PreparedStatement.class);
        final PreparedStatement update = mock(PreparedStatement.class);
        final SqlBackendLogger logger = mock(SqlBackendLogger.class);
        final List<String> sql = new ArrayList<>();

        Jdbc() throws SQLException {
            when(connection.getAutoCommit()).thenReturn(true);
            when(connection.prepareStatement(anyString())).thenAnswer(invocation -> {
                String query = invocation.getArgument(0, String.class);
                sql.add(query);
                return query.startsWith("INSERT") ? insert : update;
            });
        }

        JdbcSqlUserStorage user(UserStorage type, JdbcSqlUserStorage.Dialect dialect) {
            return new JdbcSqlUserStorage(type, USER, "Users",
                    SqlUserSchema.builder().column("Points", "INTEGER", DataType.INTEGER).build(),
                    () -> connection, dialect, logger);
        }

        void write() {
            user(UserStorage.SQLITE, JdbcSqlUserStorage.Dialect.SQLITE)
                    .write(UserStorage.SQLITE, "Points", new DataValueInt(17));
        }
    }
}
