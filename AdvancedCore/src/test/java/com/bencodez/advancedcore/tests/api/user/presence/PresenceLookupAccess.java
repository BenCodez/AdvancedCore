package com.bencodez.advancedcore.tests.api.user.presence;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

import com.bencodez.advancedcore.api.user.presence.BukkitPlayerPresenceLookup;
import com.bencodez.advancedcore.tests.support.ReflectiveAccess;

/** Adapts test fault injectors to the existing nonpublic constructor interfaces. */
final class PresenceLookupAccess {
    private PresenceLookupAccess() { }

    @FunctionalInterface interface AttributeReader { BasicFileAttributes read(Path path) throws IOException; }
    @FunctionalInterface interface DirectoryReader { DirectoryStream<Path> open(Path path) throws IOException; }

    static BukkitPlayerPresenceLookup create(Path path) { return new BukkitPlayerPresenceLookup(path); }

    static BukkitPlayerPresenceLookup create(Path path, AttributeReader attributes) {
        Class<?> reader = nested("AttributeReader");
        return ReflectiveAccess.construct(BukkitPlayerPresenceLookup.class, new Class<?>[] { Path.class, reader },
                path, adapt(reader, "read", value -> attributes.read(value)));
    }

    static BukkitPlayerPresenceLookup create(Path path, AttributeReader attributes, DirectoryReader directories) {
        Class<?> reader = nested("AttributeReader");
        Class<?> directoryReader = nested("DirectoryReader");
        return ReflectiveAccess.construct(BukkitPlayerPresenceLookup.class,
                new Class<?>[] { Path.class, reader, directoryReader }, path,
                adapt(reader, "read", value -> attributes.read(value)),
                adapt(directoryReader, "open", value -> directories.open(value)));
    }

    private static Class<?> nested(String name) {
        return ReflectiveAccess.type(BukkitPlayerPresenceLookup.class.getName() + "$" + name);
    }

    @FunctionalInterface private interface Read { Object apply(Path path) throws IOException; }

    private static Object adapt(Class<?> type, String name, Read read) {
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, (proxy, method, arguments) -> {
            if (method.getName().equals(name)) return read.apply((Path) arguments[0]);
            throw new AssertionError("Unexpected presence-reader method: " + method);
        });
    }
}
