package com.bencodez.advancedcore.api.inventory.anvilinventory.versionhandler;

import java.util.HashMap;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import com.bencodez.advancedcore.api.inventory.anvilinventory.AInventory.AnvilSlot;

/**
 * The Interface AInventoryVersionHandler.
 */
public interface AInventoryVersionHandler {

	/**
	 * Gets the inventory.
	 *
	 * @return the inventory
	 */
	public Inventory getInventory();

	/**
	 * Load classes.
	 */
	public void loadClasses();

	/**
	 * Open.
	 *
	 * @param player the player
	 * @param items  the items
	 */
	public void open(Player player, HashMap<AnvilSlot, ItemStack> items);
}
