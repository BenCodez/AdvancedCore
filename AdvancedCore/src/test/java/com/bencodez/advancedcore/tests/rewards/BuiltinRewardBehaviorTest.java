package com.bencodez.advancedcore.tests.rewards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import com.bencodez.advancedcore.AdvancedCoreConfigOptions;
import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.item.ItemBuilder;
import com.bencodez.advancedcore.api.javascript.JavascriptEngine;
import com.bencodez.advancedcore.api.misc.MiscUtils;
import com.bencodez.advancedcore.api.misc.effects.FireworkHandler;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.rewards.RewardBuilder;
import com.bencodez.advancedcore.api.rewards.RewardHandler;
import com.bencodez.advancedcore.api.rewards.RewardOptions;
import com.bencodez.advancedcore.api.rewards.injected.RewardInject;
import com.bencodez.advancedcore.api.rewards.injected.RewardInjectBoolean;
import com.bencodez.advancedcore.api.rewards.injected.RewardInjectConfigurationSection;
import com.bencodez.advancedcore.api.rewards.injected.RewardInjectDouble;
import com.bencodez.advancedcore.api.rewards.injected.RewardInjectInt;
import com.bencodez.advancedcore.api.rewards.injected.RewardInjectKeys;
import com.bencodez.advancedcore.api.rewards.injected.RewardInjectString;
import com.bencodez.advancedcore.api.rewards.injected.RewardInjectStringList;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
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

public class BuiltinRewardBehaviorTest {

    private AdvancedCorePlugin plugin;
    private AdvancedCoreConfigOptions options;
    private RewardHandler handler;
    private AdvancedCoreUser user;
    private Reward reward;
    private CopyOnWriteArrayList<RewardInject> injects;
    private HashMap<String, String> placeholders;

    @BeforeEach
    public void setUp() {
        plugin = mock(AdvancedCorePlugin.class);
        options = mock(AdvancedCoreConfigOptions.class);
        handler = mock(RewardHandler.class);
        user = mock(AdvancedCoreUser.class);
        reward = mock(Reward.class, Answers.RETURNS_DEEP_STUBS);
        injects = new CopyOnWriteArrayList<>();
        placeholders = new HashMap<>();

        AdvancedCorePlugin.setInstance(plugin);
        when(plugin.getOptions()).thenReturn(options);
        when(options.getBroadcastBlacklist()).thenReturn(new ArrayList<>());
        when(handler.getInjectedRewards()).thenReturn(injects);
        when(reward.getName()).thenReturn("SourceReward");
        when(reward.getRewardName()).thenReturn("SourceReward");
        when(user.getPlayerName()).thenReturn("Ben");
    }

    @Test
    public void expActuallyGivesExperienceAndLevels() {
        RewardExp.register(handler, plugin);

        ((RewardInjectInt) injects.get(0)).onRewardRequest(reward, user, 12, placeholders);
        ((RewardInjectInt) injects.get(1)).onRewardRequest(reward, user, 4, placeholders);

        verify(user).giveExp(12);
        verify(user).giveExpLevels(4);

        ConfigurationSection exp = section("EXP");
        exp.set("Min", 5);
        exp.set("Max", 6);
        String expResult = ((RewardInjectConfigurationSection) injects.get(2))
                .onRewardRequested(reward, user, exp, placeholders);
        assertEquals("5", expResult);
        verify(user).giveExp(5);

        ConfigurationSection levels = section("EXPLevels");
        levels.set("Min", 8);
        levels.set("Max", 9);
        String levelResult = ((RewardInjectConfigurationSection) injects.get(3))
                .onRewardRequested(reward, user, levels, placeholders);
        assertEquals("8", levelResult);
        verify(user).giveExpLevels(8);
    }

