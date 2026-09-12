package com.bencodez.advancedcore.tests.platform;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URL;
import java.net.URLClassLoader;

import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.core.platform.PlatformServices;

class PlatformServicesHeadlessTest {
    @Test
    void commandHandoffAndPlayerContractBehavior() {
        PlatformServicesFixture.run();
    }

    @Test
    void sharedServicesRunWithoutAnyServerOrTestFramework() throws Exception {
        URL[] urls = {
                PlatformServices.class.getProtectionDomain().getCodeSource().getLocation(),
                PlatformServicesFixture.class.getProtectionDomain().getCodeSource().getLocation()
        };
        try (URLClassLoader isolated = new URLClassLoader(urls, ClassLoader.getPlatformClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("org.bukkit.") || name.startsWith("io.papermc.")
                        || name.startsWith("net.minecraft.") || name.startsWith("net.md_5.")
                        || name.startsWith("net.fabricmc.") || name.startsWith("net.minecraftforge.")
                        || name.startsWith("net.neoforged.") || name.startsWith("com.bencodez.simpleapi.")
                        || name.startsWith("com.bencodez.advancedcore.bukkit.")
                        || name.startsWith("com.bencodez.advancedcore.api.")
                        || name.equals("com.bencodez.advancedcore.AdvancedCorePlugin")
                        || name.startsWith("org.junit.") || name.startsWith("org.mockito.")) {
                    throw new ClassNotFoundException(name);
                }
                return super.loadClass(name, resolve);
            }
        }) {
            assertThrows(ClassNotFoundException.class, () -> isolated.loadClass("org.bukkit.Bukkit"));
            assertThrows(ClassNotFoundException.class, () -> isolated.loadClass("org.junit.jupiter.api.Test"));
            isolated.loadClass(PlatformServicesFixture.class.getName()).getMethod("run").invoke(null);
        }
    }
}
