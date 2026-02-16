package com.bencodez.advancedcore.api.item;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Handler for item model customization.
 */
public class ItemModelHandler {
	/**
	 * Sets a custom model on an item.
	 * 
	 * @param item the item to modify
	 * @param model the model identifier
	 * @return the modified item
	 */
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

	/**
	 * Gets the model identifier from an item.
	 * 
	 * @param item the item to check
	 * @return the model identifier, or null if none
	 */
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
