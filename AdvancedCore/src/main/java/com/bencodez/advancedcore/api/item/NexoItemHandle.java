package com.bencodez.advancedcore.api.item;

import java.lang.reflect.Method;

import org.bukkit.inventory.ItemStack;

import com.bencodez.advancedcore.AdvancedCorePlugin;

/**
 * Reflection-backed Nexo integration kept isolated from the rest of ItemBuilder.
 */
public class NexoItemHandle {
	private final ClassLoader classLoader;
	private final String apiClassName;
	private final String builderClassName;
	private volatile Method itemFromIdMethod;
	private volatile Method buildMethod;
	private volatile boolean compatibilityFailureLogged;

	public NexoItemHandle() {
		this(NexoItemHandle.class.getClassLoader(), "com.nexomc.nexo.api.NexoItems", "com.nexomc.nexo.items.ItemBuilder");
	}

	protected NexoItemHandle(ClassLoader classLoader, String apiClassName, String builderClassName) {
		this.classLoader = classLoader == null ? NexoItemHandle.class.getClassLoader() : classLoader;
		this.apiClassName = apiClassName;
		this.builderClassName = builderClassName;
	}

	public ItemStack getItem(String item) {
		if (item == null || item.isEmpty()) {
			return null;
		}
		try {
			ensureMethods();
			Object itemBuilder = itemFromIdMethod.invoke(null, item);
			if (itemBuilder == null) {
				return null;
			}
			Object builtItem = buildMethod.invoke(itemBuilder);
			if (builtItem instanceof ItemStack) {
				return (ItemStack) builtItem;
			}
			logFailureOnce("Nexo build() returned an unexpected type: "
					+ (builtItem == null ? "null" : builtItem.getClass().getName()), null);
		} catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
			logFailureOnce("Unable to use the installed Nexo item API", e);
		}
		return null;
	}

	private void ensureMethods() throws ReflectiveOperationException {
		if (itemFromIdMethod != null && buildMethod != null) {
			return;
		}
		synchronized (this) {
			if (itemFromIdMethod == null) {
				Class<?> nexoItemsClass = Class.forName(apiClassName, true, classLoader);
				itemFromIdMethod = nexoItemsClass.getMethod("itemFromId", String.class);
			}
			if (buildMethod == null) {
				Class<?> itemBuilderClass = Class.forName(builderClassName, true, classLoader);
				buildMethod = itemBuilderClass.getMethod("build");
			}
		}
	}

	private void logFailureOnce(String message, Throwable throwable) {
		if (compatibilityFailureLogged) {
			return;
		}
		compatibilityFailureLogged = true;
		AdvancedCorePlugin plugin = AdvancedCorePlugin.getInstance();
		if (plugin != null) {
			plugin.getLogger().warning(message);
			if (throwable != null) {
				plugin.debug(throwable);
			}
		}
	}
}
