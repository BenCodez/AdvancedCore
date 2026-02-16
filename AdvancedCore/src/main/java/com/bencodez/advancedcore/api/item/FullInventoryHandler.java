package com.bencodez.advancedcore.api.item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.simpleapi.messages.MessageAPI;

import lombok.Getter;

/**
 * Handler for items when player inventories are full.
 */
public class FullInventoryHandler {
	/**
	 * The items waiting to be given.
	 * 
	 * @return the items waiting to be given
	 */
	@Getter
	private ConcurrentHashMap<UUID, ArrayList<ItemStack>> items;

	private AdvancedCorePlugin plugin;

	/**
	 * The timer executor service.
	 * 
	 * @return the timer executor service
	 */
	@Getter
	private ScheduledExecutorService timer;

	/**
	 * The last message time for each player.
	 * 
	 * @return the last message time for each player
	 */
	@Getter
	private ConcurrentHashMap<UUID, Long> lastMessageTime;

	/**
	 * Constructor for FullInventoryHandler.
	 *
	 * @param plugin the plugin instance
	 */
	public FullInventoryHandler(AdvancedCorePlugin plugin) {
		items = new ConcurrentHashMap<>();
		lastMessageTime = new ConcurrentHashMap<>();
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
		if (items.containsKey(uuid)) {
			ArrayList<ItemStack> current = items.get(uuid);
			current.addAll(item);
			items.put(null, current);
		} else {
			items.put(uuid, item);
		}
	}

	/**
	 * Adds a single item for a player.
	 *
	 * @param uuid the player UUID
	 * @param item the item to add
	 */
	public void add(UUID uuid, ItemStack item) {
		if (items.containsKey(uuid)) {
			ArrayList<ItemStack> current = items.get(uuid);
			current.add(item);
			items.put(uuid, current);
		} else {
			ArrayList<ItemStack> itemList = new ArrayList<>();
			itemList.add(item);
			items.put(uuid, itemList);
		}
	}

	/**
	 * Checks all players for pending items.
	 */
	public void check() {
		for (UUID entry : items.keySet()) {
			Player p = Bukkit.getPlayer(entry);
			check(p);
			if (lastMessageTime.containsKey(entry)) {
				if ((System.currentTimeMillis() - lastMessageTime.get(p.getUniqueId()).longValue()) > 5000) {
					lastMessageTime.remove(entry);
				}
			}
		}
	}

	/**
	 * Checks a specific player for pending items.
	 *
	 * @param p the player
	 */
	public void check(Player p) {
		if (p != null && items.containsKey(p.getUniqueId())) {
			ArrayList<ItemStack> extra = new ArrayList<>();
			for (ItemStack item : items.get(p.getUniqueId())) {
				HashMap<Integer, ItemStack> excess = p.getInventory().addItem(item);
				for (Map.Entry<Integer, ItemStack> me : excess.entrySet()) {
					extra.add(me.getValue());
				}
			}
			if (extra.size() == 0) {
				items.remove(p.getUniqueId());
			} else {
				items.put(p.getUniqueId(), extra);
			}
		}
	}

	/**
	 * Gives items to a player.
	 *
	 * @param p the player
	 * @param item the items to give
	 */
	public void giveItem(Player p, ItemStack... item) {
		HashMap<Integer, ItemStack> excess = p.getInventory().addItem(item);
		boolean full = false;
		boolean dropItems = plugin.getOptions().isDropOnFullInv();

		for (Map.Entry<Integer, ItemStack> me : excess.entrySet()) {
			full = true;
			if (dropItems) {
				p.getWorld().dropItem(p.getLocation(), me.getValue());
			} else {
				add(p.getUniqueId(), me.getValue());
			}
		}
		if (full) {
			if (lastMessageTime.containsKey(p.getUniqueId())) {
				if ((System.currentTimeMillis() - lastMessageTime.get(p.getUniqueId()).longValue()) > 5000) {
					sendMessage(p);
				}
			} else {
				sendMessage(p);
			}

		}

		p.updateInventory();
	}

	/**
	 * Loads the timer for checking inventories.
	 */
	public void loadTimer() {
		timer = Executors.newScheduledThreadPool(1);
		timer.scheduleAtFixedRate(new Runnable() {

			@Override
			public void run() {
				check();
			}
		}, 10, 30, TimeUnit.SECONDS);
	}

	/**
	 * Saves pending items to disk.
	 */
	public void save() {
		try {
			if (plugin.getServerDataFile().getData() == null) {
				return;
			}
			for (Entry<UUID, ArrayList<ItemStack>> entry : items.entrySet()) {
				ArrayList<ItemStack> items = entry.getValue();
				for (int i = 0; i < items.size(); i++) {
					plugin.getServerDataFile().setData("FullInventory." + entry.getKey().toString() + ".Items." + i,
							items.get(i));
				}
				plugin.getServerDataFile().setData("FullInventory." + entry.getKey().toString() + ".Time",
						System.currentTimeMillis());
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void sendMessage(Player p) {
		String msg = MessageAPI.colorize(AdvancedCorePlugin.getInstance().getOptions().getFormatInvFull());
		if (!msg.isEmpty()) {
			p.sendMessage(msg);
			lastMessageTime.put(p.getUniqueId(), System.currentTimeMillis());
		}
	}

	/**
	 * Loads pending items from disk on startup.
	 */
	public void startup() {
		try {
			if (plugin.getServerDataFile().getData() == null
					|| !plugin.getServerDataFile().getData().isConfigurationSection("FullInventory")) {
				return;
			}

			for (String uuid : plugin.getServerDataFile().getData().getConfigurationSection("FullInventory")
					.getKeys(false)) {

				// check time to keep a lot of items from long time offline players
				long time = plugin.getServerDataFile().getData().getLong("FullInventory." + uuid + ".Time");
				if (System.currentTimeMillis() - time < (1000 * 60 * 60 * 24)) {

					for (String itemnum : plugin.getServerDataFile().getData()
							.getConfigurationSection("FullInventory." + uuid + ".Items").getKeys(false)) {
						add(UUID.fromString(uuid), plugin.getServerDataFile().getData()
								.getItemStack("FullInventory." + uuid + ".Items." + itemnum));
					}
				}
			}

			plugin.getServerDataFile().setData("FullInventory", null);
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

}
