package com.bencodez.advancedcore.tests.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.api.inventory.BInventory;
import com.bencodez.advancedcore.api.inventory.GUISession;

public class GUISessionTest {

	@Test
	public void constructorValidatesPageAndKeepsGuiReference() {
		BInventory gui = mock(BInventory.class);
		GUISession session = new GUISession(gui, 2);

		assertSame(gui, session.getInventoryGUI());
		assertEquals(2, session.getPage());
		assertThrows(IllegalArgumentException.class, () -> new GUISession(gui, 0));
		assertThrows(IllegalArgumentException.class, () -> new GUISession(null, 1));
	}

	@Test
	public void inventoryExtractionOnlyReturnsGuiSessionHolders() {
		BInventory gui = mock(BInventory.class);
		GUISession session = new GUISession(gui, 1);
		Inventory inventory = mock(Inventory.class);
		when(inventory.getHolder()).thenReturn(session);

		assertSame(session, GUISession.extractSession(inventory));

		Inventory unrelated = mock(Inventory.class);
		when(unrelated.getHolder()).thenReturn(mock(InventoryHolder.class));
		assertNull(GUISession.extractSession(unrelated));
		assertNull(GUISession.extractSession((Inventory) null));
	}
}
