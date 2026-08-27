package com.bencodez.advancedcore.api.javascript;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.tests.BaseTest;

import me.clip.placeholderapi.PlaceholderAPI;

class JavascriptPlaceholderResolutionPriorityTest {

    @Test
    void customPlaceholderWinsSameNamedPapiTokenThenExpandsPapiInsideCustomValue() {
        AdvancedCorePlugin plugin = BaseTest.getInstance().plugin;
        OfflinePlayer player = mock(OfflinePlayer.class);
        JavascriptEngine engine = new JavascriptEngine();

        when(plugin.isPlaceHolderAPIEnabled()).thenReturn(true);

        try (MockedStatic<PlaceholderAPI> papiStatic = mockStatic(PlaceholderAPI.class)) {
            papiStatic.when(() -> PlaceholderAPI.setPlaceholders(player, "%reward_alias%"))
                    .thenReturn("CustomName");
            papiStatic.when(() -> PlaceholderAPI.setPlaceholders(player, "%player_name%"))
                    .thenReturn("PapiName");

            String prepared = JavascriptPlaceholderBinder.bind("'%player_name%' == 'CustomName'", player,
                    Map.of("player_name", "%reward_alias%"), engine);

            assertEquals("'CustomName' == 'CustomName'", prepared);
            papiStatic.verify(() -> PlaceholderAPI.setPlaceholders(player, "%reward_alias%"));
            papiStatic.verify(() -> PlaceholderAPI.setPlaceholders(player, "%player_name%"), never());
        }
    }
    @Test
    void decodedCustomValueStillExpandsNestedPapiToken() {
        AdvancedCorePlugin plugin = BaseTest.getInstance().plugin;
        OfflinePlayer player = mock(OfflinePlayer.class);
        JavascriptEngine engine = new JavascriptEngine();

        when(plugin.isPlaceHolderAPIEnabled()).thenReturn(true);
        String encoded = JavascriptPlaceholderValue.encode("%player_name%");

        try (MockedStatic<PlaceholderAPI> papiStatic = mockStatic(PlaceholderAPI.class)) {
            papiStatic.when(() -> PlaceholderAPI.setPlaceholders(player, "%player_name%"))
                    .thenReturn("Ben");

            String prepared = JavascriptPlaceholderBinder.bind("'" + encoded + "' == 'Ben'", player,
                    Map.of(), engine);

            assertEquals("'Ben' == 'Ben'", prepared);
            papiStatic.verify(() -> PlaceholderAPI.setPlaceholders(player, "%player_name%"));
        }
    }

}
