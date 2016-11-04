/* Obtained from https://www.spigotmc.org/threads/libish-inventory-api-kinda.49339/
 * and modified
 */

package com.Ben12345rocks.AdvancedCore.Util.Inventory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.Ben12345rocks.AdvancedCore.Main;
import com.Ben12345rocks.AdvancedCore.Utils;

// TODO: Auto-generated Javadoc
/**
 * The Class BInventory.
 */
public class BInventory implements Listener {

	/**
	 * The Class ClickEvent.
	 */
	public class ClickEvent {

		/** The player. */
		private Player player;

		/** The event. */
		private InventoryClickEvent event;

		/** The click type. */
		private ClickType clickType;

		/** The inventory. */
		private Inventory inventory;

		/** The slot. */
		private int slot;

		/** The clicked item. */
		private ItemStack clickedItem;

		/**
		 * Instantiates a new click event.
		 *
		 * @param event
		 *            the event
		 */
		public ClickEvent(InventoryClickEvent event) {
			this.event = event;
			player = (Player) event.getWhoClicked();
			clickType = event.getClick();
			inventory = event.getInventory();
			clickedItem = event.getCurrentItem();
			slot = event.getSlot();
		}

		/**
		 * Gets the click.
		 *
		 * @return the click
		 */
		public ClickType getClick() {
			return clickType;
		}

		/**
		 * Gets the clicked item.
		 *
		 * @return the clicked item
		 */
		public ItemStack getClickedItem() {
			return clickedItem;
		}

		/**
		 * Gets the click type.
		 *
		 * @return the click type
		 */
		public ClickType getClickType() {
			return clickType;
		}

		/**
		 * Gets the current item.
		 *
		 * @return the current item
		 */
		public ItemStack getCurrentItem() {
			return clickedItem;
		}

		/**
		 * Gets the event.
		 *
		 * @return the event
		 */
		public InventoryClickEvent getEvent() {
			return event;
		}

		/**
		 * Gets the inventory.
		 *
		 * @return the inventory
		 */
		public Inventory getInventory() {
			return inventory;
		}

		/**
		 * Gets the meta.
		 *
		 * @param player
		 *            the player
		 * @param str
		 *            the str
		 * @return the meta
		 */
		public Object getMeta(Player player, String str) {
			return Utils.getInstance().getPlayerMeta(player, str);
		}

		/**
		 * Gets the meta.
		 *
		 * @param str
		 *            the str
		 * @return the meta
		 */
		public Object getMeta(String str) {
			return Utils.getInstance().getPlayerMeta(player, str);
		}

		/**
		 * Gets the player.
		 *
		 * @return the player
		 */
		public Player getPlayer() {
			return player;
		}

		/**
		 * Gets the slot.
		 *
		 * @return the slot
		 */
		public int getSlot() {
			return slot;
		}

		/**
		 * Gets the who clicked.
		 *
		 * @return the who clicked
		 */
		public Player getWhoClicked() {
			return player;
		}
	}

	/**
	 * Open inventory.
	 *
	 * @param player
	 *            the player
	 * @param inventory
	 *            the inventory
	 */
	public static void openInventory(Player player, BInventory inventory) {
		inventory.openInventory(player);
	}

	/** The pages. */
	private boolean pages = false;

	/** The page. */
	private int page = 1;

	/** The max page. */
	private int maxPage = 1;

	/** The inventory name. */
	private String inventoryName;

	/** The buttons. */
	private Map<Integer, BInventoryButton> buttons = new HashMap<Integer, BInventoryButton>();

	/**
	 * Instantiates a new b inventory.
	 *
	 * @param name
	 *            the name
	 */
	public BInventory(String name) {
		setInventoryName(name);
		Bukkit.getPluginManager().registerEvents(this,
				Bukkit.getPluginManager().getPlugins()[0]);
	}

	/**
	 * Adds the button.
	 *
	 * @param position
	 *            the position
	 * @param button
	 *            the button
	 */
	public void addButton(int position, BInventoryButton button) {
		getButtons().put(position, button);
	}

	/**
	 * Destroy.
	 */
	public void destroy() {
		HandlerList.unregisterAll(this);
	}

	private Inventory inv;

	private Player player;

	/**
	 * Gets the buttons.
	 *
	 * @return the buttons
	 */
	public Map<Integer, BInventoryButton> getButtons() {
		return buttons;
	}

	/**
	 * Gets the highest slot.
	 *
	 * @return the highest slot
	 */
	public int getHighestSlot() {
		int highestNum = 0;
		for (int num : buttons.keySet()) {
			if (num > highestNum) {
				highestNum = num;
			}
		}
		return highestNum;
	}

	/**
	 * Gets the inventory name.
	 *
	 * @return the inventory name
	 */
	public String getInventoryName() {
		return inventoryName;
	}

	/**
	 * Gets the inventory size.
	 *
	 * @return the inventory size
	 */
	public int getInventorySize() {
		int highestSlot = getHighestSlot();
		if (highestSlot < 9) {
			return 9;
		} else if (highestSlot < 18) {
			return 18;
		} else if (highestSlot < 27) {
			return 27;
		} else if (highestSlot < 36) {
			return 36;
		} else if (highestSlot < 45) {
			return 45;
		} else {
			return 45;
		}
	}

	/**
	 * Gets the next slot.
	 *
	 * @return the next slot
	 */
	public int getNextSlot() {
		if (buttons.keySet().size() == 0) {
			return 0;
		}
		return getHighestSlot() + 1;
	}

