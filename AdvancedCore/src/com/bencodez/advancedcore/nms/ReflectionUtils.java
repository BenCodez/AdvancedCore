package com.bencodez.advancedcore.nms;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * A collection of Reflection Methods that return null rather than throwing
 * Exceptions for cleaner code
 *
 * @author Florian
 *
 */
public class ReflectionUtils {

	public static Object constructObject(Constructor<?> constructor, Object... args) {
		try {
			return constructor.newInstance(args);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	public static Class<?> getClassForName(String name) {
		try {
			return Class.forName(name.replaceAll("/", "."));
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	public static Object getFieldValue(Field field, Object object) {
		try {
			boolean a = !field.isAccessible();
			if (a) {
				field.setAccessible(true);
			}
			Object fieldValue = field.get(object);
			if (a) {
				field.setAccessible(false);
			}
			return fieldValue;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	public static Object invokeMethod(Method method, Object object, Object... args) {
		try {
			boolean a = !method.isAccessible();
			if (!method.isAccessible()) {
				method.setAccessible(true);
			}
			Object obj = method.invoke(object, args);
			if (a) {
				method.setAccessible(false);
			}
			return obj;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	public static int removeModifier(Object fieldOrMethod, int modifierToRemove) {
		if (!(fieldOrMethod instanceof Field) || !(fieldOrMethod instanceof Method)) {
			return -1;
		}

		ReflectionUtils reflect = new ReflectionUtils(fieldOrMethod, fieldOrMethod.getClass());
		Field mods = reflect.getFieldDeclared("modifiers");
		int oldMods = (int) getFieldValue(mods, fieldOrMethod);
		int newMods = oldMods & ~modifierToRemove;
		setField(mods, fieldOrMethod, newMods);
		return oldMods;
	}

	/**
	 * Sets the value of the specified {@code Field}
	 *
	 * @param field    The field who's value should be changed
	 * @param object   object from which the represented field's value is to be
	 *                 extracted
	 * @param newValue the new value for the field of {@code object} being modified
	 * @return The old value of this Field
	 */
	public static Object setField(Field field, Object object, Object newValue) {
		try {
			boolean a = !field.isAccessible();
			if (a) {
				field.setAccessible(true);
			}
			Object oldValue = field.get(object);
			field.set(object, newValue);
			if (a) {
				field.setAccessible(false);
			}
			return oldValue;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * Sets the modifiers of the specified Field or Method
	 *
	 * @param fieldOrMethod The object that represents the Field / Method
	 * @param newMods       the new modifiers
	 * @return the old modifiers
	 */
	public static int setModifiers(Object fieldOrMethod, int newMods) {
		if (!(fieldOrMethod instanceof Field) || !(fieldOrMethod instanceof Method)) {
			return -1;
		}

		ReflectionUtils reflect = new ReflectionUtils(fieldOrMethod, fieldOrMethod.getClass());
		Field mods = reflect.getFieldDeclared("modifiers");
		int oldMods = (int) getFieldValue(mods, fieldOrMethod);
		setField(mods, fieldOrMethod, newMods);
		return oldMods;
	}

	private Object object;

	private Class<?> clazz;

	public ReflectionUtils(Object object, Class<?> clazz) {
		if (clazz == null) {
			throw new IllegalArgumentException("The specified class may not be null");
		}
		this.clazz = clazz;
		this.object = object;
	}

	public Class<?> getClazz() {
		return clazz;
	}

	public Constructor<?> getConstructor(Class<?>... params) {
		try {
			return clazz.getConstructor(params);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	public Field getFieldDeclared(String name) {
		try {
			return getClazz().getDeclaredField(name);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	public Method getMethodDeclared(String name, Class<?>... params) {
		try {
			return getClazz().getDeclaredMethod(name, params);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	public Object getObject() {
		return object;
	}

	public Field getSuperClassField(String name) {
		try {
			return getClazz().getSuperclass().getDeclaredField(name);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	public Method getSuperClassMethod(String name, Class<?>... params) {
		try {
			return getClazz().getSuperclass().getDeclaredMethod(name, params);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
}