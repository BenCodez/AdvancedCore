package com.bencodez.advancedcore.tests.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.api.inventory.InventoryPagination;

public class InventoryPaginationTest {

	@Test
	public void pageCountHandlesZeroBasedSlotBoundary() {
		assertEquals(45, InventoryPagination.getContentSize(54));
		assertEquals(1, InventoryPagination.getPageCount(44, 54));
		assertEquals(2, InventoryPagination.getPageCount(45, 54));
		assertEquals(2, InventoryPagination.getPageCount(89, 54));
		assertEquals(3, InventoryPagination.getPageCount(90, 54));
	}

	@Test
	public void displayedSlotsMapBackToSourceSlots() {
		assertEquals(0, InventoryPagination.getButtonSlot(1, 0, 54));
		assertEquals(44, InventoryPagination.getButtonSlot(1, 44, 54));
		assertEquals(45, InventoryPagination.getButtonSlot(2, 0, 54));
		assertEquals(89, InventoryPagination.getButtonSlot(2, 44, 54));
		assertThrows(IllegalArgumentException.class, () -> InventoryPagination.getButtonSlot(0, 0, 54));
	}

	@Test
	public void contentSlotCheckExcludesNavigationRow() {
		assertTrue(InventoryPagination.isContentSlot(44, 54));
		assertFalse(InventoryPagination.isContentSlot(45, 54));
		assertFalse(InventoryPagination.isContentSlot(53, 54));
	}
}
