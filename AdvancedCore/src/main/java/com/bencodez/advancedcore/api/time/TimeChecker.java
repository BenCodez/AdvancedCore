package com.bencodez.advancedcore.api.time;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.WeekFields;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.time.events.DateChangedEvent;
import com.bencodez.advancedcore.api.time.events.DayChangeEvent;
import com.bencodez.advancedcore.api.time.events.MonthChangeEvent;
import com.bencodez.advancedcore.api.time.events.PreDateChangedEvent;
import com.bencodez.advancedcore.api.time.events.WeekChangeEvent;
import com.bencodez.advancedcore.data.ServerData.TimeChangeTransitionState;

import lombok.Getter;
import lombok.Setter;

/** Checks and dispatches local calendar transitions. */
public class TimeChecker implements TimeChangeTransition.Owner {
	private static final int MAX_PENDING_MANUAL_TRANSITIONS = 32;
	private final AdvancedCorePlugin plugin;
	private final Clock clock;
	private final Object transitionLock = new Object();
	private final Object transitionPersistenceLock = new Object();
	private final Deque<ManualTransition> reentrantTransitions = new ArrayDeque<>();
	private final AtomicInteger pendingManualTransitions = new AtomicInteger();
	private final AtomicBoolean manualBacklogWarningLogged = new AtomicBoolean();
	private volatile ActiveTransition activeTransition;
	private CompletableFuture<Void> noActiveTransition = CompletableFuture.completedFuture(null);
	private boolean acceptingTransitions = true;

	@Getter
	private volatile boolean activeProcessing = false;

	@Getter
	@Setter
	private ScheduledExecutorService timer;

	private boolean timerLoaded = false;

	@Getter
	private volatile boolean processingEnabled = true;

	public TimeChecker(AdvancedCorePlugin plugin) {
		this(plugin, Clock.systemDefaultZone());
	}

	public TimeChecker(AdvancedCorePlugin plugin, Clock clock) {
		this.plugin = plugin;
		this.clock = clock == null ? Clock.systemDefaultZone() : clock;
	}

	/** Queues a manual transition while this checker still accepts work. */
	public void forceChanged(TimeType time) {
		if (time == null || !reserveManualTransition()) return;
		ScheduledExecutorService currentTimer = timer;
		if (currentTimer == null) {
			releaseManualTransition();
			plugin.debug("Unable to force a time change before the time checker timer is loaded");
			return;
		}
		try {
			currentTimer.execute(() -> startTransition(time, true, true, true, null, true, true));
		} catch (RejectedExecutionException rejected) {
			releaseManualTransition();
			plugin.debug("Ignoring forced time change while the checker is shutting down");
		}
	}

	/** Executes a legacy/manual transition synchronously without a durable marker. */
	public void forceChanged(TimeType time, boolean fake, boolean preDate, boolean postDate) {
		if (time == null || !reserveManualTransition()) return;
		startTransition(time, fake, preDate, postDate, null, true, true);
	}

	private boolean reserveManualTransition() {
		while (true) {
			int pending = pendingManualTransitions.get();
			if (pending >= MAX_PENDING_MANUAL_TRANSITIONS) {
				if (manualBacklogWarningLogged.compareAndSet(false, true)) {
					plugin.getLogger().warning("Rejected forced time change because "
							+ MAX_PENDING_MANUAL_TRANSITIONS + " manual transitions are already pending");
				}
				return false;
			}
			if (pendingManualTransitions.compareAndSet(pending, pending + 1)) return true;
		}
	}

	private void releaseManualTransition() {
		pendingManualTransitions.decrementAndGet();
		manualBacklogWarningLogged.set(false);
	}

	/**
	 * Stops new admission and returns a non-blocking stage which completes once
	 * already admitted work has durably recorded success or recovery state.
	 */
	public CompletionStage<Void> beginShutdown() {
		synchronized (transitionLock) {
			acceptingTransitions = false;
			transitionLock.notifyAll();
			return noActiveTransition;
		}
	}

