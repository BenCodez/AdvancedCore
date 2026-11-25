package com.bencodez.advancedcore.tests.javascript;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.script.ScriptEngine;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.javascript.JavascriptEngineHandler;

class JavascriptLoaderOwnershipTest {
    @TempDir Path directory;
    @Test void throwingConstructorClosesOnlyUnpublishedLoader() throws Exception { exercise("constructor"); }
    @Test void throwingFactoryMethodClosesOnlyUnpublishedLoader() throws Exception { exercise("method"); }
    @Test void missingFactoryMethodStillClosesLoader() throws Exception { exercise("missing"); }
    @Test void successfulLoaderRemainsOwnedUntilExplicitClose() throws Exception { exercise("success"); }
    @Test void diagnosticLinkageFailureCannotReclassifyAPublishedEngine() throws Exception { exercise("metadata"); }

    @Test void malformedOptionalFactoryRetainsThePreviousLoaderAndAllowsFallback() throws Exception {
        Path folder = directory.resolve("libs/nashorn/15.7"); Files.createDirectories(folder);
        try (var jar = new JarOutputStream(Files.newOutputStream(folder.resolve("nashorn-core-15.7.jar")))) {
            jar.putNextEntry(new JarEntry("org/openjdk/nashorn/api/scripting/NashornScriptEngineFactory.class"));
            jar.write(new byte[]{1, 2, 3}); jar.closeEntry();
        }
        for (String name : new String[]{"asm", "asm-commons", "asm-analysis", "asm-tree", "asm-util"}) {
            try (var jar = new JarOutputStream(Files.newOutputStream(folder.resolve(name + "-9.8.jar")))) { }
        }
        var plugin = mock(AdvancedCorePlugin.class);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
        var handler = new JavascriptEngineHandler(); handler.setPlugin(plugin); handler.setAutoDownload(false);
        Path previousFolder = directory.resolve("previous"); Files.createDirectories(previousFolder);
        Files.writeString(previousFolder.resolve("marker.txt"), "previous");
        try (var previous = new URLClassLoader(new URL[]{previousFolder.toUri().toURL()}, null)) {
            handler.setNashornClassLoader(previous);
            Method load = JavascriptEngineHandler.class.getDeclaredMethod("getDownloadedEngine", String.class, String.class);
            load.setAccessible(true);
            assertNull(assertDoesNotThrow(() -> load.invoke(handler, "15.7", "9.8")),
                    "a malformed optional provider must return unavailable so the caller can try fallback");
            assertSame(previous, handler.getNashornClassLoader());
            assertNotNull(previous.getResource("marker.txt"));
        }
    }

