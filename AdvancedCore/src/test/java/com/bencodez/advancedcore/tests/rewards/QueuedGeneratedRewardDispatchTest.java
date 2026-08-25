package com.bencodez.advancedcore.api.rewards;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;

class QueuedGeneratedRewardDispatchTest {

    private static final String QUEUE_PREFIX = "\\AdvancedCoreQueue/1/";

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
        when(plugin.getRewardHandler()).thenReturn(handler);
        when(user.getPlugin()).thenReturn(plugin);
        when(user.getUUID()).thenReturn("uuid");
        when(user.getPlayerName()).thenReturn("Ben");
        when(user.isOnline()).thenReturn(true);
    }

    private String queuedReference(String rewardName, boolean snapshot) {
        String encodedName = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(rewardName.getBytes(StandardCharsets.UTF_8));
        return QUEUE_PREFIX + (snapshot ? "snapshot/" : "normal/") + encodedName;
    }

    @Test
    void offlineQueueReplayResolvesGeneratedSnapshotBeforeNormalFallback() {
        Reward queued = mock(Reward.class);
        Reward fallback = mock(Reward.class);
        RewardOptions options = new RewardOptions().setOnline(false).setCheckTimed(false);
        when(handler.getQueuedGeneratedReward("QueuedReward", "uuid")).thenReturn(queued);
        when(handler.getReward("QueuedReward")).thenReturn(fallback);

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
            executor.givePersistedQueueReward(user, "QueuedReward", options);
        }

        verify(handler).getQueuedGeneratedReward("QueuedReward", "uuid");
        verify(handler, never()).getReward("QueuedReward");
        verify(queued).giveReward(user, options);
        verify(fallback, never()).giveReward(user, options);
    }

    @Test
    void timedQueueReplayCanResolveGeneratedSnapshot() {
        Reward queued = mock(Reward.class);
        RewardOptions options = new RewardOptions().setCheckTimed(false).addPlaceholder("date", "now");
        when(handler.getQueuedGeneratedReward("QueuedReward", "uuid")).thenReturn(queued);

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
            executor.givePersistedQueueReward(user, "QueuedReward", options);
        }

        verify(handler).getQueuedGeneratedReward("QueuedReward", "uuid");
        verify(queued).giveReward(user, options);
    }

    @Test
    void rewardBuilderUsesAlreadyResolvedQueueRewardWithoutReresolving() {
        Reward resolved = mock(Reward.class);
        when(resolved.getRewardName()).thenReturn("QueuedReward");

        RewardBuilder builder = new RewardBuilder(resolved).setCheckTimed(false).withPlaceHolder("date", "now");
        builder.send(user);

        verify(handler, never()).getQueuedGeneratedReward("QueuedReward", "uuid");
        verify(handler).giveReward(user, resolved, builder.getRewardOptions());
    }

    @Test
    void explicitNormalQueueReferenceDoesNotUseStaleSnapshot() {
        Reward normal = mock(Reward.class);
        Reward stale = mock(Reward.class);
        RewardOptions options = new RewardOptions().setOnline(false).setCheckTimed(false);
        when(handler.getReward("Daily")).thenReturn(normal);
        when(handler.getQueuedGeneratedReward("Daily", "uuid")).thenReturn(stale);

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
            executor.givePersistedQueueReward(user, queuedReference("Daily", false), options);
        }

        verify(normal).giveReward(user, options);
        verify(handler, never()).getQueuedGeneratedReward("Daily", "uuid");
    }

    @Test
    void explicitSnapshotQueueReferenceUsesGeneratedSnapshot() {
        Reward queued = mock(Reward.class);
        Reward normal = mock(Reward.class);
        RewardOptions options = new RewardOptions().setOnline(false).setCheckTimed(false);
        when(handler.getQueuedGeneratedReward("Daily", "uuid")).thenReturn(queued);
        when(handler.getReward("Daily")).thenReturn(normal);

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
            executor.givePersistedQueueReward(user, queuedReference("Daily", true), options);
        }

        verify(queued).giveReward(user, options);
        verify(handler, never()).getReward("Daily");
    }

    @Test
    void legacyQueueReferencePrefersRegisteredRewardOverStaleSnapshot() {
        Reward normal = mock(Reward.class);
        Reward stale = mock(Reward.class);
        RewardOptions options = new RewardOptions().setOnline(false).setCheckTimed(false);
        when(handler.rewardExist("Daily")).thenReturn(true);
        when(handler.getReward("Daily")).thenReturn(normal);
        when(handler.getQueuedGeneratedReward("Daily", "uuid")).thenReturn(stale);

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
            executor.givePersistedQueueReward(user, "Daily", options);
        }

        verify(normal).giveReward(user, options);
        verify(handler, never()).getQueuedGeneratedReward("Daily", "uuid");
    }

    @Test
    void legacyQueueNameEndingInOldMarkerIsPreservedExactly() {
        String rewardName = "Promo%generatedsnapshot%true";
        Reward normal = mock(Reward.class);
        RewardOptions options = new RewardOptions().setOnline(false).setCheckTimed(false);
        when(handler.rewardExist(rewardName)).thenReturn(true);
        when(handler.getReward(rewardName)).thenReturn(normal);

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
            executor.givePersistedQueueReward(user, rewardName, options);
        }

        verify(handler).getReward(rewardName);
        verify(normal).giveReward(user, options);
        verify(handler, never()).getQueuedGeneratedReward("Promo", "uuid");
        verify(handler, never()).getReward("Promo");
    }

    @Test
    void normalDispatchPreservesRewardNameContainingSnapshotMarkerText() {
        String rewardName = "Promo%generatedsnapshot%Bonus";
        Reward normal = mock(Reward.class);
        RewardOptions options = new RewardOptions();
        when(handler.getReward(rewardName)).thenReturn(normal);

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
            executor.giveReward(user, rewardName, options);
        }

        verify(handler).getReward(rewardName);
        verify(normal).giveReward(user, options);
        verify(handler, never()).getReward("Promo");
        verify(handler, never()).getQueuedGeneratedReward("Promo", "uuid");
    }

    @Test
    void forgedOfflineOptionsCannotGrantQueueSnapshotAccess() {
        Reward normal = mock(Reward.class);
        Reward queued = mock(Reward.class);
        RewardOptions options = new RewardOptions().setOnline(false).setCheckTimed(false);
        when(handler.getReward("QueuedReward")).thenReturn(normal);
        when(handler.getQueuedGeneratedReward("QueuedReward", "uuid")).thenReturn(queued);

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
            executor.giveReward(user, "QueuedReward", options);
        }

        verify(handler).getReward("QueuedReward");
        verify(handler, never()).getQueuedGeneratedReward("QueuedReward", "uuid");
        verify(normal).giveReward(user, options);
        verify(queued, never()).giveReward(user, options);
    }

    @Test
    void forgedTimedPlaceholderCannotGrantQueueSnapshotAccess() {
        Reward normal = mock(Reward.class);
        Reward queued = mock(Reward.class);
        RewardOptions options = new RewardOptions().setCheckTimed(false).addPlaceholder("date", "now");
        when(handler.getReward("QueuedReward")).thenReturn(normal);
        when(handler.getQueuedGeneratedReward("QueuedReward", "uuid")).thenReturn(queued);

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
            executor.giveReward(user, "QueuedReward", options);
        }

        verify(handler).getReward("QueuedReward");
        verify(handler, never()).getQueuedGeneratedReward("QueuedReward", "uuid");
        verify(normal).giveReward(user, options);
        verify(queued, never()).giveReward(user, options);
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
