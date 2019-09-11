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
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.Event.Result;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import com.Ben12345rocks.AdvancedCore.AdvancedCorePlugin;
import com.Ben12345rocks.AdvancedCore.Util.Item.ItemBuilder;
import com.Ben12345rocks.AdvancedCore.Util.Misc.PlayerUtils;
import com.Ben12345rocks.AdvancedCore.Util.Misc.StringUtils;

import lombok.Getter;
import lombok.Setter;

/**
 * The Class BInventory.
 */
public class BInventory implements Listener {

	/**
	 * The Class ClickEvent.
	 */
	public class ClickEvent {

		@Getter
		private Player player;

		@Getter
		private InventoryClickEvent event;

		@Getter
		private ClickType click;

		@Getter
		private Inventory inventory;

		@Getter
		private int slot;

		@Getter
		private ItemStack clickedItem;

		@Getter
		private BInventoryButton button;

		public ClickEvent(InventoryClickEvent event, BInventoryButton b) {
			this.event = event;
			player = (Player) event.getWhoClicked();
			click = event.getClick();
			inventory = event.getInventory();
			clickedItem = event.getCurrentItem();
			slot = event.getSlot();
			button = b;
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
		 * Gets the who clicked.
		 *
		 * @return the who clicked
		 */
		public Player getWhoClicked() {
			return player;
		}

		public void runSync(Runnable run) {
			Bukkit.getScheduler().runTask(AdvancedCorePlugin.getInstance(), run);
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

	@Getter
	private boolean closeInv = true;

	public BInventory dontClose() {
		closeInv = false;
		return this;
	}

	private ItemStack prevItem;

	private ItemStack nextItem;

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

	private HashMap<String, Object> data = new HashMap<String, Object>();

	@Getter
	@Setter
	private boolean playerSound = true;

	/**
	 * Instantiates a new b inventory.
	 *
	 * @param name
	 *            the name
	 */
	public BInventory(String name) {
		setInventoryName(name);
		Bukkit.getPluginManager().registerEvents(this, AdvancedCorePlugin.getInstance());
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

	public BInventory addData(String key, Object object) {
		getData().put(key, object);
		return this;
	}

	private boolean destroy = false;

	/**
	 * Destroy.
	 */
	public void destroy() {
		if (destroy) {
			return;
		}
		destroy = true;
		final BInventory b = this;
		Bukkit.getScheduler().runTaskLater(AdvancedCorePlugin.getInstance(), new Runnable() {

			@Override
			public void run() {
				HandlerList.unregisterAll(b);
			}
		}, 200l);

		if (player != null) {
			AdvancedCorePlugin.getInstance().extraDebug("Disabling inventory listeners for " + player.getUniqueId());
		} else {
			AdvancedCorePlugin.getInstance().extraDebug("Disabling inventory listeners for null");
		}
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
	 * @return the data
	 */
	public HashMap<String, Object> getData() {
		return data;
	}

	public Object getData(String key) {
		return data.get(key);
	}

	public Object getData(String key, Object defaultValue) {
		if (data.containsKey(key)) {
			return data.get(key);
		}
		return defaultValue;
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

	public BInventory setCloseInv(boolean value) {
		closeInv = value;
		return this;
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

	public Object getMeta(Player player, String str) {
		return PlayerUtils.getInstance().getPlayerMeta(player, str);
	}

	/**
	 * @return the nextItem
	 */
	public ItemStack getNextItem() {
		return nextItem;
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

	/**
	 * @return the prevItem
	 */
	public ItemStack getPrevItem() {
		return prevItem;
	}

	private int getProperSize(int size) {
		if (size < 9) {
			return 9;
		} else if (size < 18) {
			return 18;
		} else if (size < 27) {
			return 27;
		} else if (size < 36) {
			return 36;
		} else if (size < 45) {
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

	public BInventory noSound() {
		playerSound = false;
		return this;
	}

	private void onClick(InventoryClickEvent event, BInventoryButton b) {
		playSound((Player) event.getWhoClicked());
		b.onClick(new ClickEvent(event, b), this);
	}

	public void closeInv(Player p, BInventoryButton b) {
		if (closeInv && (b != null && b.isCloseInv())) {
			if (p.getOpenInventory().getTopInventory().equals(inv)) {
				destroy();
				p.closeInventory();
			}
		}
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

		Inventory inv = event.getInventory();

		if (this.inv != null && inv.equals(this.inv) && player != null
				&& this.player.getUniqueId().equals(((Player) event.getWhoClicked()).getUniqueId())) {
			event.setCancelled(true);
			event.setResult(Result.DEFAULT);
			final Player player = (Player) event.getWhoClicked();
			player.setItemOnCursor(new ItemStack(Material.AIR));
			player.updateInventory();
			if (!pages) {
				for (int buttonSlot : getButtons().keySet()) {
					BInventoryButton button = getButtons().get(buttonSlot);
					if (event.getSlot() == buttonSlot) {

						closeInv(player, button);
						Bukkit.getServer().getScheduler().runTaskAsynchronously(AdvancedCorePlugin.getInstance(),
								new Runnable() {

									@Override
									public void run() {
										try {
											onClick(event, button);
										} catch (Exception e) {
											e.printStackTrace();
										}
									}
								});
						return;
					}

				}
			} else {
				int slot = event.getSlot();
				if (slot < maxInvSize - 9) {
					int buttonSlot = (page - 1) * (maxInvSize - 9) + event.getSlot();
					BInventoryButton button = getButtons().get(buttonSlot);
					if (button != null) {
						closeInv(player, button);
						Bukkit.getServer().getScheduler().runTaskAsynchronously(AdvancedCorePlugin.getInstance(),
								new Runnable() {

									@Override
									public void run() {
										try {
											onClick(event, button);
										} catch (Exception e) {
											e.printStackTrace();
										}
									}
								});
						return;
					}

				} else if (slot == maxInvSize - 9) {
					if (page > 1) {

						final int nextPage = page - 1;
						closeInv(player, null);

						Bukkit.getServer().getScheduler().runTaskAsynchronously(AdvancedCorePlugin.getInstance(),
								new Runnable() {

									@Override
									public void run() {
										playSound(player);
										openInventory(player, nextPage);
									}
								});
					}
				} else if (slot == maxInvSize - 1) {
					// AdvancedCorePlugin.getInstance().debug(maxPage + " " +
					// page);
					if (maxPage > page) {
						closeInv(player, null);

						final int nextPage = page + 1;

						Bukkit.getServer().getScheduler().runTaskAsynchronously(AdvancedCorePlugin.getInstance(),
								new Runnable() {

									@Override
									public void run() {
										playSound(player);
										openInventory(player, nextPage);
									}
								});
					}

				}

				for (

				BInventoryButton b : pageButtons) {
					if (slot == b.getSlot() + (getMaxInvSize() - 9)) {
						closeInv(player, b);
						Bukkit.getServer().getScheduler().runTaskAsynchronously(AdvancedCorePlugin.getInstance(),
								new Runnable() {

									@Override
									public void run() {
										try {
											onClick(event, b);
										} catch (Exception e) {
											e.printStackTrace();
										}
									}
								});

					}

				}
			}
		}
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onPlayerQuit(PlayerQuitEvent event) {
		if (!(event.getPlayer() instanceof Player)) {
			return;
		}
		if (player != null && event.getPlayer().getUniqueId().equals(player.getUniqueId())) {
			Bukkit.getScheduler().runTaskLater(AdvancedCorePlugin.getInstance(), new Runnable() {

				@Override
				public void run() {
					if (AdvancedCorePlugin.getInstance().getOptions().isAutoKillInvs()) {
						destroy();
					}
				}
			}, 10l);
		}

		return;
	}

	// event handling
	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onInventoryClose(InventoryCloseEvent event) {
		if (!(event.getPlayer() instanceof Player)) {
			return;
		}
		if (inv != null && event.getInventory().equals(inv) && player != null
				&& player.getUniqueId().equals(((Player) event.getPlayer()).getUniqueId()) && !pages) {
			Bukkit.getScheduler().runTaskLater(AdvancedCorePlugin.getInstance(), new Runnable() {

				@Override
				public void run() {
					if (player != null) {
						if (player.getOpenInventory() == null
								|| !player.getOpenInventory().getTopInventory().equals(inv)) {
							if (AdvancedCorePlugin.getInstance().getOptions().isAutoKillInvs()) {
								destroy();
							}
						}
					}
				}
			}, 10000l);

		}

		return;
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onInventoryOpen(InventoryOpenEvent event) {
		if (!(event.getPlayer() instanceof Player)) {
			return;
		}
		if (inv != null && !event.getInventory().equals(inv) && player != null
				&& player.getUniqueId().equals(((Player) event.getPlayer()).getUniqueId()) && !pages) {
			Bukkit.getScheduler().runTaskLater(AdvancedCorePlugin.getInstance(), new Runnable() {

				@Override
				public void run() {
					if (player != null) {
						if (player.getOpenInventory() == null
								|| !player.getOpenInventory().getTopInventory().equals(inv)) {
							if (AdvancedCorePlugin.getInstance().getOptions().isAutoKillInvs()) {
								destroy();
							}
						}
					}
				}
			}, 10000l);

		}

		return;
	}

	private void openInv(Player player, Inventory inv) {
		Bukkit.getScheduler().runTask(AdvancedCorePlugin.getInstance(), new Runnable() {

			@Override
			public void run() {
				player.openInventory(inv);
			}
		});
	}

	/**
	 * Open inventory.
	 *
	 * @param player
	 *            the player
	 */
	public void openInventory(Player player) {
		if (player.isSleeping()) {
			AdvancedCorePlugin.getInstance().debug(player.getName() + " is sleeping, not opening gui!");
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

			openInv(player, inv);

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
			inv.setItem((maxInvSize - 9) + b.getSlot(), b.getItem(player));
		}
		if (prevItem == null) {
			if (AdvancedCorePlugin.getInstance().getOptions().getPrevItem() != null) {
				prevItem = new ItemBuilder(AdvancedCorePlugin.getInstance().getOptions().getPrevItem())
						.toItemStack(player);
			} else {
				prevItem = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE, 1).setName("&aPrevious Page")
						.toItemStack(player);
			}
		}
		if (nextItem == null) {
			if (AdvancedCorePlugin.getInstance().getOptions().getNextItem() != null) {
				nextItem = new ItemBuilder(AdvancedCorePlugin.getInstance().getOptions().getNextItem())
						.toItemStack(player);
			} else {
				nextItem = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE, 1).setName("&aNext Page")
						.toItemStack(player);
			}
		}

		inv.setItem(maxInvSize - 9, prevItem);

		inv.setItem(maxInvSize - 1, nextItem);

		openInv(player, inv);
	}

	private void playSound(Player player) {
		if (playerSound) {
			Sound sound = AdvancedCorePlugin.getInstance().getOptions().getClickSoundSound();
			if (sound != null) {
				player.playSound(player.getLocation(), sound,
						(float) AdvancedCorePlugin.getInstance().getOptions().getClickSoundVolume(),
						(float) AdvancedCorePlugin.getInstance().getOptions().getClickSoundPitch());
			}
		}
	}

	/**
	 * @param buttons
	 *            the buttons to set
	 */
	public void setButtons(Map<Integer, BInventoryButton> buttons) {
		this.buttons = buttons;
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
	 * @param nextItem
	 *            the nextItem to set
	 */
	public void setNextItem(ItemStack nextItem) {
		this.nextItem = nextItem;
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

	/**
	 * @param prevItem
	 *            the prevItem to set
	 */
	public void setPrevItem(ItemStack prevItem) {
		this.prevItem = prevItem;
	}

}