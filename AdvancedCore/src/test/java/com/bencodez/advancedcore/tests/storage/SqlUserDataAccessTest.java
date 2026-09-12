package com.bencodez.advancedcore.tests.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.advancedcore.core.user.storage.SqlUserDataAccess;
import com.bencodez.advancedcore.core.user.storage.SqlUserStorage;
import com.bencodez.simpleapi.sql.Column;
import com.bencodez.simpleapi.sql.data.DataValue;
import com.bencodez.simpleapi.sql.data.DataValueBoolean;
import com.bencodez.simpleapi.sql.data.DataValueInt;
import com.bencodez.simpleapi.sql.data.DataValueString;

class SqlUserDataAccessTest {
    @Test
    void scalarReadsRetainTypesDefaultsCaseAndFirstMatchingStringBehavior() {
        for (UserStorage backend : UserStorage.values()) {
            SqlUserStorage store = mock(SqlUserStorage.class);
            SqlUserDataAccess data = new SqlUserDataAccess(store);
            when(store.readRow(backend)).thenReturn(List.of(
                    new Column("int", new DataValueInt(12)),
                    new Column("number", new DataValueString("34")),
                    new Column("invalid", new DataValueString("bad")),
                    new Column("invalid", new DataValueInt(99)),
                    new Column("null", new DataValueString("NuLl")),
                    new Column("empty", new DataValueString(null)),
                    new Column("boolean", new DataValueBoolean(true))));
            assertEquals(12, data.getInt(backend, "int", -1));
            assertEquals(34, data.getInt(backend, "number", -1));
            assertEquals(-1, data.getInt(backend, "invalid", -1));
            assertEquals(-1, data.getInt(backend, "INT", -1));
            assertEquals("", data.getString(backend, "int"));
            assertEquals("34", data.getString(backend, "number"));
            assertEquals("", data.getString(backend, "null"));
            assertEquals("", data.getString(backend, "empty"));
            assertEquals("true", data.getString(backend, "boolean"));
        }
    }

    @Test
    void blankKeysDoNotReadAndNullRowsKeepDefaults() {
        SqlUserStorage store = mock(SqlUserStorage.class);
        SqlUserDataAccess data = new SqlUserDataAccess(store);
        assertEquals(7, data.getInt(UserStorage.SQLITE, null, 7));
        assertEquals(7, data.getInt(UserStorage.SQLITE, "", 7));
        assertEquals("", data.getString(UserStorage.SQLITE, null));
        assertEquals("", data.getString(UserStorage.SQLITE, ""));
        verifyNoInteractions(store);
        when(store.readRow(UserStorage.SQLITE)).thenReturn(null);
        assertEquals(7, data.getInt(UserStorage.SQLITE, "missing", 7));
        assertEquals("", data.getString(UserStorage.SQLITE, "missing"));
        assertTrue(data.getKeys(UserStorage.SQLITE).isEmpty());
        assertTrue(data.getValues(UserStorage.SQLITE).isEmpty());
    }

    @Test
    void rowsRemainLiveAndLegacyReaderCanOverrideTheBackingStore() {
        SqlUserStorage store = mock(SqlUserStorage.class);
        AtomicInteger reads = new AtomicInteger();
        AtomicReference<List<Column>> row = new AtomicReference<>(List.of(new Column("value", new DataValueInt(1))));
        SqlUserDataAccess data = new SqlUserDataAccess(store, backend -> {
            reads.incrementAndGet();
            return row.get();
        });
        assertEquals(0, reads.get());
        assertEquals(1, data.getInt(UserStorage.MYSQL, "value", 0));
        row.set(List.of(new Column("value", new DataValueInt(2))));
        assertEquals(2, data.getInt(UserStorage.MYSQL, "value", 0));
        assertEquals(2, reads.get());
        verifyNoInteractions(store);
    }

    @Test
    void mapAndKeyExportsPreserveValueIdentityDuplicatesAndMutability() {
        SqlUserStorage store = mock(SqlUserStorage.class);
        SqlUserDataAccess data = new SqlUserDataAccess(store);
        DataValue first = new DataValueInt(1), last = new DataValueInt(2);
        when(store.readRow(UserStorage.SQLITE)).thenReturn(List.of(new Column("value", first), new Column("value", last)));
        ArrayList<String> keys = data.getKeys(UserStorage.SQLITE);
        assertEquals(List.of("value", "value"), keys);
        keys.add("local");
        HashMap<String, DataValue> values = data.getValues(UserStorage.SQLITE);
        assertSame(last, values.get("value"));
        values.put("local", first);
        assertEquals(1, data.getValues(UserStorage.SQLITE).size());
        assertTrue(SqlUserDataAccess.convert(null).isEmpty());
    }

    @Test
    void writesExistenceDeletionAndExceptionsUseTheSuppliedStore() {
        SqlUserStorage store = mock(SqlUserStorage.class);
        SqlUserDataAccess data = new SqlUserDataAccess(store);
        data.setInt(UserStorage.MYSQL, "Points", 42);
        data.setString(UserStorage.SQLITE, "PlayerName", null);
        ArgumentCaptor<DataValue> value = ArgumentCaptor.forClass(DataValue.class);
        verify(store).write(eq(UserStorage.MYSQL), eq("Points"), value.capture());
        assertTrue(value.getValue().isInt());
        assertEquals(42, value.getValue().getInt());
        verify(store).write(eq(UserStorage.SQLITE), eq("PlayerName"), value.capture());
        assertTrue(value.getValue().isString());
        assertEquals("", value.getValue().getString());
        HashMap<String, DataValue> values = new HashMap<>();
        data.setValues(UserStorage.SQLITE, values);
        verify(store).writeValues(UserStorage.SQLITE, values);
        when(store.contains(UserStorage.MYSQL)).thenReturn(true);
        assertTrue(data.hasData(UserStorage.MYSQL));
        data.remove(UserStorage.MYSQL);
        verify(store).delete(UserStorage.MYSQL);
        IllegalStateException failure = new IllegalStateException("database unavailable");
        when(store.readRow(UserStorage.MYSQL)).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> data.getInt(UserStorage.MYSQL, "x", 0)));
        doThrow(failure).when(store).delete(UserStorage.SQLITE);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> data.remove(UserStorage.SQLITE)));
    }
}
