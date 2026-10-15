package com.bencodez.advancedcore.api.rewards;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.bukkit.GameMode;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.simpleapi.folialib.enums.EntityTaskResult;

/** Owns optional eligibility observations and bounded replay wake-ups, never reward payloads. */
public final class RewardEligibility {
    public static final int MAX_PENDING_PLAYERS = 4096;
    private static final int FALLBACK_BATCH = 128;
    private final AdvancedCorePlugin plugin;
    private final RewardHandler handler;
    private final LinkedHashMap<String, Pending> pending = new LinkedHashMap<>();
    private ScheduledFuture<?> fallback;
    private boolean closed;

    RewardEligibility(AdvancedCorePlugin plugin, RewardHandler handler) {
        this.plugin = plugin;
        this.handler = handler;
    }

    /**
     * Schedules an opted-in player action with explicit Folia retirement handling.
     * Callers share their action claim with both terminal callbacks so a late
     * task cannot execute after its unstarted work has been retained for retry.
     */
    public static void runPlayerTask(AdvancedCorePlugin plugin, Player player, Runnable action,
            Runnable retired, Consumer<Throwable> rejected) {
        var scheduler = plugin.getBukkitScheduler();
        var folia = scheduler.getFoliaLib();
        if (folia == null || !folia.isFolia()) {
            scheduler.runTask(plugin, action, player);
            return;
        }
        var result = folia.getImpl().runAtEntityWithFallback(player, ignored -> action.run(), retired);
        if (result == null) throw new IllegalStateException("Entity scheduler returned no admission result");
        result.whenComplete((status, failure) -> {
            if (failure != null) rejected.accept(failure);
            else if (status == EntityTaskResult.ENTITY_RETIRED || status == EntityTaskResult.SCHEDULER_RETIRED) {
                retired.run();
            } else if (status != EntityTaskResult.SUCCESS) {
                rejected.accept(new IllegalStateException("Entity scheduler returned an unknown admission result"));
            }
        });
    }

    /** Retirement proves that the claimed player action did not begin. */
    public static RuntimeException retiredPlayerActionFailure(AdvancedCorePlugin plugin) {
        return AdvancedCoreUser.replayActionNotStarted("Player scheduler retired before reward action dispatch",
                plugin.isEnabled() ? Reward.playerEligibilityDeferred() : null);
    }

    /** Whether this definition opts into any live eligibility requirement. */
    public static boolean isConfigured(ConfigurationSection data) {
        return data.contains("GameModes", true) || data.contains("BlackListedGameModes", true);
    }

    /** Fast deterministic predicate; callers must already hold the player owner. */
    public boolean isEligible(ConfigurationSection data, AdvancedCoreUser user) {
        if (!isConfigured(data)) return true;
        synchronized (this) { if (closed) return false; }
        if (!validModes(data, "GameModes") || !validModes(data, "BlackListedGameModes")) return false;
        List<String> allowed = data.getStringList("GameModes");
        List<String> denied = data.getStringList("BlackListedGameModes");
        if (allowed.isEmpty() && denied.isEmpty()) return true;
        Player player = user.getPlayer();
        if (player == null || !player.isOnline()) return false;
        GameMode mode = player.getGameMode();
        if ((!allowed.isEmpty() && !containsMode(allowed, mode)) || containsMode(denied, mode)) return false;
        return true;
    }

