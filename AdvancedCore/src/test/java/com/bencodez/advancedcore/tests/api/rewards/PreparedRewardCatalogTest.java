package com.bencodez.advancedcore.tests.api.rewards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.logging.Logger;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.rewards.DirectlyDefinedReward;
import com.bencodez.advancedcore.api.rewards.PreparedRewardCatalog;
import com.bencodez.advancedcore.api.rewards.PreparedRewardDefinitionException;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.rewards.RewardHandler;
import com.bencodez.advancedcore.api.rewards.SubDirectlyDefinedReward;

class PreparedRewardCatalogTest {

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
    void catalogCapturesNamedDirectSubAndFileDefinitionsBeforeTheLiveRegistryChanges() {
        YamlConfiguration rootData = rewardData("root before");
        YamlConfiguration directData = rewardData("direct before");
        YamlConfiguration subData = rewardData("sub before");
        YamlConfiguration fileData = rewardData("file before");
        YamlConfiguration collisionFileData = rewardData("file collision");

        DirectlyDefinedReward direct = mock(DirectlyDefinedReward.class);
        when(direct.getPath()).thenReturn("Direct.Child");
        when(direct.getReward()).thenReturn(new Reward("Direct_Child", directData));
        handler.addDirectlyDefined(direct);

        SubDirectlyDefinedReward sub = mock(SubDirectlyDefinedReward.class);
        when(sub.getFullPath()).thenReturn("Sub.Child");
        when(sub.getReward()).thenReturn(new Reward("Sub_Child", subData));
        handler.addSubDirectlyDefined(sub);

        handler.getRewards().add(new Reward("File_Child", fileData));
        handler.getRewards().add(new Reward("Direct_Child", collisionFileData));

        PreparedRewardCatalog catalog = handler.prepareCatalog(new Reward("Root", rootData));
        rootData.set("Messages", List.of("root after"));
        directData.set("Messages", List.of("direct after"));
        subData.set("Messages", List.of("sub after"));
        fileData.set("Messages", List.of("file after"));

        assertEquals(5, catalog.getDefinitionCount());
        assertMessage("root before", catalog.instantiateRoot());
        assertMessage("direct before", catalog.instantiate("Direct Child"));
        assertMessage("sub before", catalog.instantiate("Sub Child"));
        assertMessage("file before", catalog.instantiate("File Child"));
        assertMessage("direct before", catalog.instantiate("Direct_Child"));
        Reward firstFileLookup = catalog.instantiate("File Child");
        firstFileLookup.getConfig().getConfigData().set("Messages", List.of("execution mutation"));
        assertMessage("file before", catalog.instantiate("File Child"));
    }

    @Test
    void catalogRoundTripRetainsItsHashAndRejectsUnknownChangedAndOversizeInputs() {
        YamlConfiguration child = rewardData("before");
        handler.getRewards().add(new Reward("Child", child));
        PreparedRewardCatalog captured = handler.prepareCatalog(new Reward("Root", rewardData("root")));
        PreparedRewardCatalog restored = PreparedRewardCatalog.decode(captured.encode());

        assertEquals(captured.encode(), restored.encode());
        assertEquals(captured.getVersionHash(), restored.getVersionHash());
        assertMessage("before", restored.instantiate("Child"));
        assertThrows(PreparedRewardDefinitionException.class, () -> restored.instantiate("missing"));

        String encoded = captured.encode();
        String lastCharacter = encoded.substring(encoded.length() - 1);
        String changed = encoded.substring(0, encoded.length() - 1) + ("0".equals(lastCharacter) ? "1" : "0");
        assertThrows(PreparedRewardDefinitionException.class, () -> PreparedRewardCatalog.decode(changed));
        assertThrows(PreparedRewardDefinitionException.class,
                () -> PreparedRewardCatalog.decode("AdvancedCorePreparedRewardCatalog/" + "x".repeat(8 * 1024 * 1024)));
    }

    @Test
    void nestedNameCanBeResolvedFromFrozenCatalogDuringPlanPreparation() {
        YamlConfiguration root = rewardData("root");
        root.set("Rewards", List.of("Child"));
        YamlConfiguration oldChild = rewardData("before reload");
        handler.getRewards().add(new Reward("Child", oldChild));
        PreparedRewardCatalog prepared = PreparedRewardCatalog.decode(
                handler.prepareCatalog(new Reward("Root", root)).encode());

        oldChild.set("Messages", List.of("after reload"));
        List<String> nestedNames = prepared.instantiateRoot().getConfig().getConfigData().getStringList("Rewards");
        assertEquals(List.of("Child"), nestedNames);
        assertMessage("before reload", prepared.instantiate(nestedNames.get(0)));
    }

    private static YamlConfiguration rewardData(String message) {
        YamlConfiguration data = new YamlConfiguration();
        data.set("Messages", List.of(message));
        return data;
    }

    private static void assertMessage(String expected, Reward reward) {
        assertEquals(List.of(expected), reward.getConfig().getConfigData().getStringList("Messages"));
    }
}
