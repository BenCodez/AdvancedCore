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
        return ownership;
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
        });
    }

    public synchronized boolean isReleased() { return released; }

}
