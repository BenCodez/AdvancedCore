package com.bencodez.advancedcore.tests.user;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.advancedcore.api.user.UserData;
import com.bencodez.advancedcore.api.user.UserDataFetchMode;
import com.bencodez.advancedcore.api.user.UserManager;
import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.advancedcore.api.user.userstorage.mysql.MySQL;
import com.bencodez.advancedcore.api.user.userstorage.sql.UserTable;
import com.bencodez.simpleapi.sql.Column;
import com.bencodez.simpleapi.sql.data.DataValue;
import com.bencodez.simpleapi.sql.data.DataValueInt;
import com.bencodez.simpleapi.sql.data.DataValueString;

class SqlOnlyUserDataTest {
    @Test
    void bothSqlBackendsRetainReadAndExistenceBehavior() {
        for (UserStorage storage : UserStorage.values()) {
            Fixture fixture = new Fixture(storage);
            ArrayList<Column> row = new ArrayList<>(List.of(new Column("Points", new DataValueInt(12)),
                    new Column("PlayerName", new DataValueString("Ben"))));
            when(fixture.mysql.getExact(fixture.id)).thenReturn(row);
            when(fixture.sqlite.getExact(any(Column.class))).thenReturn(row);
            when(fixture.mysql.containsKey(fixture.id)).thenReturn(true);
            when(fixture.sqlite.containsKey(fixture.id)).thenReturn(true);
            assertEquals(12, fixture.data.getInt("Points", 0, UserDataFetchMode.NO_CACHE));
            assertEquals("Ben", fixture.data.getString("PlayerName", UserDataFetchMode.NO_CACHE));
            assertEquals(List.of("Points", "PlayerName"), fixture.data.getKeys());
            assertEquals(2, fixture.data.getValues().size());
            assertTrue(fixture.data.hasData());
            verify(fixture.plugin, never()).getDataFolder();
        }
    }

    @Test
    void scalarWritesKeepUuidAndSqlValueTypes() {
        Fixture sqlite = new Fixture(UserStorage.SQLITE);
        sqlite.data.setInt("Points", 24, false);
        sqlite.data.setString("PlayerName", "Ben", false);
        ArgumentCaptor<Column> key = ArgumentCaptor.forClass(Column.class);
        @SuppressWarnings({ "rawtypes", "unchecked" })
        ArgumentCaptor<ArrayList<Column>> rows = ArgumentCaptor.forClass((Class) ArrayList.class);
        verify(sqlite.sqlite, times(2)).update(key.capture(), rows.capture());
        assertEquals(sqlite.id, key.getValue().getValue().getString());
        assertEquals("uuid", rows.getAllValues().get(0).get(0).getName());
        assertEquals(24, rows.getAllValues().get(0).get(1).getValue().getInt());
        assertEquals("Ben", rows.getAllValues().get(1).get(1).getValue().getString());
        verify(sqlite.manager).onChange(sqlite.user, "Points");
        verify(sqlite.manager).onChange(sqlite.user, "PlayerName");

        Fixture mysql = new Fixture(UserStorage.MYSQL);
        mysql.data.setInt("Points", 24, false);
        mysql.data.setString("PlayerName", "Ben", false);
        ArgumentCaptor<DataValue> values = ArgumentCaptor.forClass(DataValue.class);
        verify(mysql.mysql).update(eq(mysql.id), eq("Points"), values.capture());
        assertEquals(24, values.getValue().getInt());
        verify(mysql.mysql).update(eq(mysql.id), eq("PlayerName"), values.capture());
        assertEquals("Ben", values.getValue().getString());
        verify(mysql.plugin, never()).getDataFolder();
        verify(sqlite.plugin, never()).getDataFolder();
    }

    @Test
    void removalUsesSelectedSqlTableThenClearsExistingUserCache() {
        for (UserStorage storage : UserStorage.values()) {
            Fixture fixture = new Fixture(storage);
            fixture.data.remove();
            if (storage == UserStorage.MYSQL) {
                var order = inOrder(fixture.mysql, fixture.user);
                order.verify(fixture.mysql).deletePlayer(fixture.id);
                order.verify(fixture.user).clearCache();
            } else {
                ArgumentCaptor<Column> key = ArgumentCaptor.forClass(Column.class);
                var order = inOrder(fixture.sqlite, fixture.user);
                order.verify(fixture.sqlite).delete(key.capture());
                order.verify(fixture.user).clearCache();
                assertEquals("uuid", key.getValue().getName());
                assertEquals(fixture.id, key.getValue().getValue().getString());
            }
            verify(fixture.plugin, never()).getDataFolder();
        }
    }

    private static final class Fixture {
        final String id = UUID.randomUUID().toString();
        final AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        final AdvancedCoreUser user = mock(AdvancedCoreUser.class);
        final MySQL mysql = mock(MySQL.class);
        final UserTable sqlite = mock(UserTable.class);
        final UserManager manager = mock(UserManager.class);
        final UserData data = new UserData(user);

        Fixture(UserStorage storage) {
            when(user.getPlugin()).thenReturn(plugin);
            when(user.getUUID()).thenReturn(id);
            when(user.getPlayerName()).thenReturn("Ben");
            when(plugin.getStorageType()).thenReturn(storage);
            when(plugin.getMysql()).thenReturn(mysql);
            when(plugin.getSQLiteUserTable()).thenReturn(sqlite);
            when(plugin.getUserManager()).thenReturn(manager);
        }
    }
}
