package com.bencodez.advancedcore.api.inventory.editgui.valuetypes;

import org.bukkit.entity.Player;

import com.bencodez.advancedcore.api.inventory.BInventory.ClickEvent;

/**
 * Abstract GUI value for boolean editing.
 */
public abstract class EditGUIValueBoolean extends EditGUIValue {
	public EditGUIValueBoolean(String key, Object value) {
		super(key, value);
	}

	@Override
	public String getType() {
		return "boolean";
	}

	@Override
	public void onClick(ClickEvent clickEvent) {
		boolean current = Boolean.parseBoolean(currentString("false"));
		createValueRequest().requestBoolean(clickEvent.getPlayer(), current, "Type cancel to cancel",
				(Player player, boolean value) -> applyValue(player, Boolean.valueOf(value),
						newValue -> setValue(player, newValue.booleanValue())));
	}

	public abstract void setValue(Player player, boolean value);
}
