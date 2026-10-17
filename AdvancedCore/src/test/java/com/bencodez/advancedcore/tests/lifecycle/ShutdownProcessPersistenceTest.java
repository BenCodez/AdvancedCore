package com.bencodez.advancedcore.tests.lifecycle;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.bukkit.runtime.BukkitRuntimePlatform;
import com.bencodez.advancedcore.core.runtime.AdvancedCoreRuntime;
import com.bencodez.advancedcore.data.ServerData;
import com.bencodez.advancedcore.lifecycle.ServerDataOwnership;

class ShutdownProcessPersistenceTest {
    @TempDir Path directory;

    @Test void admittedTimestampPublishesBeforeOrderlyJvmExit() throws Exception {
        runProbe("complete");
        assertTrue(Files.exists(directory.resolve("result")), "admitted publication must survive orderly JVM exit");
        assertEquals("published", Files.readString(directory.resolve("result")));
    }

    @Test void admittedTimestampSurvivesDisableInsideAnExistingHostShutdownHook() throws Exception {
        runProbe("host-shutdown");
        assertTrue(Files.exists(directory.resolve("result")), "host-hook shutdown must preserve admitted publication");
        assertEquals("published", Files.readString(directory.resolve("result")));
    }

    @Test void blockedPublicationCannotRestartTheSharedShutdownDeadline() throws Exception {
        runProbe("blocked");
        assertFalse(Files.exists(directory.resolve("result")));
    }

    private void runProbe(String mode) throws Exception {
        var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-XX:+EnableDynamicAgentLoading", "-cp", System.getProperty("surefire.test.class.path",
                        System.getProperty("java.class.path")), Probe.class.getName(), directory.toString(), mode)
                .redirectErrorStream(true).redirectOutput(directory.resolve("child-output").toFile()).start();
        try {
            long admissionDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(12);
            while (!Files.exists(directory.resolve("ready")) && process.isAlive()
                    && System.nanoTime() < admissionDeadline) Thread.sleep(10);
            assertTrue(Files.exists(directory.resolve("ready")), "isolated JVM must reach actual shutdown");
            assertTrue(process.waitFor(4, TimeUnit.SECONDS), "final persistence cannot renew the spent shutdown budget");
            assertEquals(0, process.exitValue(), "isolated shutdown must finish cleanly");
        } finally {
            if (process.isAlive()) { process.destroyForcibly(); process.waitFor(5, TimeUnit.SECONDS); }
        }
    }

    public static final class Probe {
        public static void main(String[] args) throws Exception {
            Path directory = Path.of(args[0]);
            boolean blocked = args[1].equals("blocked");
            var plugin = mock(AdvancedCorePlugin.class);
            var owner = mock(ServerData.class);
            when(plugin.getLogger()).thenReturn(Logger.getLogger("shutdown-fixture"));
            when(plugin.getServerDataFile()).thenReturn(owner);
            var publication = new CompletableFuture<Void>();
            when(owner.mutateFinalData(any())).thenAnswer(call -> {
                if (!blocked) {
                    Thread writer = new Thread(() -> {
                        try {
                            Thread.sleep(300);
                            Files.writeString(directory.resolve("result"), "published");
                            publication.complete(null);
                        } catch (Throwable failure) { publication.completeExceptionally(failure); }
                    }, "fixture-publication");
                    writer.setDaemon(true); writer.start();
                }
                return publication;
            });
            var ownership = ServerDataOwnership.acquire(directory.resolve("ServerData.yml"));
            var lockField = AdvancedCorePlugin.class.getDeclaredField("lifecycleLock");
            lockField.setAccessible(true); lockField.set(plugin, new Object());
            var ownerField = AdvancedCorePlugin.class.getDeclaredField("serverDataOwnership");
            ownerField.setAccessible(true); ownerField.set(plugin, ownership);
            doCallRealMethod().when(plugin).hasServerDataOwnership();
            doCallRealMethod().when(plugin).releaseServerDataOwnershipAfter(any());
            var runtime = new AdvancedCoreRuntime(new BukkitRuntimePlatform(plugin));
            Files.writeString(directory.resolve("ready"), "ready");
            long started = System.nanoTime();
            if (blocked) started -= TimeUnit.SECONDS.toNanos(15) - TimeUnit.MILLISECONDS.toNanos(250);
            final long shutdownStarted = started;
            Runnable disable = () -> {
                ownership.beginShutdown(shutdownStarted);
                runtime.shutdown(shutdownStarted);
            };
            if (args[1].equals("host-shutdown")) {
                Runtime.getRuntime().addShutdownHook(new Thread(disable, "fixture-host-stop"));
            } else {
                disable.run();
            }
            System.exit(0);
        }
    }
}
