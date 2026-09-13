package com.bencodez.advancedcore.core.user.storage.sql;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLDataException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.simpleapi.sql.DataType;
import com.bencodez.simpleapi.sql.data.DataValueInt;

/** SQL clause, ordering and failure contracts against mocked JDBC. */
class JdbcReviewRegressionTest {
    private static final UUID USER = UUID.fromString("542b75a0-5333-4f44-828c-94676443cf5d");

    @Test void locksAnExistingPostgresRowUntilAfterTheUpdate() throws Exception {
        Connection connection = mock(Connection.class);
        when(connection.getAutoCommit()).thenReturn(true);
        PreparedStatement lock = mock(PreparedStatement.class);
        PreparedStatement update = mock(PreparedStatement.class);
        ResultSet found = mock(ResultSet.class);
        when(found.next()).thenReturn(true);
        when(lock.executeQuery()).thenReturn(found);
        when(update.executeUpdate()).thenReturn(1);
        when(connection.prepareStatement("SELECT 1 FROM \"Users\" WHERE \"uuid\"=? LIMIT 1 FOR UPDATE"))
                .thenReturn(lock);
        when(connection.prepareStatement("UPDATE \"Users\" SET \"Points\"=? WHERE \"uuid\"=?"))
                .thenReturn(update);
        storage(connection).write(UserStorage.MYSQL, "Points", new DataValueInt(42));
        verify(lock).setObject(1, USER);
        verify(update).setObject(2, USER);
        InOrder order = inOrder(connection, lock, update);
        order.verify(connection).setAutoCommit(false);
        order.verify(lock).executeQuery();
        order.verify(update).executeUpdate();
        order.verify(connection).commit();
        order.verify(connection).close();
        verify(found).close();
        verify(lock).close();
        verify(update).close();
    }

    @Test void locksTheRowWonByAConcurrentInsertToo() throws Exception {
        Connection connection = mock(Connection.class);
        List<String> sqls = new ArrayList<>();
        int[] reads = {0};
        when(connection.prepareStatement(anyString())).thenAnswer(call -> {
            String sql = call.getArgument(0, String.class);
            sqls.add(sql);
            PreparedStatement statement = mock(PreparedStatement.class);
            if (sql.startsWith("SELECT 1")) {
                assertTrue(sql.endsWith("FOR UPDATE"));
                ResultSet result = mock(ResultSet.class);
                when(result.next()).thenReturn(++reads[0] > 1);
                when(statement.executeQuery()).thenReturn(result);
            }
            // INSERT's zero update count models ON CONFLICT DO NOTHING.
            return statement;
        });
        storage(connection).write(UserStorage.MYSQL, "Points", new DataValueInt(42));
        assertEquals(2, reads[0]);
        assertEquals(4, sqls.size());
        assertTrue(sqls.get(1).startsWith("INSERT INTO"));
        assertTrue(sqls.get(2).endsWith("FOR UPDATE"));
        assertTrue(sqls.get(3).startsWith("UPDATE"));
        verify(connection).commit();
    }

    @Test void ordinaryContainsDoesNotTakeAWriteLock() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet result = mock(ResultSet.class);
        when(connection.prepareStatement("SELECT 1 FROM \"Users\" WHERE \"uuid\"=? LIMIT 1"))
                .thenReturn(statement);
        when(statement.executeQuery()).thenReturn(result);
        when(result.next()).thenReturn(true);
        assertTrue(storage(connection).contains(UserStorage.MYSQL));
        verify(connection, never()).setAutoCommit(false);
    }

    @Test void malformedLegacyIntegerTextFallsBackWithoutDiscardingTheRow() throws Exception {
        readInteger(new SQLException("bad legacy integer", "22P02"), 0);
    }

    @Test void driverDataConversionExceptionsWithoutAStateAlsoFallBack() throws Exception {
        readInteger(new SQLDataException("out of range"), 0);
    }

    @Test void validIntegersRetainTheirValue() throws Exception {
        readInteger(null, 42);
    }

    @Test void connectionFailureIsNotTreatedAsLegacyIntegerText() throws Exception {
        SQLException failure = new SQLException("connection lost", "08006");
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> readInteger(failure, 0)).getCause());
    }

    private void readInteger(SQLException failure, int expected) throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet result = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(result);
        when(result.next()).thenReturn(true, false);
        when(result.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(2);
        when(metadata.getColumnLabel(1)).thenReturn("Points");
        when(metadata.getColumnLabel(2)).thenReturn("Note");
        when(result.getString(2)).thenReturn("still readable");
        if (failure == null) when(result.getInt(1)).thenReturn(42);
        else when(result.getInt(1)).thenThrow(failure);
        var row = storage(connection).readRow(UserStorage.MYSQL);
        assertEquals(expected, row.get(0).getValue().getInt());
        assertEquals("still readable", row.get(1).getValue().getString());
        verify(result).close();
        verify(statement).close();
        verify(connection).close();
    }

    private JdbcSqlUserStorage storage(Connection connection) {
        return new JdbcSqlUserStorage(UserStorage.MYSQL, USER, "Users", SqlUserSchema.builder()
                .column("Points", "INT DEFAULT '0'", DataType.INTEGER).build(),
                () -> connection, JdbcSqlUserStorage.Dialect.POSTGRESQL, SqlBackendLogger.NO_OP);
    }
}
