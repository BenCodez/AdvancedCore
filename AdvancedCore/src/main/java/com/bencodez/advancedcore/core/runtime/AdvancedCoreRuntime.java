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
		boolean holdTimeTimer = retirementState == CleanupState.DEFERRED
				&& platform.holdTimeTimerUntilPreExecutorShutdownCompletion();
		ExecutorGrace grace = beginExecutorGrace(retirementState == CleanupState.SUCCESS, !holdTimeTimer);
		if (retirementState == CleanupState.DEFERRED) {
			finishDeferredCleanup(retirement, "pre-executor shutdown", grace, holdTimeTimer);
			return;
		}
		boolean timerForced = retirementState == CleanupState.FAILURE;
		if (timerForced) shutdownNow(platform.getTimer());
		finishAfterExecutorGrace(grace, timerForced);
	}

	private ExecutorGrace beginExecutorGrace(boolean stopStorageTimer, boolean stopTimeTimer) {
        // Resolve the time-checker timer once, after the pre-shutdown actions,
        // just as the old lifecycle did. Other getters retain their lookup order.
		ScheduledExecutorService timeTimer = platform.getTimeTimer();
		shutdown(platform.getLoginTimer());
		if (stopStorageTimer) shutdown(platform.getTimer());
		if (stopTimeTimer) shutdown(timeTimer);
        shutdown(platform.getInventoryTimer());

		platform.info("Allowing background tasks to finish before shutdown");
		await(platform.getLoginTimer(), 2, TimeUnit.SECONDS);
		if (stopStorageTimer) await(platform.getTimer(), 2, TimeUnit.SECONDS);
		if (stopTimeTimer) await(timeTimer, 2, TimeUnit.SECONDS);
        await(platform.getInventoryTimer(), 1, TimeUnit.SECONDS);
		return new ExecutorGrace(timeTimer);
	}

	private void finishAfterExecutorGrace(ExecutorGrace grace, boolean storageTimerAlreadyForced) {
		clean(platform.afterExecutorGrace());
		shutdownNow(platform.getLoginTimer());
		if (!storageTimerAlreadyForced) shutdownNow(platform.getTimer());
		platform.beforeForcedTimeTimerShutdown();
		shutdownNow(grace.timeTimer());
        shutdownNow(platform.getInventoryTimer());
        await(platform.getLoginTimer(), 1, TimeUnit.SECONDS);
		await(platform.getTimer(), 1, TimeUnit.SECONDS);
		await(grace.timeTimer(), 1, TimeUnit.SECONDS);
        await(platform.getInventoryTimer(), 1, TimeUnit.SECONDS);
		ScheduledExecutorService storageTimer = platform.getUserStorageTimer();
		if (storageTimer != null && storageTimer != platform.getTimer()) {
			shutdown(storageTimer);
			await(storageTimer, 2, TimeUnit.SECONDS);
			if (!storageTimer.isTerminated()) shutdownNow(storageTimer);
			await(storageTimer, 1, TimeUnit.SECONDS);
		}
		finishTerminalStorageCleanup(storageTimer, new AtomicBoolean());
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
	private void finishDeferredCleanup(CompletionStage<Void> completion, String component, ExecutorGrace grace,
			boolean holdTimeTimer) {
		ScheduledExecutorService timer = platform.getUserStorageTimer();
		if (timer == null) timer = platform.getTimer();
		final ScheduledExecutorService storageTimer = timer;
		// Bukkit/Folia-facing cleanup must finish on the lifecycle thread before
		// onDisable returns. Only storage-executor retirement continues later.
		finishDeferredPlatformCleanup(grace, storageTimer, holdTimeTimer);
		AtomicBoolean finished = new AtomicBoolean();
		AtomicBoolean terminalStorageCleanup = new AtomicBoolean();
		completion.whenComplete((ignored, failure) -> {
			if (!finished.compareAndSet(false, true)) {
				if (failure != null) finishDeferredStorageTimer(storageTimer, true, true, terminalStorageCleanup);
				return;
			}
			if (failure == null) shutdown(storageTimer);
			else {
				Throwable cause = failure instanceof CompletionException && failure.getCause() != null
						? failure.getCause() : failure;
				platform.cleanupFailed(component, cause);
				shutdownNow(storageTimer);
			}
			if (holdTimeTimer) shutdownNow(grace.timeTimer());
			finishDeferredStorageTimer(storageTimer, failure != null, failure != null, terminalStorageCleanup);
		});
		long timeoutMillis = Math.max(1, platform.deferredShutdownTimeoutMillis());
		Runnable timeout = () -> {
			// Mark a still-draining transition recoverable before racing its final
			// lease acknowledgement. Once this hook returns, it cannot advance a
			// time marker even if the worker ignores interruption briefly.
			if (holdTimeTimer) platform.beforeForcedTimeTimerShutdown();
			if (!finished.compareAndSet(false, true)) return;
			platform.cleanupFailed(component, new TimeoutException(
					"Deferred storage retirement exceeded " + timeoutMillis + " ms"));
			if (holdTimeTimer) shutdownNow(grace.timeTimer());
			shutdownNow(storageTimer);
			// The queued retirement may have been removed by shutdownNow and therefore
			// cannot complete its stage. Run terminal cleanup explicitly after the
			// bounded worker wait so native owners are not stranded behind that stage.
			finishDeferredStorageTimer(storageTimer, true, true, terminalStorageCleanup);
		};
		try { CompletableFuture.delayedExecutor(timeoutMillis, TimeUnit.MILLISECONDS).execute(timeout); }
		catch (RuntimeException | Error schedulingFailure) {
			platform.cleanupFailed("deferred storage shutdown watchdog", schedulingFailure);
			timeout.run();
		}
	}

	private void finishDeferredPlatformCleanup(ExecutorGrace grace, ScheduledExecutorService storageTimer,
			boolean holdTimeTimer) {
		clean(platform.afterExecutorGrace());
		shutdownNow(platform.getLoginTimer());
		if (platform.getTimer() != storageTimer) shutdownNow(platform.getTimer());
		if (!holdTimeTimer) shutdownNow(grace.timeTimer());
		shutdownNow(platform.getInventoryTimer());
		await(platform.getLoginTimer(), 1, TimeUnit.SECONDS);
		if (platform.getTimer() != storageTimer) await(platform.getTimer(), 1, TimeUnit.SECONDS);
		if (!holdTimeTimer) await(grace.timeTimer(), 1, TimeUnit.SECONDS);
		await(platform.getInventoryTimer(), 1, TimeUnit.SECONDS);
		clean(platform.afterExecutorShutdown());
	}

	private void finishDeferredStorageTimer(ScheduledExecutorService timer, boolean forced,
			boolean runTerminalCleanup, AtomicBoolean terminalStorageCleanup) {
		Runnable retirement = () -> {
			await(timer, forced ? 1 : 2, TimeUnit.SECONDS);
			if (!forced && timer != null && !timer.isTerminated()) shutdownNow(timer);
			if (runTerminalCleanup) finishTerminalStorageCleanup(timer, terminalStorageCleanup);
		};
		Thread shutdownThread = new Thread(retirement, "AdvancedCore-Storage-Shutdown");
		shutdownThread.setDaemon(true);
		try { shutdownThread.start(); }
		catch (RuntimeException | Error startFailure) {
			platform.cleanupFailed("deferred storage shutdown continuation", startFailure);
			shutdownNow(timer);
			if (runTerminalCleanup) finishTerminalStorageCleanup(timer, terminalStorageCleanup);
		}
	}

	private void finishTerminalStorageCleanup(ScheduledExecutorService timer, AtomicBoolean once) {
		if (timer == null || timer.isTerminated()) {
			if (once.compareAndSet(false, true)) clean(platform.afterStorageExecutorShutdown());
			return;
		}
		// An interrupt-ignoring JDBC call can outlive the bounded watchdog. Its
		// native owner must remain open until the actual manager worker exits.
		Thread waiter = new Thread(() -> {
			while (!timer.isTerminated()) await(timer, 1, TimeUnit.SECONDS);
			if (once.compareAndSet(false, true)) clean(platform.afterStorageExecutorShutdown());
		}, "AdvancedCore-Storage-Owner-Cleanup");
		waiter.setDaemon(true);
		waiter.start();
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
