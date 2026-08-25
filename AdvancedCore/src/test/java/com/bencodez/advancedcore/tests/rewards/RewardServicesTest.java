package com.bencodez.advancedcore.tests.rewards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.rewards.DefinedReward;
import com.bencodez.advancedcore.api.rewards.DirectlyDefinedReward;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.rewards.RewardFileDefinedReward;
import com.bencodez.advancedcore.api.rewards.RewardHandler;
import com.bencodez.advancedcore.api.rewards.SubDirectlyDefinedReward;
import com.bencodez.advancedcore.api.rewards.injected.RewardInject;
import com.bencodez.advancedcore.api.rewards.injectedrequirement.RequirementInject;

public class RewardServicesTest {

	@TempDir
	File tempDir;

	private RewardHandler handler;
	private AdvancedCorePlugin plugin;
	private Logger logger;

	@BeforeEach
	public void setUp() {
		plugin = mock(AdvancedCorePlugin.class);
		logger = mock(Logger.class);
		when(plugin.getDataFolder()).thenReturn(tempDir);
		when(plugin.getLogger()).thenReturn(logger);
		AdvancedCorePlugin.setInstance(plugin);
		handler = new RewardHandler(plugin);
	}

	@AfterEach
	public void tearDown() {
		handler.getDelayedTimer().shutdownNow();
		AdvancedCorePlugin.setInstance(null);
	}

	@Test
	public void loaderOwnsDefaultFolderAndDiscoversSortedYamlRewards() throws IOException {
		File rewards = new File(tempDir, "Rewards");
		assertEquals(rewards.getAbsolutePath(), handler.getDefaultFolder().getAbsolutePath());
		assertTrue(rewards.mkdirs());
		assertTrue(new File(rewards, "Zulu.yml").createNewFile());
		assertTrue(new File(rewards, "alpha.yml").createNewFile());
		assertTrue(new File(rewards, "ignored.txt").createNewFile());

		assertEquals(List.of("alpha", "Zulu"), handler.getRewardNames(rewards));
	}

	@Test
	public void injectionRegistrySortsRewardsAndRequirementsByPriority() {
		RewardInject lowReward = mock(RewardInject.class);
		RewardInject highReward = mock(RewardInject.class);
		when(lowReward.getPriority()).thenReturn(10);
		when(highReward.getPriority()).thenReturn(100);

		handler.addInjectedReward(lowReward);
		handler.addInjectedReward(highReward);
		assertSame(highReward, handler.getInjectedRewards().get(0));
		assertSame(lowReward, handler.getInjectedRewards().get(1));

		RequirementInject lowRequirement = mock(RequirementInject.class);
		RequirementInject highRequirement = mock(RequirementInject.class);
		when(lowRequirement.getPriority()).thenReturn(5);
		when(highRequirement.getPriority()).thenReturn(50);

		handler.addInjectedRequirements(lowRequirement);
		handler.addInjectedRequirements(highRequirement);
		assertSame(highRequirement, handler.getInjectedRequirements().get(0));
		assertSame(lowRequirement, handler.getInjectedRequirements().get(1));
	}

	@Test
	public void validatorPreservesValidPathFacadeAndRewardContentChecks() {
		Set<String> paths = new HashSet<>(List.of("CustomPath"));
		handler.setValidPaths(paths);
		handler.addValidPath("SecondPath");
		assertSame(paths, handler.getValidPaths());
		assertTrue(handler.getValidPaths().contains("SecondPath"));

		YamlConfiguration config = new YamlConfiguration();
		config.set("StringReward", "Example");
		config.set("ListReward", List.of("One"));
		config.createSection("SectionReward").set("Message", "Hello");

		assertTrue(handler.hasRewards(config, "StringReward"));
		assertTrue(handler.hasRewards(config, "ListReward"));
		assertTrue(handler.hasRewards(config, "SectionReward"));
	}

	@Test
	public void subRewardResolverStopsRecursiveDiscovery() {
		DirectlyDefinedReward root = mock(DirectlyDefinedReward.class);
		when(root.getFullPath()).thenReturn("Root");

		SubDirectlyDefinedReward loop = mock(SubDirectlyDefinedReward.class);
		when(loop.getFullPath()).thenReturn("Loop");
		when(loop.isDirectlyDefined()).thenReturn(true);

		RewardInject inject = mock(RewardInject.class);
		when(inject.subRewards(any(DefinedReward.class))).thenReturn(new ArrayList<>(List.of(loop)));
		handler.getInjectedRewards().add(inject);

		handler.checkSubRewards(root);

		assertEquals(1, handler.getSubDirectlyDefinedRewards().size());
		assertSame(loop, handler.getSubDirectlyDefinedRewards().get(0));
		verify(logger).warning("Detected recursive sub reward path, skipping: Loop");
	}

	@Test
	public void fileBackedSubRewardsRemainInternalToSectionDispatch() {
		Reward root = mock(Reward.class);
		when(root.getName()).thenReturn("Daily");
		handler.getRewards().add(root);

		SubDirectlyDefinedReward sub = mock(SubDirectlyDefinedReward.class);
		when(sub.getFullPath()).thenReturn("Daily.Rewards");

		RewardInject inject = mock(RewardInject.class);
		when(inject.subRewards(any(DefinedReward.class))).thenAnswer(invocation -> {
			DefinedReward defined = invocation.getArgument(0);
			if (defined instanceof RewardFileDefinedReward) {
				return new ArrayList<>(List.of(sub));
			}
			return new ArrayList<>();
		});
		handler.getInjectedRewards().add(inject);

		handler.checkSubRewards();

		assertNull(handler.getSubDirectlyDefined("Daily_Rewards"));
		assertSame(sub, handler.getSubRewardResolver().getFileBackedSubReward("Daily_Rewards"));
	}

	@Test
	public void fileBackedSubRewardsRemainSnapshotCapableForDeferredExecution() {
		YamlConfiguration config = new YamlConfiguration();
		config.createSection("Parent.Child").set("Messages", List.of("queued"));

		RewardFileDefinedReward fileMaster = mock(RewardFileDefinedReward.class);
		when(fileMaster.getFullPath()).thenReturn("Daily");
		when(fileMaster.needsDot()).thenReturn(".");
		when(fileMaster.getPath()).thenReturn("Parent");
		when(fileMaster.getFileData()).thenReturn(config);
		Reward fileBacked = new SubDirectlyDefinedReward(fileMaster, "Child").getReward();

		DirectlyDefinedReward directMaster = mock(DirectlyDefinedReward.class);
		when(directMaster.getFullPath()).thenReturn("Direct");
		when(directMaster.needsDot()).thenReturn(".");
		when(directMaster.getPath()).thenReturn("Parent");
		when(directMaster.getFileData()).thenReturn(config);
		Reward directlyDefined = new SubDirectlyDefinedReward(directMaster, "Child").getReward();

		assertTrue(fileBacked.isNeedsRewardFile());
		assertFalse(directlyDefined.isNeedsRewardFile());
	}
}
