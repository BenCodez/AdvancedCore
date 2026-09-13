package com.bencodez.advancedcore.core.user.storage.sql;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.sql.Connection;
import java.util.HashMap;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.simpleapi.sql.DataType;
import com.bencodez.simpleapi.sql.data.DataValue;
import com.bencodez.simpleapi.sql.data.DataValueInt;

class JdbcDuplicateCanonicalColumnTest {
    @Test
    void caseVariantAliasesCannotOverwriteOneCanonicalColumn() throws Exception {
        Connection connection = mock(Connection.class);
        JdbcSqlUserStorage storage = new JdbcSqlUserStorage(UserStorage.SQLITE, UUID.randomUUID(), "Users",
                SqlUserSchema.builder().column("Points", "INTEGER", DataType.INTEGER).build(),
                () -> connection, JdbcSqlUserStorage.Dialect.SQLITE, SqlBackendLogger.NO_OP);
        HashMap<String, DataValue> values = new HashMap<>();
        values.put("Points", new DataValueInt(1));
        values.put("points", new DataValueInt(2));
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> storage.writeValues(UserStorage.SQLITE, values));
        assertTrue(failure.getMessage().contains("Points"));
        verify(connection, never()).getAutoCommit();
        verify(connection, never()).commit();
    }
}
