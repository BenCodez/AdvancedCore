package com.bencodez.advancedcore.tests.inventory;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.inventory.BInventory;
import com.bencodez.advancedcore.api.inventory.UpdatingBInventoryButton;
import com.bencodez.advancedcore.api.item.ItemBuilder;

public class UpdatingBInventoryButtonTest {

	@Test
	public void loadRegistersPeriodicUpdateForSpecificViewer() {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		BInventory inventory = mock(BInventory.class);
		Player player = mock(Player.class);
		ItemBuilder item = mock(ItemBuilder.class);
		UpdatingBInventoryButton button = new UpdatingBInventoryButton(plugin, item, 100L, 250L) {
			@Override
			public ItemBuilder onUpdate(Player player) {
				return item;
			}

			@Override
			public void onClick(BInventory.ClickEvent clickEvent) {
			}
		};
		button.setInv(inventory);

		button.load(player);

		verify(inventory).addUpdatingButton(eq(player), eq(plugin), eq(100L), eq(250L), any(Runnable.class));
	}
}