    @Test
    public void moneyActuallyGivesFixedAndRangedAmounts() {
        RewardMoney.register(handler, plugin);

        String fixed = ((RewardInjectDouble) injects.get(0)).onRewardRequest(reward, user, 12.75, placeholders);
        assertEquals("12", fixed);
        verify(user).giveMoney(12.75);

        ConfigurationSection range = section("Money");
        range.set("Min", 5.0);
        range.set("Max", 6.0);
        range.set("Round", false);
        String result = ((RewardInjectConfigurationSection) injects.get(1))
                .onRewardRequested(reward, user, range, placeholders);

        ArgumentCaptor<Double> amount = ArgumentCaptor.forClass(Double.class);
        verify(user, atLeastOnce()).giveMoney(amount.capture());
        double ranged = amount.getAllValues().get(amount.getAllValues().size() - 1);
        assertTrue(ranged >= 5.0 && ranged < 6.0);
        assertNotNull(result);
    }

    @Test
    public void actionBarActuallySendsConfiguredMessage() {
        RewardActionBar.register(handler, plugin);
        placeholders.put("player", "Ben");
        ConfigurationSection section = section("ActionBar");
        section.set("Message", "Hello %player%");
        section.set("Delay", 42);

        configInject(0).onRewardRequested(reward, user, section, placeholders);

        verify(user).sendActionBar("Hello Ben", 42);
    }

    @Test
    public void bossBarActuallySendsConfiguredBossBar() {
        RewardBossBar.register(handler, plugin);
        ConfigurationSection section = section("BossBar");
        section.set("Enabled", true);
        section.set("Message", "Boss");
        section.set("Color", "RED");
        section.set("Style", "SEGMENTED_10");
        section.set("Progress", 0.75);
        section.set("Delay", 25);

        configInject(0).onRewardRequested(reward, user, section, placeholders);

        verify(user).sendBossBar("Boss", "RED", "SEGMENTED_10", 0.75, 25);
    }

    @Test
    public void titleActuallySendsConfiguredTitle() {
        RewardTitle.register(handler, plugin);
        placeholders.put("player", "Ben");
        ConfigurationSection section = section("Title");
        section.set("Enabled", true);
        section.set("Title", "Hi %player%");
        section.set("SubTitle", "Welcome");
        section.set("FadeIn", 2);
        section.set("ShowTime", 30);
        section.set("FadeOut", 4);

        configInject(0).onRewardRequested(reward, user, section, placeholders);

        verify(user).sendTitle("Hi Ben", "Welcome", 2, 30, 4);
    }

    @Test
    public void soundActuallyPlaysConfiguredSound() {
        RewardSound.register(handler, plugin);
        ConfigurationSection section = section("Sound");
        section.set("Enabled", true);
        section.set("Sound", "ENTITY_PLAYER_LEVELUP");
        section.set("Volume", 0.5);
        section.set("Pitch", 1.25);

        configInject(0).onRewardRequested(reward, user, section, placeholders);

        verify(user).playSound("ENTITY_PLAYER_LEVELUP", 0.5f, 1.25f);
    }

    @Test
    public void effectActuallyPlaysConfiguredParticles() {
        RewardEffect.register(handler, plugin);
        ConfigurationSection section = section("Effect");
        section.set("Enabled", true);
        section.set("Effect", "FLAME");
        section.set("Data", 2);
        section.set("Particles", 7);
        section.set("Radius", 9);

        configInject(0).onRewardRequested(reward, user, section, placeholders);

        verify(user).playParticle("FLAME", 2, 7, 9);
    }

    @Test
    public void potionsActuallyGiveEveryConfiguredEffect() {
        RewardPotions.register(handler, plugin);
        ConfigurationSection section = section("Potions");
        section.createSection("SPEED");
        section.set("SPEED.Duration", 120);
        section.set("SPEED.Amplifier", 2);
        section.createSection("JUMP_BOOST");
        section.set("JUMP_BOOST.Duration", 60);
        section.set("JUMP_BOOST.Amplifier", 1);

        configInject(0).onRewardRequested(reward, user, section, placeholders);

        verify(user).givePotionEffect("SPEED", 120, 2);
        verify(user).givePotionEffect("JUMP_BOOST", 60, 1);
    }

    @Test
    public void temporaryPermissionActuallyAddsPermission() {
        RewardTempPermission.register(handler, plugin);
        ConfigurationSection section = section("TempPermission");
        section.set("Permission", "advancedcore.test");
        section.set("Expiration", 90);

        configInject(0).onRewardRequested(reward, user, section, placeholders);

        verify(user).addPermission("advancedcore.test", 90);
    }

