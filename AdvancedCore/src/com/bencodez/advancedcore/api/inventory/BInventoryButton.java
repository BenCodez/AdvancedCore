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

	@Getter
	private boolean closeInv = true;

	@Getter
	private boolean closeInvSet = false;

	private HashMap<String, Object> data = new HashMap<>();

	@Getter
	private List<Integer> fillSlots;

	@Getter
	@Setter
	private BInventory inv;

	/** The slot. */
	private int slot = -1;

	@Getter
	private boolean fillEmptySlots = false;

	public BInventoryButton(BInventoryButton button) {
		setBuilder(button.getBuilder());
		slot = button.getSlot();
		fillSlots = button.getFillSlots();
	}

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

	public BInventoryButton addData(String key, Object object) {
		getData().put(key, object);
		return this;
	}

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

	public BInventoryButton getButton() {
		return this;
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

	public ItemStack getItem(Player player) {
		return builder.toItemStack(player);
	}

	public ItemStack getItem(Player player, HashMap<String, String> placeholders) {
		return builder.addPlaceholder(placeholders).toItemStack(player);
	}

	public String getLastRewardsPath(Player player) {
		String test = builder.getRewardsPath(player);
		return test;
	}

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

	public void load(Player p) {
	}

	/**
	 * On click.
	 *
	 * @param clickEvent the click event
	 */
	public abstract void onClick(ClickEvent clickEvent);

	public void onClick(ClickEvent event, BInventory inv) {
		this.inv = inv;
		onClick(event);
	}

	public void sendMessage(Player player, String msg) {
		player.sendMessage(MessageAPI.colorize(msg));
	}

	/**
	 * @param builder the builder to set
	 */
	public void setBuilder(ItemBuilder builder) {
		this.builder = builder;
	}

	public BInventoryButton setCloseInv(boolean value) {
		closeInv = value;
		closeInvSet = true;
		return this;
	}

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