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
import java.util.function.LongSupplier;

import org.bukkit.GameMode;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.simpleapi.folialib.enums.EntityTaskResult;

/** Owns optional eligibility observations and bounded replay wake-ups, never reward payloads. */
public final class RewardEligibility {
    public static final int MAX_PENDING_PLAYERS = 4096;
    private static final int FALLBACK_BATCH = 128;
    private static final String LOGIN_RECEIPT_KEY = "AdvancedCoreRewardEligibilityLogin";
    private static final long INITIAL_FALLBACK_NANOS = TimeUnit.SECONDS.toNanos(10);
    private static final long MAX_FALLBACK_NANOS = TimeUnit.MINUTES.toNanos(5);
    private final LongSupplier nanoClock;
    private final AdvancedCorePlugin plugin;
    private final RewardHandler handler;
    private final LinkedHashMap<String, Pending> pending = new LinkedHashMap<>();
    private final LinkedHashMap<String, Pending> overflowRunning = new LinkedHashMap<>();
    private final RewardEligibilityOverflow overflow;
    private ScheduledFuture<?> fallback;
    private boolean closed;
    private boolean startupRequested;

    RewardEligibility(AdvancedCorePlugin plugin, RewardHandler handler) {
        this(plugin, handler, System::nanoTime);
    }

    RewardEligibility(AdvancedCorePlugin plugin, RewardHandler handler, LongSupplier nanoClock) {
        this.plugin = plugin;
        this.handler = handler;
        this.nanoClock = Objects.requireNonNull(nanoClock, "nanoClock");
        this.overflow = new RewardEligibilityOverflow(plugin, this, nanoClock);
    }

    /** Initializes only existing native sessions after all startup dependencies are ready. */
    public void bootstrapOnlinePlayers() {
        synchronized (this) {
            if (closed || startupRequested) return;
            startupRequested = true;
        }
        RewardEligibilityStartup.start(plugin, this);
    }

    synchronized boolean isOpen() { return !closed; }

    /** A newly joining native session must finish the ordinary accepted-login path. */
    public void loginPending(Player player) {
        synchronized (this) { if (closed) return; }
        player.setMetadata(LOGIN_RECEIPT_KEY, new FixedMetadataValue(plugin, Boolean.FALSE));
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
        if (!validModes(data, "GameModes") || !validModes(data, "BlackListedGameModes")) return false;
        Pending observed;
        synchronized (this) {
            observed = findPending(user.getUUID());
            if (closed || observed != null && observed.awaitingLogin) return false;
        }
        if (observed != null) {
            Player current = user.getPlayer();
            synchronized (this) {
                if (closed || findPending(user.getUUID()) != observed || observed.awaitingLogin
                        || observed.player == null || observed.player != current) return false;
            }
        }
        List<String> allowed = data.getStringList("GameModes");
        List<String> denied = data.getStringList("BlackListedGameModes");
        if (allowed.isEmpty() && denied.isEmpty() && !overflow.enabled()) return true;
        Player player = user.getPlayer();
        if (player == null || !player.isOnline()) return false;
        if (player.getMetadata(LOGIN_RECEIPT_KEY).stream().anyMatch(value ->
                value.getOwningPlugin() == plugin && Boolean.FALSE.equals(value.value()))) return false;
        if (overflow.enabled() && !acceptedSession(player)) return false;
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
        synchronized (this) {
            Pending current = findPending(user.getUUID());
            if (closed || current != null && (current.awaitingLogin || current.player != player)) return;
        }
        if (player == null || !player.isOnline()) return;
        boolean accepted = acceptedSession(player);
        synchronized (this) {
            if (closed) return;
            Pending current = findPending(user.getUUID());
            // Only the accepted login or receipt check may bind an unknown session.
            if (current != null && (current.awaitingLogin || current.player != player)) return;
            if (current == null) {
                if (pending.size() >= MAX_PENDING_PLAYERS) { overflow.enable(); startFallback(); return; }
                current = new Pending(user, accepted ? player : null, nanoClock.getAsLong());
                pending.put(user.getUUID(), current);
            }
            current.user = user;
            current.player = accepted ? player : null;
            current.awaitingLogin = !accepted;
            current.revision++;
            startFallback();
        }
    }

    /** Publication can race a signal that observed an empty queue; renew the wake-up afterwards. */
    void queued(AdvancedCoreUser user) {
        retainHint(user);
    }

    /**
     * Retains a bounded automatic wake-up for an already-due timed occurrence.
     * A false result requires the caller to keep its ordinary future retry deadline
     * and timer. This worker-safe hint never owns or completes the stored reward.
     */
    public synchronized boolean retainTimedRetry(AdvancedCoreUser user) {
        Objects.requireNonNull(user, "user");
        if (user.getPlugin() != plugin) throw new IllegalArgumentException("User belongs to another plugin");
        return plugin.isEnabled() && retainHint(user) && fallback != null
                && !fallback.isCancelled() && !fallback.isDone();
    }

