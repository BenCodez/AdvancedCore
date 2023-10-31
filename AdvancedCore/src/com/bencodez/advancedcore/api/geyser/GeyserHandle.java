package com.bencodez.advancedcore.api.geyser;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;

public class GeyserHandle {
	private Class<?> geyserClass;
	private Object geyserAPI;
	private Method method;
	private boolean geyserExists = false;

	public void load() {
		try {
			geyserClass = Class.forName("org.geysermc.geyser.api.GeyserApi");

			geyserAPI = geyserClass.getMethod("api", new Class<?>[] {}).invoke(geyserClass, new Object[] {});

			method = geyserAPI.getClass().getMethod("isBedrockPlayer", UUID.class);
			geyserExists = true;
		} catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException
				| InvocationTargetException | ClassNotFoundException e) {
			e.printStackTrace();
		}
	}

	public boolean isGeyserPlayer(UUID player) {
		if (geyserExists) {
			Object value;
			try {
				value = method.invoke(geyserAPI, player);

				if (value instanceof Boolean) {
					return (boolean) value;
				}
			} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
				e.printStackTrace();
			}
		}
		return false;
	}
}
