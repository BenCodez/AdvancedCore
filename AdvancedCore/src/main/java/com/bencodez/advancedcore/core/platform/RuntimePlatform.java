package com.bencodez.advancedcore.core.platform;

import java.util.List;
import java.util.Objects;
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
    ScheduledExecutorService getLoginTimer();
    ScheduledExecutorService getInventoryTimer();
    ScheduledExecutorService getTimeTimer();
    List<Cleanup> beforeExecutorShutdown();
    List<Cleanup> afterExecutorGrace();
    List<Cleanup> afterExecutorShutdown();
    void info(String message);
    void cleanupFailed(String component, Throwable failure);
}
