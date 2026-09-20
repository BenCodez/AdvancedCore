package com.bencodez.advancedcore.core.platform;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledExecutorService;

/** Supplies existing executor owners and platform cleanup without exposing game APIs. */
public interface RuntimePlatform {
    record Cleanup(String name, Runnable action) {
        public Cleanup {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(action, "action");
        }
    }

    ScheduledExecutorService getTimer();
	/** Separate manager worker that executes shared user-storage retirement, if present. */
	default ScheduledExecutorService getUserStorageTimer() { return null; }
    ScheduledExecutorService getLoginTimer();
    ScheduledExecutorService getInventoryTimer();
    ScheduledExecutorService getTimeTimer();
    List<Cleanup> beforeExecutorShutdown();

    /**
     * Completion of asynchronous pre-shutdown work started by
     * {@link #beforeExecutorShutdown()}. Implementations must keep blocking work
     * off server threads and expose its completion here so executor retirement
     * cannot overtake it.
     */
	default CompletionStage<Void> beforeExecutorShutdownCompletion() {
		return CompletableFuture.completedFuture(null);
	}

	/**
	 * Whether the current lifecycle caller may wait a bounded time for pre-shutdown
	 * work. Bukkit's disable thread must leave database retirement to its worker.
	 */
	default boolean canBlockForPreExecutorShutdown() { return true; }

	/** Maximum time a non-blocking lifecycle waits before forcing its storage worker. */
	default long deferredShutdownTimeoutMillis() { return 5_000; }

    List<Cleanup> afterExecutorGrace();

	/** Terminal cleanup that must run only after the storage executor has retired. */
	default List<Cleanup> afterStorageExecutorShutdown() { return List.of(); }

    List<Cleanup> afterExecutorShutdown();
    void info(String message);
    void cleanupFailed(String component, Throwable failure);
}
