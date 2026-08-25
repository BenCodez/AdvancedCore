package com.bencodez.advancedcore.api.item;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

import org.bukkit.inventory.ItemStack;

import com.bencodez.advancedcore.AdvancedCorePlugin;

/**
 * Reflection-backed Nexo integration kept isolated from the rest of ItemBuilder.
 */
public class NexoItemHandle {
	private static final Map<ClassLoader, Map<String, WeakReference<ReflectionCache>>> CACHES = new WeakHashMap<>();

	private final ReflectionCache cache;

	public NexoItemHandle() {
		this(NexoItemHandle.class.getClassLoader(), "com.nexomc.nexo.api.NexoItems", "com.nexomc.nexo.items.ItemBuilder");
	}

	protected NexoItemHandle(ClassLoader classLoader, String apiClassName, String builderClassName) {
		ClassLoader effectiveClassLoader = classLoader == null ? NexoItemHandle.class.getClassLoader() : classLoader;
		cache = sharedCache(effectiveClassLoader, apiClassName, builderClassName);
	}

	public ItemStack getItem(String item) {
		if (item == null || item.isEmpty()) {
			return null;
		}
		try {
			ensureMethods();
			Object itemBuilder = cache.itemFromIdMethod.invoke(null, item);
			if (itemBuilder == null) {
				return null;
			}
			Object builtItem = cache.buildMethod.invoke(itemBuilder);
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
		if (cache.itemFromIdMethod != null && cache.buildMethod != null) {
			return;
		}
		synchronized (cache) {
			if (cache.itemFromIdMethod == null) {
				Class<?> nexoItemsClass = Class.forName(cache.apiClassName, true, cache.classLoader);
				cache.itemFromIdMethod = nexoItemsClass.getMethod("itemFromId", String.class);
			}
			if (cache.buildMethod == null) {
				Class<?> itemBuilderClass = Class.forName(cache.builderClassName, true, cache.classLoader);
				cache.buildMethod = itemBuilderClass.getMethod("build");
			}
		}
	}

	private void logFailureOnce(String message, Throwable throwable) {
		synchronized (cache) {
			if (cache.compatibilityFailureLogged) {
				return;
			}
			cache.compatibilityFailureLogged = true;
		}
		AdvancedCorePlugin plugin = AdvancedCorePlugin.getInstance();
		if (plugin != null) {
			plugin.getLogger().warning(message);
			if (throwable != null) {
				plugin.debug(throwable);
			}
		}
	}

	private static synchronized ReflectionCache sharedCache(ClassLoader classLoader, String apiClassName,
			String builderClassName) {
		Map<String, WeakReference<ReflectionCache>> classLoaderCaches = CACHES.computeIfAbsent(classLoader,
				ignored -> new HashMap<>());
		String key = apiClassName + '\0' + builderClassName;
		WeakReference<ReflectionCache> reference = classLoaderCaches.get(key);
		ReflectionCache existing = reference == null ? null : reference.get();
		if (existing != null) {
			return existing;
		}

		ReflectionCache created = new ReflectionCache(classLoader, apiClassName, builderClassName);
		classLoaderCaches.put(key, new WeakReference<>(created));
		return created;
	}

	private static final class ReflectionCache {
		private final ClassLoader classLoader;
		private final String apiClassName;
		private final String builderClassName;
		private volatile Method itemFromIdMethod;
		private volatile Method buildMethod;
		private boolean compatibilityFailureLogged;

		private ReflectionCache(ClassLoader classLoader, String apiClassName, String builderClassName) {
			this.classLoader = classLoader;
			this.apiClassName = apiClassName;
			this.builderClassName = builderClassName;
		}
	}
}
