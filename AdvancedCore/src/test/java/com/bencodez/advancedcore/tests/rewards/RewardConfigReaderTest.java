package com.bencodez.advancedcore.tests.rewards;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URL;
import java.net.URLClassLoader;

import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.core.rewards.RewardConfigReader;
import com.bencodez.simpleapi.core.config.StructuredConfigView;

class RewardConfigReaderTest {
    @Test
    void sharedSettingsAndNestedDefinitions() {
        CoreRewardConfigFixture.run();
    }

    @Test
    void sharedReaderRunsWithoutServerOrTestFrameworkClasses() throws Exception {
        URL[] classpath = {
                RewardConfigReader.class.getProtectionDomain().getCodeSource().getLocation(),
                StructuredConfigView.class.getProtectionDomain().getCodeSource().getLocation(),
                CoreRewardConfigFixture.class.getProtectionDomain().getCodeSource().getLocation()
        };
        try (URLClassLoader isolated = new URLClassLoader(classpath, ClassLoader.getPlatformClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("org.bukkit.") || name.startsWith("net.md_5.")
                        || name.startsWith("org.junit.") || name.startsWith("org.mockito.")
                        || name.startsWith("org.spongepowered.")
                        || name.startsWith("com.bencodez.advancedcore.bukkit.")
                        || name.startsWith("com.bencodez.advancedcore.api.")
                        || name.equals("com.bencodez.advancedcore.AdvancedCorePlugin")) {
                    throw new ClassNotFoundException(name);
                }
                return super.loadClass(name, resolve);
            }
        }) {
            assertThrows(ClassNotFoundException.class, () -> isolated.loadClass("org.bukkit.Bukkit"));
            assertThrows(ClassNotFoundException.class, () -> isolated.loadClass("org.junit.jupiter.api.Test"));
            Class<?> fixture = isolated.loadClass(CoreRewardConfigFixture.class.getName());
            fixture.getMethod("run").invoke(null);
        }
    }
}
