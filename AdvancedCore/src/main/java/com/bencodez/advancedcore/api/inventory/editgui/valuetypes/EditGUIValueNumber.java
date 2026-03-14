package com.bencodez.advancedcore.api.inventory.editgui.valuetypes;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import org.bukkit.entity.Player;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.inventory.BInventory.ClickEvent;
import com.bencodez.simpleapi.messages.MessageAPI;
import com.bencodez.simpleapi.valuerequest.ValueRequest;

/**
 * Abstract GUI value for number editing.
 */
public abstract class EditGUIValueNumber extends EditGUIValue {
	/**
	 * Constructor for EditGUIValueNumber.
	 *
	 * @param key the key
	 * @param value the initial value
	 */
	public EditGUIValueNumber(String key, Object value) {
		setKey(key);
		setCurrentValue(value);
	}

	@Override
	public String getType() {
		return "number";
	}

	@Override
	public void onClick(ClickEvent clickEvent) {
		if (getCurrentValue() == null) {
			setCurrentValue(Integer.valueOf(0));
		}
		new ValueRequest(AdvancedCorePlugin.getInstance(), AdvancedCorePlugin.getInstance().getDialogService(),
				getInputMethod()).requestNumber(clickEvent.getPlayer(), getCurrentNumber(), getDefaultOptions(), true,
				"Type cancel to cancel", (Player player, Number number) -> {
					setValue(player, number);
					setCurrentValue(number);
					player.sendMessage(MessageAPI.colorize("&cSetting " + getKey() + " to " + number.doubleValue()));
				});
	}

	/**
	 * Gets the current value as a number.
	 *
	 * @return the current number value
	 */
	private Number getCurrentNumber() {
		Object current = getCurrentValue();
		if (current instanceof Number) {
			return (Number) current;
		}
		try {
			return Double.valueOf(String.valueOf(current));
		} catch (NumberFormatException ex) {
			return Integer.valueOf(0);
		}
	}

	/**
	 * Builds the default number options including the current value.
	 *
	 * @return the list of default number options
	 */
	private List<Number> getDefaultOptions() {
		LinkedHashSet<Number> values = new LinkedHashSet<Number>();
		values.add(Integer.valueOf(0));
		values.add(Integer.valueOf(10));
		values.add(Integer.valueOf(25));
		values.add(Integer.valueOf(50));
		values.add(Integer.valueOf(100));
		values.add(Integer.valueOf(500));
		values.add(Integer.valueOf(1000));
		values.add(getCurrentNumber());
		return new ArrayList<Number>(values);
	}

	/**
	 * Sets the number value.
	 *
	 * @param player the player
	 * @param num the number to set
	 */
	public abstract void setValue(Player player, Number num);
}