package com.bencodez.advancedcore.tests.inventory;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.inventory.BInventory;

public class BInventoryTest {

	@Test
	@SuppressWarnings("rawtypes")
	public void updatingButtonUsesConfiguredIntervalAndCancelsFuture() {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		ScheduledExecutorService timer = mock(ScheduledExecutorService.class);
		ScheduledFuture future = mock(ScheduledFuture.class);
		Runnable runnable = mock(Runnable.class);
		when(plugin.getInventoryTimer()).thenReturn(timer);
		doReturn(future).when(timer).scheduleWithFixedDelay(eq(runnable), eq(100L), eq(250L),
				eq(TimeUnit.MILLISECONDS));

		BInventory inventory = new BInventory("Test");
		inventory.addUpdatingButton(plugin, 100L, 250L, runnable);
		inventory.cancelTimer();

		verify(timer).scheduleWithFixedDelay(runnable, 100L, 250L, TimeUnit.MILLISECONDS);
		verify(future).cancel(true);
	}

	@Test
	@SuppressWarnings("rawtypes")
	public void legacyUpdatingButtonIsCancelledWhenGuiIsForceClosed() {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		ScheduledExecutorService timer = mock(ScheduledExecutorService.class);
		ScheduledFuture future = mock(ScheduledFuture.class);
		Runnable runnable = mock(Runnable.class);
		when(plugin.getInventoryTimer()).thenReturn(timer);
		doReturn(future).when(timer).scheduleWithFixedDelay(eq(runnable), eq(100L), eq(250L),
				eq(TimeUnit.MILLISECONDS));

		BInventory inventory = new BInventory("Test");
		inventory.addUpdatingButton(plugin, 100L, 250L, runnable);
		inventory.forceClose(null);

		verify(future).cancel(true);
	}

	@Test
	@SuppressWarnings("rawtypes")
	public void viewerCancellationDoesNotCancelOtherViewerTasks() {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		ScheduledExecutorService timer = mock(ScheduledExecutorService.class);
		ScheduledFuture firstFuture = mock(ScheduledFuture.class);
		ScheduledFuture secondFuture = mock(ScheduledFuture.class);
		Player firstPlayer = mock(Player.class);
		Player secondPlayer = mock(Player.class);
		Runnable firstRunnable = mock(Runnable.class);
		Runnable secondRunnable = mock(Runnable.class);
		when(plugin.getInventoryTimer()).thenReturn(timer);
		when(firstPlayer.getUniqueId()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000001"));
		when(secondPlayer.getUniqueId()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000002"));
		doReturn(firstFuture).when(timer).scheduleWithFixedDelay(eq(firstRunnable), eq(10L), eq(20L),
				eq(TimeUnit.MILLISECONDS));
		doReturn(secondFuture).when(timer).scheduleWithFixedDelay(eq(secondRunnable), eq(30L), eq(40L),
				eq(TimeUnit.MILLISECONDS));

		BInventory inventory = new BInventory("Test");
		inventory.addUpdatingButton(firstPlayer, plugin, 10L, 20L, firstRunnable);
		inventory.addUpdatingButton(secondPlayer, plugin, 30L, 40L, secondRunnable);

		inventory.cancelTimer(firstPlayer);

		verify(firstFuture).cancel(true);
		verify(secondFuture, never()).cancel(true);

		inventory.cancelTimer();
		verify(secondFuture).cancel(true);
	}
}
