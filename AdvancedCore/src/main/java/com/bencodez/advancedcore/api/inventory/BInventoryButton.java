/* Obtained from https://www.spigotmc.org/threads/libish-inventory-api-kinda.49339/
 */

package com.bencodez.advancedcore.api.inventory;

import java.util.HashMap;
import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.inventory.BInventory.ClickEvent;
import com.bencodez.advancedcore.api.item.ItemBuilder;
import com.bencodez.simpleapi.messages.MessageAPI;
import com.bencodez.simpleapi.player.PlayerUtils;

import lombok.Getter;
import lombok.Setter;

// TODO: Auto-generated Javadoc
/**
 * The Class BInventoryButton.
 */
public abstract class BInventoryButton {

	/** The item. */
	private ItemBuilder builder;

	/**
	 * Whether the inventory should close after interaction.
	 * 
	 * @return whether the inventory should close
	 */
	@Getter
	private boolean closeInv = true;

	/**
	 * Whether closeInv has been explicitly set.
	 * 
	 * @return whether closeInv has been set
	 */
	@Getter
	private boolean closeInvSet = false;

	private HashMap<String, Object> data = new HashMap<>();

	/**
	 * The fill slots for this button.
	 * 
	 * @return the fill slots list
	 */
	@Getter
	private List<Integer> fillSlots;

	/**
	 * The inventory this button belongs to.
	 * 
	 * @return the inventory
	 * @param inv the inventory to set
	 */
	@Getter
	@Setter
	private BInventory inv;

	/** The slot. */
	private int slot = -1;

	/**
	 * Whether to fill empty slots with this button.
	 * 
	 * @return whether to fill empty slots
	 */
	@Getter
	private boolean fillEmptySlots = false;

	/**
	 * Instantiates a new BInventory button from an existing button.
	 *
	 * @param button the button to copy
	 */
	public BInventoryButton(BInventoryButton button) {
		setBuilder(button.getBuilder());
		slot = button.getSlot();
		fillSlots = button.getFillSlots();
	}

	/**
	 * Instantiates a new BInventory button from an item builder.
	 *
	 * @param item the item builder
	 */
	public BInventoryButton(ItemBuilder item) {
		setBuilder(item);
		slot = item.getSlot();
		fillSlots = item.getFillSlots();
		fillEmptySlots = item.isFillEmptySlots();
		if (item.isCloseGUISet()) {
			closeInv = item.isCloseGUI();
			closeInvSet = true;
		}
	}

	/**
	 * Instantiates a new BInventory button from an item stack.
	 *
	 * @param item the item stack
	 */
	public BInventoryButton(ItemStack item) {
		setBuilder(new ItemBuilder(item));
	}

	/**
	 * Instantiates a new b inventory button.
	 *
	 * @param name the name
	 * @param lore the lore
	 * @param item the item
	 */
	public BInventoryButton(String name, String[] lore, ItemStack item) {
		setBuilder(new ItemBuilder(item).setName(name).setLore(lore));
	}

	/**
	 * Add data to this button.
	 * 
	 * @param key the key
	 * @param object the value
	 * @return this button
	 */
	public BInventoryButton addData(String key, Object object) {
		getData().put(key, object);
		return this;
	}

	/**
	 * Set this button to not close the inventory after click.
	 * 
	 * @return this button
	 */
	public BInventoryButton dontClose() {
		closeInv = false;
		return this;
	}

	/**
	 * @return the builder
	 */
	public ItemBuilder getBuilder() {
		return builder;
	}

	/**
	 * Get this button.
	 * 
	 * @return this button
	 */
	public BInventoryButton getButton() {
		return this;
	}

	/**
	 * @return the data
	 */
	public HashMap<String, Object> getData() {
		return data;
	}

	/**
	 * Get data by key.
	 * 
	 * @param key the key
	 * @return the data value
	 */
	public Object getData(String key) {
		return data.get(key);
	}

	/**
	 * Get data by key with default value.
	 * 
	 * @param key the key
	 * @param defaultValue the default value if key is not found
	 * @return the data value or default value
	 */
	public Object getData(String key, Object defaultValue) {
		if (data.containsKey(key)) {
			return data.get(key);
		}
		return defaultValue;
	}

	/**
	 * Gets the item.
	 *
	 * @return the item
	 *
	 * @deprecated Use getItem(Player player)
	 */
	@Deprecated
	public ItemStack getItem() {
		return builder.toItemStack();
	}

	/**
	 * Gets the item.
	 *
	 * @param player the player
	 * @return the item
	 */
	public ItemStack getItem(Player player) {
		return builder.toItemStack(player);
	}

	/**
	 * Gets the item with placeholders.
	 *
	 * @param player the player
	 * @param placeholders the placeholders
	 * @return the item
	 */
	public ItemStack getItem(Player player, HashMap<String, String> placeholders) {
		return builder.addPlaceholder(placeholders).toItemStack(player);
	}

	/**
	 * Get the last rewards path for a player.
	 * 
	 * @param player the player
	 * @return the rewards path
	 */
	public String getLastRewardsPath(Player player) {
		String test = builder.getRewardsPath(player);
		return test;
	}

	/**
	 * Get meta from player.
	 * 
	 * @param player the player
	 * @param str the meta key
	 * @return the meta value
	 */
	public Object getMeta(Player player, String str) {
		return PlayerUtils.getPlayerMeta(AdvancedCorePlugin.getInstance(), player, str);
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
	 * Load this button for a player.
	 * 
	 * @param p the player
	 */
	public void load(Player p) {
	}

	/**
	 * On click.
	 *
	 * @param clickEvent the click event
	 */
	public abstract void onClick(ClickEvent clickEvent);

	/**
	 * On click with inventory.
	 *
	 * @param event the click event
	 * @param inv the inventory
	 */
	public void onClick(ClickEvent event, BInventory inv) {
		this.inv = inv;
		onClick(event);
	}

	/**
	 * Send a message to a player.
	 * 
	 * @param player the player
	 * @param msg the message
	 */
	public void sendMessage(Player player, String msg) {
		player.sendMessage(MessageAPI.colorize(msg));
	}

	/**
	 * @param builder the builder to set
	 */
	public void setBuilder(ItemBuilder builder) {
		this.builder = builder;
	}

	/**
	 * Set whether the inventory should close after interaction.
	 * 
	 * @param value whether to close
	 * @return this button
	 */
	public BInventoryButton setCloseInv(boolean value) {
		closeInv = value;
		closeInvSet = true;
		return this;
	}

	/**
	 * Sets the item.
	 *
	 * @param builder the builder
	 */
	public void setItem(ItemBuilder builder) {
		this.builder = builder;
	}

	/**
	 * Sets the item.
	 *
	 * @param item the new item
	 */
	public void setItem(ItemStack item) {
		builder = new ItemBuilder(item);
	}

	/**
	 * Set meta for a player.
	 * 
	 * @param player the player
	 * @param str the meta key
	 * @param ob the meta value
	 */
	public void setMeta(Player player, String str, Object ob) {
		PlayerUtils.setPlayerMeta(AdvancedCorePlugin.getInstance(), player, str, ob);
	}

	/**
	 * Sets the slot.
	 *
	 * @param slot the new slot
	 * @return Return button
	 */
	public BInventoryButton setSlot(int slot) {
		this.slot = slot;
		return this;
	}

}