    @Test
    public void commandsActuallyDispatchEveryCommandForm() {
        RewardCommands.register(handler, plugin);
        MiscUtils misc = mock(MiscUtils.class);
        try (MockedStatic<MiscUtils> miscStatic = mockStatic(MiscUtils.class)) {
            miscStatic.when(MiscUtils::getInstance).thenReturn(misc);

            ConfigurationSection number = section("NumberCommand");
            number.set("Min", 5);
            number.set("Max", 5);
            number.set("Command", "give Ben stone %number%");
            String generated = ((RewardInjectConfigurationSection) injects.get(0))
                    .onRewardRequested(reward, user, number, placeholders);
            assertEquals("5", generated);
            verify(misc).executeConsoleCommands("Ben", "give Ben stone 5", placeholders);

            ((RewardInjectString) injects.get(1)).onRewardRequest(reward, user, "say hi", placeholders);
            verify(misc).executeConsoleCommands("Ben", "say hi", placeholders);

            ArrayList<String> list = new ArrayList<>(List.of("say one", "say two"));
            ((RewardInjectStringList) injects.get(2)).onRewardRequest(reward, user, list, placeholders);
            verify(misc).executeConsoleCommands("Ben", list, placeholders, true);

            ConfigurationSection commands = section("Commands");
            ArrayList<String> console = new ArrayList<>(List.of("say console"));
            ArrayList<String> player = new ArrayList<>(List.of("spawn"));
            commands.set("Console", console);
            commands.set("Player", player);
            commands.set("Stagger", false);
            ((RewardInjectConfigurationSection) injects.get(3)).onRewardRequested(reward, user, commands, placeholders);
            verify(misc).executeConsoleCommands("Ben", console, placeholders, false);
            verify(user).preformCommand(player, placeholders);

            ArrayList<String> random = new ArrayList<>(List.of("say only"));
            ((RewardInjectStringList) injects.get(4)).onRewardRequest(reward, user, random, placeholders);
            verify(misc).executeConsoleCommands("Ben", "say only", placeholders);
        }
    }

    @Test
    public void messagesActuallySendPlayerAndBroadcastForms() {
        RewardMessages.register(handler, plugin);
        MiscUtils misc = mock(MiscUtils.class);
        try (MockedStatic<MiscUtils> miscStatic = mockStatic(MiscUtils.class)) {
            miscStatic.when(MiscUtils::getInstance).thenReturn(misc);

            ((RewardInjectString) injects.get(0)).onRewardRequest(reward, user, "hello", placeholders);
            verify(user).sendMessage("hello", placeholders);

            ArrayList<String> playerList = new ArrayList<>(List.of("one", "two"));
            ((RewardInjectStringList) injects.get(1)).onRewardRequest(reward, user, playerList, placeholders);
            verify(user).sendMessage(playerList, placeholders);

            ArrayList<String> legacyList = new ArrayList<>(List.of("legacy"));
            ((RewardInjectStringList) injects.get(2)).onRewardRequest(reward, user, legacyList, placeholders);
            verify(user).sendMessage(legacyList, placeholders);

            ArrayList<String> random = new ArrayList<>(List.of("only random"));
            ((RewardInjectStringList) injects.get(3)).onRewardRequest(reward, user, random, placeholders);
            verify(user).sendMessage("only random", placeholders);

            ((RewardInjectString) injects.get(4)).onRewardRequest(reward, user, "nested player", placeholders);
            verify(user).sendMessage("nested player", placeholders);

            ArrayList<String> broadcasts = new ArrayList<>(List.of("broadcast one", "broadcast two"));
            ((RewardInjectStringList) injects.get(5)).onRewardRequest(reward, user, broadcasts, placeholders);
            verify(misc).broadcast("broadcast one");
            verify(misc).broadcast("broadcast two");

            ((RewardInjectString) injects.get(6)).onRewardRequest(reward, user, "single broadcast", placeholders);
            verify(misc).broadcast("single broadcast");
        }
    }

