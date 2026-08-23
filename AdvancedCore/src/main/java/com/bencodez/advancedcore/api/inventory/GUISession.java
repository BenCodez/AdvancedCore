package com.bencodez.advancedcore.api.inventory;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;

import com.bencodez.simpleapi.player.PlayerUtils;

/**
 * Session holder for managing GUI state across inventory interactions.
 */
public class GUISession implements InventoryHolder {

	/**
	 * Get the GUISession for a given inventory, or null if none exists for this
	 * inventory.
	 *
	 * @param inventory the inventory to inspect
	 * @return the GUISession or null if none exists
	 */
	public static GUISession extractSession(Inventory inventory) {
		if (inventory == null) {
			return null;
		}
		InventoryHolder holder = inventory.getHolder();
		return holder instanceof GUISession ? (GUISession) holder : null;
	}

	/**
	 * Extract the GUISession from the inventory currently being viewed by a player.
	 *
	 * @param player the player whose open inventory should be inspected
	 * @return the GUISession or null if none exists
	 */
	public static GUISession extractSession(Player player) {
		if (player == null) {
			return null;
		}

		InventoryView openInventory = player.getOpenInventory();
		if (openInventory == null) {
			return null;
		}

		Inventory topInventory = PlayerUtils.getTopInventory(player);
		if (topInventory == null) {
			topInventory = openInventory.getTopInventory();
		}
		return extractSession(topInventory);
	}

	private final BInventory inventoryGUI;
	private int page = 1;

	/**
	 * Construct a new GUISession.
	 *
	 * @param inventoryGUI the GUI represented by this session
	 * @param page the page currently being viewed
	 */
	public GUISession(BInventory inventoryGUI, int page) {
		if (inventoryGUI == null) {
			throw new IllegalArgumentException("InventoryGUI must not be null");
		}
		this.inventoryGUI = inventoryGUI;
		setPage(page);
	}

	/**
	 * InventoryHolder implementation. The concrete inventory owns this holder, so
	 * no secondary inventory needs to be returned here.
	 *
	 * @return null
	 */
	@Override
	public Inventory getInventory() {
		return null;
	}

	public BInventory getInventoryGUI() {
		return inventoryGUI;
	}

	public int getPage() {
		return page;
	}

	public void setPage(int page) {
		if (page < 1) {
			throw new IllegalArgumentException("Page must be >= 1");
		}
		this.page = page;
	}
}
