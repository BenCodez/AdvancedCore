package com.bencodez.advancedcore.api.item;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class ItemModelHandler {
	public static ItemStack getItemWithModel(ItemStack item, String model) {
		if (item == null || model == null || model.isEmpty()) {
			return item;
		}
		ItemMeta meta = item.getItemMeta();
		if (meta != null) {
			meta.setItemModel(NamespacedKey.fromString(model));
			item.setItemMeta(meta);
		}
		return item;
	}

	public static String getModel(ItemStack item) {
		if (item == null) {
			return null;
		}
		ItemMeta meta = item.getItemMeta();
		if (meta != null) {
			return meta.getItemModel().toString();
		}
		return null;
	}
}
