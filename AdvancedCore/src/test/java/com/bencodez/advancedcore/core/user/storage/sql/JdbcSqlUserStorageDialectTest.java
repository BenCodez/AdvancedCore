package com.bencodez.advancedcore.core.user.storage.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.advancedcore.core.user.storage.SqlUserStorage;
import com.bencodez.simpleapi.sql.DataType;
import com.bencodez.simpleapi.sql.data.DataValueBoolean;
import com.bencodez.simpleapi.sql.mysql.ConnectionManager;
import com.bencodez.simpleapi.sql.mysql.DbType;
import com.bencodez.simpleapi.sql.mysql.config.MysqlConfig;

/** JDBC contract tests, not a live PostgreSQL/MySQL integration database. */
class JdbcSqlUserStorageDialectTest {
    private static final UUID UUID_VALUE = UUID.fromString("542b75a0-5333-4f44-828c-94676443cf5d");

    @Test
    void postgresqlQuotesIdentifiersAndBindsNativeUuidForEveryCrudOperation() throws Exception {
        RecordingJdbc jdbc = new RecordingJdbc();
        SqlUserSchema schema = SqlUserSchema.builder().column("Vote \"Flag\"", "VARCHAR(5)", DataType.BOOLEAN).build();
        SqlUserStorage user = new JdbcSqlUserStorage(UserStorage.MYSQL, UUID_VALUE, "User \"Data\"", schema,
                () -> jdbc.connection, JdbcSqlUserStorage.Dialect.POSTGRESQL, SqlBackendLogger.NO_OP);
        assertTrue(user.readRow(UserStorage.MYSQL).isEmpty());
        assertFalse(user.contains(UserStorage.MYSQL));
        user.delete(UserStorage.MYSQL);
        user.write(UserStorage.MYSQL, "vote \"flag\"", new DataValueBoolean(true));

        assertEquals(List.of(
                "SELECT * FROM \"User \"\"Data\"\"\" WHERE \"uuid\"=?",
                "SELECT 1 FROM \"User \"\"Data\"\"\" WHERE \"uuid\"=? LIMIT 1",
                "DELETE FROM \"User \"\"Data\"\"\" WHERE \"uuid\"=?",
                "SELECT 1 FROM \"User \"\"Data\"\"\" WHERE \"uuid\"=? LIMIT 1",
                "INSERT INTO \"User \"\"Data\"\"\" (\"uuid\", \"Vote \"\"Flag\"\"\") VALUES (?, ?) ON CONFLICT (\"uuid\") DO NOTHING",
                "UPDATE \"User \"\"Data\"\"\" SET \"Vote \"\"Flag\"\"\"=? WHERE \"uuid\"=?"), jdbc.sql);
        for (int i = 0; i < 5; i++) {
            verify(jdbc.statements.get(i)).setObject(1, UUID_VALUE);
            verify(jdbc.statements.get(i), never()).setString(1, UUID_VALUE.toString());
        }
        verify(jdbc.statements.get(4)).setString(2, "true");
        verify(jdbc.statements.get(5)).setString(1, "true");
        verify(jdbc.statements.get(5)).setObject(2, UUID_VALUE);
        verify(jdbc.connection).commit();
        verify(jdbc.connection, times(4)).close();
        for (PreparedStatement statement : jdbc.statements) verify(statement).close();
    }

    @Test
    void mysqlAndMariaDbRetainInsertIgnoreAndTextUuidBindings() throws Exception {
        for (DbType type : List.of(DbType.MYSQL, DbType.MARIADB)) {
            RecordingJdbc jdbc = new RecordingJdbc();
            SqlUserSchema schema = SqlUserSchema.builder().column("Vote `Flag`", "VARCHAR(5)", DataType.BOOLEAN).build();
            SqlUserStorage user = new JdbcSqlUserStorage(UserStorage.MYSQL, UUID_VALUE, "User `Data`", schema,
                    () -> jdbc.connection, JdbcSqlUserStorage.Dialect.fromDbType(type), SqlBackendLogger.NO_OP);
            user.write(UserStorage.MYSQL, "Vote `Flag`", new DataValueBoolean(false));
            assertEquals(List.of(
                    "INSERT IGNORE INTO `User ``Data``` (`uuid`, `Vote ``Flag```) VALUES (?, ?)",
                    "UPDATE `User ``Data``` SET `Vote ``Flag```=? WHERE `uuid`=?"), jdbc.sql);
            verify(jdbc.statements.get(0)).setString(1, UUID_VALUE.toString());
            verify(jdbc.statements.get(0)).setString(2, "false");
            verify(jdbc.statements.get(1)).setString(1, "false");
            verify(jdbc.statements.get(1)).setString(2, UUID_VALUE.toString());
            verify(jdbc.connection).commit();
            verify(jdbc.connection).close();
        }
    }