    public static boolean validModes(ConfigurationSection data, String path) {
        if (!data.contains(path, true)) return true;
        if (!data.isList(path)) return false;
        List<?> values = data.getList(path);
        if (values == null || values.size() > GameMode.values().length) return false;
        for (Object value : values) {
            if (!(value instanceof String name)) return false;
            try { GameMode.valueOf(name.trim().toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException failure) { return false; }
        }
        return true;
    }

    private static boolean containsMode(List<String> modes, GameMode mode) {
        for (String candidate : modes) if (mode.name().equalsIgnoreCase(candidate.trim())) return true;
        return false;
    }

    /** Records only a wake-up hint. The existing persistent queue remains authoritative. Owner-thread only. */
    void deferred(AdvancedCoreUser user) {
        Player player = user.getPlayer();
        if (player == null || !player.isOnline()) return;
        synchronized (this) {
            if (closed) return;
            Pending current = pending.get(user.getUUID());
            if (current == null) {
                if (pending.size() >= MAX_PENDING_PLAYERS) return;
                current = new Pending(user, player);
                pending.put(user.getUUID(), current);
            }
            current.user = user;
            current.player = player;
            current.revision++;
            startFallback();
        }
    }

    /** Publication can race a signal that observed an empty queue; renew the wake-up afterwards. */
    synchronized void queued(AdvancedCoreUser user) {
        if (closed) return;
        Pending current = pending.get(user.getUUID());
        if (current == null) {
            if (pending.size() >= MAX_PENDING_PLAYERS) return;
            current = new Pending(user, null);
            pending.put(user.getUUID(), current);
        }
        current.revision++;
        startFallback();
    }

    /**
     * Rechecks the existing offline queue and completes after its claimed occurrences
     * finish or defer again. Concurrent signals for one logical user share one stage.
     * This method accepts the consumer's user identity, including offline-mode identities.
     */
    public CompletionStage<Void> signal(AdvancedCoreUser user) {
        Objects.requireNonNull(user, "user");
        if (user.getPlugin() != plugin) return CompletableFuture.failedFuture(
                new IllegalArgumentException("User belongs to another plugin"));
        Pending current;
        long revision;
        synchronized (this) {
            if (closed || !plugin.isEnabled()) return CompletableFuture.failedFuture(
                    new IllegalStateException("Reward eligibility is closed"));
            current = pending.get(user.getUUID());
            if (current == null) {
                if (pending.size() >= MAX_PENDING_PLAYERS) return CompletableFuture.failedFuture(
                        new IllegalStateException("Too many pending reward eligibility checks"));
                current = new Pending(user, null);
                pending.put(user.getUUID(), current);
            }
            if (current.running != null) return current.running;
            current.running = new CompletableFuture<>();
            revision = current.revision;
            startFallback();
        }
        Pending captured = current;
        CompletableFuture<Void> result = current.running;
        // Replay claims use the storage monitor. Observe them on the worker,
        // including when completion originates from a player-owned callback.
        try {
            plugin.getTimer().execute(() -> runSignalCheck(user, captured, revision, result));
        } catch (Throwable failure) {
            finishSignal(user, captured, revision, result, false, failure);
        }
        return result;
    }

    private void runSignalCheck(AdvancedCoreUser user, Pending captured, long revision,
            CompletableFuture<Void> result) {
        synchronized (this) {
            if (closed || pending.get(user.getUUID()) != captured || captured.running != result) return;
        }
        final boolean claimedBeforeCheck;
        final CompletionStage<Void> check;
        try {
            claimedBeforeCheck = user.hasPendingRewardReplay();
            check = user.checkOfflineRewardsAsync();
            if (check == null) throw new IllegalStateException("Offline replay returned no completion stage");
        } catch (Throwable failure) {
            finishSignal(user, captured, revision, result, false, failure);
            return;
        }
        check.whenComplete((ignored, failure) -> {
            try {
                plugin.getTimer().execute(() -> {
                    boolean canRetireHint = false;
                    Throwable outcome = failure;
                    try {
                        canRetireHint = !user.hasPendingRewardReplay() && !claimedBeforeCheck;
                    } catch (Throwable observationFailure) {
                        if (outcome == null) outcome = observationFailure;
                    }
                    finishSignal(user, captured, revision, result, canRetireHint, outcome);
                });
            } catch (Throwable rejected) {
                finishSignal(user, captured, revision, result, false, failure == null ? rejected : failure);
            }
        });
    }

    private void finishSignal(AdvancedCoreUser user, Pending captured, long revision,
            CompletableFuture<Void> result, boolean canRetireHint, Throwable failure) {
        synchronized (this) {
            if (pending.get(user.getUUID()) == captured && captured.running == result) {
                captured.running = null;
                if (failure == null && canRetireHint && captured.revision == revision)
                    pending.remove(user.getUUID());
                else startFallback();
                stopIdleFallback();
            }
        }
        if (failure == null) result.complete(null); else result.completeExceptionally(failure);
    }

    /** Native events wake only deferred users and retain their original logical identity. */
    public void signal(Player player) {
        List<AdvancedCoreUser> users = new ArrayList<>();
        synchronized (this) {
            for (Pending entry : pending.values()) if (entry.player == player) users.add(entry.user);
        }
        for (AdvancedCoreUser user : users) signal(user);
    }

    public synchronized void disconnected(Player player) {
        pending.entrySet().removeIf(entry -> entry.getValue().player == player && entry.getValue().running == null);
        stopIdleFallback();
    }

    private void sweep() {
        List<Pending> batch = new ArrayList<>();
        synchronized (this) {
            if (closed || !plugin.isEnabled()) return;
            var iterator = pending.entrySet().iterator();
            while (iterator.hasNext() && batch.size() < FALLBACK_BATCH) {
                batch.add(iterator.next().getValue());
                iterator.remove();
            }
            // Rotate bounded hints to give every pending player a turn.
            for (Pending entry : batch) pending.put(entry.user.getUUID(), entry);
        }
        for (Pending entry : batch) signal(entry.user);
    }

    private void stopIdleFallback() {
        if (pending.isEmpty() && fallback != null) { fallback.cancel(false); fallback = null; }
    }

    private void startFallback() {
        if (fallback != null) return;
        try {
            fallback = handler.getDelayedTimer().scheduleWithFixedDelay(this::sweep, 10, 10, TimeUnit.SECONDS);
        } catch (RuntimeException failure) {
            // Explicit signals and login replay remain available after scheduler retirement.
        }
    }

    public void close() {
        List<CompletableFuture<Void>> running = new ArrayList<>();
        synchronized (this) {
            closed = true;
            if (fallback != null) fallback.cancel(false);
            fallback = null;
            for (Pending entry : pending.values()) if (entry.running != null) running.add(entry.running);
            pending.clear();
        }
        for (CompletableFuture<Void> result : running) result.completeExceptionally(
                new IllegalStateException("Reward eligibility closed before replay completed"));
    }

    private static final class Pending {
        private AdvancedCoreUser user;
        private Player player;
        private long revision;
        private CompletableFuture<Void> running;
        private Pending(AdvancedCoreUser user, Player player) { this.user = user; this.player = player; }
    }
}
