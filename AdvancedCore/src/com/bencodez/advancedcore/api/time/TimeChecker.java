package com.bencodez.advancedcore.api.time;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalField;
import java.time.temporal.WeekFields;
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

/**
 * The Class TimeChecker.
 */
public class TimeChecker {

	private AdvancedCorePlugin plugin;

	@Getter
	private boolean activeProcessing = false;

	@Getter
	private ScheduledExecutorService timer;

	private boolean timerLoaded = false;

	@Getter
	private boolean processingEnabled = true;

	public void setProcessingEnabled(boolean value) {
		processingEnabled = value;
		plugin.debug("Local time change processing disabled");
	}

	public TimeChecker(AdvancedCorePlugin plugin) {
		this.plugin = plugin;
	}

	public void forceChanged(TimeType time) {
		timer.execute(new Runnable() {

			@Override
			public void run() {
				forceChanged(time, true, true, true);
			}
		});
	}

	public synchronized void forceChanged(TimeType time, boolean fake, boolean preDate, boolean postDate) {
		activeProcessing = true;
		try {
			plugin.debug("Executing time change events: " + time.toString());
			plugin.getLogger().info("Time change event: " + time.toString() + ", Fake: " + fake);
			if (preDate) {
				PreDateChangedEvent preDateChanged = new PreDateChangedEvent(time);
				preDateChanged.setFake(fake);
				plugin.getServer().getPluginManager().callEvent(preDateChanged);
			}
			if (time.equals(TimeType.DAY)) {
				DayChangeEvent dayChange = new DayChangeEvent();
				dayChange.setFake(fake);
				plugin.getServer().getPluginManager().callEvent(dayChange);
			} else if (time.equals(TimeType.WEEK)) {
				WeekChangeEvent weekChange = new WeekChangeEvent();
				weekChange.setFake(fake);
				plugin.getServer().getPluginManager().callEvent(weekChange);
			} else if (time.equals(TimeType.MONTH)) {
				MonthChangeEvent monthChange = new MonthChangeEvent();
				monthChange.setFake(fake);
				plugin.getServer().getPluginManager().callEvent(monthChange);
			}

			if (postDate) {
				DateChangedEvent dateChanged = new DateChangedEvent(time);
				dateChanged.setFake(fake);
				plugin.getServer().getPluginManager().callEvent(dateChanged);
			}
			activeProcessing = false;

			plugin.debug("Finished executing time change events: " + time.toString());
		} catch (Exception e) {
			e.printStackTrace();
		}
		activeProcessing = false;

	}

	public LocalDateTime getTime() {
		LocalDateTime localNow = LocalDateTime.now();
		if (!plugin.getOptions().getTimeZone().isEmpty()) {
			try {
				ZonedDateTime zonedTime = localNow.atZone(ZoneId.of(plugin.getOptions().getTimeZone()));
				localNow = zonedTime.toLocalDateTime();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return localNow.plusHours(AdvancedCorePlugin.getInstance().getOptions().getTimeHourOffSet());
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
		if (plugin.getServerDataFile().getPrevDay() < LocalDateTime.now().minusDays(1).getMonth()
				.length(YearMonth.now().isLeapYear())) {
			plugin.getLogger().warning(
					"Detected a month change, but current day is not near end of a month, ignoring month change");
			plugin.getServerDataFile().setPrevMonth(month);
			return false;
		}
		return true;

	}

	public boolean hasTimeOffSet() {
		return AdvancedCorePlugin.getInstance().getOptions().getTimeHourOffSet() != 0;
	}

	public boolean hasWeekChanged(boolean set) {
		int prevDate = plugin.getServerDataFile().getPrevWeekDay();
		LocalDateTime date = getTime();
		TemporalField woy = WeekFields.of(Locale.getDefault()).weekOfWeekBasedYear();
		int weekNumber = date.get(woy);
		if (weekNumber == prevDate) {
			return false;
		}
		if (set) {
			plugin.getServerDataFile().setPrevWeekDay(weekNumber);
		}
		return true;
	}

	public void loadTimer() {
		if (!timerLoaded) {
			timerLoaded = true;
			timer = Executors.newScheduledThreadPool(1);
			if (plugin.getServerDataFile().getLastUpdated() > 0) {
				// serverdata.yml hasn't updated for 4 days, don't do time changes
				if (System.currentTimeMillis() - plugin.getServerDataFile().getLastUpdated() > 1000 * 60 * 60 * 24
						* 4) {
					plugin.getServerDataFile().setIgnoreTime(true);
					plugin.getLogger().warning(
							"Skipping time change events, since server has been offline for awhile, use /av forcetimechanged to force them if needed");
				}
			}
			plugin.getServerDataFile().setLastUpdated();
			timer.scheduleWithFixedDelay(new Runnable() {

				@Override
				public void run() {
					if (plugin != null && plugin.isEnabled()) {
						if (!isActiveProcessing() && isProcessingEnabled()) {
							update();
						}
					} else {
						timer.shutdown();
						timerLoaded = false;
					}
				}
			}, 60, 5, TimeUnit.SECONDS);
			timer.scheduleAtFixedRate(new Runnable() {

				@Override
				public void run() {
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
				}
			}, 60, 60, TimeUnit.MINUTES);
		} else {
			AdvancedCorePlugin.getInstance().debug("Timer is already loaded");
		}
	}

	/**
	 * Update.
	 */
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
			// stagger process time change events to prevent overloading mysql table
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