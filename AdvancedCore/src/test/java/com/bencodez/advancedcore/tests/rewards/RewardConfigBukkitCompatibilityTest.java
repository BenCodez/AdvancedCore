package com.bencodez.advancedcore.tests.rewards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.spongepowered.configurate.BasicConfigurationNode;

import com.bencodez.advancedcore.api.rewards.RewardFileData;
import com.bencodez.advancedcore.bukkit.rewards.BukkitRewardConfigReader;
import com.bencodez.advancedcore.core.rewards.RewardConfigReader;
import com.bencodez.simpleapi.core.config.ConfigurateStructuredConfigView;
import com.bencodez.simpleapi.core.config.StructuredConfigView;

class RewardConfigBukkitCompatibilityTest {
    @TempDir
    Path tempDir;

    @Test
    void defaultAndMalformedScalarReadsKeepNativeDefaultTreeRules() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.addDefault("Chance", 12.5);
        yaml.addDefault("Delayed.Enabled", true);
        yaml.addDefault("Delayed.Hours", 3);
        yaml.addDefault("Timed.Hour", 7);
        yaml.addDefault("Server", "default-server");
        RewardFileData data = new RewardFileData(null, yaml);
        assertScalars(data);
        yaml.set("Chance", "not a number");
        yaml.set("Delayed.Enabled", "true");
        yaml.set("Delayed.Hours", 2.75);
        yaml.set("Timed.Hour", "bad");
        assertScalars(data);
    }

    @Test
    void liveCaseInsensitiveReadsAndCasingCollisionsMatchExistingSection() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("chance", 2.5);
        yaml.set("Chance", 99);
        yaml.set("dElAyEd.hOuRs", 3);
        yaml.set("rEwArDtYpE", "oFfLiNe");
        RewardFileData data = new RewardFileData(null, yaml);
        assertScalars(data);
        assertEquals("OFFLINE", data.getRewardType());
        yaml.set("chance", 8.5);
        assertScalars(data);
    }

    @Test
    void rawCommandListsKeepIdentityOrderAndUnsupportedNativeValues() {
        YamlConfiguration yaml = new YamlConfiguration();
        Object nativeValue = new Object();
        ArrayList<Object> commands = new ArrayList<>(List.of("first", 7, nativeValue, "last"));
        yaml.set("Commands", commands);
        RewardFileData data = new RewardFileData(null, yaml);
        assertSame(data.getConfigData().getList("Commands"), data.getCommandsConsole());
        assertSame(nativeValue, ((List<?>) data.getCommandsConsole()).get(2));
        commands.add("later");
        assertEquals(5, data.getCommandsConsole().size());
    }

    @Test
    void mappedCommandPriorityAndWorldListsStayLive() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("Commands.Console", new ArrayList<>(List.of("console")));
        yaml.set("Commands.Player", new ArrayList<>(List.of("player")));
        yaml.set("Priority", new ArrayList<>(List.of("a", "b")));
        yaml.set("Worlds", new ArrayList<>(List.of("world")));
        RewardFileData data = new RewardFileData(null, yaml);
        assertSame(data.getConfigData().getList("Commands.Console"), data.getCommandsConsole());
        assertSame(data.getConfigData().getList("Commands.Player"), data.getCommandsPlayer());
        assertSame(data.getConfigData().getList("Priority"), data.getPriority());
        assertSame(data.getConfigData().getList("Worlds"), data.getWorlds());
    }

    @Test
    void missingListsStillReturnFreshMutableArrayLists() {
        RewardFileData data = new RewardFileData(null, new YamlConfiguration());
        ArrayList<String> first = data.getCommandsConsole();
        first.add("local edit");
        assertEquals(List.of(), data.getCommandsConsole());
        assertNotSame(first, data.getCommandsConsole());
        assertEquals(ArrayList.class, data.getPriority().getClass());
        assertEquals(ArrayList.class, data.getWorlds().getClass());
        assertEquals(ArrayList.class, data.getCommandsPlayer().getClass());
    }

    @Test
    void nonArrayListNativeValuesRetainTheLegacyCastFailure() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("Commands", new LinkedList<>(List.of("command")));
        RewardFileData data = new RewardFileData(null, yaml);
        data.setConfigData(yaml);
        assertThrows(ClassCastException.class, data::getCommandsConsole);
        yaml.set("Commands", List.of("immutable"));
        assertThrows(ClassCastException.class, data::getCommandsConsole);
    }

    @Test
    void replacementAndOverriddenGettersAreResolvedLazily() {
        YamlConfiguration first = new YamlConfiguration();
        first.set("Chance", 1.0);
        YamlConfiguration second = new YamlConfiguration();
        second.set("Chance", 2.0);
        RewardFileData data = new RewardFileData(null, first);
        data.setConfigData(second);
        assertSame(second, data.getConfigData());
        assertEquals(2.0, data.getChance());

        class SwitchingData extends RewardFileData {
            private ConfigurationSection active;
            SwitchingData() {
                super(null, first);
                active = first;
            }
            @Override
            public ConfigurationSection getConfigData() {
                if (active == null) throw new AssertionError("Getter invoked during construction");
                return active;
            }
        }
        SwitchingData switching = new SwitchingData();
        assertEquals(1.0, switching.getChance());
        switching.active = second;
        assertEquals(2.0, switching.getChance());
    }

    @Test
    void fileReloadUsesTheNewSectionWithoutChangingExistingReloadCasing() throws Exception {
        Path target = tempDir.resolve("reward.yml");
        YamlConfiguration initial = new YamlConfiguration();
        initial.set("chance", 3.0);
        RewardFileData data = new RewardFileData(null, initial);
        assertEquals(3.0, data.getChance());
        YamlConfiguration replacement = new YamlConfiguration();
        replacement.set("Chance", 8.0);
        replacement.set("chance", 12.0);
        replacement.save(target.toFile());
        data.setDataFile(target.toFile());
        data.reload();
        assertSame(data.getFileData().getConfigurationSection(""), data.getConfigData());
        assertEquals(data.getConfigData().getDouble("Chance"), data.getChance());
        assertEquals(8.0, data.getChance());
    }

    @Test
    void legacySectionGettersRetainNativeSectionIdentity() {
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection display = yaml.createSection("DisplayItem");
        ConfigurationSection item = yaml.createSection("Items.gift");
        ConfigurationSection choiceDisplay = yaml.createSection("Choices.daily.DisplayItem");
        yaml.createSection("Choices.daily.Rewards");
        RewardFileData data = new RewardFileData(null, yaml);
        data.setConfigData(yaml);
        assertSame(display, data.getDisplayItem());
        assertSame(item, data.getItemSection("gift"));
        assertSame(choiceDisplay, data.getChoicesItem("daily"));
        assertEquals(Set.of("daily"), data.getChoices());
        assertEquals("Choices.daily.Rewards", data.getChoicesRewardsPath("daily"));
    }

    @Test
    void rawMapsAreNotReclassifiedAsLegacyNativeSections() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("Choices", Map.of("daily", Map.of("Chance", 25)));
        RewardFileData data = new RewardFileData(null, yaml);
        data.setConfigData(yaml);
        assertFalse(yaml.isConfigurationSection("Choices"));
        assertEquals(Set.of(), data.getChoices());
        StructuredConfigView definition = new BukkitRewardConfigReader(() -> yaml).definitionAt("Choices", "daily");
        assertNotNull(definition);
        assertEquals(25, definition.getInt("Chance", 0));
    }

    @Test
    void actualConfigurateAndBukkitAdaptersAgreeOnPortableSettings() throws Exception {
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("Chance", 32.5);
        settings.put("Commands.Console", new ArrayList<>(List.of("first", "second")));
        settings.put("Commands.Player", new ArrayList<>(List.of("player")));
        settings.put("Delayed.Enabled", true);
        settings.put("Delayed.Hours", 1);
        settings.put("Delayed.Minutes", 2);
        settings.put("Delayed.Seconds", 3);
        settings.put("Delayed.MilliSeconds", 4);
        settings.put("Timed.Enabled", true);
        settings.put("Timed.Hour", 7);
        settings.put("Timed.Minute", 15);
        settings.put("EnableChoices", true);
        settings.put("ForceOffline", true);
        settings.put("RequirePermission", true);
        settings.put("RewardType", "oNlInE");
        settings.put("Server", "survival");
        settings.put("Worlds", new ArrayList<>(List.of("world")));
        settings.put("Priority", new ArrayList<>(List.of("one", "two")));
        settings.put("DirectlyDefinedReward", true);
        YamlConfiguration yaml = new YamlConfiguration();
        BasicConfigurationNode node = BasicConfigurationNode.root();
        for (Map.Entry<String, Object> setting : settings.entrySet()) {
            yaml.set(setting.getKey(), setting.getValue());
            node.node((Object[]) setting.getKey().split("\\.")).raw(setting.getValue());
        }
        RewardFileData bukkit = new RewardFileData(null, yaml);
        RewardConfigReader shared = new RewardConfigReader(() -> new ConfigurateStructuredConfigView(node));
        for (String method : List.of("getChance", "getCommandsConsole", "getCommandsPlayer", "getDelayedEnabled",
                "getDelayedHours", "getDelayedMinutes", "getDelayedSeconds", "getDelayedMilliSeconds",
                "getTimedEnabled", "getTimedHour", "getTimedMinute", "getEnableChoices", "getForceOffline",
                "getRequirePermission", "getRewardType", "getServer", "getWorlds", "getPriority", "isDirectlyDefinedReward")) {
            assertEquals(RewardFileData.class.getMethod(method).invoke(bukkit),
                    RewardConfigReader.class.getMethod(method).invoke(shared), method);
        }
    }

    @Test
    void nativePlainDataListsStayDetachedAndLiteralNestedNamesWork() {
        BasicConfigurationNode node = BasicConfigurationNode.root();
        node.node("Commands").raw(List.of("one", 7, true));
        node.node("Choices", "daily.bonus", "Rewards", "Commands").raw(List.of("nested"));
        RewardConfigReader shared = new RewardConfigReader(() -> new ConfigurateStructuredConfigView(node));
        List<?> list = shared.getCommandsConsole();
        assertEquals(List.of("one", 7, true), list);
        assertThrows(UnsupportedOperationException.class, list::clear);
        node.node("Commands").raw(List.of("changed"));
        assertEquals(List.of("one", 7, true), list);
        StructuredConfigView nested = shared.definitionAt("Choices", "daily.bonus", "Rewards");
        assertNotNull(nested);
        assertEquals(List.of("nested"), new RewardConfigReader(() -> nested).getCommandsConsole());
    }

    private static void assertScalars(RewardFileData data) {
        ConfigurationSection section = data.getConfigData();
        assertEquals(section.getDouble("Chance"), data.getChance());
        assertEquals(section.getBoolean("Delayed.Enabled"), data.getDelayedEnabled());
        assertEquals(section.getInt("Delayed.Hours"), data.getDelayedHours());
        assertEquals(section.getInt("Delayed.Minutes"), data.getDelayedMinutes());
        assertEquals(section.getInt("Delayed.Seconds"), data.getDelayedSeconds());
        assertEquals(section.getInt("Delayed.MilliSeconds"), data.getDelayedMilliSeconds());
        assertEquals(section.getBoolean("Timed.Enabled"), data.getTimedEnabled());
        assertEquals(section.getInt("Timed.Hour"), data.getTimedHour());
        assertEquals(section.getInt("Timed.Minute"), data.getTimedMinute());
        assertEquals(section.getBoolean("EnableChoices"), data.getEnableChoices());
        assertEquals(section.getBoolean("ForceOffline"), data.getForceOffline());
        assertEquals(section.getBoolean("RequirePermission"), data.getRequirePermission());
        assertEquals(section.getBoolean("DirectlyDefinedReward"), data.isDirectlyDefinedReward());
        assertEquals(section.getString("Server", ""), data.getServer());
    }
}
