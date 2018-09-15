package com.Ben12345rocks.AdvancedCore.Util.EditGUI;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import com.Ben12345rocks.AdvancedCore.Util.Inventory.BInventory;
import com.Ben12345rocks.AdvancedCore.Util.Inventory.BInventoryButton;
import com.Ben12345rocks.AdvancedCore.Util.Misc.ArrayUtils;

public class EditGUI extends BInventory {

	public EditGUI(String name) {
		super(name);
	}

	public void sort() {
		Map<Integer, BInventoryButton> map = getButtons();
		setButtons(new HashMap<Integer, BInventoryButton>());
		LinkedHashMap<String, EditGUIButton> buttons = new LinkedHashMap<String, EditGUIButton>();
		for (BInventoryButton button : map.values()) {
			if (button instanceof EditGUIButton) {
				EditGUIButton b = (EditGUIButton) button;
				buttons.put(b.getKey(), b);
			} else {
				addButton(button);
			}
		}

		ArrayList<String> keys = ArrayUtils.getInstance().convert(buttons.keySet());
		keys = ArrayUtils.getInstance().sort(keys);

		for (String key : keys) {
			addButton(buttons.get(key));
		}
	}

}
