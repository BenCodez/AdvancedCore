package com.bencodez.advancedcore.tests.lifecycle;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URL;
import java.net.URLClassLoader;

import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.core.runtime.AdvancedCoreRuntime;

class RuntimeHeadlessTest {
    @Test void runtimeExecutesAndStopsWithNoServerApiOnItsClasspath() throws Exception {
        URL main = AdvancedCoreRuntime.class.getProtectionDomain().getCodeSource().getLocation();
        URL tests = getClass().getProtectionDomain().getCodeSource().getLocation();
        try (var isolated = new URLClassLoader(new URL[] { main, tests }, ClassLoader.getPlatformClassLoader())) {
            assertThrows(ClassNotFoundException.class, () -> isolated.loadClass("org.bukkit.Bukkit"));
            assertThrows(ClassNotFoundException.class, () -> isolated.loadClass("org.junit.jupiter.api.Test"));
            Class<?> fixture = isolated.loadClass(RuntimeHeadlessFixture.class.getName());
            assertSame(isolated, fixture.getClassLoader());
            fixture.getMethod("run").invoke(null);
        }
    }
}
