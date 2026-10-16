package com.bencodez.advancedcore.tests.support;

import java.lang.reflect.InvocationTargetException;

/** Test-only access to existing nonpublic code; production visibility remains unchanged. */
public final class ReflectiveAccess {
    private ReflectiveAccess() { }

    public static Class<?> type(String name) {
        try { return Class.forName(name); }
        catch (ClassNotFoundException failure) { throw new AssertionError(failure); }
    }

    public static <T> T construct(Class<?> type, Class<?>[] parameterTypes, Object... arguments) {
        try {
            var constructor = type.getDeclaredConstructor(parameterTypes);
            constructor.setAccessible(true);
            return cast(constructor.newInstance(arguments));
        } catch (InvocationTargetException failure) { throw propagate(failure.getCause()); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    public static <T> T invoke(Class<?> owner, Object target, String name, Class<?>[] parameterTypes,
            Object... arguments) {
        try {
            var method = owner.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            return cast(method.invoke(target, arguments));
        } catch (InvocationTargetException failure) { throw propagate(failure.getCause()); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    @SuppressWarnings("unchecked")
    private static <T> T cast(Object value) { return (T) value; }

    // Preserve the original exception type for assertThrows and fault-injection tests.
    @SuppressWarnings("unchecked")
    private static <E extends Throwable> RuntimeException propagate(Throwable failure) throws E {
        throw (E) failure;
    }
}
