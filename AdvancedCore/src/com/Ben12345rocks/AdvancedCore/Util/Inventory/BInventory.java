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
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import com.Ben12345rocks.AdvancedCore.AdvancedCorePlugin;
import com.Ben12345rocks.AdvancedCore.Util.Item.ItemBuilder;
import com.Ben12345rocks.AdvancedCore.Util.Messages.StringParser;
import com.Ben12345rocks.AdvancedCore.Util.Misc.PlayerUtils;

import lombok.Getter;
import lombok.Setter;

/**
 * The Class BInventory.
 */
public class BInventory {

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

		public void closeInventory() {
			runSync(new Runnable() {

				@Override
				public void run() {
					if (player != null) {
						player.closeInventory();
					}
				}
			});
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

	private ItemStack prevItem;

	private ItemStack nextItem;

	private ArrayList<BInventoryButton> pageButtons = new ArrayList<BInventoryButton>();

	private int maxInvSize = 54;

	/** The pages. */
	private boolean pages = false;

	/** The page. */
	@Getter
	private int page = 1;

	/** The max page. */
	@Getter
	private int maxPage = 1;

	/** The inventory name. */
	private String inventoryName;

	/** The buttons. */
	private Map<Integer, BInventoryButton> buttons = new HashMap<Integer, BInventoryButton>();

	private Inventory inv;

	private HashMap<String, Object> data = new HashMap<String, Object>();

	@Getter
	private HashMap<String, String> placeholders = new HashMap<String, String>();

	@Getter
	@Setter
	private boolean playerSound = true;

	@Getter
	@Setter
	private long lastPressTime = 0;

	/**
	 * Instantiates a new b inventory.
	 *
	 * @param name
	 *            the name
	 */
	public BInventory(String name) {
		setInventoryName(name);
		// Bukkit.getPluginManager().registerEvents(this,
		// AdvancedCorePlugin.getInstance());
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
		button.setSlot(slot);
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

	public BInventory addPlaceholder(String toReplace, String replaceWith) {
		placeholders.put(toReplace, replaceWith);
		return this;
	}

	public void closeInv(Player p, BInventoryButton b) {
		if ((closeInv && (b != null && b.isCloseInv())) || pages) {
			if (p.getOpenInventory().getTopInventory().equals(inv) || pages) {
				forceClose(p);
			}
		}
	}

	public BInventory dontClose() {
		closeInv = false;
		return this;
	}
	
	private void closeUpdatingBInv() {
		for (BInventoryButton b : getButtons().values()) {
			if (b instanceof UpdatingBInventoryButton) {
				UpdatingBInventoryButton ub = (UpdatingBInventoryButton) b;
				ub.cancel();
			}
		}
	}

	public void forceClose(Player p) {
		if (Bukkit.isPrimaryThread()) {
			p.closeInventory();
			
			Bukkit.getScheduler().runTaskAsynchronously(AdvancedCorePlugin.getInstance(), new Runnable() {
				
				@Override
				public void run() {
					closeUpdatingBInv();
				}
			});
		} else {
			closeUpdatingBInv();
			Bukkit.getScheduler().runTask(AdvancedCorePlugin.getInstance(), new Runnable() {

				@Override
				public void run() {
					p.closeInventory();
				}
			});
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

	public boolean isOpen(Player p) {
		if (GUISession.extractSession(p).getInventoryGUI() == this) {
			return true;
		}
		return false;
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

	public void onClick(InventoryClickEvent event, BInventoryButton b) {
		playSound((Player) event.getWhoClicked());
		b.onClick(new ClickEvent(event, b), this);
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

		if (inventory.getHighestSlot() >= maxInvSize) {
			pages = true;
		}
		if (!pages) {
			inv = Bukkit.createInventory(new GUISession(this, 1), inventory.getInventorySize(),
					StringParser.getInstance().replaceJavascript(player, StringParser.getInstance()
							.replacePlaceHolder(inventory.getInventoryName(), getPlaceholders())));
			for (Entry<Integer, BInventoryButton> pair : inventory.getButtons().entrySet()) {
				ItemStack item = pair.getValue().getItem(player, getPlaceholders());
				inv.setItem(pair.getKey(), item);
				if (pair.getValue() instanceof UpdatingBInventoryButton) {
					UpdatingBInventoryButton b = (UpdatingBInventoryButton) pair.getValue();
					b.setInv(this);
					b.setSlot(pair.getKey());
					b.loadTimer(player);
				}
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
	void openInventory(Player player, int page) {
		BInventory inventory = this;
		inv = Bukkit.createInventory(new GUISession(this, page), maxInvSize,
				StringParser.getInstance().replaceJavascript(player, StringParser.getInstance()
						.replacePlaceHolder(inventory.getInventoryName(), getPlaceholders())));
		this.page = page;
		int startSlot = (page - 1) * (maxInvSize - 9);
		for (Entry<Integer, BInventoryButton> pair : inventory.getButtons().entrySet()) {
			int slot = pair.getKey();
			if (slot >= startSlot) {
				slot -= startSlot;
				if (slot < (maxInvSize - 9) && pair.getKey() < inventory.getButtons().size()) {
					ItemStack item = pair.getValue().getItem(player, getPlaceholders());
					inv.setItem(slot, item);
					if (pair.getValue() instanceof UpdatingBInventoryButton) {
						UpdatingBInventoryButton b = (UpdatingBInventoryButton) pair.getValue();
						b.setInv(this);
						b.setSlot(pair.getKey());
						b.loadTimer(player);
					}
				}
			}

		}

		for (BInventoryButton b : pageButtons) {
			inv.setItem((maxInvSize - 9) + b.getSlot(), b.getItem(player, getPlaceholders()));
		}
		if (prevItem == null) {
			if (AdvancedCorePlugin.getInstance().getOptions().getPrevItem() != null) {
				prevItem = new ItemBuilder(AdvancedCorePlugin.getInstance().getOptions().getPrevItem())
						.addPlaceholder(getPlaceholders()).toItemStack(player);
			} else {
				prevItem = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE, 1).setName("&aPrevious Page")
						.addPlaceholder(getPlaceholders()).toItemStack(player);
			}
		}
		if (nextItem == null) {
			if (AdvancedCorePlugin.getInstance().getOptions().getNextItem() != null) {
				nextItem = new ItemBuilder(AdvancedCorePlugin.getInstance().getOptions().getNextItem())
						.addPlaceholder(getPlaceholders()).toItemStack(player);
			} else {
				nextItem = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE, 1).setName("&aNext Page")
						.addPlaceholder(getPlaceholders()).toItemStack(player);
			}
		}

		inv.setItem(maxInvSize - 9, prevItem);

		inv.setItem(maxInvSize - 1, nextItem);

		openInv(player, inv);
	}

	public void playSound(Player player) {
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

	public BInventory setCloseInv(boolean value) {
		closeInv = value;
		return this;
	}

	/**
	 * Sets the inventory name.
	 *
	 * @param inventoryName
	 *            the new inventory name
	 */
	public void setInventoryName(String inventoryName) {
		this.inventoryName = StringParser.getInstance().colorize(inventoryName);
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