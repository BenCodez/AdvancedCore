package com.bencodez.advancedcore.api.inventory.editgui.valuetypes;

import java.util.ArrayList;

import com.bencodez.advancedcore.api.inventory.BInventory.ClickEvent;

public abstract class EditGUIValueInventory extends EditGUIValue {
	private ArrayList<String> keys = new ArrayList<>();

	public EditGUIValueInventory(String key) {
		setKey(key);
		setCanGetValue(false);
		keys.add(key);
	}

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

	public abstract void openInventory(ClickEvent clickEvent);

}
