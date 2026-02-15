package com.bencodez.advancedcore.api.inventory.editgui.valuetypes;

import java.util.ArrayList;

import com.bencodez.advancedcore.api.inventory.BInventory.ClickEvent;

/**
 * Abstract GUI value for inventory editing.
 */
public abstract class EditGUIValueInventory extends EditGUIValue {
	private ArrayList<String> keys = new ArrayList<>();

	/**
	 * Constructor for EditGUIValueInventory.
	 *
	 * @param key the key
	 */
	public EditGUIValueInventory(String key) {
		setKey(key);
		setCanGetValue(false);
		keys.add(key);
	}

	/**
	 * Adds a check key.
	 *
	 * @param key the key to add
	 * @return this instance
	 */
	public EditGUIValueInventory addCheckKey(String key) {
		keys.add(key);
		return this;
	}

	@Override
	public String getType() {
		return "unkown";
	}

	@Override
	public void onClick(ClickEvent clickEvent) {
		openInventory(clickEvent);
	}

	/**
	 * Opens the inventory for editing.
	 *
	 * @param clickEvent the click event
	 */
	public abstract void openInventory(ClickEvent clickEvent);

}
