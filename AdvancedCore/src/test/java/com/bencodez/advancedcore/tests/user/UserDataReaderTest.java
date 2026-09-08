package com.bencodez.advancedcore.tests.user;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URL;
import java.net.URLClassLoader;

import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.core.user.UserDataReader;
import com.bencodez.simpleapi.sql.data.DataValue;

class UserDataReaderTest {
    @Test void fetchModes() { UserDataReaderFixture.fetchModePrecedence(); }
    @Test void invalidTemporaryFallback() { UserDataReaderFixture.invalidTemporaryValueFallsThrough(); }
    @Test void captureAndCallbackOrder() { UserDataReaderFixture.capturedCacheAndReadCallbacks(); }
    @Test void missingCacheReadCount() { UserDataReaderFixture.missingCacheDoesNotReread(); }
    @Test void scalarConversionCompatibility() { UserDataReaderFixture.stringAndIntegerConversionDifferences(); }
    @Test void sqlFailuresAndDuplicates() { UserDataReaderFixture.sqlDuplicatesAndProviderFailures(); }
    @Test void emptyKeysAndFlatFailures() { UserDataReaderFixture.emptyKeysAndFlatFallbacks(); }
    @Test void actualTemporaryMap() { UserDataReaderFixture.temporaryMapIsNotCopied(); }

    @Test void runsWithNoBukkitOnTheClasspath() throws Exception {
        URL main = UserDataReader.class.getProtectionDomain().getCodeSource().getLocation();
        URL tests = getClass().getProtectionDomain().getCodeSource().getLocation();
        URL values = DataValue.class.getProtectionDomain().getCodeSource().getLocation();
        try (var isolated = new URLClassLoader(new URL[] { main, tests, values }, ClassLoader.getPlatformClassLoader())) {
            assertThrows(ClassNotFoundException.class, () -> isolated.loadClass("org.bukkit.Bukkit"));
            Class<?> fixture = isolated.loadClass(UserDataReaderFixture.class.getName());
            assertSame(isolated, fixture.getClassLoader());
            fixture.getMethod("run").invoke(null);
        }
    }
}
