package com.bencodez.advancedcore.tests.packaging;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.ParameterizedType;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.jar.JarFile;

import org.bukkit.entity.Entity;

import org.junit.jupiter.api.Test;

/** Verifies the actual shaded artifact rather than Maven's test classpath. */
public class PackagedArtifactTest {

    @Test
    void keepsRequiredClassesAndRelocations() throws Exception {
        Path artifactPath = packagedJar();
        try (JarFile artifact = new JarFile(artifactPath.toFile())) {
            assertNotNull(artifact.getEntry("com/bencodez/advancedcore/AdvancedCorePlugin.class"));
            assertNotNull(artifact.getEntry("com/bencodez/simpleapi/scheduler/BukkitScheduler.class"));
            assertNotNull(artifact.getEntry("com/bencodez/simpleapi/folialib/FoliaLib.class"));
            assertNotNull(artifact.getEntry("com/bencodez/simpleapi/hikari/HikariDataSource.class"));
            assertNotNull(artifact.getEntry("com/bencodez/advancedcore/rhino/Context.class"));

            assertNull(artifact.getEntry("com/tcoded/folialib/FoliaLib.class"));
            assertNull(artifact.getEntry("com/zaxxer/hikari/HikariDataSource.class"));
            assertNull(artifact.getEntry("org/mozilla/javascript/Context.class"));
        }
        System.out.printf("AdvancedCore packaged artifact: %,d bytes; required classes and relocations retained%n",
                Files.size(artifactPath));
    }

    @Test
    void packagedSchedulerUsesOneRelocatedEnumContract() throws Exception {
        Path artifactPath = packagedJar();
        String namespace = "com.bencodez.simpleapi.folialib.";
        // Isolate FoliaLib from the dependency JAR on the test classpath. Bukkit
        // stays in the parent loader; no server or live scheduler is started.
        try (URLClassLoader loader = new URLClassLoader(new URL[] { artifactPath.toUri().toURL() },
                getClass().getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!name.startsWith(namespace)) return super.loadClass(name, resolve);
                synchronized (getClassLoadingLock(name)) {
                    Class<?> loaded = findLoadedClass(name);
                    if (loaded == null) loaded = findClass(name);
                    if (resolve) resolveClass(loaded);
                    return loaded;
                }
            }
        }; JarFile artifact = new JarFile(artifactPath.toFile())) {
            Class<?> folia = loader.loadClass(namespace + "FoliaLib");
            Class<?> platform = loader.loadClass(namespace + "impl.PlatformScheduler");
            Class<?> status = loader.loadClass(namespace + "enums.EntityTaskResult");
            assertEquals(loader, folia.getClassLoader());
            assertEquals(platform, folia.getMethod("getScheduler").getReturnType());
            assertEquals(platform, folia.getMethod("getImpl").getReturnType());
            var admission = platform.getMethod("runAtEntityWithFallback", Entity.class,
                    java.util.function.Consumer.class, Runnable.class);
            ParameterizedType result = (ParameterizedType) admission.getGenericReturnType();
            assertEquals(java.util.concurrent.CompletableFuture.class, result.getRawType());
            assertEquals(status, result.getActualTypeArguments()[0]);
            assertEquals(List.of("SUCCESS", "ENTITY_RETIRED", "SCHEDULER_RETIRED"),
                    Arrays.stream(status.getEnumConstants()).map(value -> ((Enum<?>) value).name()).toList());
            assertTrue(artifact.stream().noneMatch(entry -> entry.getName().startsWith("com/tcoded/folialib/")),
                    "Original FoliaLib classes must not coexist with SimpleAPI's relocated classes");
        }
    }

    private static Path packagedJar() {
        String configured = System.getProperty("advancedcore.packagedJar");
        assertNotNull(configured, "Run this test through the Maven package lifecycle");
        Path artifact = Path.of(configured).toAbsolutePath().normalize();
        assertTrue(Files.isRegularFile(artifact), "Missing packaged artifact: " + artifact);
        return artifact;
    }
}