	/**
	 * Retains active work as recoverable before the lifecycle watchdog interrupts
	 * the time executor. The drain stage stays active until leases release.
	 */
	public void abortActiveTransitions() {
		ActiveTransition active;
		boolean persist;
		synchronized (transitionLock) {
			active = activeTransition;
			if (active == null || active.finalizing) return;
		}
		synchronized (transitionPersistenceLock) {
			synchronized (transitionLock) {
				active = activeTransition;
				if (active == null || active.finalizing) return;
				cancel(active, "Time transition was cancelled by bounded shutdown");
				active.finished = true;
				persist = !active.persistenceClosed;
				active.persistenceClosed = true;
				discardQueuedManualTransitions();
			}
			if (persist) persistFailure(active);
		}
		retireTransition(active);
	}

	/** Requests cooperative cancellation without retiring the active lease boundary. */
	public void cancelActiveTransitions() {
		synchronized (transitionLock) {
			ActiveTransition active = activeTransition;
			if (active == null || active.finalizing || active.finished) return;
			cancel(active, "Time transition was cancelled because plugin shutdown began");
		}
	}

	/**
	 * Cancels admitted work and closes this checker's authority to persist its
	 * transition result. The active drain remains open until retained work stops,
	 * so dependent user storage is not retired while a listener is still running.
	 */
	public void cancelActiveTransitionsAndClosePersistence() {
		ActiveTransition active;
		boolean persist = false;
		boolean retire;
		synchronized (transitionPersistenceLock) {
			synchronized (transitionLock) {
				active = activeTransition;
				if (active == null) return;
				if (!active.persistenceClosed) {
					cancel(active, "Time transition was cancelled because plugin shutdown began");
					active.persistenceClosed = true;
					persist = true;
				}
				discardQueuedManualTransitions();
				retire = active.finished;
			}
			if (persist) persistFailure(active);
		}
		if (retire) retireTransition(active);
	}

	private void cancel(ActiveTransition active, String message) {
		active.failed = true;
		active.cancellationRequested = true;
		active.failure = new java.util.concurrent.CancellationException(message);
	}

	@Override
	public TimeChangeTransition.Lease retain(TimeChangeTransition transition) {
		ActiveTransition active;
		synchronized (transitionLock) {
			active = activeTransition;
			if (active == null || active.transition != transition || active.finished) {
				throw new IllegalStateException("The time transition is no longer active");
			}
			active.participants++;
		}
		return new Lease(active);
	}

	@Override
	public boolean isCancellationRequested(TimeChangeTransition transition) {
		synchronized (transitionLock) {
			ActiveTransition active = activeTransition;
			return active == null || active.transition != transition || active.cancellationRequested;
		}
	}

	public LocalDateTime getTime() {
		try {
			return TimeCalculation.currentTime(clock, plugin.getOptions().getTimeZone(),
					plugin.getOptions().getTimeHourOffSet());
		} catch (Exception e) {
			plugin.getLogger().warning("Invalid time zone '" + plugin.getOptions().getTimeZone()
					+ "', using the server clock zone instead");
			plugin.debug(e);
			return TimeCalculation.currentTime(clock, "", plugin.getOptions().getTimeHourOffSet());
		}
	}

	public boolean hasDayChanged(boolean set) {
		int prevDay = plugin.getServerDataFile().getPrevDay();
		int day = getTime().getDayOfMonth();
		if (prevDay == day) return false;
		if (set) plugin.getServerDataFile().setPrevDay(day);
		return true;
	}

	public boolean hasMonthChanged(boolean set) {
		String prevMonth = plugin.getServerDataFile().getPrevMonth();
		String month = getTime().getMonth().toString();
		if (prevMonth.equals(month)) return false;
		if (set) plugin.getServerDataFile().setPrevMonth(month);
		if (!plugin.getOptions().isTimeChangeFailSafeBypass() && getTime().getDayOfMonth() > 3) {
			plugin.getLogger().warning(
					"Detected a month change, but current day is not near end of a month, ignoring month change, "
							+ getTime().getDayOfMonth());
			plugin.getServerDataFile().setPrevMonth(month);
			return false;
		}
		return true;
	}

