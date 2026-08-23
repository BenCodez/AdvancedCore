package com.bencodez.advancedcore.tests.rewards;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import com.bencodez.advancedcore.AdvancedCoreConfigOptions;
import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.javascript.JavascriptEngine;
import com.bencodez.advancedcore.api.misc.MiscUtils;
import com.bencodez.advancedcore.api.misc.PlayerManager;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.rewards.RewardHandler;
import com.bencodez.advancedcore.api.rewards.RewardOptions;
import com.bencodez.advancedcore.api.rewards.injectedrequirement.RequirementInject;
import com.bencodez.advancedcore.api.rewards.injectedrequirement.RequirementInjectConfigurationSection;
import com.bencodez.advancedcore.api.rewards.injectedrequirement.RequirementInjectDouble;
import com.bencodez.advancedcore.api.rewards.injectedrequirement.RequirementInjectInt;
import com.bencodez.advancedcore.api.rewards.injectedrequirement.RequirementInjectString;
import com.bencodez.advancedcore.api.rewards.injectedrequirement.RequirementInjectStringList;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.advancedcore.rewards.builtin.requirements.RequirementChance;
import com.bencodez.advancedcore.rewards.builtin.requirements.RequirementDate;
import com.bencodez.advancedcore.rewards.builtin.requirements.RequirementDayOfMonth;
import com.bencodez.advancedcore.rewards.builtin.requirements.RequirementJavascript;
import com.bencodez.advancedcore.rewards.builtin.requirements.RequirementLocationDistance;
import com.bencodez.advancedcore.rewards.builtin.requirements.RequirementPermission;
import com.bencodez.advancedcore.rewards.builtin.requirements.RequirementRewardExpiration;
import com.bencodez.advancedcore.rewards.builtin.requirements.RequirementRewardType;
import com.bencodez.advancedcore.rewards.builtin.requirements.RequirementServer;
import com.bencodez.advancedcore.rewards.builtin.requirements.RequirementWorld;

public class BuiltinRequirementBehaviorTest {

    private AdvancedCorePlugin plugin;
    private AdvancedCoreConfigOptions options;
    private RewardHandler handler;
    private AdvancedCoreUser user;
    private Reward reward;
    private CopyOnWriteArrayList<RequirementInject> requirements;

    @BeforeEach
    public void setUp() {
        plugin = mock(AdvancedCorePlugin.class);
        options = mock(AdvancedCoreConfigOptions.class);
        handler = mock(RewardHandler.class);
        user = mock(AdvancedCoreUser.class);
        reward = mock(Reward.class, Answers.RETURNS_DEEP_STUBS);
        requirements = new CopyOnWriteArrayList<>();

        AdvancedCorePlugin.setInstance(plugin);
        when(plugin.getOptions()).thenReturn(options);
        when(handler.getInjectedRequirements()).thenReturn(requirements);
        when(reward.getName()).thenReturn("SourceReward");
        when(reward.getRewardName()).thenReturn("SourceReward");
    }

    @Test
    public void chanceActuallyHonorsIgnoreFlagAndChanceResult() {
        RequirementChance.register(handler, plugin);
        RequirementInjectDouble chance = (RequirementInjectDouble) requirements.get(0);
        RewardOptions rewardOptions = mock(RewardOptions.class);
        MiscUtils misc = mock(MiscUtils.class);

        when(rewardOptions.isIgnoreChance()).thenReturn(true);
        assertTrue(chance.onRequirementsRequest(reward, user, 1.0, rewardOptions));

        try (MockedStatic<MiscUtils> miscStatic = mockStatic(MiscUtils.class)) {
            miscStatic.when(MiscUtils::getInstance).thenReturn(misc);
            verify(misc, never()).checkChance(1.0, 100);

            when(rewardOptions.isIgnoreChance()).thenReturn(false);
            when(misc.checkChance(25.0, 100)).thenReturn(false);
            assertFalse(chance.onRequirementsRequest(reward, user, 25.0, rewardOptions));
            verify(misc).checkChance(25.0, 100);
        }
    }

