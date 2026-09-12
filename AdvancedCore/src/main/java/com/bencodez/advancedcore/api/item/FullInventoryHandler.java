package com.bencodez.advancedcore.api.item;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.advancedcore.data.ServerData;
import com.bencodez.simpleapi.messages.MessageAPI;

import lombok.Getter;

/**
 * Handler for items when player inventories are full.
 */
public class FullInventoryHandler {
	private static final long MESSAGE_COOLDOWN_MS = 5000L;
	private static final long PENDING_ITEM_RETENTION_MS = TimeUnit.DAYS.toMillis(1);

	@Getter
	private final ConcurrentHashMap<UUID, ArrayList<ItemStack>> items = new ConcurrentHashMap<>();

	/*
	 * Replay-aware delivery cannot place an overflow into the ordinary pending map
	 * until its snapshot has been written.  The ordinary periodic sweep is allowed
	 * to consume that map, so doing so earlier makes a failed save indistinguishable
	 * from a delivery that has already happened and a replay can duplicate items.
	 */
	private final ConcurrentHashMap<String, ReservedOverflow> replayOverflowReservations = new ConcurrentHashMap<>();

	private final AdvancedCorePlugin plugin;
	private final ReentrantReadWriteLock deliveryLock = new ReentrantReadWriteLock(true);

	@Getter
	private ScheduledExecutorService timer;

	private ScheduledFuture<?> checkTask;

	@Getter
	private final ConcurrentHashMap<UUID, Long> lastMessageTime = new ConcurrentHashMap<>();

	private static final class ReservedOverflow {
		private final UUID playerId;
		private final ArrayList<ItemStack> items;

		private ReservedOverflow(UUID playerId, Collection<ItemStack> items) {
			this.playerId = playerId;
			this.items = new ArrayList<>();
			for (ItemStack item : items) {
				if (item != null) this.items.add(item);
			}
		}
	}

	public FullInventoryHandler(AdvancedCorePlugin plugin) {
		this.plugin = plugin;
		loadTimer();
		startup();
	}

	public void add(UUID uuid, ArrayList<ItemStack> item) {
		addItems(uuid, item);
	}

	public void add(UUID uuid, ItemStack item) {
		if (item == null) {
			return;
		}
		ArrayList<ItemStack> itemList = new ArrayList<>();
		itemList.add(item);
		addItems(uuid, itemList);
	}

	public void check() {
		if (!Bukkit.isPrimaryThread()) {
			plugin.getBukkitScheduler().runTask(plugin, this::schedulePendingPlayerChecks);
			return;
		}
		schedulePendingPlayerChecks();
	}

	public void check(Player player) {
		if (player == null) {
			return;
		}
		plugin.getBukkitScheduler().runTask(plugin, () -> checkOwnedPlayer(player), player);
	}

	public void giveItem(Player player, ItemStack... item) {
		scheduleItemDelivery(player, item, null);
	}

	/**
	 * Gives items on the owning player scheduler and completes after the inventory
	 * mutation, including full-inventory handling, has finished. The established
	 * void API remains fire-and-forget; replay-aware callers use this boundary so
	 * they never checkpoint a queued delivery as completed.
	 */
	public CompletionStage<Void> giveItemAsync(Player player, ItemStack... item) {
		CompletableFuture<Void> completion = new CompletableFuture<>();
		scheduleItemDelivery(player, item, completion);
		return completion;
	}

