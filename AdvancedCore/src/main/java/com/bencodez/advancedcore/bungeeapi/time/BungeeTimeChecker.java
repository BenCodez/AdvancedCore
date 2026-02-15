package com.bencodez.advancedcore.bungeeapi.time;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalField;
import java.time.temporal.WeekFields;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.time.TimeType;

import lombok.Getter;
import lombok.Setter;

/**
 * The Class TimeChecker.
 */
public abstract class BungeeTimeChecker {

	private boolean processing = false;

	/**
	 * Gets the timer.
	 *
	 * @return the scheduled executor service
	 */
	@Getter
	private ScheduledExecutorService timer;

	private boolean timerLoaded = false;

	/**
	 * Gets the time change fail safe bypass.
	 *
	 * @return true if bypass is enabled
	 * @param timeChangeFailSafeBypass the bypass flag to set
	 */
	@Getter
	@Setter
	private boolean timeChangeFailSafeBypass = false;

	/**
	 * Gets the time offset.
	 *
	 * @return the time offset in hours
	 */
	@Getter
	private int timeOffSet;

	/**
	 * Gets the time week offset.
	 *
	 * @return the time week offset
	 */
	@Getter
	private int timeWeekOffSet = 0;

	/**
	 * Gets the time zone.
	 *
	 * @return the time zone
	 */
	@Getter
	private String timeZone;

	/**
	 * Instantiates a new bungeecord time checker.
	 *
	 * @param timeZone the time zone
	 * @param timeOffSet the time offset in hours
	 * @param weekOffSet the week offset
	 */
	public BungeeTimeChecker(String timeZone, int timeOffSet, int weekOffSet) {
		this.timeOffSet = timeOffSet;
		this.timeZone = timeZone;
		this.timeWeekOffSet = weekOffSet;
	}

	/**
	 * Debug message.
	 *
	 * @param text the debug text
	 */
	public abstract void debug(String text);

	/**
	 * Force time change.
	 *
	 * @param time the time type
	 */
	public void forceChanged(TimeType time) {
		timer.execute(new Runnable() {

			@Override
			public void run() {
				forceChanged(time, true, true, true);
			}
		});
	}

