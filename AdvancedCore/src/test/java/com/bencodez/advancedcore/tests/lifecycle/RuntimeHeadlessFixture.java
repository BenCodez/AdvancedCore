package com.bencodez.advancedcore.tests.lifecycle;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.bencodez.advancedcore.core.platform.RuntimePlatform;
import com.bencodez.advancedcore.core.runtime.AdvancedCoreRuntime;

/** Runs with only project classes and the JDK, not Bukkit or the testing libraries. */
public final class RuntimeHeadlessFixture {
    private RuntimeHeadlessFixture() { }

    public static void run() throws Exception {
        var group = AdvancedCoreRuntime.createExecutors();
        var steps = new ArrayList<String>();
        try {
            if (group.timer() == group.loginTimer() || group.loginTimer() == group.inventoryTimer()) {
                throw new AssertionError("Executor ownership was combined");
            }
            if (group.timer().submit(() -> 7).get(2, TimeUnit.SECONDS) != 7) throw new AssertionError("Task failed");
            var runtime = new AdvancedCoreRuntime(new RuntimePlatform() {
                public ScheduledExecutorService getTimer() { return group.timer(); }
                public ScheduledExecutorService getLoginTimer() { return group.loginTimer(); }
                public ScheduledExecutorService getInventoryTimer() { return group.inventoryTimer(); }
                public ScheduledExecutorService getTimeTimer() { return null; }
                public List<Cleanup> beforeExecutorShutdown() { return List.of(new Cleanup("before", () -> steps.add("before"))); }
                public List<Cleanup> afterExecutorGrace() { return List.of(new Cleanup("reward", () -> steps.add("reward"))); }
                public List<Cleanup> afterExecutorShutdown() { return List.of(new Cleanup("after", () -> steps.add("after"))); }
                public void info(String message) { steps.add("wait"); }
                public void cleanupFailed(String component, Throwable failure) { throw new AssertionError(component, failure); }
            });
            runtime.shutdown();
            if (!steps.equals(List.of("before", "wait", "reward", "after"))) throw new AssertionError(steps);
            try { group.timer().execute(() -> { }); throw new AssertionError("Accepted after shutdown"); }
            catch (RejectedExecutionException expected) { }
            if (!group.timer().isTerminated() || !group.loginTimer().isTerminated() || !group.inventoryTimer().isTerminated()) {
                throw new AssertionError("Executor did not terminate");
            }
        } finally {
            group.timer().shutdownNow(); group.loginTimer().shutdownNow(); group.inventoryTimer().shutdownNow();
        }
    }
}
