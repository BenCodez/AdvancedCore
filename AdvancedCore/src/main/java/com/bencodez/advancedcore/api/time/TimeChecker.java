package com.bencodez.advancedcore.api.time;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.time.events.DateChangedEvent;
import com.bencodez.advancedcore.api.time.events.DayChangeEvent;
import com.bencodez.advancedcore.api.time.events.MonthChangeEvent;
import com.bencodez.advancedcore.api.time.events.PreDateChangedEvent;
import com.bencodez.advancedcore.api.time.events.WeekChangeEvent;

import lombok.Getter;
import lombok.Setter;

public class TimeChecker {
	private final AdvancedCorePlugin plugin;
	private final Clock clock;

	@Getter
	private boolean activeProcessing = false;

	@Getter
	@Setter
	private ScheduledExecutorService timer;

	private boolean timerLoaded = false;

	@Getter
	private boolean processingEnabled = true;

	public TimeChecker(AdvancedCorePlugin plugin) {
		this(plugin, Clock.systemDefaultZone());
	}

	public TimeChecker(AdvancedCorePlugin plugin, Clock clock) {
		this.plugin = plugin;
		this.clock = clock == null ? Clock.systemDefaultZone() : clock;
	}

	public void forceChanged(TimeType time) {
		timer.execute(() -> forceChanged(time, true, true, true));
	}

	public synchronized void forceChanged(TimeType time, boolean fake, boolean preDate, boolean postDate) {
		activeProcessing = true;
		try {
			plugin.debug("Executing time change events: " + time);
			plugin.getLogger().info("Time change event: " + time + ", Fake: " + fake);
			if (preDate) {
				PreDateChangedEvent preDateChanged = new PreDateChangedEvent(time);
				preDateChanged.setFake(fake);
				plugin.getServer().getPluginManager().callEvent(preDateChanged);
			}
			if (TimeType.DAY.equals(time)) {
				DayChangeEvent event = new DayChangeEvent();
				event.setFake(fake);
				plugin.getServer().getPluginManager().callEvent(event);
			} else if (TimeType.WEEK.equals(time)) {
				WeekChangeEvent event = new WeekChangeEvent();
				event.setFake(fake);
				plugin.getServer().getPluginManager().callEvent(event);
			} else if (TimeType.MONTH.equals(time)) {
				MonthChangeEvent event = new MonthChangeEvent();
				event.setFake(fake);
				plugin.getServer().getPluginManager().callEvent(event);
			}
			if (postDate) {
				DateChangedEvent event = new DateChangedEvent(time);
				event.setFake(fake);
				plugin.getServer().getPluginManager().callEvent(event);
			}
			plugin.debug("Finished executing time change events: " + time);
		} catch (Exception e) {
			plugin.getLogger().warning("Failed to process time change " + time + ": " + e.getMessage());
			plugin.debug(e);
		} finally {
			activeProcessing = false;
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
		if (prevDay == day) {
			return false;
		}
		if (set) {
			plugin.getServerDataFile().setPrevDay(day);
		}
		return true;
	}

	public boolean hasMonthChanged(boolean set) {
		String prevMonth = plugin.getServerDataFile().getPrevMonth();
		String month = getTime().getMonth().toString();
		if (prevMonth.equals(month)) {
			return false;
		}
		if (set) {
			plugin.getServerDataFile().setPrevMonth(month);
		}
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
		if (weekNumber == prevDate) {
			return false;
		}
		if (set) {
			plugin.getServerDataFile().setPrevWeekDay(weekNumber);
		}
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
				if (!isActiveProcessing() && isProcessingEnabled()) {
					update();
				}
			} else {
				timer.shutdown();
				timerLoaded = false;
			}
		}, 60, 5, TimeUnit.SECONDS);
		timer.scheduleAtFixedRate(() -> {
			plugin.getServerDataFile().setLastUpdated();
			if (!isProcessingEnabled()) {
				plugin.debug("Processing time changes locally disabled");
				if (hasDayChanged(false)) {
					hasDayChanged(true);
				}
				if (hasWeekChanged(false)) {
					hasWeekChanged(true);
				}
				if (hasMonthChanged(false)) {
					hasMonthChanged(true);
				}
			}
		}, 60, 60, TimeUnit.MINUTES);
	}

	public void setProcessingEnabled(boolean value) {
		processingEnabled = value;
		plugin.debug("Local time change processing " + (value ? "enabled" : "disabled"));
	}

	public void update() {
		if (plugin == null) {
			return;
		}
		if (hasTimeOffSet()) {
			plugin.extraDebug("TimeHourOffSet: " + getTime().getHour() + ":" + getTime().getMinute());
		}
		if (plugin.getServerDataFile().isIgnoreTime()) {
			hasDayChanged(true);
			hasMonthChanged(true);
			hasWeekChanged(true);
			plugin.getServerDataFile().setIgnoreTime(false);
			plugin.getLogger().info("Ignoring time change events for one time only");
		}
		if (!isActiveProcessing()) {
			if (hasMonthChanged(false)) {
				plugin.getLogger().info("Detected month changed, processing...");
				if (isProcessingEnabled()) {
					forceChanged(TimeType.MONTH, false, true, true);
				} else {
					plugin.debug("Processing time changes locally disabled");
				}
				hasMonthChanged(true);
				plugin.getLogger().info("Finished processing month changes");
			} else if (hasWeekChanged(false)) {
				plugin.getLogger().info("Detected week changed, processing...");
				if (isProcessingEnabled()) {
					forceChanged(TimeType.WEEK, false, true, true);
				} else {
					plugin.debug("Processing time changes locally disabled");
				}
				hasWeekChanged(true);
				plugin.getLogger().info("Finished processing week changes");
			} else if (hasDayChanged(false)) {
				plugin.getLogger().info("Detected day changed, processing...");
				if (isProcessingEnabled()) {
					forceChanged(TimeType.DAY, false, true, true);
				} else {
					plugin.debug("Processing time changes locally disabled");
				}
				hasDayChanged(true);
				plugin.getLogger().info("Finished processing day changes");
			}
		}
	}
}