	private void scheduleItemDelivery(Player player, ItemStack[] item, CompletableFuture<Void> completion) {
		if (player == null || item == null || item.length == 0) {
			if (completion != null) completion.complete(null);
			return;
		}
		UUID playerId = null;
		if (completion != null) {
			try {
				playerId = player.getUniqueId();
				if (playerId == null) {
					completion.completeExceptionally(AdvancedCoreUser.replayActionNotStarted(
							"Item delivery player has no UUID"));
					return;
				}
			} catch (Throwable failure) {
				completion.completeExceptionally(AdvancedCoreUser.replayActionNotStarted(
						"Unable to resolve item delivery player before dispatch", failure));
				return;
			}
		}
		final UUID deliveryPlayerId = playerId;
		final String reservationId = completion == null ? null : UUID.randomUUID().toString();
		ItemStack[] itemsToGive = item.clone();
		AtomicBoolean deliveryClaimed = new AtomicBoolean();
		Runnable delivery = () -> {
			if (!deliveryClaimed.compareAndSet(false, true)) return;
			try {
				if (completion != null) validateReplayDeliveryTarget(player, deliveryPlayerId);
				boolean pendingOverflow = giveItemOwnedPlayer(player, itemsToGive, reservationId);
				if (completion != null) {
					if (pendingOverflow) {
						persistReservedOverflowAsync(reservationId).whenComplete((ignored, failure) -> {
							if (failure == null) completion.complete(null);
							else completeStartedOverflowWithFallback(player, deliveryPlayerId, reservationId, completion);
						});
					} else {
						completion.complete(null);
					}
				}
			} catch (Throwable failure) {
				if (completion != null) completion.completeExceptionally(failure);
				else rethrowDeliveryFailure(failure);
			}
		};
		try {
			plugin.getBukkitScheduler().runTask(plugin, delivery, player);
		} catch (Throwable failure) {
			deliveryClaimed.set(true);
			if (completion != null) completion.completeExceptionally(AdvancedCoreUser.replayActionNotStarted(
					"Scheduler rejected item delivery before dispatch", failure));
			else rethrowDeliveryFailure(failure);
		}
		if (completion == null) return;
		CompletableFuture.delayedExecutor(getItemDeliveryTimeoutMillis(), TimeUnit.MILLISECONDS).execute(() -> {
			if (deliveryClaimed.compareAndSet(false, true)) {
				completion.completeExceptionally(AdvancedCoreUser.replayActionNotStarted(
						"Timed out waiting for item delivery", new TimeoutException()));
			}
		});
	}

	/** Bounds a replay-aware delivery; timeout claims the delivery to prevent a duplicate retry. */
	protected long getItemDeliveryTimeoutMillis() {
		return TimeUnit.SECONDS.toMillis(30);
	}

	/**
	 * Runs on the owning player scheduler immediately before a replay-aware inventory
	 * mutation. A reconnect creates a different player entity, so require both the
	 * captured UUID and entity identity to remain current; a retry can then safely
	 * schedule delivery for the live entity.
	 */
	private void validateReplayDeliveryTarget(Player player, UUID playerId) {
		if (!plugin.isEnabled()) {
			throw AdvancedCoreUser.replayActionNotStarted("Plugin disabled before item delivery");
		}
		Player current = Bukkit.getPlayer(playerId);
		if (current != player || !current.isOnline()) {
			throw AdvancedCoreUser.replayActionNotStarted("Player became unavailable before item delivery");
		}
	}

	private static void rethrowDeliveryFailure(Throwable failure) {
		if (failure instanceof RuntimeException) throw (RuntimeException) failure;
		if (failure instanceof Error) throw (Error) failure;
		throw new IllegalStateException("Failed to schedule item delivery", failure);
	}

	public synchronized void loadTimer() {
		if (timer == null || timer.isShutdown() || timer.isTerminated()) {
			timer = Executors.newSingleThreadScheduledExecutor();
			checkTask = null;
		}
		if (checkTask != null && !checkTask.isDone() && !checkTask.isCancelled()) {
			return;
		}
		checkTask = timer.scheduleAtFixedRate(
				() -> plugin.getBukkitScheduler().runTask(plugin, this::schedulePendingPlayerChecks), 10, 30,
				TimeUnit.SECONDS);
	}

	public synchronized void shutdown() {
		if (checkTask != null) {
			checkTask.cancel(false);
			checkTask = null;
		}
		if (timer != null) {
			timer.shutdownNow();
		}
	}

	/**
	 * Saves pending items to disk. The complete replacement section is first built in
	 * a temporary configuration. Only after that succeeds is the live configuration
	 * replaced in memory, followed by a single disk save. Delivery operations hold a
	 * shared lock, while saving holds the exclusive lock, so a snapshot cannot observe
	 * the temporary remove/re-add state of an in-flight inventory check.
	 */
	public void save() {
		saveDurably();
	}

	/** Saves pending items and reports whether the disk snapshot completed. */
	public boolean saveDurably() {
		deliveryLock.writeLock().lock();
		try {
			HashMap<String, ReservedOverflow> reservations = new HashMap<>(replayOverflowReservations);
			if (!savePendingItemsLocked(reservations.values())) return false;
			for (Entry<String, ReservedOverflow> entry : reservations.entrySet()) {
				promoteReservedOverflowLocked(entry.getKey(), entry.getValue());
			}
			return true;
		} finally {
			deliveryLock.writeLock().unlock();
		}
	}

