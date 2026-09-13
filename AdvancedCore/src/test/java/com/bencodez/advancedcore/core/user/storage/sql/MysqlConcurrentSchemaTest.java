package com.bencodez.advancedcore.core.user.storage.sql;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import com.bencodez.simpleapi.sql.DataType;
import com.bencodez.simpleapi.sql.mysql.ConnectionManager;
import com.bencodez.simpleapi.sql.mysql.DbType;
import com.bencodez.simpleapi.sql.mysql.config.MysqlConfig;

/** Two-node ADD race at the JDBC boundary, not a live shared database test. */
class MysqlConcurrentSchemaTest {
    @Test void mysqlAcceptsColumnCreatedByAnotherNode() throws Exception { concurrent(DbType.MYSQL); }
    @Test void mariaDbAcceptsColumnCreatedByAnotherNode() throws Exception { concurrent(DbType.MARIADB); }
    @Test void postgresAcceptsColumnCreatedByAnotherNode() throws Exception { concurrent(DbType.POSTGRESQL); }

    private void concurrent(DbType type) throws Exception {
        Fixture fixture = new Fixture(type);
        fixture.columnAppears = true;
        try (var managers = fixture.managers(); var backend = fixture.open()) {
            assertTrue(backend.isOpen());
            assertEquals(2, fixture.inspections);
            assertEquals(1, fixture.adds);
            verify(managers.constructed().get(0), never()).close();
        }
        fixture.assertClosed();
    }

    @Test void duplicateCodeWithoutColumnStillFailsAndClosesOwner() throws Exception {
        Fixture fixture = new Fixture(DbType.POSTGRESQL);
        try (var managers = fixture.managers()) {
            assertSame(fixture.ddl, assertThrows(IllegalStateException.class, fixture::open).getCause());
            verify(managers.constructed().get(0)).close();
        }
        fixture.assertClosed();
    }

    @Test void unrelatedDdlErrorIsNeverHiddenByAnExistingColumn() throws Exception {
        Fixture fixture = new Fixture(DbType.MYSQL);
        fixture.columnAppears = true;
        fixture.ddl = new SQLException("permission denied", "42000", 1142);
        try (var managers = fixture.managers()) {
            assertSame(fixture.ddl, assertThrows(IllegalStateException.class, fixture::open).getCause());
            assertEquals(1, fixture.inspections);
            verify(managers.constructed().get(0)).close();
        }
        fixture.assertClosed();
    }

    @Test void failedRecheckRetainsBothDdlAndInspectionEvidence() throws Exception {
        Fixture fixture = new Fixture(DbType.POSTGRESQL);
        fixture.recheckFailure = new SQLException("inspection failed");
        try (var managers = fixture.managers()) {
            assertSame(fixture.ddl, assertThrows(IllegalStateException.class, fixture::open).getCause());
            assertEquals(List.of(fixture.recheckFailure), List.of(fixture.ddl.getSuppressed()));
            verify(managers.constructed().get(0)).close();
        }
        fixture.assertClosed();
    }

    private static final class Fixture {
        final DbType type;
        final List<Connection> connections = new ArrayList<>();
        final List<PreparedStatement> statements = new ArrayList<>();
        final List<PreparedStatement> legacyStatements = new ArrayList<>();
        final List<ResultSet> results = new ArrayList<>();
        SQLException ddl;
        SQLException recheckFailure;
        boolean columnAppears;
        int inspections, adds;
        Fixture(DbType type) {
            this.type = type;
            ddl = type == DbType.POSTGRESQL ? new SQLException("duplicate column", "42701")
                    : new SQLException("duplicate column", "42S21", 1060);
        }
        MockedConstruction<ConnectionManager> managers() {
            return mockConstruction(ConnectionManager.class, (manager, context) -> {
                when(manager.open()).thenReturn(true);
                when(manager.getDbType()).thenReturn(type);
                when(manager.getConnection()).thenAnswer(ignored -> connection());
            });
        }
        MysqlUserBackend open() {
            MysqlConfig config = new MysqlConfig();
            config.setDbType(type);
            config.setDatabase("test_database");
            config.setMaxThreads(1);
            return new MysqlUserBackend("Users", config, SqlUserSchema.builder()
                    .column("Player Name", "VARCHAR(30)", DataType.STRING).build(), SqlBackendLogger.NO_OP);
        }
        Connection connection() throws SQLException {
            Connection connection = mock(Connection.class);
            connections.add(connection);
            when(connection.prepareStatement(anyString())).thenAnswer(call -> {
                String sql = call.getArgument(0, String.class);
                PreparedStatement statement = mock(PreparedStatement.class);
                if (sql.contains(" ADD COLUMN ") || sql.endsWith("WHERE 1=0")) statements.add(statement);
                else legacyStatements.add(statement);
                when(statement.executeUpdate()).thenAnswer(ignored -> {
                    if (sql.contains(" ADD COLUMN ")) { adds++; throw ddl; }
                    return 0;
                });
                when(statement.executeQuery()).thenAnswer(ignored -> {
                    if (sql.endsWith("WHERE 1=0")) {
                        inspections++;
                        if (inspections > 1 && recheckFailure != null) throw recheckFailure;
                    }
                    ResultSet result = mock(ResultSet.class);
                    results.add(result);
                    if (sql.toLowerCase(Locale.ROOT).startsWith("select data_type,")) {
                        when(result.next()).thenReturn(true, false);
                        when(result.getString(1)).thenReturn("uuid");
                    } else if (sql.endsWith("WHERE 1=0")) {
                        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
                        when(result.getMetaData()).thenReturn(metadata);
                        when(metadata.getColumnCount()).thenReturn(columnAppears && adds > 0 ? 2 : 1);
                        when(metadata.getColumnName(1)).thenReturn("uuid");
                        when(metadata.getColumnName(2)).thenReturn("Player Name");
                    }
                    return result;
                });
                return statement;
            });
            return connection;
        }
        void assertClosed() throws SQLException {
            for (Connection connection : connections) verify(connection).close();
            for (PreparedStatement statement : statements) verify(statement).close();
            // SimpleAPI-owned statements (including connection setup) may close twice.
            // Keep exactly-once assertions for this backend's inspection and ADD statements.
            for (PreparedStatement statement : legacyStatements) verify(statement, atLeastOnce()).close();
            for (ResultSet result : results) verify(result).close();
        }
    }
}
