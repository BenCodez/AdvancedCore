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
import java.util.function.LongSupplier;
import java.util.function.BiConsumer;

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
    private static final long SHUTDOWN_BUDGET_NANOS = TimeUnit.SECONDS.toNanos(15);
    private final RuntimePlatform platform;
    private final LongSupplier nanoTime;
    private volatile long shutdownStartedNanos;
	private enum CleanupState { SUCCESS, FAILURE, DEFERRED }
	private record ExecutorGrace(ScheduledExecutorService timeTimer) { }

    public AdvancedCoreRuntime(RuntimePlatform platform) {
        this(platform, System::nanoTime);
    }

    /** Clock injection keeps aggregate-deadline tests independent of wall-clock sleeps. */
    public AdvancedCoreRuntime(RuntimePlatform platform, LongSupplier nanoTime) {
        this.platform = Objects.requireNonNull(platform, "platform");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
    }

    /** The same three independent executor owners used by the Bukkit plugin. */
    public record ExecutorGroup(ScheduledExecutorService timer, ScheduledExecutorService loginTimer,
            ScheduledExecutorService inventoryTimer) { }

    public static ExecutorGroup createExecutors() {
        ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(daemonThreads("AdvancedCore-Storage"));
        ScheduledExecutorService loginTimer = null;
        try {
            loginTimer = Executors.newSingleThreadScheduledExecutor(daemonThreads("AdvancedCore-Login"));
            return new ExecutorGroup(timer, loginTimer, Executors.newSingleThreadScheduledExecutor(daemonThreads("AdvancedCore-Inventory")));
        } catch (RuntimeException | Error failure) {
            timer.shutdownNow();
            if (loginTimer != null) loginTimer.shutdownNow();
            throw failure;
        }
    }

	public static java.util.concurrent.ThreadFactory daemonThreads(String name) {
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
		shutdown(nanoTime.getAsLong());
	}

	/** The Bukkit facade supplies the timestamp captured at plugin-disable entry. */
	public void shutdown(long startedNanos) {
		shutdownStartedNanos = startedNanos;
		platform.beginShutdown(this::remainingShutdownNanos);
		clean(platform.beforeExecutorShutdown());
		CompletionStage<Void> retirement = platform.beforeExecutorShutdownCompletion();
		CleanupState retirementState = awaitCleanup(retirement, "pre-executor shutdown");
		boolean holdTimeTimer = retirementState == CleanupState.DEFERRED
				&& platform.holdTimeTimerUntilPreExecutorShutdownCompletion();
		ExecutorGrace grace = beginExecutorGrace(retirementState == CleanupState.SUCCESS);
		if (retirementState == CleanupState.DEFERRED) {
			finishDeferredCleanup(retirement, "pre-executor shutdown", grace, holdTimeTimer);
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
		// Stop new timer submissions and let work accepted before admission closed
		// drain before lifecycle-thread cleanup tears down its listeners and owners.
		shutdown(timeTimer);
        shutdown(platform.getInventoryTimer());

		platform.info("Allowing background tasks to finish before shutdown");
		awaitWithinBudget(platform.getLoginTimer(), 2, TimeUnit.SECONDS);
		if (stopStorageTimer) awaitWithinBudget(platform.getTimer(), 2, TimeUnit.SECONDS);
		awaitWithinBudget(timeTimer, 2, TimeUnit.SECONDS);
        awaitWithinBudget(platform.getInventoryTimer(), 1, TimeUnit.SECONDS);
		return new ExecutorGrace(timeTimer);
	}

	private void finishAfterExecutorGrace(ExecutorGrace grace, boolean storageTimerAlreadyForced) {
		clean(platform.afterExecutorGrace());
		shutdownNow(platform.getLoginTimer());
		if (!storageTimerAlreadyForced) shutdownNow(platform.getTimer());
		clean(List.of(new Cleanup("forced time transition shutdown",
				platform::beforeForcedTimeTimerShutdown)));
		shutdownNow(grace.timeTimer());
        shutdownNow(platform.getInventoryTimer());
        awaitWithinBudget(platform.getLoginTimer(), 1, TimeUnit.SECONDS);
		awaitWithinBudget(platform.getTimer(), 1, TimeUnit.SECONDS);
		awaitWithinBudget(grace.timeTimer(), 1, TimeUnit.SECONDS);
        awaitWithinBudget(platform.getInventoryTimer(), 1, TimeUnit.SECONDS);
		ScheduledExecutorService storageTimer = platform.getUserStorageTimer();
		if (storageTimer != null && storageTimer != platform.getTimer()) {
			shutdown(storageTimer);
			awaitWithinBudget(storageTimer, 2, TimeUnit.SECONDS);
			if (!storageTimer.isTerminated()) shutdownNow(storageTimer);
			awaitWithinBudget(storageTimer, 1, TimeUnit.SECONDS);
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
			else future.get(Math.min(TimeUnit.SECONDS.toNanos(PRE_SHUTDOWN_WAIT_SECONDS), remainingShutdownNanos()), TimeUnit.NANOSECONDS);
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

	/** Bound deferred retirement while preserving admitted transition dependencies. */
	private void finishDeferredCleanup(CompletionStage<Void> completion, String component, ExecutorGrace grace,
			boolean holdTimeTimer) {
		ScheduledExecutorService timer = platform.getUserStorageTimer();
		if (timer == null) timer = platform.getTimer();
		final ScheduledExecutorService storageTimer = timer;
		AtomicBoolean finished = new AtomicBoolean();
		AtomicBoolean terminalStorageCleanup = new AtomicBoolean();
		AtomicBoolean retirementFailureReported = new AtomicBoolean();
		if (holdTimeTimer) clean(List.of(new Cleanup("time change cancellation",
				platform::beforeDeferredPlatformCleanup)));
		// Thread-confined Bukkit cleanup must remain on the lifecycle thread. An
		// active transition is first cancelled above and its lease still gates
		// storage retirement through completion.
		finishDeferredPlatformCleanup(grace, storageTimer, holdTimeTimer);
		BiConsumer<Void, Throwable> retirementFinished = (ignored, failure) -> {
			if (failure != null && retirementFailureReported.compareAndSet(false, true)) {
				Throwable cause = failure instanceof CompletionException && failure.getCause() != null
						? failure.getCause() : failure;
				platform.cleanupFailed(component, cause);
			}
			if (!finished.compareAndSet(false, true)) {
				if (failure != null) finishDeferredStorageTimer(storageTimer, true, true, terminalStorageCleanup);
				return;
			}
			if (failure == null) shutdown(storageTimer);
			else {
				shutdownNow(storageTimer);
			}
			if (holdTimeTimer) shutdownNow(grace.timeTimer());
			finishDeferredStorageTimer(storageTimer, failure != null, failure != null, terminalStorageCleanup);
		};
		completion.whenComplete(retirementFinished);
		long timeoutMillis = Math.max(1, platform.deferredShutdownTimeoutMillis());
		Runnable forceRetirement = () -> {
			if (finished.get()) return;
			CompletionStage<Void> admitted = platform.admittedStorageRetirementCompletion();
			if (admitted != null) {
				platform.cleanupFailed(component, new TimeoutException(
						"Deferred storage retirement exceeded " + timeoutMillis
						+ " ms; retaining accepted retirement until completion"));
				// The accepted barrier/final flush owns this daemon worker. A
				// deadline or an interruption request cannot prove its completion,
				// and shutting the worker down can discard a queued final task.
				admitted.whenComplete(retirementFinished);
				return;
			}
			if (!finished.compareAndSet(false, true)) return;
			platform.cleanupFailed(component, new TimeoutException(
					"Deferred storage retirement exceeded " + timeoutMillis + " ms"));
			if (holdTimeTimer) shutdownNow(grace.timeTimer());
			// The forced time-transition hook may just have queued the final storage
			// flush. Stop new admissions but give that queued retirement one bounded
			// worker grace before forcing it and falling back to terminal cleanup.
			shutdown(storageTimer);
			finishDeferredStorageTimer(storageTimer, false, true, terminalStorageCleanup);
		};
		Runnable timeout = () -> {
			if (finished.get()) return;
			// Mark a still-draining time transition recoverable before racing its
			// final lease acknowledgement. This may start consumer preparation.
			if (holdTimeTimer) {
				clean(List.of(new Cleanup("forced time transition shutdown",
						platform::beforeForcedTimeTimerShutdown)));
				// Consumer preparation retains only storage ownership. Its completion
				// must not delay interruption of the fenced calendar worker.
				shutdownNow(grace.timeTimer());
			}
			CompletionStage<Void> preparation = platform.storageRetirementPreparationCompletion();
			if (preparation != null && !preparation.toCompletableFuture().isDone()) {
				platform.cleanupFailed(component, new TimeoutException(
						"Consumer storage preparation exceeded " + timeoutMillis
						+ " ms; retaining storage until preparation completes"));
				// Keep the daemon storage worker open for the accepted checkpoint.
				// A late admission must not immediately discard its newly queued final
				// flush just because the caller's deadline already expired. Its actual
				// retirement stage above still owns graceful shutdown and provider close.
				preparation.whenComplete((ignored, failure) -> {
					if (remainingShutdownNanos() > 0L) scheduleStorageWatchdog(timeoutMillis, forceRetirement);
				});
				return;
			}
			forceRetirement.run();
		};
		scheduleStorageWatchdog(timeoutMillis, timeout);
	}

	private void scheduleStorageWatchdog(long timeoutMillis, Runnable timeout) {
		long delayNanos = Math.min(TimeUnit.MILLISECONDS.toNanos(timeoutMillis), remainingShutdownNanos());
		try { CompletableFuture.delayedExecutor(delayNanos, TimeUnit.NANOSECONDS).execute(timeout); }
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
		awaitWithinBudget(platform.getLoginTimer(), 1, TimeUnit.SECONDS);
		if (platform.getTimer() != storageTimer) awaitWithinBudget(platform.getTimer(), 1, TimeUnit.SECONDS);
		if (!holdTimeTimer) awaitWithinBudget(grace.timeTimer(), 1, TimeUnit.SECONDS);
		awaitWithinBudget(platform.getInventoryTimer(), 1, TimeUnit.SECONDS);
		clean(platform.afterExecutorShutdown());
	}

	private void finishDeferredStorageTimer(ScheduledExecutorService timer, boolean forced,
			boolean runTerminalCleanup, AtomicBoolean terminalStorageCleanup) {
		Runnable retirement = () -> {
			awaitWithinBudget(timer, forced ? 1 : 2, TimeUnit.SECONDS);
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

    private long remainingShutdownNanos() {
        long elapsed = Math.max(0L, nanoTime.getAsLong() - shutdownStartedNanos);
        return elapsed >= SHUTDOWN_BUDGET_NANOS ? 0L : SHUTDOWN_BUDGET_NANOS - elapsed;
    }

    private void awaitWithinBudget(ScheduledExecutorService executor, long timeout, TimeUnit unit) {
        if (executor == null) return;
        long remaining = remainingShutdownNanos();
        if (remaining == 0L) return;
        if (remaining >= unit.toNanos(timeout)) await(executor, timeout, unit);
        else await(executor, remaining, TimeUnit.NANOSECONDS);
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