	/** Persists one replay reservation before making it visible to ordinary pending checks. */
	private CompletionStage<Void> persistReservedOverflowAsync(String reservationId) {
		CompletableFuture<Void> persisted = new CompletableFuture<>();
		try {
			timer.execute(() -> {
				if (persistReservedOverflow(reservationId)) persisted.complete(null);
				else persisted.completeExceptionally(
						new IllegalStateException("Unable to persist full-inventory reward overflow"));
			});
		} catch (Throwable failure) {
			persisted.completeExceptionally(failure);
		}
		return persisted;
	}

	private boolean persistReservedOverflow(String reservationId) {
		deliveryLock.writeLock().lock();
		try {
			ReservedOverflow reservation = replayOverflowReservations.get(reservationId);
			if (reservation == null || reservation.items.isEmpty()) return true;
			if (!savePendingItemsLocked(java.util.List.of(reservation))) return false;
			promoteReservedOverflowLocked(reservationId, reservation);
			return true;
		} finally {
			deliveryLock.writeLock().unlock();
		}
	}

	/**
	 * A save failure happens after Bukkit has already accepted part of this reward.
	 * Complete the replay action only after an owner-thread fallback consumes every
	 * reserved item or a later retry persists the remainder. Reserved items stay
	 * excluded from the normal delivery sweep while either handoff is pending.
	 */
	private void completeStartedOverflowWithFallback(Player player, UUID playerId, String reservationId,
			CompletableFuture<Void> completion) {
		Runnable fallback = () -> {
			if (dropReservedOverflowOwnedPlayer(player, playerId, reservationId)) completion.complete(null);
			else scheduleReservedOverflowPersistenceRetry(reservationId, completion);
		};
		try {
			plugin.getBukkitScheduler().runTask(plugin, fallback, player);
		} catch (Throwable failure) {
			// The mutation has already started, so neither acknowledge it nor fail it as
			// replayable. Keep the stage pending until the reservation is durable.
			scheduleReservedOverflowPersistenceRetry(reservationId, completion);
		}
	}

	/** Runs only on the player owner scheduler. */
	private boolean dropReservedOverflowOwnedPlayer(Player player, UUID playerId, String reservationId) {
		if (player == null || playerId == null || Bukkit.getPlayer(playerId) != player || !player.isOnline()) return false;
		deliveryLock.writeLock().lock();
		try {
			ReservedOverflow reservation = replayOverflowReservations.get(reservationId);
			if (reservation == null) return true;
			if (!playerId.equals(reservation.playerId)) return false;
			ArrayList<ItemStack> remaining = new ArrayList<>();
			for (ItemStack item : reservation.items) {
				try {
					if (player.getWorld().dropItem(player.getLocation(), item) == null) remaining.add(item);
				} catch (Throwable failure) {
					remaining.add(item);
				}
			}
			if (remaining.isEmpty()) replayOverflowReservations.remove(reservationId, reservation);
			else replayOverflowReservations.put(reservationId, new ReservedOverflow(playerId, remaining));
			return remaining.isEmpty();
		} finally {
			deliveryLock.writeLock().unlock();
		}
	}

	private void scheduleReservedOverflowPersistenceRetry(String reservationId, CompletableFuture<Void> completion) {
		if (!replayOverflowReservations.containsKey(reservationId)) {
			completion.complete(null);
			return;
		}
		try {
			timer.schedule(() -> {
				if (persistReservedOverflow(reservationId)) completion.complete(null);
				else scheduleReservedOverflowPersistenceRetry(reservationId, completion);
			}, 30, TimeUnit.SECONDS);
		} catch (Throwable ignored) {
			// Shutdown may reject this retry. Leave the completion pending: acknowledging
			// an in-memory-only remainder would lose it from the durable reward replay.
		}
	}

