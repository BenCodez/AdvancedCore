package com.bencodez.advancedcore.api.rewards;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import com.bencodez.advancedcore.AdvancedCorePlugin;
import fr.xephi.authme.api.v3.AuthMeApi;
import com.nickuc.login.api.nLoginAPI;
import com.lenis0012.bukkit.loginsecurity.LoginSecurity;

/** One sequential, session-bound bootstrap; it does not fire generic login events. */
final class RewardEligibilityStartup {
    static final String RECEIPT = "AdvancedCoreRewardEligibilityLogin";
    private RewardEligibilityStartup() { }

    static void start(AdvancedCorePlugin plugin, RewardEligibility eligibility) {
        AtomicBoolean reported = new AtomicBoolean();
        AtomicBoolean queryInFlight = new AtomicBoolean();
        plugin.getBukkitScheduler().executeOrScheduleSync(plugin, () -> {
            if (!current(plugin, eligibility)) return;
            // Retention is bounded by the current native online population. Only one
            // player is in flight; this does not enqueue a task per player in a burst.
            CompletionStage<Void> tail = CompletableFuture.completedFuture(null);
            for (Player player : new ArrayList<>(Bukkit.getOnlinePlayers())) {
                tail = tail.handle((ignored, failure) -> {
                    if (failure != null && reported.compareAndSet(false, true))
                        plugin.debug("An existing online reward session could not be initialized");
                    return null;
                }).thenCompose(ignored -> probe(plugin, eligibility, player, queryInFlight));
            }
            tail.whenComplete((ignored, failure) -> {
                if (failure != null && reported.compareAndSet(false, true))
                    plugin.debug("An existing online reward session could not be initialized");
            });
        });
    }

    private static CompletionStage<Void> probe(AdvancedCorePlugin plugin, RewardEligibility eligibility, Player player,
            AtomicBoolean queryInFlight) {
        return Reward.continueOnPlayerThread(plugin, player, () -> {
            if (!current(plugin, eligibility) || !player.isOnline() || pendingLogin(plugin, player))
                return CompletableFuture.completedFuture(null);
            String name = player.getName();
            boolean wait = plugin.getOptions().isWaitUntilLoggedIn();
            // The pinned AuthMe query is a memory-only PlayerCache read. Its native
            // player-name access stays on this owner; other providers can load storage.
            if (wait && plugin.isAuthMeLoaded() && !AuthMeApi.getInstance().isAuthenticated(player))
                return CompletableFuture.completedFuture(null);
            CompletableFuture<Boolean> authentication = authenticate(plugin, eligibility, player, name, wait, queryInFlight);
            return authentication.orTimeout(30, TimeUnit.SECONDS).thenCompose(accepted -> {
                if (!accepted || !current(plugin, eligibility)) return CompletableFuture.completedFuture(null);
                CompletableFuture<com.bencodez.advancedcore.api.user.AdvancedCoreUser> loaded = new CompletableFuture<>();
                // Names retain the existing offline-mode UUID resolution contract.
                plugin.getUserManager().getUserAsync(name, loaded::complete, loaded::completeExceptionally);
                return loaded.orTimeout(30, TimeUnit.SECONDS).thenCompose(user ->
                    Reward.continueOnPlayerThread(plugin, player, () -> {
                        if (!current(plugin, eligibility) || !player.isOnline() || pendingLogin(plugin, player)
                                || user == null || user.getPlayer() != player) return CompletableFuture.completedFuture(null);
                        return eligibility.signalLogin(user, player);
                    }));
            });
        });
    }

    /** A timeout retires its result, not a provider operation that may still be blocked. */
    static CompletableFuture<Boolean> authenticate(AdvancedCorePlugin plugin, RewardEligibility eligibility,
            Player player, String name, boolean wait, AtomicBoolean queryInFlight) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        if (!queryInFlight.compareAndSet(false, true)) {
            result.completeExceptionally(new IllegalStateException("Existing online authentication query is still running"));
            return result;
        }
        try {
            plugin.getTimer().execute(() -> {
                boolean accepted = false;
                Throwable failure = null;
                try {
                    if (current(plugin, eligibility) && !result.isDone()) {
                        accepted = true;
                        if (wait && plugin.isNLoginLoaded()) {
                            var api = nLoginAPI.getApi();
                            accepted = api != null && api.isAvailable() && api.isAuthenticated(name);
                        }
                        if (accepted && wait && plugin.isLoginSecurityLoaded()) {
                            // A session miss may load storage; the native accesses inside
                            // this optional provider are limited to identity/session getters.
                            var session = loginSecuritySession(player);
                            accepted = session != null && session.isAuthorized();
                        }
                    }
                } catch (RuntimeException | LinkageError outcome) {
                    failure = outcome;
                } finally {
                    queryInFlight.set(false);
                }
                // Complete after releasing physical ownership: normal synchronous
                // continuations may immediately begin the next player's probe.
                if (failure == null) result.complete(accepted);
                else result.completeExceptionally(failure);
            });
        } catch (RuntimeException failure) {
            queryInFlight.set(false);
            result.completeExceptionally(failure);
        }
        return result;
    }

    /** Optional provider boundary; a session miss is allowed to touch its own storage. */
    static com.lenis0012.bukkit.loginsecurity.session.PlayerSession loginSecuritySession(Player player) {
        var manager = LoginSecurity.getSessionManager();
        return manager == null ? null : manager.getPlayerSession(player);
    }

    private static boolean pendingLogin(AdvancedCorePlugin plugin, Player player) {
        return player.getMetadata(RECEIPT).stream().anyMatch(value ->
                value.getOwningPlugin() == plugin && Boolean.FALSE.equals(value.value()));
    }

    private static boolean current(AdvancedCorePlugin plugin, RewardEligibility eligibility) {
        var handler = plugin.getRewardHandler();
        return plugin.isEnabled() && eligibility.isOpen() && handler != null
                && handler.getRewardEligibility() == eligibility;
    }
}
