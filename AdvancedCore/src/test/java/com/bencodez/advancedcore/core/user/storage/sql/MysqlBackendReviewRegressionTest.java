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
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import com.bencodez.simpleapi.sql.DataType;
import com.bencodez.simpleapi.sql.mysql.ConnectionManager;
import com.bencodez.simpleapi.sql.mysql.DbType;
import com.bencodez.simpleapi.sql.mysql.config.MysqlConfig;

/** Exercises real backend/SimpleAPI construction with mocked JDBC, not a live database. */
class MysqlBackendReviewRegressionTest {
    @Test void malformedUuidDiagnosticsAreBoundedAndValueFree() throws Exception {
        Fixture fixture = new Fixture(DbType.MYSQL, "Points");
        fixture.enumerationRows = List.of("1-1-1-1-1", "private-two");
        List<String> warnings = new ArrayList<>();
        SqlBackendLogger logger = new SqlBackendLogger() {
            @Override public void info(String message) { }
            @Override public void warn(String message, Throwable error) {
                warnings.add(message + ":" + error.getMessage());
            }
        };
        try (var managers = fixture.managers(); var backend = fixture.open(logger)) {
            assertTrue(backend.enumerateUsers().isEmpty());
        }
        assertEquals(List.of("Skipping malformed UUID entries while enumerating SQL users; further diagnostics suppressed:Malformed SQL UUID value"), warnings);
    }

    @Test void postgresRenamesCaseOnlyHistoricalColumnInsteadOfCreatingAParallelColumn() throws Exception {
        Fixture fixture = new Fixture(DbType.POSTGRESQL, "points");
        try (var managers = fixture.managers()) {
            try (var backend = fixture.open()) {
                assertTrue(backend.isOpen());
                assertTrue(fixture.adds.isEmpty());
                assertEquals(List.of("ALTER TABLE \"Users\" RENAME COLUMN \"points\" TO \"Points\";"), fixture.renames);
            }
            verify(managers.constructed().get(0)).close();
        }
        fixture.assertClosed();
    }

    @Test void postgresDoesNotRecreateTheExactColumn() throws Exception { existingSpelling(DbType.POSTGRESQL, "Points"); }
    @Test void mysqlRetainsCaseInsensitiveColumnLookup() throws Exception { existingSpelling(DbType.MYSQL, "points"); }
    @Test void mariaDbRetainsCaseInsensitiveColumnLookup() throws Exception { existingSpelling(DbType.MARIADB, "points"); }

    private void existingSpelling(DbType type, String name) throws Exception {
        Fixture fixture = new Fixture(type, name);
        try (var managers = fixture.managers(); var backend = fixture.open()) {
            assertTrue(backend.isOpen());
            assertTrue(fixture.adds.isEmpty());
            assertTrue(fixture.renames.isEmpty());
        }
        fixture.assertClosed();
    }

    @Test void competingUuidConversionIsAcceptedAfterFreshInspection() throws Exception {
        Fixture fixture = new Fixture(DbType.POSTGRESQL, "Points");
        fixture.uuidType = "character varying";
        fixture.migrationFailure = new SQLException("invalid input syntax for uuid", "22P02");
        fixture.competitorConverts = true;
        try (var managers = fixture.managers()) {
            try (var backend = fixture.open()) {
                assertTrue(backend.isOpen());
                assertEquals(2, fixture.uuidInspections);
                assertEquals(1, fixture.migrations);
                verify(managers.constructed().get(0), never()).close();
            }
            verify(managers.constructed().get(0)).close();
        }
        fixture.assertClosed();
    }

    @Test void genuineUuidConversionFailureStillRejectsConstruction() throws Exception {
        Fixture fixture = new Fixture(DbType.POSTGRESQL, "Points");
        fixture.uuidType = "character varying";
        fixture.migrationFailure = new SQLException("bad stored UUID", "22P02");
        try (var managers = fixture.managers()) {
            assertSame(fixture.migrationFailure, assertThrows(IllegalStateException.class, fixture::open).getCause());
            assertEquals(2, fixture.uuidInspections);
            verify(managers.constructed().get(0)).close();
        }
        fixture.assertClosed();
    }

