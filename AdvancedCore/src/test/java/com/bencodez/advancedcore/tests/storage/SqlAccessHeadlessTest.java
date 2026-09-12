package com.bencodez.advancedcore.tests.storage;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URL;
import java.net.URLClassLoader;

import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.core.user.storage.SqlUserDataAccess;
import com.bencodez.simpleapi.sql.Column;

class SqlAccessHeadlessTest {
    @Test
    void sharedTypesAndBehaviorLoadWithBukkitAndPluginClassesDenied() throws Exception {
        URL[] paths = { SqlUserDataAccess.class.getProtectionDomain().getCodeSource().getLocation(),
                Column.class.getProtectionDomain().getCodeSource().getLocation(),
                SqlAccessHeadlessFixture.class.getProtectionDomain().getCodeSource().getLocation() };
        try (URLClassLoader loader = new URLClassLoader(paths, ClassLoader.getPlatformClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("org.bukkit.") || name.startsWith("io.papermc.")
                        || name.startsWith("net.minecraft.") || name.startsWith("net.fabricmc.")
                        || name.startsWith("net.minecraftforge.") || name.startsWith("net.neoforged.")
                        || name.startsWith("com.bencodez.advancedcore.bukkit.")
                        || name.equals("com.bencodez.advancedcore.AdvancedCorePlugin")
                        || name.equals("com.bencodez.advancedcore.api.user.AdvancedCoreUser")
                        || name.equals("com.bencodez.advancedcore.api.user.UserData")) {
                    throw new ClassNotFoundException("Platform class denied: " + name);
                }
                return super.loadClass(name, resolve);
            }
        }) {
            assertThrows(ClassNotFoundException.class, () -> loader.loadClass("org.bukkit.Bukkit"));
            assertThrows(ClassNotFoundException.class, () -> loader.loadClass("com.bencodez.advancedcore.AdvancedCorePlugin"));
            loader.loadClass("com.bencodez.advancedcore.tests.storage.SqlAccessHeadlessFixture").getMethod("run").invoke(null);
        }
    }
}
