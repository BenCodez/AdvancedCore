package com.Ben12345rocks.AdvancedCore.TimeChecker;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalField;
import java.time.temporal.WeekFields;
import java.util.Locale;

import com.Ben12345rocks.AdvancedCore.AdvancedCoreHook;
import com.Ben12345rocks.AdvancedCore.Data.ServerData;
import com.Ben12345rocks.AdvancedCore.Listeners.DayChangeEvent;
import com.Ben12345rocks.AdvancedCore.Listeners.MonthChangeEvent;
import com.Ben12345rocks.AdvancedCore.Listeners.WeekChangeEvent;

/**
 * The Class TimeChecker.
 */
public class TimeChecker {

	/** The instance. */
	static TimeChecker instance = new TimeChecker();

	/** The plugin. */
	AdvancedCoreHook plugin = AdvancedCoreHook.getInstance();

	/**
	 * Gets the single instance of TimeChecker.
	 *
	 * @return single instance of TimeChecker
	 */
	public static TimeChecker getInstance() {
		return instance;
	}

	/**
	 * Instantiates a new time checker.
	 */
	private TimeChecker() {
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
		if (hasDayChanged()) {
			plugin.debug("Day changed");
			DayChangeEvent dayChange = new DayChangeEvent();
			plugin.getPlugin().getServer().getPluginManager().callEvent(dayChange);
		}
		if (hasMonthChanged()) {
			plugin.debug("Month Changed");
			MonthChangeEvent dayChange = new MonthChangeEvent();
			plugin.getPlugin().getServer().getPluginManager().callEvent(dayChange);
		}
		if (hasWeekChanged()) {
			plugin.debug("Week Changed");
			WeekChangeEvent dayChange = new WeekChangeEvent();
			plugin.getPlugin().getServer().getPluginManager().callEvent(dayChange);
		}
	}
}