	/** Caller holds the write lock. Includes only the reservations in this durable handoff. */
	private boolean savePendingItemsLocked(Collection<ReservedOverflow> reservedOverflows) {
		FileConfiguration data = null;
		YamlConfiguration previous = null;
		try {
			ServerData serverData = plugin.getServerDataFile();
			if (serverData == null || serverData.getData() == null) return false;

			HashMap<UUID, ArrayList<ItemStack>> snapshotItems = new HashMap<>();
			for (Entry<UUID, ArrayList<ItemStack>> entry : items.entrySet()) {
				snapshotItems.put(entry.getKey(), new ArrayList<>(entry.getValue()));
			}
			for (ReservedOverflow reservedOverflow : reservedOverflows) {
				if (!reservedOverflow.items.isEmpty()) {
					snapshotItems.computeIfAbsent(reservedOverflow.playerId, ignored -> new ArrayList<>())
						.addAll(reservedOverflow.items);
				}
			}

			YamlConfiguration snapshot = new YamlConfiguration();
			long now = System.currentTimeMillis();
			for (Entry<UUID, ArrayList<ItemStack>> entry : snapshotItems.entrySet()) {
				String basePath = "FullInventory." + entry.getKey();
				for (int i = 0; i < entry.getValue().size(); i++) {
					snapshot.set(basePath + ".Items." + i, entry.getValue().get(i));
				}
				snapshot.set(basePath + ".Time", now);
			}

			data = serverData.getData();
			previous = copySection(data, "FullInventory");
			data.set("FullInventory", null);
			ConfigurationSection replacement = snapshot.getConfigurationSection("FullInventory");
			if (replacement != null) {
				for (String path : replacement.getKeys(true)) {
					if (!replacement.isConfigurationSection(path)) data.set("FullInventory." + path, replacement.get(path));
				}
			}
			serverData.saveData();
			return true;
		} catch (Exception e) {
			if (data != null && previous != null) replaceSection(data, "FullInventory", previous);
			try {
				plugin.getLogger().log(Level.WARNING, "Failed to save pending full-inventory items", e);
			} catch (Throwable ignored) {
				// A partially initialized plugin can still retain the reservation for retry.
			}
			return false;
		}
	}

	private static YamlConfiguration copySection(ConfigurationSection source, String path) {
		YamlConfiguration copy = new YamlConfiguration();
		ConfigurationSection section = source.getConfigurationSection(path);
		if (section == null) return copy;
		for (String child : section.getKeys(true)) {
			if (!section.isConfigurationSection(child)) copy.set(child, section.get(child));
		}
		return copy;
	}

	private static void replaceSection(ConfigurationSection target, String path, YamlConfiguration replacement) {
		target.set(path, null);
		for (String child : replacement.getKeys(true)) {
			if (!replacement.isConfigurationSection(child)) target.set(path + "." + child, replacement.get(child));
		}
	}

	/** Caller holds the write lock. */
	private void promoteReservedOverflowLocked(String reservationId, ReservedOverflow reservation) {
		if (!replayOverflowReservations.remove(reservationId, reservation)) return;
		items.compute(reservation.playerId, (key, current) -> {
			ArrayList<ItemStack> merged = current == null ? new ArrayList<>() : new ArrayList<>(current);
			merged.addAll(reservation.items);
			return merged;
		});
	}

	public void startup() {
		try {
			if (plugin.getServerDataFile() == null || plugin.getServerDataFile().getData() == null) {
				return;
			}
			ConfigurationSection root = plugin.getServerDataFile().getData().getConfigurationSection("FullInventory");
			if (root == null) {
				return;
			}

			long now = System.currentTimeMillis();
			for (String uuidString : root.getKeys(false)) {
				try {
					UUID uuid = UUID.fromString(uuidString);
					long time = root.getLong(uuidString + ".Time");
					if (now - time >= PENDING_ITEM_RETENTION_MS) {
						continue;
					}
					ConfigurationSection itemSection = root.getConfigurationSection(uuidString + ".Items");
					if (itemSection == null) {
						continue;
					}
					for (String itemNumber : itemSection.getKeys(false)) {
						ItemStack item = itemSection.getItemStack(itemNumber);
						if (item != null) {
							add(uuid, item);
						}
					}
				} catch (IllegalArgumentException e) {
					plugin.getLogger().warning("Skipping invalid FullInventory UUID entry: " + uuidString);
				}
			}

			plugin.getServerDataFile().setData("FullInventory", null);
		} catch (Exception e) {
			plugin.getLogger().log(Level.WARNING, "Failed to load pending full-inventory items", e);
		}
	}

