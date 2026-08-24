package com.bencodez.advancedcore.tests.item;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.item.FullInventoryHandler;
import com.bencodez.advancedcore.data.ServerData;
import com.bencodez.simpleapi.scheduler.BukkitScheduler;

public class FullInventoryHandlerTest {
	private final List<FullInventoryHandler> handlers = new ArrayList<>();

	@AfterEach
	public void shutdownHandlers() {
		for (FullInventoryHandler handler : handlers) {
			handler.shutdown();
		}
	}

	@Test
	public void addListMergesUnderSameUuid() {
		Fixture fixture = createFixture();
		UUID uuid = UUID.randomUUID();
		ItemStack first = mock(ItemStack.class);
		ItemStack second = mock(ItemStack.class);

		fixture.handler.add(uuid, new ArrayList<>(java.util.List.of(first)));
		fixture.handler.add(uuid, new ArrayList<>(java.util.List.of(second)));

		assertEquals(java.util.List.of(first, second), fixture.handler.getItems().get(uuid));
		assertEquals(1, fixture.handler.getItems().size());
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

			assertDoesNotThrow(() -> fixture.handler.check());
		}

		assertFalse(fixture.handler.getLastMessageTime().containsKey(uuid));
	}

	@Test
	public void pendingSweepSchedulesEachOnlinePlayerOnEntityScheduler() {
		Fixture fixture = createFixture();
		UUID uuid = UUID.randomUUID();
		Player player = mock(Player.class);
		fixture.handler.add(uuid, mock(ItemStack.class));

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
			bukkit.when(() -> Bukkit.getPlayer(uuid)).thenReturn(player);

			fixture.handler.check();
		}

		verify(fixture.bukkitScheduler).runTask(eq(fixture.plugin), any(Runnable.class), eq(player));
	}

	@Test
	public void playerCheckAlwaysUsesEntityScheduler() {
		Fixture fixture = createFixture();
		Player player = mock(Player.class);

		fixture.handler.check(player);

		verify(fixture.bukkitScheduler).runTask(eq(fixture.plugin), any(Runnable.class), eq(player));
	}

	@Test
	public void giveItemAlwaysUsesEntityScheduler() {
		Fixture fixture = createFixture();
		Player player = mock(Player.class);
		ItemStack item = mock(ItemStack.class);

		fixture.handler.giveItem(player, item);

		verify(fixture.bukkitScheduler).runTask(eq(fixture.plugin), any(Runnable.class), eq(player));
	}

	@Test
	public void handlerTimerIsIsolatedFromPluginInventoryTimer() {
		Fixture fixture = createFixture();
		ScheduledExecutorService handlerTimer = fixture.handler.getTimer();

		assertNotSame(fixture.sharedInventoryTimer, handlerTimer);
		assertFalse(handlerTimer.isShutdown());

		handlerTimer.shutdownNow();
		fixture.handler.loadTimer();

		assertNotSame(handlerTimer, fixture.handler.getTimer());
		assertFalse(fixture.handler.getTimer().isShutdown());
		verify(fixture.sharedInventoryTimer, never()).shutdown();
		verify(fixture.sharedInventoryTimer, never()).shutdownNow();
	}

	@Test
	public void shutdownStopsOnlyHandlerTimer() {
		Fixture fixture = createFixture();
		ScheduledExecutorService handlerTimer = fixture.handler.getTimer();

		fixture.handler.shutdown();

		assertTrue(handlerTimer.isShutdown());
		verify(fixture.sharedInventoryTimer, never()).shutdown();
		verify(fixture.sharedInventoryTimer, never()).shutdownNow();
	}

	@Test
	public void savePersistsCompletedSnapshotOnce() {
		Fixture fixture = createFixture();
		UUID uuid = UUID.randomUUID();
		ItemStack item = mock(ItemStack.class);
		fixture.data.set("FullInventory.previous.Time", 123L);
		fixture.handler.add(uuid, item);

		fixture.handler.save();

		verify(fixture.serverData).saveData();
		verify(fixture.serverData, never()).setData(eq("FullInventory"), any());
		ConfigurationSection root = fixture.data.getConfigurationSection("FullInventory");
		assertNotNull(root);
		assertFalse(root.contains("previous"));
		assertEquals(item, root.getItemStack(uuid + ".Items.0"));
		assertTrue(root.getLong(uuid + ".Time") > 0L);
	}

	private Fixture createFixture() {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		ScheduledExecutorService sharedInventoryTimer = mock(ScheduledExecutorService.class);
		BukkitScheduler bukkitScheduler = mock(BukkitScheduler.class);
		ServerData serverData = mock(ServerData.class);
		YamlConfiguration data = new YamlConfiguration();

		when(plugin.getInventoryTimer()).thenReturn(sharedInventoryTimer);
		when(plugin.getBukkitScheduler()).thenReturn(bukkitScheduler);
		when(plugin.getServerDataFile()).thenReturn(serverData);
		when(serverData.getData()).thenReturn(data);

		FullInventoryHandler handler = new FullInventoryHandler(plugin);
		handlers.add(handler);
		return new Fixture(plugin, sharedInventoryTimer, bukkitScheduler, serverData, data, handler);
	}

	private static final class Fixture {
		private final AdvancedCorePlugin plugin;
		private final ScheduledExecutorService sharedInventoryTimer;
		private final BukkitScheduler bukkitScheduler;
		private final ServerData serverData;
		private final YamlConfiguration data;
		private final FullInventoryHandler handler;

		private Fixture(AdvancedCorePlugin plugin, ScheduledExecutorService sharedInventoryTimer,
				BukkitScheduler bukkitScheduler, ServerData serverData, YamlConfiguration data,
				FullInventoryHandler handler) {
			this.plugin = plugin;
			this.sharedInventoryTimer = sharedInventoryTimer;
			this.bukkitScheduler = bukkitScheduler;
			this.serverData = serverData;
			this.data = data;
			this.handler = handler;
		}
	}
}
