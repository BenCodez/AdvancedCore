
package com.bencodez.advancedcore.api.item;

import java.lang.reflect.Method;

import org.bukkit.inventory.ItemStack;

public class NexoItemHandle {
	public NexoItemHandle() {
	}

	public ItemStack getItem(String item) {
		try {
			Class<?> nexoItemsClass = Class.forName("com.nexomc.nexo.api.NexoItems");
			Method itemFromIdMethod = nexoItemsClass.getMethod("itemFromId", String.class);
			Object itemBuilder = itemFromIdMethod.invoke(null, item);
			if (itemBuilder != null) {
				Class<?> itemBuilderClass = Class.forName("com.nexomc.nexo.items.ItemBuilder");
				Method buildMethod = itemBuilderClass.getMethod("build");
				Object builtItem = buildMethod.invoke(itemBuilder);
				return (ItemStack) builtItem;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

}
