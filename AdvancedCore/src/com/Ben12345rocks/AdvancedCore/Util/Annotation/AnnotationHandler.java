package com.Ben12345rocks.AdvancedCore.Util.Annotation;

import java.lang.reflect.Field;
import java.util.ArrayList;

import org.bukkit.configuration.file.FileConfiguration;

public class AnnotationHandler {

	public AnnotationHandler() {
	}

	@SuppressWarnings("unchecked")
	public void load(FileConfiguration config, Object classToLoad) {
		Class<?> clazz = classToLoad.getClass();

		for (Field field : clazz.getDeclaredFields()) {
			try {
				field.setAccessible(true);

				ConfigDataString stringAnnotation = field.getAnnotation(ConfigDataString.class);
				if (stringAnnotation != null) {
					String value = config.getString(stringAnnotation.path(), stringAnnotation.defaultValue());

					field.set(classToLoad, value);

				}

				ConfigDataBoolean booleanAnnotation = field.getAnnotation(ConfigDataBoolean.class);
				if (booleanAnnotation != null) {
					boolean value = config.getBoolean(booleanAnnotation.path(), booleanAnnotation.defaultValue());

					field.set(classToLoad, value);

				}

				ConfigDataInt intAnnotation = field.getAnnotation(ConfigDataInt.class);
				if (intAnnotation != null) {
					int value = config.getInt(intAnnotation.path(), intAnnotation.defaultValue());

					field.set(classToLoad, value);

				}

				ConfigDataListString listAnnotation = field.getAnnotation(ConfigDataListString.class);
				if (listAnnotation != null) {
					ArrayList<String> value = (ArrayList<String>) config.getList(listAnnotation.path(),
							new ArrayList<String>());

					field.set(classToLoad, value);
				}

			} catch (Exception e) {
				e.printStackTrace();
			}
		}

	}

}
