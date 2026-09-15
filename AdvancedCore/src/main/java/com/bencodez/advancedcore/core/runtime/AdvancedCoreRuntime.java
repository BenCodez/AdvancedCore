package com.bencodez.advancedcore.core.runtime;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.bencodez.advancedcore.core.platform.RuntimePlatform;
import com.bencodez.advancedcore.core.platform.RuntimePlatform.Cleanup;

/**
 * First shared runtime slice: executor creation and existing shutdown sequencing.
 * It creates no second cache/service registry, and does not replace an entity or
 * region scheduler with a global executor. The platform serializes lifecycle
 * calls; cleanup hooks are synchronous and retain their existing failure policy.
 */
public final class AdvancedCoreRuntime {
	private static final long PRE_SHUTDOWN_WAIT_SECONDS = 5;
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
		boolean sharedRetirementFinished = awaitCleanup(platform.beforeExecutorShutdownCompletion(), "pre-executor shutdown");

        // Resolve the time-checker timer once, after the pre-shutdown actions,
        // just as the old lifecycle did. Other getters retain their lookup order.
		ScheduledExecutorService timeTimer = platform.getTimeTimer();
		shutdown(platform.getLoginTimer());
		if (sharedRetirementFinished) shutdown(platform.getTimer());
		shutdown(timeTimer);
        shutdown(platform.getInventoryTimer());

		platform.info("Allowing background tasks to finish before shutdown");
		await(platform.getLoginTimer(), 2, TimeUnit.SECONDS);
		if (sharedRetirementFinished) await(platform.getTimer(), 2, TimeUnit.SECONDS);
		await(timeTimer, 2, TimeUnit.SECONDS);
        await(platform.getInventoryTimer(), 1, TimeUnit.SECONDS);

		clean(platform.afterExecutorGrace());
		shutdownNow(platform.getLoginTimer());
		if (sharedRetirementFinished) shutdownNow(platform.getTimer());
		shutdownNow(timeTimer);
        shutdownNow(platform.getInventoryTimer());
        await(platform.getLoginTimer(), 1, TimeUnit.SECONDS);
		if (sharedRetirementFinished) await(platform.getTimer(), 1, TimeUnit.SECONDS);
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

	/**
	 * Wait only where the platform permits it and never indefinitely.  A failed or
	 * still-running storage retirement retains its worker: it owns the queued data
	 * and native provider until it has either flushed successfully or reported its
	 * own failure/retry outcome.
	 */
	private boolean awaitCleanup(CompletionStage<Void> completion, String component) {
		if (completion == null) return true;
		var future = completion.toCompletableFuture();
		if (!platform.canBlockForPreExecutorShutdown() && !future.isDone()) {
			finishDeferredCleanup(completion, component);
			return false;
		}
		try {
			if (future.isDone()) future.join();
			else future.get(PRE_SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS);
			return true;
		} catch (TimeoutException timeout) {
			platform.cleanupFailed(component, timeout);
			finishDeferredCleanup(completion, component);
			return false;
		} catch (CompletionException failure) {
			Throwable cause = failure.getCause() == null ? failure : failure.getCause();
			platform.cleanupFailed(component, cause);
			shutdownNow(platform.getTimer());
			return false;
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			platform.cleanupFailed(component, interrupted);
			finishDeferredCleanup(completion, component);
			return false;
		} catch (Exception failure) {
			Throwable cause = failure.getCause() == null ? failure : failure.getCause();
			platform.cleanupFailed(component, cause);
			if (future.isDone()) shutdownNow(platform.getTimer());
			else finishDeferredCleanup(completion, component);
			return false;
		}
	}

	/** Retire the platform timer only after deferred storage work has actually completed. */
	private void finishDeferredCleanup(CompletionStage<Void> completion, String component) {
		ScheduledExecutorService timer = platform.getTimer();
		completion.whenComplete((ignored, failure) -> {
			if (failure == null) {
				shutdown(timer);
				return;
			}
			Throwable cause = failure instanceof CompletionException && failure.getCause() != null
					? failure.getCause() : failure;
			platform.cleanupFailed(component, cause);
			shutdownNow(timer);
		});
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
