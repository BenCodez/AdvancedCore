package com.bencodez.advancedcore.core.runtime;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.bencodez.advancedcore.core.platform.RuntimePlatform;
import com.bencodez.advancedcore.core.platform.RuntimePlatform.Cleanup;

/**
 * First shared runtime slice: executor creation and existing shutdown sequencing.
 * It creates no second cache/service registry, and does not replace an entity or
 * region scheduler with a global executor. The platform serializes lifecycle
 * calls; cleanup hooks are synchronous and retain their existing failure policy.
 */
public final class AdvancedCoreRuntime {
    private final RuntimePlatform platform;

    public AdvancedCoreRuntime(RuntimePlatform platform) {
        this.platform = Objects.requireNonNull(platform, "platform");
    }

    /** The same three independent executor owners used by the Bukkit plugin. */
    public record ExecutorGroup(ScheduledExecutorService timer, ScheduledExecutorService loginTimer,
            ScheduledExecutorService inventoryTimer) { }

    public static ExecutorGroup createExecutors() {
        ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
        ScheduledExecutorService loginTimer = null;
        try {
            loginTimer = Executors.newSingleThreadScheduledExecutor();
            return new ExecutorGroup(timer, loginTimer, Executors.newSingleThreadScheduledExecutor());
        } catch (RuntimeException | Error failure) {
            timer.shutdownNow();
            if (loginTimer != null) loginTimer.shutdownNow();
            throw failure;
        }
    }

    public void shutdown() {
        clean(platform.beforeExecutorShutdown());

        // Resolve the time-checker timer once, after the pre-shutdown actions,
        // just as the old lifecycle did. Other getters retain their lookup order.
        ScheduledExecutorService timeTimer = platform.getTimeTimer();
        shutdown(platform.getLoginTimer());
        shutdown(platform.getTimer());
        shutdown(timeTimer);
        shutdown(platform.getInventoryTimer());

        platform.info("Allowing background tasks to finish before shutdown");
        await(platform.getLoginTimer(), 2, TimeUnit.SECONDS);
        await(platform.getTimer(), 2, TimeUnit.SECONDS);
        await(timeTimer, 2, TimeUnit.SECONDS);
        await(platform.getInventoryTimer(), 1, TimeUnit.SECONDS);

        clean(platform.afterExecutorGrace());
        shutdownNow(platform.getLoginTimer());
        shutdownNow(platform.getTimer());
        shutdownNow(timeTimer);
        shutdownNow(platform.getInventoryTimer());
        await(platform.getLoginTimer(), 1, TimeUnit.SECONDS);
        await(platform.getTimer(), 1, TimeUnit.SECONDS);
        await(timeTimer, 1, TimeUnit.SECONDS);
        await(platform.getInventoryTimer(), 1, TimeUnit.SECONDS);
        clean(platform.afterExecutorShutdown());
    }

    private void clean(List<Cleanup> actions) {
        for (Cleanup action : actions) {
            try {
                action.action().run();
            } catch (Throwable failure) {
                platform.cleanupFailed(action.name(), failure);
            }
        }
    }

    public static void shutdown(ScheduledExecutorService executor) {
        if (executor != null && !executor.isShutdown()) executor.shutdown();
    }
    public static void shutdownNow(ScheduledExecutorService executor) {
        if (executor != null && !executor.isTerminated()) executor.shutdownNow();
    }
    public static void await(ScheduledExecutorService executor, long timeout, TimeUnit unit) {
        if (executor == null) return;
        try {
            executor.awaitTermination(timeout, unit);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
