package com.bencodez.advancedcore.tests.api.rewards;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

import org.bukkit.entity.Player;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.rewards.RewardFileData;
import com.bencodez.advancedcore.api.rewards.RewardEligibility;
import com.bencodez.advancedcore.api.rewards.RewardHandler;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.advancedcore.tests.support.ReflectiveAccess;

/** Test-only access to the existing eligibility internals; no production visibility changes. */
final class EligibilityAccess {
    private EligibilityAccess() { }

    static RewardEligibility create(AdvancedCorePlugin plugin, RewardHandler handler) {
        return ReflectiveAccess.construct(RewardEligibility.class,
                new Class<?>[] { AdvancedCorePlugin.class, RewardHandler.class }, plugin, handler);
    }

    static RewardEligibility create(AdvancedCorePlugin plugin, RewardHandler handler, LongSupplier clock) {
        return ReflectiveAccess.construct(RewardEligibility.class,
                new Class<?>[] { AdvancedCorePlugin.class, RewardHandler.class, LongSupplier.class },
                plugin, handler, clock);
    }

    static void bind(Reward reward, AdvancedCorePlugin plugin) { setPlugin(Reward.class, reward, plugin); }
    static void bind(RewardFileData config, AdvancedCorePlugin plugin) { setPlugin(RewardFileData.class, config, plugin); }

    private static void setPlugin(Class<?> owner, Object target, AdvancedCorePlugin plugin) {
        try {
            var field = owner.getDeclaredField("plugin");
            field.setAccessible(true);
            field.set(target, plugin);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    static void queued(RewardEligibility eligibility, AdvancedCoreUser user) {
        ReflectiveAccess.invoke(RewardEligibility.class, eligibility, "queued",
                new Class<?>[] { AdvancedCoreUser.class }, user);
    }

    static void deferred(RewardEligibility eligibility, AdvancedCoreUser user) {
        ReflectiveAccess.invoke(RewardEligibility.class, eligibility, "deferred",
                new Class<?>[] { AdvancedCoreUser.class }, user);
    }

    static final class Startup {
        private static final Class<?> TYPE = ReflectiveAccess.type(
                "com.bencodez.advancedcore.api.rewards.RewardEligibilityStartup");
        static Class<?> type() { return TYPE; }
        static void start(AdvancedCorePlugin plugin, RewardEligibility eligibility) {
            ReflectiveAccess.invoke(TYPE, null, "start",
                    new Class<?>[] { AdvancedCorePlugin.class, RewardEligibility.class }, plugin, eligibility);
        }
        static CompletableFuture<Boolean> authenticate(AdvancedCorePlugin plugin, RewardEligibility eligibility,
                Player player, String name, boolean online, AtomicBoolean permit) {
            return ReflectiveAccess.invoke(TYPE, null, "authenticate", new Class<?>[] {
                    AdvancedCorePlugin.class, RewardEligibility.class, Player.class, String.class,
                    boolean.class, AtomicBoolean.class }, plugin, eligibility, player, name, online, permit);
        }
        static com.lenis0012.bukkit.loginsecurity.session.PlayerSession loginSecuritySession(Player player) {
            return ReflectiveAccess.invoke(TYPE, null, "loginSecuritySession", new Class<?>[] { Player.class }, player);
        }
    }

    static final class Overflow {
        private static final Class<?> TYPE = ReflectiveAccess.type(
                "com.bencodez.advancedcore.api.rewards.RewardEligibilityOverflow");
        static final int BATCH = batchSize();
        private static int batchSize() {
            try {
                var field = TYPE.getDeclaredField("BATCH");
                field.setAccessible(true);
                return field.getInt(null);
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        }
        private final Object target;
        Overflow(Object target) { this.target = target; }
        void enable() { ReflectiveAccess.invoke(TYPE, target, "enable", new Class<?>[0]); }
        void sweep() { ReflectiveAccess.invoke(TYPE, target, "sweep", new Class<?>[0]); }
        CompletionStage<Void> signal(Player player) {
            return ReflectiveAccess.invoke(TYPE, target, "signal", new Class<?>[] { Player.class }, player);
        }
    }
}
