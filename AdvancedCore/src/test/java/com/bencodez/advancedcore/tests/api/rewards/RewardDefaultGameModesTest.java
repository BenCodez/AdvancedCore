package com.bencodez.advancedcore.tests.api.rewards;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.bukkit.GameMode;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import com.bencodez.advancedcore.AdvancedCoreConfigOptions;
import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.rewards.RewardHandler;
import com.bencodez.advancedcore.api.rewards.RewardEligibility;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.simpleapi.file.YMLConfig;

class RewardDefaultGameModesTest {
    @Test void annotatedOptionsLoadDefaultsAndKeepTheEmptyCompatibilityDefault() {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        AdvancedCoreConfigOptions options = new AdvancedCoreConfigOptions();
        assertTrue(options.getDefaultRewardGameModes().isEmpty());
        YamlConfiguration data = new YamlConfiguration();
        data.set("DefaultRewardGameModes", List.of("SURVIVAL", "ADVENTURE"));
        YMLConfig config = mock(YMLConfig.class);
        when(config.getData()).thenReturn(data);
        options.setYmlConfig(config);
        options.load(plugin);
        assertEquals(List.of("SURVIVAL", "ADVENTURE"), options.getDefaultRewardGameModes());
        data.set("DefaultRewardGameModes", null);
        options.load(plugin);
        assertTrue(options.getDefaultRewardGameModes().isEmpty(), "Removing a default must clear the loaded value");
    }

    @Test void absentOrEmptyDefaultsKeepLegacyBehavior() {
        Fixture f = new Fixture();
        assertTrue(f.eligibility.isEligible(f.reward, f.user));
        f.defaults.set("DefaultRewardGameModes", List.of());
        assertTrue(f.eligibility.isEligible(f.reward, f.user));
        verify(f.user, never()).getPlayer();
    }

    @Test void globalAllowlistUsesCurrentNativeMode() {
        Fixture f = new Fixture();
        f.defaults.set("DefaultRewardGameModes", List.of("survival"));
        assertFalse(f.eligibility.isEligible(f.reward, f.user));
        when(f.player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        assertTrue(f.eligibility.isEligible(f.reward, f.user));
    }

    @Test void explicitRewardAllowlistOverridesDefault() {
        Fixture f = new Fixture();
        f.defaults.set("DefaultRewardGameModes", List.of("SURVIVAL"));
        f.reward.set("GameModes", List.of("CREATIVE"));
        assertTrue(f.eligibility.isEligible(f.reward, f.user));
    }

    @Test void explicitEmptyRewardAllowlistOptsOutButDenylistStillWins() {
        Fixture f = new Fixture();
        f.defaults.set("DefaultRewardGameModes", List.of("SURVIVAL"));
        f.reward.set("GameModes", List.of());
        assertTrue(f.eligibility.isEligible(f.reward, f.user));
        f.reward.set("BlackListedGameModes", List.of("CREATIVE"));
        assertFalse(f.eligibility.isEligible(f.reward, f.user));
    }

    @Test void rewardDenylistCombinesWithDefaultAllowlist() {
        Fixture f = new Fixture();
        f.defaults.set("DefaultRewardGameModes", List.of("SURVIVAL", "CREATIVE"));
        f.reward.set("BlackListedGameModes", List.of("CREATIVE"));
        assertFalse(f.eligibility.isEligible(f.reward, f.user));
        when(f.player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        assertTrue(f.eligibility.isEligible(f.reward, f.user));
    }

    @Test void invalidGlobalShapeNamesAndMembersFailClosedWithoutNativeReads() {
        Fixture f = new Fixture();
        for (Object invalid : List.of("SURVIVAL", List.of("UNKNOWN"), List.of(1),
                List.of("SURVIVAL", "CREATIVE", "ADVENTURE", "SPECTATOR", "SURVIVAL"))) {
            f.defaults.set("DefaultRewardGameModes", invalid);
            assertFalse(f.eligibility.isEligible(f.reward, f.user), "Invalid default: " + invalid);
        }
        verify(f.user, never()).getPlayer();
    }

    @Test void explicitValidRewardRulesOverrideMalformedDefault() {
        Fixture f = new Fixture();
        f.defaults.set("DefaultRewardGameModes", "invalid");
        f.reward.set("GameModes", List.of("CREATIVE"));
        assertTrue(f.eligibility.isEligible(f.reward, f.user));
    }

    @Test void changedSavedDefaultsAreObservedWithoutMutatingRewardDefinition() {
        Fixture f = new Fixture();
        f.defaults.set("DefaultRewardGameModes", List.of("SURVIVAL"));
        assertFalse(f.eligibility.isEligible(f.reward, f.user));
        f.defaults.set("DefaultRewardGameModes", List.of("CREATIVE"));
        assertTrue(f.eligibility.isEligible(f.reward, f.user));
        f.defaults.set("DefaultRewardGameModes", null);
        assertTrue(f.eligibility.isEligible(f.reward, f.user));
        assertFalse(f.reward.contains("GameModes", true));
    }

    private static final class Fixture {
        final AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        final AdvancedCoreUser user = mock(AdvancedCoreUser.class);
        final Player player = mock(Player.class);
        final YamlConfiguration defaults = new YamlConfiguration(), reward = new YamlConfiguration();
        final RewardEligibility eligibility;
        Fixture() {
            AdvancedCoreConfigOptions options = new AdvancedCoreConfigOptions();
            YMLConfig config = mock(YMLConfig.class);
            when(config.getData()).thenReturn(defaults);
            options.setYmlConfig(config);
            when(plugin.getOptions()).thenReturn(options);
            when(user.getPlugin()).thenReturn(plugin);
            when(user.getPlayer()).thenReturn(player);
            when(player.isOnline()).thenReturn(true);
            when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
            eligibility = EligibilityAccess.create(plugin, mock(RewardHandler.class));
        }
    }
}
