/* Obtained from https://www.spigotmc.org/threads/libish-inventory-api-kinda.49339/
 * and modified
 */

package com.Ben12345rocks.AdvancedCore.Util.Inventory;

import java.util.ArrayList;
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

import com.Ben12345rocks.AdvancedCore.AdvancedCoreHook;
import com.Ben12345rocks.AdvancedCore.Util.Item.ItemBuilder;
import com.Ben12345rocks.AdvancedCore.Util.Misc.PlayerUtils;
import com.Ben12345rocks.AdvancedCore.Util.Misc.StringUtils;

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
			return PlayerUtils.getInstance().getPlayerMeta(player, str);
		}

		/**
		 * Gets the meta.
		 *
		 * @param str
		 *            the str
		 * @return the meta
		 */
		public Object getMeta(String str) {
			return PlayerUtils.getInstance().getPlayerMeta(player, str);
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

	private ArrayList<BInventoryButton> pageButtons = new ArrayList<BInventoryButton>();

	private int maxInvSize = 54;

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

	private Inventory inv;

	private Player player;

	/**
	 * Instantiates a new b inventory.
	 *
	 * @param name
	 *            the name
	 */
	public BInventory(String name) {
		setInventoryName(name);
		Bukkit.getPluginManager().registerEvents(this, AdvancedCoreHook.getInstance().getPlugin());
	}

	/**
	 * Adds the button.
	 *
	 * @param button
	 *            the button
	 */
	public void addButton(BInventoryButton button) {
		int slot = button.getSlot();
		if (slot == -1) {
			slot = getNextSlot();
		}
		getButtons().put(slot, button);
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
		return getProperSize(getHighestSlot());
	}

	/**
	 * @return the maxInvSize
	 */
	public int getMaxInvSize() {
		return maxInvSize;
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

	/**
	 * @return the pageButtons
	 */
	public ArrayList<BInventoryButton> getPageButtons() {
		return pageButtons;
	}

	private int getProperSize(int size) {
		if (size <= 9) {
			return 9;
		} else if (size <= 18) {
			return 18;
		} else if (size <= 27) {
			return 27;
		} else if (size <= 36) {
			return 36;
		} else if (size <= 45) {
			return 45;
		} else {
			return 54;
		}
	}

	/**
	 * @return the pages
	 */
	public boolean isPages() {
		return pages;
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

		if (this.inv != null && inv.equals(this.inv) && player != null
				&& player.getUniqueId().equals(((Player) event.getWhoClicked()).getUniqueId())) {

			if (!pages) {
				for (int buttonSlot : getButtons().keySet()) {
					BInventoryButton button = getButtons().get(buttonSlot);
					if (event.getSlot() == buttonSlot) {
						// Main.plugin.debug("Running onclick");
						Player player = (Player) event.getWhoClicked();
						event.setCancelled(true);
						player.closeInventory();
						Bukkit.getServer().getScheduler()
								.runTaskAsynchronously(AdvancedCoreHook.getInstance().getPlugin(), new Runnable() {

									@Override
									public void run() {
										button.onClick(new ClickEvent(event));
									}
								});
						destroy();
						return;
					}

				}
			} else {
				event.setCancelled(true);
				int slot = event.getSlot();
				if (slot < maxInvSize - 9) {
					int buttonSlot = (page - 1) * (maxInvSize - 9) + event.getSlot();
					BInventoryButton button = getButtons().get(buttonSlot);
					if (button != null) {
						Player player = (Player) event.getWhoClicked();
						player.closeInventory();
						Bukkit.getServer().getScheduler()
								.runTaskAsynchronously(AdvancedCoreHook.getInstance().getPlugin(), new Runnable() {

									@Override
									public void run() {
										button.onClick(new ClickEvent(event));
									}
								});
						destroy();
						return;
					}

				} else if (slot == maxInvSize - 9) {
					if (page > 1) {
						Player player = (Player) event.getWhoClicked();
						player.closeInventory();
						int nextPage = page - 1;
						openInventory(player, nextPage);
					}
				} else if (slot == maxInvSize - 1) {
					// AdvancedCoreHook.getInstance().debug(maxPage + " " +
					// page);
					if (maxPage > page) {
						Player player = (Player) event.getWhoClicked();
						player.closeInventory();
						int nextPage = page + 1;
						openInventory(player, nextPage);
						AdvancedCoreHook.getInstance().debug("Opening inv");
					}

				}

				for (BInventoryButton b : pageButtons) {
					if (slot == b.getSlot() + (getMaxInvSize() - 9)) {
						b.onClick(new ClickEvent(event));
					}
				}
			}
		}
	}

	// event handling
	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onInventoryClose(InventoryCloseEvent event) {
		if (!(event.getPlayer() instanceof Player)) {
			return;
		}
		if (inv != null && inv.equals(inv) && player != null
				&& player.getUniqueId().equals(((Player) event.getPlayer()).getUniqueId()) && !pages) {
			destroy();
		}
		return;
	}

	/**
	 * Open inventory.
	 *
	 * @param player
	 *            the player
	 */
	public void openInventory(Player player) {
		if (player.isSleeping()) {
			AdvancedCoreHook.getInstance().debug(player.getName() + " is sleeping, not opening gui!");
			return;
		}
		BInventory inventory = this;
		this.player = player;

		if (inventory.getHighestSlot() >= maxInvSize) {
			pages = true;
		}
		if (!pages) {
			inv = Bukkit.createInventory(player, inventory.getInventorySize(), inventory.getInventoryName());
			for (Entry<Integer, BInventoryButton> pair : inventory.getButtons().entrySet()) {
				ItemStack item = pair.getValue().getItem(player);
				inv.setItem(pair.getKey(), item);
			}
			Bukkit.getScheduler().runTask(AdvancedCoreHook.getInstance().getPlugin(), new Runnable() {

				@Override
				public void run() {
					player.openInventory(inv);
				}
			});

		} else {
			maxPage = getHighestSlot() / (maxInvSize - 9);
			if (getHighestSlot() % (maxInvSize - 9) != 0) {
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
		if (player.isSleeping()) {
			AdvancedCoreHook.getInstance().debug(player.getName() + " is sleeping, not opening gui!");
			return;
		}
		BInventory inventory = this;
		this.player = player;
		inv = Bukkit.createInventory(player, maxInvSize, inventory.getInventoryName());
		this.page = page;
		int startSlot = (page - 1) * (maxInvSize - 9);
		for (Entry<Integer, BInventoryButton> pair : inventory.getButtons().entrySet()) {
			int slot = pair.getKey();
			if (slot >= startSlot) {
				slot -= startSlot;
				if (slot < (maxInvSize - 9) && pair.getKey() < inventory.getButtons().size()) {
					ItemStack item = pair.getValue().getItem(player);
					inv.setItem(slot, item);
				}
			}

		}

		for (BInventoryButton b : pageButtons) {
			inv.setItem((maxInvSize - 9) + b.getSlot(), b.getItem());
		}

		inv.setItem(maxInvSize - 9,
				new ItemBuilder(Material.STAINED_GLASS_PANE, 1, (short) 15).setName("&aPrevious Page").toItemStack());

		inv.setItem(maxInvSize - 1,
				new ItemBuilder(Material.STAINED_GLASS_PANE, 1, (short) 15).setName("&aNext Page").toItemStack());

		Bukkit.getScheduler().runTask(AdvancedCoreHook.getInstance().getPlugin(), new Runnable() {

			@Override
			public void run() {
				player.openInventory(inv);
			}
		});
	}

	/**
	 * Sets the inventory name.
	 *
	 * @param inventoryName
	 *            the new inventory name
	 */
	public void setInventoryName(String inventoryName) {
		this.inventoryName = StringUtils.getInstance().colorize(inventoryName);
	}

	/**
	 * @param maxInvSize
	 *            the maxInvSize to set
	 */
	public void setMaxInvSize(int maxInvSize) {
		this.maxInvSize = getProperSize(maxInvSize);
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
		PlayerUtils.getInstance().setPlayerMeta(player, str, ob);
	}

	/**
	 * @param pageButtons
	 *            the pageButtons to set
	 */
	public void setPageButtons(ArrayList<BInventoryButton> pageButtons) {
		this.pageButtons = pageButtons;
	}

	/**
	 * @param pages
	 *            the pages to set
	 */
	public void setPages(boolean pages) {
		this.pages = pages;
	}

}