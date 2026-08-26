package com.bencodez.advancedcore.api.javascript;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.bencodez.advancedcore.AdvancedCorePlugin;

import me.clip.placeholderapi.PlaceholderAPI;

class JavascriptPlaceholderResolutionPriorityTest {

    @Test
    void customPlaceholderWinsSameNamedPapiTokenThenExpandsPapiInsideCustomValue() {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        OfflinePlayer player = mock(OfflinePlayer.class);
        JavascriptEngine engine = new JavascriptEngine();

        when(plugin.isPlaceHolderAPIEnabled()).thenReturn(true);

        try (MockedStatic<AdvancedCorePlugin> pluginStatic = mockStatic(AdvancedCorePlugin.class);
                MockedStatic<PlaceholderAPI> papiStatic = mockStatic(PlaceholderAPI.class)) {
            pluginStatic.when(AdvancedCorePlugin::getInstance).thenReturn(plugin);
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
}