    @Test
    void backendUsesConnectionManagerDialectAndExistingUuidSchemaType() throws Exception {
        for (DbType type : List.of(DbType.POSTGRESQL, DbType.MYSQL, DbType.MARIADB)) {
            RecordingJdbc jdbc = new RecordingJdbc();
            MysqlConfig config = new MysqlConfig();
            config.setDbType(type);
            config.setDatabase("test_database");
            config.setMaxThreads(1);
            config.setTablePrefix("prefix-");
            config.setTableName("User Data");
            try (MockedConstruction<ConnectionManager> managers = mockConstruction(ConnectionManager.class,
                    (manager, context) -> {
                        when(manager.open()).thenReturn(true);
                        when(manager.getDbType()).thenReturn(type);
                        when(manager.getConnection()).thenReturn(jdbc.connection);
                    })) {
                try (MysqlUserBackend backend = new MysqlUserBackend("ignored", config,
                        SqlUserSchema.builder().build(), SqlBackendLogger.NO_OP)) {
                    backend.user(UUID_VALUE).contains(UserStorage.MYSQL);
                    PreparedStatement lookup = jdbc.statements.get(jdbc.statements.size() - 1);
                    if (type == DbType.POSTGRESQL) {
                        assertTrue(jdbc.sql.contains("CREATE TABLE IF NOT EXISTS \"prefix-User Data\" (\"uuid\" UUID, PRIMARY KEY (\"uuid\"));"));
                        assertEquals("SELECT 1 FROM \"prefix-User Data\" WHERE \"uuid\"=? LIMIT 1",
                                jdbc.sql.get(jdbc.sql.size() - 1));
                        verify(lookup).setObject(1, UUID_VALUE);
                    } else {
                        assertTrue(jdbc.sql.contains("CREATE TABLE IF NOT EXISTS `prefix-User Data` (`uuid` VARCHAR(37), PRIMARY KEY (`uuid`));"));
                        verify(lookup).setString(1, UUID_VALUE.toString());
                    }
                }
                assertEquals(1, managers.constructed().size());
                verify(managers.constructed().get(0)).close();
            }
        }
    }

    @Test
    void dialectRejectsInvalidIdentifiersWithoutRejectingQuotedNames() {
        for (JdbcSqlUserStorage.Dialect dialect : JdbcSqlUserStorage.Dialect.values()) {
            assertThrows(IllegalArgumentException.class, () -> dialect.quote(null));
            assertThrows(IllegalArgumentException.class, () -> dialect.quote(""));
            assertThrows(IllegalArgumentException.class, () -> dialect.quote("   "));
            assertThrows(IllegalArgumentException.class, () -> dialect.quote("invalid\0name"));
            assertTrue(dialect.quote("custom-name with space").contains("custom-name with space"));
        }
    }

    private static final class RecordingJdbc {
        final Connection connection = mock(Connection.class);
        final List<String> sql = new ArrayList<>();
        final List<PreparedStatement> statements = new ArrayList<>();

        RecordingJdbc() throws SQLException {
            when(connection.getAutoCommit()).thenReturn(true);
            when(connection.prepareStatement(anyString())).thenAnswer(invocation -> {
                sql.add(invocation.getArgument(0, String.class));
                PreparedStatement statement = mock(PreparedStatement.class);
                statements.add(statement);
                when(statement.executeQuery()).thenReturn(mock(ResultSet.class));
                when(statement.executeUpdate()).thenReturn(1);
                return statement;
            });
        }
    }
}