	public boolean hasTimeOffSet() {
		return plugin.getOptions().getTimeHourOffSet() != 0;
	}

	public boolean hasWeekChanged(boolean set) {
		int prevDate = plugin.getServerDataFile().getPrevWeekDay();
		int weekNumber = TimeCalculation.weekNumber(getTime(), plugin.getOptions().getTimeWeekOffSet(), Locale.getDefault());
		if (weekNumber == prevDate) return false;
		if (set) plugin.getServerDataFile().setPrevWeekDay(weekNumber);
		return true;
	}

	public synchronized void loadTimer() {
		if (timerLoaded) {
			plugin.debug("Timer is already loaded");
			return;
		}
		timerLoaded = true;
		timer = Executors.newSingleThreadScheduledExecutor();
		if (plugin.getServerDataFile().getLastUpdated() > 0
				&& System.currentTimeMillis() - plugin.getServerDataFile().getLastUpdated() > TimeUnit.DAYS.toMillis(4)) {
			plugin.getServerDataFile().setIgnoreTime(true);
			plugin.getLogger().warning(
					"Skipping time change events, since server has been offline for awhile, use /av forcetimechanged to force them if needed");
		}
		plugin.getServerDataFile().setLastUpdated();
		timer.scheduleWithFixedDelay(() -> {
			if (plugin != null && plugin.isEnabled()) {
				if (!isActiveProcessing() && isProcessingEnabled()) update();
			} else {
				timer.shutdown();
				timerLoaded = false;
			}
		}, 60, 5, TimeUnit.SECONDS);
		timer.scheduleAtFixedRate(() -> {
			plugin.getServerDataFile().setLastUpdated();
			if (!isProcessingEnabled()) {
				plugin.debug("Processing time changes locally disabled");
				if (hasDayChanged(false)) hasDayChanged(true);
				if (hasWeekChanged(false)) hasWeekChanged(true);
				if (hasMonthChanged(false)) hasMonthChanged(true);
			}
		}, 60, 60, TimeUnit.MINUTES);
	}

	public void setProcessingEnabled(boolean value) {
		processingEnabled = value;
		plugin.debug("Local time change processing " + (value ? "enabled" : "disabled"));
	}

	public void update() {
		if (plugin == null) return;
		if (hasTimeOffSet()) plugin.extraDebug("TimeHourOffSet: " + getTime().getHour() + ":" + getTime().getMinute());
		if (isActiveProcessing()) return;
		for (TimeType type : new TimeType[] { TimeType.MONTH, TimeType.WEEK, TimeType.DAY }) {
			TimeChangeTransitionState pending = plugin.getServerDataFile().getPendingTimeChangeTransition(type);
			if (pending != null) {
				plugin.getLogger().info("Recovering pending " + type + " time change " + pending.id());
				startDetectedTransition(type, pending);
				return;
			}
		}
		if (plugin.getServerDataFile().isIgnoreTime()) {
			hasDayChanged(true);
			hasMonthChanged(true);
			hasWeekChanged(true);
			plugin.getServerDataFile().setIgnoreTime(false);
			plugin.getLogger().info("Ignoring time change events for one time only");
			return;
		}

		if (hasMonthChanged(false)) {
			plugin.getLogger().info("Detected month changed, processing...");
			if (isProcessingEnabled()) startDetectedTransition(TimeType.MONTH);
			else plugin.debug("Processing time changes locally disabled");
		} else if (hasWeekChanged(false)) {
			plugin.getLogger().info("Detected week changed, processing...");
			if (isProcessingEnabled()) startDetectedTransition(TimeType.WEEK);
			else plugin.debug("Processing time changes locally disabled");
		} else if (hasDayChanged(false)) {
			plugin.getLogger().info("Detected day changed, processing...");
			if (isProcessingEnabled()) startDetectedTransition(TimeType.DAY);
			else plugin.debug("Processing time changes locally disabled");
		}
	}

