package com.bencodez.advancedcore.tests.lifecycle;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.AdvancedCoreConfigOptions;
import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.item.FullInventoryHandler;
import com.bencodez.advancedcore.api.rewards.RewardHandler;
import com.bencodez.advancedcore.api.time.TimeChecker;
import com.bencodez.advancedcore.lifecycle.AdvancedCoreLifecycle;
import com.bencodez.advancedcore.lifecycle.AdvancedCoreLifecycle.RuntimeExecutors;
import com.bencodez.simpleapi.scheduler.BukkitScheduler;

public class AdvancedCoreLifecycleTest {

	@Test
	public void runtimeExecutorsKeepExistingOwnershipBoundaries() {
		BukkitScheduler bukkitScheduler = mock(BukkitScheduler.class);
		ScheduledExecutorService timer = mock(ScheduledExecutorService.class);
		ScheduledExecutorService loginTimer = mock(ScheduledExecutorService.class);
		ScheduledExecutorService inventoryTimer = mock(ScheduledExecutorService.class);
		RuntimeExecutors runtime = new RuntimeExecutors(bukkitScheduler, timer, loginTimer, inventoryTimer);

		assertNotSame(runtime.getTimer(), runtime.getLoginTimer());
		assertNotSame(runtime.getTimer(), runtime.getInventoryTimer());
		assertNotSame(runtime.getLoginTimer(), runtime.getInventoryTimer());
	}

	@Test
	public void shutdownCoordinatesExecutorsAndInventoryPersistence() throws Exception {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		AdvancedCoreConfigOptions options = mock(AdvancedCoreConfigOptions.class);
		ScheduledExecutorService timer = mock(ScheduledExecutorService.class);
		ScheduledExecutorService loginTimer = mock(ScheduledExecutorService.class);
		ScheduledExecutorService inventoryTimer = mock(ScheduledExecutorService.class);
		ScheduledExecutorService timeTimer = mock(ScheduledExecutorService.class);
		TimeChecker timeChecker = mock(TimeChecker.class);
		RewardHandler rewardHandler = mock(RewardHandler.class);
		FullInventoryHandler fullInventoryHandler = mock(FullInventoryHandler.class);

		when(plugin.getLogger()).thenReturn(Logger.getLogger("AdvancedCoreLifecycleTest"));
		when(plugin.getOptions()).thenReturn(options);
		when(plugin.getTimer()).thenReturn(timer);
		when(plugin.getLoginTimer()).thenReturn(loginTimer);
		when(plugin.getInventoryTimer()).thenReturn(inventoryTimer);
		when(plugin.getTimeChecker()).thenReturn(timeChecker);
		when(timeChecker.getTimer()).thenReturn(timeTimer);
		when(plugin.getRewardHandler()).thenReturn(rewardHandler);
		when(plugin.getFullInventoryHandler()).thenReturn(fullInventoryHandler);

		new AdvancedCoreLifecycle(plugin).shutdown();

		verify(timer).shutdown();
		verify(loginTimer).shutdown();
		verify(inventoryTimer).shutdown();
		verify(timeTimer).shutdown();
		verify(timer).awaitTermination(2, TimeUnit.SECONDS);
		verify(loginTimer).awaitTermination(2, TimeUnit.SECONDS);
		verify(inventoryTimer).awaitTermination(1, TimeUnit.SECONDS);
		verify(timeTimer).awaitTermination(2, TimeUnit.SECONDS);
		verify(rewardHandler).shutdown();
		verify(fullInventoryHandler).shutdown();
		verify(fullInventoryHandler).save();
		verify(plugin).onUnLoad();
	}
}
