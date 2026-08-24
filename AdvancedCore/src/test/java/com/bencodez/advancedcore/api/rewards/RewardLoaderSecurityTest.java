package com.bencodez.advancedcore.api.rewards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.bencodez.advancedcore.AdvancedCoreConfigOptions;
import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.rewards.injected.RewardInject;
import com.bencodez.advancedcore.api.rewards.injectedrequirement.RequirementInject;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.advancedcore.api.user.UserManager;

class RewardLoaderSecurityTest {

	@TempDir
	File tempDir;

	@AfterEach
	void tearDown() {
		AdvancedCorePlugin.setInstance(null);
	}

	@Test
	void loadRewardsQuarantinesOrphanedGeneratedDirectReward() throws IOException {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		AdvancedCoreConfigOptions options = mock(AdvancedCoreConfigOptions.class);
		RewardHandler handler = mock(RewardHandler.class);
		RewardRegistry registry = mock(RewardRegistry.class);
		Logger logger = mock(Logger.class);

		File rewardsFolder = new File(tempDir, "Rewards");
		File directlyDefined = new File(rewardsFolder, "DirectlyDefined");
		assertTrue(directlyDefined.mkdirs());
		File stale = new File(directlyDefined, "Daily_Rewards.yml");
		YamlConfiguration staleData = new YamlConfiguration();
		staleData.set("DirectlyDefinedReward", true);
		staleData.set("Messages", java.util.List.of("legacy generated reward"));
		staleData.save(stale);

		when(plugin.getDataFolder()).thenReturn(tempDir);
		when(plugin.getOptions()).thenReturn(options);
		when(plugin.getLogger()).thenReturn(logger);
		when(options.isLoadDefaultRewards()).thenReturn(false);
		when(handler.getRewardRegistry()).thenReturn(registry);
		when(handler.getRewards()).thenReturn(new ArrayList<>());

		AdvancedCorePlugin.setInstance(plugin);
		RewardLoader loader = new RewardLoader(handler, plugin);
		loader.addRewardFolder(directlyDefined, false, false);
		loader.loadRewards();

		assertFalse(stale.exists());
		assertTrue(new File(directlyDefined, "Daily_Rewards.yml.disabled").exists());
		assertTrue(handler.getRewards().isEmpty());
	}

	@Test
	void loadRewardsPreservesQueuedGeneratedRewardAndRestrictsItToQueuedUser() throws IOException {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		AdvancedCoreConfigOptions options = mock(AdvancedCoreConfigOptions.class);
		RewardHandler handler = mock(RewardHandler.class);
		RewardRegistry registry = mock(RewardRegistry.class);
		UserManager userManager = mock(UserManager.class);
		AdvancedCoreUser queuedUser = mock(AdvancedCoreUser.class);
		Logger logger = mock(Logger.class);
		ArrayList<Reward> rewards = new ArrayList<>();
		UUID queuedUuid = UUID.randomUUID();

		File directlyDefined = new File(tempDir, "Rewards/DirectlyDefined");
		assertTrue(directlyDefined.mkdirs());
		File queued = new File(directlyDefined, "QueuedReward.yml");
		YamlConfiguration queuedData = new YamlConfiguration();
		queuedData.set("DirectlyDefinedReward", true);
		queuedData.set("Messages", List.of("queued reward"));
		queuedData.save(queued);

		when(plugin.getDataFolder()).thenReturn(tempDir);
		when(plugin.getOptions()).thenReturn(options);
		when(plugin.getLogger()).thenReturn(logger);
		when(plugin.getUserManager()).thenReturn(userManager);
		when(plugin.getRewardHandler()).thenReturn(handler);
		when(options.isLoadDefaultRewards()).thenReturn(false);
		when(handler.getRewardRegistry()).thenReturn(registry);
		when(handler.getRewards()).thenReturn(rewards);
		when(handler.getInjectedRequirements()).thenReturn(new CopyOnWriteArrayList<RequirementInject>());
		when(handler.getInjectedRewards()).thenReturn(new CopyOnWriteArrayList<RewardInject>());
		when(handler.rewardExist("QueuedReward")).thenReturn(false);
		when(userManager.getAllUUIDs()).thenReturn(new ArrayList<>(List.of(queuedUuid.toString())));
		when(userManager.getUser(queuedUuid)).thenReturn(queuedUser);
		when(queuedUser.getOfflineRewards())
				.thenReturn(new ArrayList<>(List.of("QueuedReward%placeholders%player=Ben")));
		when(queuedUser.getTimedRewards()).thenReturn(new HashMap<>());

		AdvancedCorePlugin.setInstance(plugin);
		RewardLoader loader = new RewardLoader(handler, plugin);
		loader.addRewardFolder(directlyDefined, false, false);
		loader.loadRewards();

		assertTrue(queued.exists());
		assertFalse(new File(directlyDefined, "QueuedReward.yml.disabled").exists());
		assertEquals(1, rewards.size());
		assertTrue(rewards.get(0) instanceof QueuedGeneratedReward);
		assertEquals(java.util.Set.of(queuedUuid.toString()),
				((QueuedGeneratedReward) rewards.get(0)).getAllowedUserUuids());
	}

	@Test
	void loadRewardsDoesNotQuarantineNormalDirectFolderFileWithoutGeneratedMarker() throws IOException {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		AdvancedCoreConfigOptions options = mock(AdvancedCoreConfigOptions.class);
		RewardHandler handler = mock(RewardHandler.class);
		RewardRegistry registry = mock(RewardRegistry.class);
		Logger logger = mock(Logger.class);
		ArrayList<Reward> rewards = new ArrayList<>();

		File directlyDefined = new File(tempDir, "Rewards/DirectlyDefined");
		assertTrue(directlyDefined.mkdirs());
		File manual = new File(directlyDefined, "ExampleBasic.yml");
		YamlConfiguration manualData = new YamlConfiguration();
		manualData.set("Messages", java.util.List.of("manual reward"));
		manualData.save(manual);

		when(plugin.getDataFolder()).thenReturn(tempDir);
		when(plugin.getOptions()).thenReturn(options);
		when(plugin.getLogger()).thenReturn(logger);
		when(options.isLoadDefaultRewards()).thenReturn(false);
		when(handler.getRewardRegistry()).thenReturn(registry);
		when(handler.getRewards()).thenReturn(rewards);
		when(handler.rewardExist("ExampleBasic")).thenReturn(false);

		AdvancedCorePlugin.setInstance(plugin);
		RewardLoader loader = new RewardLoader(handler, plugin);
		loader.addRewardFolder(directlyDefined, false, false);
		loader.loadRewards();

		assertTrue(manual.exists());
		assertFalse(new File(directlyDefined, "ExampleBasic.yml.disabled").exists());
		assertEquals(1, rewards.size());
	}

	@Test
	void choosesNonConflictingDisabledBackupName() throws IOException {
		File stale = new File(tempDir, "Daily_Rewards.yml");
		assertTrue(stale.createNewFile());
		assertTrue(new File(tempDir, "Daily_Rewards.yml.disabled").createNewFile());

		File disabled = RewardLoader.nextDisabledFile(stale);

		assertTrue(disabled.getName().equals("Daily_Rewards.yml.disabled.1"));
	}
}
