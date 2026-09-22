package com.bencodez.advancedcore.api.time;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.data.ServerData;

class TimeCheckerRecurringTaskTest {
	@Test
	void transientFailureDoesNotPreventTheNextRecurringAttempt() {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		ServerData serverData = mock(ServerData.class);
		Logger logger = mock(Logger.class);
		ScheduledExecutorService timer = mock(ScheduledExecutorService.class);
		ScheduledFuture<?> scheduled = mock(ScheduledFuture.class);
		when(plugin.getLogger()).thenReturn(logger);
		when(plugin.getServerDataFile()).thenReturn(serverData);
		when(plugin.isEnabled()).thenReturn(true);
		when(serverData.getLastUpdated()).thenReturn(-1L);
		ArgumentCaptor<Runnable> recurring = ArgumentCaptor.forClass(Runnable.class);
		Mockito.doReturn(scheduled).when(timer)
				.scheduleWithFixedDelay(recurring.capture(), eq(60L), eq(5L), eq(TimeUnit.SECONDS));
		Mockito.doReturn(scheduled).when(timer)
				.scheduleAtFixedRate(any(Runnable.class), eq(60L), eq(60L), eq(TimeUnit.MINUTES));
		TimeChecker checker = spy(new TimeChecker(plugin));
		Mockito.doThrow(new IllegalStateException("disk unavailable")).doNothing().when(checker).update();

		try (MockedStatic<Executors> executors = Mockito.mockStatic(Executors.class)) {
			executors.when(Executors::newSingleThreadScheduledExecutor).thenReturn(timer);
			checker.loadTimer();
		}
		recurring.getValue().run();
		recurring.getValue().run();

		verify(checker, times(2)).update();
		verify(logger).warning("Failed to run time change check; automatic processing will retry: "
				+ "IllegalStateException: disk unavailable");
		verify(plugin).debug(org.mockito.ArgumentMatchers.any(IllegalStateException.class));
	}
}
