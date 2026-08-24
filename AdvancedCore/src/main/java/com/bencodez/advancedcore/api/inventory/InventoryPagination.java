package com.bencodez.advancedcore.api.inventory;

/**
 * Shared pagination calculations for {@link BInventory} and its listener.
 */
public final class InventoryPagination {
	private static final int NAVIGATION_ROW_SIZE = 9;

	private InventoryPagination() {
	}

	public static int getContentSize(int inventorySize) {
		return Math.max(1, inventorySize - NAVIGATION_ROW_SIZE);
	}

	public static int getPageCount(int highestSlot, int inventorySize) {
		int contentSize = getContentSize(inventorySize);
		int requiredSlots = Math.max(1, highestSlot + 1);
		return Math.max(1, (requiredSlots + contentSize - 1) / contentSize);
	}

	public static int getButtonSlot(int page, int displayedSlot, int inventorySize) {
		if (page < 1) {
			throw new IllegalArgumentException("Page must be >= 1");
		}
		return (page - 1) * getContentSize(inventorySize) + displayedSlot;
	}

	public static boolean isContentSlot(int slot, int inventorySize) {
		return slot >= 0 && slot < getContentSize(inventorySize);
	}
}
