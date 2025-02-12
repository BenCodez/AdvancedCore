
package com.bencodez.advancedcore.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.temporal.IsoFields;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Logger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.bencodez.advancedcore.AdvancedCoreConfigOptions;
import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.time.TimeChecker;
import com.bencodez.advancedcore.api.time.TimeType;
import com.bencodez.advancedcore.data.ServerData;

public class TimeCheckerTest {

	private AdvancedCorePlugin plugin;
	private AdvancedCoreConfigOptions options;

	@BeforeEach
	public void setUp() {
		plugin = mock(AdvancedCorePlugin.class);
		options = mock(AdvancedCoreConfigOptions.class);
		when(plugin.getOptions()).thenReturn(options);
		when(options.getTimeZone()).thenReturn("UTC");
		when(options.getTimeHourOffSet()).thenReturn(2);

		Logger logger = mock(Logger.class);
		when(plugin.getLogger()).thenReturn(logger);

		// Ensure that getInstance() returns the mocked plugin
		AdvancedCorePlugin.setInstance(plugin);
	}

	@Test
	public void testDayWeekMonthChanges() {
		ServerData serverDataFile = mock(ServerData.class);
		when(plugin.getServerDataFile()).thenReturn(serverDataFile);
		when(serverDataFile.getPrevDay()).thenReturn(31);
		when(serverDataFile.getPrevWeekDay()).thenReturn(52);
		when(serverDataFile.getPrevMonth()).thenReturn("DECEMBER");

		// Mock the set methods
		Mockito.doNothing().when(serverDataFile).setPrevDay(anyInt());
		Mockito.doNothing().when(serverDataFile).setPrevWeekDay(anyInt());
		Mockito.doNothing().when(serverDataFile).setPrevMonth(anyString());

		TimeChecker timeChecker = Mockito.spy(new TimeChecker(plugin));

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

				verify(serverDataFile).setPrevDay(day);
				int weekOfYear = mockedTime.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
				if (weekOfYear != mockedTime.minusDays(1).get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)) {
					assertTrue(weekChanged);
					verify(serverDataFile).setPrevWeekDay(weekOfYear);
				}
				if (day == 1) {
					verify(serverDataFile).setPrevMonth(mockedTime.getMonth().toString());
				}

				when(serverDataFile.getPrevDay()).thenReturn(day);
				when(serverDataFile.getPrevWeekDay()).thenReturn(weekOfYear);
				when(serverDataFile.getPrevMonth()).thenReturn(mockedTime.getMonth().toString());

				// Reset the mock's invocation count
				Mockito.reset(serverDataFile);
				when(serverDataFile.getPrevDay()).thenReturn(day);
				when(serverDataFile.getPrevWeekDay()).thenReturn(weekOfYear);
				when(serverDataFile.getPrevMonth()).thenReturn(mockedTime.getMonth().toString());
			}
		}
	}

	@Test
	public void forceChangedExecutesTimeChangeEvents() {
		ScheduledExecutorService timer = mock(ScheduledExecutorService.class);
		TimeChecker timeChecker = new TimeChecker(plugin);
		timeChecker.setTimer(timer);

		timeChecker.forceChanged(TimeType.DAY);

		verify(timer).execute(any(Runnable.class));
	}

	@Test
	public void hasDayChangedReturnsTrueWhenDayChanges() {
		ServerData serverDataFile = mock(ServerData.class);
		when(plugin.getServerDataFile()).thenReturn(serverDataFile);
		when(serverDataFile.getPrevDay()).thenReturn(1);
		TimeChecker timeChecker = Mockito.spy(new TimeChecker(plugin));
		LocalDateTime mockedTime = LocalDateTime.of(2023, 11, 5, 12, 0);
		Mockito.doReturn(mockedTime).when(timeChecker).getTime();

		boolean result = timeChecker.hasDayChanged(true);

		assertTrue(result);
		verify(serverDataFile).setPrevDay(anyInt());
	}

	@Test
	public void hasMonthChangedReturnsFalseWhenMonthDoesNotChange() {
		ServerData serverDataFile = mock(ServerData.class);
		when(plugin.getServerDataFile()).thenReturn(serverDataFile);
		when(serverDataFile.getPrevMonth()).thenReturn("JANUARY");
		TimeChecker timeChecker = Mockito.spy(new TimeChecker(plugin));
		LocalDateTime mockedTime = LocalDateTime.of(2023, 1, 1, 12, 0);
		Mockito.doReturn(mockedTime).when(timeChecker).getTime();

		boolean result = timeChecker.hasMonthChanged(true);

		assertFalse(result);
		verify(serverDataFile, never()).setPrevMonth(anyString());
	}

	@Test
	public void hasWeekChangedReturnsTrueWhenWeekChanges() {
		ServerData serverDataFile = mock(ServerData.class);
		when(plugin.getServerDataFile()).thenReturn(serverDataFile);
		when(serverDataFile.getPrevWeekDay()).thenReturn(1);
		TimeChecker timeChecker = Mockito.spy(new TimeChecker(plugin));
		LocalDateTime mockedTime = LocalDateTime.of(2023, 11, 1, 12, 0);
		Mockito.doReturn(mockedTime).when(timeChecker).getTime();

		boolean result = timeChecker.hasWeekChanged(true);

		assertTrue(result);
		verify(serverDataFile).setPrevWeekDay(anyInt());
	}

	@Test
	public void getTimeReturnsCorrectTimeWithTimeZone() {
		TimeChecker timeChecker = Mockito.spy(new TimeChecker(plugin));
		LocalDateTime mockedTime = LocalDateTime.of(2023, 11, 1, 12, 0);
		Mockito.doReturn(mockedTime).when(timeChecker).getTime();

		LocalDateTime result = timeChecker.getTime();

		assertNotNull(result);
		assertEquals(result.getHour(), mockedTime.getHour());
	}
}