    private void exercise(String mode) throws Exception {
        String propertyKey = "test.javascript.loader." + UUID.randomUUID();
        String marker = "marker-" + UUID.randomUUID() + ".txt";
        String constructor = mode.equals("constructor") ? "throw new IllegalStateException(\"constructor refused\");" : "";
        String method = mode.equals("missing") ? "" : "public javax.script.ScriptEngine getScriptEngine(){"
                + (mode.equals("method") ? "throw new IllegalStateException(\"factory refused\");" : "return new Engine();") + "}";
        String source = "package org.openjdk.nashorn.api.scripting; public class NashornScriptEngineFactory {"
                + "static { System.getProperties().put(\"" + propertyKey + "\",NashornScriptEngineFactory.class.getClassLoader()); }"
                + "public NashornScriptEngineFactory(){" + constructor + "}" + method
                + "static class Engine extends javax.script.AbstractScriptEngine {"
                + "public Object eval(String s,javax.script.ScriptContext c){return null;}"
                + "public Object eval(java.io.Reader r,javax.script.ScriptContext c){return null;}"
                + "public javax.script.Bindings createBindings(){return new javax.script.SimpleBindings();}"
                + "public javax.script.ScriptEngineFactory getFactory(){"
                + (mode.equals("metadata") ? "throw new NoClassDefFoundError(\"metadata unavailable\");" : "return null;") + "}}}";
        Path sourceFile = directory.resolve("source/org/openjdk/nashorn/api/scripting/NashornScriptEngineFactory.java");
        Files.createDirectories(sourceFile.getParent()); Files.writeString(sourceFile, source);
        Path classes = directory.resolve("classes"); Files.createDirectories(classes);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, "-d", classes.toString(), sourceFile.toString()));
        Path folder = directory.resolve("libs/nashorn/15.7"); Files.createDirectories(folder);
        try (var jar = new JarOutputStream(Files.newOutputStream(folder.resolve("nashorn-core-15.7.jar")))) {
            try (var files = Files.walk(classes)) {
                for (Path file : files.filter(Files::isRegularFile).toList()) {
                    jar.putNextEntry(new JarEntry(classes.relativize(file).toString().replace('\\', '/')));
                    Files.copy(file, jar); jar.closeEntry();
                }
            }
            jar.putNextEntry(new JarEntry(marker)); jar.write(1); jar.closeEntry();
        }
        for (String name : new String[]{"asm", "asm-commons", "asm-analysis", "asm-tree", "asm-util"}) {
            try (var jar = new JarOutputStream(Files.newOutputStream(folder.resolve(name + "-9.8.jar")))) { }
        }
        var plugin = mock(AdvancedCorePlugin.class); when(plugin.getDataFolder()).thenReturn(directory.toFile()); when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
        var handler = new JavascriptEngineHandler(); handler.setPlugin(plugin); handler.setAutoDownload(false);
        Path oldFolder = directory.resolve("old"); Files.createDirectories(oldFolder); Files.writeString(oldFolder.resolve(marker), "previous");
        try (var previous = new URLClassLoader(new URL[]{oldFolder.toUri().toURL()}, null)) {
            handler.setNashornClassLoader(previous);
            URLClassLoader candidate = null;
            try {
                Object engine;
                if (mode.equals("metadata")) {
                    assertFalse(Files.exists(directory.resolve("libs/nashorn/15.4")), "no fallback provider is available");
                    ClassLoader context = Thread.currentThread().getContextClassLoader();
                    try {
                        Thread.currentThread().setContextClassLoader(ClassLoader.getPlatformClassLoader());
                        handler.setEnabled(true);
                        assertTrue(handler.prepareEngine(), "metadata failure cannot make a published engine unavailable");
                        engine = handler.getJSScriptEngine();
                        assertSame(engine, handler.getCachedEngine()); assertTrue(handler.isPrepared());
                        assertEquals("15.7", handler.getLoadedVersion());
                    } finally { Thread.currentThread().setContextClassLoader(context); }
                    assertFalse(Files.exists(directory.resolve("libs/nashorn/15.4")), "fallback was not needed");
                } else {
                    Method load = JavascriptEngineHandler.class.getDeclaredMethod("getDownloadedEngine", String.class, String.class);
                    load.setAccessible(true); engine = load.invoke(handler, "15.7", "9.8");
                }
                candidate = (URLClassLoader) System.getProperties().get(propertyKey); assertNotNull(candidate);
                if (mode.equals("success") || mode.equals("metadata")) {
                    assertInstanceOf(ScriptEngine.class, engine); assertSame(candidate, handler.getNashornClassLoader());
                    assertNotNull(candidate.getResource(marker)); assertNull(previous.getResource(marker));
                    handler.closeExistingClassLoader(); assertNull(candidate.getResource(marker));
                } else {
                    assertNull(engine); assertSame(previous, handler.getNashornClassLoader()); assertNotNull(previous.getResource(marker));
                    assertNull(candidate.getResource(marker), "failed attempt must close its own unpublished loader");
                }
            } finally {
                System.getProperties().remove(propertyKey);
                if (candidate != null) candidate.close();
                handler.closeExistingClassLoader();
            }
        }
    }
}
