package com.bencodez.advancedcore.tests.api.rewards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
import com.bencodez.advancedcore.api.rewards.RewardRegistry;
import com.bencodez.advancedcore.api.rewards.SubDirectlyDefinedReward;

class PreparedRewardCatalogTest {

    private AdvancedCorePlugin plugin;
    private RewardHandler handler;

    @BeforeEach
    void setUp() {
        plugin = mock(AdvancedCorePlugin.class);
        Logger logger = mock(Logger.class);
        when(plugin.getLogger()).thenReturn(logger);
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
        Reward directReward = new Reward("Direct_Child", directData);
        when(direct.getReward()).thenReturn(directReward);
        handler.addDirectlyDefined(direct);

        SubDirectlyDefinedReward sub = mock(SubDirectlyDefinedReward.class);
        when(sub.getFullPath()).thenReturn("Sub.Child");
        Reward subReward = new Reward("Sub_Child", subData);
        when(sub.getReward()).thenReturn(subReward);
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

    @Test
    void optionalMissingDirectHandleDoesNotBlockValidRewards() {
        DirectlyDefinedReward optional = mock(DirectlyDefinedReward.class);
        when(optional.getPath()).thenReturn("Optional.Absent");
        handler.addDirectlyDefined(optional);
        handler.getRewards().add(new Reward("Available", rewardData("available")));

        PreparedRewardCatalog catalog = handler.prepareCatalog(new Reward("Root", rewardData("root")));

        assertEquals(2, catalog.getDefinitionCount());
        assertMessage("available", catalog.instantiate("Available"));
        assertThrows(PreparedRewardDefinitionException.class, () -> catalog.instantiate("Optional.Absent"));
    }

    @Test
    void missingDirectHandleShadowsSameNamedFileAfterRoundTrip() {
        DirectlyDefinedReward optional = mock(DirectlyDefinedReward.class);
        when(optional.getPath()).thenReturn("Optional.Absent");
        handler.addDirectlyDefined(optional);
        handler.getRewards().add(new Reward("Optional_Absent", rewardData("file must stay shadowed")));
        handler.getRewards().add(new Reward("Available", rewardData("available")));

        PreparedRewardCatalog catalog = PreparedRewardCatalog.decode(
                handler.prepareCatalog(new Reward("Root", rewardData("root"))).encode());

        assertThrows(PreparedRewardDefinitionException.class, () -> catalog.instantiate("Optional.Absent"));
        assertMessage("available", catalog.instantiate("Available"));
    }

    @Test
    void fileLookupMatchesRegistryUnicodeCaseSemanticsAndFirstEntryWins() {
        handler.getRewards().add(new Reward("İ", rewardData("first")));
        handler.getRewards().add(new Reward("i", rewardData("second")));
        PreparedRewardCatalog catalog = PreparedRewardCatalog.decode(
                handler.prepareCatalog(new Reward("Root", rewardData("root"))).encode());

        assertMessage("first", catalog.instantiate("i"));
        assertMessage("first", catalog.instantiate("İ"));
    }

    @Test
    void fileLookupKeepsRawRegisteredNamesDistinctFromNormalizedRequests() {
        handler.getRewards().add(new Reward("Foo Bar", rewardData("spaced file")));
        handler.getRewards().add(new Reward("Foo_Bar", rewardData("underscored file")));
        PreparedRewardCatalog catalog = PreparedRewardCatalog.decode(
                handler.prepareCatalog(new Reward("Root", rewardData("root"))).encode());

        assertMessage("underscored file", handler.getReward("Foo Bar"));
        assertMessage("underscored file", catalog.instantiate("Foo Bar"));
    }

    @Test
    void combinedDefinitionBytesAreBoundedBeforeJoiningRecords() {
        String largeValue = "x".repeat(800_000);
        for (int index = 0; index < 5; index++) {
            YamlConfiguration data = new YamlConfiguration();
            data.set("Payload", largeValue);
            handler.getRewards().add(new Reward("Large" + index, data));
        }

        assertThrows(PreparedRewardDefinitionException.class,
                () -> handler.prepareCatalog(new Reward("Root", rewardData("root"))));
    }

    @Test
    void decodeRejectsExcessRecordsBeforeSplittingThem() throws Exception {
        String[] parts = handler.prepareCatalog(new Reward("Root", rewardData("root"))).encode().split("/", -1);
        String rootEncoded = new String(Base64.getUrlDecoder().decode(parts[2]), StandardCharsets.UTF_8);
        String records = "\n".repeat(1024);
        String hashInput = "2\n" + rootEncoded + "\n" + records;
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(hashInput.getBytes(StandardCharsets.UTF_8));
        StringBuilder hash = new StringBuilder();
        for (byte value : digest) hash.append(String.format("%02x", value));
        String encoded = "AdvancedCorePreparedRewardCatalog/2/" + parts[2] + "/"
                + Base64.getUrlEncoder().withoutPadding().encodeToString(records.getBytes(StandardCharsets.UTF_8))
                + "/" + hash;

        PreparedRewardDefinitionException failure = assertThrows(PreparedRewardDefinitionException.class,
                () -> PreparedRewardCatalog.decode(encoded));
        assertTrue(failure.getMessage().contains("definitions"));
    }

    @Test
    void oldCatalogFormatIsRejectedBeforeRestoringItsDefinitions() {
        assertThrows(PreparedRewardDefinitionException.class,
                () -> PreparedRewardCatalog.decode("AdvancedCorePreparedRewardCatalog/1/eA/eQ/oldhash"));
    }

    @Test
    void rewardFileCaptureWaitsForConcurrentRegistryReload() throws Exception {
        List<Reward> files = handler.getRewards();
        files.add(new Reward("Before", rewardData("before")));
        Reward root = new Reward("Root", rewardData("root"));
        RewardRegistry registry = handler.getRewardRegistry();
        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread reload = new Thread(() -> {
            synchronized (registry) {
                registry.resetRewards();
                lockHeld.countDown();
                try {
                    release.await();
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                }
                registry.getRewards().add(new Reward("After", rewardData("after")));
            }
        });
        reload.start();
        try {
            org.junit.jupiter.api.Assertions.assertTrue(lockHeld.await(5, TimeUnit.SECONDS));
            CountDownLatch captureStarted = new CountDownLatch(1);
            CompletableFuture<PreparedRewardCatalog> capture = CompletableFuture.supplyAsync(() -> {
                captureStarted.countDown();
                return handler.prepareCatalog(root);
            });
            org.junit.jupiter.api.Assertions.assertTrue(captureStarted.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> capture.get(100, TimeUnit.MILLISECONDS));
            release.countDown();
            PreparedRewardCatalog catalog = capture.get(5, TimeUnit.SECONDS);
            assertMessage("after", catalog.instantiate("After"));
            assertThrows(PreparedRewardDefinitionException.class, () -> catalog.instantiate("Before"));
        } finally {
            release.countDown();
            reload.join(5000);
        }
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
