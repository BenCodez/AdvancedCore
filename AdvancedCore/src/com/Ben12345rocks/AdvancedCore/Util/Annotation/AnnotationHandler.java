package com.Ben12345rocks.AdvancedCore.Util.Annotation;

import java.lang.reflect.Field;
import java.util.ArrayList;

import org.bukkit.configuration.file.FileConfiguration;

import com.Ben12345rocks.AdvancedCore.AdvancedCoreHook;
import com.Ben12345rocks.AdvancedCore.Util.Misc.ArrayUtils;

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
					AdvancedCoreHook.getInstance().debug(stringAnnotation.path() + " = " + value);
					field.set(classToLoad, value);
					return;

				}

				ConfigDataBoolean booleanAnnotation = field.getAnnotation(ConfigDataBoolean.class);
				if (booleanAnnotation != null) {
					boolean value = config.getBoolean(booleanAnnotation.path(), booleanAnnotation.defaultValue());
					AdvancedCoreHook.getInstance().debug(booleanAnnotation.path() + " = " + value);
					field.set(classToLoad, value);
					return;

				}

				ConfigDataInt intAnnotation = field.getAnnotation(ConfigDataInt.class);
				if (intAnnotation != null) {
					int value = config.getInt(intAnnotation.path(), intAnnotation.defaultValue());
					AdvancedCoreHook.getInstance().debug(intAnnotation.path() + " = " + value);
					field.set(classToLoad, value);
					return;

				}

				ConfigDataListString listAnnotation = field.getAnnotation(ConfigDataListString.class);
				if (listAnnotation != null) {
					ArrayList<String> value = (ArrayList<String>) config.getList(listAnnotation.path(),
							new ArrayList<String>());
					AdvancedCoreHook.getInstance()
							.debug(listAnnotation.path() + " = " + ArrayUtils.getInstance().makeStringList(value));
					field.set(classToLoad, value);
					return;
				}

			} catch (Exception e) {
				e.printStackTrace();
			}
		}

	}

}
