package com.bencodez.advancedcore.tests.api.rewards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.logging.Logger;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.rewards.PreparedRewardDefinition;
import com.bencodez.advancedcore.api.rewards.PreparedRewardDefinitionException;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.rewards.RewardHandler;
import com.bencodez.advancedcore.api.rewards.RewardOptions;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PreparedRewardDefinitionTest {

    private AdvancedCorePlugin plugin;
    private RewardHandler handler;

    @BeforeEach
    void setUp() {
        plugin = mock(AdvancedCorePlugin.class);
        when(plugin.getLogger()).thenReturn(mock(Logger.class));
        AdvancedCorePlugin.setInstance(plugin);
        handler = new RewardHandler(plugin);
        when(plugin.getRewardHandler()).thenReturn(handler);
    }

    @AfterEach
    void tearDown() {
        AdvancedCorePlugin.setInstance(null);
    }

    @Test
    void preparedInlineRewardSurvivesSourceMutationAndCreatesFreshConfigurations() {
        YamlConfiguration source = new YamlConfiguration();
        ConfigurationSection inline = source.createSection("Inline");
        inline.set("Commands", List.of("say before"));
        inline.createSection("Nested").set("Message", "before");

        PreparedRewardDefinition prepared = handler.prepareReward(source, "Inline",
                new RewardOptions().setPrefix("Parent"));
        source.set("Inline.Commands", List.of("say after"));
        source.set("Inline.Nested.Message", "after");

        Reward first = prepared.instantiate();
        first.getConfig().getConfigData().set("Commands", List.of("execution mutation"));
        Reward second = prepared.instantiate();

        assertEquals("Parent_Inline", prepared.getRewardName());
        assertEquals(List.of("say before"), second.getConfig().getConfigData().getStringList("Commands"));
        assertEquals("before", second.getConfig().getConfigData().getString("Nested.Message"));
    }

    @Test
    void encodedDefinitionRoundTripsWithAStableHash() {
        YamlConfiguration source = new YamlConfiguration();
        source.set("Commands.Console", List.of("say one", "say two"));
        source.set("Chance", 12.5D);

        PreparedRewardDefinition captured = PreparedRewardDefinition.capture("Named", source);
        PreparedRewardDefinition restored = PreparedRewardDefinition.decode(captured.encode());

        assertEquals(captured.encode(), restored.encode());
        assertEquals(captured.getVersionHash(), restored.getVersionHash());
        assertEquals(List.of("say one", "say two"),
                restored.instantiate().getConfig().getConfigData().getStringList("Commands.Console"));
    }

    @Test
    void changedOrUnsupportedDefinitionsFailClosed() {
        YamlConfiguration source = new YamlConfiguration();
        source.set("Commands", List.of("say one"));
        String encoded = PreparedRewardDefinition.capture("Named", source).encode();
        String lastCharacter = encoded.substring(encoded.length() - 1);
        String changed = encoded.substring(0, encoded.length() - 1) + ("0".equals(lastCharacter) ? "1" : "0");

        assertThrows(PreparedRewardDefinitionException.class, () -> PreparedRewardDefinition.decode(changed));

        source.set("Unsupported", new Object());
        assertThrows(PreparedRewardDefinitionException.class,
                () -> PreparedRewardDefinition.capture("Unsupported", source));
    }

    @Test
    void namedPreparationCapturesTheResolvedRewardRatherThanTheLiveRegistryEntry() {
        YamlConfiguration source = new YamlConfiguration();
        source.set("Messages", List.of("before reload"));
        Reward registered = new Reward("Named_Reward", source);
        handler.getRewards().add(registered);

        PreparedRewardDefinition prepared = handler.prepareReward("Named Reward");
        source.set("Messages", List.of("after reload"));

        assertEquals("Named_Reward", prepared.getRewardName());
        assertEquals(List.of("before reload"),
                prepared.instantiate().getConfig().getConfigData().getStringList("Messages"));
        assertNotEquals(PreparedRewardDefinition.capture("Named_Reward", source).getVersionHash(),
                prepared.getVersionHash());
    }

    @Test
    void namedPreparationRejectsARewardThatWasNotResolvedByTheRegistry() {
        assertThrows(IllegalArgumentException.class, () -> handler.prepareReward("missing"));
    }

    @Test
    void detachedDirectDefinitionDoesNotCreateALegacyGeneratedRewardFile() {
        YamlConfiguration source = new YamlConfiguration();
        source.set("Delay.Seconds", 5);
        Reward direct = new Reward("Direct", source).needsRewardFile(false);
        Reward prepared = PreparedRewardDefinition.decode(PreparedRewardDefinition.capture(direct).encode())
                .instantiate();

        assertDoesNotThrow(prepared::checkRewardFile);
    }
}
