package com.bencodez.advancedcore.api.item;

import org.bukkit.inventory.ItemStack;

import dev.lone.itemsadder.api.CustomStack;

/**
 * Handler for ItemsAdder custom items.
 */
public class ItemsAdderHandle {
	/**
	 * Creates a new ItemsAdder handler.
	 */
	public ItemsAdderHandle() {

	}

	/**
	 * Gets a custom item by its identifier.
	 * 
	 * @param item the item identifier
	 * @return the custom ItemStack, or null if not found
	 */
	public ItemStack getItem(String item) {
		CustomStack stack = CustomStack.getInstance(item);
		if (stack != null) {
			return stack.getItemStack();
		}
		// no custom item found with that id
		return null;
	}
}
