package com.bencodez.advancedcore.core.user.storage.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.simpleapi.sql.mysql.ConnectionManager;
import com.bencodez.simpleapi.sql.mysql.DbType;
import com.bencodez.simpleapi.sql.mysql.config.MysqlConfig;

/** Real backend/SimpleAPI construction with mocked JDBC; not a live PostgreSQL test. */
class MysqlUserBackendUuidMigrationTest {
    private static final UUID UUID_VALUE = UUID.fromString("542b75a0-5333-4f44-828c-94676443cf5d");
    private static final String TABLE = "prefix-User \"Data\"";
    private static final String QUOTED_TABLE = "\"prefix-User \"\"Data\"\"\"";
    private static final String ALTER_UUID = "ALTER TABLE " + QUOTED_TABLE
            + " ALTER COLUMN \"uuid\" TYPE UUID USING NULLIF(\"uuid\", '')::uuid;";
    private static final String ALTER_MYSQL_UUID = "ALTER TABLE `prefix-User \"Data\"` MODIFY `uuid` VARCHAR(37);";

    @Test
    void migratesNarrowMysqlFamilyUuidSynchronouslyBeforeUserAccess() throws Exception {
        for (DbType dbType : List.of(DbType.MYSQL, DbType.MARIADB)) {
            JdbcFixture jdbc = new JdbcFixture(dbType, "varchar", 16L);
            try (MockedConstruction<ConnectionManager> managers = jdbc.managers()) {
                try (MysqlUserBackend backend = openBackend(dbType)) {
                    assertTrue(backend.isOpen());
                    assertEquals(37L, jdbc.uuidLength);
                    assertSame(Thread.currentThread(), jdbc.migrationThread);
                    assertEquals(1, jdbc.sql.stream().filter(ALTER_MYSQL_UUID::equals).count());
                    assertTrue(backend.user(UUID_VALUE).contains(UserStorage.MYSQL));
                    verify(jdbc.lookup).setString(1, UUID_VALUE.toString());
                    assertEquals(List.of("migration-complete", "text-uuid-lookup"), jdbc.events);
                }
                assertEquals(1, managers.constructed().size());
                verify(managers.constructed().get(0)).close();
                jdbc.verifyClosed();
            }
        }
    }

    @Test
    void migratesLegacyVarcharUuidSynchronouslyBeforeUserAccess() throws Exception {
        JdbcFixture jdbc = new JdbcFixture("character varying");
        try (MockedConstruction<ConnectionManager> managers = jdbc.managers()) {
            try (MysqlUserBackend backend = openBackend()) {
                assertTrue(backend.isOpen());
                assertEquals("uuid", jdbc.uuidType);
                assertSame(Thread.currentThread(), jdbc.migrationThread);
                assertEquals(1, jdbc.sql.stream().filter(ALTER_UUID::equals).count());
                assertTrue(backend.user(UUID_VALUE).contains(UserStorage.MYSQL));
                verify(jdbc.lookup).setObject(1, UUID_VALUE);
                assertEquals(List.of("migration-complete", "native-uuid-lookup"), jdbc.events);
            }
            assertEquals(1, managers.constructed().size());
            verify(managers.constructed().get(0)).close();
            jdbc.verifyClosed();
        }
    }

    @Test
    void alreadyNativeUuidDoesNotRunAnotherAlter() throws Exception {
        JdbcFixture jdbc = new JdbcFixture("uuid");
        try (MockedConstruction<ConnectionManager> managers = jdbc.managers()) {
            try (MysqlUserBackend backend = openBackend()) {
                assertTrue(backend.user(UUID_VALUE).contains(UserStorage.MYSQL));
                assertEquals(0, jdbc.sql.stream().filter(ALTER_UUID::equals).count());
                assertEquals(List.of("native-uuid-lookup"), jdbc.events);
            }
            verify(managers.constructed().get(0)).close();
            jdbc.verifyClosed();
        }
    }

