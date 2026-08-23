/* Obtained from https://www.spigotmc.org/threads/libish-inventory-api-kinda.49339/
 * and modified
 */

package com.bencodez.advancedcore.api.inventory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
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
 * Inventory GUI with button, pagination and update-task lifecycle management.
 */
public class BInventory {

	public class ClickEvent {
		@Getter
		private final BInventoryButton button;
		@Getter
		private final ClickType click;
		@Getter
		private final ItemStack clickedItem;
		@Getter
		private final InventoryClickEvent event;
		@Getter
		private final Inventory inventory;
		@Getter
		private final Player player;
		@Getter
		private final int slot;

		public ClickEvent(InventoryClickEvent event, BInventoryButton button) {
			this.event = event;
			this.player = (Player) event.getWhoClicked();
			this.click = event.getClick();
			this.inventory = event.getInventory();
			this.clickedItem = event.getCurrentItem();
			this.slot = event.getSlot();
			this.button = button;
		}

		public void closeInventory() {
			runSync(() -> {
				if (player != null) {
					player.closeInventory();
				}
			});
		}

		public ItemStack getCurrentItem() {
			return clickedItem;
		}

		public Object getMeta(Player player, String str) {
			return PlayerUtils.getPlayerMeta(AdvancedCorePlugin.getInstance(), player, str);
		}

		public Object getMeta(String str) {
			return PlayerUtils.getPlayerMeta(AdvancedCorePlugin.getInstance(), player, str);
		}

		public Player getWhoClicked() {
			return player;
		}

		public void runSync(Runnable runnable) {
			AdvancedCorePlugin plugin = AdvancedCorePlugin.getInstance();
			plugin.getBukkitScheduler().runTask(plugin, runnable);
		}
	}

	public static void openInventory(Player player, BInventory inventory) {
		inventory.openInventory(player);
	}

	private Map<Integer, BInventoryButton> buttons = new HashMap<>();
	@Getter
	private boolean closeInv = true;
	@Getter
	private boolean clickAsync = true;
	private final HashMap<String, Object> data = new HashMap<>();
	private final ArrayList<BInventoryButton> fillItems = new ArrayList<>();
	private final List<ScheduledFuture<?>> futures = new CopyOnWriteArrayList<>();
	private final Map<UUID, List<ScheduledFuture<?>>> playerFutures = new ConcurrentHashMap<>();
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
	private final HashMap<String, String> placeholders = new HashMap<>();
	@Getter
	@Setter
	private boolean playerSound = true;
	private ItemStack prevItem;

	public BInventory(String name) {
		setInventoryName(name);
	}

	/**
	 * Adds a button. Slot -1 selects the next slot and -2 selects the final slot of
	 * the current inventory row size.
	 */
	public void addButton(BInventoryButton button) {
		int slot = button.getSlot();
		if (slot == -1) {
			slot = getNextSlot();
		} else if (slot == -2) {
			slot = getProperSize(getNextSlot()) - 1;
		}

		if (button.isFillEmptySlots()) {
			fillItems.add(button);
			return;
		}

		List<Integer> fillSlots = button.getFillSlots();
		if (fillSlots != null && !fillSlots.isEmpty()) {
			for (Integer fillSlot : fillSlots) {
				if (fillSlot != null) {
					buttons.put(fillSlot, copyButton(button, fillSlot));
				}
			}
			return;
		}

		button.setSlot(slot);
		buttons.put(slot, button);
	}

	public boolean isSlotTaken(int slot) {
		return buttons.containsKey(slot);
	}

	public void addButton(int position, BInventoryButton button) {
		buttons.put(position, button);
	}

	public BInventory addData(String key, Object object) {
		data.put(key, object);
		return this;
	}

	public BInventory addPlaceholder(String toReplace, String replaceWith) {
		placeholders.put(toReplace, replaceWith);
		return this;
	}

	/**
	 * Compatibility form for update tasks that are not associated with a player.
	 */
	public void addUpdatingButton(AdvancedCorePlugin plugin, long delay, long interval, Runnable runnable) {
		futures.add(plugin.getInventoryTimer().scheduleWithFixedDelay(runnable, delay, interval, TimeUnit.MILLISECONDS));
	}

