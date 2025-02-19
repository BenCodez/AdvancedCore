/* Obtained from https://www.spigotmc.org/threads/libish-inventory-api-kinda.49339/
 * and modified
 */

package com.bencodez.advancedcore.api.inventory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.item.ItemBuilder;
import com.bencodez.advancedcore.api.messages.PlaceholderUtils;
import com.bencodez.simpleapi.messages.MessageAPI;
import com.bencodez.simpleapi.player.PlayerUtils;

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
		private BInventoryButton button;

		@Getter
		private ClickType click;

		@Getter
		private ItemStack clickedItem;

		@Getter
		private InventoryClickEvent event;

		@Getter
		private Inventory inventory;

		@Getter
		private Player player;

		@Getter
		private int slot;

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
		 * @param player the player
		 * @param str    the str
		 * @return the meta
		 */
		public Object getMeta(Player player, String str) {
			return PlayerUtils.getPlayerMeta(AdvancedCorePlugin.getInstance(), player, str);
		}

		/**
		 * Gets the meta.
		 *
		 * @param str the str
		 * @return the meta
		 */
		public Object getMeta(String str) {
			return PlayerUtils.getPlayerMeta(AdvancedCorePlugin.getInstance(), player, str);
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
			AdvancedCorePlugin.getInstance().getBukkitScheduler().runTask(AdvancedCorePlugin.getInstance(), run);
		}
	}

	/**
	 * Open inventory.
	 *
	 * @param player    the player
	 * @param inventory the inventory
	 */
	public static void openInventory(Player player, BInventory inventory) {
		inventory.openInventory(player);
	}

	private Map<Integer, BInventoryButton> buttons = new HashMap<>();

	@Getter
	private boolean closeInv = true;

	private HashMap<String, Object> data = new HashMap<>();

	private Inventory inv;

	private String inventoryName;

	@Getter
	@Setter
	private long lastPressTime = 0;

	private int maxInvSize = 54;

	@Getter
	private int maxPage = 1;

	private ItemStack nextItem;

	@Getter
	private int page = 1;

	private ArrayList<BInventoryButton> pageButtons = new ArrayList<>();

	private boolean pages = false;

	private String perm;

	@Getter
	private HashMap<String, String> placeholders = new HashMap<>();

	@Getter
	@Setter
	private boolean playerSound = true;

	private ItemStack prevItem;

	@SuppressWarnings("rawtypes")
	ArrayList<ScheduledFuture> futures;

	private ArrayList<BInventoryButton> fillItems = new ArrayList<>();

	/**
	 * Instantiates a new b inventory.
	 *
	 * @param name the name
	 */
	public BInventory(String name) {
		setInventoryName(name);
	}

	/**
	 * Adds the button.
	 *
	 * Slot of -2 will add item to end of the GUI (last available slot)
	 *
	 * @param button the button
	 */
	public void addButton(BInventoryButton button) {
		int slot = button.getSlot();
		if (slot == -1) {
			slot = getNextSlot();
		}
		if (slot == -2) {
			slot = getProperSize(getNextSlot()) - 1;
		}
		if (!button.isFillEmptySlots()) {
			if (button.getFillSlots() != null && button.getFillSlots().size() > 0) {
				for (Integer fill : button.getFillSlots()) {
					slot = fill.intValue();
					BInventoryButton button1 = new BInventoryButton(button) {

						@Override
						public void onClick(ClickEvent clickEvent) {
							button.onClick(clickEvent);
						}
					};
					button1.setSlot(slot);
					getButtons().put(slot, button1);
				}
			} else {
				// no fill slots set
				button.setSlot(slot);
				getButtons().put(slot, button);
			}
		} else {
			fillItems.add(button);
			// fill empty slots

		}
	}

	/**
	 * Adds the button.
	 *
	 * @param position the position
	 * @param button   the button
	 */
	public void addButton(int position, BInventoryButton button) {
		getButtons().put(position, button);
	}

	public BInventory addData(String key, Object object) {
		getData().put(key, object);
		return this;
	}

	private void addFillSlots() {
		for (BInventoryButton button : fillItems) {
			for (int i = 0; i < getInventorySize(); i++) {
				boolean slotExist = false;
				for (Integer exist : getButtons().keySet()) {
					if (exist.intValue() == i) {
						slotExist = true;
					}
				}
				if (!slotExist) {
					BInventoryButton button1 = new BInventoryButton(button) {

						@Override
						public void onClick(ClickEvent clickEvent) {
							button.onClick(clickEvent);
						}
					};
					button1.setSlot(i);
					getButtons().put(i, button1);
				}
			}
		}
		fillItems.clear();
	}

	public BInventory addPlaceholder(String toReplace, String replaceWith) {
		placeholders.put(toReplace, replaceWith);
		return this;
	}

	public void addUpdatingButton(AdvancedCorePlugin plugin, long delay, long interval, Runnable runnable) {
		if (futures == null) {
			futures = new ArrayList<>();
		}
		futures.add(plugin.getInventoryTimer().scheduleWithFixedDelay(runnable, delay, delay, TimeUnit.MILLISECONDS));
	}

	@SuppressWarnings("rawtypes")
	public void cancelTimer() {
		if (futures != null) {
			for (ScheduledFuture f : futures) {
				f.cancel(true);
			}
			futures = null;
		}
	}

	public void closeInv(Player p, BInventoryButton b) {
		if (!PlayerUtils.getTopInventory(p).equals(inv)) {
			return;
		}

		if (pages || (closeInv && (b == null || !b.isCloseInvSet()))) {
			forceClose(p);
			return;
		}

		if (b != null && b.isCloseInvSet() && b.isCloseInv()) {
			forceClose(p);
			return;
		}
	}

	private void closeUpdatingBInv() {
		cancelTimer();
	}

	public BInventory dontClose() {
		closeInv = false;
		return this;
	}

	public void forceClose(Player p) {
		if (Bukkit.isPrimaryThread()) {
			p.closeInventory();

			AdvancedCorePlugin.getInstance().getBukkitScheduler()
					.runTaskAsynchronously(AdvancedCorePlugin.getInstance(), new Runnable() {

						@Override
						public void run() {
							closeUpdatingBInv();
						}
					});
		} else {
			closeUpdatingBInv();
			AdvancedCorePlugin.getInstance().getBukkitScheduler().runTask(AdvancedCorePlugin.getInstance(),
					new Runnable() {

						@Override
						public void run() {
							p.closeInventory();
						}
					}, p);
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

	public int getFirstEmptySlot() {
		if (buttons.keySet().size() == 0) {
			return 0;
		}

		for (int i = 0; i < getInventorySize(); i++) {
			if (!buttons.containsKey(i)) {
				return i;
			}
		}
		return getHighestSlot() + 1;

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
		return PlayerUtils.getPlayerMeta(AdvancedCorePlugin.getInstance(), player, str);
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
		}
		if (size < 18) {
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
		GUISession session = GUISession.extractSession(p);
		if (session != null && session.getInventoryGUI() == this) {
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
		AdvancedCorePlugin.getInstance().getBukkitScheduler().runTask(AdvancedCorePlugin.getInstance(), new Runnable() {

			@Override
			public void run() {
				player.openInventory(inv);
			}
		}, player);
	}

	/**
	 * Open inventory.
	 *
	 * @param player the player
	 */
	@SuppressWarnings({})
	public void openInventory(Player player) {
		if (player.isSleeping()) {
			AdvancedCorePlugin.getInstance().debug(player.getName() + " is sleeping, not opening gui!");
			return;
		}
		if (perm != null) {
			if (!perm.contains("|")) {
				if (!player.hasPermission(perm)) {
					player.sendMessage(
							MessageAPI.colorize(AdvancedCorePlugin.getInstance().getOptions().getFormatNoPerms()));
					return;
				}
			} else {
				boolean hasPerm = false;
				for (String permStr : perm.split(Pattern.quote("|"))) {
					if (player.hasPermission(permStr)) {
						hasPerm = true;
					}
				}
				if (!hasPerm) {
					player.sendMessage(
							MessageAPI.colorize(AdvancedCorePlugin.getInstance().getOptions().getFormatNoPerms()));
					return;
				}
			}
		}
		addFillSlots();
		BInventory inventory = this;

		if (inventory.getHighestSlot() >= maxInvSize) {
			pages = true;
		}
		if (!pages) {
			inv = Bukkit.createInventory(new GUISession(this, 1), inventory.getInventorySize(),
					PlaceholderUtils.replaceJavascript(player,
							PlaceholderUtils.replacePlaceHolder(inventory.getInventoryName(), getPlaceholders())));
			for (Entry<Integer, BInventoryButton> pair : inventory.getButtons().entrySet()) {
				ItemStack item = pair.getValue().getItem(player, getPlaceholders());
				inv.setItem(pair.getKey(), item);

				BInventoryButton b = pair.getValue();
				b.setInv(this);
				b.setSlot(pair.getKey());
				b.load(player);

			}

			openInv(player, inv);

		} else {
			maxPage = getHighestSlot() / (maxInvSize - 9);
			if (getHighestSlot() % (maxInvSize - 9) != 0) {
				maxPage++;
			}
			addPlaceholder("totalpages", "" + maxPage);
			openInventory(player, 1);
		}

	}

	/**
	 * Open inventory.
	 *
	 * @param player the player
	 * @param page   the page
	 */
	public void openInventory(Player player, int page) {
		BInventory inventory = this;
		addPlaceholder("currentpage", "" + page);
		inv = Bukkit.createInventory(new GUISession(this, page), maxInvSize, PlaceholderUtils.replaceJavascript(player,
				PlaceholderUtils.replacePlaceHolder(inventory.getInventoryName(), getPlaceholders())));
		this.page = page;
		int startSlot = (page - 1) * (maxInvSize - 9);
		for (Entry<Integer, BInventoryButton> pair : inventory.getButtons().entrySet()) {
			int slot = pair.getKey();
			if (slot >= startSlot) {
				slot -= startSlot;
				if (slot < (maxInvSize - 9)) {
					ItemStack item = pair.getValue().getItem(player, getPlaceholders());
					inv.setItem(slot, item);

					BInventoryButton b = pair.getValue();
					b.setInv(this);
					b.setSlot(pair.getKey());
					b.load(player);
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

	public void requirePermission(String permission) {
		this.perm = permission;
	}

	/**
	 * @param buttons the buttons to set
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
	 * @param inventoryName the new inventory name
	 */
	public void setInventoryName(String inventoryName) {
		this.inventoryName = MessageAPI.colorize(inventoryName);
	}

	/**
	 * @param maxInvSize the maxInvSize to set
	 */
	public void setMaxInvSize(int maxInvSize) {
		this.maxInvSize = getProperSize(maxInvSize);
	}

	/**
	 * Sets the meta.
	 *
	 * @param player the player
	 * @param str    the str
	 * @param ob     the ob
	 */
	public void setMeta(Player player, String str, Object ob) {
		PlayerUtils.setPlayerMeta(AdvancedCorePlugin.getInstance(), player, str, ob);
	}

	/**
	 * @param nextItem the nextItem to set
	 */
	public void setNextItem(ItemStack nextItem) {
		this.nextItem = nextItem;
	}

	/**
	 * @param pageButtons the pageButtons to set
	 */
	public void setPageButtons(ArrayList<BInventoryButton> pageButtons) {
		this.pageButtons = pageButtons;
	}

	/**
	 * @param pages the pages to set
	 */
	public void setPages(boolean pages) {
		this.pages = pages;
	}

	/**
	 * @param prevItem the prevItem to set
	 */
	public void setPrevItem(ItemStack prevItem) {
		this.prevItem = prevItem;
	}

}