/* Obtained from https://www.spigotmc.org/threads/libish-inventory-api-kinda.49339/
 */

package com.Ben12345rocks.AdvancedCore.Util.Inventory;

import org.bukkit.inventory.ItemStack;

import com.Ben12345rocks.AdvancedCore.Util.Inventory.BInventory.ClickEvent;
import com.Ben12345rocks.AdvancedCore.Util.Item.ItemBuilder;
import com.Ben12345rocks.AdvancedCore.Util.Misc.ArrayUtils;
import com.Ben12345rocks.AdvancedCore.Util.Misc.StringUtils;

// TODO: Auto-generated Javadoc
/**
 * The Class BInventoryButton.
 */
public abstract class BInventoryButton {

	/** The name. */
	private String name;

	/** The lore. */
	private String[] lore;

	/** The item. */
	private ItemStack item;

	/** The slot. */
	private int slot;

	public BInventoryButton(ItemBuilder item) {
		setItem(item);
	}

	public BInventoryButton(ItemStack item) {
		setItem(item);
	}

	/**
	 * Instantiates a new b inventory button.
	 *
	 * @param name
	 *            the name
	 * @param lore
	 *            the lore
	 * @param item
	 *            the item
	 */
	public BInventoryButton(String name, String[] lore, ItemStack item) {
		setName(name);
		setLore(lore);
		setItem(item);
	}

	/**
	 * Gets the item.
	 *
	 * @return the item
	 */
	public ItemStack getItem() {
		return item;
	}

	/**
	 * Gets the lore.
	 *
	 * @return the lore
	 */
	public String[] getLore() {
		return lore;
	}

	/**
	 * Gets the name.
	 *
	 * @return the name
	 */
	public String getName() {
		return name;
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
	 * On click.
	 *
	 * @param clickEvent
	 *            the click event
	 */
	public abstract void onClick(ClickEvent clickEvent);

	public void setItem(ItemBuilder builder) {
		item = builder.toItemStack();
	}

	/**
	 * Sets the item.
	 *
	 * @param item
	 *            the new item
	 */
	public void setItem(ItemStack item) {
		this.item = item;
	}

	/**
	 * Sets the lore.
	 *
	 * @param lore
	 *            the new lore
	 */
	public void setLore(String[] lore) {
		this.lore = ArrayUtils.getInstance().colorize(lore);
	}

	/**
	 * Sets the name.
	 *
	 * @param name
	 *            the new name
	 */
	public void setName(String name) {
		this.name = StringUtils.getInstance().colorize(name);
	}

	/**
	 * Sets the slot.
	 *
	 * @param slot
	 *            the new slot
	 */
	public void setSlot(int slot) {
		this.slot = slot;
	}

}