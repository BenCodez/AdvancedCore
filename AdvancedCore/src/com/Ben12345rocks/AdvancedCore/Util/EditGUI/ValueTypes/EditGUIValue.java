package com.Ben12345rocks.AdvancedCore.Util.EditGUI.ValueTypes;

import java.util.ArrayList;

import com.Ben12345rocks.AdvancedCore.Util.Inventory.BInventory;
import com.Ben12345rocks.AdvancedCore.Util.Inventory.BInventory.ClickEvent;

import lombok.Getter;
import lombok.Setter;

public abstract class EditGUIValue {
	@Getter
	@Setter
	private String key;

	@Getter
	@Setter
	private Object currentValue;

	@Getter
	private ArrayList<String> options = new ArrayList<String>();
	
	@Getter
	@Setter
	private BInventory inv;

	public abstract void onClick(ClickEvent event);

	public EditGUIValue addOptions(String... str) {
		for (String s : str) {
			options.add(s);
		}
		return this;
	}
}
