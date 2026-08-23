package com.bencodez.advancedcore.tests.rewards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.misc.MiscUtils;
import com.bencodez.advancedcore.api.rewards.DirectlyDefinedReward;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.rewards.RewardBuilder;
import com.bencodez.advancedcore.api.rewards.RewardExecutionContext;
import com.bencodez.advancedcore.api.rewards.RewardExecutor;
import com.bencodez.advancedcore.api.rewards.RewardFileData;
import com.bencodez.advancedcore.api.rewards.RewardHandler;
import com.bencodez.advancedcore.api.rewards.RewardOptions;
import com.bencodez.advancedcore.api.rewards.SubDirectlyDefinedReward;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;

public class RewardExecutorTest {

    private AdvancedCorePlugin plugin;
    private RewardHandler handler;
    private AdvancedCoreUser user;
    private RewardExecutor executor;

    @BeforeEach
    public void setUp() {
        plugin = mock(AdvancedCorePlugin.class);
        handler = mock(RewardHandler.class);
        user = mock(AdvancedCoreUser.class);
        executor = new RewardExecutor(handler, plugin);

        when(plugin.getLogger()).thenReturn(mock(Logger.class));
        when(plugin.isEnabled()).thenReturn(true);
        when(user.getPlayerName()).thenReturn("Ben");
        when(user.getUUID()).thenReturn("uuid");
        when(user.isOnline()).thenReturn(true);
    }

    @Test
    public void executionContextBuildsRewardNamesAndInitializesOnlineState() {
        RewardOptions options = new RewardOptions().setPrefix("Parent").setSuffix("Child");
        RewardExecutionContext context = new RewardExecutionContext(options);
        when(user.isOnline()).thenReturn(false);

        context.initializeOnlineState(user);

        assertSame(options, context.getOptions());
        assertTrue(options.isOnlineSet());
        assertFalse(options.isOnline());
        assertEquals("Parent_Nested_Reward_Child", context.buildRewardName("Nested.Reward"));
    }

    @Test
    public void nullOptionsProduceAUsableExecutionContext() {
        RewardExecutionContext context = new RewardExecutionContext(null).initializeOnlineState(user);

        assertNotNull(context.getOptions());
        assertTrue(context.getOptions().isOnlineSet());
        assertEquals("Reward", context.buildRewardName("Reward"));
    }

    @Test
    public void getRewardConstructsInlineRewardUsingPrefixAndSuffix() {
        YamlConfiguration data = new YamlConfiguration();
        ConfigurationSection section = data.createSection("Nested.Reward");
        RewardOptions options = new RewardOptions().setPrefix("Parent").setSuffix("Child");
        AtomicReference<List<?>> arguments = new AtomicReference<>();

        try (MockedConstruction<Reward> rewards = mockConstruction(Reward.class,
                (mock, context) -> arguments.set(context.arguments()))) {
            Reward result = executor.getReward(data, "Nested.Reward", options);

            assertSame(rewards.constructed().get(0), result);
            assertEquals("Parent_Nested_Reward_Child", arguments.get().get(0));
            assertSame(section, arguments.get().get(1));
        }
    }

    @Test
    public void listRewardDispatchesEveryNamedReward() {
        YamlConfiguration data = new YamlConfiguration();
        data.set("Rewards", new ArrayList<>(List.of("First", "Second")));
        Reward first = mock(Reward.class);
        Reward second = mock(Reward.class);
        RewardOptions options = new RewardOptions();
        when(handler.getReward("First")).thenReturn(first);
        when(handler.getReward("Second")).thenReturn(second);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
            executor.giveReward(user, data, "Rewards", options);
        }

