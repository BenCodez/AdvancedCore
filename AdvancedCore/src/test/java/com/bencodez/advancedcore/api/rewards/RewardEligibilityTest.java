package com.bencodez.advancedcore.api.rewards;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import org.bukkit.GameMode;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;

class RewardEligibilityTest {
    @Test
    void allowlistAndDenylistUseTheOnlinePlayerMode() {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        AdvancedCoreUser user = mock(AdvancedCoreUser.class);
        Player player = mock(Player.class);
        when(user.getPlugin()).thenReturn(plugin);
        when(user.getPlayer()).thenReturn(player);
        when(player.isOnline()).thenReturn(true);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        RewardEligibility eligibility = new RewardEligibility(plugin, mock(RewardHandler.class));
        YamlConfiguration data = new YamlConfiguration();
        data.set("GameModes", List.of("SURVIVAL"));
        assertTrue(eligibility.isEligible(data, user));
        data.set("BlackListedGameModes", List.of("survival"));
        assertFalse(eligibility.isEligible(data, user));
    }

    @Test
    void missingConfigurationAndMalformedValuesAreHandledSafely() {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        AdvancedCoreUser user = mock(AdvancedCoreUser.class);
        RewardEligibility eligibility = new RewardEligibility(plugin, mock(RewardHandler.class));
        YamlConfiguration data = new YamlConfiguration();
        assertTrue(eligibility.isEligible(data, user));
        data.set("GameModes", List.of("UNKNOWN"));
        assertFalse(eligibility.isEligible(data, user));
        data.set("GameModes", "SURVIVAL");
        assertFalse(eligibility.isEligible(data, user));
        verify(user, never()).getPlayer();
    }

    @Test
    void ownerSignalDoesNotWaitForStorageOwnershipMonitor() throws Exception {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        AdvancedCoreUser user = mock(AdvancedCoreUser.class);
        RewardHandler handler = mock(RewardHandler.class);
        var storage = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
        var owner = java.util.concurrent.Executors.newSingleThreadExecutor();
        when(plugin.isEnabled()).thenReturn(true);
        when(plugin.getTimer()).thenReturn(storage);
        when(handler.getDelayedTimer()).thenReturn(storage);
        when(user.getPlugin()).thenReturn(plugin);
        when(user.getUUID()).thenReturn("synthetic-user");
        when(user.hasPendingRewardReplay()).thenAnswer(call -> {
            synchronized (plugin) { return false; }
        });
        when(user.checkOfflineRewardsAsync()).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));
        RewardEligibility eligibility = new RewardEligibility(plugin, handler);
        try {
            java.util.concurrent.CompletionStage<Void> completion;
            synchronized (plugin) {
                completion = owner.submit(() -> eligibility.signal(user)).get(1, java.util.concurrent.TimeUnit.SECONDS);
                assertFalse(completion.toCompletableFuture().isDone(),
                        "Ownership observation must wait on the worker while the player owner returns");
            }
            completion.toCompletableFuture().get(2, java.util.concurrent.TimeUnit.SECONDS);
        } finally {
            eligibility.close();
            owner.shutdownNow();
            storage.shutdownNow();
        }
    }

    @Test
    void rejectedWorkerCompletesSignalWithoutTouchingStorageOnCaller() {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        AdvancedCoreUser user = mock(AdvancedCoreUser.class);
        RewardHandler handler = mock(RewardHandler.class);
        var worker = mock(java.util.concurrent.ScheduledExecutorService.class);
        when(plugin.isEnabled()).thenReturn(true);
        when(plugin.getTimer()).thenReturn(worker);
        when(handler.getDelayedTimer()).thenReturn(worker);
        when(user.getPlugin()).thenReturn(plugin);
        when(user.getUUID()).thenReturn("synthetic-rejected-user");
        doThrow(new java.util.concurrent.RejectedExecutionException()).when(worker).execute(any());
        RewardEligibility eligibility = new RewardEligibility(plugin, handler);
        try {
            assertTrue(eligibility.signal(user).toCompletableFuture().isCompletedExceptionally());
            verify(user, never()).hasPendingRewardReplay();
            verify(user, never()).checkOfflineRewardsAsync();
        } finally { eligibility.close(); }
    }

    @Test
    void closedSignalCannotResumeItsDiscardedWorkerCallback() {
        AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
        AdvancedCoreUser user = mock(AdvancedCoreUser.class);
        RewardHandler handler = mock(RewardHandler.class);
        var worker = mock(java.util.concurrent.ScheduledExecutorService.class);
        var queued = new java.util.concurrent.atomic.AtomicReference<Runnable>();
        when(plugin.isEnabled()).thenReturn(true);
        when(plugin.getTimer()).thenReturn(worker);
        when(handler.getDelayedTimer()).thenReturn(worker);
        when(user.getPlugin()).thenReturn(plugin);
        when(user.getUUID()).thenReturn("synthetic-closed-user");
        doAnswer(call -> { queued.set(call.getArgument(0)); return null; }).when(worker).execute(any());
        RewardEligibility eligibility = new RewardEligibility(plugin, handler);
        var completion = eligibility.signal(user).toCompletableFuture();
        assertFalse(completion.isDone());
        eligibility.close();
        assertTrue(completion.isCompletedExceptionally());
        queued.get().run();
        verify(user, never()).hasPendingRewardReplay();
        verify(user, never()).checkOfflineRewardsAsync();
    }
}
