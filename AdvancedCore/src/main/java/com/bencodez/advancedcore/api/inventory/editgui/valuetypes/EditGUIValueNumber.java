package com.bencodez.advancedcore.api.inventory.editgui.valuetypes;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import org.bukkit.entity.Player;

import com.bencodez.advancedcore.api.inventory.BInventory.ClickEvent;

/**
 * Abstract GUI value for number editing.
 */
public abstract class EditGUIValueNumber extends EditGUIValue {
	public EditGUIValueNumber(String key, Object value) {
		super(key, value);
	}

	@Override
	public String getType() {
		return "number";
	}

	@Override
	public void onClick(ClickEvent clickEvent) {
		Number current = currentNumber(Integer.valueOf(0));
		createValueRequest().requestNumber(clickEvent.getPlayer(), current, getDefaultOptions(current), true,
				"Type cancel to cancel", (Player player, Number number) -> applyValue(player, number,
						newValue -> setValue(player, newValue)));
	}

	private List<Number> getDefaultOptions(Number current) {
		LinkedHashSet<Number> values = new LinkedHashSet<>();
		values.add(Integer.valueOf(0));
		values.add(Integer.valueOf(10));
		values.add(Integer.valueOf(25));
		values.add(Integer.valueOf(50));
		values.add(Integer.valueOf(100));
		values.add(Integer.valueOf(500));
		values.add(Integer.valueOf(1000));
		values.add(current);
		return new ArrayList<>(values);
	}

	public abstract void setValue(Player player, Number num);
}
