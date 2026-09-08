package com.bencodez.advancedcore.lifecycle;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.bukkit.runtime.BukkitRuntimePlatform;
import com.bencodez.advancedcore.core.runtime.AdvancedCoreRuntime;
import com.bencodez.simpleapi.scheduler.BukkitScheduler;

/**
 * Existing Bukkit lifecycle facade. Public signatures and executor identity are
 * retained; the shared runtime owns the same sequencing through a Bukkit adapter.
 */
public final class AdvancedCoreLifecycle {

    private final AdvancedCorePlugin plugin;

    public AdvancedCoreLifecycle(AdvancedCorePlugin plugin) {
        this.plugin = plugin;
    }

    public static RuntimeExecutors createRuntimeExecutors(AdvancedCorePlugin plugin) {
        BukkitScheduler scheduler = new BukkitScheduler(plugin);
        AdvancedCoreRuntime.ExecutorGroup executors = AdvancedCoreRuntime.createExecutors();
        return new RuntimeExecutors(scheduler, executors.timer(), executors.loginTimer(), executors.inventoryTimer());
    }

    public void shutdown() {
        if (plugin != null) new AdvancedCoreRuntime(new BukkitRuntimePlatform(plugin)).shutdown();
    }

    static void shutdown(ScheduledExecutorService executor) {
        AdvancedCoreRuntime.shutdown(executor);
    }
    static void shutdownNow(ScheduledExecutorService executor) {
        AdvancedCoreRuntime.shutdownNow(executor);
    }
    static void await(ScheduledExecutorService executor, long timeout, TimeUnit unit) {
        AdvancedCoreRuntime.await(executor, timeout, unit);
    }

    public static final class RuntimeExecutors {
        private final BukkitScheduler bukkitScheduler;
        private final ScheduledExecutorService timer;
        private final ScheduledExecutorService loginTimer;
        private final ScheduledExecutorService inventoryTimer;

        public RuntimeExecutors(BukkitScheduler bukkitScheduler, ScheduledExecutorService timer,
                ScheduledExecutorService loginTimer, ScheduledExecutorService inventoryTimer) {
            this.bukkitScheduler = bukkitScheduler;
            this.timer = timer;
            this.loginTimer = loginTimer;
            this.inventoryTimer = inventoryTimer;
        }

        public BukkitScheduler getBukkitScheduler() { return bukkitScheduler; }
        public ScheduledExecutorService getTimer() { return timer; }
        public ScheduledExecutorService getLoginTimer() { return loginTimer; }
        public ScheduledExecutorService getInventoryTimer() { return inventoryTimer; }
    }
}
