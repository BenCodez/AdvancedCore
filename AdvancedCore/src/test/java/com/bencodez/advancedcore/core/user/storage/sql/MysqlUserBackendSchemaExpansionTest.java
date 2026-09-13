package com.bencodez.advancedcore.core.user.storage.sql;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import com.bencodez.simpleapi.sql.DataType;
import com.bencodez.simpleapi.sql.mysql.ConnectionManager;
import com.bencodez.simpleapi.sql.mysql.DbType;
import com.bencodez.simpleapi.sql.mysql.config.MysqlConfig;

/** Mocked JDBC contract tests through the real backend and SimpleAPI construction. */
class MysqlUserBackendSchemaExpansionTest {
    @Test void mysqlExpansionMatchesDeclaredCreateTypes() throws Exception { checkTypes(DbType.MYSQL); }
    @Test void mariaDbExpansionMatchesDeclaredCreateTypes() throws Exception { checkTypes(DbType.MARIADB); }
    @Test void postgresExpansionUsesTheSameNormalizationAsCreate() throws Exception { checkTypes(DbType.POSTGRESQL); }

    private void checkTypes(DbType type) throws Exception {
        Fixture fixture = new Fixture(type);
        try (var managers = fixture.managers(); var backend = fixture.open()) {
            assertTrue(backend.isOpen());
            String q = type == DbType.POSTGRESQL ? "\"" : "`";
            String prefix = "ALTER TABLE " + q + "Users" + q + " ADD COLUMN ";
            assertTrue(fixture.sql.contains(prefix + q + "Player Name" + q + " VARCHAR(30);"));
            assertTrue(fixture.sql.contains(prefix + q + "History" + q + " "
                    + (type == DbType.POSTGRESQL ? "TEXT" : "MEDIUMTEXT") + ";"));
            assertTrue(fixture.sql.contains(prefix + q + "Votes" + q + " INT DEFAULT '0';"));
            assertEquals(3, fixture.sql.stream().filter(s -> s.startsWith("ALTER TABLE")).count());
        }
        fixture.assertClosed();
    }

    @Test void existingColumnsAreNotAlteredEvenWithDifferentCasing() throws Exception {
        Fixture fixture = new Fixture(DbType.MYSQL);
        fixture.existing.addAll(List.of("player name", "HISTORY", "Votes"));
        try (var managers = fixture.managers(); var backend = fixture.open()) {
            assertFalse(fixture.sql.stream().anyMatch(s -> s.startsWith("ALTER TABLE")));
        }
        fixture.assertClosed();
    }

    @Test void failedAddRejectsInitializationAndClosesThePool() throws Exception {
        Fixture fixture = new Fixture(DbType.POSTGRESQL);
        fixture.addFailure = new SQLException("DDL denied");
        try (var managers = fixture.managers()) {
            var error = assertThrows(IllegalStateException.class, fixture::open);
            assertSame(fixture.addFailure, error.getCause());
            verify(managers.constructed().get(0)).close();
        }
        fixture.assertClosed();
    }

    @Test void failedInspectionIsNotTreatedAsAnEmptySchema() throws Exception {
        Fixture fixture = new Fixture(DbType.MYSQL);
        fixture.inspectFailure = new SQLException("metadata denied");
        try (var managers = fixture.managers()) {
            var error = assertThrows(IllegalStateException.class, fixture::open);
            assertSame(fixture.inspectFailure, error.getCause());
            assertFalse(fixture.sql.stream().anyMatch(s -> s.startsWith("ALTER TABLE")));
            verify(managers.constructed().get(0)).close();
        }
        fixture.assertClosed();
    }

    private static final class Fixture {
        final DbType type;
        final List<String> sql = new ArrayList<>();
        final List<String> existing = new ArrayList<>(List.of("uuid"));
        final List<Connection> connections = new ArrayList<>();
        final List<PreparedStatement> statements = new ArrayList<>();
        final List<ResultSet> results = new ArrayList<>();
        SQLException addFailure;
        SQLException inspectFailure;
        Fixture(DbType type) { this.type = type; }

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
            SqlUserSchema schema = SqlUserSchema.builder()
                    .column("Player Name", "VARCHAR(30)", DataType.STRING)
                    .column("History", "MEDIUMTEXT", DataType.STRING)
                    .column("Votes", "INT DEFAULT '0'", DataType.INTEGER).build();
            return new MysqlUserBackend("Users", config, schema, SqlBackendLogger.NO_OP);
        }

        Connection connection() throws SQLException {
            Connection connection = mock(Connection.class);
            connections.add(connection);
            when(connection.prepareStatement(anyString())).thenAnswer(call -> {
                String query = call.getArgument(0, String.class);
                sql.add(query);
                PreparedStatement statement = mock(PreparedStatement.class);
                statements.add(statement);
                when(statement.executeUpdate()).thenAnswer(ignored -> {
                    if (query.startsWith("ALTER TABLE") && addFailure != null) throw addFailure;
                    return 0;
                });
                when(statement.executeQuery()).thenAnswer(ignored -> {
                    if (query.endsWith("WHERE 1=0") && inspectFailure != null) throw inspectFailure;
                    ResultSet result = mock(ResultSet.class);
                    results.add(result);
                    if (query.toLowerCase(Locale.ROOT).startsWith("select data_type,")) {
                        when(result.next()).thenReturn(true, false);
                        when(result.getString(1)).thenReturn("uuid");
                    } else if (query.endsWith("WHERE 1=0")) {
                        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
                        when(result.getMetaData()).thenReturn(metadata);
                        when(metadata.getColumnCount()).thenReturn(existing.size());
                        for (int i = 0; i < existing.size(); i++) {
                            when(metadata.getColumnName(i + 1)).thenReturn(existing.get(i));
                        }
                    }
                    return result;
                });
                return statement;
            });
            return connection;
        }

        void assertClosed() throws SQLException {
            for (Connection connection : connections) verify(connection).close();
            for (PreparedStatement statement : statements) verify(statement, atLeastOnce()).close();
            for (ResultSet result : results) verify(result).close();
        }
    }
}