	/**
	 * Force time change.
	 *
	 * @param time the time type
	 * @param fake whether this is a fake change
	 * @param preDate whether to run pre-date actions
	 * @param postDate whether to run post-date actions
	 */
	public void forceChanged(TimeType time, boolean fake, boolean preDate, boolean postDate) {
		processing = true;
		try {
			debug("Executing time change events: " + time.toString());
			info("Time change event: " + time.toString() + ", Fake: " + fake);
			if (preDate) {
				// timeChanged(time, fake, true, false);
			}

			if (time.equals(TimeType.DAY)) {
				timeChanged(time, fake, false, false);
			} else if (time.equals(TimeType.WEEK)) {
				timeChanged(time, fake, false, false);
			} else if (time.equals(TimeType.MONTH)) {
				timeChanged(time, fake, false, false);
			}

			if (postDate) {
				// timeChanged(time, fake, false, true);
			}

			debug("Finished executing time change events: " + time.toString());
			processing = false;
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	/**
	 * Gets the last updated time.
	 *
	 * @return the last updated timestamp
	 */
	public abstract long getLastUpdated();

	/**
	 * Gets the previous day.
	 *
	 * @return the previous day
	 */
	public abstract int getPrevDay();

	/**
	 * Gets the previous month.
	 *
	 * @return the previous month
	 */
	public abstract String getPrevMonth();

	/**
	 * Gets the previous week.
	 *
	 * @return the previous week
	 */
	public abstract int getPrevWeek();

	/**
	 * Gets the current time with timezone and offset applied.
	 *
	 * @return the current local date time
	 */
	public LocalDateTime getTime() {
		LocalDateTime localNow = LocalDateTime.now();
		if (!timeZone.isEmpty()) {
			try {
				ZonedDateTime zonedTime = localNow.atZone(ZoneId.systemDefault())
						.withZoneSameInstant(ZoneId.of(getTimeZone()));
				localNow = zonedTime.toLocalDateTime();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return localNow.plusHours(timeOffSet);
	}

	/**
	 * Checks if day has changed.
	 *
	 * @param set whether to set the new day
	 * @return true if day has changed
	 */
	public boolean hasDayChanged(boolean set) {
		int prevDay = getPrevDay();
		int day = getTime().getDayOfMonth();

		if (prevDay == day) {
			return false;
		}
		if (set) {
			setPrevDay(day);
		}
		return true;
	}

	/**
	 * Checks if month has changed.
	 *
	 * @param set whether to set the new month
	 * @return true if month has changed
	 */
	public boolean hasMonthChanged(boolean set) {
		String prevMonth = getPrevMonth();
		String month = getTime().getMonth().toString();

		if (prevMonth.equals(month)) {
			return false;
		}
		if (set) {
			setPrevMonth(month);
		}
		if (!timeChangeFailSafeBypass) {
			if (getTime().getDayOfMonth() > 3) {
				warning("Detected a month change, but current day is not near end of a month, ignoring month change, "
						+ getTime().getDayOfMonth());
				setPrevMonth(month);
				return false;
			}
		}
		return true;

	}

	/**
	 * Checks if time has offset.
	 *
	 * @return true if time offset is set
	 */
	public boolean hasTimeOffSet() {
		return getTimeOffSet() != 0;
	}

	/**
	 * Checks if week has changed.
	 *
	 * @param set whether to set the new week
	 * @return true if week has changed
	 */
	public boolean hasWeekChanged(boolean set) {
		int prevDate = getPrevWeek();
		LocalDateTime date = getTime().plusDays(timeWeekOffSet);
		TemporalField woy = WeekFields.of(Locale.getDefault()).weekOfWeekBasedYear();
		int weekNumber = date.get(woy);
		if (weekNumber == prevDate) {
			return false;
		}
		if (set) {
			setPrevWeek(weekNumber);
		}
		return true;
	}

	/**
	 * Info message.
	 *
	 * @param text the info text
	 */
	public abstract void info(String text);

	/**
	 * Checks if time checker is enabled.
	 *
	 * @return true if enabled
	 */
	public abstract boolean isEnabled();

	/**
	 * Checks if time changes should be ignored.
	 *
	 * @return true if ignoring time
	 */
	public abstract boolean isIgnoreTime();

	/**
	 * Load the time checker timer.
	 */
	public void loadTimer() {
		if (!timerLoaded) {
			timerLoaded = true;
			timer = Executors.newScheduledThreadPool(1);
			if (getLastUpdated() > 0) {
				// serverdata.yml hasn't updated for 4 days, don't do time changes
				if (System.currentTimeMillis() - getLastUpdated() > 1000 * 60 * 60 * 24 * 4) {
					setIgnoreTime(true);
					warning("Skipping time change events, since server has been offline for awhile, use /votingpluginbungee forcetimechanged to force them if needed");
				}
			}
			setLastUpdated();
			timer.scheduleWithFixedDelay(new Runnable() {

				@Override
				public void run() {
					if (isEnabled()) {
						if (!processing) {
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
					setLastUpdated();
				}
			}, 60, 60, TimeUnit.MINUTES);
		} else {
			AdvancedCorePlugin.getInstance().debug("Timer is already loaded");
		}
	}

	/**
	 * Sets ignore time flag.
	 *
	 * @param ignore whether to ignore time changes
	 */
	public abstract void setIgnoreTime(boolean ignore);

	/**
	 * Sets the last updated time to now.
	 */
	public abstract void setLastUpdated();

	/**
	 * Sets the previous day.
	 *
	 * @param day the day to set
	 */
	public abstract void setPrevDay(int day);

	/**
	 * Sets the previous month.
	 *
	 * @param text the month to set
	 */
	public abstract void setPrevMonth(String text);

	/**
	 * Sets the previous week.
	 *
	 * @param week the week to set
	 */
	public abstract void setPrevWeek(int week);

	/**
	 * Called when time has changed.
	 *
	 * @param type the time type
	 * @param fake whether this is a fake change
	 * @param pre whether this is pre-change
	 * @param post whether this is post-change
	 */
	public abstract void timeChanged(TimeType type, boolean fake, boolean pre, boolean post);

	/**
	 * Shutdown the time checker.
	 */
	public void shutdown() {
		timer.shutdownNow();
	}

	/**
	 * Update.
	 */
	public void update() {
		if (!isEnabled()) {
			return;
		}
		if (hasTimeOffSet()) {
			debug("TimeHourOffSet: " + getTime().getHour() + ":" + getTime().getMinute());
		}

		if (isIgnoreTime()) {
			hasDayChanged(true);
			hasMonthChanged(true);
			hasWeekChanged(true);
			setIgnoreTime(false);
			info("Ignoring time change events for one time only");
		}

		if (!processing) {
			// stagger process time change events to prevent overloading mysql table
			if (hasMonthChanged(false)) {
				info("Detected month changed, processing...");
				forceChanged(TimeType.MONTH, false, true, true);
				hasMonthChanged(true);
				info("Finished processing month changes");
			} else if (hasWeekChanged(false)) {
				info("Detected week changed, processing...");
				forceChanged(TimeType.WEEK, false, true, true);
				hasWeekChanged(true);
				info("Finished processing week changes");
			} else if (hasDayChanged(false)) {
				info("Detected day changed, processing...");
				forceChanged(TimeType.DAY, false, true, true);
				hasDayChanged(true);
				info("Finished processing day changes");
			}
		}
	}

	/**
	 * Warning message.
	 *
	 * @param text the warning text
	 */
	public abstract void warning(String text);
}