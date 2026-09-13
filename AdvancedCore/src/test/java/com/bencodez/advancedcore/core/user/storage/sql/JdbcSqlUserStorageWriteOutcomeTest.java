package com.bencodez.advancedcore.core.user.storage.sql;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.simpleapi.sql.DataType;
import com.bencodez.simpleapi.sql.data.DataValue;
import com.bencodez.simpleapi.sql.data.DataValueInt;
import com.bencodez.simpleapi.sql.data.DataValueString;

class JdbcSqlUserStorageWriteOutcomeTest {
    private static final UUID USER = UUID.fromString("c17d7784-00ce-421f-a38b-a30ed419e1a4");

    @Test void committedWriteSurvivesAutoCommitRestorationFailure() throws Exception {
        Jdbc jdbc = new Jdbc(); SQLException restore = new SQLException("restore failed");
        doThrow(restore).when(jdbc.connection).setAutoCommit(true);
        assertDoesNotThrow(() -> jdbc.write()); verify(jdbc.connection).commit(); verify(jdbc.connection, never()).rollback();
        verify(jdbc.connection).close(); verify(jdbc.logger).warn(anyString(), same(restore));
    }

    @Test void committedWriteSurvivesConnectionCloseFailure() throws Exception {
        Jdbc jdbc = new Jdbc(); SQLException close = new SQLException("close failed");
        doThrow(close).when(jdbc.connection).close();
        assertDoesNotThrow(() -> jdbc.write()); verify(jdbc.connection).commit(); verify(jdbc.connection, never()).rollback();
        verify(jdbc.connection).setAutoCommit(true); verify(jdbc.logger).warn(anyString(), same(close));
    }

    @Test void bothPostCommitCleanupFailuresAreReportedWithoutRetry() throws Exception {
        Jdbc jdbc = new Jdbc(); SQLException restore = new SQLException("restore failed"); IllegalStateException close = new IllegalStateException("driver close failed");
        doThrow(restore).when(jdbc.connection).setAutoCommit(true); doThrow(close).when(jdbc.connection).close();
        assertDoesNotThrow(() -> jdbc.write()); verify(jdbc.connection).commit(); verify(jdbc.connection).close();
        verify(jdbc.logger).warn(anyString(), same(restore)); verify(jdbc.logger).warn(anyString(), same(close));
    }

    @Test void failedWriteRetainsSqlCauseAndSuppressedCleanupFailures() throws Exception {
        Jdbc jdbc = new Jdbc(); SQLException write = new SQLException("update failed"); SQLException restore = new SQLException("restore failed"); SQLException close = new SQLException("close failed");
        when(jdbc.update.executeUpdate()).thenThrow(write); doThrow(restore).when(jdbc.connection).setAutoCommit(true); doThrow(close).when(jdbc.connection).close();
        IllegalStateException result = assertThrows(IllegalStateException.class, () -> jdbc.write());
        assertSame(write, result.getCause()); assertEquals(List.of(restore, close), Arrays.asList(write.getSuppressed()));
        verify(jdbc.connection).rollback(); verify(jdbc.connection, never()).commit(); verify(jdbc.connection).close();
    }

    @Test void runtimeFailureIsNotReplacedByRestorationFailure() throws Exception {
        Jdbc jdbc = new Jdbc(); IllegalArgumentException write = new IllegalArgumentException("value conversion failed"); SQLException restore = new SQLException("restore failed");
        when(jdbc.update.executeUpdate()).thenThrow(write); doThrow(restore).when(jdbc.connection).setAutoCommit(true);
        assertSame(write, assertThrows(IllegalArgumentException.class, () -> jdbc.write()));
        assertEquals(List.of(restore), Arrays.asList(write.getSuppressed())); verify(jdbc.connection).rollback();
        verify(jdbc.connection, never()).commit(); verify(jdbc.connection).close();
    }

    @Test void failedRollbackNeverEnablesAutoCommitOnPartialBatch() throws Exception {
        Jdbc jdbc = new Jdbc(); SQLException write = new SQLException("update failed"); SQLException rollback = new SQLException("rollback failed");
        when(jdbc.update.executeUpdate()).thenThrow(write); doThrow(rollback).when(jdbc.connection).rollback();
        IllegalStateException result = assertThrows(IllegalStateException.class, () -> jdbc.write());
        assertSame(write, result.getCause()); assertEquals(List.of(rollback), Arrays.asList(write.getSuppressed()));
        verify(jdbc.connection, never()).setAutoCommit(true); verify(jdbc.connection, never()).commit(); verify(jdbc.connection).close();
    }

