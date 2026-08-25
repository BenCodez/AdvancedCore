package com.bencodez.advancedcore.bungeeapi.time;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.bencodez.advancedcore.api.time.TimeCalculation;
import com.bencodez.advancedcore.api.time.TimeType;

import lombok.Getter;
import lombok.Setter;

/**
 * Proxy-side time change checker.
 */
public abstract class BungeeTimeChecker {
	private boolean processing = false;
	private final Clock clock;

	@Getter
	private ScheduledExecutorService timer;

	private boolean timerLoaded = false;

	@Getter
	@Setter
	private boolean timeChangeFailSafeBypass = false;

	@Getter
	private int timeOffSet;

	@Getter
	private int timeWeekOffSet = 0;

	@Getter
	private String timeZone;

	public BungeeTimeChecker(String timeZone, int timeOffSet, int weekOffSet) {
		this(timeZone, timeOffSet, weekOffSet, Clock.systemDefaultZone());
	}

	public BungeeTimeChecker(String timeZone, int timeOffSet, int weekOffSet, Clock clock) {
		this.timeOffSet = timeOffSet;
		this.timeZone = timeZone;
		this.timeWeekOffSet = weekOffSet;
		this.clock = clock == null ? Clock.systemDefaultZone() : clock;
	}

	public abstract void debug(String text);

	public void forceChanged(TimeType time) {
		timer.execute(() -> forceChanged(time, true, true, true));
	}

	public void forceChanged(TimeType time, boolean fake, boolean preDate, boolean postDate) {
		processing = true;
		try {
			debug("Executing time change events: " + time);
			info("Time change event: " + time + ", Fake: " + fake);
			if (TimeType.DAY.equals(time) || TimeType.WEEK.equals(time) || TimeType.MONTH.equals(time)) {
				timeChanged(time, fake, false, false);
			}
			debug("Finished executing time change events: " + time);
		} catch (Exception e) {
			warning("Failed to process time change " + time + ": " + e.getMessage());
		} finally {
			processing = false;
		}
	}

	public abstract long getLastUpdated();

	public abstract int getPrevDay();

	public abstract String getPrevMonth();

	public abstract int getPrevWeek();

	public LocalDateTime getTime() {
		try {
			return TimeCalculation.currentTime(clock, getTimeZone(), getTimeOffSet());
		} catch (Exception e) {
			warning("Invalid time zone '" + getTimeZone() + "', using the local clock zone instead");
			return TimeCalculation.currentTime(clock, "", getTimeOffSet());
		}
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
		if (!timeChangeFailSafeBypass && getTime().getDayOfMonth() > 3) {
			warning("Detected a month change, but current day is not near end of a month, ignoring month change, "
					+ getTime().getDayOfMonth());
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
		int weekNumber = TimeCalculation.weekNumber(getTime(), timeWeekOffSet, Locale.getDefault());
		if (weekNumber == prevDate) {
			return false;
		}
		if (set) {
			setPrevWeek(weekNumber);
		}
		return true;
	}

	public abstract void info(String text);

	public abstract boolean isEnabled();

	public abstract boolean isIgnoreTime();

	public synchronized void loadTimer() {
		if (timerLoaded) {
			debug("Timer is already loaded");
			return;
		}
		timerLoaded = true;
		timer = Executors.newSingleThreadScheduledExecutor();
		if (getLastUpdated() > 0 && System.currentTimeMillis() - getLastUpdated() > TimeUnit.DAYS.toMillis(4)) {
			setIgnoreTime(true);
			warning("Skipping time change events, since server has been offline for awhile, use /votingpluginbungee forcetimechanged to force them if needed");
		}
		setLastUpdated();
		timer.scheduleWithFixedDelay(() -> {
			if (isEnabled()) {
				if (!processing) {
					update();
				}
			} else {
				timer.shutdown();
				timerLoaded = false;
			}
		}, 60, 5, TimeUnit.SECONDS);
		timer.scheduleAtFixedRate(this::setLastUpdated, 60, 60, TimeUnit.MINUTES);
	}

	public abstract void setIgnoreTime(boolean ignore);

	public abstract void setLastUpdated();

	public abstract void setPrevDay(int day);

	public abstract void setPrevMonth(String text);

	public abstract void setPrevWeek(int week);

	public abstract void timeChanged(TimeType type, boolean fake, boolean pre, boolean post);

	public void shutdown() {
		if (timer != null) {
			timer.shutdownNow();
		}
		timerLoaded = false;
	}

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

	public abstract void warning(String text);
}