    private synchronized boolean retainHint(AdvancedCoreUser user) {
        if (closed) return false;
        Pending current = findPending(user.getUUID());
        if (current == null) {
            if (pending.size() >= MAX_PENDING_PLAYERS) { overflow.enable(); startFallback(); return false; }
            current = new Pending(user, null, nanoClock.getAsLong());
            pending.put(user.getUUID(), current);
        }
        current.revision++;
        startFallback();
        return true;
    }

    /**
     * Rechecks the existing offline queue and completes after its claimed occurrences
     * finish or defer again. Concurrent signals for one logical user share one stage.
     * This method accepts the consumer's user identity, including offline-mode identities.
     */
    public CompletionStage<Void> signal(AdvancedCoreUser user) {
        return signal(user, null);
    }

    private Pending findPending(String uuid) {
        Pending current = pending.get(uuid);
        return current == null ? overflowRunning.get(uuid) : current;
    }

    boolean acceptedSession(Player player) {
        return player.getMetadata(LOGIN_RECEIPT_KEY).stream().anyMatch(value ->
                value.getOwningPlugin() == plugin && Boolean.TRUE.equals(value.value()));
    }

    /** Called only after exact native-session validation on its owner. */
    CompletionStage<Void> signalOverflow(AdvancedCoreUser user, Player player) {
        if (user.getPlugin() != plugin) return CompletableFuture.failedFuture(
                new IllegalArgumentException("User belongs to another plugin"));
        synchronized (this) {
            if (closed || !plugin.isEnabled()) return CompletableFuture.completedFuture(null);
            Pending current = findPending(user.getUUID());
            if (current == null) {
                if (overflowRunning.size() >= RewardEligibilityOverflow.BATCH)
                    return CompletableFuture.completedFuture(null);
                current = new Pending(user, player, nanoClock.getAsLong());
                overflowRunning.put(user.getUUID(), current);
            }
            current.user = user;
            current.player = player;
            current.awaitingLogin = false;
            current.revision++;
        }
        return signal(user);
    }

