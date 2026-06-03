package com.bencodez.advancedcore.tests.rewards;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.logging.Logger;

import org.bukkit.configuration.ConfigurationSection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.rewards.DirectlyDefinedReward;
import com.bencodez.advancedcore.api.rewards.RewardHandler;
import com.bencodez.advancedcore.api.rewards.SubDirectlyDefinedReward;

public class RewardHandlerTest {

	@TempDir
	File tempDir;

	private RewardHandler rewardHandler;

	@BeforeEach
	public void setUp() {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		when(plugin.getDataFolder()).thenReturn(tempDir);
		when(plugin.getLogger()).thenReturn(mock(Logger.class));

		rewardHandler = new RewardHandler(plugin);
	}

	@AfterEach
	public void tearDown() {
		rewardHandler.getDelayedTimer().shutdownNow();
	}

	@Test
	public void getSubDirectlyDefinedMatchesFileStylePathWithUnderscoresInParentName() {
		rewardHandler.getSubDirectlyDefinedRewards()
				.add(new SubDirectlyDefinedReward(directlyDefinedReward("Test_Rewards_Name"), "Rewards"));

		assertNotNull(rewardHandler.getSubDirectlyDefined("Test_Rewards_Name_Rewards"));
	}

	@Test
	public void hasDirectRewardHandleMatchesSubRewardWithUnderscoresInParentName() {
		rewardHandler.getSubDirectlyDefinedRewards()
				.add(new SubDirectlyDefinedReward(directlyDefinedReward("Test_Rewards_Name"), "Rewards"));

		assertTrue(rewardHandler.hasDirectRewardHandle("Test_Rewards_Name_Rewards"));
	}

	private DirectlyDefinedReward directlyDefinedReward(String path) {
		return new DirectlyDefinedReward(path) {

			@Override
			public void createSection(String key) {
			}

			@Override
			public ConfigurationSection getFileData() {
				return null;
			}

			@Override
			public void save() {
			}

			@Override
			public void setData(String path, Object value) {
			}
		};
	}
}