	private void addItems(UUID uuid, Collection<ItemStack> itemsToAdd) {
		if (uuid == null || itemsToAdd == null || itemsToAdd.isEmpty()) {
			return;
		}
		deliveryLock.readLock().lock();
		try {
			items.compute(uuid, (key, current) -> {
				ArrayList<ItemStack> merged = current == null ? new ArrayList<>() : new ArrayList<>(current);
				for (ItemStack item : itemsToAdd) {
					if (item != null) {
						merged.add(item);
					}
				}
				return merged.isEmpty() ? null : merged;
			});
		} finally {
			deliveryLock.readLock().unlock();
		}
	}

	private void checkOwnedPlayer(Player player) {
		deliveryLock.readLock().lock();
		try {
			UUID uuid = player.getUniqueId();
			ArrayList<ItemStack> pending = items.remove(uuid);
			if (pending == null || pending.isEmpty()) {
				return;
			}

			ArrayList<ItemStack> extra = new ArrayList<>();
			for (ItemStack item : pending) {
				if (item == null) {
					continue;
				}
				HashMap<Integer, ItemStack> excess = player.getInventory().addItem(item);
				extra.addAll(excess.values());
			}
			if (!extra.isEmpty()) {
				addItems(uuid, extra);
			}
		} finally {
			deliveryLock.readLock().unlock();
		}
	}

	private boolean giveItemOwnedPlayer(Player player, ItemStack[] item, String reservationId) {
		deliveryLock.readLock().lock();
		try {
			HashMap<Integer, ItemStack> excess = player.getInventory().addItem(item);
			if (excess.isEmpty()) {
				player.updateInventory();
				return false;
			}

			boolean dropItems = plugin.getOptions().isDropOnFullInv();
			if (dropItems) {
				if (reservationId == null) {
					for (ItemStack extra : excess.values()) player.getWorld().dropItem(player.getLocation(), extra);
				} else {
					ReservedOverflow reservation = new ReservedOverflow(player.getUniqueId(), excess.values());
					replayOverflowReservations.put(reservationId, reservation);
					ArrayList<ItemStack> undropped = new ArrayList<>();
					for (ItemStack extra : reservation.items) {
						try {
							if (player.getWorld().dropItem(player.getLocation(), extra) == null) undropped.add(extra);
						} catch (Throwable failure) {
							undropped.add(extra);
						}
					}
					if (undropped.isEmpty()) replayOverflowReservations.remove(reservationId, reservation);
					else replayOverflowReservations.put(reservationId,
							new ReservedOverflow(player.getUniqueId(), undropped));
				}
			} else if (reservationId != null) {
				replayOverflowReservations.put(reservationId,
						new ReservedOverflow(player.getUniqueId(), excess.values()));
			} else {
				for (ItemStack extra : excess.values()) add(player.getUniqueId(), extra);
			}

			if (shouldSendMessage(player.getUniqueId())) {
				sendMessage(player);
			}
			player.updateInventory();
			return reservationId != null && replayOverflowReservations.containsKey(reservationId);
		} finally {
			deliveryLock.readLock().unlock();
		}
	}

	private void schedulePendingPlayerChecks() {
		long now = System.currentTimeMillis();
		for (UUID uuid : new ArrayList<>(items.keySet())) {
			Player player = Bukkit.getPlayer(uuid);
			if (player != null) {
				plugin.getBukkitScheduler().runTask(plugin, () -> checkOwnedPlayer(player), player);
			}
			Long lastMessage = lastMessageTime.get(uuid);
			if (lastMessage != null && now - lastMessage.longValue() > MESSAGE_COOLDOWN_MS) {
				lastMessageTime.remove(uuid, lastMessage);
			}
		}
	}

	private boolean shouldSendMessage(UUID uuid) {
		Long lastMessage = lastMessageTime.get(uuid);
		return lastMessage == null || System.currentTimeMillis() - lastMessage.longValue() > MESSAGE_COOLDOWN_MS;
	}

	private void sendMessage(Player player) {
		String msg = MessageAPI.colorize(plugin.getOptions().getFormatInvFull());
		if (!msg.isEmpty()) {
			player.sendMessage(msg);
			lastMessageTime.put(player.getUniqueId(), System.currentTimeMillis());
		}
	}
}
