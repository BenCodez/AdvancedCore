package com.bencodez.advancedcore.tests.time;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.IsoFields;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import org.bukkit.Server;
import org.bukkit.event.Event;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.time.TimeChecker;
import com.bencodez.advancedcore.api.time.TimeChangeTransition;
import com.bencodez.advancedcore.api.time.TimeType;
import com.bencodez.advancedcore.api.time.events.DayChangeEvent;
import com.bencodez.advancedcore.api.time.events.DateChangedEvent;
import com.bencodez.advancedcore.data.ServerData;
import com.bencodez.advancedcore.data.ServerData.TimeChangeTransitionState;
import com.bencodez.advancedcore.tests.BaseTest;
import com.bencodez.advancedcore.AdvancedCoreConfigOptions;

public class TimeCheckerTest {

	private AdvancedCorePlugin plugin;
	private AdvancedCoreConfigOptions options;
	private ServerData serverDataFile;
	private MockedStatic<AdvancedCorePlugin> pluginStatic;
	private BaseTest baseTest;

	@BeforeEach
	public void setUp() {
		// 1) create mocks
		plugin = mock(AdvancedCorePlugin.class);
		options = mock(AdvancedCoreConfigOptions.class);
		serverDataFile = mock(ServerData.class);

		// 3) stub plugin getters
		when(plugin.getOptions()).thenReturn(options);
		when(plugin.getServerDataFile()).thenReturn(serverDataFile);

		// 4) default option values used in tests
		when(options.getTimeZone()).thenReturn("UTC");
		when(options.getTimeHourOffSet()).thenReturn(2);
		when(options.getTimeWeekOffSet()).thenReturn(0);

		baseTest = BaseTest.getInstance();
	}

	@AfterEach
	public void tearDown() {

	}

