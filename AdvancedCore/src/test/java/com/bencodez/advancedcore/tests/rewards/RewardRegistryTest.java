package com.bencodez.advancedcore.tests.rewards;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    public void directlyDefinedLookupNormalizesCaseDotsUnderscoresAndDuplicatePaths() {
        DirectlyDefinedReward first = mock(DirectlyDefinedReward.class);
        DirectlyDefinedReward duplicate = mock(DirectlyDefinedReward.class);
        when(first.getPath()).thenReturn("Test.Path");
        when(duplicate.getPath()).thenReturn("test_path");

        registry.addDirectlyDefined(first);
        registry.addDirectlyDefined(duplicate);

        assertSame(first, registry.getDirectlyDefined("TEST.PATH"));
        assertSame(first, registry.getDirectlyDefined("test_path"));
        assertTrue(registry.hasDirectRewardHandle("TEST_PATH"));
        assertEquals(1, registry.getDirectlyDefinedRewards().size());
    }

    @Test
    public void subDirectlyDefinedLookupNormalizesSeparatorsAndRejectsDuplicates() {
        SubDirectlyDefinedReward first = mock(SubDirectlyDefinedReward.class);
        SubDirectlyDefinedReward duplicate = mock(SubDirectlyDefinedReward.class);
        when(first.getFullPath()).thenReturn("Test_Rewards_Name.Rewards");
        when(duplicate.getFullPath()).thenReturn("test.rewards.name.rewards");

        registry.addSubDirectlyDefined(first);
        registry.addSubDirectlyDefined(duplicate);

        assertSame(first, registry.getSubDirectlyDefined("Test_Rewards_Name_Rewards"));
        assertSame(first, registry.getSubDirectlyDefined("TEST.REWARDS.NAME.REWARDS"));
        assertTrue(registry.hasDirectRewardHandle("test_rewards_name_rewards"));
        assertEquals(1, registry.getSubDirectlyDefinedRewards().size());
    }

    @Test
    public void loadedRewardLookupNormalizesSpacesAndIgnoresCase() {
        Reward reward = mock(Reward.class);
        when(reward.getName()).thenReturn("My_Reward");
        registry.getRewards().add(reward);

        assertSame(reward, registry.getReward("my reward"));
    }

    @Test
    public void directlyDefinedRewardLookupUsesSameNormalizedPathRules() {
        DirectlyDefinedReward direct = mock(DirectlyDefinedReward.class);
        Reward reward = mock(Reward.class);
        when(direct.getPath()).thenReturn("Direct.Reward");
        when(direct.getReward()).thenReturn(reward);
        registry.addDirectlyDefined(direct);

        assertSame(reward, registry.getReward("Direct_Reward"));
        assertSame(reward, registry.getReward("direct.reward"));
        assertSame(reward, registry.getReward("DIRECT REWARD"));
    }

    @Test
    public void rewardExistUsesNormalizedNamesAndRejectsEmptyNames() {
        Reward reward = mock(Reward.class);
        when(reward.getName()).thenReturn("Daily_Reward");
        registry.getRewards().add(reward);

        assertTrue(registry.rewardExist("daily reward"));
        assertTrue(registry.rewardExist("DAILY_REWARD"));
        assertFalse(registry.rewardExist(""));
    }

    @Test
    public void normalizationIsLocaleIndependentAndNullSafe() {
        assertEquals("", RewardRegistry.normalizeLookupName(null));
        assertEquals("my_reward", RewardRegistry.normalizeDirectPath("My.Reward"));
        assertEquals("my_reward", RewardRegistry.normalizeDirectPath("My Reward"));
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
        assertEquals(1, registry.getRewards().size());
        assertSame(replacement, registry.getRewards().get(0));
    }
}
