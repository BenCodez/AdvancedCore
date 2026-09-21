package com.bencodez.advancedcore.core.user.storage.sql;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import com.bencodez.simpleapi.sql.DataType;
import com.bencodez.simpleapi.sql.mysql.ConnectionManager;
import com.bencodez.simpleapi.sql.mysql.DbType;

class MysqlBorrowedSchemaReconciliationTest {
    /**
     * Verifies that reconciling a table with only a UUID column adds all registered schema columns
     * without enumerating existing users, and that subsequent reconciliations are idempotent.
     *
     * @throws Exception if test setup or assertions fail
     */
    @Test
    void proxyFirstUuidOnlyTableGetsVotingPluginColumnsWithRegisteredDefinitions() throws Exception {
        Fixture fixture = new Fixture();
        SqlUserSchema schema = votingPluginSchema();

        MysqlUserBackend.reconcileExistingTable("VotingPlugin_Users", fixture.mysql, schema, SqlBackendLogger.NO_OP);

        assertTrue(fixture.sql.contains(
                "CREATE TABLE IF NOT EXISTS `VotingPlugin_Users` (`uuid` VARCHAR(37), `LastVotes` TEXT, `VoteRemindersMap` LONGTEXT, PRIMARY KEY (`uuid`));"));
        assertTrue(fixture.sql.contains(
                "ALTER TABLE `VotingPlugin_Users` ADD COLUMN `LastVotes` TEXT;"));
        assertTrue(fixture.sql.contains(
                "ALTER TABLE `VotingPlugin_Users` ADD COLUMN `VoteRemindersMap` LONGTEXT;"));
        assertEquals(List.of("uuid", "LastVotes", "VoteRemindersMap"), fixture.existing);
        assertFalse(fixture.sql.stream().anyMatch(sql ->
                sql.startsWith("SELECT `uuid` FROM `VotingPlugin_Users`")),
                "schema-only reconciliation must not enumerate every user row");

        long alters = fixture.sql.stream().filter(sql -> sql.startsWith("ALTER TABLE")).count();
        MysqlUserBackend.reconcileExistingTable("VotingPlugin_Users", fixture.mysql, schema, SqlBackendLogger.NO_OP);
        assertEquals(alters, fixture.sql.stream().filter(sql -> sql.startsWith("ALTER TABLE")).count(),
                "restarting against an already-correct schema must be idempotent");

        fixture.assertBorrowedPoolOpen();
        fixture.assertJdbcClosed();
    }

    /**
     * Verifies that reconciling a table with some columns already present only adds the missing ones.
     *
     * @throws Exception if test setup or assertions fail
     */
    @Test
    void partiallyExistingRegisteredSchemaAddsOnlyTheMissingColumn() throws Exception {
        Fixture fixture = new Fixture();
        fixture.existing.add("LastVotes");

        MysqlUserBackend.reconcileExistingTable("VotingPlugin_Users", fixture.mysql,
                votingPluginSchema(), SqlBackendLogger.NO_OP);

        assertFalse(fixture.sql.contains(
                "ALTER TABLE `VotingPlugin_Users` ADD COLUMN `LastVotes` TEXT;"));
        assertTrue(fixture.sql.contains(
                "ALTER TABLE `VotingPlugin_Users` ADD COLUMN `VoteRemindersMap` LONGTEXT;"));
        assertEquals(List.of("uuid", "LastVotes", "VoteRemindersMap"), fixture.existing);
        fixture.assertBorrowedPoolOpen();
        fixture.assertJdbcClosed();
    }

    /**
     * Verifies that a duplicate column error during DDL is tolerated if a subsequent physical
     * inspection confirms the column now exists, handling concurrent schema modifications.
     *
     * @throws Exception if test setup or assertions fail
     */
    @Test
    void duplicateColumnRaceIsAcceptedOnlyAfterPhysicalRecheck() throws Exception {
        Fixture fixture = new Fixture();
        fixture.addFailure = new SQLException("duplicate column", "42S21", 1060);
        fixture.columnAppearsOnFailure = true;

        assertDoesNotThrow(() -> MysqlUserBackend.reconcileExistingTable("VotingPlugin_Users", fixture.mysql,
                SqlUserSchema.builder().column("VoteRemindersMap", "LONGTEXT", DataType.STRING).build(),
                SqlBackendLogger.NO_OP));
        assertTrue(fixture.existing.contains("VoteRemindersMap"));
        fixture.assertBorrowedPoolOpen();
        fixture.assertJdbcClosed();
    }

    /**
     * Verifies that a duplicate column error without a corresponding physical column remains
     * a fatal exception, preventing false-positive recovery.
     *
     * @throws Exception if test setup or assertions fail
     */
    @Test
    void duplicateColumnResponseWithoutPhysicalColumnStillFails() throws Exception {
        Fixture fixture = new Fixture();
        fixture.addFailure = new SQLException("duplicate column", "42S21", 1060);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> MysqlUserBackend.reconcileExistingTable("VotingPlugin_Users", fixture.mysql,
                        SqlUserSchema.builder().column("VoteRemindersMap", "LONGTEXT", DataType.STRING).build(),
                        SqlBackendLogger.NO_OP));

