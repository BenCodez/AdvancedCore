package com.Ben12345rocks.AdvancedCore.Util.Annotation;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Set;

import org.bukkit.configuration.ConfigurationSection;

public class AnnotationHandler {

	public AnnotationHandler() {
	}

	@SuppressWarnings("unchecked")
	public void load(ConfigurationSection config, Object classToLoad) {
		Class<?> clazz = classToLoad.getClass();

		for (Field field : clazz.getDeclaredFields()) {
			try {
				field.setAccessible(true);

				ConfigDataString stringAnnotation = field.getAnnotation(ConfigDataString.class);
				if (stringAnnotation != null) {

					String defaultValue = stringAnnotation.defaultValue();
					if (defaultValue.isEmpty()) {
						try {
							String v = (String) field.get(classToLoad);
							defaultValue = v;
						} catch (Exception e) {

						}
					}
					String value = config.getString(stringAnnotation.path(), defaultValue);

					field.set(classToLoad, value);

				}

				ConfigDataBoolean booleanAnnotation = field.getAnnotation(ConfigDataBoolean.class);
				if (booleanAnnotation != null) {
					boolean defaultValue = booleanAnnotation.defaultValue();
					if (!defaultValue) {
						try {
							boolean v = field.getBoolean(classToLoad);
							defaultValue = v;
						} catch (Exception e) {

						}

					}
					boolean value = config.getBoolean(booleanAnnotation.path(), defaultValue);

					field.set(classToLoad, value);

				}

				ConfigDataInt intAnnotation = field.getAnnotation(ConfigDataInt.class);
				if (intAnnotation != null) {
					int defaultValue = intAnnotation.defaultValue();
					if (defaultValue == 0) {
						try {
							int v = field.getInt(classToLoad);
							defaultValue = v;
						} catch (Exception e) {

						}
					}
					int value = config.getInt(intAnnotation.path(), defaultValue);

					field.set(classToLoad, value);

				}

				ConfigDataListString listAnnotation = field.getAnnotation(ConfigDataListString.class);
				if (listAnnotation != null) {
					ArrayList<String> defaultValue = new ArrayList<String>();
					try {
						ArrayList<String> v = (ArrayList<String>) field.get(classToLoad);
						defaultValue = v;
					} catch (Exception e) {

					}
					ArrayList<String> value = (ArrayList<String>) config.getList(listAnnotation.path(), defaultValue);

					field.set(classToLoad, value);
				}

				ConfigDataKeys setAnnotation = field.getAnnotation(ConfigDataKeys.class);
				if (listAnnotation != null) {
					Set<String> value = config.getConfigurationSection(setAnnotation.path()).getKeys(false);

					field.set(classToLoad, value);
				}

			} catch (Exception e) {
				e.printStackTrace();
			}
		}

	}

}
