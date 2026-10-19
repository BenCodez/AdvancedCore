package com.bencodez.advancedcore.tests.item;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.bencodez.advancedcore.AdvancedCoreConfigOptions;
import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.item.FullInventoryHandler;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
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
	public void giveItemAsyncCompletesOnlyAfterOwnedInventoryDelivery() {
		Fixture fixture = createFixture();
		UUID uuid = UUID.randomUUID();
		Player player = mock(Player.class);
		PlayerInventory inventory = mock(PlayerInventory.class);
		ItemStack item = mock(ItemStack.class);
		when(player.getUniqueId()).thenReturn(uuid);
		when(player.isOnline()).thenReturn(true);
		when(player.getInventory()).thenReturn(inventory);
		when(inventory.addItem(item)).thenReturn(new HashMap<>());

		CompletionStage<Void> delivery = fixture.handler.giveItemAsync(player, item);
		assertFalse(delivery.toCompletableFuture().isDone());
		ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
		verify(fixture.bukkitScheduler).runTask(eq(fixture.plugin), task.capture(), eq(player));

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getPlayer(uuid)).thenReturn(player);
			task.getValue().run();
		}

		assertTrue(delivery.toCompletableFuture().isDone());
		verify(inventory).addItem(item);
	}

	@Test
	public void eligibilityLossBeforeDeliveryDoesNotMutateInventoryOrOverflow() throws Exception {
		Fixture fixture = createFixture();
		UUID uuid = UUID.randomUUID();
		Player player = mock(Player.class);
		PlayerInventory inventory = mock(PlayerInventory.class);
		World world = mock(World.class);
		ItemStack item = mock(ItemStack.class);
		when(player.getUniqueId()).thenReturn(uuid);
		when(player.isOnline()).thenReturn(true);
		when(player.getInventory()).thenReturn(inventory);
		when(player.getWorld()).thenReturn(world);
		when(fixture.plugin.getOptions().isDropOnFullInv()).thenReturn(true);
		AtomicBoolean eligible = new AtomicBoolean(true);
		CompletionStage<Void> delivery = fixture.handler.giveItemWhenEligibleAsync(player, eligible::get, item);
		ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
		verify(fixture.bukkitScheduler).runTask(eq(fixture.plugin), task.capture(), eq(player));
		eligible.set(false);
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getPlayer(uuid)).thenReturn(player);
			task.getValue().run();
		}

		java.util.concurrent.ExecutionException failure = assertThrows(java.util.concurrent.ExecutionException.class,
				() -> delivery.toCompletableFuture().get(2, TimeUnit.SECONDS));
		assertTrue(AdvancedCoreUser.isReplayActionNotStarted(failure));
		assertEquals(Reward.playerEligibilityDeferred().getClass(), failure.getCause().getCause().getClass());
		verify(inventory, never()).addItem(any(ItemStack[].class));
		verify(world, never()).dropItem(any(Location.class), any(ItemStack.class));
		verify(player, never()).updateInventory();
		assertTrue(fixture.handler.getItems().isEmpty());
	}

	@Test
	public void oldAsyncItemVarargsStillAcceptNullFirstItem() throws Exception {
		Fixture fixture = createFixture();
		UUID uuid = UUID.randomUUID();
		Player player = mock(Player.class);
		PlayerInventory inventory = mock(PlayerInventory.class);
		ItemStack item = mock(ItemStack.class);
		when(player.getUniqueId()).thenReturn(uuid);
		when(player.isOnline()).thenReturn(true);
		when(player.getInventory()).thenReturn(inventory);
		when(inventory.addItem(null, item)).thenReturn(new HashMap<>());

		CompletionStage<Void> delivery = fixture.handler.giveItemAsync(player, null, item);
		ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
		verify(fixture.bukkitScheduler).runTask(eq(fixture.plugin), task.capture(), eq(player));
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getPlayer(uuid)).thenReturn(player);
			task.getValue().run();
		}
		delivery.toCompletableFuture().get(2, TimeUnit.SECONDS);
		verify(inventory).addItem(null, item);
	}

	@Test
	public void committedOverflowRemainsDeliverableAfterEligibilityChanges() throws Exception {
		Fixture fixture = createFixture();
		UUID uuid = UUID.randomUUID();
		Player player = mock(Player.class);
		PlayerInventory inventory = mock(PlayerInventory.class);
		ItemStack item = mock(ItemStack.class);
		ItemStack excess = mock(ItemStack.class);
		when(player.getUniqueId()).thenReturn(uuid);
		when(player.isOnline()).thenReturn(true);
		when(player.getInventory()).thenReturn(inventory);
		when(inventory.addItem(item)).thenReturn(new HashMap<>(java.util.Map.of(0, excess)));
		when(inventory.addItem(excess)).thenReturn(new HashMap<>());
		fixture.handler.getLastMessageTime().put(uuid, System.currentTimeMillis());
		AtomicBoolean eligible = new AtomicBoolean(true);
		AtomicInteger eligibilityChecks = new AtomicInteger();
		CompletionStage<Void> delivery = fixture.handler.giveItemWhenEligibleAsync(player,
				() -> { eligibilityChecks.incrementAndGet(); return eligible.get(); }, item);
		ArgumentCaptor<Runnable> tasks = ArgumentCaptor.forClass(Runnable.class);
		verify(fixture.bukkitScheduler).runTask(eq(fixture.plugin), tasks.capture(), eq(player));
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getPlayer(uuid)).thenReturn(player);
			tasks.getValue().run();
			delivery.toCompletableFuture().get(2, TimeUnit.SECONDS);
			assertEquals(List.of(excess), fixture.handler.getItems().get(uuid));
			eligible.set(false);
			fixture.handler.check(player);
			verify(fixture.bukkitScheduler, times(2)).runTask(eq(fixture.plugin), tasks.capture(), eq(player));
			tasks.getValue().run();
		}
		assertEquals(1, eligibilityChecks.get());
		verify(inventory, times(1)).addItem(item);
		verify(inventory, times(1)).addItem(excess);
		assertTrue(fixture.handler.getItems().isEmpty());
	}

	@Test
	public void noOverflowCompletionPrecedesShutdownSnapshot() throws Exception {
		Fixture fixture = createFixture();
		UUID uuid = UUID.randomUUID();
		Player player = mock(Player.class);
		PlayerInventory inventory = mock(PlayerInventory.class);
		ItemStack item = mock(ItemStack.class);
		when(player.getUniqueId()).thenReturn(uuid);
		when(player.isOnline()).thenReturn(true);
		when(player.getInventory()).thenReturn(inventory);
		when(inventory.addItem(item)).thenReturn(new HashMap<>());
		CountDownLatch saveStarted = new CountDownLatch(1);
		doAnswer(invocation -> {
			saveStarted.countDown();
			return null;
		}).when(fixture.serverData).saveData();

		CompletionStage<Void> delivery = fixture.handler.giveItemAsync(player, item);
		AtomicReference<Thread> shutdownThread = new AtomicReference<>();
		CompletionStage<Void> completionObserved = delivery.thenRun(() -> {
			Thread shutdown = new Thread(fixture.handler::shutdown, "full-inventory-shutdown-race");
			shutdownThread.set(shutdown);
			shutdown.start();
			try {
				assertFalse(saveStarted.await(100, TimeUnit.MILLISECONDS),
						"shutdown snapshot overtook the successful delivery completion");
			} catch (InterruptedException interrupted) {
				Thread.currentThread().interrupt();
				throw new AssertionError(interrupted);
			}
		});
		ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
		verify(fixture.bukkitScheduler).runTask(eq(fixture.plugin), task.capture(), eq(player));
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getPlayer(uuid)).thenReturn(player);
			task.getValue().run();
		}

		completionObserved.toCompletableFuture().get(2, TimeUnit.SECONDS);
		Thread shutdown = shutdownThread.get();
		assertNotNull(shutdown);
		shutdown.join(TimeUnit.SECONDS.toMillis(2));
		assertFalse(shutdown.isAlive());
		assertEquals(0, saveStarted.getCount());
		verify(inventory).addItem(item);
	}

	@Test
	public void giveItemAsyncWaitsForOverflowToBePersisted() throws Exception {
		Fixture fixture = createFixture();
		UUID uuid = UUID.randomUUID();
		Player player = mock(Player.class);
		PlayerInventory inventory = mock(PlayerInventory.class);
		ItemStack item = mock(ItemStack.class);
		ItemStack excess = mock(ItemStack.class);
		when(player.getUniqueId()).thenReturn(uuid);
		when(player.isOnline()).thenReturn(true);
		when(player.getInventory()).thenReturn(inventory);
		when(inventory.addItem(item)).thenReturn(new HashMap<>(java.util.Map.of(0, excess)));
		fixture.handler.getLastMessageTime().put(uuid, System.currentTimeMillis());
		CountDownLatch saveStarted = new CountDownLatch(1);
		CountDownLatch releaseSave = new CountDownLatch(1);
		doAnswer(invocation -> {
			saveStarted.countDown();
			assertTrue(releaseSave.await(2, TimeUnit.SECONDS));
			return null;
		}).when(fixture.serverData).saveData();

		CompletionStage<Void> delivery = fixture.handler.giveItemAsync(player, item);
		ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
		verify(fixture.bukkitScheduler).runTask(eq(fixture.plugin), task.capture(), eq(player));
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getPlayer(uuid)).thenReturn(player);
			task.getValue().run();
		}

		assertTrue(saveStarted.await(2, TimeUnit.SECONDS));
		assertFalse(delivery.toCompletableFuture().isDone());
		releaseSave.countDown();
		delivery.toCompletableFuture().get(2, TimeUnit.SECONDS);
		assertEquals(List.of(excess), fixture.handler.getItems().get(uuid));
	}

	@Test
	public void replayCompletionIsRegisteredBeforeOverflowReservationCanRaceShutdown() throws Exception {
		Fixture fixture = createFixture();
		UUID uuid = UUID.randomUUID();
		Player player = mock(Player.class);
		PlayerInventory inventory = mock(PlayerInventory.class);
		ItemStack item = mock(ItemStack.class);
		ItemStack excess = mock(ItemStack.class);
		when(player.getUniqueId()).thenReturn(uuid);
		when(player.isOnline()).thenReturn(true);
		when(player.getInventory()).thenReturn(inventory);
		fixture.handler.getLastMessageTime().put(uuid, System.currentTimeMillis());
		doAnswer(invocation -> {
			java.lang.reflect.Field completionsField = FullInventoryHandler.class
					.getDeclaredField("replayOverflowCompletions");
			completionsField.setAccessible(true);
			@SuppressWarnings("unchecked")
			ConcurrentHashMap<String, java.util.concurrent.CompletableFuture<Void>> completions =
					(ConcurrentHashMap<String, java.util.concurrent.CompletableFuture<Void>>) completionsField
							.get(fixture.handler);
			assertEquals(1, completions.size(),
					"shutdown must be able to find the completion before it can snapshot a reservation");
			return new HashMap<>(java.util.Map.of(0, excess));
		}).when(inventory).addItem(item);

		CompletionStage<Void> delivery = fixture.handler.giveItemAsync(player, item);
		ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
		verify(fixture.bukkitScheduler).runTask(eq(fixture.plugin), task.capture(), eq(player));
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getPlayer(uuid)).thenReturn(player);
			task.getValue().run();
		}

		delivery.toCompletableFuture().get(2, TimeUnit.SECONDS);
	}

	@Test
	public void replayDropFailurePersistsOnlyTheUndroppedOverflow() throws Exception {
		Fixture fixture = createFixture();
		UUID uuid = UUID.randomUUID();
		Player player = mock(Player.class);
		PlayerInventory inventory = mock(PlayerInventory.class);
		ItemStack item = mock(ItemStack.class);
		ItemStack excess = mock(ItemStack.class);
		World world = mock(World.class);
		Location location = mock(Location.class);
		when(fixture.plugin.getOptions().isDropOnFullInv()).thenReturn(true);
		when(player.getUniqueId()).thenReturn(uuid);
		when(player.isOnline()).thenReturn(true);
		when(player.getInventory()).thenReturn(inventory);
		when(player.getWorld()).thenReturn(world);
		when(player.getLocation()).thenReturn(location);
		when(inventory.addItem(item)).thenReturn(new HashMap<>(java.util.Map.of(0, excess)));
		doAnswer(invocation -> {
			throw new IllegalStateException("world unavailable");
		}).when(world).dropItem(location, excess);
		fixture.handler.getLastMessageTime().put(uuid, System.currentTimeMillis());
		CountDownLatch saveStarted = new CountDownLatch(1);
		CountDownLatch releaseSave = new CountDownLatch(1);
		doAnswer(invocation -> {
			saveStarted.countDown();
			assertTrue(releaseSave.await(2, TimeUnit.SECONDS));
			return null;
		}).when(fixture.serverData).saveData();

		CompletionStage<Void> delivery = fixture.handler.giveItemAsync(player, item);
		ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
		verify(fixture.bukkitScheduler).runTask(eq(fixture.plugin), task.capture(), eq(player));
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getPlayer(uuid)).thenReturn(player);
			task.getValue().run();
		}

		assertTrue(saveStarted.await(2, TimeUnit.SECONDS));
		assertFalse(delivery.toCompletableFuture().isDone());
		releaseSave.countDown();
		delivery.toCompletableFuture().get(2, TimeUnit.SECONDS);
		verify(inventory, times(1)).addItem(item);
		verify(world).dropItem(location, excess);
		assertEquals(List.of(excess), fixture.handler.getItems().get(uuid));
		assertEquals(excess, fixture.data.getItemStack("FullInventory." + uuid + ".Items.0"));
	}

	@Test
	public void replayOverflowRejectedBeforePublicationDropsOnlyReservedOverflowWithoutReplayingPartialInsert() throws Exception {
		Fixture fixture = createFixture();
		UUID uuid = UUID.randomUUID();
		Player player = mock(Player.class);
		PlayerInventory inventory = mock(PlayerInventory.class);
		ItemStack item = mock(ItemStack.class);
		ItemStack excess = mock(ItemStack.class);
		World world = mock(World.class);
		Location location = mock(Location.class);
		when(fixture.plugin.getOptions().isDropOnFullInv()).thenReturn(false);
		when(player.getUniqueId()).thenReturn(uuid);
		when(player.isOnline()).thenReturn(true);
		when(player.getInventory()).thenReturn(inventory);
		when(player.getWorld()).thenReturn(world);
		when(player.getLocation()).thenReturn(location);
		when(inventory.addItem(item)).thenReturn(new HashMap<>(java.util.Map.of(0, excess)));
		when(world.dropItem(location, excess)).thenReturn(mock(org.bukkit.entity.Item.class));
		fixture.handler.getLastMessageTime().put(uuid, System.currentTimeMillis());
		fixture.handler.getTimer().shutdownNow(); // Rejected before any owner publication.

		AtomicInteger scheduled = new AtomicInteger();
		AtomicReference<Runnable> initialDelivery = new AtomicReference<>();
		AtomicReference<Runnable> fallback = new AtomicReference<>();
		CountDownLatch fallbackScheduled = new CountDownLatch(1);
		doAnswer(invocation -> {
			Runnable task = invocation.getArgument(1);
			if (scheduled.incrementAndGet() == 1) initialDelivery.set(task);
			else {
				fallback.set(task);
				fallbackScheduled.countDown();
			}
			return null;
		}).when(fixture.bukkitScheduler).runTask(eq(fixture.plugin), any(Runnable.class), eq(player));

		CompletionStage<Void> delivery = fixture.handler.giveItemAsync(player, item);
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getPlayer(uuid)).thenReturn(player);
			bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
			initialDelivery.get().run();
			assertTrue(fallbackScheduled.await(2, TimeUnit.SECONDS));
			assertFalse(delivery.toCompletableFuture().isDone());

			// The overflow remains reserved while its persistence is failing, so an
			// ordinary sweep cannot consume it before the fallback owns it.
			fixture.handler.check();
			assertEquals(2, scheduled.get());
			fallback.get().run();
		}

		delivery.toCompletableFuture().get(2, TimeUnit.SECONDS);
		verify(inventory, times(1)).addItem(item);
		verify(world).dropItem(location, excess);
		assertFalse(fixture.handler.getItems().containsKey(uuid));
		assertFalse(fixture.data.contains("FullInventory"));
	}

	@Test
	public void replayOverflowDoesNotAcknowledgeAnUnavailableFallback() throws Exception {
		Fixture fixture = createFixture();
		UUID uuid = UUID.randomUUID();
		Player player = mock(Player.class);
		PlayerInventory inventory = mock(PlayerInventory.class);
		ItemStack item = mock(ItemStack.class);
		ItemStack excess = mock(ItemStack.class);
		when(fixture.plugin.getOptions().isDropOnFullInv()).thenReturn(false);
		when(player.getUniqueId()).thenReturn(uuid);
		when(player.isOnline()).thenReturn(true);
		when(player.getInventory()).thenReturn(inventory);
		when(inventory.addItem(item)).thenReturn(new HashMap<>(java.util.Map.of(0, excess)));
		fixture.handler.getLastMessageTime().put(uuid, System.currentTimeMillis());
		fixture.handler.getTimer().shutdownNow(); // Rejected before any owner publication.

		AtomicReference<Runnable> initialDelivery = new AtomicReference<>();
		AtomicReference<Runnable> fallback = new AtomicReference<>();
		CountDownLatch fallbackScheduled = new CountDownLatch(1);
		doAnswer(invocation -> {
			Runnable task = invocation.getArgument(1);
			if (initialDelivery.get() == null) initialDelivery.set(task);
			else {
				fallback.set(task);
				fallbackScheduled.countDown();
			}
			return null;
		}).when(fixture.bukkitScheduler).runTask(eq(fixture.plugin), any(Runnable.class), eq(player));

		CompletionStage<Void> delivery = fixture.handler.giveItemAsync(player, item);
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getPlayer(uuid)).thenReturn(player);
			initialDelivery.get().run();
			assertTrue(fallbackScheduled.await(2, TimeUnit.SECONDS));
			bukkit.when(() -> Bukkit.getPlayer(uuid)).thenReturn(null);
			fallback.get().run();
		}

		assertFalse(delivery.toCompletableFuture().isDone());
		verify(inventory, times(1)).addItem(item);
		assertFalse(fixture.handler.getItems().containsKey(uuid));
		assertFalse(fixture.data.contains("FullInventory"));
	}

	@Test
	public void asyncShutdownReturnsWhileCheckedSnapshotIsBlockedOnOwnedDaemonWorker() throws Exception {
		Fixture fixture = createFixture();
		assertTrue(fixture.handler.getTimer().submit(() -> Thread.currentThread().isDaemon()).get(2, TimeUnit.SECONDS));
		UUID uuid = UUID.randomUUID();
		ItemStack item = mock(ItemStack.class);
		fixture.handler.add(uuid, item);
		CountDownLatch saving = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		AtomicReference<Thread> workerThread = new AtomicReference<>();
		doAnswer(call -> {
			workerThread.set(Thread.currentThread());
			saving.countDown();
			assertTrue(release.await(2, TimeUnit.SECONDS));
			return null;
		}).when(fixture.serverData).saveDataChecked();
		var worker = java.util.concurrent.Executors.newSingleThreadExecutor(
				com.bencodez.advancedcore.core.runtime.AdvancedCoreRuntime.daemonThreads("inventory-save-fixture"));
		try {
			CompletionStage<Void> completion = fixture.handler.shutdownAsync(worker);
			assertTrue(saving.await(2, TimeUnit.SECONDS));
			assertFalse(completion.toCompletableFuture().isDone());
			assertTrue(workerThread.get().isDaemon());
			assertNotSame(Thread.currentThread(), workerThread.get());
			assertEquals(completion, fixture.handler.shutdownAsync(worker));
			release.countDown();
			completion.toCompletableFuture().get(2, TimeUnit.SECONDS);
			assertEquals(item, fixture.data.getItemStack("FullInventory." + uuid + ".Items.0"));
			verify(fixture.serverData, times(1)).saveDataChecked();
		} finally {
			release.countDown();
			worker.shutdownNow();
		}
	}

	@Test
	public void failedCheckedShutdownSaveDoesNotAcknowledgeAcceptedReplayOverflow() throws Exception {
		Fixture fixture = createFixture();
		UUID uuid = UUID.randomUUID();
		Player player = mock(Player.class);
		PlayerInventory inventory = mock(PlayerInventory.class);
		ItemStack item = mock(ItemStack.class);
		ItemStack excess = mock(ItemStack.class);
		when(fixture.plugin.getOptions().isDropOnFullInv()).thenReturn(false);
		when(player.getUniqueId()).thenReturn(uuid);
		when(player.isOnline()).thenReturn(true);
		when(player.getInventory()).thenReturn(inventory);
		when(inventory.addItem(item)).thenReturn(new HashMap<>(java.util.Map.of(0, excess)));
		fixture.handler.getLastMessageTime().put(uuid, System.currentTimeMillis());
		CountDownLatch started = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		fixture.handler.getTimer().execute(() -> {
			started.countDown();
			try { release.await(2, TimeUnit.SECONDS); }
			catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
		});
		assertTrue(started.await(2, TimeUnit.SECONDS));
		try {
			CompletionStage<Void> delivery = fixture.handler.giveItemAsync(player, item);
			ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
			verify(fixture.bukkitScheduler).runTask(eq(fixture.plugin), task.capture(), eq(player));
			try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
				bukkit.when(() -> Bukkit.getPlayer(uuid)).thenReturn(player);
				task.getValue().run();
			}
			doAnswer(call -> { throw new java.io.UncheckedIOException(new java.io.IOException("disk failure")); })
					.when(fixture.serverData).saveDataChecked();
			CompletionStage<Void> completion = fixture.handler.shutdownAsync(Runnable::run);
			assertThrows(java.util.concurrent.CompletionException.class, () -> completion.toCompletableFuture().join());
			assertFalse(delivery.toCompletableFuture().isDone(), "failed disk save must keep the replay receipt pending");
			assertFalse(fixture.handler.getItems().containsKey(uuid), "failed reservation must not enter the delivery sweep");
			assertFalse(fixture.data.contains("FullInventory." + uuid), "unacknowledged overflow must stay out of later whole-file snapshots");
			verify(inventory, times(1)).addItem(item);
		} finally { release.countDown(); }
	}

	@Test
	public void pendingFinalPublicationFencesPreviouslyQueuedOverflowDropFallback() throws Exception {
		Fixture fixture = createFixture();
		UUID uuid = UUID.randomUUID();
		Player player = mock(Player.class); PlayerInventory inventory = mock(PlayerInventory.class);
		World world = mock(World.class); ItemStack item = mock(ItemStack.class), excess = mock(ItemStack.class);
		when(fixture.plugin.getOptions().isDropOnFullInv()).thenReturn(false);
		when(player.getUniqueId()).thenReturn(uuid); when(player.isOnline()).thenReturn(true);
		when(player.getInventory()).thenReturn(inventory); when(player.getWorld()).thenReturn(world);
		when(inventory.addItem(item)).thenReturn(new HashMap<>(java.util.Map.of(0, excess)));
		fixture.handler.getLastMessageTime().put(uuid, System.currentTimeMillis());
		AtomicReference<Runnable> first = new AtomicReference<>(), fallback = new AtomicReference<>();
		CountDownLatch fallbackScheduled = new CountDownLatch(1);
		doAnswer(call -> {
			Runnable task = call.getArgument(1);
			if (!first.compareAndSet(null, task)) { fallback.set(task); fallbackScheduled.countDown(); }
			return null;
		}).when(fixture.bukkitScheduler).runTask(eq(fixture.plugin), any(Runnable.class), eq(player));
		fixture.handler.getTimer().shutdownNow(); // Queue the fallback before any owner save.
		CompletionStage<Void> delivery = fixture.handler.giveItemAsync(player, item);
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getPlayer(uuid)).thenReturn(player);
			first.get().run();
			assertTrue(fallbackScheduled.await(2, TimeUnit.SECONDS));
			var publication = new java.util.concurrent.CompletableFuture<Void>();
			doAnswer(call -> {
				var candidate = new YamlConfiguration();
				call.getArgument(0, java.util.function.Consumer.class).accept(candidate);
				return publication.thenRun(() -> {
					fixture.data.set("FullInventory", null);
					for (String path : candidate.getKeys(true)) {
						if (!candidate.isConfigurationSection(path)) fixture.data.set(path, candidate.get(path));
					}
				});
			}).when(fixture.serverData).mutateFinalData(any());
			var finalFiles = fixture.handler.shutdownAsync(Runnable::run).toCompletableFuture();
			assertFalse(finalFiles.isDone()); fallback.get().run();
			verify(world, never()).dropItem(any(Location.class), any(ItemStack.class));
			assertFalse(delivery.toCompletableFuture().isDone());
			assertFalse(fixture.data.contains("FullInventory." + uuid));
			publication.complete(null); finalFiles.get(2, TimeUnit.SECONDS);
			assertEquals(excess, fixture.data.getItemStack("FullInventory." + uuid + ".Items.0"));
			assertTrue(delivery.toCompletableFuture().isDone());
			assertEquals(java.util.List.of(excess), fixture.handler.getItems().get(uuid));
		}
	}

	@Test
	public void rejectedAsyncShutdownPersistenceReportsFailureWithoutCallerThreadSave() {
		Fixture fixture = createFixture();
		CompletionStage<Void> completion = fixture.handler.shutdownAsync(task -> {
			throw new java.util.concurrent.RejectedExecutionException("worker stopped");
		});
		assertThrows(java.util.concurrent.CompletionException.class, () -> completion.toCompletableFuture().join());
		verify(fixture.serverData, never()).saveDataChecked();
		assertTrue(fixture.handler.getTimer().isShutdown());
	}

	@Test
	public void shutdownPersistsAcceptedOverflowBeforeDiscardingQueuedPersistence() throws Exception {
		Fixture fixture = createFixture();
		UUID uuid = UUID.randomUUID();
		Player player = mock(Player.class);
		PlayerInventory inventory = mock(PlayerInventory.class);
		ItemStack item = mock(ItemStack.class);
		ItemStack excess = mock(ItemStack.class);
		when(fixture.plugin.getOptions().isDropOnFullInv()).thenReturn(false);
		when(player.getUniqueId()).thenReturn(uuid);
		when(player.isOnline()).thenReturn(true);
		when(player.getInventory()).thenReturn(inventory);
		when(inventory.addItem(item)).thenReturn(new HashMap<>(java.util.Map.of(0, excess)));
		fixture.handler.getLastMessageTime().put(uuid, System.currentTimeMillis());
		CountDownLatch executorBlocked = new CountDownLatch(1);
		CountDownLatch releaseExecutor = new CountDownLatch(1);
		fixture.handler.getTimer().execute(() -> {
			executorBlocked.countDown();
			try {
				releaseExecutor.await(2, TimeUnit.SECONDS);
			} catch (InterruptedException ignored) {
				Thread.currentThread().interrupt();
			}
		});
		assertTrue(executorBlocked.await(2, TimeUnit.SECONDS));

		CompletionStage<Void> delivery = fixture.handler.giveItemAsync(player, item);
		ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
		verify(fixture.bukkitScheduler).runTask(eq(fixture.plugin), task.capture(), eq(player));
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getPlayer(uuid)).thenReturn(player);
			task.getValue().run();
		}
		assertFalse(delivery.toCompletableFuture().isDone());

		fixture.handler.shutdown();
		delivery.toCompletableFuture().get(2, TimeUnit.SECONDS);
		releaseExecutor.countDown();
		assertEquals(excess, fixture.data.getItemStack("FullInventory." + uuid + ".Items.0"));
		verify(inventory, times(1)).addItem(item);
	}

	@Test
	public void queuedReplayDeliveryDoesNotStartAfterShutdownSnapshot() throws Exception {
		Fixture fixture = createFixture();
		UUID uuid = UUID.randomUUID();
		Player player = mock(Player.class);
		PlayerInventory inventory = mock(PlayerInventory.class);
		ItemStack item = mock(ItemStack.class);
		when(player.getUniqueId()).thenReturn(uuid);
		when(player.isOnline()).thenReturn(true);
		when(player.getInventory()).thenReturn(inventory);

		CompletionStage<Void> delivery = fixture.handler.giveItemAsync(player, item);
		ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
		verify(fixture.bukkitScheduler).runTask(eq(fixture.plugin), task.capture(), eq(player));
		fixture.handler.shutdown();
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getPlayer(uuid)).thenReturn(player);
			task.getValue().run();
		}

		java.util.concurrent.ExecutionException failure = assertThrows(java.util.concurrent.ExecutionException.class,
				() -> delivery.toCompletableFuture().get(2, TimeUnit.SECONDS));
		assertTrue(AdvancedCoreUser.isReplayActionNotStarted(failure));
		verify(inventory, never()).addItem(item);
	}

	@Test
	public void queuedPendingCheckCannotMutateItemsAfterShutdownSnapshot() {
		Fixture fixture = createFixture();
		UUID uuid = UUID.randomUUID();
		Player player = mock(Player.class);
		PlayerInventory inventory = mock(PlayerInventory.class);
		ItemStack item = mock(ItemStack.class);
		when(player.getUniqueId()).thenReturn(uuid);
		when(player.getInventory()).thenReturn(inventory);
		fixture.handler.add(uuid, item);

		fixture.handler.check(player);
		ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
		verify(fixture.bukkitScheduler).runTask(eq(fixture.plugin), task.capture(), eq(player));
		fixture.handler.shutdown();
		assertEquals(item, fixture.data.getItemStack("FullInventory." + uuid + ".Items.0"));

		task.getValue().run();

		verify(inventory, never()).addItem(any(ItemStack[].class));
		assertEquals(List.of(item), fixture.handler.getItems().get(uuid));
	}

	@Test
	public void giveItemAsyncFailsWithoutMutationWhenPlayerDisconnectsAfterEnqueue() throws Exception {
		Fixture fixture = createFixture();
		UUID uuid = UUID.randomUUID();
		Player player = mock(Player.class);
		PlayerInventory inventory = mock(PlayerInventory.class);
		ItemStack item = mock(ItemStack.class);
		when(player.getUniqueId()).thenReturn(uuid);
		when(player.getInventory()).thenReturn(inventory);

		CompletionStage<Void> delivery = fixture.handler.giveItemAsync(player, item);
		ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
		verify(fixture.bukkitScheduler).runTask(eq(fixture.plugin), task.capture(), eq(player));

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getPlayer(uuid)).thenReturn(null);
			task.getValue().run();
		}

		java.util.concurrent.ExecutionException failure = assertThrows(java.util.concurrent.ExecutionException.class,
				() -> delivery.toCompletableFuture().get(2, TimeUnit.SECONDS));
		assertTrue(AdvancedCoreUser.isReplayActionNotStarted(failure));
		verify(inventory, never()).addItem(item);
	}

	@Test
	public void rejectedItemSchedulerSignalsThatDeliveryNeverStarted() throws Exception {
		Fixture fixture = createFixture();
		Player player = mock(Player.class);
		ItemStack item = mock(ItemStack.class);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());
		org.mockito.Mockito.doThrow(new IllegalStateException("scheduler stopped"))
				.when(fixture.bukkitScheduler).runTask(eq(fixture.plugin), any(Runnable.class), eq(player));

		CompletionStage<Void> delivery = fixture.handler.giveItemAsync(player, item);
		java.util.concurrent.ExecutionException failure = assertThrows(java.util.concurrent.ExecutionException.class,
				() -> delivery.toCompletableFuture().get(2, TimeUnit.SECONDS));

		assertTrue(AdvancedCoreUser.isReplayActionNotStarted(failure));
	}

	@Test
	public void timedOutGiveItemAsyncSuppressesTheQueuedDeliveryToPreventReplayDuplicates() throws Exception {
		Fixture fixture = createFixture(0L);
		UUID uuid = UUID.randomUUID();
		Player player = mock(Player.class);
		PlayerInventory inventory = mock(PlayerInventory.class);
		ItemStack item = mock(ItemStack.class);
		when(player.getUniqueId()).thenReturn(uuid);
		when(player.getInventory()).thenReturn(inventory);
		when(inventory.addItem(item)).thenReturn(new HashMap<>());

		CompletionStage<Void> delivery = fixture.handler.giveItemAsync(player, item);
		java.util.concurrent.ExecutionException failure = assertThrows(java.util.concurrent.ExecutionException.class,
				() -> delivery.toCompletableFuture().get(2, TimeUnit.SECONDS));
		assertTrue(AdvancedCoreUser.isReplayActionNotStarted(failure));
		ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
		verify(fixture.bukkitScheduler).runTask(eq(fixture.plugin), task.capture(), eq(player));

		task.getValue().run();
		task.getValue().run();

		verify(inventory, never()).addItem(item);
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

	@Test
	public void saveWaitsForInflightDeliveryBeforeSnapshotting() throws Exception {
		Fixture fixture = createFixture();
		UUID uuid = UUID.randomUUID();
		Player player = mock(Player.class);
		PlayerInventory inventory = mock(PlayerInventory.class);
		ItemStack pending = mock(ItemStack.class);
		ItemStack excessItem = mock(ItemStack.class);
		CountDownLatch deliveryStarted = new CountDownLatch(1);
		CountDownLatch releaseDelivery = new CountDownLatch(1);
		CountDownLatch saveFinished = new CountDownLatch(1);

		when(player.getUniqueId()).thenReturn(uuid);
		when(player.getInventory()).thenReturn(inventory);
		when(inventory.addItem(pending)).thenAnswer(invocation -> {
			deliveryStarted.countDown();
			assertTrue(releaseDelivery.await(2, TimeUnit.SECONDS));
			HashMap<Integer, ItemStack> excess = new HashMap<>();
			excess.put(0, excessItem);
			return excess;
		});
		fixture.handler.add(uuid, pending);

		fixture.handler.check(player);
		ArgumentCaptor<Runnable> deliveryTask = ArgumentCaptor.forClass(Runnable.class);
		verify(fixture.bukkitScheduler).runTask(eq(fixture.plugin), deliveryTask.capture(), eq(player));

		Thread deliveryThread = new Thread(deliveryTask.getValue());
		deliveryThread.start();
		assertTrue(deliveryStarted.await(2, TimeUnit.SECONDS));

		Thread saveThread = new Thread(() -> {
			fixture.handler.save();
			saveFinished.countDown();
		});
		saveThread.start();
		assertFalse(saveFinished.await(100, TimeUnit.MILLISECONDS));

		releaseDelivery.countDown();
		assertTrue(saveFinished.await(2, TimeUnit.SECONDS));
		deliveryThread.join(2000L);
		saveThread.join(2000L);

		ConfigurationSection root = fixture.data.getConfigurationSection("FullInventory");
		assertNotNull(root);
		assertEquals(excessItem, root.getItemStack(uuid + ".Items.0"));
	}

    @Test void disabledUninitializedServerDataPreservesSafeOverflowFallback() throws Exception {
        Fixture f = createFixture();
        when(f.plugin.isLoadServerData()).thenReturn(false); when(f.serverData.getData()).thenReturn(null);
        UUID owner = UUID.randomUUID(); var player = mock(Player.class); var slots = mock(PlayerInventory.class);
        var input = mock(ItemStack.class); var overflow = mock(ItemStack.class);
        var world = mock(World.class); var location = mock(Location.class);
        when(player.getUniqueId()).thenReturn(owner); when(player.isOnline()).thenReturn(true);
        when(player.getInventory()).thenReturn(slots); when(player.getWorld()).thenReturn(world); when(player.getLocation()).thenReturn(location);
        when(slots.addItem(input)).thenReturn(new HashMap<>(java.util.Map.of(0, overflow)));
        when(world.dropItem(location, overflow)).thenReturn(mock(org.bukkit.entity.Item.class));
        f.handler.getLastMessageTime().put(owner, System.currentTimeMillis());
        var tasks = new java.util.concurrent.LinkedBlockingQueue<Runnable>();
        doAnswer(call -> { tasks.add(call.getArgument(1)); return null; })
                .when(f.bukkitScheduler).runTask(eq(f.plugin), any(Runnable.class), eq(player));
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(owner)).thenReturn(player);
            var receipt = f.handler.giveItemAsync(player, input).toCompletableFuture();
            tasks.poll(3, TimeUnit.SECONDS).run(); f.handler.getTimer().submit(() -> {}).get(3, TimeUnit.SECONDS);
            Runnable fallback = tasks.poll(3, TimeUnit.SECONDS); assertNotNull(fallback); assertFalse(receipt.isDone());
            fallback.run(); receipt.get(3, TimeUnit.SECONDS);
            verify(slots, times(1)).addItem(input); verify(world, times(1)).dropItem(location, overflow);
            verify(f.serverData, never()).mutateFinalData(any()); assertFalse(f.handler.getItems().containsKey(owner));
        }
    }

    @Test void overlappingSuccessfulAsyncOwnerSavesRetainEveryUnpublishedOverflow() throws Exception {
        overlappingAsyncOwnerSavesRetainEveryUnpublishedOverflow(false);
    }
    @Test void failedEarlierSaveCannotDropOverflowOwnedByAnotherPendingPublication() throws Exception {
        overlappingAsyncOwnerSavesRetainEveryUnpublishedOverflow(true);
    }
    private void overlappingAsyncOwnerSavesRetainEveryUnpublishedOverflow(boolean failFirst) throws Exception {
        Fixture f = createFixture();
        var submissions = new java.util.concurrent.LinkedBlockingQueue<java.util.Map.Entry<YamlConfiguration, java.util.concurrent.CompletableFuture<Void>>>();
        doAnswer(call -> {
            var candidate = new YamlConfiguration();
            call.getArgument(0, java.util.function.Consumer.class).accept(candidate);
            var confirmed = new java.util.concurrent.CompletableFuture<Void>();
            submissions.add(java.util.Map.entry(candidate, confirmed));
            return confirmed.thenRun(() -> {
                synchronized (f.serverData.publicationMonitor()) {
                    f.data.set("FullInventory", null);
                    for (String path : candidate.getKeys(true))
                        if (!candidate.isConfigurationSection(path)) f.data.set(path, candidate.get(path));
                }
            });
        }).when(f.serverData).mutateFinalData(any());
        var ownerTasks = new java.util.concurrent.LinkedBlockingQueue<Runnable>();
        doAnswer(call -> { ownerTasks.add(call.getArgument(1)); return null; })
                .when(f.bukkitScheduler).runTask(eq(f.plugin), any(Runnable.class), any(Player.class));
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        var a = mock(Player.class); var b = mock(Player.class);
        var slots = mock(PlayerInventory.class); var item = mock(ItemStack.class);
        when(a.getUniqueId()).thenReturn(first); when(b.getUniqueId()).thenReturn(second);
        for (Player player : List.of(a, b)) { when(player.isOnline()).thenReturn(true); when(player.getInventory()).thenReturn(slots); }
        when(slots.addItem(item)).thenReturn(new HashMap<>(java.util.Map.of(0, item)));
        f.handler.getLastMessageTime().put(first, System.currentTimeMillis());
        f.handler.getLastMessageTime().put(second, System.currentTimeMillis());
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(first)).thenReturn(a); bukkit.when(() -> Bukkit.getPlayer(second)).thenReturn(b);
            var receiptA = f.handler.giveItemAsync(a, item).toCompletableFuture();
            ownerTasks.poll(3, TimeUnit.SECONDS).run(); var publicationA = submissions.poll(3, TimeUnit.SECONDS);
            var receiptB = f.handler.giveItemAsync(b, item).toCompletableFuture();
            ownerTasks.poll(3, TimeUnit.SECONDS).run(); var publicationB = submissions.poll(3, TimeUnit.SECONDS);
            assertNotNull(publicationA); assertNotNull(publicationB);
            assertFalse(receiptA.isDone()); assertFalse(receiptB.isDone()); assertTrue(f.handler.getItems().isEmpty());
            assertTrue(publicationB.getKey().contains("FullInventory." + first));
            assertTrue(publicationB.getKey().contains("FullInventory." + second));
            if (failFirst) {
                publicationA.getValue().completeExceptionally(new IllegalStateException("first publication rejected"));
                // An unconfirmed owner save can already be visible on disk.
                // It must remain reserved for confirmed retry, never fall back to a drop.
                f.handler.getTimer().submit(() -> {}).get(3, TimeUnit.SECONDS);
                assertTrue(ownerTasks.isEmpty()); verify(a, never()).getWorld(); assertFalse(receiptA.isDone());
            } else publicationA.getValue().complete(null);
            publicationB.getValue().complete(null);
            receiptA.get(3, TimeUnit.SECONDS); receiptB.get(3, TimeUnit.SECONDS);
            assertEquals(List.of(item), f.handler.getItems().get(first));
            assertEquals(List.of(item), f.handler.getItems().get(second));
            assertEquals(item, f.data.getItemStack("FullInventory." + first + ".Items.0"));
            assertEquals(item, f.data.getItemStack("FullInventory." + second + ".Items.0"));
        }
    }

	private Fixture createFixture() {
		return createFixture(TimeUnit.SECONDS.toMillis(30));
	}

	private Fixture createFixture(long itemDeliveryTimeoutMillis) {
		AdvancedCorePlugin plugin = mock(AdvancedCorePlugin.class);
		AdvancedCoreConfigOptions options = mock(AdvancedCoreConfigOptions.class);
		ScheduledExecutorService sharedInventoryTimer = mock(ScheduledExecutorService.class);
		BukkitScheduler bukkitScheduler = mock(BukkitScheduler.class);
		ServerData serverData = mock(ServerData.class);
		when(serverData.publicationMonitor()).thenCallRealMethod();
		org.mockito.Mockito.doCallRealMethod().when(serverData).mutateFinalData(any());
		// The fixture's synchronous publisher is the existing controllable saveData stub.
		doAnswer(call -> { serverData.saveData(); return null; }).when(serverData).saveDataChecked();
		YamlConfiguration data = new YamlConfiguration();

		when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
		when(plugin.getInventoryTimer()).thenReturn(sharedInventoryTimer);
		when(plugin.getOptions()).thenReturn(options);
		when(plugin.isEnabled()).thenReturn(true);
		when(plugin.getBukkitScheduler()).thenReturn(bukkitScheduler);
		when(plugin.getServerDataFile()).thenReturn(serverData);
		when(serverData.getData()).thenReturn(data);

		FullInventoryHandler handler = new FullInventoryHandler(plugin) {
			@Override
			protected long getItemDeliveryTimeoutMillis() {
				return itemDeliveryTimeoutMillis;
			}
		};
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
