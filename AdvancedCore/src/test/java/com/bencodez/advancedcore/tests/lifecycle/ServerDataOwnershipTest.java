package com.bencodez.advancedcore.tests.lifecycle;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import com.bencodez.advancedcore.lifecycle.ServerDataOwnership;

@ResourceLock(Resources.SYSTEM_PROPERTIES)
class ServerDataOwnershipTest {
    private static final String PREFIX = "AdvancedCore:ServerData-ownership:v1:";
    @TempDir Path directory;
    private Properties original;
    private Properties properties;

    @BeforeEach void isolateProperties() {
        original = System.getProperties();
        properties = new Properties();
        properties.putAll(original);
        System.setProperties(properties);
    }

    @AfterEach void restoreProperties() { System.setProperties(original); }

    @Test void pendingCompletionRejectsTheSameNormalizedPathAndSuccessAllowsRetry() {
        Path file = directory.resolve("ServerData.yml");
        var owner = ServerDataOwnership.acquire(file);
        var pending = new CompletableFuture<Void>();
        owner.releaseAfter(pending);
        assertFalse(owner.isReleased());
        assertThrows(IllegalStateException.class,
                () -> ServerDataOwnership.acquire(directory.resolve("unused/../ServerData.yml")));
        pending.complete(null);
        assertTrue(owner.isReleased());
        var successor = ServerDataOwnership.acquire(file);
        // A duplicate predecessor callback must never remove the successor token.
        owner.releaseAfter(CompletableFuture.completedFuture(null));
        assertThrows(IllegalStateException.class, () -> ServerDataOwnership.acquire(file));
        successor.releaseAfter(CompletableFuture.completedFuture(null));
        assertTrue(successor.isReleased());
    }

    @Test void failureAndCancellationRetainAdmissionEvenAfterALaterSuccessSignal() {
        for (boolean cancelled : new boolean[] {false, true}) {
            Path file = directory.resolve(cancelled ? "cancelled.yml" : "failed.yml");
            var owner = ServerDataOwnership.acquire(file);
            var pending = new CompletableFuture<Void>();
            owner.releaseAfter(pending);
            if (cancelled) pending.cancel(false);
            else pending.completeExceptionally(new IllegalStateException("publication failed"));
            owner.releaseAfter(CompletableFuture.completedFuture(null));
            assertFalse(owner.isReleased());
            assertThrows(IllegalStateException.class, () -> ServerDataOwnership.acquire(file));
        }
    }

    @Test void releaseUsesItsOriginatingPropertiesObject() {
        Path file = directory.resolve("ServerData.yml");
        var owner = ServerDataOwnership.acquire(file);
        String key = properties.stringPropertyNames().stream().filter(name -> name.startsWith(PREFIX))
                .findFirst().orElseThrow();
        Properties replacement = new Properties();
        replacement.putAll(properties);
        System.setProperties(replacement);
        owner.releaseAfter(CompletableFuture.completedFuture(null));
        assertTrue(owner.isReleased());
        assertFalse(properties.containsKey(key));
        assertTrue(replacement.containsKey(key), "release must not mutate another Properties generation");
    }

    @Test void independentClassloadersShareOnlyStringAdmissionTokens() throws Exception {
        Class<?> predecessorType = isolatedOwnershipType();
        Class<?> successorType = isolatedOwnershipType();
        assertNotSame(predecessorType.getClassLoader(), successorType.getClassLoader());
        Path file = directory.resolve("ServerData.yml");
        Object predecessor = predecessorType.getMethod("acquire", Path.class).invoke(null, file);
        var publication = new CompletableFuture<Void>();
        predecessorType.getMethod("releaseAfter", CompletionStage.class).invoke(predecessor, publication);
        InvocationTargetException denial = assertThrows(InvocationTargetException.class,
                () -> successorType.getMethod("acquire", Path.class).invoke(null, file));
        assertInstanceOf(IllegalStateException.class, denial.getCause());
        properties.forEach((key, value) -> {
            if (key instanceof String name && name.startsWith(PREFIX)) assertInstanceOf(String.class, value);
        });
        publication.complete(null);
        Object successor = successorType.getMethod("acquire", Path.class).invoke(null, file);
        successorType.getMethod("releaseAfter", CompletionStage.class).invoke(successor,
                CompletableFuture.completedFuture(null));
    }

    @Test void capacityIsBoundedAndAnExistingOwnerIsNeverEvicted() {
        for (int index = 0; index < 256; index++) properties.setProperty(PREFIX + "fixture-" + index, "opaque");
        assertThrows(IllegalStateException.class, () -> ServerDataOwnership.acquire(directory.resolve("new.yml")));
        properties.remove(PREFIX + "fixture-255");
        var owner = ServerDataOwnership.acquire(directory.resolve("new.yml"));
        owner.releaseAfter(CompletableFuture.completedFuture(null));
        assertEquals("opaque", properties.getProperty(PREFIX + "fixture-0"));
    }

    @Test void admissionDoesNotCreateTheDataDirectory() {
        Path file = directory.resolve("absent/ServerData.yml");
        var owner = ServerDataOwnership.acquire(file);
        assertFalse(java.nio.file.Files.exists(file.getParent()));
        owner.releaseAfter(CompletableFuture.completedFuture(null));
        assertThrows(IllegalArgumentException.class, () -> ServerDataOwnership.acquire(Path.of("a".repeat(8193))));
    }

    private static Class<?> isolatedOwnershipType() throws Exception {
        String name = ServerDataOwnership.class.getName();
        byte[] bytes;
        try (var input = ServerDataOwnership.class.getResourceAsStream("ServerDataOwnership.class")) {
            bytes = java.util.Objects.requireNonNull(input).readAllBytes();
        }
        ClassLoader loader = new ClassLoader(ClassLoader.getPlatformClassLoader()) {
            @Override protected Class<?> findClass(String requested) throws ClassNotFoundException {
                if (!requested.equals(name)) throw new ClassNotFoundException(requested);
                return defineClass(requested, bytes, 0, bytes.length);
            }
        };
        return loader.loadClass(name);
    }
}
