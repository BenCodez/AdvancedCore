package com.bencodez.advancedcore.api.rewards;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.Logger;

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
	void quarantinesPreviouslyMaterializedFileBackedSubReward() throws IOException {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		AdvancedCoreConfigOptions options = mock(AdvancedCoreConfigOptions.class);
		RewardHandler handler = mock(RewardHandler.class);
		RewardRegistry registry = mock(RewardRegistry.class);
		SubRewardResolver resolver = mock(SubRewardResolver.class);
		Reward reward = mock(Reward.class);
		RewardFileData config = mock(RewardFileData.class);
		Logger logger = mock(Logger.class);

		File rewardsFolder = new File(tempDir, "Rewards");
		File directlyDefined = new File(rewardsFolder, "DirectlyDefined");
		assertTrue(directlyDefined.mkdirs());
		File stale = new File(directlyDefined, "Daily_Rewards.yml");
		assertTrue(stale.createNewFile());

		when(plugin.getDataFolder()).thenReturn(tempDir);
		when(plugin.getOptions()).thenReturn(options);
		when(plugin.getLogger()).thenReturn(logger);
		when(options.isLoadDefaultRewards()).thenReturn(false);
		when(handler.getRewardRegistry()).thenReturn(registry);
		when(handler.getSubRewardResolver()).thenReturn(resolver);
		when(handler.getRewards()).thenReturn(new ArrayList<>(java.util.List.of(reward)));
		when(reward.getConfig()).thenReturn(config);
		when(config.getRewardFolder()).thenReturn(directlyDefined);
		when(reward.getName()).thenReturn("Daily_Rewards");
		when(reward.getFile()).thenReturn(stale);
		when(resolver.getFileBackedSubReward("Daily_Rewards")).thenReturn(mock(SubDirectlyDefinedReward.class));

		AdvancedCorePlugin.setInstance(plugin);
		RewardLoader loader = new RewardLoader(handler, plugin);
		loader.checkDirectlyDefined();

		assertFalse(stale.exists());
		assertTrue(new File(directlyDefined, "Daily_Rewards.yml.disabled").exists());
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
