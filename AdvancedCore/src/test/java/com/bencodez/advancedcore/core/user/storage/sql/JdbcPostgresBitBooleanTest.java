package com.bencodez.advancedcore.core.user.storage.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.simpleapi.sql.DataType;
import com.bencodez.simpleapi.sql.data.DataValueBoolean;
import com.bencodez.simpleapi.sql.data.DataValueInt;

class JdbcPostgresBitBooleanTest {
    private static final UUID USER = UUID.fromString("51829cd0-c37c-45bf-9910-57914800e0a1");

    @Test
    void postgresIntegerBindingIsTargetTypedForLegacyTextColumns() throws Exception {
        Connection connection = mock(Connection.class);
        when(connection.getAutoCommit()).thenReturn(true);
        PreparedStatement exists = mock(PreparedStatement.class);
        PreparedStatement insert = mock(PreparedStatement.class);
        ResultSet missing = mock(ResultSet.class);
        when(exists.executeQuery()).thenReturn(missing);
        when(connection.prepareStatement(anyString())).thenReturn(exists, insert);
        when(insert.executeUpdate()).thenReturn(1);
        SqlUserSchema schema = SqlUserSchema.builder().column("Points", "INT DEFAULT '0'", DataType.INTEGER).build();
        JdbcSqlUserStorage storage = new JdbcSqlUserStorage(UserStorage.MYSQL, USER, "Users", schema,
                () -> connection, JdbcSqlUserStorage.Dialect.POSTGRESQL, SqlBackendLogger.NO_OP);

        storage.write(UserStorage.MYSQL, "Points", new DataValueInt(17));

        verify(insert).setObject(2, "17", Types.OTHER);
        verify(insert, never()).setInt(2, 17);
    }

    @Test
    void postgresBooleanBindingIsTargetTypedForRetainedLegacyTextColumns() throws Exception {
        Connection connection = mock(Connection.class);
        when(connection.getAutoCommit()).thenReturn(true);
        PreparedStatement exists = mock(PreparedStatement.class);
        PreparedStatement insert = mock(PreparedStatement.class);
        ResultSet missing = mock(ResultSet.class);
        when(exists.executeQuery()).thenReturn(missing);
        when(connection.prepareStatement(anyString())).thenReturn(exists, insert);
        when(insert.executeUpdate()).thenReturn(1);
        SqlUserSchema schema = SqlUserSchema.builder().column("Enabled", "BOOLEAN", DataType.BOOLEAN).build();
        JdbcSqlUserStorage storage = new JdbcSqlUserStorage(UserStorage.MYSQL, USER, "Users", schema,
                () -> connection, JdbcSqlUserStorage.Dialect.POSTGRESQL, SqlBackendLogger.NO_OP);

        storage.write(UserStorage.MYSQL, "Enabled", new DataValueBoolean(true));

        verify(insert).setObject(2, "true", Types.OTHER);
        verify(insert, never()).setBoolean(2, true);
    }

    @Test
    void postgresBitUsesExplicitBitCastAndStringBindingOnInsert() throws Exception {
        Connection connection = mock(Connection.class);
        when(connection.getAutoCommit()).thenReturn(true);
        List<String> sql = new ArrayList<>();
        List<PreparedStatement> statements = new ArrayList<>();
        when(connection.prepareStatement(anyString())).thenAnswer(call -> {
            String query = call.getArgument(0, String.class);
            sql.add(query);
            PreparedStatement statement = mock(PreparedStatement.class);
            statements.add(statement);
            when(statement.executeQuery()).thenReturn(mock(ResultSet.class));
            when(statement.executeUpdate()).thenReturn(1);
            return statement;
        });
        SqlUserSchema schema = SqlUserSchema.builder().column("Flag", "BIT(1)", DataType.BOOLEAN).build();
        JdbcSqlUserStorage storage = new JdbcSqlUserStorage(UserStorage.MYSQL, USER, "Users", schema,
                () -> connection, JdbcSqlUserStorage.Dialect.POSTGRESQL, SqlBackendLogger.NO_OP);

        storage.write(UserStorage.MYSQL, "Flag", new DataValueBoolean(true));

        assertEquals(List.of(
                "SELECT 1 FROM \"Users\" WHERE \"uuid\"=? LIMIT 1 FOR UPDATE",
                "INSERT INTO \"Users\" (\"uuid\", \"Flag\") VALUES (?, CAST(? AS BIT(1))) ON CONFLICT (\"uuid\") DO NOTHING"), sql);
        verify(statements.get(1)).setString(2, "1");
        verify(statements.get(1), never()).setInt(2, 1);
    }

    @Test
    void postgresBitReadsOneAsTrue() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet result = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(result);
        when(result.next()).thenReturn(true);
        when(result.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(1);
        when(metadata.getColumnLabel(1)).thenReturn("Flag");
        when(result.getString(1)).thenReturn("1");
        SqlUserSchema schema = SqlUserSchema.builder().column("Flag", "BIT(1)", DataType.BOOLEAN).build();
        JdbcSqlUserStorage storage = new JdbcSqlUserStorage(UserStorage.MYSQL, USER, "Users", schema,
                () -> connection, JdbcSqlUserStorage.Dialect.POSTGRESQL, SqlBackendLogger.NO_OP);

        assertTrue(storage.readRow(UserStorage.MYSQL).get(0).getValue().getBoolean());
        verify(result, never()).getInt(1);
        verify(result, never()).getBoolean(1);
    }

    @Test
    void postgresFixedWidthBitReadsAnyNonzeroValueAsTrue() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet result = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(result);
        when(result.next()).thenReturn(true);
        when(result.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(1);
        when(metadata.getColumnLabel(1)).thenReturn("Flag");
        when(result.getString(1)).thenReturn("10");
        SqlUserSchema schema = SqlUserSchema.builder().column("Flag", "BIT(2)", DataType.BOOLEAN).build();
        JdbcSqlUserStorage storage = new JdbcSqlUserStorage(UserStorage.MYSQL, USER, "Users", schema,
                () -> connection, JdbcSqlUserStorage.Dialect.POSTGRESQL, SqlBackendLogger.NO_OP);

        assertTrue(storage.readRow(UserStorage.MYSQL).get(0).getValue().getBoolean());
    }
}
