package com.bencodez.advancedcore.tests;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.temporal.IsoFields;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.time.TimeChecker;
import com.bencodez.advancedcore.data.ServerData;
import com.bencodez.advancedcore.AdvancedCoreConfigOptions;

public class TimeCheckerTest {

	private AdvancedCorePlugin plugin;
	private AdvancedCoreConfigOptions options;
	private ServerData serverDataFile;
	private MockedStatic<AdvancedCorePlugin> pluginStatic;
	private BaseTest baseTest;

	@BeforeEach
	public void setUp() {
		// 1) create mocks
		plugin = mock(AdvancedCorePlugin.class);
		options = mock(AdvancedCoreConfigOptions.class);
		serverDataFile = mock(ServerData.class);

		// 3) stub plugin getters
		when(plugin.getOptions()).thenReturn(options);
		when(plugin.getServerDataFile()).thenReturn(serverDataFile);

		// 4) default option values used in tests
		when(options.getTimeZone()).thenReturn("UTC");
		when(options.getTimeHourOffSet()).thenReturn(2);
		when(options.getTimeWeekOffSet()).thenReturn(0);

		baseTest = BaseTest.getInstance();
	}

	@AfterEach
	public void tearDown() {

	}

	@Test
	public void testDayWeekMonthChanges() {
		// initial “previous” values
		when(serverDataFile.getPrevDay()).thenReturn(31);
		when(serverDataFile.getPrevWeekDay()).thenReturn(52);
		when(serverDataFile.getPrevMonth()).thenReturn("DECEMBER");

		// stub the setters as no-ops
		Mockito.doNothing().when(serverDataFile).setPrevDay(anyInt());
		Mockito.doNothing().when(serverDataFile).setPrevWeekDay(anyInt());
		Mockito.doNothing().when(serverDataFile).setPrevMonth(anyString());

		// spy a fresh TimeChecker
		TimeChecker timeChecker = Mockito.spy(new TimeChecker(plugin));

		for (int month = 1; month <= 12; month++) {
			int daysInMonth = LocalDateTime.of(2023, month, 1, 0, 0).getMonth().length(false);

			for (int day = 1; day <= daysInMonth; day++) {
				LocalDateTime mockedTime = LocalDateTime.of(2023, month, day, 12, 0);
				Mockito.doReturn(mockedTime).when(timeChecker).getTime();

				boolean dayChanged = timeChecker.hasDayChanged(true);
				boolean weekChanged = timeChecker.hasWeekChanged(true);
				boolean monthChanged = timeChecker.hasMonthChanged(true);

				// day should always change
				assertTrue(dayChanged);

				// month only changes on the 1st
				if (day == 1) {
					assertTrue(monthChanged);
				} else {
					assertFalse(monthChanged);
				}

				// verify we recorded the new day
				verify(serverDataFile).setPrevDay(day);

				// compute this day's week and check if it rolled over
				int weekOfYear = mockedTime.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
				int prevWeekOfYear = mockedTime.minusDays(1).get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);

				if (weekOfYear != prevWeekOfYear) {
					assertTrue(weekChanged);
					verify(serverDataFile).setPrevWeekDay(weekOfYear);
				}

				// verify month setter on the 1st
				if (day == 1) {
					verify(serverDataFile).setPrevMonth(mockedTime.getMonth().toString());
				}

				// prepare for next iteration: update “previous” getters
				when(serverDataFile.getPrevDay()).thenReturn(day);
				when(serverDataFile.getPrevWeekDay()).thenReturn(weekOfYear);
				when(serverDataFile.getPrevMonth()).thenReturn(mockedTime.getMonth().toString());

				// reset invocation counts so verifies start fresh each loop
				reset(serverDataFile);
				when(serverDataFile.getPrevDay()).thenReturn(day);
				when(serverDataFile.getPrevWeekDay()).thenReturn(weekOfYear);
				when(serverDataFile.getPrevMonth()).thenReturn(mockedTime.getMonth().toString());
			}
		}
	}
}