    @Test
    void conversionFailureRejectsConstructionAndClosesOwnedPool() throws Exception {
        JdbcFixture jdbc = new JdbcFixture("character varying");
        jdbc.migrationFailure = new SQLException("invalid UUID in existing row");
        try (MockedConstruction<ConnectionManager> managers = jdbc.managers()) {
            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    MysqlUserBackendUuidMigrationTest::openBackend);
            assertSame(jdbc.migrationFailure, failure.getCause());
            assertEquals("character varying", jdbc.uuidType);
            assertEquals(List.of(), jdbc.events);
            verify(managers.constructed().get(0)).close();
            jdbc.verifyClosed();
        }
    }

    @Test
    void inspectionFailureDoesNotExposeUnknownUuidTypeOrLeakPool() throws Exception {
        JdbcFixture jdbc = new JdbcFixture("character varying");
        jdbc.inspectionFailure = new SQLException("metadata unavailable");
        try (MockedConstruction<ConnectionManager> managers = jdbc.managers()) {
            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    MysqlUserBackendUuidMigrationTest::openBackend);
            assertSame(jdbc.inspectionFailure, failure.getCause());
            assertEquals(0, jdbc.sql.stream().filter(ALTER_UUID::equals).count());
            verify(managers.constructed().get(0)).close();
            jdbc.verifyClosed();
        }
    }

    private static MysqlUserBackend openBackend() {
        return openBackend(DbType.POSTGRESQL);
    }

    private static MysqlUserBackend openBackend(DbType dbType) {
        MysqlConfig config = new MysqlConfig();
        config.setDbType(dbType);
        config.setDatabase("test_database");
        config.setMaxThreads(2);
        config.setTablePrefix("prefix-");
        config.setTableName("User \"Data\"");
        return new MysqlUserBackend("ignored", config, SqlUserSchema.builder().build(), SqlBackendLogger.NO_OP);
    }

    private static final class JdbcFixture {
        final List<Connection> connections = new ArrayList<>();
        final List<PreparedStatement> statements = new ArrayList<>();
        final List<ResultSet> results = new ArrayList<>();
        final List<String> sql = new ArrayList<>();
        final List<String> events = new ArrayList<>();
        String uuidType;
        Long uuidLength;
        final DbType dbType;
        Thread migrationThread;
        SQLException migrationFailure;
        SQLException inspectionFailure;
        PreparedStatement lookup;

        JdbcFixture(String uuidType) {
            this(DbType.POSTGRESQL, uuidType, 37L);
        }

        JdbcFixture(DbType dbType, String uuidType, Long uuidLength) {
            this.dbType = dbType;
            this.uuidType = uuidType;
            this.uuidLength = uuidLength;
        }

        MockedConstruction<ConnectionManager> managers() {
            return mockConstruction(ConnectionManager.class, (manager, context) -> {
                when(manager.open()).thenReturn(true);
                when(manager.getDbType()).thenReturn(dbType);
                when(manager.getConnection()).thenAnswer(ignored -> connection());
            });
        }

        Connection connection() throws SQLException {
            Connection connection = mock(Connection.class);
            connections.add(connection);
            when(connection.prepareStatement(anyString())).thenAnswer(invocation -> {
                String query = invocation.getArgument(0, String.class);
                sql.add(query);
                PreparedStatement statement = mock(PreparedStatement.class);
                statements.add(statement);
                when(statement.executeUpdate()).thenAnswer(ignored -> {
                    if (ALTER_UUID.equals(query) || ALTER_MYSQL_UUID.equals(query)) {
                        migrationThread = Thread.currentThread();
                        if (migrationFailure != null) throw migrationFailure;
                        uuidType = dbType == DbType.POSTGRESQL ? "uuid" : "varchar";
                        uuidLength = dbType == DbType.POSTGRESQL ? null : 37L;
                        events.add("migration-complete");
                    }
                    // CREATE IF NOT EXISTS must not replace the legacy column type.
                    return 0;
                });
                when(statement.executeQuery()).thenAnswer(ignored -> {
                    if (query.toLowerCase(Locale.ROOT).startsWith("select data_type,")) {
                        if (inspectionFailure != null) throw inspectionFailure;
                        verify(statement).setString(1, TABLE);
                        verify(statement).setString(2, "uuid");
                        ResultSet result = row(uuidType);
                        when(result.getObject(2)).thenReturn(uuidLength);
                        when(result.getString("DATA_TYPE")).thenReturn(uuidType);
                        when(result.getObject("CHARACTER_MAXIMUM_LENGTH")).thenReturn(uuidLength);
                        when(result.getString("COLUMN_DEFAULT")).thenReturn(null);
                        return result;
                    }
                    if (query.startsWith("SELECT column_name FROM information_schema.columns")) {
                        return row("uuid");
                    }
                    if (query.startsWith("SELECT 1 FROM")) {
                        if (dbType == DbType.POSTGRESQL && !"uuid".equals(uuidType)) {
                            throw new SQLException("varchar = uuid is invalid");
                        }
                        lookup = statement;
                        events.add(dbType == DbType.POSTGRESQL ? "native-uuid-lookup" : "text-uuid-lookup");
                        return row("1");
                    }
                    return row(UUID_VALUE.toString());
                });
                return statement;
            });
            return connection;
        }

        ResultSet row(String value) throws SQLException {
            ResultSet result = mock(ResultSet.class);
            results.add(result);
            when(result.next()).thenReturn(true, false);
            when(result.getString(1)).thenReturn(value);
            return result;
        }

        void verifyClosed() throws SQLException {
            for (Connection connection : connections) verify(connection).close();
            for (PreparedStatement statement : statements) verify(statement, atLeastOnce()).close();
            for (ResultSet result : results) verify(result).close();
        }
    }
}