    @Test void commitFailureRemainsAFailureAndAttemptsRollback() throws Exception {
        Jdbc jdbc = new Jdbc(); SQLException commit = new SQLException("commit failed"); SQLException restore = new SQLException("restore failed");
        doThrow(commit).when(jdbc.connection).commit(); doThrow(restore).when(jdbc.connection).setAutoCommit(true);
        IllegalStateException result = assertThrows(IllegalStateException.class, () -> jdbc.write());
        assertSame(commit, result.getCause()); assertEquals(List.of(restore), Arrays.asList(commit.getSuppressed()));
        verify(jdbc.connection).rollback(); verify(jdbc.connection).close();
    }

    @Test void setupFailureStillClosesConnectionAndKeepsOriginalCause() throws Exception {
        Jdbc jdbc = new Jdbc(); SQLException setup = new SQLException("cannot inspect auto-commit"); SQLException close = new SQLException("close failed");
        when(jdbc.connection.getAutoCommit()).thenThrow(setup); doThrow(close).when(jdbc.connection).close();
        IllegalStateException result = assertThrows(IllegalStateException.class, () -> jdbc.write());
        assertSame(setup, result.getCause()); assertEquals(List.of(close), Arrays.asList(setup.getSuppressed()));
        verify(jdbc.connection, never()).commit(); verify(jdbc.connection, never()).rollback(); verify(jdbc.connection).close();
    }

    @Test void throwingLoggerCannotMakeCommittedCleanupRetryable() throws Exception {
        Jdbc jdbc = new Jdbc(); SQLException restore = new SQLException("restore failed"); IllegalStateException logging = new IllegalStateException("logger failed");
        doThrow(restore).when(jdbc.connection).setAutoCommit(true); doThrow(logging).when(jdbc.logger).warn(anyString(), same(restore));
        assertDoesNotThrow(() -> jdbc.write()); assertEquals(List.of(logging), Arrays.asList(restore.getSuppressed())); verify(jdbc.connection).commit();
    }

    @Test void throwingLoggerCannotReplaceSqlOperationFailure() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        SQLException sqlFailure = new SQLException("query failed");
        IllegalStateException loggingFailure = new IllegalStateException("logger failed");
        SqlBackendLogger logger = mock(SqlBackendLogger.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenThrow(sqlFailure);
        doThrow(loggingFailure).when(logger).warn(anyString(), same(sqlFailure));
        JdbcSqlUserStorage storage = new JdbcSqlUserStorage(UserStorage.SQLITE, USER, "Users",
                SqlUserSchema.builder().build(), () -> connection, JdbcSqlUserStorage.Dialect.SQLITE, logger);

        IllegalStateException result = assertThrows(IllegalStateException.class,
                () -> storage.contains(UserStorage.SQLITE));

        assertSame(sqlFailure, result.getCause());
        assertEquals(List.of(loggingFailure), Arrays.asList(sqlFailure.getSuppressed()));
    }

    @Test void uuidMetadataCannotChangeBoundIdentityInAnyDialect() throws Exception {
        for (JdbcSqlUserStorage.Dialect dialect : JdbcSqlUserStorage.Dialect.values()) {
            Jdbc jdbc = new Jdbc(); UserStorage type = dialect == JdbcSqlUserStorage.Dialect.SQLITE ? UserStorage.SQLITE : UserStorage.MYSQL;
            JdbcSqlUserStorage user = jdbc.user(type, dialect);
            HashMap<String, DataValue> values = new HashMap<>();
            values.put("uuid", new DataValueString(UUID.randomUUID().toString())); values.put("UUID", null);
            values.put("UuId", new DataValueString("not an identity")); values.put("Points", new DataValueInt(17));
            HashMap<String, DataValue> before = new HashMap<>(values); user.writeValues(type, values); assertEquals(before, values);
            assertEquals(dialect == JdbcSqlUserStorage.Dialect.SQLITE ? 1 : 2, jdbc.sql.size());
            assertTrue(jdbc.sql.stream().noneMatch(sql -> sql.startsWith("UPDATE") || sql.contains("SET " + dialect.quote("uuid"))));
            if (dialect == JdbcSqlUserStorage.Dialect.POSTGRESQL) verify(jdbc.insert).setObject(1, USER); else verify(jdbc.insert).setString(1, USER.toString());
            verify(jdbc.insert).setInt(2, 17); verify(jdbc.connection).commit();
        }
    }

