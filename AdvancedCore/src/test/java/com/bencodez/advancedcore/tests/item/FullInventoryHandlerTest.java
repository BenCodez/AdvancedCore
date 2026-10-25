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

    @Test void deferredFixturePublishesControlOnlyAfterOwnerCallbackRegistration() throws Exception {
        Fixture f = createFixture();
        var registering = new CountDownLatch(1);
        var allowRegistration = new CountDownLatch(1);
        var control = new java.util.concurrent.CompletableFuture<Void>() {
            @Override public java.util.concurrent.CompletableFuture<Void> thenRun(Runnable action) {
                registering.countDown();
                try { assertTrue(allowRegistration.await(3, TimeUnit.SECONDS)); }
                catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(failure);
                }
                return super.thenRun(action);
            }
        };
        var submissions = deferredPublications(f, null, () -> control);
        UUID owner = UUID.randomUUID(); var item = mock(ItemStack.class);
        f.handler.add(owner, item);
        var worker = java.util.concurrent.Executors.newSingleThreadExecutor();
        var save = worker.submit(f.handler::saveDurably);
        try {
            assertTrue(registering.await(3, TimeUnit.SECONDS));
            assertTrue(submissions.isEmpty(), "raw completion must not precede its owner installation callback");
            allowRegistration.countDown();
            assertFalse(save.get(3, TimeUnit.SECONDS));
            var publication = submissions.poll(3, TimeUnit.SECONDS); assertNotNull(publication);
            publication.getValue().complete(null);
            assertEquals(item, f.data.getItemStack("FullInventory." + owner + ".Items.0"));
        } finally {
            allowRegistration.countDown(); control.complete(null);
            worker.shutdown(); assertTrue(worker.awaitTermination(3, TimeUnit.SECONDS));
        }
    }

    @Test void reversedOwnerCompletionsCannotEraseLaterSavedItems() throws Exception {
        Fixture f = createFixture();
        var submissions = deferredPublications(f);
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        var item = mock(ItemStack.class);
        f.handler.add(first, item); assertFalse(f.handler.saveDurably());
        var publicationA = submissions.poll(3, TimeUnit.SECONDS); assertNotNull(publicationA);
        f.handler.add(second, item); assertFalse(f.handler.saveDurably());
        var publicationB = submissions.poll();
        if (publicationB != null) {
            // An owner is allowed to finish concurrent submissions in reverse order.
            publicationB.getValue().complete(null); publicationA.getValue().complete(null);
        } else {
            publicationA.getValue().complete(null);
            publicationB = submissions.poll(3, TimeUnit.SECONDS); assertNotNull(publicationB);
            publicationB.getValue().complete(null);
        }
        assertEquals(item, f.data.getItemStack("FullInventory." + first + ".Items.0"));
        assertEquals(item, f.data.getItemStack("FullInventory." + second + ".Items.0"),
                "an older snapshot must never erase a later completed save");
    }

    @Test void laterSaveWaitsForEarlierOwnerAndCapturesLatestItems() throws Exception {
        Fixture f = createFixture();
        var submissions = deferredPublications(f);
        UUID first = UUID.randomUUID(), second = UUID.randomUUID(), latest = UUID.randomUUID();
        var item = mock(ItemStack.class);
        f.handler.add(first, item);
        assertFalse(f.handler.saveDurably());
        var publicationA = submissions.poll(3, TimeUnit.SECONDS); assertNotNull(publicationA);
        f.handler.add(second, item);
        assertFalse(f.handler.saveDurably());
        // This addition must be captured when B can actually publish, not when B queues.
        f.handler.add(latest, item);
        assertTrue(submissions.isEmpty(), "a later owner stage must not overtake the pending stage");
        publicationA.getValue().complete(null);
        var publicationB = submissions.poll(3, TimeUnit.SECONDS); assertNotNull(publicationB);
        assertTrue(publicationB.getKey().contains("FullInventory." + latest));
        publicationB.getValue().complete(null);
        assertEquals(item, f.data.getItemStack("FullInventory." + first + ".Items.0"));
        assertEquals(item, f.data.getItemStack("FullInventory." + second + ".Items.0"));
        assertEquals(item, f.data.getItemStack("FullInventory." + latest + ".Items.0"));
    }

    @Test void shutdownWaitsForPendingOrdinaryOwnerSave() throws Exception {
        Fixture f = createFixture();
        var submissions = deferredPublications(f);
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        var item = mock(ItemStack.class);
        f.handler.add(first, item); assertFalse(f.handler.saveDurably());
        var publicationA = submissions.poll(3, TimeUnit.SECONDS); assertNotNull(publicationA);
        f.handler.add(second, item);
        var shutdown = f.handler.shutdownAsync(Runnable::run).toCompletableFuture();
        assertFalse(shutdown.isDone());
        assertTrue(submissions.isEmpty(), "shutdown must wait for the outstanding owner stage");
        publicationA.getValue().complete(null);
        var finalPublication = submissions.poll(3, TimeUnit.SECONDS); assertNotNull(finalPublication);
        finalPublication.getValue().complete(null); shutdown.get(3, TimeUnit.SECONDS);
        assertEquals(item, f.data.getItemStack("FullInventory." + first + ".Items.0"));
        assertEquals(item, f.data.getItemStack("FullInventory." + second + ".Items.0"));
        assertTrue(f.handler.getTimer().isShutdown());
    }

    @Test void repeatedLegacyShutdownReusesPendingAndCompletedPublication() throws Exception {
        Fixture f = createFixture(); var submissions = deferredPublications(f);
        f.handler.add(UUID.randomUUID(), mock(ItemStack.class));
        f.handler.shutdown(); var last = submissions.poll(3, TimeUnit.SECONDS); assertNotNull(last);
        f.handler.shutdown();
        var finalStage = f.handler.shutdownAsync(Runnable::run).toCompletableFuture();
        last.getValue().complete(null);
        finalStage.get(3, TimeUnit.SECONDS);
        assertTrue(f.handler.getTimer().isShutdown());
        f.handler.shutdown();
        assertTrue(submissions.isEmpty(), "repeated shutdown must not submit stale final writes");
        verify(f.serverData, times(1)).mutateFinalData(any());
    }

    @Test void shutdownDrainsAcceptedSnapshotContinuationBeforeFinalPublication() throws Exception {
        Fixture f = createFixture(); var submissions = deferredPublications(f);
        UUID first = UUID.randomUUID(), second = UUID.randomUUID(); var item = mock(ItemStack.class);
        f.handler.add(first, item); assertFalse(f.handler.saveDurably());
        var a = submissions.poll(3, TimeUnit.SECONDS); assertNotNull(a);
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var interrupted = new AtomicBoolean();
        f.handler.getTimer().execute(() -> {
            entered.countDown();
            try { release.await(5, TimeUnit.SECONDS); }
            catch (InterruptedException failure) { interrupted.set(true); }
        });
        assertTrue(entered.await(3, TimeUnit.SECONDS));
        try {
            f.handler.add(second, item); assertFalse(f.handler.saveDurably());
            // B is accepted by the timer but cannot run until the blocked task drains.
            a.getValue().complete(null);
            var shutdown = f.handler.shutdownAsync(Runnable::run).toCompletableFuture();
            assertTrue(f.handler.getTimer().isShutdown()); assertFalse(shutdown.isDone());
            release.countDown();
            var b = submissions.poll(3, TimeUnit.SECONDS); assertNotNull(b);
            assertFalse(interrupted.get()); assertFalse(shutdown.isDone());
            b.getValue().complete(null);
            var last = submissions.poll(3, TimeUnit.SECONDS); assertNotNull(last);
            assertFalse(shutdown.isDone()); last.getValue().complete(null); shutdown.get(3, TimeUnit.SECONDS);
            assertEquals(item, f.data.getItemStack("FullInventory." + first + ".Items.0"));
            assertEquals(item, f.data.getItemStack("FullInventory." + second + ".Items.0"));
        } finally { release.countDown(); }
    }

    @Test void cancelledShutdownObservationCannotAdvanceActualLifecyclePublications() throws Exception {
        verifyShutdownObservationCannotAdvanceActualLifecyclePublications("cancel");
    }
    @Test void completedShutdownObservationCannotAdvanceActualLifecyclePublications() throws Exception {
        verifyShutdownObservationCannotAdvanceActualLifecyclePublications("complete");
    }
    @Test void failedShutdownObservationCannotAdvanceActualLifecyclePublications() throws Exception {
        verifyShutdownObservationCannotAdvanceActualLifecyclePublications("fail");
    }
    @Test void untouchedShutdownObservationRetainsActualLifecycleOrder() throws Exception {
        verifyShutdownObservationCannotAdvanceActualLifecyclePublications("untouched");
    }
    private void verifyShutdownObservationCannotAdvanceActualLifecyclePublications(String action) throws Exception {
        Fixture f = createFixture();
        when(f.plugin.isLoadServerData()).thenReturn(true);
        when(f.plugin.getFullInventoryHandler()).thenReturn(f.handler);
        var permissions = mock(com.bencodez.advancedcore.api.permissions.PermissionHandler.class);
        when(f.plugin.getPermissionHandler()).thenReturn(permissions);
        var publications = new java.util.concurrent.LinkedBlockingQueue<java.util.Map.Entry<YamlConfiguration,
                java.util.concurrent.CompletableFuture<Void>>>();
        var controls = new java.util.concurrent.CopyOnWriteArrayList<java.util.concurrent.CompletableFuture<Void>>();
        doAnswer(call -> {
            var snapshot = new YamlConfiguration();
            synchronized (f.serverData.publicationMonitor()) {
                for (String path : f.data.getKeys(true))
                    if (!f.data.isConfigurationSection(path)) snapshot.set(path, f.data.get(path));
                call.getArgument(0, java.util.function.Consumer.class).accept(snapshot);
            }
            var control = new java.util.concurrent.CompletableFuture<Void>();
            controls.add(control);
            var installed = control.thenRun(() -> {
                synchronized (f.serverData.publicationMonitor()) {
                    for (String key : new ArrayList<>(f.data.getKeys(false))) f.data.set(key, null);
                    for (String path : snapshot.getKeys(true))
                        if (!snapshot.isConfigurationSection(path)) f.data.set(path, snapshot.get(path));
                }
            });
            publications.add(java.util.Map.entry(snapshot, control));
            return installed;
        }).when(f.serverData).mutateFinalData(any());
        var permissionsStarted = new CountDownLatch(1);
        var permissionSave = new java.util.concurrent.CompletableFuture<Void>();
        when(permissions.shutDownAsync(any())).thenAnswer(call -> {
            call.getArgument(0, java.util.concurrent.Executor.class).execute(permissionsStarted::countDown);
            return permissionSave;
        });
        var finalFiles = new AtomicReference<CompletionStage<Void>>();
        doAnswer(call -> { finalFiles.set(call.getArgument(0)); return null; })
                .when(f.plugin).onShutdownComplete(any(CompletionStage.class));
        UUID owner = UUID.randomUUID(); var item = mock(ItemStack.class);
        f.handler.add(owner, item);
        var caller = f.handler.shutdownAsync(Runnable::run).toCompletableFuture();
        var inventory = publications.poll(3, TimeUnit.SECONDS); assertNotNull(inventory);
        switch (action) {
            case "cancel" -> assertTrue(caller.cancel(false));
            case "complete" -> assertTrue(caller.complete(null));
            case "fail" -> assertTrue(caller.completeExceptionally(new IllegalStateException("synthetic caller failure")));
            default -> assertFalse(caller.isDone());
        }
        var platform = new com.bencodez.advancedcore.bukkit.runtime.BukkitRuntimePlatform(f.plugin);
        var workerField = platform.getClass().getDeclaredField("persistenceWorker"); workerField.setAccessible(true);
        var worker = (java.util.concurrent.ThreadPoolExecutor) workerField.get(platform);
        try {
            platform.beforeExecutorShutdown().stream()
                    .filter(cleanup -> cleanup.name().equals("full inventory handler")
                            || cleanup.name().equals("server data timestamp"))
                    .forEach(cleanup -> cleanup.action().run());
            platform.afterExecutorShutdown().stream()
                    .filter(cleanup -> cleanup.name().equals("permission handler"))
                    .forEach(cleanup -> cleanup.action().run());
            worker.submit(() -> {}).get(3, TimeUnit.SECONDS);
            assertTrue(publications.isEmpty(), "timestamp must await the actual inventory owner after caller completion");
            assertEquals(1L, permissionsStarted.getCount());
            var lifecycleObservation = f.handler.shutdownAsync(Runnable::run).toCompletableFuture();
            assertFalse(lifecycleObservation.isDone());
            inventory.getValue().complete(null);
            lifecycleObservation.get(3, TimeUnit.SECONDS);
            var timestamp = publications.poll(3, TimeUnit.SECONDS); assertNotNull(timestamp);
            assertTrue(timestamp.getKey().contains("LastUpdated"));
            assertEquals(item, timestamp.getKey().getItemStack("FullInventory." + owner + ".Items.0"));
            assertEquals(1L, permissionsStarted.getCount());
            timestamp.getValue().complete(null);
            assertTrue(permissionsStarted.await(3, TimeUnit.SECONDS),
                    "permission publication must start after the timestamp owner settles");
            platform.afterExecutorShutdown().stream()
                    .filter(cleanup -> cleanup.name().equals("shutdown persistence worker")
                            || cleanup.name().equals("plugin shutdown completion hook"))
                    .forEach(cleanup -> cleanup.action().run());
            assertFalse(finalFiles.get().toCompletableFuture().isDone());
            permissionSave.complete(null);
            finalFiles.get().toCompletableFuture().get(3, TimeUnit.SECONDS);
            assertTrue(worker.awaitTermination(3, TimeUnit.SECONDS));
            assertEquals(item, f.data.getItemStack("FullInventory." + owner + ".Items.0"));
            assertTrue(f.data.contains("LastUpdated"));
        } finally {
            controls.forEach(control -> control.complete(null)); permissionSave.complete(null);
            worker.shutdownNow(); assertTrue(worker.awaitTermination(3, TimeUnit.SECONDS));
        }
    }

    @Test void cancellingShutdownViewCannotReleasePendingOwner() throws Exception {
        Fixture f = createFixture(); var submissions = deferredPublications(f);
        UUID owner = UUID.randomUUID(); var item = mock(ItemStack.class);
        f.handler.add(owner, item); assertFalse(f.handler.saveDurably());
        var a = submissions.poll(3, TimeUnit.SECONDS); assertNotNull(a);
        var shutdown = f.handler.shutdownAsync(Runnable::run).toCompletableFuture();
        assertTrue(shutdown.cancel(false)); assertTrue(submissions.isEmpty());
        assertTrue(f.handler.getTimer().isShutdown());
        a.getValue().complete(null);
        var last = submissions.poll(3, TimeUnit.SECONDS); assertNotNull(last);
        last.getValue().complete(null);
        assertEquals(item, f.data.getItemStack("FullInventory." + owner + ".Items.0"));
    }

    @Test void deferredSnapshotRunsOnWorkerInsteadOfOwnerCompletionThread() throws Exception {
        Fixture f = createFixture();
        var calls = new java.util.concurrent.LinkedBlockingQueue<Thread>();
        var submissions = deferredPublications(f, calls);
        f.handler.add(UUID.randomUUID(), mock(ItemStack.class)); assertFalse(f.handler.saveDurably());
        var a = submissions.poll(3, TimeUnit.SECONDS); assertNotNull(a); calls.take();
        assertFalse(f.handler.saveDurably()); a.getValue().complete(null);
        var b = submissions.poll(3, TimeUnit.SECONDS); assertNotNull(b);
        Thread worker = calls.poll(3, TimeUnit.SECONDS); assertNotNull(worker);
        assertNotSame(Thread.currentThread(), worker); assertTrue(worker.isDaemon());
        b.getValue().complete(null);
    }

    private java.util.concurrent.LinkedBlockingQueue<java.util.Map.Entry<YamlConfiguration,
            java.util.concurrent.CompletableFuture<Void>>> deferredPublications(Fixture f) {
        return deferredPublications(f, null);
    }

    private java.util.concurrent.LinkedBlockingQueue<java.util.Map.Entry<YamlConfiguration,
            java.util.concurrent.CompletableFuture<Void>>> deferredPublications(Fixture f,
                    java.util.Queue<Thread> threads) {
        return deferredPublications(f, threads, java.util.concurrent.CompletableFuture::new);
    }

    private java.util.concurrent.LinkedBlockingQueue<java.util.Map.Entry<YamlConfiguration,
            java.util.concurrent.CompletableFuture<Void>>> deferredPublications(Fixture f,
                    java.util.Queue<Thread> threads,
                    java.util.function.Supplier<java.util.concurrent.CompletableFuture<Void>> controls) {
        var submissions = new java.util.concurrent.LinkedBlockingQueue<java.util.Map.Entry<YamlConfiguration,
                java.util.concurrent.CompletableFuture<Void>>>();
        doAnswer(call -> {
            if (threads != null) threads.add(Thread.currentThread());
            var candidate = new YamlConfiguration();
            call.getArgument(0, java.util.function.Consumer.class).accept(candidate);
            var confirmed = controls.get();
            var publication = confirmed.thenRun(() -> {
                synchronized (f.serverData.publicationMonitor()) {
                    f.data.set("FullInventory", null);
                    for (String path : candidate.getKeys(true))
                        if (!candidate.isConfigurationSection(path)) f.data.set(path, candidate.get(path));
                }
            });
            // Completion is observable only after its installation callback exists.
            submissions.add(java.util.Map.entry(candidate, confirmed));
            return publication;
        }).when(f.serverData).mutateFinalData(any());
        return submissions;
    }

    @Test void overlappingSuccessfulAsyncOwnerSavesRetainEveryUnpublishedOverflow() throws Exception {
        overlappingAsyncOwnerSavesRetainEveryUnpublishedOverflow(false);
    }
    @Test void failedEarlierSaveCannotDropOverflowOwnedByAnotherPendingPublication() throws Exception {
        overlappingAsyncOwnerSavesRetainEveryUnpublishedOverflow(true);
    }
    @Test void shutdownConfirmsQueuedOverflowOnlyAfterItsOrderedFinalSave() throws Exception {
        overlappingAsyncOwnerSavesRetainEveryUnpublishedOverflow(false, true);
    }
    private void overlappingAsyncOwnerSavesRetainEveryUnpublishedOverflow(boolean failFirst) throws Exception {
        overlappingAsyncOwnerSavesRetainEveryUnpublishedOverflow(failFirst, false);
    }
    private void overlappingAsyncOwnerSavesRetainEveryUnpublishedOverflow(boolean failFirst, boolean shutdown) throws Exception {
        Fixture f = createFixture();
        var submissions = deferredPublications(f);
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
            ownerTasks.poll(3, TimeUnit.SECONDS).run();
            f.handler.getTimer().submit(() -> {}).get(3, TimeUnit.SECONDS);
            assertNotNull(publicationA);
            assertTrue(submissions.isEmpty(), "the second snapshot must await the first owner");
            assertFalse(receiptA.isDone()); assertFalse(receiptB.isDone()); assertTrue(f.handler.getItems().isEmpty());
            var finalFiles = shutdown ? f.handler.shutdownAsync(Runnable::run).toCompletableFuture() : null;
            if (failFirst) {
                publicationA.getValue().completeExceptionally(new IllegalStateException("first publication rejected"));
                // An unconfirmed owner save can already be visible on disk.
                // It must remain reserved for confirmed retry, never fall back to a drop.
                f.handler.getTimer().submit(() -> {}).get(3, TimeUnit.SECONDS);
                assertTrue(ownerTasks.isEmpty()); verify(a, never()).getWorld(); assertFalse(receiptA.isDone());
            } else publicationA.getValue().complete(null);
            var publicationB = submissions.poll(3, TimeUnit.SECONDS); assertNotNull(publicationB);
            assertTrue(publicationB.getKey().contains("FullInventory." + first));
            assertTrue(publicationB.getKey().contains("FullInventory." + second));
            assertFalse(receiptB.isDone());
            publicationB.getValue().complete(null);
            if (finalFiles != null) finalFiles.get(3, TimeUnit.SECONDS);
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
