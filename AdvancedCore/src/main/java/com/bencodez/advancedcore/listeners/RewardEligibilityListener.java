package com.bencodez.advancedcore.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.bencodez.advancedcore.AdvancedCorePlugin;

/** Wakes existing deferred reward occurrences after player-context transitions. */
public final class RewardEligibilityListener implements Listener {
    private final AdvancedCorePlugin plugin;
    public RewardEligibilityListener(AdvancedCorePlugin plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        if (!plugin.isEnabled() || event.isCancelled()) return;
        var player = event.getPlayer();
        plugin.getBukkitScheduler().runTaskLater(plugin, () -> {
            if (plugin.isEnabled() && player.isOnline())
                plugin.getRewardHandler().getRewardEligibility().signal(player);
        }, 1, player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        if (plugin.isEnabled() && !plugin.getOptions().isDisableCheckOnWorldChange()) {
            plugin.getRewardHandler().getRewardEligibility().signal(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLogin(AdvancedCoreLoginEvent event) {
        if (plugin.isEnabled() && !event.isCancelled() && event.getUser() != null && event.getPlayer() != null) {
            plugin.getRewardHandler().getRewardEligibility().signalLogin(event.getUser(), event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.getRewardHandler().getRewardEligibility().disconnected(event.getPlayer());
    }
}
