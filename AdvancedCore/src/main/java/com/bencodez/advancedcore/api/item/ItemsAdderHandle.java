package com.bencodez.advancedcore.api.item;

import org.bukkit.inventory.ItemStack;

import dev.lone.itemsadder.api.CustomStack;

public class ItemsAdderHandle {
	public ItemsAdderHandle() {

	}

	public ItemStack getItem(String item) {
		CustomStack stack = CustomStack.getInstance(item);
		if (stack != null) {
			return stack.getItemStack();
		}
		// no custom item found with that id
		return null;
	}
}
