package com.bencodez.advancedcore.tests.rewards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.rewards.RewardHandler;
import com.bencodez.advancedcore.api.rewards.injected.RewardInject;
import com.bencodez.advancedcore.rewards.builtin.BuiltinRewards;
import com.bencodez.advancedcore.rewards.builtin.RewardActionBar;
import com.bencodez.advancedcore.rewards.builtin.RewardAdvancedPriority;
import com.bencodez.advancedcore.rewards.builtin.RewardAdvancedRandomReward;
import com.bencodez.advancedcore.rewards.builtin.RewardAdvancedRewards;
import com.bencodez.advancedcore.rewards.builtin.RewardAdvancedWorld;
import com.bencodez.advancedcore.rewards.builtin.RewardBossBar;
import com.bencodez.advancedcore.rewards.builtin.RewardChoices;
import com.bencodez.advancedcore.rewards.builtin.RewardCommands;
import com.bencodez.advancedcore.rewards.builtin.RewardEffect;
import com.bencodez.advancedcore.rewards.builtin.RewardExp;
import com.bencodez.advancedcore.rewards.builtin.RewardFirework;
import com.bencodez.advancedcore.rewards.builtin.RewardItems;
import com.bencodez.advancedcore.rewards.builtin.RewardJavascript;
import com.bencodez.advancedcore.rewards.builtin.RewardLucky;
import com.bencodez.advancedcore.rewards.builtin.RewardMessages;
import com.bencodez.advancedcore.rewards.builtin.RewardMoney;
import com.bencodez.advancedcore.rewards.builtin.RewardPotions;
import com.bencodez.advancedcore.rewards.builtin.RewardPriority;
import com.bencodez.advancedcore.rewards.builtin.RewardRandom;
import com.bencodez.advancedcore.rewards.builtin.RewardRandomReward;
import com.bencodez.advancedcore.rewards.builtin.RewardSound;
import com.bencodez.advancedcore.rewards.builtin.RewardSpecialChance;
import com.bencodez.advancedcore.rewards.builtin.RewardSubRewards;
import com.bencodez.advancedcore.rewards.builtin.RewardTempPermission;
import com.bencodez.advancedcore.rewards.builtin.RewardTitle;

public class BuiltinRewardRegistrationTest {

    @TempDir
    File tempDir;

    private AdvancedCorePlugin plugin;
    private RewardHandler rewardHandler;

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
    public void actionBarRegistersExpectedPaths() {
        assertRewardPaths(RewardActionBar::register, "ActionBar");
    }

    @Test
    public void advancedPriorityRegistersExpectedPaths() {
        assertRewardPaths(RewardAdvancedPriority::register, "AdvancedPriority");
    }

    @Test
    public void advancedRandomRewardRegistersExpectedPaths() {
        assertRewardPaths(RewardAdvancedRandomReward::register, "AdvancedRandomReward");
    }

    @Test
    public void advancedRewardsRegistersExpectedPaths() {
        assertRewardPaths(RewardAdvancedRewards::register, "AdvancedRewards");
    }

    @Test
    public void advancedWorldRegistersExpectedPaths() {
        assertRewardPaths(RewardAdvancedWorld::register, "AdvancedWorld");
    }

    @Test
    public void bossBarRegistersExpectedPaths() {
        assertRewardPaths(RewardBossBar::register, "BossBar");
    }

    @Test
    public void choicesRegistersExpectedPaths() {
        assertRewardPaths(RewardChoices::register, "EnableChoices");
    }

    @Test
    public void commandsRegistersEveryLegacyForm() {
        assertRewardPaths(RewardCommands::register, "NumberCommand", "Command", "Commands", "Commands", "RandomCommand");
    }

    @Test
    public void effectRegistersExpectedPaths() {
        assertRewardPaths(RewardEffect::register, "Effect");
    }

    @Test
    public void expRegistersScalarAndRangeForms() {
        assertRewardPaths(RewardExp::register, "EXP", "EXPLevels", "EXP", "EXPLevels");
    }

    @Test
    public void fireworkRegistersExpectedPaths() {
        assertRewardPaths(RewardFirework::register, "Firework");
    }

    @Test
    public void itemsRegistersEveryLegacyForm() {
        assertRewardPaths(RewardItems::register, "Item", "RandomItem", "Items");
    }

    @Test
    public void javascriptRegistersListAndConditionalForms() {
        assertRewardPaths(RewardJavascript::register, "Javascripts", "Javascript");
    }

    @Test
    public void luckyRegistersExpectedPaths() {
        assertRewardPaths(RewardLucky::register, "Lucky");
    }

    @Test
    public void messagesRegistersEveryLegacyForm() {
        assertRewardPaths(RewardMessages::register, "Message", "Messages.Player", "Message", "RandomMessage",
                "Messages.Player", "Messages.Broadcast", "Messages.Broadcast");
    }

    @Test
    public void moneyRegistersScalarAndRangeForms() {
        assertRewardPaths(RewardMoney::register, "Money", "Money");
    }

    @Test
    public void potionsRegistersExpectedPaths() {
        assertRewardPaths(RewardPotions::register, "Potions");
    }

    @Test
    public void priorityRegistersExpectedPaths() {
        assertRewardPaths(RewardPriority::register, "Priority");
    }

    @Test
    public void randomRegistersExpectedPaths() {
        assertRewardPaths(RewardRandom::register, "Random");
    }

    @Test
    public void randomRewardRegistersExpectedPaths() {
        assertRewardPaths(RewardRandomReward::register, "RandomReward");
    }

    @Test
    public void soundRegistersExpectedPaths() {
        assertRewardPaths(RewardSound::register, "Sound");
    }

    @Test
    public void specialChanceRegistersExpectedPaths() {
        assertRewardPaths(RewardSpecialChance::register, "SpecialChance");
    }

    @Test
    public void subRewardsRegistersExpectedPaths() {
        assertRewardPaths(RewardSubRewards::register, "Rewards");
    }

    @Test
    public void tempPermissionRegistersExpectedPaths() {
        assertRewardPaths(RewardTempPermission::register, "TempPermission");
    }

    @Test
    public void titleRegistersExpectedPaths() {
        assertRewardPaths(RewardTitle::register, "Title");
    }

    @Test
    public void builtinCatalogRegistersAllRewardPathsInHistoricalOrder() {
        assertRewardPaths(BuiltinRewards::register,
                "Money", "Money", "NumberCommand",
                "EXP", "EXPLevels", "EXP", "EXPLevels",
                "Message", "Messages.Player", "Message", "RandomMessage", "Messages.Player", "Messages.Broadcast",
                "Messages.Broadcast",
                "Command", "ActionBar", "Commands", "Commands", "Javascripts", "Javascript", "Lucky", "Random",
                "Rewards", "RandomCommand", "RandomReward", "TempPermission", "AdvancedRewards", "AdvancedRandomReward",
                "Priority", "Potions", "Title", "BossBar", "Sound", "Effect", "Firework", "Item",
                "AdvancedPriority", "AdvancedWorld", "SpecialChance", "RandomItem", "EnableChoices", "Items");
    }

    private void assertRewardPaths(BiConsumer<RewardHandler, AdvancedCorePlugin> registrar, String... expectedPaths) {
        int before = rewardHandler.getInjectedRewards().size();
        registrar.accept(rewardHandler, plugin);

        List<String> actualPaths = rewardHandler.getInjectedRewards().subList(before, rewardHandler.getInjectedRewards().size())
                .stream().map(RewardInject::getPath).toList();

        assertEquals(List.of(expectedPaths), actualPaths);
    }
}
