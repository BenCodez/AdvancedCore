package com.bencodez.advancedcore.tests.misc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.ConsoleCommandSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.misc.MiscUtils;
import com.bencodez.advancedcore.tests.BaseTest;
import com.bencodez.simpleapi.scheduler.BukkitScheduler;

public class MiscUtilsTest {

	private AdvancedCorePlugin plugin;
	private BukkitScheduler scheduler;
	private MiscUtils miscUtils;

	@BeforeEach
	public void setUp() throws Exception {
		plugin = BaseTest.getInstance().plugin;
		scheduler = mock(BukkitScheduler.class);
		when(plugin.getBukkitScheduler()).thenReturn(scheduler);
		miscUtils = MiscUtils.getInstance();

		Field pluginField = MiscUtils.class.getDeclaredField("plugin");
		pluginField.setAccessible(true);
		pluginField.set(miscUtils, plugin);
	}

	@Test
	public void addSecondsUsesProvidedDate() {
		Date base = new Date(1_000_000L);

		Date result = miscUtils.addSeconds(base, 30);

		assertEquals(1_030_000L, result.getTime());
	}

	@Test
	public void staggeredCommandsIncrementDelayForEachCommand() {
		ArrayList<String> commands = new ArrayList<>(List.of("first", "second", "third"));

		miscUtils.executeConsoleCommands(commands, new HashMap<>(), true);

		verify(scheduler).runTask(eq(plugin), any(Runnable.class));
		verify(scheduler).runTaskLater(eq(plugin), any(Runnable.class), eq(1L));
		verify(scheduler).runTaskLater(eq(plugin), any(Runnable.class), eq(2L));
	}

	@Test
	public void consoleCommandsStripLeadingSlashBeforeDispatch() {
		Server server = mock(Server.class);
		ConsoleCommandSender console = mock(ConsoleCommandSender.class);
		ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(Bukkit::getServer).thenReturn(server);
			bukkit.when(Bukkit::getConsoleSender).thenReturn(console);
			bukkit.when(() -> Bukkit.getOfflinePlayer("Ben")).thenReturn(null);

			miscUtils.executeConsoleCommands("Ben", "/say hi", new HashMap<>());

			verify(scheduler).executeOrScheduleSync(eq(plugin), task.capture());
			task.getValue().run();
			verify(server).dispatchCommand(console, "say hi");
		}
	}

	@Test
	public void asyncConsoleCommandCompletesOnlyAfterItsScheduledDispatch() {
		Server server = mock(Server.class);
		ConsoleCommandSender console = mock(ConsoleCommandSender.class);
		ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(Bukkit::getServer).thenReturn(server);
			bukkit.when(Bukkit::getConsoleSender).thenReturn(console);
			bukkit.when(() -> Bukkit.getOfflinePlayer("Ben")).thenReturn(null);

			java.util.concurrent.CompletionStage<Void> completion = miscUtils.executeConsoleCommandsAsync("Ben", "/say hi",
					new HashMap<>());
			verify(scheduler).executeOrScheduleSync(eq(plugin), task.capture());
			assertFalse(completion.toCompletableFuture().isDone());

			task.getValue().run();
			completion.toCompletableFuture().join();
			assertTrue(completion.toCompletableFuture().isDone());
			verify(server).dispatchCommand(console, "say hi");
		}
	}

	@Test
	public void asyncStaggeredCommandsCompleteOnlyAfterEveryScheduledDispatch() {
		Server server = mock(Server.class);
		ConsoleCommandSender console = mock(ConsoleCommandSender.class);
		ArgumentCaptor<Runnable> immediate = ArgumentCaptor.forClass(Runnable.class);
		ArgumentCaptor<Runnable> firstDelayed = ArgumentCaptor.forClass(Runnable.class);
		ArgumentCaptor<Runnable> secondDelayed = ArgumentCaptor.forClass(Runnable.class);

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(Bukkit::getServer).thenReturn(server);
			bukkit.when(Bukkit::getConsoleSender).thenReturn(console);
			bukkit.when(() -> Bukkit.getOfflinePlayer("Ben")).thenReturn(null);

			java.util.concurrent.CompletionStage<Void> completion = miscUtils.executeConsoleCommandsAsync("Ben",
					new ArrayList<>(List.of("say one", "say two", "say three")), new HashMap<>(), true);
			verify(scheduler).runTask(eq(plugin), immediate.capture());
			verify(scheduler, never()).runTaskLater(eq(plugin), any(Runnable.class), anyLong());
			assertFalse(completion.toCompletableFuture().isDone());

			immediate.getValue().run();
			assertFalse(completion.toCompletableFuture().isDone());
			verify(scheduler).runTaskLater(eq(plugin), firstDelayed.capture(), eq(1L));
			firstDelayed.getValue().run();
			assertFalse(completion.toCompletableFuture().isDone());
			verify(scheduler, times(2)).runTaskLater(eq(plugin), secondDelayed.capture(), eq(1L));
			secondDelayed.getAllValues().get(1).run();
			completion.toCompletableFuture().join();

			verify(server).dispatchCommand(console, "say one");
			verify(server).dispatchCommand(console, "say two");
			verify(server).dispatchCommand(console, "say three");
		}
	}
}
