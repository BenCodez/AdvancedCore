package com.bencodez.advancedcore.tests.packaging;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarFile;

import org.junit.jupiter.api.Test;

/** Verifies the actual minimized artifact rather than Maven's test classpath. */
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

    private static Path packagedJar() {
        String configured = System.getProperty("advancedcore.packagedJar");
        assertNotNull(configured, "Run this test through the Maven package lifecycle");
        Path artifact = Path.of(configured).toAbsolutePath().normalize();
        assertTrue(Files.isRegularFile(artifact), "Missing packaged artifact: " + artifact);
        return artifact;
    }
}