	private void startDetectedTransition(TimeType type) {
		LocalDateTime current = getTime();
		TimeChangeTransitionState persisted = plugin.getServerDataFile().beginTimeChangeTransition(type,
				periodKey(type, current), markerValue(type, current));
		startDetectedTransition(type, persisted);
	}

	private void startDetectedTransition(TimeType type, TimeChangeTransitionState persisted) {
		TimeChangeTransition transition = new TimeChangeTransition(this, persisted.id(), persisted.periodKey(), type);
		startTransition(type, false, true, true, new DurableTransition(transition, persisted));
	}

	private void startTransition(TimeType type, boolean fake, boolean preDate, boolean postDate, DurableTransition durable) {
		startTransition(type, fake, preDate, postDate, durable, false, false);
	}

	private void startTransition(TimeType type, boolean fake, boolean preDate, boolean postDate,
			DurableTransition durable, boolean waitForTurn, boolean manualReservation) {
		if (type == null) {
			if (manualReservation) releaseManualTransition();
			return;
		}
		ActiveTransition active;
		boolean interrupted = false;
		synchronized (transitionLock) {
			if (waitForTurn && activeTransition != null
					&& activeTransition.dispatchThread == Thread.currentThread()) {
				if (acceptingTransitions) {
					reentrantTransitions.addLast(new ManualTransition(type, fake, preDate, postDate));
				} else if (manualReservation) releaseManualTransition();
				return;
			}
			if (waitForTurn && activeTransition != null && activeTransition.dispatchComplete) {
				if (acceptingTransitions) {
					reentrantTransitions.addLast(new ManualTransition(type, fake, preDate, postDate));
				} else if (manualReservation) releaseManualTransition();
				return;
			}
			while (waitForTurn && acceptingTransitions && activeTransition != null) {
				if (activeTransition.dispatchComplete) {
					reentrantTransitions.addLast(new ManualTransition(type, fake, preDate, postDate));
					return;
				}
				try { transitionLock.wait(); }
				catch (InterruptedException interruption) { interrupted = true; }
			}
			if (interrupted) Thread.currentThread().interrupt();
			if (!acceptingTransitions) {
				if (durable != null) persistFailure(durable.persisted);
				if (manualReservation) releaseManualTransition();
				return;
			}
			if (activeTransition != null) {
				if (manualReservation) releaseManualTransition();
				return;
			}
			CompletableFuture<Void> drain = new CompletableFuture<>();
			active = new ActiveTransition(type, durable == null ? null : durable.transition,
					durable == null ? null : durable.persisted, drain, Thread.currentThread(), manualReservation);
			activeTransition = active;
			noActiveTransition = drain;
			activeProcessing = true;
		}
		dispatchTransition(active, type, fake, preDate, postDate);
	}

	private void dispatchTransition(ActiveTransition active, TimeType type, boolean fake, boolean preDate,
			boolean postDate) {
		Throwable failure = null;
		try {
			plugin.debug("Executing time change events: " + type);
			plugin.getLogger().info("Time change event: " + type + ", Fake: " + fake);
			TimeChangeTransition transition = active.transition;
			if (preDate) {
				PreDateChangedEvent preDateChanged = new PreDateChangedEvent(type, transition);
				preDateChanged.setFake(fake);
				plugin.getServer().getPluginManager().callEvent(preDateChanged);
			}
			if (TimeType.DAY.equals(type)) {
				DayChangeEvent event = new DayChangeEvent(transition);
				event.setFake(fake);
				plugin.getServer().getPluginManager().callEvent(event);
			} else if (TimeType.WEEK.equals(type)) {
				WeekChangeEvent event = new WeekChangeEvent(transition);
				event.setFake(fake);
				plugin.getServer().getPluginManager().callEvent(event);
			} else if (TimeType.MONTH.equals(type)) {
				MonthChangeEvent event = new MonthChangeEvent(transition);
				event.setFake(fake);
				plugin.getServer().getPluginManager().callEvent(event);
			}
			if (postDate) {
				DateChangedEvent event = new DateChangedEvent(type, transition);
				event.setFake(fake);
				plugin.getServer().getPluginManager().callEvent(event);
			}
			plugin.debug("Finished executing time change events: " + type);
		} catch (RuntimeException thrown) {
			failure = thrown;
			plugin.getLogger().warning("Failed to process time change " + type + ": " + thrown.getMessage());
			plugin.debug(thrown);
		} catch (Error fatal) {
			failure = fatal;
			throw fatal;
		} finally {
			synchronized (transitionLock) {
				active.dispatchComplete = true;
				transitionLock.notifyAll();
			}
			finish(active, failure);
		}
	}