    @Test
    public void itemRewardsActuallyGiveBuiltItems() {
        try (MockedConstruction<ItemBuilder> builders = mockConstruction(ItemBuilder.class,
                withSettings().defaultAnswer(Answers.RETURNS_SELF))) {
            RewardItems.registerItem(handler, plugin);
            configInject(0).onRewardRequested(reward, user, section("Item"), placeholders);
            verify(user).giveItem(any(ItemBuilder.class));

            injects.clear();
            RewardItems.registerRandomItem(handler, plugin);
            ConfigurationSection random = section("RandomItem");
            random.createSection("OnlyItem");
            String selected = ((RewardInjectKeys) injects.get(0)).onRewardRequested(reward, user,
                    random.getKeys(false), random, placeholders);
            assertEquals("OnlyItem", selected);
            verify(user, atLeastOnce()).giveItem(any(ItemBuilder.class));

            injects.clear();
            RewardItems.registerItems(handler, plugin);
            ConfigurationSection items = section("Items");
            items.createSection("FirstItem");
            ((RewardInjectKeys) injects.get(0)).onRewardRequested(reward, user, items.getKeys(false), items, placeholders);
            verify(user, atLeastOnce()).giveItem(any(ItemBuilder.class));
            assertTrue(builders.constructed().size() >= 3);
        }
    }

    @Test
    public void fireworkActuallyLaunchesConfiguredFirework() {
        RewardFirework.register(handler, plugin);
        FireworkHandler fireworkHandler = mock(FireworkHandler.class);
        Player player = mock(Player.class);
        Location location = mock(Location.class);
        when(user.getPlayer()).thenReturn(player);
        when(player.getLocation()).thenReturn(location);

        ConfigurationSection section = section("Firework");
        section.set("Enabled", true);
        section.set("Power", 2);
        section.set("Colors", new ArrayList<>(List.of("RED")));
        section.set("FadeOutColor", new ArrayList<>(List.of("BLUE")));
        section.set("Trail", true);
        section.set("Flicker", false);
        section.set("Types", new ArrayList<>(List.of("BALL")));
        section.set("Detonate", true);

        try (MockedStatic<FireworkHandler> fireworks = mockStatic(FireworkHandler.class)) {
            fireworks.when(FireworkHandler::getInstance).thenReturn(fireworkHandler);
            configInject(0).onRewardRequested(reward, user, section, placeholders);
        }

        verify(fireworkHandler).launchFirework(eq(location), eq(2), eq(new ArrayList<>(List.of("RED"))),
                eq(new ArrayList<>(List.of("BLUE"))), eq(true), eq(false),
                eq(new ArrayList<>(List.of("BALL"))), eq(true));
    }

    @Test
    public void randomRewardActuallyDispatchesSelectedReward() {
        RewardRandomReward.register(handler, plugin);
        ArrayList<String> rewards = new ArrayList<>(List.of("OnlyReward"));

        String selected = ((RewardInjectStringList) injects.get(0)).onRewardRequest(reward, user, rewards, placeholders);

        assertEquals("OnlyReward", selected);
        verify(handler).giveReward(eq(user), eq("OnlyReward"), any(RewardOptions.class));
    }

    @Test
    public void advancedRandomRewardActuallyDispatchesSelectedChild() {
        RewardAdvancedRandomReward.register(handler, plugin);
        ConfigurationSection section = section("AdvancedRandomReward");
        section.createSection("OnlyReward");

        String selected = configInject(0).onRewardRequested(reward, user, section, placeholders);

        assertEquals("OnlyReward", selected);
        verify(handler).giveReward(eq(user), eq(section), eq("OnlyReward"), any(RewardOptions.class));
    }

    @Test
    public void advancedRewardsActuallyDispatchesEveryChild() {
        RewardAdvancedRewards.register(handler, plugin);
        ConfigurationSection section = section("AdvancedRewards");
        section.createSection("One");
        section.createSection("Two");

        configInject(0).onRewardRequested(reward, user, section, placeholders);

        verify(handler).giveReward(eq(user), eq(section), eq("One"), any(RewardOptions.class));
        verify(handler).giveReward(eq(user), eq(section), eq("Two"), any(RewardOptions.class));
    }

