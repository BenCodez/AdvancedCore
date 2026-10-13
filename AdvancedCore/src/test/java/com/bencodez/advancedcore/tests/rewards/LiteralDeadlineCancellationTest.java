package com.bencodez.advancedcore.tests.rewards;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.bukkit.Bukkit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.misc.MiscUtils;
import com.bencodez.simpleapi.scheduler.BukkitScheduler;

class LiteralDeadlineCancellationTest {
    @TempDir Path temporary;

    @Test void completedDeliveryPathsRemoveTheirActualScheduledTimeouts() throws Exception {
        // Isolate the JVM's timer queue so unrelated tests cannot affect the count.
        // Only this test child opens the JDK diagnostic field; production uses public APIs.
        Path output = temporary.resolve("deadline-probe.txt");
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        Process child = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-XX:+StartAttachListener", "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED",
                "-cp", classpath, Probe.class.getName())
                .redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            assertTrue(child.waitFor(20, TimeUnit.SECONDS), "deadline probe exceeded its bound");
            assertEquals(0, child.exitValue(), () -> {
                try { return Files.readString(output); } catch (Exception failure) { return "probe output unavailable"; }
            });
            assertTrue(Files.readString(output).contains("deadline queue empty"));
        } finally { if (child.isAlive()) child.destroyForcibly(); }
    }

    public static class Probe {
        public static void main(String[] args) throws Exception {
            var owner = mock(AdvancedCorePlugin.class);
            var scheduler = mock(BukkitScheduler.class);
            when(owner.getBukkitScheduler()).thenReturn(scheduler);
            when(owner.isEnabled()).thenReturn(true);
            doAnswer(call -> { call.getArgument(1, Runnable.class).run(); return null; })
                    .when(scheduler).executeOrScheduleSync(eq(owner), any(Runnable.class));
            var misc = MiscUtils.getInstance();
            var plugin = MiscUtils.class.getDeclaredField("plugin");
            plugin.setAccessible(true); plugin.set(misc, owner);
            // Force initialization before reading the otherwise private diagnostic queue.
            new CompletableFuture<>().orTimeout(1, TimeUnit.DAYS).complete(null);
            var timer = Class.forName("java.util.concurrent.CompletableFuture$Delayer").getDeclaredField("delayer");
            timer.setAccessible(true);
            var executor = (ScheduledThreadPoolExecutor) timer.get(null);
            if (!executor.getQueue().isEmpty()) throw new AssertionError("probe queue was not isolated");
            try (var bukkit = mockStatic(Bukkit.class)) {
                bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
                for (int iteration = 0; iteration < 64; iteration++) {
                    misc.dispatchPreparedComponentLineAsync(owner, UUID.randomUUID(), player -> null).toCompletableFuture().join();
                    misc.dispatchPreparedConsoleLineAsync(owner, () -> null).toCompletableFuture().join();
                    misc.dispatchPreparedConsoleMessageAsync(owner, () -> null).toCompletableFuture().join();
                    misc.captureOnlinePlayerIdsAsync(owner).toCompletableFuture().join();
                    misc.broadcastPreparedComponentsAsync(player -> null, null).toCompletableFuture().join();
                    misc.broadcastPreparedMessageAsync(player -> null, () -> null).toCompletableFuture().join();
                }
                // Successful, failed and cancelled operations all reclaim their deadlines.
                try { misc.dispatchPreparedConsoleMessageAsync(owner, () -> { throw new IllegalStateException("synthetic failure"); })
                        .toCompletableFuture().join(); throw new AssertionError("failure was not propagated"); }
                catch (java.util.concurrent.CompletionException expected) { }
                var queued = new java.util.ArrayList<Runnable>();
                doAnswer(call -> { queued.add(call.getArgument(1)); return null; })
                        .when(scheduler).executeOrScheduleSync(eq(owner), any(Runnable.class));
                var cancelled = misc.dispatchPreparedConsoleMessageAsync(owner, () -> { throw new AssertionError("late renderer ran"); })
                        .toCompletableFuture();
                cancelled.cancel(false);
                queued.forEach(Runnable::run);
                if (!executor.getQueue().isEmpty()) throw new AssertionError("settled deliveries retained " + executor.getQueue().size() + " deadlines");
                System.out.println("deadline queue empty");
            }
        }
    }
}