	private void finish(ActiveTransition active, Throwable failure) {
		finish(active, failure, false);
	}

	private void finish(ActiveTransition active, Throwable failure, boolean deferPersistence) {
		synchronized (transitionLock) {
			if (active.finished) return;
			if (failure != null) {
				active.failed = true;
				active.failure = failure;
			}
			if (--active.participants > 0) return;
			active.finished = true;
		}
		if (deferPersistence && active.persisted != null) {
			scheduleFinalization(active);
			return;
		}
		finalizeTransition(active);
	}

	private void scheduleFinalization(ActiveTransition active) {
		Runnable finalization = () -> finalizeTransition(active);
		ScheduledExecutorService currentTimer = timer;
		if (tryExecute(currentTimer, finalization)) return;
		ScheduledExecutorService storageTimer = plugin.getTimer();
		if (storageTimer != currentTimer && tryExecute(storageTimer, finalization)) return;
		Thread fallback = new Thread(finalization, "AdvancedCore-Time-Transition-Completion");
		fallback.setDaemon(true);
		fallback.start();
	}

	private boolean tryExecute(ScheduledExecutorService executor, Runnable task) {
		if (executor == null) return false;
		try {
			executor.execute(task);
			return true;
		} catch (RejectedExecutionException rejected) {
			return false;
		}
	}

	private void finalizeTransition(ActiveTransition active) {
		synchronized (transitionPersistenceLock) {
			boolean skipPersistence;
			synchronized (transitionLock) {
				skipPersistence = active.retired || activeTransition != active || active.persistenceClosed;
				if (!skipPersistence) active.finalizing = true;
			}
			if (!skipPersistence) {
				try {
					if (active.persisted != null) {
						if (!active.failed) {
							plugin.getServerDataFile().completeTimeChangeTransition(active.persisted);
							plugin.getLogger().info("Finished processing " + active.type + " changes");
						} else {
							persistFailure(active);
							Throwable cause = active.failure;
							plugin.getLogger().warning("Time change " + active.type + " remains pending for retry"
									+ (cause == null ? "" : ": " + cause.getClass().getSimpleName()
											+ (cause.getMessage() == null ? "" : ": " + cause.getMessage())));
						}
					}
				} catch (RuntimeException persistenceFailure) {
					persistFailure(active);
					plugin.getLogger().warning("Failed to record time change transition "
							+ (active.transition == null ? "" : active.transition.getId()) + ": " + persistenceFailure.getMessage());
					plugin.debug(persistenceFailure);
				} finally {
					synchronized (transitionLock) { active.persistenceClosed = true; }
				}
			}
		}
		retireTransition(active);
	}

	private void retireTransition(ActiveTransition active) {
		ActiveTransition nextActive = null;
		ManualTransition next;
		synchronized (transitionLock) {
			if (active.retired && activeTransition != active) return;
			active.retired = true;
			if (active.manualReservation && !active.manualReservationReleased) {
				active.manualReservationReleased = true;
				releaseManualTransition();
			}
			if (activeTransition == active) {
				activeTransition = null;
				activeProcessing = false;
			}
			next = reentrantTransitions.pollFirst();
			if (next != null) {
				nextActive = new ActiveTransition(next.type, null, null, active.drain, Thread.currentThread(), true);
				activeTransition = nextActive;
				noActiveTransition = active.drain;
				activeProcessing = true;
			}
			transitionLock.notifyAll();
		}
		if (nextActive == null) active.drain.complete(null);
		else dispatchTransition(nextActive, next.type, next.fake, next.preDate, next.postDate);
	}

