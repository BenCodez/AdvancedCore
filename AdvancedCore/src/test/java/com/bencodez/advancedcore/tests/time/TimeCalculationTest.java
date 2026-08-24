package com.bencodez.advancedcore.tests.time;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.api.time.TimeCalculation;
import com.bencodez.advancedcore.api.time.TimeType;
import com.bencodez.advancedcore.bungeeapi.time.BungeeTimeChecker;

public class TimeCalculationTest {

	@Test
	public void appliesTargetTimeZoneAndHourOffset() {
		Clock clock = Clock.fixed(Instant.parse("2024-03-10T09:30:00Z"), ZoneOffset.UTC);

		LocalDateTime result = TimeCalculation.currentTime(clock, "America/Los_Angeles", 2);

		assertEquals(LocalDateTime.of(2024, 3, 10, 3, 30), result);
	}

	@Test
	public void weekCalculationUsesConfiguredDayOffset() {
		LocalDateTime sunday = LocalDateTime.of(2024, 1, 7, 12, 0);

		int withoutOffset = TimeCalculation.weekNumber(sunday, 0, Locale.US);
		int withOffset = TimeCalculation.weekNumber(sunday, 1, Locale.US);

		assertEquals(2, withoutOffset);
		assertEquals(2, withOffset);
	}

	@Test
	public void proxyProcessingStateRecoversAfterTimeChangeFailure() {
		AtomicInteger calls = new AtomicInteger();
		BungeeTimeChecker checker = new BungeeTimeChecker("UTC", 0, 0) {
			@Override public void debug(String text) { }
			@Override public long getLastUpdated() { return 0; }
			@Override public int getPrevDay() { return 1; }
			@Override public String getPrevMonth() { return "JANUARY"; }
			@Override public int getPrevWeek() { return 1; }
			@Override public void info(String text) { }
			@Override public boolean isEnabled() { return true; }
			@Override public boolean isIgnoreTime() { return false; }
			@Override public void setIgnoreTime(boolean ignore) { }
			@Override public void setLastUpdated() { }
			@Override public void setPrevDay(int day) { }
			@Override public void setPrevMonth(String text) { }
			@Override public void setPrevWeek(int week) { }
			@Override public void warning(String text) { }
			@Override public boolean hasMonthChanged(boolean set) { return true; }
			@Override public boolean hasWeekChanged(boolean set) { return false; }
			@Override public boolean hasDayChanged(boolean set) { return false; }
			@Override public void timeChanged(TimeType type, boolean fake, boolean pre, boolean post) {
				calls.incrementAndGet();
				throw new IllegalStateException("test");
			}
		};

		checker.forceChanged(TimeType.MONTH, false, true, true);
		checker.update();

		assertEquals(2, calls.get(), "a failed change must not leave proxy processing permanently stuck");
	}
}
