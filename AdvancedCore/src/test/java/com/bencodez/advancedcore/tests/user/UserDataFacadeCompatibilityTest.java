package com.bencodez.advancedcore.tests.user;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.advancedcore.api.user.UserData;
import com.bencodez.advancedcore.api.user.UserDataFetchMode;
import com.bencodez.advancedcore.api.user.UserStorage;
import com.bencodez.advancedcore.api.user.usercache.UserDataCache;
import com.bencodez.simpleapi.sql.Column;
import com.bencodez.simpleapi.sql.data.DataValue;
import com.bencodez.simpleapi.sql.data.DataValueInt;
import com.bencodez.simpleapi.sql.data.DataValueString;

class UserDataFacadeCompatibilityTest {
    @Test void temporaryOnlyReadsKeepTheOriginalMutableMap() {
        UserData data = new UserData(null);
        var map = new HashMap<String, DataValue>();
        data.setTempCache(map);
        assertSame(map, data.getTempCache());
        map.put("Points", new DataValueInt(7));
        assertEquals(7, data.getInt(UserStorage.MYSQL, "Points", -1, UserDataFetchMode.TEMP_ONLY));
        map.put("Points", new DataValueInt(8));
        assertEquals(8, data.getInt(UserStorage.MYSQL, "Points", -1, UserDataFetchMode.TEMP_ONLY));
        data.clearTempCache();
        assertTrue(map.isEmpty());
        assertNull(data.getTempCache());
        assertEquals(-1, data.getInt(UserStorage.MYSQL, "Points", -1, UserDataFetchMode.TEMP_ONLY));
    }

    @Test void readsDoNotIntroduceCallsToOverridableTemporaryGetter() {
        UserData data = new UserData(null) {
            @Override public HashMap<String, DataValue> getTempCache() {
                throw new AssertionError("Existing scalar reads used the field, not this getter");
            }
        };
        data.setTempCache(new HashMap<>(Map.of("Points", new DataValueInt(7), "Name", new DataValueString("player"))));
        assertEquals(7, data.getInt(UserStorage.MYSQL, "Points", -1, UserDataFetchMode.TEMP_ONLY));
        assertEquals("player", data.getString(UserStorage.MYSQL, "Name", UserDataFetchMode.TEMP_ONLY));
    }

    @Test void overriddenRowProvidersAreStillInvoked() {
        UserData data = new UserData(null) {
            @Override public List<Column> getMySqlRow() {
                return List.of(new Column("Points", new DataValueInt(7)), new Column("Name", new DataValueString("mysql")));
            }
            @Override public List<Column> getSQLiteRow() {
                return List.of(new Column("Points", new DataValueInt(8)), new Column("Name", new DataValueString("sqlite")));
            }
        };
        assertEquals(7, data.getInt(UserStorage.MYSQL, "Points", -1, UserDataFetchMode.NO_CACHE));
        assertEquals(8, data.getInt(UserStorage.SQLITE, "Points", -1, UserDataFetchMode.NO_CACHE));
        assertEquals("mysql", data.getString(UserStorage.MYSQL, "Name", UserDataFetchMode.NO_CACHE));
        assertEquals("sqlite", data.getString(UserStorage.SQLITE, "Name", UserDataFetchMode.NO_CACHE));
    }

    @SuppressWarnings("deprecation")
    @Test void flatReadsKeepVirtualFileAccessAndLiveUserIdentity() {
        AdvancedCoreUser user = mock(AdvancedCoreUser.class);
        when(user.getUUID()).thenReturn("first", "second");
        AtomicReference<String> observed = new AtomicReference<>();
        FileConfiguration config = new YamlConfiguration();
        config.set("Points", 7); config.set("Name", "player");
        UserData data = new UserData(user) {
            @Override public FileConfiguration getData(String uuid) { observed.set(uuid); return config; }
        };
        assertEquals(7, data.getInt(UserStorage.FLAT, "Points", -1, UserDataFetchMode.NO_CACHE));
        assertEquals("first", observed.get());
        assertEquals("player", data.getString(UserStorage.FLAT, "Name", UserDataFetchMode.NO_CACHE));
        assertEquals("second", observed.get());
    }

    @Test void integerReadUsesCapturedCacheAcrossCacheRefresh() {
        AdvancedCoreUser user = mock(AdvancedCoreUser.class);
        UserDataCache oldCache = mock(UserDataCache.class);
        UserDataCache replacement = mock(UserDataCache.class);
        AtomicReference<UserDataCache> current = new AtomicReference<>(oldCache);
        when(user.getCache()).thenAnswer(call -> current.get());
        when(oldCache.isCached("Points")).thenReturn(true);
        when(oldCache.getCache()).thenReturn(new HashMap<>(Map.of("Points", new DataValueInt(7))));
        doAnswer(call -> { current.set(replacement); return null; }).when(user).cacheIfNeeded();
        UserData data = new UserData(user);
        assertEquals(7, data.getInt(UserStorage.MYSQL, "Points", -1, UserDataFetchMode.CACHE_ONLY));
        verify(user, times(1)).getCache();
        verifyNoInteractions(replacement);
    }

    @Test void defaultOverloadsStillConsultPluginStorageAndUserFetchMode() {
        AdvancedCoreUser user = mock(AdvancedCoreUser.class);
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        when(user.getPlugin()).thenReturn(plugin);
        when(plugin.getStorageType()).thenReturn(UserStorage.MYSQL);
        when(user.getUserDataFetchMode()).thenReturn(UserDataFetchMode.TEMP_ONLY);
        UserData data = new UserData(user);
        data.setTempCache(new HashMap<>(Map.of("Points", new DataValueInt(7), "Name", new DataValueString("player"))));
        assertEquals(7, data.getInt("Points"));
        assertEquals("player", data.getString("Name"));
        verify(user, never()).cache();
        verify(user, never()).getCache();
        verify(plugin, times(2)).getStorageType();
    }
}