	/**
	 * Tracks an updating task for one viewer so closing another player's GUI does
	 * not cancel it.
	 */
	public void addUpdatingButton(Player player, AdvancedCorePlugin plugin, long delay, long interval,
			Runnable runnable) {
		ScheduledFuture<?> future = plugin.getInventoryTimer().scheduleWithFixedDelay(runnable, delay, interval,
				TimeUnit.MILLISECONDS);
		trackFuture(player, future);
	}

	void addDelayedTask(Player player, AdvancedCorePlugin plugin, long delay, Runnable runnable) {
		ScheduledFuture<?> future = plugin.getInventoryTimer().schedule(runnable, delay, TimeUnit.MILLISECONDS);
		trackFuture(player, future);
	}

	/**
	 * Cancels every task owned by this GUI.
	 */
	public void cancelTimer() {
		cancelLegacyTimers();
		for (List<ScheduledFuture<?>> viewerFutures : playerFutures.values()) {
			cancelFutures(viewerFutures);
		}
		playerFutures.clear();
	}

	/**
	 * Cancels only tasks associated with one viewer.
	 */
	public void cancelTimer(Player player) {
		if (player == null) {
			return;
		}
		List<ScheduledFuture<?>> viewerFutures = playerFutures.remove(player.getUniqueId());
		cancelFutures(viewerFutures);
	}

	public void closeInv(Player player, BInventoryButton button) {
		Inventory topInventory = PlayerUtils.getTopInventory(player);
		if (topInventory == null || inv == null || !topInventory.equals(inv)) {
			return;
		}

		if (pages || (closeInv && (button == null || !button.isCloseInvSet()))) {
			forceClose(player);
			return;
		}
		if (button != null && button.isCloseInvSet() && button.isCloseInv()) {
			forceClose(player);
		}
	}

	public BInventory dontClose() {
		closeInv = false;
		return this;
	}

	public void forceClose(Player player) {
		cancelLegacyTimers();
		cancelTimer(player);
		if (player == null) {
			return;
		}
		if (Bukkit.isPrimaryThread()) {
			player.closeInventory();
			return;
		}

		AdvancedCorePlugin plugin = AdvancedCorePlugin.getInstance();
		plugin.getBukkitScheduler().runTask(plugin, player::closeInventory, player);
	}

	public Map<Integer, BInventoryButton> getButtons() {
		return buttons;
	}

	public HashMap<String, Object> getData() {
		return data;
	}

	public Object getData(String key) {
		return data.get(key);
	}

	public Object getData(String key, Object defaultValue) {
		return data.containsKey(key) ? data.get(key) : defaultValue;
	}

	public int getFirstEmptySlot() {
		if (buttons.isEmpty()) {
			return 0;
		}
		for (int i = 0; i < getInventorySize(); i++) {
			if (!buttons.containsKey(i)) {
				return i;
			}
		}
		return getHighestSlot() + 1;
	}

	public int getHighestSlot() {
		int highest = 0;
		for (int slot : buttons.keySet()) {
			if (slot > highest) {
				highest = slot;
			}
		}
		return highest;
	}

	public String getInventoryName() {
		return inventoryName;
	}

	public int getInventorySize() {
		return getProperSize(getHighestSlot());
	}

	public int getMaxInvSize() {
		return maxInvSize;
	}

	public Object getMeta(Player player, String str) {
		return PlayerUtils.getPlayerMeta(AdvancedCorePlugin.getInstance(), player, str);
	}

	public ItemStack getNextItem() {
		return nextItem;
	}

	public int getNextSlot() {
		return buttons.isEmpty() ? 0 : getHighestSlot() + 1;
	}

	public ArrayList<BInventoryButton> getPageButtons() {
		return pageButtons;
	}

	public ItemStack getPrevItem() {
		return prevItem;
	}

	public boolean isOpen(Player player) {
		GUISession session = GUISession.extractSession(player);
		return session != null && session.getInventoryGUI() == this;
	}

	public boolean isPages() {
		return pages;
	}

	public BInventory noSound() {
		playerSound = false;
		return this;
	}

	public void onClick(InventoryClickEvent event, BInventoryButton button) {
		playSound((Player) event.getWhoClicked());
		button.onClick(new ClickEvent(event, button), this);
	}

