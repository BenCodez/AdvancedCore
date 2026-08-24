package com.bencodez.advancedcore.tests.rewards;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.rewards.RewardExecutor;
import com.bencodez.advancedcore.api.rewards.RewardHandler;
import com.bencodez.advancedcore.api.rewards.RewardOptions;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;

class QueuedGeneratedRewardDispatchTest {

    private AdvancedCorePlugin plugin;
    private RewardHandler handler;
    private AdvancedCoreUser user;
    private RewardExecutor executor;

    @BeforeEach
    void setUp() {
        plugin = mock(AdvancedCorePlugin.class);
        handler = mock(RewardHandler.class);
        user = mock(AdvancedCoreUser.class);
        executor = new RewardExecutor(handler, plugin);
        when(plugin.getLogger()).thenReturn(mock(Logger.class));
        when(user.getUUID()).thenReturn("uuid");
        when(user.getPlayerName()).thenReturn("Ben");
        when(user.isOnline()).thenReturn(true);
    }

    @Test
    void offlineQueueReplayCanResolveGeneratedSnapshot() {
        Reward queued = mock(Reward.class);
        RewardOptions options = new RewardOptions().setOnline(false).setCheckTimed(false);
        when(handler.getQueuedGeneratedReward("QueuedReward", "uuid")).thenReturn(queued);

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
            executor.giveReward(user, "QueuedReward", options);
        }

        verify(handler).getQueuedGeneratedReward("QueuedReward", "uuid");
        verify(queued).giveReward(user, options);
    }

    @Test
    void timedQueueReplayCanResolveGeneratedSnapshot() {
        Reward queued = mock(Reward.class);
        RewardOptions options = new RewardOptions().setCheckTimed(false).addPlaceholder("date", "now");
        when(handler.getQueuedGeneratedReward("QueuedReward", "uuid")).thenReturn(queued);

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
            executor.giveReward(user, "QueuedReward", options);
        }

        verify(handler).getQueuedGeneratedReward("QueuedReward", "uuid");
        verify(queued).giveReward(user, options);
    }

    @Test
    void normalStandaloneDispatchCannotResolveGeneratedSnapshot() {
        RewardOptions options = new RewardOptions();

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
            executor.giveReward(user, "QueuedReward", options);
        }

        verify(handler, never()).getQueuedGeneratedReward("QueuedReward", "uuid");
    }
}
