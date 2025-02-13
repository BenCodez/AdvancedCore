
package com.bencodez.advancedcore.tests;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.temporal.IsoFields;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.bencodez.advancedcore.api.time.TimeChecker;

public class TimeCheckerTest {

    private BaseTest baseTest;

    @BeforeEach
    public void setUp() {
        baseTest = BaseTest.getInstance();
        when(baseTest.options.getTimeZone()).thenReturn("UTC");
        when(baseTest.options.getTimeHourOffSet()).thenReturn(2);
    }

    @Test
    public void testDayWeekMonthChanges() {
        when(baseTest.serverDataFile.getPrevDay()).thenReturn(31);
        when(baseTest.serverDataFile.getPrevWeekDay()).thenReturn(52);
        when(baseTest.serverDataFile.getPrevMonth()).thenReturn("DECEMBER");

        // Mock the set methods
        Mockito.doNothing().when(baseTest.serverDataFile).setPrevDay(anyInt());
        Mockito.doNothing().when(baseTest.serverDataFile).setPrevWeekDay(anyInt());
        Mockito.doNothing().when(baseTest.serverDataFile).setPrevMonth(anyString());

        TimeChecker timeChecker = Mockito.spy(new TimeChecker(baseTest.plugin));

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

                verify(baseTest.serverDataFile).setPrevDay(day);
                int weekOfYear = mockedTime.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
                if (weekOfYear != mockedTime.minusDays(1).get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)) {
                    assertTrue(weekChanged);
                    verify(baseTest.serverDataFile).setPrevWeekDay(weekOfYear);
                }
                if (day == 1) {
                    verify(baseTest.serverDataFile).setPrevMonth(mockedTime.getMonth().toString());
                }

                when(baseTest.serverDataFile.getPrevDay()).thenReturn(day);
                when(baseTest.serverDataFile.getPrevWeekDay()).thenReturn(weekOfYear);
                when(baseTest.serverDataFile.getPrevMonth()).thenReturn(mockedTime.getMonth().toString());

                // Reset the mock's invocation count
                Mockito.reset(baseTest.serverDataFile);
                when(baseTest.serverDataFile.getPrevDay()).thenReturn(day);
                when(baseTest.serverDataFile.getPrevWeekDay()).thenReturn(weekOfYear);
                when(baseTest.serverDataFile.getPrevMonth()).thenReturn(mockedTime.getMonth().toString());
            }
        }
    }
}
