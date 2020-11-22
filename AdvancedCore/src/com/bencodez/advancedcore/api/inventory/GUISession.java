package com.bencodez.advancedcore.api.inventory;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;

public class GUISession implements InventoryHolder {
	/**
	 * Get the GUISession for a given inventory, or null if none exists for this
	 * inventory
	 *
	 * @param inventory The inventory to get the GUISession from
	 * @return The GUISession or null if none exists
	 */
	public static GUISession extractSession(Inventory inventory) {
		if (inventory == null) {
			return null;
		}
		InventoryHolder ih = inventory.getHolder();
		if (ih != null && ih instanceof GUISession) {
			return (GUISession) ih;
		}
		return null;
	}

	/**
	 * Extract the GUISession from the inventory currently being viewed by a player,
	 * or null if none exists
	 *
	 * @param player The player who's open inventory to extract the GUISession from
	 * @return The GUISession or null if none exists
	 */
	public static GUISession extractSession(Player player) {
		if (player == null) {
			return null;
		}
		InventoryView oInv = player.getOpenInventory();
		if (oInv == null) {
			return null;
		}
		return extractSession(oInv.getTopInventory()); // Get the inventory the player is looking at (Bottom is always
														// their own inventory)
	}

	private BInventory inventoryGUI; // GUI Being viewed
	private int page = 1; // Currently displayed page number

	/**
	 * Construct a new GUISession
	 *
	 * @param inventoryGUI The inventory that this a session to view
	 * @param page         The page currently being viewed
	 */
	public GUISession(BInventory inventoryGUI, int page) {
		if (inventoryGUI == null) {
			throw new IllegalArgumentException("InventoryGUI must not be null");
		}

		this.inventoryGUI = inventoryGUI;
		this.page = page;
	}

	/**
	 * Method inherited from Bukkit's InventoryHolder. Will always return null
	 *
	 * @return Null
	 */
	@Override
	public Inventory getInventory() { // Part of InventoryHolder from bukkit
		return null; // doesn't matter at all if null returned
	}

	/**
	 * Get the InventoryGUI being viewed
	 *
	 * @return The InventoryGUI being viewed
	 */
	public BInventory getInventoryGUI() {
		return inventoryGUI;
	}

	/**
	 * Get the page currently being viewed
	 *
	 * @return The page
	 */
	public int getPage() {
		return page;
	}

	/**
	 * Set the page currently being viewed
	 *
	 * @param page The page
	 */
	public void setPage(int page) {
		if (page < 1) {
			throw new IllegalArgumentException("Page must be >= 1");
		}
		this.page = page;
	}
}
