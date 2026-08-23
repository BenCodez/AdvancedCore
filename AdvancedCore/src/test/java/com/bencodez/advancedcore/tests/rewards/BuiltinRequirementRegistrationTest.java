package com.bencodez.advancedcore.tests.rewards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.rewards.RewardHandler;
import com.bencodez.advancedcore.api.rewards.injectedrequirement.RequirementInject;
import com.bencodez.advancedcore.rewards.builtin.requirements.BuiltinRequirements;
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

public class BuiltinRequirementRegistrationTest {

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
    public void chanceRegistersExpectedPaths() {
        assertRequirementPaths(RequirementChance::register, "Chance");
    }

    @Test
    public void dateRegistersExpectedPaths() {
        assertRequirementPaths(RequirementDate::register, "Date");
    }

    @Test
    public void dayOfMonthRegistersExpectedPaths() {
        assertRequirementPaths(RequirementDayOfMonth::register, "DayOfMonth");
    }

    @Test
    public void javascriptRegistersExpectedPaths() {
        assertRequirementPaths(RequirementJavascript::register, "JavascriptExpression");
    }

    @Test
    public void locationDistanceRegistersExpectedPaths() {
        assertRequirementPaths(RequirementLocationDistance::register, "LocationDistance");
    }

    @Test
    public void permissionRegistersExpectedPaths() {
        assertRequirementPaths(RequirementPermission::register, "Permission");
    }

    @Test
    public void rewardExpirationRegistersExpectedPaths() {
        assertRequirementPaths(RequirementRewardExpiration::register, "RewardExpiration");
    }

    @Test
    public void rewardTypeRegistersExpectedPaths() {
        assertRequirementPaths(RequirementRewardType::register, "RewardType");
    }

    @Test
    public void serverRegistersAllowedAndBlockedForms() {
        withMockedServer(() -> assertRequirementPaths(RequirementServer::register, "Server", "BlockedServers"));
    }

    @Test
    public void worldRegistersAllowedAndBlacklistedForms() {
        assertRequirementPaths(RequirementWorld::register, "Worlds", "BlackListedWorlds");
    }

    @Test
    public void builtinCatalogRegistersAllRequirementPathsInHistoricalOrder() {
        withMockedServer(() -> assertRequirementPaths(BuiltinRequirements::register,
                "Chance", "RewardExpiration", "Permission", "DayOfMonth", "Server", "BlockedServers", "Worlds",
                "BlackListedWorlds", "RewardType", "JavascriptExpression", "Date", "LocationDistance"));
    }

    private void assertRequirementPaths(BiConsumer<RewardHandler, AdvancedCorePlugin> registrar,
            String... expectedPaths) {
        int before = rewardHandler.getInjectedRequirements().size();
        registrar.accept(rewardHandler, plugin);

        List<String> actualPaths = rewardHandler.getInjectedRequirements()
                .subList(before, rewardHandler.getInjectedRequirements().size()).stream()
                .map(RequirementInject::getPath).toList();

        assertEquals(List.of(expectedPaths), actualPaths);
    }

    private void withMockedServer(Runnable test) {
        Server server = mock(Server.class);
        when(server.getName()).thenReturn("TestServer");
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getServer).thenReturn(server);
            test.run();
        }
    }
}
