package com.bencodez.advancedcore.api.inventory.editgui.valuetypes;

import org.bukkit.entity.Player;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.inventory.BInventory.ClickEvent;
import com.bencodez.simpleapi.messages.MessageAPI;
import com.bencodez.simpleapi.valuerequest.ValueRequest;

/**
 * Abstract GUI value for boolean editing.
 */
public abstract class EditGUIValueBoolean extends EditGUIValue {
	/**
	 * Constructor for EditGUIValueBoolean.
	 *
	 * @param key the key
	 * @param value the initial value
	 */
	public EditGUIValueBoolean(String key, Object value) {
		setKey(key);
		setCurrentValue(value);
	}

	@Override
	public String getType() {
		return "boolean";
	}

	@Override
	public void onClick(ClickEvent clickEvent) {
		if (getCurrentValue() == null) {
			setCurrentValue(Boolean.FALSE);
		}
		new ValueRequest(AdvancedCorePlugin.getInstance(), AdvancedCorePlugin.getInstance().getDialogService(),
				getInputMethod()).requestBoolean(clickEvent.getPlayer(), Boolean.valueOf(String.valueOf(getCurrentValue())),
				"Type cancel to cancel", (Player player, boolean value) -> {
					setValue(player, value);
					setCurrentValue(Boolean.valueOf(value));
					player.sendMessage(MessageAPI.colorize("&cSetting " + getKey() + " to " + value));
				});
	}

	/**
	 * Sets the boolean value.
	 *
	 * @param player the player
	 * @param value the value to set
	 */
	public abstract void setValue(Player player, boolean value);
}