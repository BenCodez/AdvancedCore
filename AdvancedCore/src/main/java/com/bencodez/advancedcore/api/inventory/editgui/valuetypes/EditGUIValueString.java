package com.bencodez.advancedcore.api.inventory.editgui.valuetypes;

import org.bukkit.entity.Player;

import com.bencodez.advancedcore.api.inventory.BInventory.ClickEvent;

/**
 * Abstract GUI value for string editing.
 */
public abstract class EditGUIValueString extends EditGUIValue {
	public EditGUIValueString(String key, Object value) {
		super(key, value);
	}

	@Override
	public String getType() {
		return "string";
	}

	@Override
	public void onClick(ClickEvent clickEvent) {
		createValueRequest().requestString(clickEvent.getPlayer(), currentString(""), getOptions(), true,
				"Type cancel to cancel", (Player player, String value) -> applyValue(player, value,
						newValue -> setValue(player, newValue)));
	}

	public abstract void setValue(Player player, String value);
}