	@Test
	public void testDayWeekMonthChanges() {
		// initial previous values
		when(serverDataFile.getPrevDay()).thenReturn(31);
		when(serverDataFile.getPrevWeekDay()).thenReturn(52);
		when(serverDataFile.getPrevMonth()).thenReturn("DECEMBER");

		// stub the setters as no-ops
		Mockito.doNothing().when(serverDataFile).setPrevDay(anyInt());
		Mockito.doNothing().when(serverDataFile).setPrevWeekDay(anyInt());
		Mockito.doNothing().when(serverDataFile).setPrevMonth(anyString());

		// spy a fresh TimeChecker
		TimeChecker timeChecker = Mockito.spy(new TimeChecker(plugin));

		for (int month = 1; month <= 12; month++) {
			int daysInMonth = LocalDateTime.of(2023, month, 1, 0, 0).getMonth().length(false);

			for (int day = 1; day <= daysInMonth; day++) {
				LocalDateTime mockedTime = LocalDateTime.of(2023, month, day, 12, 0);
				Mockito.doReturn(mockedTime).when(timeChecker).getTime();

				boolean dayChanged = timeChecker.hasDayChanged(true);
				boolean weekChanged = timeChecker.hasWeekChanged(true);
				boolean monthChanged = timeChecker.hasMonthChanged(true);

				// day should always change
				assertTrue(dayChanged);

				// month only changes on the 1st
				if (day == 1) {
					assertTrue(monthChanged);
				} else {
					assertFalse(monthChanged);
				}

				// verify we recorded the new day
				verify(serverDataFile).setPrevDay(day);

				// compute this day's week and check if it rolled over
				int weekOfYear = mockedTime.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
				int prevWeekOfYear = mockedTime.minusDays(1).get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);

				if (weekOfYear != prevWeekOfYear) {
					assertTrue(weekChanged);
					verify(serverDataFile).setPrevWeekDay(weekOfYear);
				}

				// verify month setter on the 1st
				if (day == 1) {
					verify(serverDataFile).setPrevMonth(mockedTime.getMonth().toString());
				}

				// prepare for next iteration: update previous getters
				when(serverDataFile.getPrevDay()).thenReturn(day);
				when(serverDataFile.getPrevWeekDay()).thenReturn(weekOfYear);
				when(serverDataFile.getPrevMonth()).thenReturn(mockedTime.getMonth().toString());

				// reset invocation counts so verifies start fresh each loop
				reset(serverDataFile);
				when(serverDataFile.getPrevDay()).thenReturn(day);
				when(serverDataFile.getPrevWeekDay()).thenReturn(weekOfYear);
				when(serverDataFile.getPrevMonth()).thenReturn(mockedTime.getMonth().toString());
			}
		}
	}

	@Test
	public void detectedTransitionAdvancesMarkerOnlyAfterSuccessfulDispatch() {
		TimeChangeTransitionState transition = transitionState();
		PluginManager pluginManager = configureDetectedDay(transition);
		AtomicReference<TimeChangeTransition> observed = new AtomicReference<>();
		Mockito.doAnswer(call -> {
			Event event = call.getArgument(0);
			if (event instanceof DayChangeEvent day) observed.set(day.getTransition());
			return null;
		}).when(pluginManager).callEvent(any(Event.class));

		new TimeChecker(plugin, Clock.fixed(Instant.parse("2025-01-02T12:00:00Z"), ZoneOffset.UTC)).update();

		assertNotNull(observed.get());
		assertEquals("DAY:2025-01-02", observed.get().getId());
		verify(serverDataFile).completeTimeChangeTransition(transition);
		verify(serverDataFile, Mockito.never()).failTimeChangeTransition(transition);
	}

	@Test
	public void failedDispatchLeavesDurableTransitionPending() {
		TimeChangeTransitionState transition = transitionState();
		PluginManager pluginManager = configureDetectedDay(transition);
		Mockito.doAnswer(call -> {
			if (call.getArgument(0) instanceof DayChangeEvent) throw new IllegalStateException("listener failure");
			return null;
		}).when(pluginManager).callEvent(any(Event.class));

		new TimeChecker(plugin, Clock.fixed(Instant.parse("2025-01-02T12:00:00Z"), ZoneOffset.UTC)).update();

		verify(serverDataFile, Mockito.never()).completeTimeChangeTransition(any());
		verify(serverDataFile).failTimeChangeTransition(transition);
	}

	@Test
	public void shutdownWaitsForRetainedWorkAndWatchdogAbortCannotCompleteMarker() throws Exception {
		TimeChangeTransitionState transition = transitionState();
		PluginManager pluginManager = configureDetectedDay(transition);
		AtomicReference<TimeChangeTransition.Lease> lease = new AtomicReference<>();
		AtomicReference<TimeChangeTransition> observed = new AtomicReference<>();
		Mockito.doAnswer(call -> {
			if (call.getArgument(0) instanceof DayChangeEvent day) {
				observed.set(day.getTransition());
				lease.set(day.getTransition().retain());
			}
			return null;
		}).when(pluginManager).callEvent(any(Event.class));
		TimeChecker checker = new TimeChecker(plugin, Clock.fixed(Instant.parse("2025-01-02T12:00:00Z"), ZoneOffset.UTC));

		checker.update();
		CompletionStage<Void> drain = checker.beginShutdown();
		assertFalse(drain.toCompletableFuture().isDone());
		assertNotNull(lease.get());
		assertFalse(observed.get().isCancellationRequested(),
				"normal shutdown must give admitted work its bounded completion grace");
		checker.abortActiveTransitions();
		assertTrue(observed.get().isCancellationRequested());
		drain.toCompletableFuture().get(2, TimeUnit.SECONDS);
		lease.get().complete();
		verify(serverDataFile, Mockito.never()).completeTimeChangeTransition(any());
		verify(serverDataFile).failTimeChangeTransition(transition);
	}

	@Test
	public void cooperativeCancellationKeepsDrainOpenUntilRetainedWorkStops() throws Exception {
		TimeChangeTransitionState transition = transitionState();
		PluginManager pluginManager = configureDetectedDay(transition);
		AtomicReference<TimeChangeTransition.Lease> lease = new AtomicReference<>();
		AtomicReference<TimeChangeTransition> observed = new AtomicReference<>();
		Mockito.doAnswer(call -> {
			if (call.getArgument(0) instanceof DayChangeEvent day) {
				observed.set(day.getTransition());
				lease.set(day.getTransition().retain());
			}
			return null;
		}).when(pluginManager).callEvent(any(Event.class));
		TimeChecker checker = new TimeChecker(plugin,
				Clock.fixed(Instant.parse("2025-01-02T12:00:00Z"), ZoneOffset.UTC));

		checker.update();
		CompletionStage<Void> drain = checker.beginShutdown();
		checker.cancelActiveTransitions();

		assertTrue(observed.get().isCancellationRequested());
		assertFalse(drain.toCompletableFuture().isDone());
		lease.get().complete();
		drain.toCompletableFuture().get(2, TimeUnit.SECONDS);
		verify(serverDataFile, Mockito.never()).completeTimeChangeTransition(any());
		verify(serverDataFile).failTimeChangeTransition(transition);
	}

	@Test
	public void watchdogCannotReverseSuccessAfterFinalPersistenceStarts() throws Exception {
		TimeChangeTransitionState transition = transitionState();
		PluginManager pluginManager = configureDetectedDay(transition);
		AtomicReference<TimeChangeTransition.Lease> lease = new AtomicReference<>();
		CountDownLatch persistenceStarted = new CountDownLatch(1);
		CountDownLatch releasePersistence = new CountDownLatch(1);
		Mockito.doAnswer(call -> {
			if (call.getArgument(0) instanceof DayChangeEvent day) lease.set(day.getTransition().retain());
			return null;
		}).when(pluginManager).callEvent(any(Event.class));
		Mockito.doAnswer(call -> {
			persistenceStarted.countDown();
			assertTrue(releasePersistence.await(2, TimeUnit.SECONDS));
			return null;
		}).when(serverDataFile).completeTimeChangeTransition(transition);
		ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
		try {
			TimeChecker checker = new TimeChecker(plugin,
					Clock.fixed(Instant.parse("2025-01-02T12:00:00Z"), ZoneOffset.UTC));
			checker.setTimer(timer);
			checker.update();
			CompletionStage<Void> drain = checker.beginShutdown();

			lease.get().complete();
			assertTrue(persistenceStarted.await(2, TimeUnit.SECONDS));
			checker.abortActiveTransitions();
			releasePersistence.countDown();

			drain.toCompletableFuture().get(2, TimeUnit.SECONDS);
			verify(serverDataFile).completeTimeChangeTransition(transition);
			verify(serverDataFile, Mockito.never()).failTimeChangeTransition(any());
		} finally {
			releasePersistence.countDown();
			timer.shutdownNow();
			assertTrue(timer.awaitTermination(2, TimeUnit.SECONDS));
		}
	}

	@Test
	public void watchdogRetiresTransitionWhileFinalizationIsQueued() throws Exception {
		TimeChangeTransitionState transition = transitionState();
		PluginManager pluginManager = configureDetectedDay(transition);
		AtomicReference<TimeChangeTransition.Lease> lease = new AtomicReference<>();
		CountDownLatch timerBlocked = new CountDownLatch(1);
		CountDownLatch releaseTimer = new CountDownLatch(1);
		Mockito.doAnswer(call -> {
			if (call.getArgument(0) instanceof DayChangeEvent day) lease.set(day.getTransition().retain());
			return null;
		}).when(pluginManager).callEvent(any(Event.class));
		ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
		try {
			timer.execute(() -> {
				timerBlocked.countDown();
				try {
					releaseTimer.await();
				} catch (InterruptedException interruption) {
					Thread.currentThread().interrupt();
				}
			});
			assertTrue(timerBlocked.await(2, TimeUnit.SECONDS));
			TimeChecker checker = new TimeChecker(plugin,
					Clock.fixed(Instant.parse("2025-01-02T12:00:00Z"), ZoneOffset.UTC));
			checker.setTimer(timer);
			checker.update();
			CompletionStage<Void> drain = checker.beginShutdown();

			lease.get().complete();
			checker.abortActiveTransitions();

			drain.toCompletableFuture().get(2, TimeUnit.SECONDS);
			releaseTimer.countDown();
			timer.shutdown();
			assertTrue(timer.awaitTermination(2, TimeUnit.SECONDS));
			verify(serverDataFile, Mockito.never()).completeTimeChangeTransition(any());
			verify(serverDataFile).failTimeChangeTransition(transition);
		} finally {
			releaseTimer.countDown();
			timer.shutdownNow();
		}
	}

	@Test
	public void shutdownAdmissionStopDoesNotReplaySuccessfullyFinishingWork() throws Exception {
		TimeChangeTransitionState transition = transitionState();
		PluginManager pluginManager = configureDetectedDay(transition);
		AtomicReference<TimeChangeTransition.Lease> lease = new AtomicReference<>();
		Mockito.doAnswer(call -> {
			if (call.getArgument(0) instanceof DayChangeEvent day) lease.set(day.getTransition().retain());
			return null;
		}).when(pluginManager).callEvent(any(Event.class));
		TimeChecker checker = new TimeChecker(plugin, Clock.fixed(Instant.parse("2025-01-02T12:00:00Z"), ZoneOffset.UTC));

		checker.update();
		CompletionStage<Void> drain = checker.beginShutdown();
		lease.get().complete();

		drain.toCompletableFuture().get(2, TimeUnit.SECONDS);
		verify(serverDataFile).completeTimeChangeTransition(transition);
		verify(serverDataFile, Mockito.never()).failTimeChangeTransition(any());
	}

	@Test
	public void retainedLeasePersistsCompletionOnTimeCheckerWorker() throws Exception {
		TimeChangeTransitionState transition = transitionState();
		PluginManager pluginManager = configureDetectedDay(transition);
		AtomicReference<TimeChangeTransition.Lease> lease = new AtomicReference<>();
		AtomicReference<String> persistenceThread = new AtomicReference<>();
		CountDownLatch persisted = new CountDownLatch(1);
		Mockito.doAnswer(call -> {
			if (call.getArgument(0) instanceof DayChangeEvent day) lease.set(day.getTransition().retain());
			return null;
		}).when(pluginManager).callEvent(any(Event.class));
		Mockito.doAnswer(call -> {
			persistenceThread.set(Thread.currentThread().getName());
			persisted.countDown();
			return null;
		}).when(serverDataFile).completeTimeChangeTransition(transition);
		ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(task ->
				new Thread(task, "time-checker-test-worker"));
		try {
			TimeChecker checker = new TimeChecker(plugin,
					Clock.fixed(Instant.parse("2025-01-02T12:00:00Z"), ZoneOffset.UTC));
			checker.setTimer(timer);
			checker.update();

			lease.get().complete();

			assertTrue(persisted.await(2, TimeUnit.SECONDS));
			assertEquals("time-checker-test-worker", persistenceThread.get());
		} finally {
			timer.shutdownNow();
			assertTrue(timer.awaitTermination(2, TimeUnit.SECONDS));
		}
	}

	@Test
	public void concurrentManualTransitionsAreSerializedRatherThanDropped() throws Exception {
		PluginManager pluginManager = configureDetectedDay(transitionState());
		CountDownLatch firstEntered = new CountDownLatch(1);
		CountDownLatch releaseFirst = new CountDownLatch(1);
		CountDownLatch weekObserved = new CountDownLatch(1);
		Mockito.doAnswer(call -> {
			Event event = call.getArgument(0);
			if (event instanceof DayChangeEvent) {
				firstEntered.countDown();
				assertTrue(releaseFirst.await(2, TimeUnit.SECONDS));
			} else if (event instanceof com.bencodez.advancedcore.api.time.events.WeekChangeEvent) {
				weekObserved.countDown();
			}
			return null;
		}).when(pluginManager).callEvent(any(Event.class));
		TimeChecker checker = new TimeChecker(plugin);
		Thread first = new Thread(() -> checker.forceChanged(TimeType.DAY, true, false, false));
		Thread second = new Thread(() -> checker.forceChanged(TimeType.WEEK, true, false, false));

		first.start();
		assertTrue(firstEntered.await(2, TimeUnit.SECONDS));
		second.start();
		assertFalse(weekObserved.await(100, TimeUnit.MILLISECONDS));
		releaseFirst.countDown();
		first.join(2_000);
		second.join(2_000);

		assertFalse(first.isAlive());
		assertFalse(second.isAlive());
		assertTrue(weekObserved.await(2, TimeUnit.SECONDS));
	}

	@Test
	public void reentrantManualTransitionRunsAfterCurrentDispatchWithoutDeadlock() throws Exception {
		PluginManager pluginManager = configureDetectedDay(transitionState());
		TimeChecker checker = new TimeChecker(plugin);
		CountDownLatch weekObserved = new CountDownLatch(1);
		Mockito.doAnswer(call -> {
			Event event = call.getArgument(0);
			if (event instanceof DayChangeEvent) checker.forceChanged(TimeType.WEEK, true, false, false);
			else if (event instanceof com.bencodez.advancedcore.api.time.events.WeekChangeEvent) {
				weekObserved.countDown();
			}
			return null;
		}).when(pluginManager).callEvent(any(Event.class));
		var worker = java.util.concurrent.Executors.newSingleThreadExecutor();
		try {
			worker.submit(() -> checker.forceChanged(TimeType.DAY, true, false, false)).get(2, TimeUnit.SECONDS);
			assertTrue(weekObserved.await(2, TimeUnit.SECONDS));
		} finally {
			worker.shutdownNow();
			assertTrue(worker.awaitTermination(2, TimeUnit.SECONDS));
		}
	}

	@Test
	public void reentrantManualTransitionAcceptedBeforeShutdownIsDrained() {
		PluginManager pluginManager = configureDetectedDay(transitionState());
		TimeChecker checker = new TimeChecker(plugin);
		AtomicReference<CompletionStage<Void>> drain = new AtomicReference<>();
		AtomicBoolean weekObserved = new AtomicBoolean();
		AtomicBoolean monthObserved = new AtomicBoolean();
		Mockito.doAnswer(call -> {
			Event event = call.getArgument(0);
			if (event instanceof DayChangeEvent) {
				checker.forceChanged(TimeType.WEEK, true, false, false);
				drain.set(checker.beginShutdown());
			} else if (event instanceof com.bencodez.advancedcore.api.time.events.WeekChangeEvent) {
				weekObserved.set(true);
				checker.forceChanged(TimeType.MONTH, true, false, false);
			} else if (event instanceof com.bencodez.advancedcore.api.time.events.MonthChangeEvent) {
				monthObserved.set(true);
			}
			return null;
		}).when(pluginManager).callEvent(any(Event.class));

		checker.forceChanged(TimeType.DAY, true, false, false);

		assertTrue(weekObserved.get());
		assertFalse(monthObserved.get(), "new work must remain rejected after shutdown begins");
		assertNotNull(drain.get());
		assertTrue(drain.get().toCompletableFuture().isDone());
	}

	@Test
	public void manualTransitionQueuesWithoutBlockingBehindRetainedLease() throws Exception {
		PluginManager pluginManager = configureDetectedDay(transitionState());
		TimeChecker checker = new TimeChecker(plugin,
				Clock.fixed(Instant.parse("2025-01-02T12:00:00Z"), ZoneOffset.UTC));
		AtomicReference<TimeChangeTransition.Lease> lease = new AtomicReference<>();
		CountDownLatch weekObserved = new CountDownLatch(1);
		Mockito.doAnswer(call -> {
			Event event = call.getArgument(0);
			if (event instanceof DayChangeEvent day) lease.set(day.getTransition().retain());
			else if (event instanceof com.bencodez.advancedcore.api.time.events.WeekChangeEvent) {
				weekObserved.countDown();
			}
			return null;
		}).when(pluginManager).callEvent(any(Event.class));
		checker.update();
		var caller = Executors.newSingleThreadExecutor();
		try {
			caller.submit(() -> checker.forceChanged(TimeType.WEEK, true, false, false))
					.get(1, TimeUnit.SECONDS);
			assertFalse(weekObserved.await(100, TimeUnit.MILLISECONDS));
			lease.get().complete();
			assertTrue(weekObserved.await(2, TimeUnit.SECONDS));
		} finally {
			caller.shutdownNow();
			assertTrue(caller.awaitTermination(2, TimeUnit.SECONDS));
		}
	}

	@Test
	public void waitingManualTransitionWakesWhenDispatchEndsBeforeLeaseRelease() throws Exception {
		PluginManager pluginManager = configureDetectedDay(transitionState());
		TimeChecker checker = new TimeChecker(plugin,
				Clock.fixed(Instant.parse("2025-01-02T12:00:00Z"), ZoneOffset.UTC));
		AtomicReference<TimeChangeTransition.Lease> lease = new AtomicReference<>();
		CountDownLatch dispatchEntered = new CountDownLatch(1);
		CountDownLatch releaseDispatch = new CountDownLatch(1);
		CountDownLatch weekObserved = new CountDownLatch(1);
		Mockito.doAnswer(call -> {
			Event event = call.getArgument(0);
			if (event instanceof DayChangeEvent day) {
				lease.set(day.getTransition().retain());
				dispatchEntered.countDown();
				assertTrue(releaseDispatch.await(2, TimeUnit.SECONDS));
			} else if (event instanceof com.bencodez.advancedcore.api.time.events.WeekChangeEvent) {
				weekObserved.countDown();
			}
			return null;
		}).when(pluginManager).callEvent(any(Event.class));
		var workers = Executors.newFixedThreadPool(2);
		try {
			var detected = workers.submit(checker::update);
			assertTrue(dispatchEntered.await(2, TimeUnit.SECONDS));
			var manual = workers.submit(() -> checker.forceChanged(TimeType.WEEK, true, false, false));
			releaseDispatch.countDown();

			detected.get(2, TimeUnit.SECONDS);
			manual.get(2, TimeUnit.SECONDS);
			assertFalse(weekObserved.await(100, TimeUnit.MILLISECONDS));
			lease.get().complete();
			assertTrue(weekObserved.await(2, TimeUnit.SECONDS));
		} finally {
			releaseDispatch.countDown();
			workers.shutdownNow();
			assertTrue(workers.awaitTermination(2, TimeUnit.SECONDS));
		}
	}

	@Test
	public void lifecyclePersistenceFencePreventsLateOldRuntimeWrite() throws Exception {
		TimeChangeTransitionState transition = transitionState();
		PluginManager pluginManager = configureDetectedDay(transition);
		AtomicReference<TimeChangeTransition.Lease> lease = new AtomicReference<>();
		Mockito.doAnswer(call -> {
			if (call.getArgument(0) instanceof DayChangeEvent day) lease.set(day.getTransition().retain());
			return null;
		}).when(pluginManager).callEvent(any(Event.class));
		TimeChecker checker = new TimeChecker(plugin,
				Clock.fixed(Instant.parse("2025-01-02T12:00:00Z"), ZoneOffset.UTC));

		checker.update();
		CompletionStage<Void> drain = checker.beginShutdown();
		checker.cancelActiveTransitionsAndClosePersistence();

		verify(serverDataFile, Mockito.never()).failTimeChangeTransition(any());
		lease.get().complete();
		drain.toCompletableFuture().get(2, TimeUnit.SECONDS);
		verify(serverDataFile, Mockito.never()).completeTimeChangeTransition(any());
		verify(serverDataFile, Mockito.never()).failTimeChangeTransition(any());
	}

	@Test
	public void shutdownBeforeFirstEventSkipsTheCancelledDispatch() {
		PluginManager pluginManager = configureDetectedDay(transitionState());
		AtomicReference<TimeChecker> checkerReference = new AtomicReference<>();
		Mockito.doAnswer(call -> {
			TimeChecker checker = checkerReference.get();
			checker.beginShutdown();
			checker.cancelActiveTransitionsAndClosePersistence();
			return null;
		}).when(plugin).debug("Executing time change events: DAY");
		TimeChecker checker = new TimeChecker(plugin,
				Clock.fixed(Instant.parse("2025-01-02T12:00:00Z"), ZoneOffset.UTC));
		checkerReference.set(checker);

		checker.update();

		verify(pluginManager, Mockito.never()).callEvent(any(Event.class));
		verify(serverDataFile, Mockito.never()).completeTimeChangeTransition(any());
		verify(serverDataFile, Mockito.never()).failTimeChangeTransition(any());
		assertFalse(checker.isActiveProcessing());
	}

	@Test
	public void shutdownAfterPendingSnapshotDoesNotRewriteItUnderTheTransitionLock() {
		TimeChangeTransitionState transition = transitionState();
		PluginManager pluginManager = configureDetectedDay(transition);
		AtomicReference<TimeChecker> checkerReference = new AtomicReference<>();
		when(serverDataFile.beginTimeChangeTransition(any(), anyString(), anyString())).thenAnswer(call -> {
			checkerReference.get().beginShutdown();
			return transition;
		});
		TimeChecker checker = new TimeChecker(plugin,
				Clock.fixed(Instant.parse("2025-01-02T12:00:00Z"), ZoneOffset.UTC));
		checkerReference.set(checker);

		checker.update();

		verify(pluginManager, Mockito.never()).callEvent(any(Event.class));
		verify(serverDataFile, Mockito.never()).completeTimeChangeTransition(any());
		verify(serverDataFile, Mockito.never()).failTimeChangeTransition(any());
		assertFalse(checker.isActiveProcessing());
	}

	@Test
	public void lifecyclePersistenceFenceDoesNotWaitForAnInFlightFinalWrite() throws Exception {
		TimeChangeTransitionState transition = transitionState();
		PluginManager pluginManager = configureDetectedDay(transition);
		AtomicReference<TimeChangeTransition.Lease> lease = new AtomicReference<>();
		CountDownLatch persistenceEntered = new CountDownLatch(1);
		CountDownLatch releasePersistence = new CountDownLatch(1);
		Mockito.doAnswer(call -> {
			if (call.getArgument(0) instanceof DayChangeEvent day) lease.set(day.getTransition().retain());
			return null;
		}).when(pluginManager).callEvent(any(Event.class));
		Mockito.doAnswer(call -> {
			persistenceEntered.countDown();
			assertTrue(releasePersistence.await(2, TimeUnit.SECONDS));
			return null;
		}).when(serverDataFile).completeTimeChangeTransition(transition);
		TimeChecker checker = new TimeChecker(plugin,
				Clock.fixed(Instant.parse("2025-01-02T12:00:00Z"), ZoneOffset.UTC));

		checker.update();
		CompletionStage<Void> drain = checker.beginShutdown();
		lease.get().complete();
		assertTrue(persistenceEntered.await(2, TimeUnit.SECONDS));
		var lifecycle = Executors.newSingleThreadExecutor();
		try {
			lifecycle.submit(checker::cancelActiveTransitionsAndClosePersistence)
					.get(500, TimeUnit.MILLISECONDS);
			assertFalse(drain.toCompletableFuture().isDone());
		} finally {
			releasePersistence.countDown();
			lifecycle.shutdownNow();
			assertTrue(lifecycle.awaitTermination(2, TimeUnit.SECONDS));
		}
		drain.toCompletableFuture().get(2, TimeUnit.SECONDS);
		verify(serverDataFile).completeTimeChangeTransition(transition);
		verify(serverDataFile, Mockito.never()).failTimeChangeTransition(any());
	}

	@Test
	public void retainedLeaseBoundsAndReportsSynchronousManualBacklog() throws Exception {
		PluginManager pluginManager = configureDetectedDay(transitionState());
		Logger logger = mock(Logger.class);
		when(plugin.getLogger()).thenReturn(logger);
		TimeChecker checker = new TimeChecker(plugin,
				Clock.fixed(Instant.parse("2025-01-02T12:00:00Z"), ZoneOffset.UTC));
		AtomicReference<TimeChangeTransition.Lease> lease = new AtomicReference<>();
		CountDownLatch weeksObserved = new CountDownLatch(32);
		Mockito.doAnswer(call -> {
			Event event = call.getArgument(0);
			if (event instanceof DayChangeEvent day) lease.set(day.getTransition().retain());
			else if (event instanceof com.bencodez.advancedcore.api.time.events.WeekChangeEvent) {
				weeksObserved.countDown();
			}
			return null;
		}).when(pluginManager).callEvent(any(Event.class));
		checker.update();

		for (int index = 0; index < 40; index++) {
			checker.forceChanged(TimeType.WEEK, true, false, false);
		}
		lease.get().complete();

		assertTrue(weeksObserved.await(2, TimeUnit.SECONDS));
		verify(logger).warning("Rejected forced time change because 32 manual transitions are already pending");
	}

	@Test
	public void asynchronousManualAdmissionIsBoundedBeforeExecutorQueueing() throws Exception {
		Logger logger = mock(Logger.class);
		when(plugin.getLogger()).thenReturn(logger);
		ScheduledThreadPoolExecutor timer = new ScheduledThreadPoolExecutor(1);
		CountDownLatch occupied = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		try {
			timer.execute(() -> {
				occupied.countDown();
				try { release.await(); }
				catch (InterruptedException interruption) { Thread.currentThread().interrupt(); }
			});
			assertTrue(occupied.await(2, TimeUnit.SECONDS));
			TimeChecker checker = new TimeChecker(plugin);
			checker.setTimer(timer);

			for (int index = 0; index < 40; index++) checker.forceChanged(TimeType.DAY);

			assertEquals(32, timer.getQueue().size());
			verify(logger).warning("Rejected forced time change because 32 manual transitions are already pending");
		} finally {
			release.countDown();
			timer.shutdownNow();
			assertTrue(timer.awaitTermination(2, TimeUnit.SECONDS));
		}
	}

	@Test
	public void fatalListenerErrorRemainsPendingAndPropagates() {
		TimeChangeTransitionState transition = transitionState();
		PluginManager pluginManager = configureDetectedDay(transition);
		AssertionError fatal = new AssertionError("fatal listener failure");
		Mockito.doAnswer(call -> {
			if (call.getArgument(0) instanceof DayChangeEvent) throw fatal;
			return null;
		}).when(pluginManager).callEvent(any(Event.class));
		TimeChecker checker = new TimeChecker(plugin, Clock.fixed(Instant.parse("2025-01-02T12:00:00Z"), ZoneOffset.UTC));

		assertEquals(fatal, assertThrows(AssertionError.class, checker::update));
		verify(serverDataFile).failTimeChangeTransition(transition);
		verify(serverDataFile, Mockito.never()).completeTimeChangeTransition(any());
	}

	@Test
	public void pendingTransitionIsRecoveredBeforeOneTimeIgnoreMarkersAdvance() {
		TimeChangeTransitionState transition = transitionState();
		configurePendingTransition(TimeType.DAY, transition);
		when(serverDataFile.isIgnoreTime()).thenReturn(true);

		new TimeChecker(plugin, Clock.fixed(Instant.parse("2025-01-02T12:00:00Z"), ZoneOffset.UTC)).update();

		verify(serverDataFile).completeTimeChangeTransition(transition);
		verify(serverDataFile, Mockito.never()).setIgnoreTime(false);
	}

	@Test
	public void dayDetectedTransitionAdvancesOnlyAfterSuccessfulProcessing() {
		assertDetectedTransitionAdvancesOnlyAfterSuccessfulProcessing(TimeType.DAY);
	}

	@Test
	public void weekDetectedTransitionAdvancesOnlyAfterSuccessfulProcessing() {
		assertDetectedTransitionAdvancesOnlyAfterSuccessfulProcessing(TimeType.WEEK);
	}

	@Test
	public void monthDetectedTransitionAdvancesOnlyAfterSuccessfulProcessing() {
		assertDetectedTransitionAdvancesOnlyAfterSuccessfulProcessing(TimeType.MONTH);
	}

	private void assertDetectedTransitionAdvancesOnlyAfterSuccessfulProcessing(TimeType type) {
		TimeChangeTransitionState transition = transitionState(type);
		PluginManager pluginManager = configurePendingTransition(type, transition);
		AtomicReference<TimeChangeTransition> observed = new AtomicReference<>();
		Mockito.doAnswer(call -> {
			if (call.getArgument(0) instanceof DateChangedEvent event) observed.set(event.getTransition());
			return null;
		}).when(pluginManager).callEvent(any(Event.class));

		new TimeChecker(plugin, Clock.fixed(Instant.parse("2025-01-02T12:00:00Z"), ZoneOffset.UTC)).update();

		assertNotNull(observed.get());
		assertEquals(type, observed.get().getType());
		verify(serverDataFile).completeTimeChangeTransition(transition);
		verify(serverDataFile, Mockito.never()).failTimeChangeTransition(transition);
	}

	@Test
	public void dayDetectedTransitionRemainsPendingAfterFailure() {
		assertDetectedTransitionRemainsPendingAfterFailure(TimeType.DAY);
	}

	@Test
	public void weekDetectedTransitionRemainsPendingAfterFailure() {
		assertDetectedTransitionRemainsPendingAfterFailure(TimeType.WEEK);
	}

	@Test
	public void monthDetectedTransitionRemainsPendingAfterFailure() {
		assertDetectedTransitionRemainsPendingAfterFailure(TimeType.MONTH);
	}

	private void assertDetectedTransitionRemainsPendingAfterFailure(TimeType type) {
		TimeChangeTransitionState transition = transitionState(type);
		PluginManager pluginManager = configurePendingTransition(type, transition);
		Mockito.doAnswer(call -> {
			if (call.getArgument(0) instanceof DateChangedEvent) throw new IllegalStateException("listener failure");
			return null;
		}).when(pluginManager).callEvent(any(Event.class));

		new TimeChecker(plugin, Clock.fixed(Instant.parse("2025-01-02T12:00:00Z"), ZoneOffset.UTC)).update();

		verify(serverDataFile, Mockito.never()).completeTimeChangeTransition(any());
		verify(serverDataFile).failTimeChangeTransition(transition);
	}

	@Test
	public void pendingTransitionIsRecoveredWithItsOriginalIdBeforeClockDetection() {
		TimeChangeTransitionState transition = transitionState();
		PluginManager pluginManager = configureDetectedDay(transition);
		when(serverDataFile.getPrevDay()).thenReturn(2);
		when(serverDataFile.getPendingTimeChangeTransition(TimeType.DAY)).thenReturn(transition);
		AtomicReference<TimeChangeTransition> observed = new AtomicReference<>();
		Mockito.doAnswer(call -> {
			if (call.getArgument(0) instanceof DayChangeEvent day) observed.set(day.getTransition());
			return null;
		}).when(pluginManager).callEvent(any(Event.class));

		new TimeChecker(plugin, Clock.fixed(Instant.parse("2025-01-05T12:00:00Z"), ZoneOffset.UTC)).update();

		assertNotNull(observed.get());
		assertEquals("DAY:2025-01-02", observed.get().getId());
		verify(serverDataFile, Mockito.never()).beginTimeChangeTransition(any(), anyString(), anyString());
		verify(serverDataFile).completeTimeChangeTransition(transition);
	}

	private PluginManager configureDetectedDay(TimeChangeTransitionState transition) {
		Server server = mock(Server.class);
		PluginManager pluginManager = mock(PluginManager.class);
		when(plugin.getServer()).thenReturn(server);
		when(server.getPluginManager()).thenReturn(pluginManager);
		when(plugin.getLogger()).thenReturn(Logger.getLogger("TimeCheckerTest"));
		when(options.getTimeHourOffSet()).thenReturn(0);
		when(options.getTimeZone()).thenReturn("UTC");
		when(options.getTimeWeekOffSet()).thenReturn(0);
		when(options.isTimeChangeFailSafeBypass()).thenReturn(true);
		when(serverDataFile.isIgnoreTime()).thenReturn(false);
		when(serverDataFile.getPrevMonth()).thenReturn("JANUARY");
		when(serverDataFile.getPrevWeekDay()).thenReturn(1);
		when(serverDataFile.getPrevDay()).thenReturn(1);
		when(serverDataFile.beginTimeChangeTransition(any(), anyString(), anyString())).thenReturn(transition);
		return pluginManager;
	}

	private PluginManager configurePendingTransition(TimeType type, TimeChangeTransitionState transition) {
		PluginManager pluginManager = configureDetectedDay(transition);
		when(serverDataFile.getPendingTimeChangeTransition(type)).thenReturn(transition);
		return pluginManager;
	}

	private TimeChangeTransitionState transitionState() {
		return transitionState(TimeType.DAY);
	}

	private TimeChangeTransitionState transitionState(TimeType type) {
		return new TimeChangeTransitionState(type, type + ":2025-01-02", "2025-01-02", "2", true);
	}
}