	public void openInventory(Player player) {
		if (player.isSleeping()) {
			AdvancedCorePlugin.getInstance().debug(player.getName() + " is sleeping, not opening gui!");
			return;
		}
		if (!hasPermission(player)) {
			return;
		}

		addFillSlots();
		if (getHighestSlot() >= maxInvSize) {
			pages = true;
		}

		if (pages) {
			maxPage = InventoryPagination.getPageCount(getHighestSlot(), maxInvSize);
			addPlaceholder("totalpages", String.valueOf(maxPage));
			openInventory(player, 1);
			return;
		}

		cancelTimer(player);
		inv = Bukkit.createInventory(new GUISession(this, 1), getInventorySize(), getDisplayName(player));
		for (Entry<Integer, BInventoryButton> entry : buttons.entrySet()) {
			loadButton(player, entry.getValue(), entry.getKey(), entry.getKey());
		}
		openInv(player, inv);
	}

	public void openInventory(Player player, int page) {
		if (page < 1) {
			throw new IllegalArgumentException("Page must be >= 1");
		}

		maxPage = InventoryPagination.getPageCount(getHighestSlot(), maxInvSize);
		int targetPage = Math.min(page, maxPage);
		cancelTimer(player);
		addPlaceholder("currentpage", String.valueOf(targetPage));
		addPlaceholder("totalpages", String.valueOf(maxPage));

		inv = Bukkit.createInventory(new GUISession(this, targetPage), maxInvSize, getDisplayName(player));
		this.page = targetPage;

		int contentSize = InventoryPagination.getContentSize(maxInvSize);
		int startSlot = (targetPage - 1) * contentSize;
		for (Entry<Integer, BInventoryButton> entry : buttons.entrySet()) {
			int sourceSlot = entry.getKey();
			if (sourceSlot < startSlot || sourceSlot >= startSlot + contentSize) {
				continue;
			}
			loadButton(player, entry.getValue(), sourceSlot, sourceSlot - startSlot);
		}

		for (BInventoryButton button : pageButtons) {
			int displayedSlot = contentSize + button.getSlot();
			inv.setItem(displayedSlot, button.getItem(player, placeholders));
			button.setInv(this);
		}

		loadNavigationItems(player);
		inv.setItem(contentSize, prevItem);
		inv.setItem(maxInvSize - 1, nextItem);
		openInv(player, inv);
	}

	public void playSound(Player player) {
		if (!playerSound) {
			return;
		}
		AdvancedCorePlugin plugin = AdvancedCorePlugin.getInstance();
		Sound sound = plugin.getOptions().getClickSoundSound();
		if (sound != null) {
			player.playSound(player.getLocation(), sound, (float) plugin.getOptions().getClickSoundVolume(),
					(float) plugin.getOptions().getClickSoundPitch());
		}
	}

	public void requirePermission(String permission) {
		this.perm = permission;
	}

	public void setButtons(Map<Integer, BInventoryButton> buttons) {
		this.buttons = buttons;
	}

	/**
	 * Set whether button callbacks are dispatched asynchronously. This defaults to
	 * true for backwards compatibility with existing AdvancedCore GUI code.
	 *
	 * @param value true to run button callbacks asynchronously, false to run them
	 *              on the inventory event thread
	 * @return this inventory
	 */
	public BInventory setClickAsync(boolean value) {
		clickAsync = value;
		return this;
	}

	/**
	 * Convenience method for opting this inventory into synchronous button callbacks.
	 *
	 * @return this inventory
	 */
	public BInventory runClicksSync() {
		return setClickAsync(false);
	}

	/**
	 * Convenience method for explicitly using the legacy asynchronous callback mode.
	 *
	 * @return this inventory
	 */
	public BInventory runClicksAsync() {
		return setClickAsync(true);
	}

	public BInventory setCloseInv(boolean value) {
		closeInv = value;
		return this;
	}

	public void setInventoryName(String inventoryName) {
		this.inventoryName = MessageAPI.colorize(inventoryName);
	}

	public void setMaxInvSize(int maxInvSize) {
		this.maxInvSize = getProperSize(maxInvSize);
	}

	public void setMeta(Player player, String str, Object ob) {
		PlayerUtils.setPlayerMeta(AdvancedCorePlugin.getInstance(), player, str, ob);
	}

