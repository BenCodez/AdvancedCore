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
	private enum CleanupState { SUCCESS, FAILURE, DEFERRED }
	private record ExecutorGrace(ScheduledExecutorService timeTimer) { }

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
		CompletionStage<Void> retirement = platform.beforeExecutorShutdownCompletion();
		CleanupState retirementState = awaitCleanup(retirement, "pre-executor shutdown");
		ExecutorGrace grace = beginExecutorGrace(retirementState == CleanupState.SUCCESS);
		if (retirementState == CleanupState.DEFERRED) {
			finishDeferredCleanup(retirement, "pre-executor shutdown", grace);
			return;
		}
		boolean timerForced = retirementState == CleanupState.FAILURE;
		if (timerForced) shutdownNow(platform.getTimer());
		finishAfterExecutorGrace(grace, timerForced);
	}

	private ExecutorGrace beginExecutorGrace(boolean stopStorageTimer) {
        // Resolve the time-checker timer once, after the pre-shutdown actions,
        // just as the old lifecycle did. Other getters retain their lookup order.
		ScheduledExecutorService timeTimer = platform.getTimeTimer();
		shutdown(platform.getLoginTimer());
		if (stopStorageTimer) shutdown(platform.getTimer());
		shutdown(timeTimer);
        shutdown(platform.getInventoryTimer());

		platform.info("Allowing background tasks to finish before shutdown");
		await(platform.getLoginTimer(), 2, TimeUnit.SECONDS);
		if (stopStorageTimer) await(platform.getTimer(), 2, TimeUnit.SECONDS);
		await(timeTimer, 2, TimeUnit.SECONDS);
        await(platform.getInventoryTimer(), 1, TimeUnit.SECONDS);
		return new ExecutorGrace(timeTimer);
	}

	private void finishAfterExecutorGrace(ExecutorGrace grace, boolean storageTimerAlreadyForced) {
		clean(platform.afterExecutorGrace());
		shutdownNow(platform.getLoginTimer());
		if (!storageTimerAlreadyForced) shutdownNow(platform.getTimer());
		shutdownNow(grace.timeTimer());
        shutdownNow(platform.getInventoryTimer());
        await(platform.getLoginTimer(), 1, TimeUnit.SECONDS);
		await(platform.getTimer(), 1, TimeUnit.SECONDS);
		await(grace.timeTimer(), 1, TimeUnit.SECONDS);
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
	private CleanupState awaitCleanup(CompletionStage<Void> completion, String component) {
		if (completion == null) return CleanupState.SUCCESS;
		var future = completion.toCompletableFuture();
		if (!platform.canBlockForPreExecutorShutdown() && !future.isDone()) {
			return CleanupState.DEFERRED;
		}
		try {
			if (future.isDone()) future.join();
			else future.get(PRE_SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS);
			return CleanupState.SUCCESS;
		} catch (TimeoutException timeout) {
			platform.cleanupFailed(component, timeout);
			return CleanupState.DEFERRED;
		} catch (CompletionException failure) {
			Throwable cause = failure.getCause() == null ? failure : failure.getCause();
			platform.cleanupFailed(component, cause);
			return CleanupState.FAILURE;
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			platform.cleanupFailed(component, interrupted);
			return future.isDone() ? CleanupState.FAILURE : CleanupState.DEFERRED;
		} catch (Exception failure) {
			Throwable cause = failure.getCause() == null ? failure : failure.getCause();
			platform.cleanupFailed(component, cause);
			if (future.isDone()) {
				return CleanupState.FAILURE;
			}
			return CleanupState.DEFERRED;
		}
	}

	/** Finish every teardown phase only after deferred storage work has completed. */
	private void finishDeferredCleanup(CompletionStage<Void> completion, String component, ExecutorGrace grace) {
		ScheduledExecutorService timer = platform.getTimer();
		completion.whenComplete((ignored, failure) -> {
			boolean timerForced = failure != null;
			if (failure == null) {
				shutdown(timer);
			} else {
				Throwable cause = failure instanceof CompletionException && failure.getCause() != null
						? failure.getCause() : failure;
				platform.cleanupFailed(component, cause);
				shutdownNow(timer);
			}
			Runnable continuation = () -> {
				await(timer, failure == null ? 2 : 1, TimeUnit.SECONDS);
				finishAfterExecutorGrace(grace, timerForced);
			};
			// Completion normally runs on the storage timer itself. Let that callback
			// return before awaiting the timer, otherwise it waits for its own task.
			Thread shutdownThread = new Thread(continuation, "AdvancedCore-Shutdown");
			shutdownThread.setDaemon(true);
			try { shutdownThread.start(); }
			catch (RuntimeException | Error startFailure) {
				platform.cleanupFailed("deferred shutdown continuation", startFailure);
				continuation.run();
			}
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