	private void discardQueuedManualTransitions() {
		while (reentrantTransitions.pollFirst() != null) releaseManualTransition();
	}

	private void persistFailure(ActiveTransition active) {
		if (active.persisted != null) persistFailure(active.persisted);
	}

	private void persistFailure(TimeChangeTransitionState persisted) {
		try {
			plugin.getServerDataFile().failTimeChangeTransition(persisted);
		} catch (Throwable failure) {
			plugin.getLogger().warning("Failed to retain recoverable time change transition " + persisted.id()
					+ ": " + failure.getMessage());
			plugin.debug(failure);
		}
	}

	private String markerValue(TimeType type, LocalDateTime current) {
		return switch (type) {
		case DAY -> Integer.toString(current.getDayOfMonth());
		case MONTH -> current.getMonth().toString();
		case WEEK -> Integer.toString(TimeCalculation.weekNumber(current, plugin.getOptions().getTimeWeekOffSet(), Locale.getDefault()));
		};
	}

	private String periodKey(TimeType type, LocalDateTime current) {
		return switch (type) {
		case DAY -> current.toLocalDate().toString();
		case MONTH -> current.getYear() + "-" + String.format(Locale.ROOT, "%02d", current.getMonthValue());
		case WEEK -> {
			LocalDateTime adjusted = current.plusDays(plugin.getOptions().getTimeWeekOffSet());
			WeekFields fields = WeekFields.of(Locale.getDefault());
			yield adjusted.get(fields.weekBasedYear()) + "-W"
					+ String.format(Locale.ROOT, "%02d", adjusted.get(fields.weekOfWeekBasedYear()));
		}
		};
	}

	private final class Lease implements TimeChangeTransition.Lease {
		private final ActiveTransition active;
		private final AtomicBoolean released = new AtomicBoolean();

		private Lease(ActiveTransition active) {
			this.active = active;
		}

		@Override public void complete() {
			if (released.compareAndSet(false, true)) finish(active, null, true);
		}

		@Override public void fail(Throwable failure) {
			if (released.compareAndSet(false, true)) finish(active,
					failure == null ? new IllegalStateException("Time transition participant failed") : failure, true);
		}
	}

	private static final class DurableTransition {
		private final TimeChangeTransition transition;
		private final TimeChangeTransitionState persisted;

		private DurableTransition(TimeChangeTransition transition, TimeChangeTransitionState persisted) {
			this.transition = transition;
			this.persisted = persisted;
		}
	}

	private static final class ManualTransition {
		private final TimeType type;
		private final boolean fake;
		private final boolean preDate;
		private final boolean postDate;

		private ManualTransition(TimeType type, boolean fake, boolean preDate, boolean postDate) {
			this.type = type;
			this.fake = fake;
			this.preDate = preDate;
			this.postDate = postDate;
		}
	}

	private static final class ActiveTransition {
		private final TimeType type;
		private final TimeChangeTransition transition;
		private final TimeChangeTransitionState persisted;
		private final CompletableFuture<Void> drain;
		private final Thread dispatchThread;
		private final boolean manualReservation;
		private int participants = 1;
		private boolean failed;
		private boolean finished;
		private boolean cancellationRequested;
		private boolean dispatchComplete;
		private boolean finalizing;
		private boolean persistenceClosed;
		private boolean retired;
		private boolean manualReservationReleased;
		private Throwable failure;

		private ActiveTransition(TimeType type, TimeChangeTransition transition, TimeChangeTransitionState persisted,
				CompletableFuture<Void> drain, Thread dispatchThread, boolean manualReservation) {
			this.type = type;
			this.transition = transition;
			this.persisted = persisted;
			this.drain = drain;
			this.dispatchThread = dispatchThread;
			this.manualReservation = manualReservation;
		}
	}
}
