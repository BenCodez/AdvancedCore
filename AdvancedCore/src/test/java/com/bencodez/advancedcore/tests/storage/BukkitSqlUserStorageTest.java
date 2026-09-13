package com.bencodez.advancedcore.tests.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL;
import com.bencodez.advancedcore.api.user.userstorage.sql.UserTable;
import com.bencodez.advancedcore.bukkit.user.storage.BukkitSqlUserStorage;
import com.bencodez.simpleapi.sql.Column;
import com.bencodez.simpleapi.sql.data.DataValue;
import com.bencodez.simpleapi.sql.data.DataValueInt;
import com.bencodez.simpleapi.sql.data.DataValueString;

class BukkitSqlUserStorageTest {
    @Test
    void constructionDoesNotResolveOwnersAndReadUsesCurrentTableAndUuid() {
        BukkitSqlUserStorage uninitialized = new BukkitSqlUserStorage(
                () -> { throw new AssertionError("eager plugin"); },
                () -> { throw new AssertionError("eager UUID"); });
        assertNotNull(uninitialized);
        AdvancedCorePlugin first = mock(AdvancedCorePlugin.class), second = mock(AdvancedCorePlugin.class);
        MySQL firstTable = mock(MySQL.class), secondTable = mock(MySQL.class);
        when(first.getMysql()).thenReturn(firstTable);
        when(second.getMysql()).thenReturn(secondTable);
        AtomicReference<AdvancedCorePlugin> owner = new AtomicReference<>(first);
        AtomicReference<String> uuid = new AtomicReference<>("first-id");
        BukkitSqlUserStorage storage = new BukkitSqlUserStorage(owner::get, uuid::get);
        ArrayList<Column> firstRow = new ArrayList<>(), secondRow = new ArrayList<>();
        when(firstTable.getExact("first-id")).thenReturn(firstRow);
        when(secondTable.getExact("second-id")).thenReturn(secondRow);
        assertSame(firstRow, storage.readRow(UserStorage.MYSQL));
        owner.set(second);
        uuid.set("second-id");
        assertSame(secondRow, storage.readRow(UserStorage.MYSQL));
        storage.write(UserStorage.MYSQL, "Points", new DataValueInt(1));
        verify(secondTable).update(eq("second-id"), eq("Points"), any(DataValue.class));
        verify(first, never()).getDataFolder();
        verify(second, never()).getDataFolder();
    }

    @Test
    void sqliteBulkUsesOneUpdateWithAllNonUuidColumns() {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        UserTable table = mock(UserTable.class);
        when(plugin.getSQLiteUserTable()).thenReturn(table);
        BukkitSqlUserStorage storage = new BukkitSqlUserStorage(() -> plugin, () -> "target-id");
        List<List<String>> writes = new ArrayList<>();
        doAnswer(call -> {
            Column primary = call.getArgument(0);
            assertEquals("uuid", primary.getName());
            assertEquals("target-id", primary.getValue().getString());
            ArrayList<Column> columns = call.getArgument(1);
            writes.add(columns.stream().map(Column::getName).toList());
            return null;
        }).when(table).update(any(Column.class), any());
        HashMap<String, DataValue> values = new LinkedHashMap<>();
        values.put("Points", new DataValueInt(1));
        values.put("uuid", new DataValueString("ignored-id"));
        values.put("PlayerName", new DataValueString("Ben"));
        storage.writeValues(UserStorage.SQLITE, values);
        assertEquals(List.of(List.of("Points", "PlayerName")), writes);
        storage.writeValues(UserStorage.SQLITE, new HashMap<>());
        assertEquals(1, writes.size());
    }

    @Test
    void mysqlBulkUsesExistingNonQueuedUpdateAndMissingProviderNoOp() {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        MySQL table = mock(MySQL.class);
        when(plugin.getMysql()).thenReturn(table);
        BukkitSqlUserStorage storage = new BukkitSqlUserStorage(() -> plugin, () -> "target-id");
        HashMap<String, DataValue> values = new LinkedHashMap<>();
        DataValue points = new DataValueInt(1);
        values.put("uuid", new DataValueString("ignored-id"));
        values.put("Points", points);
        storage.writeValues(UserStorage.MYSQL, values);
        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<ArrayList<Column>> columns = ArgumentCaptor.forClass((Class) ArrayList.class);
        verify(table).update(eq("target-id"), columns.capture(), eq(false));
        assertEquals(1, columns.getValue().size());
        assertEquals("Points", columns.getValue().get(0).getName());
        assertSame(points, columns.getValue().get(0).getValue());
        when(plugin.getMysql()).thenReturn(null);
        assertDoesNotThrow(() -> storage.writeValues(UserStorage.MYSQL, values));
        verifyNoMoreInteractions(table);
    }
}
