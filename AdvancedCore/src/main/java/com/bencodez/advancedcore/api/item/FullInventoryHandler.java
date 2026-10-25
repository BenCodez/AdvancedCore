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
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
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
import com.bencodez.advancedcore.api.rewards.RewardEligibility;
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
	private final ConcurrentHashMap<String, CompletableFuture<Void>> replayOverflowCompletions = new ConcurrentHashMap<>();

	private final AdvancedCorePlugin plugin;
	private final ReentrantReadWriteLock deliveryLock = new ReentrantReadWriteLock(true);
	private final AtomicBoolean shuttingDown = new AtomicBoolean();
	private final java.util.concurrent.atomic.AtomicReference<CompletionStage<Void>> shutdownPersistence =
			new java.util.concurrent.atomic.AtomicReference<>();

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
		if (shuttingDown.get()) return;
		if (!Bukkit.isPrimaryThread()) {
			plugin.getBukkitScheduler().runTask(plugin, this::schedulePendingPlayerChecks);
			return;
		}
		schedulePendingPlayerChecks();
	}

	public void check(Player player) {
		if (player == null || shuttingDown.get()) {
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
		return giveItemWhenEligibleAsync(player, null, item);
	}

	/** Replay-aware delivery with an optional final owner-thread eligibility check. */
	public CompletionStage<Void> giveItemWhenEligibleAsync(Player player, BooleanSupplier eligibility, ItemStack... item) {
		CompletableFuture<Void> completion = new CompletableFuture<>();
		scheduleItemDelivery(player, item, completion, eligibility);
		return completion;
	}

	private void scheduleItemDelivery(Player player, ItemStack[] item, CompletableFuture<Void> completion) {
		scheduleItemDelivery(player, item, completion, null);
	}

	private void scheduleItemDelivery(Player player, ItemStack[] item, CompletableFuture<Void> completion,
			BooleanSupplier eligibility) {
		if (player == null || item == null || item.length == 0) {
			if (completion != null) completion.complete(null);
			return;
		}
		if (completion != null && shuttingDown.get()) {
			completion.completeExceptionally(AdvancedCoreUser.replayActionNotStarted(
					"Full-inventory handler is shutting down before item delivery"));
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
			boolean replayDeliveryLocked = false;
			try {
				if (completion != null) {
					deliveryLock.readLock().lock();
					replayDeliveryLocked = true;
				}
				if (completion != null) validateReplayDeliveryTarget(player, deliveryPlayerId, eligibility != null);
				// Check only before the initial mutation. Retained overflow is already
				// committed and must remain deliverable if eligibility later changes.
				if (eligibility != null && !eligibility.getAsBoolean()) {
					throw AdvancedCoreUser.replayActionNotStarted(
							"Player reward context became unavailable before item mutation",
							com.bencodez.advancedcore.api.rewards.Reward.playerEligibilityDeferred());
				}
				if (completion != null) replayOverflowCompletions.put(reservationId, completion);
				boolean pendingOverflow = giveItemOwnedPlayer(player, itemsToGive, reservationId);
				if (completion != null) {
					if (pendingOverflow) {
						persistReservedOverflowAsync(reservationId).whenComplete((ignored, failure) -> {
							if (failure == null) completeReplayOverflow(reservationId, completion);
							else if (failure instanceof UnconfirmedOverflowPublication)
								scheduleReservedOverflowPersistenceRetry(reservationId, completion);
							else completeStartedOverflowWithFallback(player, deliveryPlayerId, reservationId, completion);
						});
					} else {
						replayOverflowCompletions.remove(reservationId, completion);
						completion.complete(null);
					}
				}
			} catch (Throwable failure) {
				if (completion != null) {
					replayOverflowCompletions.remove(reservationId, completion);
					completion.completeExceptionally(failure);
				}
				else rethrowDeliveryFailure(failure);
			} finally {
				if (replayDeliveryLocked) deliveryLock.readLock().unlock();
			}
		};
		try {
			if (completion != null && eligibility != null) {
				RewardEligibility.runPlayerTask(plugin, player, delivery, () -> {
					if (deliveryClaimed.compareAndSet(false, true)) {
						completion.completeExceptionally(RewardEligibility.retiredPlayerActionFailure(plugin));
					}
				}, failure -> {
					if (deliveryClaimed.compareAndSet(false, true)) {
						completion.completeExceptionally(AdvancedCoreUser.replayActionNotStarted(
								"Scheduler rejected item delivery before dispatch", failure));
					}
				});
			} else plugin.getBukkitScheduler().runTask(plugin, delivery, player);
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
	private void validateReplayDeliveryTarget(Player player, UUID playerId, boolean eligibilityDeferral) {
		if (!plugin.isEnabled()) {
			throw AdvancedCoreUser.replayActionNotStarted("Plugin disabled before item delivery");
		}
		Player current = Bukkit.getPlayer(playerId);
		if (current != player || !current.isOnline()) {
			throw AdvancedCoreUser.replayActionNotStarted("Player became unavailable before item delivery",
					eligibilityDeferral ? com.bencodez.advancedcore.api.rewards.Reward.playerEligibilityDeferred() : null);
		}
	}

	private static void rethrowDeliveryFailure(Throwable failure) {
		if (failure instanceof RuntimeException) throw (RuntimeException) failure;
		if (failure instanceof Error) throw (Error) failure;
		throw new IllegalStateException("Failed to schedule item delivery", failure);
	}

	public synchronized void loadTimer() {
		if (timer == null || timer.isShutdown() || timer.isTerminated()) {
			timer = Executors.newSingleThreadScheduledExecutor(
					com.bencodez.advancedcore.core.runtime.AdvancedCoreRuntime.daemonThreads("AdvancedCore-FullInventory"));
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
		// Reuse the same final publication for legacy and asynchronous shutdown.
		// A second shutdown must not retire a worker needed by an earlier save.
		shutdownAsync(Runnable::run).whenComplete((ignored, failure) -> {
			if (failure != null) plugin.getLogger().log(Level.WARNING,
					"Failed to confirm pending full-inventory items", failure);
		});
	}

	/**
	 * Fences player delivery immediately and exposes the actual final disk save.
	 * The caller owns the persistence executor; pending or failed saves never
	 * acknowledge replay overflow and must keep storage retirement ordered behind it.
	 */
	public CompletionStage<Void> shutdownAsync(Executor persistenceExecutor) {
		CompletableFuture<Void> completion = new CompletableFuture<>();
		// Keep one shared observation stage; each CompletableFuture view is isolated.
		CompletionStage<Void> observation = completion.minimalCompletionStage();
		if (!shutdownPersistence.compareAndSet(null, observation)) return shutdownPersistence.get();
		shuttingDown.set(true);
		ScheduledFuture<?> task = checkTask;
		if (task != null) task.cancel(false);
		// Reject new timer work immediately, but drain already accepted continuations.
		if (timer != null) timer.shutdown();
		ServerData serverData = plugin.getServerDataFile();
		if (!plugin.isLoadServerData() && (serverData == null || serverData.getData() == null)) {
			// No native file was loaded. Still fence delivery and retire its timer,
			// but do not snapshot or acknowledge any unpublished replay overflow.
			if (timer != null) timer.shutdownNow();
			completion.complete(null);
			return observation;
		}
		try {
			persistenceExecutor.execute(() -> {
				try {
					finalSnapshot(serverData, false, persistenceExecutor).whenComplete((ignored, failure) -> {
						if (timer != null) timer.shutdownNow();
						if (failure == null) completion.complete(null);
						else completion.completeExceptionally(failure);
					});
				} catch (Throwable failure) {
					if (timer != null) timer.shutdownNow();
					completion.completeExceptionally(failure);
				}
			});
		} catch (Throwable failure) {
			completion.completeExceptionally(failure);
			if (timer != null) timer.shutdownNow();
		}
		return observation;
	}

	private final java.util.Map<String, Integer> overflowPublications = new HashMap<>();

	private final Object overflowPublicationOrder = new Object();
	private CompletableFuture<Void> overflowPublicationTail = CompletableFuture.completedFuture(null);

	private CompletionStage<Void> finalSnapshot(ServerData serverData, boolean requireRunning) {
		return finalSnapshot(serverData, requireRunning, timer);
	}

	private CompletionStage<Void> finalSnapshot(ServerData serverData, boolean requireRunning, Executor executor) {
		CompletableFuture<Void> settled = new CompletableFuture<>();
		CompletableFuture<Void> previous;
		synchronized (overflowPublicationOrder) {
			previous = overflowPublicationTail;
			overflowPublicationTail = settled;
		}
		Runnable publish = () -> {
			try {
				publishSnapshot(serverData, requireRunning).whenComplete((ignored, failure) -> {
					if (failure == null) settled.complete(null);
					else settled.completeExceptionally(failure);
				});
			} catch (Throwable failure) { settled.completeExceptionally(failure); }
		};
		// Claim order before invoking any owner or callback. Snapshot capture belongs
		// to that order too: an older asynchronous replacement must not publish last.
		if (previous.isDone()) publish.run();
		else previous.whenComplete((ignored, failure) -> {
			// Owner completion may run on a platform thread; never start disk work there.
			try { executor.execute(publish); }
			catch (Throwable rejected) { settled.completeExceptionally(rejected); }
		});
		// A caller cancelling its view must not release the actual publication queue.
		return settled.copy();
	}

	private CompletionStage<Void> publishSnapshot(ServerData serverData, boolean requireRunning) {
		HashMap<String, ReservedOverflow> reservations;
		CompletionStage<Void> publication;
		deliveryLock.writeLock().lock();
		try {
			if (requireRunning && shuttingDown.get()) return CompletableFuture.failedFuture(
					new IllegalStateException("Final inventory publication owns pending overflow"));
			reservations = new HashMap<>(replayOverflowReservations);
			YamlConfiguration snapshot = pendingItemsSnapshot(reservations.values());
			YamlConfiguration replacement = copySection(snapshot, "FullInventory");
			reservations.keySet().forEach(id -> overflowPublications.merge(id, 1, Integer::sum));
			try { publication = serverData.mutateFinalData(data -> replaceSection(data, "FullInventory", replacement)); }
			catch (Throwable failure) { publication = CompletableFuture.failedFuture(failure); }
			if (publication == null) publication = CompletableFuture.failedFuture(
					new IllegalStateException("ServerData owner returned no publication stage"));
		} finally { deliveryLock.writeLock().unlock(); }
		// Publication may belong to a different worker. Never join while holding
		// delivery/YAML admission. Settle promotion and fallback ownership together.
		return publication.whenComplete((ignored, failure) -> {
			deliveryLock.writeLock().lock();
			try {
				reservations.keySet().forEach(id -> overflowPublications.computeIfPresent(id,
						(key, count) -> count == 1 ? null : count - 1));
				if (failure == null) reservations.forEach(this::promoteReservedOverflowLocked);
			} finally { deliveryLock.writeLock().unlock(); }
			if (failure == null) reservations.keySet().forEach(id -> completeReplayOverflow(id, null));
		});
	}

	/** Caller holds delivery admission; only pending stored items are serialized. */
	private YamlConfiguration pendingItemsSnapshot(Collection<ReservedOverflow> reservedOverflows) {
		HashMap<UUID, ArrayList<ItemStack>> snapshotItems = new HashMap<>();
		items.forEach((uuid, values) -> snapshotItems.put(uuid, new ArrayList<>(values)));
		for (ReservedOverflow overflow : reservedOverflows) {
			if (!overflow.items.isEmpty()) snapshotItems.computeIfAbsent(overflow.playerId,
					ignored -> new ArrayList<>()).addAll(overflow.items);
		}
		YamlConfiguration snapshot = new YamlConfiguration();
		long now = System.currentTimeMillis();
		for (Entry<UUID, ArrayList<ItemStack>> entry : snapshotItems.entrySet()) {
			String path = "FullInventory." + entry.getKey();
			for (int i = 0; i < entry.getValue().size(); i++) snapshot.set(path + ".Items." + i, entry.getValue().get(i));
			snapshot.set(path + ".Time", now);
		}
		return snapshot;
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
		return saveDurably(plugin.getServerDataFile());
	}

	private boolean saveDurably(ServerData serverData) {
		try {
			// Native owners complete synchronously. A facade's queued save is not
			// durable yet; do not block the caller or claim that it has completed.
			var publication = finalSnapshot(serverData, false).toCompletableFuture();
			if (!publication.isDone()) return false;
			publication.join();
			return true;
		} catch (Throwable failure) {
			plugin.getLogger().log(Level.WARNING, "Failed to confirm pending full-inventory items", failure);
			return false;
		}
	}

	/** A failed owner stage may already have replaced disk bytes; never drop/replay it. */
	private static final class UnconfirmedOverflowPublication extends RuntimeException {
		private static final long serialVersionUID = 1L;
		private UnconfirmedOverflowPublication(Throwable cause) { super("Overflow publication is unconfirmed", cause); }
	}

	/** Persists replay overflow through the actual owner's acknowledged publication. */
	private CompletionStage<Void> persistReservedOverflowAsync(String reservationId) {
		CompletableFuture<Void> persisted = new CompletableFuture<>();
		try {
			timer.execute(() -> {
				try {
					persistReservedOverflow(reservationId).whenComplete((ignored, failure) -> {
						if (failure == null) persisted.complete(null);
						else persisted.completeExceptionally(new UnconfirmedOverflowPublication(failure));
					});
				} catch (Throwable failure) { persisted.completeExceptionally(failure); }
			});
		} catch (Throwable failure) { persisted.completeExceptionally(failure); }
		return persisted;
	}

	private CompletionStage<Void> persistReservedOverflow(String reservationId) {
		deliveryLock.writeLock().lock();
		try {
			ReservedOverflow reservation = replayOverflowReservations.get(reservationId);
			if (reservation == null || reservation.items.isEmpty())
				return CompletableFuture.completedFuture(null);
			if (shuttingDown.get()) return CompletableFuture.failedFuture(
					new IllegalStateException("Final inventory publication owns pending overflow"));
		} finally { deliveryLock.writeLock().unlock(); }
		// A disabled/uninitialized native file has not admitted any publication.
		// Keep its established safe overflow fallback instead of treating it as uncertain.
		ServerData owner = plugin.getServerDataFile();
		if (owner == null || owner.getData() == null)
			throw new IllegalStateException("Full-inventory persistence owner is uninitialized");
		// Include every unpublished reservation. Invoke completion callbacks only
		// after releasing this caller's admission, including a synchronous owner.
		return finalSnapshot(owner, true);
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
			if (dropReservedOverflowOwnedPlayer(player, playerId, reservationId)) {
				completeReplayOverflow(reservationId, completion);
			}
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
			// A pending final publication already owns these items. An older queued
			// fallback cannot drop them while the owner's snapshot is publishing.
			if (shuttingDown.get() || overflowPublications.containsKey(reservationId)) return false;
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
			completeReplayOverflow(reservationId, completion);
			return;
		}
		try {
			timer.schedule(() -> {
				try {
					persistReservedOverflow(reservationId).whenComplete((ignored, failure) -> {
						if (failure == null) completeReplayOverflow(reservationId, completion);
						else scheduleReservedOverflowPersistenceRetry(reservationId, completion);
					});
				} catch (Throwable failure) { scheduleReservedOverflowPersistenceRetry(reservationId, completion); }
			}, 30, TimeUnit.SECONDS);
		} catch (Throwable ignored) {
			// Shutdown may reject this retry. Leave the completion pending: acknowledging
			// an in-memory-only remainder would lose it from the durable reward replay.
		}
	}

	private void completeReplayOverflow(String reservationId, CompletableFuture<Void> fallbackCompletion) {
		CompletableFuture<Void> completion = replayOverflowCompletions.remove(reservationId);
		if (completion == null) completion = fallbackCompletion;
		if (completion != null) completion.complete(null);
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
			if (shuttingDown.get()) return;
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
			if (reservationId != null && shuttingDown.get()) {
				throw AdvancedCoreUser.replayActionNotStarted(
						"Full-inventory handler shut down before queued item delivery began");
			}
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
		if (shuttingDown.get()) return;
		long now = System.currentTimeMillis();
		for (UUID uuid : new ArrayList<>(items.keySet())) {
			if (shuttingDown.get()) return;
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
