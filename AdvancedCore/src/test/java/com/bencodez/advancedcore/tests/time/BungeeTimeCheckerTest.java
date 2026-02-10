
package com.bencodez.advancedcore.tests.time;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.temporal.IsoFields;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.bencodez.advancedcore.api.time.TimeType;
import com.bencodez.advancedcore.bungeeapi.time.BungeeTimeChecker;

public class BungeeTimeCheckerTest {

	@Test
	public void dayWeekMonthChangesThroughYear() {
		BungeeTimeChecker timeChecker = Mockito.spy(new BungeeTimeChecker("UTC", 0, 0) {
			@Override
			public void debug(String text) {
			}

			@Override
			public long getLastUpdated() {
				return 0;
			}

			@Override
			public int getPrevDay() {
				return 31;
			}

			@Override
			public String getPrevMonth() {
				return "DECEMBER";
			}

			@Override
			public int getPrevWeek() {
				return 52;
			}

			@Override
			public void info(String text) {
			}

			@Override
			public boolean isEnabled() {
				return true;
			}

			@Override
			public boolean isIgnoreTime() {
				return false;
			}

			@Override
			public void setIgnoreTime(boolean ignore) {
			}

			@Override
			public void setLastUpdated() {
			}

			@Override
			public void setPrevDay(int day) {
			}

			@Override
			public void setPrevMonth(String text) {
			}

			@Override
			public void setPrevWeek(int week) {
			}

			@Override
			public void timeChanged(TimeType type, boolean fake, boolean pre, boolean post) {
			}

			@Override
			public void warning(String text) {
			}
		});

		// Simulate each day of the year
		for (int month = 1; month <= 12; month++) {
			int daysInMonth = LocalDateTime.of(2023, month, 1, 0, 0).getMonth().length(false);
			for (int day = 1; day <= daysInMonth; day++) {
				LocalDateTime mockedTime = LocalDateTime.of(2023, month, day, 12, 0);
				Mockito.doReturn(mockedTime).when(timeChecker).getTime();

				boolean dayChanged = timeChecker.hasDayChanged(true);
				boolean weekChanged = timeChecker.hasWeekChanged(true);
				boolean monthChanged = timeChecker.hasMonthChanged(true);

				assertTrue(dayChanged);

				if (day == 1) {
					assertTrue(monthChanged);
				} else {
					assertFalse(monthChanged);
				}

				verify(timeChecker).setPrevDay(day);
				int weekOfYear = mockedTime.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
				if (weekOfYear != mockedTime.minusDays(1).get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)) {
					assertTrue(weekChanged);
					verify(timeChecker).setPrevWeek(weekOfYear);
				}
				if (day == 1) {
					verify(timeChecker).setPrevMonth(mockedTime.getMonth().toString());
				}

				when(timeChecker.getPrevDay()).thenReturn(day);
				when(timeChecker.getPrevWeek()).thenReturn(weekOfYear);
				when(timeChecker.getPrevMonth()).thenReturn(mockedTime.getMonth().toString());

				// Reset the mock's invocation count
				Mockito.reset(timeChecker);
				when(timeChecker.getPrevDay()).thenReturn(day);
				when(timeChecker.getPrevWeek()).thenReturn(weekOfYear);
				when(timeChecker.getPrevMonth()).thenReturn(mockedTime.getMonth().toString());
			}
		}
	}

	@Test
	public void dayChangeEventTriggersCorrectly() {
		BungeeTimeChecker timeChecker = Mockito.spy(new BungeeTimeChecker("UTC", 0, 0) {
			@Override
			public void debug(String text) {
			}

			@Override
			public long getLastUpdated() {
				return 0;
			}

			@Override
			public int getPrevDay() {
				return 1;
			}

			@Override
			public String getPrevMonth() {
				return "DECEMBER";
			}

			@Override
			public int getPrevWeek() {
				return 52;
			}

			@Override
			public void info(String text) {
			}

			@Override
			public boolean isEnabled() {
				return true;
			}

			@Override
			public boolean isIgnoreTime() {
				return false;
			}

			@Override
			public void setIgnoreTime(boolean ignore) {
			}

			@Override
			public void setLastUpdated() {
			}

			@Override
			public void setPrevDay(int day) {
			}

			@Override
			public void setPrevMonth(String text) {
			}

			@Override
			public void setPrevWeek(int week) {
			}

			@Override
			public void timeChanged(TimeType type, boolean fake, boolean pre, boolean post) {
			}

			@Override
			public void warning(String text) {
			}
		});

		LocalDateTime mockedTime = LocalDateTime.of(2023, 11, 2, 12, 0);
		Mockito.doReturn(mockedTime).when(timeChecker).getTime();

		boolean result = timeChecker.hasDayChanged(true);

		assertTrue(result);
		verify(timeChecker).setPrevDay(2);
	}

	@Test
	public void weekChangeEventTriggersCorrectly() {
		BungeeTimeChecker timeChecker = Mockito.spy(new BungeeTimeChecker("UTC", 0, 0) {
			@Override
			public void debug(String text) {
			}

			@Override
			public long getLastUpdated() {
				return 0;
			}

			@Override
			public int getPrevDay() {
				return 1;
			}

			@Override
			public String getPrevMonth() {
				return "DECEMBER";
			}

			@Override
			public int getPrevWeek() {
				return 44;
			}

			@Override
			public void info(String text) {
			}

			@Override
			public boolean isEnabled() {
				return true;
			}

			@Override
			public boolean isIgnoreTime() {
				return false;
			}

			@Override
			public void setIgnoreTime(boolean ignore) {
			}

			@Override
			public void setLastUpdated() {
			}

			@Override
			public void setPrevDay(int day) {
			}

			@Override
			public void setPrevMonth(String text) {
			}

			@Override
			public void setPrevWeek(int week) {
			}

			@Override
			public void timeChanged(TimeType type, boolean fake, boolean pre, boolean post) {
			}

			@Override
			public void warning(String text) {
			}
		});

		LocalDateTime mockedTime = LocalDateTime.of(2023, 11, 6, 12, 0);
		Mockito.doReturn(mockedTime).when(timeChecker).getTime();

		boolean result = timeChecker.hasWeekChanged(true);

		assertTrue(result);
		verify(timeChecker).setPrevWeek(45);
	}

	@Test
	public void monthChangeEventTriggersCorrectly() {
		BungeeTimeChecker timeChecker = Mockito.spy(new BungeeTimeChecker("UTC", 0, 0) {
			@Override
			public void debug(String text) {
			}

			@Override
			public long getLastUpdated() {
				return 0;
			}

			@Override
			public int getPrevDay() {
				return 1;
			}

			@Override
			public String getPrevMonth() {
				return "OCTOBER";
			}

			@Override
			public int getPrevWeek() {
				return 44;
			}

			@Override
			public void info(String text) {
			}

			@Override
			public boolean isEnabled() {
				return true;
			}

			@Override
			public boolean isIgnoreTime() {
				return false;
			}

			@Override
			public void setIgnoreTime(boolean ignore) {
			}

			@Override
			public void setLastUpdated() {
			}

			@Override
			public void setPrevDay(int day) {
			}

			@Override
			public void setPrevMonth(String text) {
			}

			@Override
			public void setPrevWeek(int week) {
			}

			@Override
			public void timeChanged(TimeType type, boolean fake, boolean pre, boolean post) {
			}

			@Override
			public void warning(String text) {
			}
		});

		LocalDateTime mockedTime = LocalDateTime.of(2023, 11, 1, 12, 0);
		Mockito.doReturn(mockedTime).when(timeChecker).getTime();

		boolean result = timeChecker.hasMonthChanged(true);

		assertTrue(result);
		verify(timeChecker).setPrevMonth("NOVEMBER");
	}

	@Test
	public void noDayChangeEventWhenDayDoesNotChange() {
		BungeeTimeChecker timeChecker = Mockito.spy(new BungeeTimeChecker("UTC", 0, 0) {
			@Override
			public void debug(String text) {
			}

			@Override
			public long getLastUpdated() {
				return 0;
			}

			@Override
			public int getPrevDay() {
				return 2;
			}

			@Override
			public String getPrevMonth() {
				return "DECEMBER";
			}

			@Override
			public int getPrevWeek() {
				return 44;
			}

			@Override
			public void info(String text) {
			}

			@Override
			public boolean isEnabled() {
				return true;
			}

			@Override
			public boolean isIgnoreTime() {
				return false;
			}

			@Override
			public void setIgnoreTime(boolean ignore) {
			}

			@Override
			public void setLastUpdated() {
			}

			@Override
			public void setPrevDay(int day) {
			}

			@Override
			public void setPrevMonth(String text) {
			}

			@Override
			public void setPrevWeek(int week) {
			}

			@Override
			public void timeChanged(TimeType type, boolean fake, boolean pre, boolean post) {
			}

			@Override
			public void warning(String text) {
			}
		});

		LocalDateTime mockedTime = LocalDateTime.of(2023, 11, 2, 12, 0);
		Mockito.doReturn(mockedTime).when(timeChecker).getTime();

		boolean result = timeChecker.hasDayChanged(true);

		assertFalse(result);
		verify(timeChecker, never()).setPrevDay(anyInt());
	}

	@Test
	public void noWeekChangeEventWhenWeekDoesNotChange() {
		BungeeTimeChecker timeChecker = Mockito.spy(new BungeeTimeChecker("UTC", 0, 0) {
			@Override
			public void debug(String text) {
			}

			@Override
			public long getLastUpdated() {
				return 0;
			}

			@Override
			public int getPrevDay() {
				return 1;
			}

			@Override
			public String getPrevMonth() {
				return "DECEMBER";
			}

			@Override
			public int getPrevWeek() {
				return 45;
			}

			@Override
			public void info(String text) {
			}

			@Override
			public boolean isEnabled() {
				return true;
			}

			@Override
			public boolean isIgnoreTime() {
				return false;
			}

			@Override
			public void setIgnoreTime(boolean ignore) {
			}

			@Override
			public void setLastUpdated() {
			}

			@Override
			public void setPrevDay(int day) {
			}

			@Override
			public void setPrevMonth(String text) {
			}

			@Override
			public void setPrevWeek(int week) {
			}

			@Override
			public void timeChanged(TimeType type, boolean fake, boolean pre, boolean post) {
			}

			@Override
			public void warning(String text) {
			}
		});

		LocalDateTime mockedTime = LocalDateTime.of(2023, 11, 8, 12, 0);
		Mockito.doReturn(mockedTime).when(timeChecker).getTime();

		boolean result = timeChecker.hasWeekChanged(true);

		assertFalse(result);
		verify(timeChecker, never()).setPrevWeek(anyInt());
	}

	@Test
	public void noMonthChangeEventWhenMonthDoesNotChange() {
		BungeeTimeChecker timeChecker = Mockito.spy(new BungeeTimeChecker("UTC", 0, 0) {
			@Override
			public void debug(String text) {
			}

			@Override
			public long getLastUpdated() {
				return 0;
			}

			@Override
			public int getPrevDay() {
				return 1;
			}

			@Override
			public String getPrevMonth() {
				return "NOVEMBER";
			}

			@Override
			public int getPrevWeek() {
				return 44;
			}

			@Override
			public void info(String text) {
			}

			@Override
			public boolean isEnabled() {
				return true;
			}

			@Override
			public boolean isIgnoreTime() {
				return false;
			}

			@Override
			public void setIgnoreTime(boolean ignore) {
			}

			@Override
			public void setLastUpdated() {
			}

			@Override
			public void setPrevDay(int day) {
			}

			@Override
			public void setPrevMonth(String text) {
			}

			@Override
			public void setPrevWeek(int week) {
			}

			@Override
			public void timeChanged(TimeType type, boolean fake, boolean pre, boolean post) {
			}

			@Override
			public void warning(String text) {
			}
		});

		LocalDateTime mockedTime = LocalDateTime.of(2023, 11, 15, 12, 0);
		Mockito.doReturn(mockedTime).when(timeChecker).getTime();

		boolean result = timeChecker.hasMonthChanged(true);

		assertFalse(result);
		verify(timeChecker, never()).setPrevMonth(anyString());
	}
}
