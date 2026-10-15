package com.bencodez.advancedcore.lifecycle;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Non-blocking admission before native ServerData reads. Only opaque String
 * tokens live in JVM properties, so shaded plugin classloaders share admission
 * without retaining plugin objects, futures, callbacks, or classloaders there.
 */
public final class ServerDataOwnership {
    private static final String PREFIX = "AdvancedCore:ServerData-ownership:v1:";
    private static final int MAX_OWNERS = 256;
    private final Properties properties;
    private final String key;
    private final String token = UUID.randomUUID().toString();
    private boolean completionAttached;
    private boolean released;
    private final CompletableFuture<Long> shutdownStarted = new CompletableFuture<>();
    private final CompletableFuture<Void> processReleased = new CompletableFuture<>();
    private final AtomicBoolean hookRemoved = new AtomicBoolean();
    private Thread processHook;
    private Runtime processRuntime;

    private ServerDataOwnership(Properties properties, String key) {
        this.properties = properties;
        this.key = key;
    }

    /** Uses the stable platform data path; no filesystem access or predecessor wait. */
    public static ServerDataOwnership acquire(Path file) {
        String path = Objects.requireNonNull(file, "file").toAbsolutePath().normalize().toString();
        if (path.length() > 8192) throw new IllegalArgumentException("ServerData path is too long");
        String key;
        try {
            key = PREFIX + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(path.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException unavailable) { throw new IllegalStateException(unavailable); }
        Properties properties = System.getProperties();
        ServerDataOwnership ownership = new ServerDataOwnership(properties, key);
        synchronized (properties) {
            if (properties.containsKey(key)) throw new IllegalStateException(
                    "Predecessor ServerData owner has not finished; startup rejected until it settles or the JVM restarts");
            int active = 0;
            for (Object candidate : properties.keySet()) {
                if (candidate instanceof String name && name.startsWith(PREFIX) && ++active >= MAX_OWNERS)
                    throw new IllegalStateException("ServerData ownership capacity reached");
            }
            properties.setProperty(key, ownership.token);
        }
        try { ownership.installProcessHook(); }
        catch (RuntimeException | Error failure) {
            synchronized (properties) {
                if (ownership.token.equals(properties.get(key))) properties.remove(key);
            }
            throw failure;
        }
        return ownership;
    }

    private void installProcessHook() {
        // Register during startup: registering from a host's shutdown hook is too late.
        // The hook observes ownership only; it never invokes plugin or Bukkit callbacks.
        processRuntime = Runtime.getRuntime();
        processHook = new Thread(this::awaitProcessExit, "AdvancedCore-ServerData-Exit");
        processRuntime.addShutdownHook(processHook);
    }

    /** Starts once at disable entry; final admission cannot renew the same deadline. */
    public void beginShutdown(long startedNanos) {
        if (shutdownStarted.complete(startedNanos)) {
            CompletableFuture.delayedExecutor(remaining(startedNanos), TimeUnit.NANOSECONDS)
                    .execute(this::removeProcessHook);
        }
    }

    private void awaitProcessExit() {
        long processExitStarted = System.nanoTime();
        try {
            long disableStarted = shutdownStarted.get(remaining(processExitStarted), TimeUnit.NANOSECONDS);
            long budget = Math.min(remaining(processExitStarted), remaining(disableStarted));
            if (budget > 0) processReleased.get(budget, TimeUnit.NANOSECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException settledOrExpired) {
            // Publication stages retain their real outcome; timeout is never an acknowledgement.
        }
    }

    private static long remaining(long startedNanos) {
        long elapsed = Math.max(0L, System.nanoTime() - startedNanos);
        return Math.max(0L, TimeUnit.SECONDS.toNanos(15) - elapsed);
    }

    private void removeProcessHook() {
        if (!hookRemoved.compareAndSet(false, true)) return;
        try { processRuntime.removeShutdownHook(processHook); }
        catch (IllegalStateException stopping) { /* The JVM is already invoking its hooks. */ }
    }

    /**
     * Release once after successful preparation and publication settle. A failed
     * or cancelled stage can precede another admitted writer's termination, so
     * it deliberately retains admission until restart rather than guessing drain.
     * Cancelling a caller's observation must not cancel this completion stage.
     */
    public void releaseAfter(CompletionStage<Void> completion) {
        Objects.requireNonNull(completion, "completion");
        synchronized (this) {
            if (completionAttached) return;
            completionAttached = true;
        }
        beginShutdown(System.nanoTime());
        completion.whenComplete((ignored, failure) -> {
            if (failure != null) return;
            synchronized (this) {
                if (released) return;
                synchronized (properties) {
                    if (!token.equals(properties.get(key))) return;
                    properties.remove(key);
                    released = true;
                }
            }
            processReleased.complete(null);
            removeProcessHook();
        });
    }

    public synchronized boolean isReleased() { return released; }

}
