package com.bencodez.advancedcore.tests.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.advancedcore.api.user.UserData;
import com.bencodez.advancedcore.api.user.UserManager;
import com.bencodez.advancedcore.api.user.usercache.UserDataCache;
import com.bencodez.advancedcore.api.user.usercache.UserDataManager;
import com.bencodez.advancedcore.api.user.usercache.change.UserDataChange;
import com.bencodez.advancedcore.api.user.usercache.change.UserDataChangeString;
import com.bencodez.simpleapi.sql.data.DataValue;
import com.bencodez.simpleapi.sql.data.DataValueString;

public class UserDataCacheSchedulingTest {

	@Test
	@SuppressWarnings("rawtypes")
	public void changesQueuedDuringScheduledFlushAreRescheduled() {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		UserDataManager manager = mock(UserDataManager.class);
		ScheduledExecutorService timer = mock(ScheduledExecutorService.class);
		ScheduledFuture future = mock(ScheduledFuture.class);
		List<Runnable> scheduledTasks = new ArrayList<>();

		when(manager.getPlugin()).thenReturn(plugin);
		when(manager.getTimer()).thenReturn(timer);
		doAnswer(invocation -> {
			scheduledTasks.add(invocation.getArgument(0));
			return future;
		}).when(timer).schedule(any(Runnable.class), eq(3L), eq(TimeUnit.SECONDS));

		UserDataCache cache = spy(new UserDataCache(manager, UUID.randomUUID()));
		doAnswer(invocation -> {
			cache.addChange(new UserDataChangeString("second", "two"), true);
			return null;
		}).when(cache).processChanges();

		cache.addChange(new UserDataChangeString("first", "one"), true);
		assertEquals(1, scheduledTasks.size());

		scheduledTasks.get(0).run();

		assertEquals(2, scheduledTasks.size(), "a change queued during the flush must schedule another flush");
	}

	@Test
	@SuppressWarnings("rawtypes")
	public void failedFlushPreparationRestoresDrainedChanges() {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		UserManager userManager = mock(UserManager.class);
		AdvancedCoreUser user = mock(AdvancedCoreUser.class);
		UserDataManager manager = mock(UserDataManager.class);
		ScheduledExecutorService timer = mock(ScheduledExecutorService.class);
		ScheduledFuture future = mock(ScheduledFuture.class);
		UUID uuid = UUID.randomUUID();

		when(manager.getPlugin()).thenReturn(plugin);
		when(manager.getTimer()).thenReturn(timer);
		when(plugin.getUserManager()).thenReturn(userManager);
		when(userManager.getUser(uuid, false)).thenReturn(user);
		doReturn(future).when(timer).schedule(any(Runnable.class), eq(3L), eq(TimeUnit.SECONDS));

		AtomicInteger conversions = new AtomicInteger();
		UserDataChange failingChange = new UserDataChange("Custom") {
			@Override
			public void dump() {
			}

			@Override
			public DataValue toUserDataValue() {
				if (conversions.getAndIncrement() == 0) {
					return new DataValueString("value");
				}
				throw new IllegalStateException("conversion failed");
			}
		};

		UserDataCache cache = new UserDataCache(manager, uuid);
		cache.addChange(failingChange, true);

		assertThrows(IllegalStateException.class, cache::processChanges);
		assertTrue(cache.hasChangesToProcess(), "failed preparation must leave the drained change queued for retry");
	}

	@Test
	@SuppressWarnings("rawtypes")
	public void dumpWaitsForFailedInFlightBatchToRequeueAndRetry() throws Exception {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		UserManager userManager = mock(UserManager.class);
		AdvancedCoreUser user = mock(AdvancedCoreUser.class);
		UserData userData = mock(UserData.class);
		UserDataManager manager = mock(UserDataManager.class);
		ScheduledExecutorService timer = mock(ScheduledExecutorService.class);
		ScheduledFuture future = mock(ScheduledFuture.class);
		UUID uuid = UUID.randomUUID();

		when(manager.getPlugin()).thenReturn(plugin);
		when(manager.getTimer()).thenReturn(timer);
		when(plugin.getUserManager()).thenReturn(userManager);
		when(userManager.getUser(uuid, false)).thenReturn(user);
		when(user.getUserData()).thenReturn(userData);
		doReturn(future).when(timer).schedule(any(Runnable.class), eq(3L), eq(TimeUnit.SECONDS));

		CountDownLatch inFlight = new CountDownLatch(1);
		CountDownLatch releaseFailure = new CountDownLatch(1);
		CountDownLatch dumpFinished = new CountDownLatch(1);
		AtomicInteger conversions = new AtomicInteger();
		AtomicInteger dumps = new AtomicInteger();
		UserDataChange change = new UserDataChange("Custom") {
			@Override
			public void dump() {
				dumps.incrementAndGet();
			}

			@Override
			public DataValue toUserDataValue() {
				int call = conversions.incrementAndGet();
				if (call == 2) {
					inFlight.countDown();
					try {
						releaseFailure.await();
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						throw new IllegalStateException(e);
					}
					throw new IllegalStateException("conversion failed while batch is in flight");
				}
				return new DataValueString("value");
			}
		};

		UserDataCache cache = new UserDataCache(manager, uuid);
		cache.addChange(change, true);

		Thread flushThread = new Thread(() -> {
			try {
				cache.processChanges();
			} catch (IllegalStateException expected) {
				// The failed batch must be restored for dump() to retry.
			}
		});
		flushThread.start();
		assertTrue(inFlight.await(1, TimeUnit.SECONDS));

		Thread dumpThread = new Thread(() -> {
			cache.dump();
			dumpFinished.countDown();
		});
		dumpThread.start();
		assertFalse(dumpFinished.await(100, TimeUnit.MILLISECONDS),
				"dump must wait while a drained batch is still in flight");

		releaseFailure.countDown();
		flushThread.join(1000);
		dumpThread.join(1000);

		assertEquals(0L, dumpFinished.getCount(), "dump should finish after the failed batch is restored and retried");
		assertEquals(1, dumps.get(), "the restored change must be persisted and dumped exactly once before disposal");
		assertFalse(cache.hasChangesToProcess());
	}

	@Test
	public void updateCacheDefensivelyCopiesInput() {
		UserDataManager manager = mock(UserDataManager.class);
		UserDataCache cache = new UserDataCache(manager, UUID.randomUUID());
		HashMap<String, DataValue> values = new HashMap<>();
		values.put("PlayerName", new DataValueString("Ben"));

		cache.updateCache(values);
		values.clear();

		assertTrue(cache.isCached("PlayerName"));
	}
}
