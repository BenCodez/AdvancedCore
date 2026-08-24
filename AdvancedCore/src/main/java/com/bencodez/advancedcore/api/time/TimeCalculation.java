package com.bencodez.advancedcore.api.time;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.WeekFields;
import java.util.Locale;

/**
 * Shared deterministic time calculations used by Bukkit and proxy time checkers.
 */
public final class TimeCalculation {

	private TimeCalculation() {
	}

	public static LocalDateTime currentTime(Clock clock, String timeZone, int hourOffset) {
		Clock effectiveClock = clock == null ? Clock.systemDefaultZone() : clock;
		ZonedDateTime now = ZonedDateTime.now(effectiveClock);
		if (timeZone != null && !timeZone.isEmpty()) {
			now = now.withZoneSameInstant(ZoneId.of(timeZone));
		}
		return now.toLocalDateTime().plusHours(hourOffset);
	}

	public static int weekNumber(LocalDateTime time, int weekOffset, Locale locale) {
		Locale effectiveLocale = locale == null ? Locale.getDefault() : locale;
		return time.plusDays(weekOffset).get(WeekFields.of(effectiveLocale).weekOfWeekBasedYear());
	}
}
