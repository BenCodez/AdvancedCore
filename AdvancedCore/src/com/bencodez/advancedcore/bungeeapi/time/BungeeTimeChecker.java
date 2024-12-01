package com.bencodez.advancedcore.bungeeapi.time;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.TemporalField;
import java.time.temporal.WeekFields;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.time.TimeType;

import lombok.Getter;

/**
 * The Class TimeChecker.
 */
public abstract class BungeeTimeChecker {

	private boolean processing = false;

	@Getter
	private ScheduledExecutorService timer;

	private boolean timerLoaded = false;

	@Getter
	private int timeOffSet;

	@Getter
	private String timeZone;

	public BungeeTimeChecker(String timeZone, int timeOffSet) {
		this.timeOffSet = timeOffSet;
		this.timeZone = timeZone;
	}

	public abstract void info(String text);

	public abstract void debug(String text);

	public abstract void warning(String text);

	public abstract void timeChanged(TimeType type, boolean fake, boolean pre, boolean post);

	public abstract void setPrevDay(int day);

	public abstract int getPrevDay();

	public abstract void setPrevWeek(int week);

	public abstract int getPrevWeek();

	public abstract String getPrevMonth();

	public abstract void setPrevMonth(String text);

	public void forceChanged(TimeType time) {
		timer.execute(new Runnable() {

			@Override
			public void run() {
				forceChanged(time, true, true, true);
			}
		});
	}

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

	public LocalDateTime getTime() {
		LocalDateTime localNow = LocalDateTime.now();
		if (!timeZone.isEmpty()) {
			try {
				localNow.atZone(ZoneId.of(timeZone));
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return localNow.plusHours(timeOffSet);
	}

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

	public boolean hasMonthChanged(boolean set) {
		String prevMonth = getPrevMonth();
		String month = getTime().getMonth().toString();

		if (prevMonth.equals(month)) {
			return false;
		}
		if (set) {
			setPrevMonth(month);
		}
		int cDay = (LocalDateTime.now().minusDays(1).getMonth().length(YearMonth.now().isLeapYear()) - 3);
		if (getPrevDay() < cDay) {
			warning("Detected a month change, but current day is not near end of a month, ignoring month change, "
					+ getPrevDay() + " " + cDay);
			setPrevMonth(month);
			return false;
		}
		return true;

	}

	public boolean hasTimeOffSet() {
		return getTimeOffSet() != 0;
	}

	public boolean hasWeekChanged(boolean set) {
		int prevDate = getPrevWeek();
		LocalDateTime date = getTime();
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

	public abstract void setLastUpdated();

	public abstract long getLastUpdated();

	public abstract void setIgnoreTime(boolean ignore);

	public abstract boolean isIgnoreTime();

	public abstract boolean isEnabled();

	public void loadTimer() {
		if (!timerLoaded) {
			timerLoaded = true;
			timer = Executors.newScheduledThreadPool(1);
			if (getLastUpdated() > 0) {
				// serverdata.yml hasn't updated for 4 days, don't do time changes
				if (System.currentTimeMillis() - getLastUpdated() > 1000 * 60 * 60 * 24 * 4) {
					setIgnoreTime(true);
					warning("Skipping time change events, since server has been offline for awhile, use /av forcetimechanged to force them if needed");
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
}