    @Test void failedUuidReinspectionRetainsTheOriginalDdlFailure() throws Exception {
        Fixture fixture = new Fixture(DbType.POSTGRESQL, "Points");
        fixture.uuidType = "character varying";
        fixture.migrationFailure = new SQLException("conversion failed", "22P02");
        fixture.reinspectionFailure = new SQLException("connection lost", "08006");
        try (var managers = fixture.managers()) {
            assertSame(fixture.migrationFailure, assertThrows(IllegalStateException.class, fixture::open).getCause());
            assertEquals(List.of(fixture.reinspectionFailure), List.of(fixture.migrationFailure.getSuppressed()));
            verify(managers.constructed().get(0)).close();
        }
        fixture.assertClosed();
    }

    private static final class Fixture {
        final DbType type;
        final String storedColumn;
        final List<String> adds = new ArrayList<>();
        final List<String> renames = new ArrayList<>();
        final List<Connection> connections = new ArrayList<>();
        final List<PreparedStatement> statements = new ArrayList<>();
        final List<ResultSet> results = new ArrayList<>();
        String uuidType = "uuid";
        int uuidInspections, migrations;
        boolean competitorConverts;
        SQLException migrationFailure, reinspectionFailure;
        List<String> enumerationRows = List.of();

        Fixture(DbType type, String storedColumn) {
            this.type = type;
            this.storedColumn = storedColumn;
            if (type != DbType.POSTGRESQL) uuidType = "varchar";
        }

        MockedConstruction<ConnectionManager> managers() {
            return mockConstruction(ConnectionManager.class, (manager, context) -> {
                when(manager.open()).thenReturn(true);
                when(manager.getDbType()).thenReturn(type);
                when(manager.getConnection()).thenAnswer(ignored -> connection());
            });
        }

        MysqlUserBackend open() {
            return open(SqlBackendLogger.NO_OP);
        }

        MysqlUserBackend open(SqlBackendLogger logger) {
            MysqlConfig config = new MysqlConfig();
            config.setDbType(type);
            config.setDatabase("test_database");
            config.setTablePrefix("");
            config.setTableName("Users");
            config.setMaxThreads(1);
            return new MysqlUserBackend("Users", config, SqlUserSchema.builder()
                    .column("Points", "INT DEFAULT '0'", DataType.INTEGER).build(), logger);
        }

        Connection connection() throws SQLException {
            Connection connection = mock(Connection.class);
            connections.add(connection);
            when(connection.prepareStatement(anyString())).thenAnswer(call -> {
                String sql = call.getArgument(0, String.class);
                PreparedStatement statement = mock(PreparedStatement.class);
                statements.add(statement);
                when(statement.executeUpdate()).thenAnswer(ignored -> {
                    if (sql.contains(" ADD COLUMN ")) adds.add(sql);
                    if (sql.contains(" RENAME COLUMN ")) renames.add(sql);
                    if (sql.contains(" ALTER COLUMN ")) {
                        migrations++;
                        if (competitorConverts) uuidType = "uuid";
                        if (migrationFailure != null) throw migrationFailure;
                        uuidType = "uuid";
                    }
                    return 0;
                });
                when(statement.executeQuery()).thenAnswer(ignored -> {
                    boolean inspectUuid = sql.toLowerCase(Locale.ROOT).startsWith("select data_type,");
                    if (inspectUuid && ++uuidInspections > 1 && reinspectionFailure != null) throw reinspectionFailure;
                    ResultSet result = mock(ResultSet.class);
                    results.add(result);
                    if (inspectUuid) {
                        when(result.next()).thenReturn(true, false);
                        when(result.getString(1)).thenReturn(uuidType);
                        when(result.getObject(2)).thenReturn(37L);
                        when(result.getString("DATA_TYPE")).thenReturn(uuidType);
                        when(result.getObject("CHARACTER_MAXIMUM_LENGTH")).thenReturn(37L);
                        when(result.getString("COLUMN_DEFAULT")).thenReturn(null);
                    } else if (sql.endsWith("WHERE 1=0")) {
                        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
                        when(result.getMetaData()).thenReturn(metadata);
                        when(metadata.getColumnCount()).thenReturn(2);
                        when(metadata.getColumnName(1)).thenReturn("uuid");
                        when(metadata.getColumnName(2)).thenReturn(storedColumn);
                    } else if (sql.startsWith("SELECT `uuid` FROM")) {
                        AtomicInteger row = new AtomicInteger();
                        when(result.next()).thenAnswer(invocation -> row.get() < enumerationRows.size());
                        when(result.getString(1)).thenAnswer(invocation -> enumerationRows.get(row.getAndIncrement()));
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
