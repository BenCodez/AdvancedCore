package com.Ben12345rocks.AdvancedCore.NMSManager;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class NMSManager {
    private static NMSManager instance;

    public static NMSManager get(){
        if(instance == null)
            instance = new NMSManager();
        return instance;
    }

    public static final Map<Class<?>, Class<?>> CORRESPONDING_TYPES = new HashMap<Class<?>, Class<?>>();

    public Class<?> getPrimitiveType(Class<?> clazz) {
        return CORRESPONDING_TYPES.containsKey(clazz) ? CORRESPONDING_TYPES
                .get(clazz) : clazz;
    }

    public Class<?>[] toPrimitiveTypeArray(Class<?>[] classes) {
        int a = classes != null ? classes.length : 0;
        Class<?>[] types = new Class<?>[a];
        for (int i = 0; i < a; i++)
            types[i] = getPrimitiveType(classes[i]);
        return types;
    }

    public static boolean equalsTypeArray(Class<?>[] a, Class<?>[] o) {
        if (a.length != o.length)
            return false;
        for (int i = 0; i < a.length; i++)
            if (!a[i].equals(o[i]) && !a[i].isAssignableFrom(o[i]))
                return false;
        return true;
    }

    public Object getHandle(Object obj) {
        try {
            return getMethod("getHandle", obj.getClass()).invoke(obj);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public Object invokeMethod(String method, Object obj) {
        try {
            return getMethod(method, obj.getClass()).invoke(obj);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public Object invokeMethodWithArgs(String method, Object obj, Object... args) {
        try {
            return getMethod(method, obj.getClass()).invoke(obj, args);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public Method getMethod(String name, Class<?> clazz,
                             Class<?>... paramTypes) {
        Class<?>[] t = toPrimitiveTypeArray(paramTypes);
        for (Method m : clazz.getMethods()) {
            Class<?>[] types = toPrimitiveTypeArray(m.getParameterTypes());
            if (m.getName().equals(name) && equalsTypeArray(types, t))
                return m;
        }
        return null;
    }

    public String getVersion() {
        String name = Bukkit.getServer().getClass().getPackage().getName();
        return name.substring(name.lastIndexOf('.') + 1) + ".";
    }

    public Class<?> getNMSClass(String className) {
        String fullName = "net.minecraft.server." + getVersion() + className;
        Class<?> clazz = null;
        try {
            clazz = Class.forName(fullName);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return clazz;
    }

    public Field getField(Class<?> clazz, String name) {
        try {
            Field field = clazz.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static boolean set(Object object, String fieldName, Object fieldValue) {
        Class<?> clazz = object.getClass();
        while (clazz != null) {
            try {
                Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(object, fieldValue);
                return true;
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
        return false;
    }

    public Object getPlayerField(Player player, String name) throws SecurityException, NoSuchMethodException, NoSuchFieldException, IllegalArgumentException, IllegalAccessException, InvocationTargetException {
        Method getHandle = player.getClass().getMethod("getHandle");
        Object nmsPlayer = getHandle.invoke(player);
        Field field = nmsPlayer.getClass().getField(name);
        return field.get(nmsPlayer);
    }

    public Method getMethod(Class<?> clazz, String name, Class<?>... args) {
        for (Method m : clazz.getMethods())
            if (m.getName().equals(name)
                    && (args.length == 0 || ClassListEqual(args,
                    m.getParameterTypes()))) {
                m.setAccessible(true);
                return m;
            }
        return null;
    }

    public boolean ClassListEqual(Class<?>[] l1, Class<?>[] l2) {
        boolean equal = true;
        if (l1.length != l2.length)
            return false;
        for (int i = 0; i < l1.length; i++)
            if (l1[i] != l2[i]) {
                equal = false;
                break;
            }
        return equal;
    }
}