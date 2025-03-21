package com.bencodez.advancedcore.api.item;

import org.bukkit.inventory.ItemStack;

import com.nexomc.nexo.api.NexoItems;
import com.nexomc.nexo.items.ItemBuilder;

public class NexoItemHandle {
	public NexoItemHandle() {

	}

	public ItemStack getItem(String item) {
		ItemBuilder stack = NexoItems.itemFromId(item);
		if (stack != null) {
			return stack.build();
		}
		// no custom item found with that id
		return null;
	}
}
