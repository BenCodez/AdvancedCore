package com.bencodez.advancedcore.core.runtime;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

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
        ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(daemonThreads("AdvancedCore-Storage"));
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

	private static java.util.concurrent.ThreadFactory daemonThreads(String name) {
		AtomicInteger sequence = new AtomicInteger();
		return task -> {
			Thread thread = new Thread(task, name + "-" + sequence.incrementAndGet());
			// shutdownNow only interrupts JDBC; a driver may legally ignore it. The
			// bounded watchdog therefore cannot leave a plugin-owned worker keeping
			// the JVM alive after disable. The watchdog records its timeout while the
			// retirement stage remains intact to report the eventual flush outcome.
			thread.setDaemon(true);
			return thread;
		};
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
		clean(platform.afterStorageExecutorShutdown());
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

	/** Finish platform teardown now and bound the remaining storage-worker retirement. */
	private void finishDeferredCleanup(CompletionStage<Void> completion, String component, ExecutorGrace grace) {
		ScheduledExecutorService timer = platform.getTimer();
		// Bukkit/Folia-facing cleanup must finish on the lifecycle thread before
		// onDisable returns. Only storage-executor retirement continues later.
		finishDeferredPlatformCleanup(grace);
		AtomicBoolean finished = new AtomicBoolean();
		AtomicBoolean terminalStorageCleanup = new AtomicBoolean();
		completion.whenComplete((ignored, failure) -> {
			if (!finished.compareAndSet(false, true)) {
				if (failure != null) finishDeferredStorageTimer(timer, true, true, terminalStorageCleanup);
				return;
			}
			if (failure == null) shutdown(timer);
			else {
				Throwable cause = failure instanceof CompletionException && failure.getCause() != null
						? failure.getCause() : failure;
				platform.cleanupFailed(component, cause);
				shutdownNow(timer);
			}
			finishDeferredStorageTimer(timer, failure != null, failure != null, terminalStorageCleanup);
		});
		long timeoutMillis = Math.max(1, platform.deferredShutdownTimeoutMillis());
		Runnable timeout = () -> {
			if (!finished.compareAndSet(false, true)) return;
			platform.cleanupFailed(component, new TimeoutException(
					"Deferred storage retirement exceeded " + timeoutMillis + " ms"));
			shutdownNow(timer);
			// The retirement result is not known yet. Do not close its native owner
			// merely because the watchdog expired; an eventual exceptional completion
			// will run terminal cleanup after this forced worker retirement.
			finishDeferredStorageTimer(timer, true, false, terminalStorageCleanup);
		};
		try { CompletableFuture.delayedExecutor(timeoutMillis, TimeUnit.MILLISECONDS).execute(timeout); }
		catch (RuntimeException | Error schedulingFailure) {
			platform.cleanupFailed("deferred storage shutdown watchdog", schedulingFailure);
			timeout.run();
		}
	}

	private void finishDeferredPlatformCleanup(ExecutorGrace grace) {
		clean(platform.afterExecutorGrace());
		shutdownNow(platform.getLoginTimer());
		shutdownNow(grace.timeTimer());
		shutdownNow(platform.getInventoryTimer());
		await(platform.getLoginTimer(), 1, TimeUnit.SECONDS);
		await(grace.timeTimer(), 1, TimeUnit.SECONDS);
		await(platform.getInventoryTimer(), 1, TimeUnit.SECONDS);
		clean(platform.afterExecutorShutdown());
	}

	private void finishDeferredStorageTimer(ScheduledExecutorService timer, boolean forced,
			boolean runTerminalCleanup, AtomicBoolean terminalStorageCleanup) {
		Runnable retirement = () -> {
			await(timer, forced ? 1 : 2, TimeUnit.SECONDS);
			if (!forced && timer != null && !timer.isTerminated()) shutdownNow(timer);
			if (runTerminalCleanup && terminalStorageCleanup.compareAndSet(false, true)) {
				clean(platform.afterStorageExecutorShutdown());
			}
		};
		Thread shutdownThread = new Thread(retirement, "AdvancedCore-Storage-Shutdown");
		shutdownThread.setDaemon(true);
		try { shutdownThread.start(); }
		catch (RuntimeException | Error startFailure) {
			platform.cleanupFailed("deferred storage shutdown continuation", startFailure);
			shutdownNow(timer);
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
