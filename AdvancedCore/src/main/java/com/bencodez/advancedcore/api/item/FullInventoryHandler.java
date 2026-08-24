package com.bencodez.advancedcore.api.item;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.simpleapi.messages.MessageAPI;

import lombok.Getter;

/**
 * Handler for items when player inventories are full.
 */
public class FullInventoryHandler {
	private static final long MESSAGE_COOLDOWN_MS = 5000L;
	private static final long PENDING_ITEM_RETENTION_MS = TimeUnit.DAYS.toMillis(1);

	/**
	 * The items waiting to be given.
	 *
	 * @return the items waiting to be given
	 */
	@Getter
	private final ConcurrentHashMap<UUID, ArrayList<ItemStack>> items = new ConcurrentHashMap<>();

	private final AdvancedCorePlugin plugin;

	/**
	 * The shared inventory timer executor service.
	 *
	 * @return the timer executor service
	 */
	@Getter
	private ScheduledExecutorService timer;

	private ScheduledFuture<?> checkTask;

	/**
	 * The last message time for each player.
	 *
	 * @return the last message time for each player
	 */
	@Getter
	private final ConcurrentHashMap<UUID, Long> lastMessageTime = new ConcurrentHashMap<>();

	/**
	 * Constructor for FullInventoryHandler.
	 *
	 * @param plugin the plugin instance
	 */
	public FullInventoryHandler(AdvancedCorePlugin plugin) {
		this.plugin = plugin;
		loadTimer();
		startup();
	}

	/**
	 * Adds multiple items for a player.
	 *
	 * @param uuid the player UUID
	 * @param item the items to add
	 */
	public void add(UUID uuid, ArrayList<ItemStack> item) {
		addItems(uuid, item);
	}

	/**
	 * Adds a single item for a player.
	 *
	 * @param uuid the player UUID
	 * @param item the item to add
	 */
	public void add(UUID uuid, ItemStack item) {
		if (item == null) {
			return;
		}
		ArrayList<ItemStack> itemList = new ArrayList<>();
		itemList.add(item);
		addItems(uuid, itemList);
	}

	/**
	 * Checks all players for pending items. The sweep itself runs on the normal
	 * Bukkit/global scheduler, but every player inventory operation is scheduled on
	 * that player's scheduler/region before it is executed.
	 */
	public void check() {
		if (!Bukkit.isPrimaryThread()) {
			plugin.getBukkitScheduler().runTask(plugin, this::schedulePendingPlayerChecks);
			return;
		}
		schedulePendingPlayerChecks();
	}

	/**
	 * Checks a specific player for pending items.
	 *
	 * @param player the player
	 */
	public void check(Player player) {
		if (player == null) {
			return;
		}
		if (!Bukkit.isPrimaryThread()) {
			plugin.getBukkitScheduler().runTask(plugin, () -> checkOwnedPlayer(player), player);
			return;
		}
		checkOwnedPlayer(player);
	}

	/**
	 * Gives items to a player. Bukkit inventory/world operations are moved to the
	 * Bukkit scheduler when called asynchronously.
	 *
	 * @param player the player
	 * @param item the items to give
	 */
	public void giveItem(Player player, ItemStack... item) {
		if (player == null || item == null || item.length == 0) {
			return;
		}
		if (!Bukkit.isPrimaryThread()) {
			ItemStack[] itemsToGive = item.clone();
			plugin.getBukkitScheduler().runTask(plugin, () -> giveItem(player, itemsToGive), player);
			return;
		}

		HashMap<Integer, ItemStack> excess = player.getInventory().addItem(item);
		if (excess.isEmpty()) {
			player.updateInventory();
			return;
		}

		boolean dropItems = plugin.getOptions().isDropOnFullInv();
		for (ItemStack extra : excess.values()) {
			if (dropItems) {
				player.getWorld().dropItem(player.getLocation(), extra);
			} else {
				add(player.getUniqueId(), extra);
			}
		}

		if (shouldSendMessage(player.getUniqueId())) {
			sendMessage(player);
		}
		player.updateInventory();
	}

	/**
	 * Loads the timer for checking inventories. The handler reuses AdvancedCore's
	 * inventory executor and schedules Bukkit work back through the Bukkit scheduler.
	 */
	public synchronized void loadTimer() {
		if (checkTask != null && !checkTask.isDone() && !checkTask.isCancelled()) {
			return;
		}
		timer = plugin.getInventoryTimer();
		if (timer == null || timer.isShutdown()) {
			return;
		}
		checkTask = timer.scheduleAtFixedRate(
				() -> plugin.getBukkitScheduler().runTask(plugin, this::schedulePendingPlayerChecks), 10, 30,
				TimeUnit.SECONDS);
	}

	/**
	 * Stops this handler's repeating task without shutting down the shared inventory
	 * executor.
	 */
	public synchronized void shutdown() {
		if (checkTask != null) {
			checkTask.cancel(false);
			checkTask = null;
		}
	}

	/**
	 * Saves pending items to disk.
	 */
	public void save() {
		try {
			if (plugin.getServerDataFile() == null || plugin.getServerDataFile().getData() == null) {
				return;
			}
			plugin.getServerDataFile().setData("FullInventory", null);
			for (Entry<UUID, ArrayList<ItemStack>> entry : items.entrySet()) {
				ArrayList<ItemStack> pending = new ArrayList<>(entry.getValue());
				for (int i = 0; i < pending.size(); i++) {
					plugin.getServerDataFile().setData("FullInventory." + entry.getKey() + ".Items." + i, pending.get(i));
				}
				plugin.getServerDataFile().setData("FullInventory." + entry.getKey() + ".Time", System.currentTimeMillis());
			}
		} catch (Exception e) {
			plugin.getLogger().log(Level.WARNING, "Failed to save pending full-inventory items", e);
		}
	}

	/**
	 * Loads pending items from disk on startup.
	 */
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
		items.compute(uuid, (key, current) -> {
			ArrayList<ItemStack> merged = current == null ? new ArrayList<>() : new ArrayList<>(current);
			for (ItemStack item : itemsToAdd) {
				if (item != null) {
					merged.add(item);
				}
			}
			return merged.isEmpty() ? null : merged;
		});
	}

	private void checkOwnedPlayer(Player player) {
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
