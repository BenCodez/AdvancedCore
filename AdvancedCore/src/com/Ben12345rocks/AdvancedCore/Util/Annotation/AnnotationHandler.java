package com.Ben12345rocks.AdvancedCore.Util.Annotation;

import java.lang.reflect.Field;
import java.util.ArrayList;

import org.bukkit.configuration.file.FileConfiguration;

import com.Ben12345rocks.AdvancedCore.AdvancedCoreHook;

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
					field.set(classToLoad, config.getString(stringAnnotation.path(), stringAnnotation.defaultValue()));
					return;

				}

				ConfigDataBoolean booleanAnnotation = field.getAnnotation(ConfigDataBoolean.class);
				if (booleanAnnotation != null) {
					field.set(classToLoad,
							config.getBoolean(booleanAnnotation.path(), booleanAnnotation.defaultValue()));
					return;

				}

				ConfigDataInt intAnnotation = field.getAnnotation(ConfigDataInt.class);
				if (intAnnotation != null) {
					field.set(classToLoad, config.getInt(intAnnotation.path(), intAnnotation.defaultValue()));
					return;

				}

				ConfigDataListString listAnnotation = field.getAnnotation(ConfigDataListString.class);
				if (listAnnotation != null) {
					field.set(classToLoad,
							(ArrayList<String>) config.getList(listAnnotation.path(), new ArrayList<String>()));
					return;
				}

			} catch (Exception e) {
				AdvancedCoreHook.getInstance().debug(e);
			}
		}

	}

}
