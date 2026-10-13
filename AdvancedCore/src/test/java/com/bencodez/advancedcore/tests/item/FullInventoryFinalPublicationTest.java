package com.bencodez.advancedcore.tests.item;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.AdvancedCoreConfigOptions;
import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.item.FullInventoryHandler;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.rewards.RewardOptions;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.advancedcore.data.ServerData;
import com.bencodez.simpleapi.scheduler.BukkitScheduler;

class FullInventoryFinalPublicationTest {
	@TempDir Path directory;

	@Test
	void failedFinalPublicationCannotLeakOverflowThroughLaterSaveAndRestart() throws Exception {
		laterWholeFileSaveAndRestartKeepOverflowAndReplayReceiptConsistent(true);
	}

	@Test
	void successfulFinalPublicationKeepsOverflowAndReceiptConsistentAfterRestart() throws Exception {
		laterWholeFileSaveAndRestartKeepOverflowAndReplayReceiptConsistent(false);
	}

	private void laterWholeFileSaveAndRestartKeepOverflowAndReplayReceiptConsistent(boolean failInventory) throws Exception {
		ConfigurationSerialization.registerClass(PersistedItem.class, "ACFinalOverflowTestItem");
		var plugin = mock(AdvancedCorePlugin.class);
		var options = mock(AdvancedCoreConfigOptions.class);
		var scheduler = mock(BukkitScheduler.class);
		var server = mock(Server.class);
		when(plugin.getDataFolder()).thenReturn(directory.toFile()); when(plugin.getServer()).thenReturn(server);
		when(server.getScheduler()).thenReturn(mock(org.bukkit.scheduler.BukkitScheduler.class));
		when(plugin.getLogger()).thenReturn(Logger.getLogger("FullInventoryFinalPublicationTest"));
		when(plugin.getOptions()).thenReturn(options); when(options.isOnlineMode()).thenReturn(true);
		when(plugin.isEnabled()).thenReturn(true); when(plugin.getBukkitScheduler()).thenReturn(scheduler);
		var checkpointExecutor = mock(java.util.concurrent.ScheduledExecutorService.class);
		doAnswer(call -> { call.getArgument(0, Runnable.class).run(); return null; })
				.when(checkpointExecutor).execute(any(Runnable.class));
		when(plugin.getTimer()).thenReturn(checkpointExecutor);
		var owner = new FailingPublicationOwner(plugin); owner.setup();
		when(plugin.getServerDataFile()).thenReturn(owner);
		var handler = new FullInventoryHandler(plugin);
		when(plugin.getFullInventoryHandler()).thenReturn(handler);
		var uuid = UUID.randomUUID(); var player = mock(Player.class); var inventory = mock(PlayerInventory.class);
		when(player.getUniqueId()).thenReturn(uuid); when(player.isOnline()).thenReturn(true);
		when(player.getInventory()).thenReturn(inventory);
		var item = new PersistedItem();
		when(inventory.addItem(item)).thenReturn(new HashMap<>(Map.of(0, item)), new HashMap<>());
		handler.getLastMessageTime().put(uuid, System.currentTimeMillis());
		var ownerTask = new AtomicReference<Runnable>();
		doAnswer(call -> { ownerTask.set(call.getArgument(1)); return null; })
				.when(scheduler).runTask(eq(plugin), any(Runnable.class), eq(player));
		var timerEntered = new CountDownLatch(1); var releaseTimer = new CountDownLatch(1);
		handler.getTimer().execute(() -> {
			timerEntered.countDown();
			try { releaseTimer.await(5, TimeUnit.SECONDS); }
			catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
		});
		assertTrue(timerEntered.await(2, TimeUnit.SECONDS));
		var durableCheckpoint = new AtomicReference<>(new HashMap<String, String>());
		var rewardOptions = new RewardOptions();
		rewardOptions.setAsyncReplayCheckpointConsumer(
				checkpoint -> durableCheckpoint.set(new HashMap<>(checkpoint.getPlaceholders())));
		String injection = "AsyncReward/FinalOverflow";
		FullInventoryHandler restarted = null;
		try (var bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getPlayer(uuid)).thenReturn(player);
			var user = new AdvancedCoreUser(plugin, uuid, false, false);
			var placeholders = new HashMap<String, String>();
			var actions = user.beginAsyncActionCollection(Reward.replayStateFor(rewardOptions), placeholders, injection);
			user.giveItem(item);
			var replay = user.endAsyncActionCollection(actions).toCompletableFuture();
			ownerTask.get().run(); assertFalse(replay.isDone());
			owner.failNext.set(failInventory);
			var finalFiles = handler.shutdownAsync(Runnable::run).toCompletableFuture();
			if (failInventory) assertThrows(java.util.concurrent.CompletionException.class, finalFiles::join);
			else finalFiles.get(2, TimeUnit.SECONDS);
			// These are the actual subsequent whole-file final mutations, not an
			// acknowledgement inferred from the failed inventory publication.
			owner.mutateFinalData(yaml -> yaml.set("LastUpdated", 2468L)).toCompletableFuture().join();
			owner.mutateFinalData(yaml -> yaml.set("TimedPermissions.example", java.util.List.of("example.use%line%12345")))
					.toCompletableFuture().join();
			var disk = YamlConfiguration.loadConfiguration(owner.getdFile());
			assertEquals(!failInventory, disk.contains("FullInventory." + uuid));
			assertEquals(!failInventory, replay.isDone());
			assertEquals(!failInventory, durableCheckpoint.get().containsKey(Reward.legacyActionReplayKey(injection)));
			assertEquals(2468L, disk.getLong("LastUpdated"));
			assertFalse(failInventory && handler.getItems().containsKey(uuid));
			// A successor independently reads the published file and the checkpoint
			// emitted by the production action collection, as restart recovery does.
			var successor = new FailingPublicationOwner(plugin); successor.setup();
			when(plugin.getServerDataFile()).thenReturn(successor);
			restarted = new FullInventoryHandler(plugin); when(plugin.getFullInventoryHandler()).thenReturn(restarted);
			restarted.startup();
			assertEquals(!failInventory, restarted.getItems().containsKey(uuid));
			ownerTask.set(null);
			var successorUser = new AdvancedCoreUser(plugin, uuid, false, false);
			var recovered = new HashMap<>(durableCheckpoint.get());
			var resumed = successorUser.beginAsyncActionCollection(Reward.replayStateFor(rewardOptions), recovered, injection);
			successorUser.giveItem(item);
			var retried = successorUser.endAsyncActionCollection(resumed).toCompletableFuture();
			if (failInventory) { assertNotNull(ownerTask.get()); ownerTask.get().run(); }
			else assertNull(ownerTask.get(), "completed action receipt must suppress replay");
			retried.get(2, TimeUnit.SECONDS);
			verify(inventory, times(failInventory ? 2 : 1)).addItem(item);
		} finally {
			releaseTimer.countDown(); handler.getTimer().shutdownNow();
			if (restarted != null) restarted.getTimer().shutdownNow();
			ConfigurationSerialization.unregisterClass(PersistedItem.class);
		}
	}

	private static final class FailingPublicationOwner extends ServerData {
		private final AtomicBoolean failNext = new AtomicBoolean();
		private FailingPublicationOwner(AdvancedCorePlugin plugin) { super(plugin); }
		@Override protected void moveAtomically(Path source, Path target) throws IOException {
			if (failNext.compareAndSet(true, false)) throw new IOException("inventory publication rejected");
			super.moveAtomically(source, target);
		}
	}

	/** A headless item serializer; delivery still uses the production inventory path. */
	@SerializableAs("ACFinalOverflowTestItem")
	public static final class PersistedItem extends ItemStack {
		public PersistedItem() { super(Material.STONE, 1); }
		@Override public Map<String, Object> serialize() { return Map.of("amount", 1); }
		public static PersistedItem deserialize(Map<String, Object> value) { return new PersistedItem(); }
		@Override public boolean equals(Object value) { return value instanceof PersistedItem; }
		@Override public int hashCode() { return 1; }
	}
}
