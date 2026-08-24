package com.bencodez.advancedcore.api.inventory.editgui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.bencodez.advancedcore.api.inventory.BInventory;
import com.bencodez.advancedcore.api.inventory.BInventoryButton;

/**
 * Edit GUI for sorting and organizing edit buttons.
 */
public class EditGUI extends BInventory {
	public EditGUI(String name) {
		super(name);
	}

	/**
	 * Sorts edit buttons alphabetically by editor key while retaining non-editor
	 * buttons and duplicate editor keys.
	 */
	public void sort() {
		Map<Integer, BInventoryButton> current = getButtons();
		List<BInventoryButton> fixedButtons = new ArrayList<>();
		List<EditGUIButton> editButtons = new ArrayList<>();

		for (BInventoryButton button : current.values()) {
			if (button instanceof EditGUIButton) {
				EditGUIButton editButton = (EditGUIButton) button;
				editButton.setSlot(-1);
				editButtons.add(editButton);
			} else {
				fixedButtons.add(button);
			}
		}

		editButtons.sort(Comparator.comparing(button -> button.getEditor().getKey(),
				Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));

		setButtons(new HashMap<>());
		for (BInventoryButton button : fixedButtons) {
			addButton(button);
		}
		for (EditGUIButton button : editButtons) {
			addButton(button);
		}
	}
}
