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
import java.sql.ResultSetMetaData;
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

class JdbcSqlUserStorageDialectTest {
    private static final UUID UUID_VALUE = UUID.fromString("542b75a0-5333-4f44-828c-94676443cf5d");

    @Test void rowReadsReturnRegisteredColumnCasing() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet result = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(result);
        when(result.next()).thenReturn(true);
        when(result.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(1);
        when(metadata.getColumnLabel(1)).thenReturn("points");
        when(result.getInt(1)).thenReturn(7);
        SqlUserSchema schema = SqlUserSchema.builder().column("Points", "INT", DataType.INTEGER).build();

        List<com.bencodez.simpleapi.sql.Column> columns = new JdbcSqlUserStorage(UserStorage.MYSQL, UUID_VALUE,
                "Users", schema, () -> connection, JdbcSqlUserStorage.Dialect.MYSQL, SqlBackendLogger.NO_OP)
                .readRow(UserStorage.MYSQL);

        assertEquals("Points", columns.get(0).getName());
        assertEquals(7, columns.get(0).getValue().getInt());
    }

    @Test void postgresqlNewRowUsesTheAtomicInsertWithoutARedundantUpdate() throws Exception {
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
                "SELECT 1 FROM \"User \"\"Data\"\"\" WHERE \"uuid\"=? LIMIT 1 FOR UPDATE",
                "INSERT INTO \"User \"\"Data\"\"\" (\"uuid\", \"Vote \"\"Flag\"\"\") VALUES (?, ?) ON CONFLICT (\"uuid\") DO NOTHING"), jdbc.sql);
        for (int i = 0; i < 5; i++) {
            verify(jdbc.statements.get(i)).setObject(1, UUID_VALUE);
            verify(jdbc.statements.get(i), never()).setString(1, UUID_VALUE.toString());
        }
        verify(jdbc.statements.get(4)).setString(2, "true");
        verify(jdbc.connection).commit();
        verify(jdbc.connection, times(4)).close();
        for (PreparedStatement statement : jdbc.statements) verify(statement).close();
    }

    @Test void mysqlAndMariaDbNewRowsUseConstraintSafeInsertWithoutARedundantUpdate() throws Exception {
        for (DbType type : List.of(DbType.MYSQL, DbType.MARIADB)) {
            RecordingJdbc jdbc = new RecordingJdbc();
            SqlUserSchema schema = SqlUserSchema.builder().column("Vote `Flag`", "VARCHAR(5)", DataType.BOOLEAN).build();
            SqlUserStorage user = new JdbcSqlUserStorage(UserStorage.MYSQL, UUID_VALUE, "User `Data`", schema,
                    () -> jdbc.connection, JdbcSqlUserStorage.Dialect.fromDbType(type), SqlBackendLogger.NO_OP);
            user.write(UserStorage.MYSQL, "Vote `Flag`", new DataValueBoolean(false));
            assertEquals(List.of(
                    "SELECT 1 FROM `User ``Data``` WHERE `uuid`=? LIMIT 1 FOR UPDATE",
                    "INSERT INTO `User ``Data``` (`uuid`, `Vote ``Flag```) VALUES (?, ?)"), jdbc.sql);
            verify(jdbc.statements.get(0)).setString(1, UUID_VALUE.toString());
            verify(jdbc.statements.get(1)).setString(1, UUID_VALUE.toString());
            verify(jdbc.statements.get(1)).setString(2, "false");
            verify(jdbc.connection).commit();
            verify(jdbc.connection).close();
        }
    }

    @Test void booleanReadsUseARepresentationIndependentParser() throws Exception {
        Connection writeConnection = mock(Connection.class);
        when(writeConnection.getAutoCommit()).thenReturn(true);
        PreparedStatement exists = mock(PreparedStatement.class);
        PreparedStatement insert = mock(PreparedStatement.class);
        ResultSet missing = mock(ResultSet.class);
        when(exists.executeQuery()).thenReturn(missing);
        when(insert.executeUpdate()).thenReturn(1);
        when(writeConnection.prepareStatement(anyString())).thenAnswer(call -> call.getArgument(0, String.class).startsWith("SELECT 1") ? exists : insert);
        SqlUserSchema schema = SqlUserSchema.builder().column("Flag", "TINYINT(1)", DataType.BOOLEAN).build();
        SqlUserStorage writer = new JdbcSqlUserStorage(UserStorage.MYSQL, UUID_VALUE, "Users", schema,
                () -> writeConnection, JdbcSqlUserStorage.Dialect.MYSQL, SqlBackendLogger.NO_OP);
        writer.write(UserStorage.MYSQL, "Flag", new DataValueBoolean(true));
        verify(insert).setInt(2, 1);

        Connection readConnection = mock(Connection.class);
        PreparedStatement read = mock(PreparedStatement.class);
        ResultSet result = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        when(readConnection.prepareStatement(anyString())).thenReturn(read);
        when(read.executeQuery()).thenReturn(result);
        when(result.next()).thenReturn(true);
        when(result.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(1);
        when(metadata.getColumnLabel(1)).thenReturn("Flag");
        // An existing SQLite/PostgreSQL text column can outlive a newer numeric
        // declaration.  Decoding the returned representation keeps its true
        // value instead of asking the driver to coerce it to an integer.
        when(result.getString(1)).thenReturn("true");
        SqlUserStorage reader = new JdbcSqlUserStorage(UserStorage.MYSQL, UUID_VALUE, "Users", schema,
                () -> readConnection, JdbcSqlUserStorage.Dialect.MYSQL, SqlBackendLogger.NO_OP);
        assertTrue(reader.readRow(UserStorage.MYSQL).get(0).getValue().getBoolean());
        verify(result, never()).getInt(1);
        verify(result, never()).getBoolean(1);
    }

    @Test void backendUsesConnectionManagerDialectAndExistingUuidSchemaType() throws Exception {
        for (DbType type : List.of(DbType.POSTGRESQL, DbType.MYSQL, DbType.MARIADB)) {
            RecordingJdbc jdbc = new RecordingJdbc();
            MysqlConfig config = new MysqlConfig();
            config.setDbType(type); config.setDatabase("test_database"); config.setMaxThreads(1);
            config.setTablePrefix("prefix-"); config.setTableName("User Data");
            try (MockedConstruction<ConnectionManager> managers = mockConstruction(ConnectionManager.class,
                    (manager, context) -> {
                        when(manager.open()).thenReturn(true); when(manager.getDbType()).thenReturn(type);
                        when(manager.getConnection()).thenReturn(jdbc.connection);
                    })) {
                try (MysqlUserBackend backend = new MysqlUserBackend("ignored", config, SqlUserSchema.builder().build(), SqlBackendLogger.NO_OP)) {
                    backend.user(UUID_VALUE).contains(UserStorage.MYSQL);
                    PreparedStatement lookup = jdbc.statements.get(jdbc.statements.size() - 1);
                    if (type == DbType.POSTGRESQL) {
                        assertTrue(jdbc.sql.contains("CREATE TABLE IF NOT EXISTS \"prefix-User Data\" (\"uuid\" UUID, PRIMARY KEY (\"uuid\"));"));
                        assertEquals("SELECT 1 FROM \"prefix-User Data\" WHERE \"uuid\"=? LIMIT 1", jdbc.sql.get(jdbc.sql.size() - 1));
                        verify(lookup).setObject(1, UUID_VALUE);
                    } else {
                        assertTrue(jdbc.sql.contains("CREATE TABLE IF NOT EXISTS `prefix-User Data` (`uuid` VARCHAR(37), PRIMARY KEY (`uuid`));"));
                        verify(lookup).setString(1, UUID_VALUE.toString());
                    }
                }
                assertEquals(1, managers.constructed().size()); verify(managers.constructed().get(0)).close();
            }
        }
    }

    @Test void dialectRejectsInvalidIdentifiersWithoutRejectingQuotedNames() {
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
                PreparedStatement statement = mock(PreparedStatement.class); statements.add(statement);
                when(statement.executeQuery()).thenReturn(mock(ResultSet.class)); when(statement.executeUpdate()).thenReturn(1);
                return statement;
            });
        }
    }
}