        verify(first).giveReward(user, options);
        verify(second).giveReward(user, options);
        assertTrue(options.isOnlineSet());
    }

    @Test
    public void stringRewardDispatchesNamedReward() {
        YamlConfiguration data = new YamlConfiguration();
        data.set("Reward", "Daily");
        Reward daily = mock(Reward.class);
        when(handler.getReward("Daily")).thenReturn(daily);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
            executor.giveReward(user, data, "Reward", null);
        }

        verify(daily).giveReward(eq(user), any(RewardOptions.class));
    }

    @Test
    public void directSectionUsesRegisteredDirectReward() {
        YamlConfiguration data = new YamlConfiguration();
        data.createSection("Direct.Reward");
        DirectlyDefinedReward direct = mock(DirectlyDefinedReward.class);
        Reward directReward = mock(Reward.class);
        RewardOptions options = new RewardOptions();
        when(handler.getDirectlyDefined("Direct.Reward")).thenReturn(direct);
        when(direct.getReward()).thenReturn(directReward);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
            executor.giveReward(user, data, "Direct.Reward", options);
        }

        verify(directReward).giveReward(user, options);
    }

    @Test
    public void subDirectSectionUsesConstructedPrefixNameForLookup() {
        YamlConfiguration data = new YamlConfiguration();
        data.createSection("Rewards");
        SubDirectlyDefinedReward sub = mock(SubDirectlyDefinedReward.class);
        Reward subReward = mock(Reward.class);
        RewardOptions options = new RewardOptions().setPrefix("Parent");
        when(handler.getSubDirectlyDefined("Parent_Rewards")).thenReturn(sub);
        when(sub.getReward()).thenReturn(subReward);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
            executor.giveReward(user, data, "Rewards", options);
        }

        verify(subReward).giveReward(user, options);
    }

    @Test
    public void inlineSectionConstructsChecksAndExecutesReward() {
        YamlConfiguration data = new YamlConfiguration();
        data.createSection("Inline");
        RewardOptions options = new RewardOptions();

        try (MockedConstruction<Reward> rewards = mockConstruction(Reward.class);
                MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
            executor.giveReward(user, data, "Inline", options);

            Reward reward = rewards.constructed().get(0);
            verify(reward).checkRewardFile();
            verify(reward).giveReward(user, options);
        }
    }

    @Test
    public void commandStyleRewardExecutesConsoleCommand() {
        MiscUtils misc = mock(MiscUtils.class);
        RewardOptions options = new RewardOptions().addPlaceholder("player", "Ben");

        try (MockedStatic<MiscUtils> miscStatic = mockStatic(MiscUtils.class)) {
            miscStatic.when(MiscUtils::getInstance).thenReturn(misc);
            executor.giveReward(user, "/say hi", options);
        }

        verify(misc).executeConsoleCommands("Ben", "/say hi", options.getPlaceholders());
    }

    @Test
    public void mainThreadRewardExecutionHandsOffAsynchronously() {
        Reward reward = mock(Reward.class);
        RewardOptions options = new RewardOptions();
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(plugin.getBukkitScheduler()).thenReturn(scheduler);
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            executor.giveReward(user, reward, options);
        }

        verify(scheduler).runTaskAsynchronously(eq(plugin), task.capture());
        verify(reward, never()).giveReward(user, options);
        task.getValue().run();
        verify(reward).giveReward(user, options);
    }

    @Test
    public void asyncRewardExecutionRunsImmediately() {
        Reward reward = mock(Reward.class);
        RewardOptions options = new RewardOptions();

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
            executor.giveReward(user, reward, options);
        }

        verify(reward).giveReward(user, options);
    }

    @Test
    public void choicesDispatchThroughRewardBuilder() {
        Reward mainReward = mock(Reward.class);
        RewardFileData config = mock(RewardFileData.class);
        ConfigurationSection data = mock(ConfigurationSection.class);
        when(mainReward.getConfig()).thenReturn(config);
        when(mainReward.getName()).thenReturn("Source");
        when(config.getConfigData()).thenReturn(data);
        when(config.getChoicesRewardsPath("A")).thenReturn("Choices.A.Rewards");

        try (MockedConstruction<RewardBuilder> builders = mockConstruction(RewardBuilder.class, (builder, context) -> {
            when(builder.withPrefix(anyString())).thenReturn(builder);
            when(builder.withPlaceHolder(anyString(), anyString())).thenReturn(builder);
        })) {
            executor.giveChoicesReward(mainReward, user, "A");

            RewardBuilder builder = builders.constructed().get(0);
            verify(builder).withPrefix("Source");
            verify(builder).withPlaceHolder("choice", "A");
            verify(builder).send(user);
        }
    }

    @Test
    public void updateRewardConstructsAndChecksInlineReward() {
        YamlConfiguration data = new YamlConfiguration();
        data.createSection("Nested");
        RewardOptions options = new RewardOptions().setPrefix("Parent");
        AtomicReference<List<?>> arguments = new AtomicReference<>();

        try (MockedConstruction<Reward> rewards = mockConstruction(Reward.class,
                (mock, context) -> arguments.set(context.arguments()))) {
            executor.updateReward(data, "Nested", options);

            Reward reward = rewards.constructed().get(0);
            assertEquals("Parent_Nested", arguments.get().get(0));
            verify(reward).checkRewardFile();
        }
    }
}
