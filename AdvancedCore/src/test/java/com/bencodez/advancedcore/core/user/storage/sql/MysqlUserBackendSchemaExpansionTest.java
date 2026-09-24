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
import java.util.concurrent.CopyOnWriteArrayList;

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

    @Test void postgresRejectsAmbiguousCaseFoldedColumns() throws Exception {
        Fixture fixture = new Fixture(DbType.POSTGRESQL);
        fixture.existing.addAll(List.of("Points", "points"));
        SqlUserSchema schema = SqlUserSchema.builder().column("Points", "INT DEFAULT '0'", DataType.INTEGER).build();
        try (var managers = fixture.managers()) {
            IllegalStateException failure = assertThrows(IllegalStateException.class, () -> fixture.open(schema));
            assertInstanceOf(SQLException.class, failure.getCause());
            assertTrue(failure.getCause().getMessage().contains("Ambiguous case-folded SQL columns"));
            verify(managers.constructed().get(0)).close();
        }
        fixture.assertClosed();
    }

    @Test void postgresMigratesRetainedNumericColumnsBeforeStringWrites() throws Exception {
        Fixture fixture = new Fixture(DbType.POSTGRESQL);
        fixture.existing.add("Note");
        fixture.numericColumns.add("Note");
        SqlUserSchema schema = SqlUserSchema.builder().column("Note", "TEXT", DataType.STRING).build();
        try (var managers = fixture.managers(); var backend = fixture.open(schema)) {
            assertTrue(fixture.sql.contains(
                    "ALTER TABLE \"Users\" ALTER COLUMN \"Note\" TYPE TEXT USING \"Note\"::text;"));
        }
        fixture.assertClosed();
    }

    @Test void postgresNumericToStringMigrationPreservesNumericDefault() throws Exception {
        Fixture fixture = new Fixture(DbType.POSTGRESQL);
        fixture.existing.add("Note");
        fixture.numericColumns.add("Note");
        fixture.columnDefaults.put("Note", "0");
        SqlUserSchema schema = SqlUserSchema.builder().column("Note", "TEXT", DataType.STRING).build();
        try (var managers = fixture.managers(); var backend = fixture.open(schema)) {
            assertTrue(fixture.sql.contains("ALTER TABLE \"Users\" ALTER COLUMN \"Note\" DROP DEFAULT,"
                    + " ALTER COLUMN \"Note\" TYPE TEXT USING \"Note\"::text,"
                    + " ALTER COLUMN \"Note\" SET DEFAULT (0)::text;"));
        }
        fixture.assertClosed();
    }

    @Test void mysqlMigratesRetainedNumericColumnsBeforeStringWrites() throws Exception {
        assertMysqlFamilyMigratesRetainedNumericColumn(DbType.MYSQL);
    }

    @Test void mariaDbMigratesRetainedNumericColumnsBeforeStringWrites() throws Exception {
        assertMysqlFamilyMigratesRetainedNumericColumn(DbType.MARIADB);
    }

    @Test void mariaDbAcceptsPeerMigrationBeforeAttributeInspectionCompletes() throws Exception {
        Fixture fixture = new Fixture(DbType.MARIADB);
        fixture.existing.add("VoteRemindersLast");
        fixture.numericColumns.add("VoteRemindersLast");
        fixture.peerMigrationDuringAttributeInspection = true;
        SqlUserSchema schema = SqlUserSchema.builder()
                .column("VoteRemindersLast", "TEXT", DataType.STRING).build();

        try (var managers = fixture.managers(); var backend = fixture.open(schema)) {
            assertTrue(backend.isOpen());
            assertFalse(fixture.numericColumns.contains("VoteRemindersLast"));
            assertFalse(fixture.sql.stream().anyMatch(sql -> sql.startsWith(
                    "ALTER TABLE `Users` MODIFY COLUMN `VoteRemindersLast`")));
        }
        fixture.assertClosed();
    }

    @Test void mariaDbWaitsForPeerMigrationAfterAttributeInspectionFailure() throws Exception {
        Fixture fixture = new Fixture(DbType.MARIADB);
        fixture.existing.add("VoteRemindersLast");
        fixture.numericColumns.add("VoteRemindersLast");
        fixture.columnDefaults.put("VoteRemindersLast", "'0'");
        fixture.peerMigrationAfterAttributeFailure = true;
        SqlUserSchema schema = SqlUserSchema.builder()
                .column("VoteRemindersLast", "TEXT", DataType.STRING).build();

        try (var managers = fixture.managers(); var backend = fixture.open(schema)) {
            assertTrue(backend.isOpen());
            assertFalse(fixture.numericColumns.contains("VoteRemindersLast"));
            assertFalse(fixture.sql.stream().anyMatch(sql -> sql.startsWith(
                    "ALTER TABLE `Users` MODIFY COLUMN `VoteRemindersLast`")));
        }
        fixture.assertClosed();
    }

    @Test void mariaDbRetainsDeclaredNumericStorageForStringValues() throws Exception {
        Fixture fixture = new Fixture(DbType.MARIADB);
        fixture.existing.add("VoteRemindersLast");
        fixture.numericColumns.add("VoteRemindersLast");
        fixture.columnTypeNames.put("VoteRemindersLast", "BIGINT");
        fixture.columnDefaults.put("VoteRemindersLast", "'0'");
        SqlUserSchema schema = SqlUserSchema.builder()
                .column("VoteRemindersLast", "BIGINT", DataType.STRING).build();

        try (var managers = fixture.managers(); var backend = fixture.open(schema)) {
            assertTrue(backend.isOpen());
            assertTrue(fixture.numericColumns.contains("VoteRemindersLast"));
            assertFalse(fixture.sql.stream().anyMatch(sql -> sql.startsWith(
                    "SELECT IS_NULLABLE, COLUMN_DEFAULT")));
            assertFalse(fixture.sql.stream().anyMatch(sql -> sql.startsWith(
                    "ALTER TABLE `Users` MODIFY COLUMN `VoteRemindersLast`")));
        }
        fixture.assertClosed();
    }

    @Test void mariaDbWidensRetainedNumericStorageToTheDeclaredType() throws Exception {
        Fixture fixture = new Fixture(DbType.MARIADB);
        fixture.existing.add("VoteRemindersLast");
        fixture.numericColumns.add("VoteRemindersLast");
        fixture.columnDefaults.put("VoteRemindersLast", "0");
        SqlUserSchema schema = SqlUserSchema.builder()
                .column("VoteRemindersLast", "BIGINT", DataType.STRING).build();

        try (var managers = fixture.managers(); var backend = fixture.open(schema)) {
            assertTrue(backend.isOpen());
            assertTrue(fixture.sql.contains(
                    "ALTER TABLE `Users` MODIFY COLUMN `VoteRemindersLast` BIGINT NULL DEFAULT '0';"));
        }
        fixture.assertClosed();
    }

    @Test void mariaDbReconcilesRetainedDecimalPrecision() throws Exception {
        Fixture fixture = new Fixture(DbType.MARIADB);
        fixture.existing.add("Balance");
        fixture.numericColumns.add("Balance");
        fixture.columnTypeNames.put("Balance", "DECIMAL");
        fixture.columnPrecisions.put("Balance", 8);
        fixture.columnScales.put("Balance", 2);
        SqlUserSchema schema = SqlUserSchema.builder()
                .column("Balance", "DECIMAL(12,2)", DataType.STRING).build();

        try (var managers = fixture.managers(); var backend = fixture.open(schema)) {
            assertTrue(backend.isOpen());
            assertTrue(fixture.sql.contains(
                    "ALTER TABLE `Users` MODIFY COLUMN `Balance` DECIMAL(12,2) NULL;"));
        }
        fixture.assertClosed();
    }

    @Test void mariaDbAcceptsPeerMigrationAfterConflictingAlter() throws Exception {
        Fixture fixture = new Fixture(DbType.MARIADB);
        fixture.existing.add("VoteRemindersLast");
        fixture.numericColumns.add("VoteRemindersLast");
        fixture.peerMigrationOnAlterFailure = true;
        SqlUserSchema schema = SqlUserSchema.builder()
                .column("VoteRemindersLast", "TEXT", DataType.STRING).build();

        try (var managers = fixture.managers(); var backend = fixture.open(schema)) {
            assertTrue(backend.isOpen());
            assertFalse(fixture.numericColumns.contains("VoteRemindersLast"));
        }
        fixture.assertClosed();
    }

    private void assertMysqlFamilyMigratesRetainedNumericColumn(DbType dbType) throws Exception {
        Fixture fixture = new Fixture(dbType);
        fixture.existing.add("Note");
        fixture.numericColumns.add("Note");
        fixture.nonNullableColumns.add("Note");
        fixture.columnDefaults.put("Note", "0");
        SqlUserSchema schema = SqlUserSchema.builder().column("Note", "TEXT", DataType.STRING).build();
        try (var managers = fixture.managers(); var backend = fixture.open(schema)) {
            assertTrue(fixture.sql.contains(
                    "ALTER TABLE `Users` MODIFY COLUMN `Note` TEXT NOT NULL DEFAULT '0';"));
        }
        fixture.assertClosed();
    }

    @Test void postgresMigratesRetainedBooleanColumnsBeforeStringWrites() throws Exception {
        Fixture fixture = new Fixture(DbType.POSTGRESQL);
        fixture.existing.add("Note");
        fixture.booleanColumns.add("Note");
        SqlUserSchema schema = SqlUserSchema.builder().column("Note", "TEXT", DataType.STRING).build();
        try (var managers = fixture.managers(); var backend = fixture.open(schema)) {
            assertTrue(fixture.sql.contains(
                    "ALTER TABLE \"Users\" ALTER COLUMN \"Note\" TYPE TEXT USING \"Note\"::text;"));
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
        final List<String> numericColumns = new CopyOnWriteArrayList<>();
        final List<String> booleanColumns = new ArrayList<>();
        final List<String> nonNullableColumns = new ArrayList<>();
        final java.util.Map<String, String> columnDefaults = new java.util.HashMap<>();
        final java.util.Map<String, String> columnTypeNames = new java.util.HashMap<>();
        final java.util.Map<String, Integer> columnPrecisions = new java.util.HashMap<>();
        final java.util.Map<String, Integer> columnScales = new java.util.HashMap<>();
        final List<Connection> connections = new ArrayList<>();
        final List<PreparedStatement> statements = new ArrayList<>();
        final List<ResultSet> results = new ArrayList<>();
        SQLException addFailure;
        SQLException inspectFailure;
        boolean peerMigrationDuringAttributeInspection;
        boolean peerMigrationAfterAttributeFailure;
        boolean peerMigrationOnAlterFailure;
        Fixture(DbType type) { this.type = type; }

        MockedConstruction<ConnectionManager> managers() {
            return mockConstruction(ConnectionManager.class, (manager, context) -> {
                when(manager.open()).thenReturn(true);
                when(manager.getDbType()).thenReturn(type);
                when(manager.getConnection()).thenAnswer(ignored -> connection());
            });
        }

        MysqlUserBackend open() {
            return open(SqlUserSchema.builder()
                    .column("Player Name", "VARCHAR(30)", DataType.STRING)
                    .column("History", "MEDIUMTEXT", DataType.STRING)
                    .column("Votes", "INT DEFAULT '0'", DataType.INTEGER).build());
        }

        MysqlUserBackend open(SqlUserSchema schema) {
            MysqlConfig config = new MysqlConfig();
            config.setDbType(type);
            config.setDatabase("test_database");
            config.setMaxThreads(1);
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
                String[] stringParameters = new String[3];
                doAnswer(parameter -> {
                    stringParameters[parameter.getArgument(0, Integer.class)] = parameter.getArgument(1, String.class);
                    return null;
                }).when(statement).setString(anyInt(), anyString());
                when(statement.executeUpdate()).thenAnswer(ignored -> {
                    if (peerMigrationOnAlterFailure && query.startsWith("ALTER TABLE `Users` MODIFY COLUMN")) {
                        peerMigrationOnAlterFailure = false;
                        numericColumns.remove("VoteRemindersLast");
                        columnDefaults.put("VoteRemindersLast", "'0'");
                        throw new SQLException("column definition changed concurrently", "HY000", 1901);
                    }
                    if (query.startsWith("ALTER TABLE") && addFailure != null) throw addFailure;
                    return 0;
                });
                when(statement.executeQuery()).thenAnswer(ignored -> {
                    if (query.endsWith("WHERE 1=0") && inspectFailure != null) throw inspectFailure;
                    ResultSet result = mock(ResultSet.class);
                    results.add(result);
                    if (query.toLowerCase(Locale.ROOT).startsWith("select data_type,")) {
                        when(result.next()).thenReturn(true, false);
                        when(result.getString(1)).thenReturn(type == DbType.POSTGRESQL ? "uuid" : "varchar");
                        when(result.getString("DATA_TYPE")).thenReturn("varchar");
                        when(result.getObject("CHARACTER_MAXIMUM_LENGTH")).thenReturn(37L);
                        when(result.getString("COLUMN_DEFAULT")).thenReturn(null);
                    } else if (query.startsWith("SELECT IS_NULLABLE, COLUMN_DEFAULT")) {
                        String column = stringParameters[2];
                        if (peerMigrationDuringAttributeInspection && "VoteRemindersLast".equals(column)) {
                            peerMigrationDuringAttributeInspection = false;
                            numericColumns.remove(column);
                            columnDefaults.put(column, "'0'");
                        }
                        if (peerMigrationAfterAttributeFailure && "VoteRemindersLast".equals(column)) {
                            peerMigrationAfterAttributeFailure = false;
                            Thread peer = new Thread(() -> {
                                try { Thread.sleep(100); }
                                catch (InterruptedException interrupted) {
                                    Thread.currentThread().interrupt();
                                    return;
                                }
                                numericColumns.remove(column);
                            }, "schema-migration-peer");
                            peer.setDaemon(true);
                            peer.start();
                        }
                        when(result.next()).thenReturn(true, false);
                        when(result.getString(1)).thenReturn(nonNullableColumns.contains(column) ? "NO" : "YES");
                        when(result.getString(2)).thenReturn(columnDefaults.get(column));
                        when(result.getString(3)).thenReturn("");
                        when(result.getString(4)).thenReturn("");
                    } else if (query.startsWith("SELECT pg_catalog.pg_get_expr")) {
                        String column = stringParameters[2];
                        String defaultValue = columnDefaults.get(column);
                        when(result.next()).thenReturn(true, false);
                        when(result.getString(1)).thenReturn(defaultValue);
                    } else if (query.endsWith("WHERE 1=0")) {
                        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
                        when(result.getMetaData()).thenReturn(metadata);
                        when(metadata.getColumnCount()).thenReturn(existing.size());
                        for (int i = 0; i < existing.size(); i++) {
                            when(metadata.getColumnName(i + 1)).thenReturn(existing.get(i));
                            String existingColumn = existing.get(i);
                            when(metadata.getColumnType(i + 1)).thenReturn(booleanColumns.contains(existingColumn)
                                    ? java.sql.Types.BOOLEAN : numericColumns.contains(existing.get(i))
                                    ? jdbcType(columnTypeNames.get(existingColumn)) : java.sql.Types.VARCHAR);
                            when(metadata.getColumnTypeName(i + 1)).thenReturn(columnTypeNames.get(existingColumn));
                            when(metadata.getPrecision(i + 1)).thenReturn(columnPrecisions.getOrDefault(existingColumn, 0));
                            when(metadata.getScale(i + 1)).thenReturn(columnScales.getOrDefault(existingColumn, 0));
                        }
                    }
                    return result;
                });
                return statement;
            });
            return connection;
        }

        private static int jdbcType(String typeName) {
            if ("BIGINT".equals(typeName)) return java.sql.Types.BIGINT;
            if ("DECIMAL".equals(typeName)) return java.sql.Types.DECIMAL;
            return java.sql.Types.INTEGER;
        }

        void assertClosed() throws SQLException {
            for (Connection connection : connections) verify(connection).close();
            for (PreparedStatement statement : statements) verify(statement, atLeastOnce()).close();
            for (ResultSet result : results) verify(result).close();
        }
    }
}
