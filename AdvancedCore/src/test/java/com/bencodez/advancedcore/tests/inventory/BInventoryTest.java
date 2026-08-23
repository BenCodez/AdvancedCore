package com.bencodez.advancedcore.tests.inventory;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

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
}
