package com.bencodez.advancedcore.api.inventory.editgui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import com.bencodez.advancedcore.api.inventory.BInventory;
import com.bencodez.advancedcore.api.inventory.BInventoryButton;

public class EditGUI extends BInventory {

	public EditGUI(String name) {
		super(name);
	}

	public void sort() {
		Map<Integer, BInventoryButton> map = getButtons();
		setButtons(new HashMap<>());
		LinkedHashMap<String, EditGUIButton> buttons = new LinkedHashMap<>();
		ArrayList<String> sortedList = new ArrayList<>();
		for (BInventoryButton button : map.values()) {
			if (button instanceof EditGUIButton) {
				EditGUIButton b = (EditGUIButton) button;
				String key = b.getEditor().getKey();
				sortedList.add(key);
				b.setSlot(-1);
				buttons.put(key, b);
			} else {
				addButton(button);
			}
		}
		sortedList.sort(Comparator.naturalOrder());

		for (String key : sortedList) {
			addButton(buttons.get(key));
		}
	}

}