    @Test
    public void advancedPriorityActuallyRunsFirstEligibleChild() {
        RewardAdvancedPriority.register(handler, plugin);
        ConfigurationSection section = section("AdvancedPriority");
        section.createSection("First");
        section.createSection("Second");
        Reward first = mock(Reward.class);
        Reward second = mock(Reward.class);
        when(handler.getReward(eq(section), eq("First"), any(RewardOptions.class))).thenReturn(first);
        when(handler.getReward(eq(section), eq("Second"), any(RewardOptions.class))).thenReturn(second);
        when(first.canGiveReward(eq(user), any(RewardOptions.class))).thenReturn(false);
        when(second.canGiveReward(eq(user), any(RewardOptions.class))).thenReturn(true);
        when(second.getName()).thenReturn("Second");

        String selected = configInject(0).onRewardRequested(reward, user, section, placeholders);

        assertEquals("Second", selected);
        verify(first, never()).giveReward(eq(user), any(RewardOptions.class));
        verify(second).giveReward(eq(user), any(RewardOptions.class));
    }

    @Test
    public void advancedWorldActuallyScopesEachChildToItsWorld() {
        RewardAdvancedWorld.register(handler, plugin);
        ConfigurationSection section = section("AdvancedWorld");
        section.createSection("world_nether");

        configInject(0).onRewardRequested(reward, user, section, placeholders);

        assertEquals(List.of("world_nether"), section.getStringList("world_nether.Worlds"));
        verify(handler).giveReward(eq(user), eq(section), eq("world_nether"), any(RewardOptions.class));
    }

    @Test
    public void priorityActuallySkipsIneligibleRewardAndSendsEligibleReward() {
        RewardPriority.register(handler, plugin);
        Reward first = mock(Reward.class);
        Reward second = mock(Reward.class);
        when(handler.getReward("First")).thenReturn(first);
        when(handler.getReward("Second")).thenReturn(second);
        when(first.canGiveReward(eq(user), any(RewardOptions.class))).thenReturn(false);
        when(second.canGiveReward(eq(user), any(RewardOptions.class))).thenReturn(true);
        when(second.getName()).thenReturn("Second");

        try (MockedConstruction<RewardBuilder> builders = mockConstruction(RewardBuilder.class,
                withSettings().defaultAnswer(Answers.RETURNS_SELF))) {
            String selected = ((RewardInjectStringList) injects.get(0)).onRewardRequest(reward, user,
                    new ArrayList<>(List.of("First", "Second")), placeholders);
            assertEquals("Second", selected);
            assertEquals(1, builders.constructed().size());
            verify(builders.constructed().get(0)).send(user);
        }
    }

    @Test
    public void randomActuallyDispatchesConfiguredRewardWhenChancePasses() {
        RewardRandom.register(handler, plugin);
        MiscUtils misc = mock(MiscUtils.class);
        when(misc.checkChance(anyDouble(), anyDouble())).thenReturn(true);
        ConfigurationSection section = section("Random");
        section.set("Chance", 100.0);
        section.set("PickRandom", true);
        section.set("Rewards", new ArrayList<>(List.of("OnlyReward")));

        try (MockedStatic<MiscUtils> miscStatic = mockStatic(MiscUtils.class)) {
            miscStatic.when(MiscUtils::getInstance).thenReturn(misc);
            configInject(0).onRewardRequested(reward, user, section, placeholders);
        }

        verify(handler).giveReward(eq(user), eq("OnlyReward"), any(RewardOptions.class));
    }

    @Test
    public void luckyActuallySendsPassingLuckyReward() {
        RewardLucky.register(handler, plugin);
        MiscUtils misc = mock(MiscUtils.class);
        when(misc.checkChance(1, 1)).thenReturn(true);
        when(reward.getConfig().getConfigData().getBoolean("OnlyOneLucky", false)).thenReturn(true);
        ConfigurationSection section = section("Lucky");
        section.createSection("1");

        try (MockedStatic<MiscUtils> miscStatic = mockStatic(MiscUtils.class);
                MockedConstruction<RewardBuilder> builders = mockConstruction(RewardBuilder.class,
                        withSettings().defaultAnswer(Answers.RETURNS_SELF))) {
            miscStatic.when(MiscUtils::getInstance).thenReturn(misc);
            configInject(0).onRewardRequested(reward, user, section, placeholders);
            assertEquals(1, builders.constructed().size());
            verify(builders.constructed().get(0)).send(user);
        }
    }