    @Test
    public void rewardExpirationActuallyAllowsFreshAndRejectsExpiredTriggers() {
        RequirementRewardExpiration.register(handler, plugin);
        RequirementInjectInt expiration = (RequirementInjectInt) requirements.get(0);
        RewardOptions rewardOptions = mock(RewardOptions.class);

        when(rewardOptions.getOrginalTrigger()).thenReturn(System.currentTimeMillis() - 30_000);
        assertTrue(expiration.onRequirementsRequest(reward, user, 1, rewardOptions));

        when(rewardOptions.getOrginalTrigger()).thenReturn(System.currentTimeMillis() - 120_000);
        assertFalse(expiration.onRequirementsRequest(reward, user, 1, rewardOptions));
    }

    @Test
    public void permissionActuallyChecksAndSupportsReversePermission() {
        RequirementPermission.register(handler, plugin);
        RequirementInjectString permission = (RequirementInjectString) requirements.get(0);
        RewardOptions rewardOptions = new RewardOptions();
        PlayerManager playerManager = mock(PlayerManager.class);
        String uuid = UUID.randomUUID().toString();
        when(user.getUUID()).thenReturn(uuid);
        when(user.getPlayerName()).thenReturn("Ben");
        when(reward.getConfig().getRequirePermission()).thenReturn(true);
        when(playerManager.hasServerPermission(UUID.fromString(uuid), "Ben", "advancedcore.test")).thenReturn(true);

        try (MockedStatic<PlayerManager> managerStatic = mockStatic(PlayerManager.class)) {
            managerStatic.when(PlayerManager::getInstance).thenReturn(playerManager);

            assertTrue(permission.onRequirementsRequest(reward, user, "advancedcore.test", rewardOptions));
            assertFalse(permission.onRequirementsRequest(reward, user, "!advancedcore.test", rewardOptions));
        }
    }

    @Test
    public void dayOfMonthActuallyMatchesCurrentDay() {
        RequirementDayOfMonth.register(handler, plugin);
        RequirementInjectConfigurationSection day = configRequirement(0);
        ConfigurationSection section = section("DayOfMonth");
        section.set("Enabled", true);
        int today = LocalDateTime.now().getDayOfMonth();
        section.set("Days", List.of(today));

        assertTrue(day.onRequirementsRequested(reward, user, section, new RewardOptions()));

        int differentDay = today == 1 ? 2 : 1;
        section.set("Days", List.of(differentDay));
        assertFalse(day.onRequirementsRequested(reward, user, section, new RewardOptions()));
    }

