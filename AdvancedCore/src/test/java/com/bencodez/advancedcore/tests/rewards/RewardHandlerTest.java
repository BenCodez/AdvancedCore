package com.bencodez.advancedcore.tests.rewards;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
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
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;

public class RewardHandlerTest {

	@TempDir
	File tempDir;

	private RewardHandler rewardHandler;
	private AdvancedCorePlugin plugin;

	@BeforeEach
	public void setUp() {
		plugin = mock(AdvancedCorePlugin.class);
		when(plugin.getDataFolder()).thenReturn(tempDir);
		when(plugin.getLogger()).thenReturn(mock(Logger.class));

		rewardHandler = new RewardHandler(plugin);
	}

	@AfterEach
	public void tearDown() {
		rewardHandler.getDelayedTimer().shutdownNow();
	}

	@Test
	public void beginShutdownClosesEligibilityAndWaitsForAdmittedReplayChecks() throws Exception {
		assertShutdownClosesEligibility(false);
	}

	@Test
	public void boundedShutdownClosesEligibilityAndWaitsForAdmittedReplayChecks() throws Exception {
		assertShutdownClosesEligibility(true);
	}

	private void assertShutdownClosesEligibility(boolean bounded) throws Exception {
		when(plugin.isEnabled()).thenReturn(true);
		AdvancedCoreUser user = mock(AdvancedCoreUser.class);
		when(user.getPlugin()).thenReturn(plugin);
		try (var admitted = rewardHandler.admitPersistedReplayCheck()) {
			assertNotNull(admitted);
			if (bounded) rewardHandler.shutdown(0, TimeUnit.NANOSECONDS);
			var retirement = rewardHandler.beginShutdown().toCompletableFuture();
			assertNull(rewardHandler.admitPersistedReplayCheck());
			CompletionException failure = assertThrows(CompletionException.class,
					() -> rewardHandler.getRewardEligibility().signal(user).toCompletableFuture().join());
			assertTrue(failure.getCause() instanceof IllegalStateException);
			assertFalse(retirement.isDone(), "shutdown must retain an already admitted replay check");
		}
		rewardHandler.beginShutdown().toCompletableFuture().get(2, TimeUnit.SECONDS);
		assertTrue(rewardHandler.getDelayedTimer().isTerminated());
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