        assertSame(fixture.addFailure, failure.getCause());
        fixture.assertBorrowedPoolOpen();
        fixture.assertJdbcClosed();
    }

    /**
     * Verifies that non-duplicate DDL errors remain fatal even if the column appears,
     * ensuring proper error handling for permission and other database issues.
     *
     * @throws Exception if test setup or assertions fail
     */
    @Test
    void unrelatedDdlFailureRemainsFatalEvenIfColumnAppears() throws Exception {
        Fixture fixture = new Fixture();
        fixture.addFailure = new SQLException("permission denied", "42000", 1142);
        fixture.columnAppearsOnFailure = true;

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> MysqlUserBackend.reconcileExistingTable("VotingPlugin_Users", fixture.mysql,
                        SqlUserSchema.builder().column("VoteRemindersMap", "LONGTEXT", DataType.STRING).build(),
                        SqlBackendLogger.NO_OP));

        assertSame(fixture.addFailure, failure.getCause());
        fixture.assertBorrowedPoolOpen();
        fixture.assertJdbcClosed();
    }

    /**
     * Creates a sample schema with columns typical of a voting plugin.
     *
     * @return a schema with LastVotes and VoteRemindersMap columns
     */
    private static SqlUserSchema votingPluginSchema() {
        return SqlUserSchema.builder()
                .column("LastVotes", "TEXT", DataType.STRING)
                .column("VoteRemindersMap", "LONGTEXT", DataType.STRING)
                .build();
    }

    private static final class Fixture {
        final com.bencodez.simpleapi.sql.mysql.MySQL mysql =
                mock(com.bencodez.simpleapi.sql.mysql.MySQL.class);
        final ConnectionManager manager = mock(ConnectionManager.class);
        final List<String> existing = new ArrayList<>(List.of("uuid"));
        final List<String> sql = new ArrayList<>();
        final List<Connection> connections = new ArrayList<>();
        final List<PreparedStatement> statements = new ArrayList<>();
        final List<ResultSet> results = new ArrayList<>();
        SQLException addFailure;
        boolean columnAppearsOnFailure;

        /**
         * Initializes test fixtures with mocked MySQL connection infrastructure.
         *
         * @throws SQLException if mock setup fails
         */
        Fixture() throws SQLException {
            when(mysql.getConnectionManager()).thenReturn(manager);
            when(manager.getDbType()).thenReturn(DbType.MARIADB);
            when(manager.getConnection()).thenAnswer(ignored -> connection());
        }

        /**
         * Creates a mocked connection that simulates DDL operations and result sets.
         *
         * @return a mocked connection with prepared statement behavior
         * @throws SQLException if mock setup fails
         */
        private Connection connection() throws SQLException {
            Connection connection = mock(Connection.class);
            connections.add(connection);
            when(connection.prepareStatement(anyString())).thenAnswer(call -> {
                String query = call.getArgument(0, String.class);
                sql.add(query);
                PreparedStatement statement = mock(PreparedStatement.class);
                statements.add(statement);
                when(statement.executeUpdate()).thenAnswer(ignored -> {
                    if (query.contains(" ADD COLUMN ")) {
                        String column = addedColumn(query);
                        if (addFailure != null) {
                            if (columnAppearsOnFailure && existing.stream().noneMatch(column::equalsIgnoreCase)) {
                                existing.add(column);
                            }
                            throw addFailure;
                        }
                        if (existing.stream().noneMatch(column::equalsIgnoreCase)) existing.add(column);
                    }
                    return 0;
                });
                when(statement.executeQuery()).thenAnswer(ignored -> resultFor(query));
                return statement;
            });
            return connection;
        }

        /**
         * Creates a mocked result set appropriate for the given query type.
         *
         * @param query the SQL query to mock results for
         * @return a mocked result set with query-appropriate data
         * @throws SQLException if mock setup fails
         */
        private ResultSet resultFor(String query) throws SQLException {
            ResultSet result = mock(ResultSet.class);
            results.add(result);
            if (query.toLowerCase(Locale.ROOT).startsWith("select data_type,")) {
                when(result.next()).thenReturn(true, false);
                when(result.getString(1)).thenReturn("varchar");
                when(result.getString("DATA_TYPE")).thenReturn("varchar");
                when(result.getObject("CHARACTER_MAXIMUM_LENGTH")).thenReturn(37L);
                when(result.getString("COLUMN_DEFAULT")).thenReturn(null);
            } else if (query.endsWith("WHERE 1=0")) {
                ResultSetMetaData metadata = mock(ResultSetMetaData.class);
                when(result.getMetaData()).thenReturn(metadata);
                when(metadata.getColumnCount()).thenAnswer(ignored -> existing.size());
                for (int i = 0; i < existing.size(); i++) {
                    int column = i + 1;
                    when(metadata.getColumnName(column)).thenAnswer(ignored -> existing.get(column - 1));
                    when(metadata.getColumnType(column)).thenReturn(Types.VARCHAR);
                }
            }
            return result;
        }

        /**
         * Extracts the column name from an ALTER TABLE ADD COLUMN statement.
         *
         * @param query the DDL query containing an ADD COLUMN clause
         * @return the column name being added
         */
        private String addedColumn(String query) {
            String marker = " ADD COLUMN `";
            int start = query.indexOf(marker);
            if (start < 0) throw new AssertionError("Missing ADD COLUMN marker: " + query);
            start += marker.length();
            int end = query.indexOf('`', start);
            if (end < 0) throw new AssertionError("Missing closing identifier quote: " + query);
            return query.substring(start, end);
        }

        /**
         * Verifies that the borrowed connection pool was never closed during reconciliation.
         */
        void assertBorrowedPoolOpen() {
            verify(mysql, never()).disconnect();
            verify(manager, never()).close();
        }

        /**
         * Verifies that all JDBC resources (connections, statements, result sets) were properly closed.
         *
         * @throws SQLException if verification fails
         */
        void assertJdbcClosed() throws SQLException {
            for (Connection connection : connections) verify(connection, atLeastOnce()).close();
            for (PreparedStatement statement : statements) verify(statement, atLeastOnce()).close();
            for (ResultSet result : results) verify(result, atLeastOnce()).close();
        }
    }
}
