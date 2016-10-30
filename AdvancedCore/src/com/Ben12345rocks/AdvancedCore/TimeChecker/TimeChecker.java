package com.Ben12345rocks.AdvancedCore.TimeChecker;

import com.Ben12345rocks.AdvancedCore.Main;
import com.Ben12345rocks.AdvancedCore.Configs.Config;
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
	static Main plugin = Main.plugin;

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
	@SuppressWarnings("deprecation")
	public boolean hasDayChanged() {
		int prevDay = ServerData.getInstance().getPrevDay();

		java.util.TimeZone tz = java.util.TimeZone.getTimeZone(Config
				.getInstance().getTimeZone());
		java.util.Calendar c = java.util.Calendar.getInstance(tz);
		int day = c.getTime().getDate();
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
	@SuppressWarnings("deprecation")
	public boolean hasMonthChanged() {
		int prevMonth = ServerData.getInstance().getPrevMonth();
		java.util.TimeZone tz = java.util.TimeZone.getTimeZone(Config
				.getInstance().getTimeZone());
		java.util.Calendar c = java.util.Calendar.getInstance(tz);
		int month = c.getTime().getMonth();
		ServerData.getInstance().setPrevMonth(month);
		if (prevMonth == -1) {
			return false;
		}
		if (prevMonth != month) {
			return true;
		}
		return false;
	}

	/**
	 * Checks for week changed.
	 *
	 * @return true, if successful
	 */
	@SuppressWarnings("deprecation")
	public boolean hasWeekChanged() {
		int prevDate = ServerData.getInstance().getPrevWeekDay();
		java.util.TimeZone tz = java.util.TimeZone.getTimeZone(Config
				.getInstance().getTimeZone());
		java.util.Calendar c = java.util.Calendar.getInstance(tz);
		ServerData.getInstance().setPrevWeekDay(c.getTime().getDate());
		if (prevDate == -1) {
			return false;
		}
		if (ServerData.getInstance().getPrevDay() == 0
				&& c.getTime().getDate() != prevDate) {
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
			plugin.getServer().getPluginManager().callEvent(dayChange);
		}
		if (hasMonthChanged()) {
			plugin.debug("Month Changed");
			MonthChangeEvent dayChange = new MonthChangeEvent();
			plugin.getServer().getPluginManager().callEvent(dayChange);
		}
		if (hasWeekChanged()) {
			plugin.debug("Week Changed");
			WeekChangeEvent dayChange = new WeekChangeEvent();
			plugin.getServer().getPluginManager().callEvent(dayChange);
		}
	}
}