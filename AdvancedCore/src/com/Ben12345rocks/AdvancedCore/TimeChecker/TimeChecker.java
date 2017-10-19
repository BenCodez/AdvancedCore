package com.Ben12345rocks.AdvancedCore.TimeChecker;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalField;
import java.time.temporal.WeekFields;
import java.util.Locale;

import com.Ben12345rocks.AdvancedCore.AdvancedCoreHook;
import com.Ben12345rocks.AdvancedCore.Data.ServerData;
import com.Ben12345rocks.AdvancedCore.Listeners.DateChangedEvent;
import com.Ben12345rocks.AdvancedCore.Listeners.DayChangeEvent;
import com.Ben12345rocks.AdvancedCore.Listeners.MonthChangeEvent;
import com.Ben12345rocks.AdvancedCore.Listeners.PreDateChangedEvent;
import com.Ben12345rocks.AdvancedCore.Listeners.WeekChangeEvent;

/**
 * The Class TimeChecker.
 */
public class TimeChecker {

	/** The instance. */
	static TimeChecker instance = new TimeChecker();

	/**
	 * Gets the single instance of TimeChecker.
	 *
	 * @return single instance of TimeChecker
	 */
	public static TimeChecker getInstance() {
		return instance;
	}

	/** The plugin. */
	AdvancedCoreHook plugin = AdvancedCoreHook.getInstance();

	/**
	 * Instantiates a new time checker.
	 */
	private TimeChecker() {
	}

	public void forceChanged(TimeType time) {
		plugin.debug("Executing time change events: " + time.toString());
		PreDateChangedEvent preDateChanged = new PreDateChangedEvent(time);
		plugin.getPlugin().getServer().getPluginManager().callEvent(preDateChanged);
		if (time.equals(TimeType.DAY)) {
			DayChangeEvent dayChange = new DayChangeEvent();
			plugin.getPlugin().getServer().getPluginManager().callEvent(dayChange);
		} else if (time.equals(TimeType.WEEK)) {
			WeekChangeEvent weekChange = new WeekChangeEvent();
			plugin.getPlugin().getServer().getPluginManager().callEvent(weekChange);
		} else if (time.equals(TimeType.MONTH)) {
			MonthChangeEvent monthChange = new MonthChangeEvent();
			plugin.getPlugin().getServer().getPluginManager().callEvent(monthChange);
		}

		DateChangedEvent dateChanged = new DateChangedEvent(time);
		plugin.getPlugin().getServer().getPluginManager().callEvent(dateChanged);

		plugin.debug("Finished executing time change events: " + time.toString());
	}

	/**
	 * Checks for day changed.
	 *
	 * @return true, if successful
	 */
	public boolean hasDayChanged() {
		int prevDay = ServerData.getInstance().getPrevDay();
		int day = LocalDateTime.now().getDayOfMonth();
		ServerData.getInstance().setPrevDay(day);
		if (prevDay == -1) {
			return false;
		}
		if (prevDay != day) {
			return true;
		}
		return false;
	}

	/**
	 * Checks for month changed.
	 *
	 * @return true, if successful
	 */
	public boolean hasMonthChanged() {
		String prevMonth = ServerData.getInstance().getPrevMonth();
		String month = LocalDateTime.now().getMonth().toString();
		ServerData.getInstance().setPrevMonth(month);
		return !prevMonth.equals(month);

	}

	/**
	 * Checks for week changed.
	 *
	 * @return true, if successful
	 */
	public boolean hasWeekChanged() {
		int prevDate = ServerData.getInstance().getPrevWeekDay();
		LocalDate date = LocalDate.now();
		TemporalField woy = WeekFields.of(Locale.getDefault()).weekOfWeekBasedYear();
		int weekNumber = date.get(woy);
		ServerData.getInstance().setPrevWeekDay(weekNumber);
		if (prevDate == -1) {
			return false;
		}
		if (weekNumber != prevDate) {
			return true;
		}
		return false;
	}

	/**
	 * Update.
	 */
	public void update() {

		boolean dayChanged = false;
		boolean weekChanged = false;
		boolean monthChanged = false;
		if (hasDayChanged()) {
			plugin.debug("Day changed");
			dayChanged = true;
		}
		if (hasWeekChanged()) {
			plugin.debug("Week Changed");
			weekChanged = true;
		}
		if (hasMonthChanged()) {
			plugin.debug("Month Changed");
			monthChanged = true;
		}

		if (dayChanged) {
			forceChanged(TimeType.DAY);
		}
		if (weekChanged) {
			forceChanged(TimeType.WEEK);
		}
		if (monthChanged) {
			forceChanged(TimeType.MONTH);
		}

	}
}