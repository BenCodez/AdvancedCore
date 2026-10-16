package com.bencodez.advancedcore.api.rewards;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.bencodez.advancedcore.AdvancedCorePlugin;

/** Bounded discovery after hint saturation; it never stores reward payloads. */
final class RewardEligibilityOverflow {
    static final int BATCH = 128;
    private final AdvancedCorePlugin plugin;
    private final RewardEligibility eligibility;
    private final LongSupplier clock;
    private final IdentityHashMap<Player, CompletableFuture<Void>> active = new IdentityHashMap<>();
    private List<Player> snapshot = List.of();
    private int position;
    private volatile boolean enabled;
    private boolean closed;
    private boolean scanning;
    private boolean reported;
    private long delay = TimeUnit.SECONDS.toNanos(10);
    private long nextSweep;

    RewardEligibilityOverflow(AdvancedCorePlugin plugin, RewardEligibility eligibility, LongSupplier clock) {
        this.plugin = plugin;
        this.eligibility = eligibility;
        this.clock = clock;
    }

    synchronized void enable() {
        if (!closed && !enabled) {
            enabled = true;
            nextSweep = clock.getAsLong() + delay;
        }
    }

    boolean enabled() { return enabled; }

    CompletionStage<Void> signal(Player player) {
        CompletableFuture<Void> result;
        synchronized (this) {
            if (closed || !enabled) return CompletableFuture.completedFuture(null);
            result = active.get(player);
            if (result != null) return result;
            // Admission exhaustion is retried by rotating discovery, not forgotten.
            if (active.size() >= BATCH) return CompletableFuture.completedFuture(null);
            result = new CompletableFuture<>();
            active.put(player, result);
        }
        CompletableFuture<Void> captured = result;
        result.whenComplete((ignored, failure) -> {
            synchronized (this) { active.remove(player, captured); }
            if (failure != null) reportFailure();
        });
        try {
            Reward.continueOnPlayerThread(plugin, player, () -> {
                if (!live() || !player.isOnline() || !eligibility.acceptedSession(player))
                    return CompletableFuture.completedFuture(null);
                String name = player.getName();
                CompletableFuture<com.bencodez.advancedcore.api.user.AdvancedCoreUser> user = new CompletableFuture<>();
                // Name-based resolution preserves the configured offline-mode identity.
                plugin.getTimer().execute(() -> {
                    try {
                        if (!live()) { user.complete(null); return; }
                        plugin.getUserManager().getUserAsync(name, user::complete, user::completeExceptionally);
                    } catch (RuntimeException failure) { user.completeExceptionally(failure); }
                });
                return user.thenCompose(loaded -> Reward.continueOnPlayerThread(plugin, player, () -> {
                    if (!live() || loaded == null || loaded.getPlayer() != player || !player.isOnline()
                            || !eligibility.acceptedSession(player)) return CompletableFuture.completedFuture(null);
                    return eligibility.signalOverflow(loaded, player);
                }));
            }).whenComplete((ignored, failure) -> {
                if (failure == null) captured.complete(null); else captured.completeExceptionally(failure);
            });
        } catch (RuntimeException failure) { captured.completeExceptionally(failure); }
        return result;
    }

    void sweep() {
        synchronized (this) {
            if (closed || !enabled || scanning || clock.getAsLong() - nextSweep < 0) return;
            scanning = true;
            nextSweep = clock.getAsLong() + delay;
            delay = Math.min(TimeUnit.MINUTES.toNanos(5), delay * 2);
        }
        try {
            plugin.getBukkitScheduler().executeOrScheduleSync(plugin, () -> {
                try {
                    if (!live()) { finishScan(); return; }
                    // Hold one online-population snapshot across passes. New arrivals
                    // cannot continually displace older members before their turn.
                    List<Player> fresh = null;
                    synchronized (this) { if (position == snapshot.size()) fresh = new ArrayList<>(); }
                    if (fresh != null) fresh.addAll(Bukkit.getOnlinePlayers());
                    List<Player> batch;
                    synchronized (this) {
                        if (closed) { scanning = false; return; }
                        if (fresh != null) { snapshot = fresh; position = 0; }
                        int end = Math.min(snapshot.size(), position + BATCH);
                        batch = new ArrayList<>(snapshot.subList(position, end));
                        position = end;
                        if (position == snapshot.size()) { snapshot = List.of(); position = 0; }
                    }
                    // Each signal has its own bounded physical admission. A pending
                    // resolution/replay consumes that slot, not the entire scan lane.
                    for (Player player : batch) signal(player);
                    finishScan();
                } catch (RuntimeException failure) { reportFailure(); finishScan(); }
            });
        } catch (RuntimeException failure) { reportFailure(); finishScan(); }
    }

    private boolean live() {
        synchronized (this) { if (closed) return false; }
        return plugin.isEnabled() && eligibility.isOpen() && plugin.getRewardHandler() != null
                && plugin.getRewardHandler().getRewardEligibility() == eligibility;
    }

    private synchronized void finishScan() { scanning = false; }

    private void reportFailure() {
        synchronized (this) {
            if (closed || reported) return;
            reported = true;
        }
        plugin.debug("An overflow reward eligibility check could not complete; durable work remains pending");
    }

    void close() {
        List<CompletableFuture<Void>> running;
        synchronized (this) {
            closed = true;
            snapshot = List.of();
            running = new ArrayList<>(active.values());
            active.clear();
        }
        for (var result : running) result.completeExceptionally(new IllegalStateException("Reward eligibility closed"));
    }
}
