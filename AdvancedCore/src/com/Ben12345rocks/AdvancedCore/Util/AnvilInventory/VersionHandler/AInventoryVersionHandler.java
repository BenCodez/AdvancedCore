package com.Ben12345rocks.AdvancedCore.Util.AnvilInventory.VersionHandler;

import java.util.HashMap;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import com.Ben12345rocks.AdvancedCore.Util.AnvilInventory.AInventory.AnvilSlot;

public interface AInventoryVersionHandler {

	/**
	 * Load classes.
	 */
	public void loadClasses();

	public void open(Player player, HashMap<AnvilSlot, ItemStack> items);
	
	public Inventory getInventory();
}
