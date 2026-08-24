package com.bencodez.advancedcore.api.rewards;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RewardRegistrySecurityTest {

	@Test
	void rewardFileNameRejectsPathTraversal() {
		assertFalse(RewardRegistry.isSafeRewardFileName("../../OtherPlugin/config"));
		assertFalse(RewardRegistry.isSafeRewardFileName("..\\..\\OtherPlugin\\config"));
		assertFalse(RewardRegistry.isSafeRewardFileName("folder/reward"));
	}

	@Test
	void rewardFileNameAllowsNormalRewardNames() {
		assertTrue(RewardRegistry.isSafeRewardFileName("Daily"));
		assertTrue(RewardRegistry.isSafeRewardFileName("Daily_Reward"));
		assertTrue(RewardRegistry.isSafeRewardFileName("Daily.Reward-v2"));
	}
}
