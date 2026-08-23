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
import org.mockito.ArgumentCaptor;
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
	public void guiButtonClickDefaultsToAsyncCallback() {
		TestContext context = createContext(true);
		ArgumentCaptor<Runnable> asyncTask = ArgumentCaptor.forClass(Runnable.class);

		runListener(context);

		verify(context.scheduler).runTask(eq(context.plugin), any(Runnable.class), eq(context.player));
		verify(context.scheduler).runTaskAsynchronously(eq(context.plugin), asyncTask.capture());
		verify(context.gui, never()).onClick(context.event, context.button);

		asyncTask.getValue().run();
		verify(context.gui).onClick(context.event, context.button);
	}

	@Test
	public void guiButtonClickCanOptIntoSyncCallback() {
		TestContext context = createContext(false);

		runListener(context);

		verify(context.scheduler).runTask(eq(context.plugin), any(Runnable.class), eq(context.player));
		verify(context.scheduler, never()).runTaskAsynchronously(eq(context.plugin), any(Runnable.class));
		verify(context.gui).onClick(context.event, context.button);
	}

	private TestContext createContext(boolean clickAsync) {
		TestContext context = new TestContext();
		context.plugin = mock(AdvancedCorePlugin.class);
		context.options = mock(AdvancedCoreConfigOptions.class);
		context.scheduler = mock(BukkitScheduler.class);
		context.event = mock(InventoryClickEvent.class);
		context.player = mock(Player.class);
		context.view = mock(InventoryView.class);
		context.topInventory = mock(Inventory.class);
		context.gui = mock(BInventory.class);
		context.button = mock(BInventoryButton.class);
		context.session = new GUISession(context.gui, 1);

		when(context.plugin.getOptions()).thenReturn(context.options);
		when(context.plugin.getBukkitScheduler()).thenReturn(context.scheduler);
		when(context.options.getSpamClickTimeMs()).thenReturn(0L);
		when(context.event.getWhoClicked()).thenReturn(context.player);
		when(context.player.getOpenInventory()).thenReturn(context.view);
		when(context.topInventory.getHolder()).thenReturn(context.session);
		when(context.event.getClickedInventory()).thenReturn(context.topInventory);
		when(context.event.getInventory()).thenReturn(context.topInventory);
		when(context.event.getSlot()).thenReturn(3);
		when(context.gui.getButtons()).thenReturn(Map.of(3, context.button));
		when(context.gui.isPages()).thenReturn(false);
		when(context.gui.isCloseInv()).thenReturn(false);
		when(context.gui.isClickAsync()).thenReturn(clickAsync);
		return context;
	}

	private void runListener(TestContext context) {
		try (MockedStatic<PlayerUtils> playerUtils = mockStatic(PlayerUtils.class)) {
			playerUtils.when(() -> PlayerUtils.getTopInventory(context.player)).thenReturn(context.topInventory);
			new BInventoryListener(context.plugin).onInventoryClick(context.event);
		}
	}

	private static class TestContext {
		private AdvancedCorePlugin plugin;
		private AdvancedCoreConfigOptions options;
		private BukkitScheduler scheduler;
		private InventoryClickEvent event;
		private Player player;
		private InventoryView view;
		private Inventory topInventory;
		private BInventory gui;
		private BInventoryButton button;
		private GUISession session;
	}
}
