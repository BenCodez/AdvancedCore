package com.bencodez.advancedcore.tests.sound;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Keep the synthetic Bukkit/Registry/Sound bootstrap out of the shared test loader. */
class SoundResolutionTest {
    private static URLClassLoader isolated;
    private static Class<?> fixture;

    @BeforeAll
    static void isolateRegistryBootstrap() throws Throwable {
        URL[] urls = Arrays.stream(System.getProperty("surefire.test.class.path").split(File.pathSeparator))
                .map(path -> {
                    try { return new File(path).toURI().toURL(); }
                    catch (java.net.MalformedURLException ex) { throw new IllegalArgumentException(ex); }
                }).toArray(URL[]::new);
        isolated = new URLClassLoader(urls, ClassLoader.getPlatformClassLoader());
        fixture = Class.forName("com.bencodez.advancedcore.tests.sound.SoundResolutionFixture", true, isolated);
        assertNotSame(SoundResolutionTest.class.getClassLoader(), fixture.getClassLoader());
        invoke(null, "initializeRegistry");
        assertSame(isolated, Class.forName("org.bukkit.Registry", false, isolated).getClassLoader());
        assertSame(isolated, Class.forName("org.bukkit.Sound", false, isolated).getClassLoader());
    }

    @AfterAll
    static void closeIsolatedLoader() throws Exception {
        if (isolated != null) { isolated.close(); }
    }

    private static void invoke(Object target, String method) throws Throwable {
        var call = fixture.getDeclaredMethod(method);
        call.setAccessible(true);
        try { call.invoke(target); }
        catch (InvocationTargetException ex) { throw ex.getCause(); }
    }

    private void check(String method) throws Throwable {
        Object instance = fixture.getDeclaredConstructor().newInstance();
        invoke(instance, "setup");
        invoke(instance, method);
    }

    @Test void editorKeysIgnoreRegistryDebugTextAndRoundTrip() throws Throwable { check("editorKeysIgnoreRegistryDebugTextAndRoundTrip"); }
    @Test void actualEditorOffersStableKeysInsteadOfDebugStrings() throws Throwable { check("actualEditorOffersStableKeysInsteadOfDebugStrings"); }
    @Test void canonicalNamespacedAndLegacyInputsResolve() throws Throwable { check("canonicalNamespacedAndLegacyInputsResolve"); }
    @Test void invalidAndUnrelatedNamesDoNotInventRegistryEntries() throws Throwable { check("invalidAndUnrelatedNamesDoNotInventRegistryEntries"); }
    @Test void playbackPreservesLocationVolumePitchAndOfflineNoop() throws Throwable { check("playbackPreservesLocationVolumePitchAndOfflineNoop"); }
    @Test void invalidRewardWarnsNormallyWithBoundedTextAndRepetition() throws Throwable { check("invalidRewardWarnsNormallyWithBoundedTextAndRepetition"); }
    @Test void namespacedClickKeysNeverUseLegacyRewrite() throws Throwable { check("namespacedClickKeysNeverUseLegacyRewrite"); }
    @Test void mixedLegacyClickAliasRemainsSupported() throws Throwable { check("mixedLegacyClickAliasRemainsSupported"); }
    @Test void clickSoundUsesSameResolverAndRetainsNoneAndFallback() throws Throwable { check("clickSoundUsesSameResolverAndRetainsNoneAndFallback"); }
}
