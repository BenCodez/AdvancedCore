package com.bencodez.advancedcore.api.rewards;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;

import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.AdvancedCorePlugin;

class RewardRegistrySecurityTest {

	@Test
	void rewardFileNameRejectsPathTraversal() {
		assertFalse(RewardRegistry.isSafeRewardFileName("../../OtherPlugin/config"));
		assertFalse(RewardRegistry.isSafeRewardFileName("..\\..\\OtherPlugin\\config"));
		assertFalse(RewardRegistry.isSafeRewardFileName("folder/reward"));
		assertFalse(RewardRegistry.isSafeRewardFileName("reward\0name"));
	}

	@Test
	void rewardFileNameAllowsNormalRewardNames() {
		assertTrue(RewardRegistry.isSafeRewardFileName("Daily"));
		assertTrue(RewardRegistry.isSafeRewardFileName("Daily_Reward"));
		assertTrue(RewardRegistry.isSafeRewardFileName("Daily.Reward-v2"));
	}

	@Test
	void generatedDirectlyDefinedSnapshotIsNeverPublishedToRegistry() {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		RewardRegistry registry = new RewardRegistry(plugin);
		Reward reward = mock(Reward.class);
		RewardFileData config = mock(RewardFileData.class);

		when(reward.getConfig()).thenReturn(config);
		when(reward.getName()).thenReturn("PriorityChild");
		when(config.isDirectlyDefinedReward()).thenReturn(true);
		when(config.getRewardFolder()).thenReturn(new File("Rewards", "DirectlyDefined"));

		registry.updateReward(reward);

		assertFalse(registry.getRewards().contains(reward));
		verify(reward, never()).validate();
	}
}