	public void setNextItem(ItemStack nextItem) {
		this.nextItem = nextItem;
	}

	public void setPageButtons(ArrayList<BInventoryButton> pageButtons) {
		this.pageButtons = pageButtons;
	}

	public void setPages(boolean pages) {
		this.pages = pages;
		if (!pages) {
			maxPage = 1;
		}
	}

	public void setPrevItem(ItemStack prevItem) {
		this.prevItem = prevItem;
	}

	private void addFillSlots() {
		if (fillItems.isEmpty()) {
			return;
		}
		int inventorySize = getInventorySize();
		for (BInventoryButton button : fillItems) {
			for (int slot = 0; slot < inventorySize; slot++) {
				if (!buttons.containsKey(slot)) {
					buttons.put(slot, copyButton(button, slot));
				}
			}
		}
		fillItems.clear();
	}

	private BInventoryButton copyButton(BInventoryButton button, int slot) {
		BInventoryButton copy = new BInventoryButton(button) {
			@Override
			public void onClick(ClickEvent clickEvent) {
				button.onClick(clickEvent);
			}
		};
		copy.setSlot(slot);
		return copy;
	}

	private void cancelFutures(List<ScheduledFuture<?>> scheduledFutures) {
		if (scheduledFutures == null) {
			return;
		}
		for (ScheduledFuture<?> future : scheduledFutures) {
			if (future != null) {
				future.cancel(true);
			}
		}
	}

	private void cancelLegacyTimers() {
		cancelFutures(futures);
		futures.clear();
	}

	private String getDisplayName(Player player) {
		return PlaceholderUtils.replaceJavascript(player,
				PlaceholderUtils.replacePlaceHolder(inventoryName, placeholders));
	}

	private int getProperSize(int size) {
		if (size < 9) {
			return 9;
		}
		if (size < 18) {
			return 18;
		}
		if (size < 27) {
			return 27;
		}
		if (size < 36) {
			return 36;
		}
		if (size < 45) {
			return 45;
		}
		return 54;
	}

	private boolean hasPermission(Player player) {
		if (perm == null) {
			return true;
		}

		if (!perm.contains("|")) {
			if (player.hasPermission(perm)) {
				return true;
			}
		} else {
			for (String permission : perm.split(Pattern.quote("|"))) {
				if (player.hasPermission(permission)) {
					return true;
				}
			}

		player.sendMessage(MessageAPI.colorize(AdvancedCorePlugin.getInstance().getOptions().getFormatNoPerms()));
		return false;
	}

	private void loadButton(Player player, BInventoryButton button, int sourceSlot, int displayedSlot) {
		inv.setItem(displayedSlot, button.getItem(player, placeholders));
		button.setInv(this);
		button.setSlot(sourceSlot);
		button.load(player);
	}

	private void loadNavigationItems(Player player) {
		AdvancedCorePlugin plugin = AdvancedCorePlugin.getInstance();
		if (prevItem == null) {
			if (plugin.getOptions().getPrevItem() != null) {
				prevItem = new ItemBuilder(plugin.getOptions().getPrevItem()).addPlaceholder(placeholders).toItemStack(player);
			} else {
				prevItem = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE, 1).setName("&aPrevious Page")
						.addPlaceholder(placeholders).toItemStack(player);
			}
		}
		if (nextItem == null) {
			if (plugin.getOptions().getNextItem() != null) {
				nextItem = new ItemBuilder(plugin.getOptions().getNextItem()).addPlaceholder(placeholders).toItemStack(player);
			} else {
				nextItem = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE, 1).setName("&aNext Page")
						.addPlaceholder(placeholders).toItemStack(player);
			}
		}
	}

	private void openInv(Player player, Inventory inventory) {
		AdvancedCorePlugin plugin = AdvancedCorePlugin.getInstance();
		plugin.getBukkitScheduler().runTask(plugin, () -> player.openInventory(inventory), player);
	}

	private void trackFuture(Player player, ScheduledFuture<?> future) {
		if (player == null) {
			futures.add(future);
			return;
		}
		playerFutures.computeIfAbsent(player.getUniqueId(), ignored -> new CopyOnWriteArrayList<>()).add(future);
	}
}
