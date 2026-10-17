package com.bencodez.advancedcore.tests.listeners;

import com.bencodez.advancedcore.api.rewards.*;
import com.bencodez.advancedcore.listeners.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.rewards.RewardEligibility;
import com.bencodez.advancedcore.api.rewards.RewardHandler;

class RewardGamemodeListenerTest {
    @Test
    void cancelledChangeIsIgnoredByTheEventContract() throws Exception {
        var annotation = RewardEligibilityListener.class.getDeclaredMethod("onGameModeChange",
                PlayerGameModeChangeEvent.class).getAnnotation(org.bukkit.event.EventHandler.class);
        org.junit.jupiter.api.Assertions.assertTrue(annotation.ignoreCancelled());
    }

    @Test
    void cancelledChangeDoesNotScheduleAnotherCheck() {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        when(plugin.isEnabled()).thenReturn(true);
        var event = new PlayerGameModeChangeEvent(mock(Player.class), GameMode.CREATIVE);
        event.setCancelled(true);
        new RewardEligibilityListener(plugin).onGameModeChange(event);
        verify(plugin, never()).getBukkitScheduler();
        verify(plugin, never()).getRewardHandler();
    }

    @Test
    void transitionWakeupRunsAfterCommitOnPlayerScheduler() {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        when(plugin.isEnabled()).thenReturn(true);
        RewardHandler handler = mock(RewardHandler.class);
        when(plugin.getRewardHandler()).thenReturn(handler);
        RewardEligibility eligibility = mock(RewardEligibility.class);
        when(handler.getRewardEligibility()).thenReturn(eligibility);
        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        var scheduler = mock(com.bencodez.simpleapi.scheduler.BukkitScheduler.class);
        when(plugin.getBukkitScheduler()).thenReturn(scheduler);
        RewardEligibilityListener listener = new RewardEligibilityListener(plugin);
        listener.onGameModeChange(new PlayerGameModeChangeEvent(player, GameMode.CREATIVE));
        var callback = org.mockito.ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTaskLater(eq(plugin), callback.capture(), eq(1L), eq(player));
        verifyNoInteractions(eligibility);
        callback.getValue().run();
        verify(eligibility).signal(player);
    }
    @Test
    void cancelledResolvedLoginDoesNotWakeQueues() {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        when(plugin.isEnabled()).thenReturn(true);
        var event = new AdvancedCoreLoginEvent(mock(com.bencodez.advancedcore.api.user.AdvancedCoreUser.class),
                "synthetic-logical-user", mock(Player.class));
        event.setCancelled(true);
        new RewardEligibilityListener(plugin).onLogin(event);
        verify(plugin, never()).getRewardHandler();
    }

}
