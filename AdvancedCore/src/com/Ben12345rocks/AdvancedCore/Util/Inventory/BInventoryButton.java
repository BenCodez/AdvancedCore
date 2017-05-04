/* Obtained from https://www.spigotmc.org/threads/libish-inventory-api-kinda.49339/
 */

package com.Ben12345rocks.AdvancedCore.Util.Inventory;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.Ben12345rocks.AdvancedCore.Util.Inventory.BInventory.ClickEvent;
import com.Ben12345rocks.AdvancedCore.Util.Item.ItemBuilder;

// TODO: Auto-generated Javadoc
/**
 * The Class BInventoryButton.
 */
public abstract class BInventoryButton {

	/** The item. */
	private ItemBuilder builder;

	/** The slot. */
	private int slot;

	public BInventoryButton(ItemBuilder item) {
		setBuilder(item);
		slot = item.getSlot();
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
		setBuilder(new ItemBuilder(item).setName(name).setLore(lore));
	}

	/**
	 * @return the builder
	 */
	public ItemBuilder getBuilder() {
		return builder;
	}

	/**
	 * Gets the item.
	 *
	 * @return the item
	 */
	public ItemStack getItem() {
		return builder.toItemStack();
	}

	public ItemStack getItem(Player player) {
		return builder.toItemStack(player);
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

	/**
	 * @param builder
	 *            the builder to set
	 */
	public void setBuilder(ItemBuilder builder) {
		this.builder = builder;
	}

	public void setItem(ItemBuilder builder) {
		this.builder = builder;
	}

	/**
	 * Sets the item.
	 *
	 * @param item
	 *            the new item
	 */
	public void setItem(ItemStack item) {
		builder = new ItemBuilder(item);
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