	// event handling
	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onInventoryClick(InventoryCloseEvent event) {
		if (!(event.getPlayer() instanceof Player)) {
			return;
		}
		if (this.inv != null
				&& inv.equals(this.inv)
				&& this.player != null
				&& this.player.getUniqueId().equals(
						((Player) event.getPlayer()).getUniqueId())) {
			destroy();
		}
		return;
	}

	/**
	 * On inventory click.
	 *
	 * @param event
	 *            the event
	 */
	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onInventoryClick(InventoryClickEvent event) {
		if (!(event.getWhoClicked() instanceof Player)) {
			return;
		}

		// Main.plugin.debug("Event ran");

		Inventory inv = event.getInventory();

		if (this.inv != null
				&& inv.equals(this.inv)
				&& this.player != null
				&& this.player.getUniqueId().equals(
						((Player) event.getWhoClicked()).getUniqueId())) {

			if (!pages) {
				for (int buttonSlot : getButtons().keySet()) {
					BInventoryButton button = getButtons().get(buttonSlot);
					if (event.getSlot() == buttonSlot) {
						// Main.plugin.debug("Running onclick");
						Player player = (Player) event.getWhoClicked();
						event.setCancelled(true);
						player.closeInventory();
						button.onClick(new ClickEvent(event));
						destroy();
						return;
					}

				}
			} else {
				event.setCancelled(true);
				int slot = event.getSlot();
				if (slot < 45) {
					int buttonSlot = (page - 1) * 45 + event.getSlot();
					BInventoryButton button = getButtons().get(buttonSlot);
					if (button != null) {
						Player player = (Player) event.getWhoClicked();
						player.closeInventory();
						button.onClick(new ClickEvent(event));
						destroy();
						return;
					}

				} else if (slot == 45) {
					if (page > 1) {
						Player player = (Player) event.getWhoClicked();
						player.closeInventory();
						int nextPage = page - 1;
						openInventory(player, nextPage);
					}
				} else if (slot == 53) {
					Main.plugin.debug(maxPage + " " + page);
					if (maxPage > page) {
						Player player = (Player) event.getWhoClicked();
						player.closeInventory();
						int nextPage = page + 1;
						openInventory(player, nextPage);
						Main.plugin.debug("Opening inv");
					}

				}
			}
		}
	}

	/**
	 * Open inventory.
	 *
	 * @param player
	 *            the player
	 */
	public void openInventory(Player player) {
		BInventory inventory = this;
		this.player = player;

		pages = false;
		if (inventory.getHighestSlot() > 53) {
			pages = true;
		}
		if (!pages) {
			inv = Bukkit.createInventory(player, inventory.getInventorySize(),
					inventory.getInventoryName());
			for (Entry<Integer, BInventoryButton> pair : inventory.getButtons()
					.entrySet()) {
				ItemStack item = pair.getValue().getItem();
				ItemMeta meta = item.getItemMeta();
				if (pair.getValue().getName() != null) {
					meta.setDisplayName(pair.getValue().getName());
				}
				if (pair.getValue().getLore() != null) {
					meta.setLore(new ArrayList<String>(Arrays.asList(pair
							.getValue().getLore())));
				}
				item.setItemMeta(meta);
				inv.setItem(pair.getKey(), item);
			}
			player.openInventory(inv);
		} else {
			maxPage = getHighestSlot() / 45;
			if (getHighestSlot() % 45 != 0) {
				maxPage++;
			}
			openInventory(player, 1);
		}

	}

	/**
	 * Open inventory.
	 *
	 * @param player
	 *            the player
	 * @param page
	 *            the page
	 */
	private void openInventory(Player player, int page) {
		BInventory inventory = this;
		this.player = player;
		inv = Bukkit.createInventory(player, 54, inventory.getInventoryName());
		this.page = page;
		int startSlot = (page - 1) * 45;
		for (Entry<Integer, BInventoryButton> pair : inventory.getButtons()
				.entrySet()) {
			int slot = pair.getKey();
			if (slot >= startSlot) {
				slot -= startSlot;
				if (slot < 45 && pair.getKey() < inventory.getButtons().size()) {
					ItemStack item = pair.getValue().getItem();
					ItemMeta meta = item.getItemMeta();
					if (pair.getValue().getName() != null) {
						meta.setDisplayName(pair.getValue().getName());
					}
					if (pair.getValue().getLore() != null) {
						meta.setLore(new ArrayList<String>(Arrays.asList(pair
								.getValue().getLore())));
					}
					item.setItemMeta(meta);
					inv.setItem(slot, item);
				}
			}

		}
		inv.setItem(
				45,
				Utils.getInstance().setName(
						new ItemStack(Material.STAINED_GLASS_PANE, 1,
								(short) 15), "&aPrevious Page"));
		inv.setItem(
				53,
				Utils.getInstance().setName(
						new ItemStack(Material.STAINED_GLASS_PANE, 1,
								(short) 15), "&aNext Page"));

		player.openInventory(inv);
	}

	/**
	 * Sets the inventory name.
	 *
	 * @param inventoryName
	 *            the new inventory name
	 */
	public void setInventoryName(String inventoryName) {
		this.inventoryName = Utils.getInstance().colorize(inventoryName);
	}

	/**
	 * Sets the meta.
	 *
	 * @param player
	 *            the player
	 * @param str
	 *            the str
	 * @param ob
	 *            the ob
	 */
	public void setMeta(Player player, String str, Object ob) {
		Utils.getInstance().setPlayerMeta(player, str, ob);
	}

}