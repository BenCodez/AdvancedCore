package com.bencodez.advancedcore.nms;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * The Class NMSManager.
 */
@Deprecated
public class NMSManager {

	/** The Constant CORRESPONDING_TYPES. */
	public static final Map<Class<?>, Class<?>> CORRESPONDING_TYPES = new HashMap<>();

	/** The instance. */
	private static NMSManager instance;

	/**
	 * Equals type array.
	 *
	 * @param a the a
	 * @param o the o
	 * @return true, if successful
	 */
	public static boolean equalsTypeArray(Class<?>[] a, Class<?>[] o) {
		if (a.length != o.length) {
			return false;
		}
		for (int i = 0; i < a.length; i++) {
			if (!a[i].equals(o[i]) && !a[i].isAssignableFrom(o[i])) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Gets the.
	 *
	 * @return the NMS manager
	 */
	public static NMSManager get() {
		if (instance == null) {
			instance = new NMSManager();
		}
		return instance;
	}

	/**
	 * Gets the.
	 *
	 * @return the NMS manager
	 */
	public static NMSManager getInstance() {
		if (instance == null) {
			instance = new NMSManager();
		}
		return instance;
	}

	/**
	 * Sets the.
	 *
	 * @param object     the object
	 * @param fieldName  the field name
	 * @param fieldValue the field value
	 * @return true, if successful
	 */
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

	/**
	 * Class list equal.
	 *
	 * @param l1 the l 1
	 * @param l2 the l 2
	 * @return true, if successful
	 */
	public boolean ClassListEqual(Class<?>[] l1, Class<?>[] l2) {
		boolean equal = true;
		if (l1.length != l2.length) {
			return false;
		}
		for (int i = 0; i < l1.length; i++) {
			if (l1[i] != l2[i]) {
				equal = false;
				break;
			}
		}
		return equal;
	}

	/**
	 * Gets the field.
	 *
	 * @param clazz the clazz
	 * @param name  the name
	 * @return the field
	 */
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

	/**
	 * Gets the handle.
	 *
	 * @param obj the obj
	 * @return the handle
	 */
	public Object getHandle(Object obj) {
		try {
			return getMethod("getHandle", obj.getClass()).invoke(obj);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * Gets the method.
	 *
	 * @param clazz the clazz
	 * @param name  the name
	 * @param args  the args
	 * @return the method
	 */
	public Method getMethod(Class<?> clazz, String name, Class<?>... args) {
		for (Method m : clazz.getMethods()) {
			if (m.getName().equals(name) && (args.length == 0 || ClassListEqual(args, m.getParameterTypes()))) {
				m.setAccessible(true);
				return m;
			}
		}
		return null;
	}

	/**
	 * Gets the method.
	 *
	 * @param name       the name
	 * @param clazz      the clazz
	 * @param paramTypes the param types
	 * @return the method
	 */
	public Method getMethod(String name, Class<?> clazz, Class<?>... paramTypes) {
		Class<?>[] t = toPrimitiveTypeArray(paramTypes);
		for (Method m : clazz.getMethods()) {
			Class<?>[] types = toPrimitiveTypeArray(m.getParameterTypes());
			if (m.getName().equals(name) && equalsTypeArray(types, t)) {
				return m;
			}
		}
		return null;
	}

	/**
	 * Gets the NMS class.
	 *
	 * @param className the class name
	 * @return the NMS class
	 */
	@Deprecated
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

	/**
	 * Gets the player field.
	 *
	 * @param player the player
	 * @param name   the name
	 * @return the player field
	 * @throws SecurityException         the security exception
	 * @throws NoSuchMethodException     the no such method exception
	 * @throws NoSuchFieldException      the no such field exception
	 * @throws IllegalArgumentException  the illegal argument exception
	 * @throws IllegalAccessException    the illegal access exception
	 * @throws InvocationTargetException the invocation target exception
	 */
	public Object getPlayerField(Player player, String name) throws SecurityException, NoSuchMethodException,
			NoSuchFieldException, IllegalArgumentException, IllegalAccessException, InvocationTargetException {
		Method getHandle = player.getClass().getMethod("getHandle");
		Object nmsPlayer = getHandle.invoke(player);
		Field field = nmsPlayer.getClass().getField(name);
		return field.get(nmsPlayer);
	}

	/**
	 * Gets the primitive type.
	 *
	 * @param clazz the clazz
	 * @return the primitive type
	 */
	public Class<?> getPrimitiveType(Class<?> clazz) {
		return CORRESPONDING_TYPES.containsKey(clazz) ? CORRESPONDING_TYPES.get(clazz) : clazz;
	}

	/**
	 * Gets the version.
	 *
	 * @return the version
	 */
	public String getVersion() {
		String name = Bukkit.getServer().getClass().getPackage().getName();
		return name.substring(name.lastIndexOf('.') + 1) + ".";
	}

	/**
	 * Invoke method.
	 *
	 * @param method the method
	 * @param obj    the obj
	 * @return the object
	 */
	public Object invokeMethod(String method, Object obj) {
		try {
			return getMethod(method, obj.getClass()).invoke(obj);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * Invoke method with args.
	 *
	 * @param method the method
	 * @param obj    the obj
	 * @param args   the args
	 * @return the object
	 */
	public Object invokeMethodWithArgs(String method, Object obj, Object... args) {
		try {
			return getMethod(method, obj.getClass()).invoke(obj, args);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	public boolean isVersion(String... versions) {
		String serverVersion = Bukkit.getVersion();
		for (String version : versions) {
			if (serverVersion.contains(version)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * To primitive type array.
	 *
	 * @param classes the classes
	 * @return the class[]
	 */
	public Class<?>[] toPrimitiveTypeArray(Class<?>[] classes) {
		if (classes == null) {
			return null;
		}
		int a = classes != null ? classes.length : 0;
		Class<?>[] types = new Class<?>[a];
		for (int i = 0; i < a; i++) {
			types[i] = getPrimitiveType(classes[i]);
		}
		return types;
	}
}