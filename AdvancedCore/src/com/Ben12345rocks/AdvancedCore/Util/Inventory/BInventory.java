/* Obtained from https://www.spigotmc.org/threads/libish-inventory-api-kinda.49339/
 */

package com.Ben12345rocks.AdvancedCore.Util.Inventory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

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
		if (position >= 54) {
			position = 53;
		}
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
		if (inv.getName().equalsIgnoreCase(getInventoryName())) {
			// Main.plugin.debug("Inventory equal");
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
		Inventory inv = Bukkit.createInventory(player,
				inventory.getInventorySize(), inventory.getInventoryName());
		Iterator<Entry<Integer, BInventoryButton>> it = inventory.getButtons()
				.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Integer, BInventoryButton> pair = it.next();
			{
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
			inv.setItem(pair.getKey(), pair.getValue().getItem());
		}
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