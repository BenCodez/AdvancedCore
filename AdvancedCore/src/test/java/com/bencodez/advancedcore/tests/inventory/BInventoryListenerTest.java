package com.bencodez.advancedcore.tests.inventory;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.bencodez.advancedcore.AdvancedCoreConfigOptions;
import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.inventory.BInventory;
import com.bencodez.advancedcore.api.inventory.BInventoryButton;
import com.bencodez.advancedcore.api.inventory.BInventoryListener;
import com.bencodez.advancedcore.api.inventory.GUISession;
import com.bencodez.simpleapi.player.PlayerUtils;
import com.bencodez.simpleapi.scheduler.BukkitScheduler;

public class BInventoryListenerTest {

	@Test
	public void guiButtonClickStaysOnEventThread() {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		AdvancedCoreConfigOptions options = mock(AdvancedCoreConfigOptions.class);
		BukkitScheduler scheduler = mock(BukkitScheduler.class);
		InventoryClickEvent event = mock(InventoryClickEvent.class);
		Player player = mock(Player.class);
		InventoryView view = mock(InventoryView.class);
		Inventory topInventory = mock(Inventory.class);
		BInventory gui = mock(BInventory.class);
		BInventoryButton button = mock(BInventoryButton.class);
		GUISession session = new GUISession(gui, 1);

		when(plugin.getOptions()).thenReturn(options);
		when(plugin.getBukkitScheduler()).thenReturn(scheduler);
		when(options.getSpamClickTimeMs()).thenReturn(0L);
		when(event.getWhoClicked()).thenReturn(player);
		when(player.getOpenInventory()).thenReturn(view);
		when(topInventory.getHolder()).thenReturn(session);
		when(event.getClickedInventory()).thenReturn(topInventory);
		when(event.getInventory()).thenReturn(topInventory);
		when(event.getSlot()).thenReturn(3);
		when(gui.getButtons()).thenReturn(Map.of(3, button));
		when(gui.isPages()).thenReturn(false);
		when(gui.isCloseInv()).thenReturn(false);

		try (MockedStatic<PlayerUtils> playerUtils = mockStatic(PlayerUtils.class)) {
			playerUtils.when(() -> PlayerUtils.getTopInventory(player)).thenReturn(topInventory);

			new BInventoryListener(plugin).onInventoryClick(event);
		}

		verify(scheduler).runTask(eq(plugin), any(Runnable.class), eq(player));
		verify(scheduler, never()).runTaskAsynchronously(eq(plugin), any(Runnable.class));
		verify(gui).onClick(event, button);
	}
}
