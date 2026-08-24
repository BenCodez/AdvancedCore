package com.bencodez.advancedcore.tests.item;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.item.FullInventoryHandler;
import com.bencodez.advancedcore.data.ServerData;
import com.bencodez.simpleapi.scheduler.BukkitScheduler;

public class FullInventoryHandlerTest {

	@Test
	public void addListMergesUnderSameUuid() {
		Fixture fixture = createFixture();
		UUID uuid = UUID.randomUUID();
		ItemStack first = mock(ItemStack.class);
		ItemStack second = mock(ItemStack.class);

		fixture.handler.add(uuid, new ArrayList<>(java.util.List.of(first)));
		fixture.handler.add(uuid, new ArrayList<>(java.util.List.of(second)));

		assertEquals(java.util.List.of(first, second), fixture.handler.getItems().get(uuid));
		assertFalse(fixture.handler.getItems().containsKey(null));
	}

	@Test
	public void offlinePlayerDoesNotBreakPendingSweep() {
		Fixture fixture = createFixture();
		UUID uuid = UUID.randomUUID();
		fixture.handler.add(uuid, mock(ItemStack.class));
		fixture.handler.getLastMessageTime().put(uuid, 0L);

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
			bukkit.when(() -> Bukkit.getPlayer(uuid)).thenReturn(null);

			assertDoesNotThrow(fixture.handler::check);
		}

		assertFalse(fixture.handler.getLastMessageTime().containsKey(uuid));
	}

	@Test
	public void timerOnlySchedulesBukkitWork() {
		Fixture fixture = createFixture();
		ArgumentCaptor<Runnable> timerTask = ArgumentCaptor.forClass(Runnable.class);
		verify(fixture.timer).scheduleAtFixedRate(timerTask.capture(), eq(10L), eq(30L), eq(TimeUnit.SECONDS));

		timerTask.getValue().run();

		verify(fixture.bukkitScheduler).runTask(eq(fixture.plugin), any(Runnable.class));
	}

	@Test
	public void asyncPlayerCheckMovesToBukkitScheduler() {
		Fixture fixture = createFixture();
		Player player = mock(Player.class);

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
			fixture.handler.check(player);
		}

		verify(fixture.bukkitScheduler).runTask(eq(fixture.plugin), any(Runnable.class), eq(player));
	}

	@Test
	public void asyncGiveItemMovesToBukkitScheduler() {
		Fixture fixture = createFixture();
		Player player = mock(Player.class);
		ItemStack item = mock(ItemStack.class);

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
			fixture.handler.giveItem(player, item);
		}

		verify(fixture.bukkitScheduler).runTask(eq(fixture.plugin), any(Runnable.class), eq(player));
	}

	@Test
	public void shutdownCancelsOnlyHandlerTask() {
		Fixture fixture = createFixture();

		fixture.handler.shutdown();

		verify(fixture.future).cancel(false);
	}

	private Fixture createFixture() {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		ScheduledExecutorService timer = mock(ScheduledExecutorService.class);
		ScheduledFuture<?> future = mock(ScheduledFuture.class);
		BukkitScheduler bukkitScheduler = mock(BukkitScheduler.class);
		ServerData serverData = mock(ServerData.class);
		YamlConfiguration data = new YamlConfiguration();

		when(plugin.getInventoryTimer()).thenReturn(timer);
		when(plugin.getBukkitScheduler()).thenReturn(bukkitScheduler);
		when(plugin.getServerDataFile()).thenReturn(serverData);
		when(serverData.getData()).thenReturn(data);
		doReturn(future).when(timer).scheduleAtFixedRate(any(Runnable.class), eq(10L), eq(30L), eq(TimeUnit.SECONDS));

		FullInventoryHandler handler = new FullInventoryHandler(plugin);
		return new Fixture(plugin, timer, future, bukkitScheduler, handler);
	}

	private static final class Fixture {
		private final AdvancedCorePlugin plugin;
		private final ScheduledExecutorService timer;
		private final ScheduledFuture<?> future;
		private final BukkitScheduler bukkitScheduler;
		private final FullInventoryHandler handler;

		private Fixture(AdvancedCorePlugin plugin, ScheduledExecutorService timer, ScheduledFuture<?> future,
				BukkitScheduler bukkitScheduler, FullInventoryHandler handler) {
			this.plugin = plugin;
			this.timer = timer;
			this.future = future;
			this.bukkitScheduler = bukkitScheduler;
			this.handler = handler;
		}
	}
}
