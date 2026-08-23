package com.bencodez.advancedcore.tests.rewards;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.List;
import java.util.logging.Logger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.rewards.DirectlyDefinedReward;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.rewards.RewardRegistry;
import com.bencodez.advancedcore.api.rewards.SubDirectlyDefinedReward;

public class RewardRegistryTest {

	private RewardRegistry registry;

	@BeforeEach
	public void setUp() {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		when(plugin.getLogger()).thenReturn(mock(Logger.class));
		registry = new RewardRegistry(plugin);
	}

	@Test
	public void directlyDefinedLookupIsCaseInsensitiveAndRejectsDuplicatePaths() {
		DirectlyDefinedReward first = mock(DirectlyDefinedReward.class);
		DirectlyDefinedReward duplicate = mock(DirectlyDefinedReward.class);
		when(first.getPath()).thenReturn("Test.Path");
		when(duplicate.getPath()).thenReturn("test.path");

		registry.addDirectlyDefined(first);
		registry.addDirectlyDefined(duplicate);

		assertSame(first, registry.getDirectlyDefined("TEST.PATH"));
		assertTrue(registry.getDirectlyDefinedRewards().size() == 1);
	}

	@Test
	public void subDirectlyDefinedLookupSupportsFileStyleUnderscores() {
		SubDirectlyDefinedReward sub = mock(SubDirectlyDefinedReward.class);
		when(sub.getFullPath()).thenReturn("Test_Rewards_Name.Rewards");

		registry.addSubDirectlyDefined(sub);

		assertSame(sub, registry.getSubDirectlyDefined("Test_Rewards_Name_Rewards"));
		assertTrue(registry.hasDirectRewardHandle("Test_Rewards_Name_Rewards"));
	}

	@Test
	public void loadedRewardLookupNormalizesSpacesAndIgnoresCase() {
		Reward reward = mock(Reward.class);
		when(reward.getName()).thenReturn("My_Reward");
		registry.getRewards().add(reward);

		assertSame(reward, registry.getReward("my reward"));
	}

	@Test
	public void directlyDefinedRewardLookupPreservesExistingPathBehavior() {
		DirectlyDefinedReward direct = mock(DirectlyDefinedReward.class);
		Reward reward = mock(Reward.class);
		when(direct.getPath()).thenReturn("Direct.Reward");
		when(direct.getReward()).thenReturn(reward);
		registry.addDirectlyDefined(direct);

		assertSame(reward, registry.getReward("Direct_Reward"));
	}

	@Test
	public void rewardExistIsCaseInsensitiveAndRejectsEmptyNames() {
		Reward reward = mock(Reward.class);
		when(reward.getName()).thenReturn("DailyReward");
		registry.getRewards().add(reward);

		assertTrue(registry.rewardExist("dailyreward"));
		assertFalse(registry.rewardExist(""));
	}

	@Test
	public void resetRewardsReplacesAndClearsTheLoadedRewardList() {
		List<Reward> original = registry.getRewards();
		original.add(mock(Reward.class));

		registry.resetRewards();

		assertNotSame(original, registry.getRewards());
		assertTrue(registry.getRewards().isEmpty());
	}

	@Test
	public void updateRewardReplacesMatchingFileAndValidatesReplacement() {
		Reward oldReward = mock(Reward.class);
		Reward replacement = mock(Reward.class);
		File oldFile = mock(File.class);
		File replacementFile = mock(File.class);
		when(oldReward.getFile()).thenReturn(oldFile);
		when(replacement.getFile()).thenReturn(replacementFile);
		when(oldFile.getPath()).thenReturn("Rewards/Test.yml");
		when(replacementFile.getPath()).thenReturn("Rewards/Test.yml");
		registry.getRewards().add(oldReward);

		registry.updateReward(replacement);

		verify(replacement).validate();
		assertTrue(registry.getRewards().size() == 1);
		assertSame(replacement, registry.getRewards().get(0));
	}
}