    @Test
    public void specialChanceActuallySendsSelectedBucket() {
        RewardSpecialChance.register(handler, plugin);
        ConfigurationSection section = section("SpecialChance");
        section.createSection("100");

        try (MockedConstruction<RewardBuilder> builders = mockConstruction(RewardBuilder.class,
                withSettings().defaultAnswer(Answers.RETURNS_SELF))) {
            configInject(0).onRewardRequested(reward, user, section, placeholders);
            assertEquals(1, builders.constructed().size());
            verify(builders.constructed().get(0)).send(user);
        }
    }

    @Test
    public void subRewardsActuallyBuildAndSendNestedRewards() {
        RewardSubRewards.register(handler, plugin);
        ConfigurationSection section = section("Rewards");

        try (MockedConstruction<RewardBuilder> builders = mockConstruction(RewardBuilder.class,
                withSettings().defaultAnswer(Answers.RETURNS_SELF))) {
            configInject(0).onRewardRequested(reward, user, section, placeholders);
            assertEquals(1, builders.constructed().size());
            verify(builders.constructed().get(0)).send(user);
        }
    }

    @Test
    public void choicesActuallyQueuesOrDispatchesSelectedChoice() {
        RewardChoices.register(handler, plugin);
        RewardInjectBoolean choices = (RewardInjectBoolean) injects.get(0);

        when(user.getChoicePreference("SourceReward")).thenReturn("");
        choices.onRewardRequest(reward, user, true, placeholders);
        verify(user).addUnClaimedChoiceReward("SourceReward");

        when(user.getChoicePreference("SourceReward")).thenReturn("OptionA");
        choices.onRewardRequest(reward, user, true, placeholders);
        verify(handler).giveChoicesReward(reward, user, "OptionA");
    }

    @Test
    public void javascriptActuallyExecutesScriptsAndTrueBranch() {
        RewardJavascript.register(handler, plugin);

        try (MockedConstruction<JavascriptEngine> engines = mockConstruction(JavascriptEngine.class,
                withSettings().defaultAnswer(Answers.RETURNS_SELF))) {
            ((RewardInjectStringList) injects.get(0)).onRewardRequest(reward, user,
                    new ArrayList<>(List.of("run()")), placeholders);
            assertEquals(1, engines.constructed().size());
            verify(engines.constructed().get(0)).execute("run()");
        }

        ConfigurationSection section = section("Javascript");
        section.set("Enabled", true);
        section.set("Expression", "true");
        List<List<?>> constructorArgs = new ArrayList<>();
        try (MockedConstruction<JavascriptEngine> engines = mockConstruction(JavascriptEngine.class,
                withSettings().defaultAnswer(Answers.RETURNS_SELF),
                (engine, context) -> when(engine.getBooleanValue("true")).thenReturn(true));
                MockedConstruction<RewardBuilder> builders = mockConstruction(RewardBuilder.class,
                        withSettings().defaultAnswer(Answers.RETURNS_SELF),
                        (builder, context) -> constructorArgs.add(context.arguments()))) {
            configInject(1).onRewardRequested(reward, user, section, placeholders);
            assertEquals(1, builders.constructed().size());
            assertEquals("TrueRewards", constructorArgs.get(0).get(1));
            verify(builders.constructed().get(0)).send(user);
        }
    }

    private RewardInjectConfigurationSection configInject(int index) {
        return (RewardInjectConfigurationSection) injects.get(index);
    }

    private ConfigurationSection section(String name) {
        return new YamlConfiguration().createSection(name);
    }

    @SuppressWarnings("unused")
    private RewardInject register(BiConsumer<RewardHandler, AdvancedCorePlugin> registrar) {
        injects.clear();
        registrar.accept(handler, plugin);
        return injects.get(0);
    }
}