    @Test
    public void serverActuallyAllowsMatchingAndRejectsBlockedServer() {
        Server server = mock(Server.class);
        when(server.getName()).thenReturn("Lobby");
        when(options.getServer()).thenReturn("Lobby");

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getServer).thenReturn(server);
            RequirementServer.register(handler, plugin);
        }

        RequirementInjectString serverRequirement = (RequirementInjectString) requirements.get(0);
        RequirementInjectStringList blocked = (RequirementInjectStringList) requirements.get(1);
        RewardOptions rewardOptions = new RewardOptions();

        assertTrue(serverRequirement.onRequirementsRequest(reward, user, "Lobby", rewardOptions));
        assertFalse(serverRequirement.onRequirementsRequest(reward, user, "Other", rewardOptions));
        assertFalse(blocked.onRequirementsRequest(reward, user, new ArrayList<>(List.of("Lobby")), rewardOptions));
        assertTrue(blocked.onRequirementsRequest(reward, user, new ArrayList<>(List.of("Other")), rewardOptions));
    }

    @Test
    public void worldActuallyAllowsWhitelistAndRejectsBlacklist() {
        RequirementWorld.register(handler, plugin);
        RequirementInjectStringList worlds = (RequirementInjectStringList) requirements.get(0);
        RequirementInjectStringList blacklist = (RequirementInjectStringList) requirements.get(1);
        RewardOptions rewardOptions = new RewardOptions();
        Player player = mock(Player.class);
        World world = mock(World.class);
        when(user.getPlayer()).thenReturn(player);
        when(player.getWorld()).thenReturn(world);
        when(world.getName()).thenReturn("world");

        assertTrue(worlds.onRequirementsRequest(reward, user, new ArrayList<>(List.of("world")), rewardOptions));
        assertFalse(worlds.onRequirementsRequest(reward, user, new ArrayList<>(List.of("other")), rewardOptions));
        verify(user).setCheckWorld(true);

        assertFalse(blacklist.onRequirementsRequest(reward, user, new ArrayList<>(List.of("world")), rewardOptions));
        assertTrue(blacklist.onRequirementsRequest(reward, user, new ArrayList<>(List.of("other")), rewardOptions));
    }

    @Test
    public void rewardTypeActuallyDistinguishesOnlineAndOfflineDelivery() {
        RequirementRewardType.register(handler, plugin);
        RequirementInjectString type = (RequirementInjectString) requirements.get(0);
        RewardOptions rewardOptions = mock(RewardOptions.class);

        when(rewardOptions.isOnline()).thenReturn(true);
        assertTrue(type.onRequirementsRequest(reward, user, "ONLINE", rewardOptions));
        assertFalse(type.onRequirementsRequest(reward, user, "OFFLINE", rewardOptions));

        when(rewardOptions.isOnline()).thenReturn(false);
        assertTrue(type.onRequirementsRequest(reward, user, "OFFLINE", rewardOptions));
        assertFalse(type.onRequirementsRequest(reward, user, "ONLINE", rewardOptions));
        assertTrue(type.onRequirementsRequest(reward, user, "BOTH", rewardOptions));
    }

    @Test
    public void javascriptActuallyUsesExpressionResult() {
        RequirementJavascript.register(handler, plugin);
        RequirementInjectString javascript = (RequirementInjectString) requirements.get(0);
        RewardOptions rewardOptions = new RewardOptions();

        try (MockedConstruction<JavascriptEngine> engines = mockConstruction(JavascriptEngine.class,
                withSettings().defaultAnswer(Answers.RETURNS_SELF),
                (engine, context) -> when(engine.getBooleanValue(anyString())).thenReturn(true))) {
            assertTrue(javascript.onRequirementsRequest(reward, user, "true", rewardOptions));
            assertTrue(engines.constructed().size() == 1);
        }

        try (MockedConstruction<JavascriptEngine> engines = mockConstruction(JavascriptEngine.class,
                withSettings().defaultAnswer(Answers.RETURNS_SELF),
                (engine, context) -> when(engine.getBooleanValue(anyString())).thenReturn(false))) {
            assertFalse(javascript.onRequirementsRequest(reward, user, "false", rewardOptions));
        }
    }

    @Test
    public void dateActuallyMatchesCurrentDateAndRejectsDifferentDate() {
        RequirementDate.register(handler, plugin);
        RequirementInjectConfigurationSection date = configRequirement(0);
        LocalDateTime now = LocalDateTime.now();
        ConfigurationSection section = section("Date");
        section.set("WeekDay", now.getDayOfWeek().name());
        section.set("DayOfMonth", now.getDayOfMonth());
        section.set("Month", now.getMonth().name());

        assertTrue(date.onRequirementsRequested(reward, user, section, new RewardOptions()));

        section.set("WeekDay", now.getDayOfWeek().plus(1).name());
        assertFalse(date.onRequirementsRequested(reward, user, section, new RewardOptions()));
    }

    @Test
    public void locationDistanceActuallyChecksWorldAndDistance() {
        RequirementLocationDistance.register(handler, plugin);
        RequirementInjectConfigurationSection distance = configRequirement(0);
        World world = mock(World.class);
        Player player = mock(Player.class);
        when(world.getName()).thenReturn("world");
        when(user.isOnline()).thenReturn(true);
        when(user.getPlayer()).thenReturn(player);
        when(player.getLocation()).thenReturn(new Location(world, 3, 0, 0));

        ConfigurationSection section = section("LocationDistance");
        section.set("World", "world");
        section.set("X", 0);
        section.set("Y", 0);
        section.set("Z", 0);
        section.set("Distance", 5);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);
            assertTrue(distance.onRequirementsRequested(reward, user, section, new RewardOptions()));

            section.set("Distance", 2);
            assertFalse(distance.onRequirementsRequested(reward, user, section, new RewardOptions()));
        }
    }

    private RequirementInjectConfigurationSection configRequirement(int index) {
        return (RequirementInjectConfigurationSection) requirements.get(index);
    }

    private ConfigurationSection section(String name) {
        return new YamlConfiguration().createSection(name);
    }
}