    private CompletionStage<Void> signal(AdvancedCoreUser user, Pending fallbackEntry) {
        Objects.requireNonNull(user, "user");
        if (user.getPlugin() != plugin) return CompletableFuture.failedFuture(
                new IllegalArgumentException("User belongs to another plugin"));
        Pending current;
        long revision;
        synchronized (this) {
            if (closed || !plugin.isEnabled()) return CompletableFuture.failedFuture(
                    new IllegalStateException("Reward eligibility is closed"));
            current = findPending(user.getUUID());
            if (current != null && current.awaitingLogin
                    && overflowRunning.get(user.getUUID()) != current) return CompletableFuture.completedFuture(null);
            long now = nanoClock.getAsLong();
            if (fallbackEntry != null && (current != fallbackEntry || current.running != null
                    || now - current.nextSweepNanos < 0)) return CompletableFuture.completedFuture(null);
            if (current == null) {
                if (pending.size() >= MAX_PENDING_PLAYERS) return CompletableFuture.failedFuture(
                        new IllegalStateException("Too many pending reward eligibility checks"));
                current = new Pending(user, null, nanoClock.getAsLong());
                pending.put(user.getUUID(), current);
            }
            if (fallbackEntry == null) {
                current.fallbackDelayNanos = INITIAL_FALLBACK_NANOS;
            } else {
                current.fallbackDelayNanos = Math.min(MAX_FALLBACK_NANOS, current.fallbackDelayNanos * 2);
            }
            current.nextSweepNanos = now + current.fallbackDelayNanos;
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
        boolean awaitingLogin;
        boolean needsBinding;
        synchronized (this) {
            if (closed || findPending(user.getUUID()) != captured || captured.running != result) return;
            awaitingLogin = captured.awaitingLogin;
            needsBinding = captured.player == null;
        }
        if (awaitingLogin) {
            // Overflow admissions are temporary execution gates. They may retire
            // after claims finish; online discovery remains the automatic wake-up.
            boolean retire = false;
            try {
                synchronized (this) { retire = overflowRunning.get(user.getUUID()) == captured; }
                retire = retire && !user.hasPendingRewardReplay();
                finishSignal(user, captured, revision, result, retire, null);
            } catch (Throwable failure) { finishSignal(user, captured, revision, result, false, failure); }
            return;
        }
        if (needsBinding) {
            resolveSignalPlayer(user, captured, revision, result);
            return;
        }
        final boolean claimedBeforeCheck;
        final CompletionStage<Void> check;
        try {
            claimedBeforeCheck = user.hasPendingRewardReplay();
            user.checkDelayedTimedRewards();
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

    /** Unknown worker-published hints require proof before any queue/storage probe. */
    private void resolveSignalPlayer(AdvancedCoreUser user, Pending captured, long revision,
            CompletableFuture<Void> result) {
        Reward.continueOnResolvedPlayerThread(plugin, user, player -> {
            synchronized (this) {
                if (closed || findPending(user.getUUID()) != captured || captured.running != result
                        || captured.awaitingLogin) return CompletableFuture.completedFuture(false);
                if (captured.player != null) return CompletableFuture.completedFuture(true);
            }
            boolean accepted = player != null && user.getPlayer() == player && player.isOnline()
                    && player.getMetadata(LOGIN_RECEIPT_KEY).stream().anyMatch(value ->
                            value.getOwningPlugin() == plugin && Boolean.TRUE.equals(value.value()));
            synchronized (this) {
                if (closed || findPending(user.getUUID()) != captured || captured.running != result
                        || captured.awaitingLogin) return CompletableFuture.completedFuture(false);
                // A newer accepted login may have rebound the hint while this task waited.
                if (captured.player != null) return CompletableFuture.completedFuture(true);
                if (accepted) captured.player = player;
                else {
                    captured.awaitingLogin = true;
                    captured.revision++;
                }
            }
            return CompletableFuture.completedFuture(accepted);
        }).whenComplete((accepted, failure) -> {
            if (failure != null || !Boolean.TRUE.equals(accepted)) {
                finishSignal(user, captured, revision, result, false, failure);
                return;
            }
            try { plugin.getTimer().execute(() -> runSignalCheck(user, captured, revision, result)); }
            catch (Throwable rejected) { finishSignal(user, captured, revision, result, false, rejected); }
        });
    }

    private void finishSignal(AdvancedCoreUser user, Pending captured, long revision,
            CompletableFuture<Void> result, boolean canRetireHint, Throwable failure) {
        synchronized (this) {
            if (findPending(user.getUUID()) == captured && captured.running == result) {
                captured.running = null;
                if (failure == null && canRetireHint && overflowRunning.get(user.getUUID()) == captured)
                    overflowRunning.remove(user.getUUID());
                else if (failure == null && canRetireHint && captured.revision == revision)
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
        if (overflow.enabled()) overflow.signal(player);
    }

    /**
     * The logical hint may be the sole automatic wake-up for due timed work.
     * Retain it across quit without polling storage or keeping a native player.
     */
    public void disconnected(Player player) {
        player.removeMetadata(LOGIN_RECEIPT_KEY, plugin);
        synchronized (this) {
            for (Pending entry : allPending()) {
                if (entry.player == player) {
                    entry.player = null;
                    entry.awaitingLogin = true;
                    entry.revision++;
                }
            }
        }
    }

    /** Rebinds only known work after the resolved, authenticated login event. */
    public CompletionStage<Void> signalLogin(AdvancedCoreUser user, Player player) {
        Objects.requireNonNull(user, "user");
        Objects.requireNonNull(player, "player");
        if (user.getPlugin() != plugin) return CompletableFuture.failedFuture(
                new IllegalArgumentException("User belongs to another plugin"));
        synchronized (this) {
            if (closed || !plugin.isEnabled()) return CompletableFuture.failedFuture(
                    new IllegalStateException("Reward eligibility is closed"));
        }
        // Login events are asynchronous and can outlive their connection. Validate
        // on that exact player's owner, rather than scheduling a replacement session.
        return Reward.continueOnPlayerThread(plugin, player, () -> {
            if (user.getPlayer() != player || !player.isOnline())
                return CompletableFuture.completedFuture(null);
            synchronized (this) { if (closed) return CompletableFuture.completedFuture(null); }
            // Keep the receipt on the native session even if its work is published later.
            player.setMetadata(LOGIN_RECEIPT_KEY, new FixedMetadataValue(plugin, Boolean.TRUE));
            synchronized (this) {
                if (closed) return CompletableFuture.completedFuture(null);
                Pending current = findPending(user.getUUID());
                if (current == null && !overflow.enabled()) return CompletableFuture.completedFuture(null);
                if (current != null) {
                    current.user = user;
                    current.player = player;
                    current.awaitingLogin = false;
                    current.revision++;
                }
            }
            return overflow.enabled() ? signalOverflow(user, player) : signal(user);
        });
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
        List<Pending> temporary;
        synchronized (this) { temporary = new ArrayList<>(overflowRunning.values()); }
        for (Pending entry : batch) signal(entry.user, entry);
        for (Pending entry : temporary) signal(entry.user, entry);
        overflow.sweep();
    }

    private List<Pending> allPending() {
        List<Pending> entries = new ArrayList<>(pending.values());
        entries.addAll(overflowRunning.values());
        return entries;
    }

    private void stopIdleFallback() {
        if (pending.isEmpty() && overflowRunning.isEmpty() && !overflow.enabled() && fallback != null) { fallback.cancel(false); fallback = null; }
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
            for (Pending entry : allPending()) if (entry.running != null) running.add(entry.running);
            pending.clear();
            overflowRunning.clear();
        }
        overflow.close();
        for (CompletableFuture<Void> result : running) result.completeExceptionally(
                new IllegalStateException("Reward eligibility closed before replay completed"));
    }

    private static final class Pending {
        private AdvancedCoreUser user;
        private Player player;
        private long revision;
        private boolean awaitingLogin;
        private CompletableFuture<Void> running;
        private long fallbackDelayNanos = INITIAL_FALLBACK_NANOS;
        private long nextSweepNanos;
        private Pending(AdvancedCoreUser user, Player player, long now) {
            this.user = user;
            this.player = player;
            this.nextSweepNanos = now + INITIAL_FALLBACK_NANOS;
        }
    }
}
