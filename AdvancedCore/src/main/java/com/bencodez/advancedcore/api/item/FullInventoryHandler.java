package com.bencodez.advancedcore.api.item;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.data.ServerData;
import com.bencodez.simpleapi.messages.MessageAPI;

import lombok.Getter;

public class FullInventoryHandler {
	private static final long MESSAGE_COOLDOWN_MS = 5000L;
	private static final long PENDING_ITEM_RETENTION_MS = TimeUnit.DAYS.toMillis(1);

	@Getter
	private final ConcurrentHashMap<UUID, ArrayList<ItemStack>> items = new ConcurrentHashMap<>();

	private final AdvancedCorePlugin plugin;

	@Getter
	private ScheduledExecutorService timer;

	private ScheduledFuture<?> checkTask;

	@Getter
	private final ConcurrentHashMap<UUID, Long> lastMessageTime = new ConcurrentHashMap<>();

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
		if (player == null || item == null || item.length == 0) {
			return;
		}
		ItemStack[] itemsToGive = item.clone();
		plugin.getBukkitScheduler().runTask(plugin, () -> giveItemOwnedPlayer(player, itemsToGive), player);
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
	 * Builds the complete replacement section before changing the live config, then
	 * persists it with one save. This avoids deliberately writing an empty/partial
	 * recovery snapshot while the replacement is still being constructed.
	 */
	public void save() {
		try {
			ServerData serverData = plugin.getServerDataFile();
			if (serverData == null || serverData.getData() == null) {
				return;
			}

			YamlConfiguration snapshot = new YamlConfiguration();
			long now = System.currentTimeMillis();
			for (Entry<UUID, ArrayList<ItemStack>> entry : items.entrySet()) {
				ArrayList<ItemStack> pending = new ArrayList<>(entry.getValue());
				String basePath = "FullInventory." + entry.getKey();
				for (int i = 0; i < pending.size(); i++) {
					snapshot.set(basePath + ".Items." + i, pending.get(i));
				}
				snapshot.set(basePath + ".Time", now);
			}

			FileConfiguration data = serverData.getData();
			data.set("FullInventory", null);
			ConfigurationSection replacement = snapshot.getConfigurationSection("FullInventory");
			if (replacement != null) {
				for (String path : replacement.getKeys(true)) {
					if (!replacement.isConfigurationSection(path)) {
						data.set("FullInventory." + path, replacement.get(path));
					}
				}
			}
			serverData.saveData();
		} catch (Exception e) {
			plugin.getLogger().log(Level.WARNING, "Failed to save pending full-inventory items", e);
		}
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

	private void giveItemOwnedPlayer(Player player, ItemStack[] item) {
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
