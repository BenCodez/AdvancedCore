package com.bencodez.advancedcore.api.rewards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.bencodez.advancedcore.AdvancedCoreConfigOptions;
import com.bencodez.advancedcore.AdvancedCorePlugin;

class RewardLoaderSecurityTest {

	@TempDir
	File tempDir;

	@AfterEach
	void tearDown() {
		AdvancedCorePlugin.setInstance(null);
	}

	@Test
	void loadRewardsSuppressesGeneratedSnapshotsWithoutDeletingThem() throws IOException {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		AdvancedCoreConfigOptions options = mock(AdvancedCoreConfigOptions.class);
		RewardHandler handler = mock(RewardHandler.class);
		RewardRegistry registry = mock(RewardRegistry.class);
		Logger logger = mock(Logger.class);
		ArrayList<Reward> rewards = new ArrayList<>();

		File directlyDefined = new File(tempDir, "Rewards/DirectlyDefined");
		assertTrue(directlyDefined.mkdirs());
		File generated = new File(directlyDefined, "Daily_Rewards.yml");
		YamlConfiguration data = new YamlConfiguration();
		data.set("DirectlyDefinedReward", true);
		data.set("Messages", List.of("generated reward"));
		data.save(generated);

		when(plugin.getDataFolder()).thenReturn(tempDir);
		when(plugin.getOptions()).thenReturn(options);
		when(plugin.getLogger()).thenReturn(logger);
		when(options.isLoadDefaultRewards()).thenReturn(false);
		when(handler.getRewardRegistry()).thenReturn(registry);
		when(handler.getRewards()).thenReturn(rewards);

		AdvancedCorePlugin.setInstance(plugin);
		RewardLoader loader = new RewardLoader(handler, plugin);
		loader.addRewardFolder(directlyDefined, false, false);
		loader.loadRewards();

		assertTrue(generated.exists());
		assertFalse(new File(directlyDefined, "Daily_Rewards.yml.disabled").exists());
		assertTrue(rewards.isEmpty());
	}

	@Test
	void standaloneDirectLookupOfGeneratedSnapshotIsBlocked() throws IOException {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		AdvancedCoreConfigOptions options = mock(AdvancedCoreConfigOptions.class);
		RewardHandler handler = mock(RewardHandler.class);
		Logger logger = mock(Logger.class);

		File directlyDefined = new File(tempDir, "Rewards/DirectlyDefined");
		assertTrue(directlyDefined.mkdirs());
		YamlConfiguration data = new YamlConfiguration();
		data.set("DirectlyDefinedReward", true);
		data.set("Messages", List.of("generated reward"));
		data.save(new File(directlyDefined, "QueuedReward.yml"));

		when(plugin.getDataFolder()).thenReturn(tempDir);
		when(plugin.getOptions()).thenReturn(options);
		when(plugin.getLogger()).thenReturn(logger);
		when(handler.getRewards()).thenReturn(new ArrayList<>());

		AdvancedCorePlugin.setInstance(plugin);
		RewardLoader loader = new RewardLoader(handler, plugin);
		Reward reward = loader.getRewardDirectlyDefined("QueuedReward");

		assertTrue(reward instanceof QueuedGeneratedReward);
		assertTrue(((QueuedGeneratedReward) reward).getAllowedUserUuids().isEmpty());
	}

