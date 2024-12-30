package com.bencodez.advancedcore.api.javascript;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import com.bencodez.simpleapi.nms.ReflectionUtils;

public class JavascriptEngineHandler {

	private static JavascriptEngineHandler instance = new JavascriptEngineHandler();

	public static JavascriptEngineHandler getInstance() {
		return instance;
	}

	private boolean builtIn = false;

	private Class<?> factory;

	private Method methodToUse;

	public JavascriptEngineHandler() {
		if (Double.parseDouble(System.getProperty("java.specification.version")) < 15) {
			builtIn = true;
		} else {
			try {
				builtIn = false;
				factory = ReflectionUtils
						.getClassForName("org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory");
				for (Method m : factory.getDeclaredMethods()) {
					if (m.getParameterCount() == 0) {
						if (m.getName().equals("getScriptEngine")) {
							methodToUse = m;
						}
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

	}

	public ScriptEngine getJSScriptEngine() {
		if (builtIn) {
			return new ScriptEngineManager().getEngineByName("js");
		}
		if (factory != null) {
			try {
				return (ScriptEngine) methodToUse.invoke(factory.newInstance(), new Object[] {});
			} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException
					| InstantiationException e) {
				e.printStackTrace();
			}
		}
		return null;
	}
}