    @Test void bulkValuesUseOneAtomicUpdateStatementForAnExistingRow() throws Exception {
        Connection connection = mock(Connection.class); when(connection.getAutoCommit()).thenReturn(true);
        PreparedStatement statement = mock(PreparedStatement.class); ResultSet existing = mock(ResultSet.class); when(existing.next()).thenReturn(true); when(statement.executeQuery()).thenReturn(existing);
        List<String> sql = new ArrayList<>(); when(connection.prepareStatement(anyString())).thenAnswer(call -> { sql.add(call.getArgument(0, String.class)); return statement; });
        SqlUserSchema schema = SqlUserSchema.builder().column("A", "INTEGER", DataType.INTEGER).column("B", "INTEGER", DataType.INTEGER).build();
        JdbcSqlUserStorage user = new JdbcSqlUserStorage(UserStorage.MYSQL, USER, "Users", schema, () -> connection, JdbcSqlUserStorage.Dialect.POSTGRESQL, SqlBackendLogger.NO_OP);
        HashMap<String, DataValue> values = new LinkedHashMap<>(); values.put("A", new DataValueInt(2)); values.put("B", new DataValueInt(2));
        user.writeValues(UserStorage.MYSQL, values);
        assertEquals(List.of("SELECT 1 FROM \"Users\" WHERE \"uuid\"=? LIMIT 1 FOR UPDATE", "UPDATE \"Users\" SET \"A\"=?, \"B\"=? WHERE \"uuid\"=?"), sql);
        verify(statement).setInt(1, 2); verify(statement).setInt(2, 2); verify(statement).setObject(3, USER);
    }

    @Test void uuidOnlyBulkIsNoOpButExplicitIdentityMutationIsRejected() throws Exception {
        Jdbc jdbc = new Jdbc(); JdbcSqlUserStorage user = jdbc.user(UserStorage.SQLITE, JdbcSqlUserStorage.Dialect.SQLITE);
        HashMap<String, DataValue> values = new HashMap<>(); values.put("UUID", new DataValueString(UUID.randomUUID().toString()));
        user.writeValues(UserStorage.SQLITE, values); user.writeValues(UserStorage.SQLITE, new HashMap<>());
        assertThrows(IllegalArgumentException.class, () -> user.write(UserStorage.SQLITE, "uuid", values.get("UUID")));
        assertThrows(IllegalArgumentException.class, () -> user.writeValues(UserStorage.MYSQL, values));
        assertTrue(jdbc.sql.isEmpty()); verify(jdbc.connection, never()).getAutoCommit(); verify(jdbc.connection, never()).commit();
    }

    @Test void postgresPartialUpdateDoesNotReinsertAnExistingRequiredColumnRow() throws Exception {
        Jdbc jdbc = new Jdbc(); ResultSet row = mock(ResultSet.class); when(row.next()).thenReturn(true); when(jdbc.update.executeQuery()).thenReturn(row);
        jdbc.user(UserStorage.MYSQL, JdbcSqlUserStorage.Dialect.POSTGRESQL).write(UserStorage.MYSQL, "Points", new DataValueInt(23));
        assertTrue(jdbc.sql.stream().noneMatch(sql -> sql.startsWith("INSERT"))); verify(jdbc.update).setInt(1, 23); verify(jdbc.connection).commit(); verify(row).close();
    }

    private static final class Jdbc {
        final Connection connection = mock(Connection.class);
        final PreparedStatement insert = mock(PreparedStatement.class);
        final PreparedStatement update = mock(PreparedStatement.class);
        final SqlBackendLogger logger = mock(SqlBackendLogger.class);
        final List<String> sql = new ArrayList<>();
        Jdbc() throws SQLException {
            when(connection.getAutoCommit()).thenReturn(true); when(insert.executeUpdate()).thenReturn(1);
            when(update.executeQuery()).thenReturn(mock(ResultSet.class));
            when(connection.prepareStatement(anyString())).thenAnswer(invocation -> { String query = invocation.getArgument(0, String.class); sql.add(query); return query.startsWith("INSERT") ? insert : update; });
        }
        JdbcSqlUserStorage user(UserStorage type, JdbcSqlUserStorage.Dialect dialect) {
            return new JdbcSqlUserStorage(type, USER, "Users", SqlUserSchema.builder().column("Points", "INTEGER", DataType.INTEGER).build(), () -> connection, dialect, logger);
        }
        void write() {
            try {
                when(insert.executeUpdate()).thenReturn(0);
                ResultSet existing = mock(ResultSet.class); when(existing.next()).thenReturn(true); when(update.executeQuery()).thenReturn(existing);
            } catch (SQLException impossible) { throw new AssertionError(impossible); }
            user(UserStorage.SQLITE, JdbcSqlUserStorage.Dialect.SQLITE).write(UserStorage.SQLITE, "Points", new DataValueInt(17));
        }
    }
}