	@Test
	void queueOnlyResolverBindsGeneratedSnapshotToRequestedUser() throws IOException {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		AdvancedCoreConfigOptions options = mock(AdvancedCoreConfigOptions.class);
		RewardHandler handler = mock(RewardHandler.class);
		Logger logger = mock(Logger.class);
		UUID queuedUuid = UUID.randomUUID();

		File directlyDefined = new File(tempDir, "Rewards/DirectlyDefined");
		assertTrue(directlyDefined.mkdirs());
		YamlConfiguration data = new YamlConfiguration();
		data.set("DirectlyDefinedReward", true);
		data.set("Messages", List.of("queued reward"));
		data.save(new File(directlyDefined, "QueuedReward.yml"));

		when(plugin.getDataFolder()).thenReturn(tempDir);
		when(plugin.getOptions()).thenReturn(options);
		when(plugin.getLogger()).thenReturn(logger);
		when(handler.getRewards()).thenReturn(new ArrayList<>());

		AdvancedCorePlugin.setInstance(plugin);
		RewardLoader loader = new RewardLoader(handler, plugin);
		Reward reward = loader.getQueuedGeneratedReward("QueuedReward", queuedUuid.toString());

		assertTrue(reward instanceof QueuedGeneratedReward);
		assertTrue(reward.isGeneratedSnapshotCreated());
		assertEquals(java.util.Set.of(queuedUuid.toString()),
				((QueuedGeneratedReward) reward).getAllowedUserUuids());
	}

	@Test
	@SuppressWarnings("unchecked")
	void generatedDotNameDoesNotSuppressManualUnderscoreName() throws Exception {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		RewardHandler handler = mock(RewardHandler.class);

		File directlyDefined = new File(tempDir, "Rewards/DirectlyDefined");
		assertTrue(directlyDefined.mkdirs());
		YamlConfiguration generatedData = new YamlConfiguration();
		generatedData.set("DirectlyDefinedReward", true);
		generatedData.set("Messages", List.of("generated"));
		generatedData.save(new File(directlyDefined, "Foo.Bar.yml"));
		YamlConfiguration manualData = new YamlConfiguration();
		manualData.set("Messages", List.of("manual"));
		manualData.save(new File(directlyDefined, "Foo_Bar.yml"));

		when(plugin.getDataFolder()).thenReturn(tempDir);

		RewardLoader loader = new RewardLoader(handler, plugin);
		loader.addRewardFolder(directlyDefined, false, false);

		java.lang.reflect.Method suppress = RewardLoader.class
				.getDeclaredMethod("suppressGeneratedDirectlyDefinedFiles");
		suppress.setAccessible(true);
		suppress.invoke(loader);

		java.lang.reflect.Field suppressedField = RewardLoader.class
				.getDeclaredField("suppressedDirectlyDefinedRewards");
		suppressedField.setAccessible(true);
		Set<String> suppressed = (Set<String>) suppressedField.get(loader);

		assertTrue(suppressed.contains("foo.bar.yml"));
		assertFalse(suppressed.contains("foo_bar.yml"));
	}

	@Test
	void reusedGeneratedSnapshotDropsRemovedRewardKeys() throws IOException {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		AdvancedCorePlugin.setInstance(plugin);

		File directlyDefined = new File(tempDir, "Rewards/DirectlyDefined");
		assertTrue(directlyDefined.mkdirs());
		File snapshot = new File(directlyDefined, "Daily_AdvancedRewards_foo.yml");
		YamlConfiguration oldData = new YamlConfiguration();
		oldData.set("Commands", List.of("old command"));
		oldData.set("Messages", List.of("old message"));
		oldData.save(snapshot);

		Reward reward = new Reward(directlyDefined, "Daily_AdvancedRewards_foo");
		YamlConfiguration replacement = new YamlConfiguration();
		replacement.set("Messages", List.of("new message"));
		reward.getConfig().setData(replacement);

		YamlConfiguration saved = YamlConfiguration.loadConfiguration(snapshot);
		assertFalse(saved.contains("Commands"));
		assertFalse(saved.isConfigurationSection("Commands"));
		assertEquals(List.of("new message"), saved.getStringList("Messages"));
	}

	@Test
	void loadRewardsDoesNotSuppressNormalDirectFolderFileWithoutGeneratedMarker() throws IOException {
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
		manualData.set("Messages", List.of("manual reward"));
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
		assertEquals(1, rewards.size());
	}
}
