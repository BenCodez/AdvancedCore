package com.bencodez.advancedcore.api.inventory.editgui.valuetypes;

import org.bukkit.entity.Player;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.inventory.BInventory.ClickEvent;
import com.bencodez.simpleapi.messages.MessageAPI;
import com.bencodez.simpleapi.valuerequest.ValueRequest;

/**
 * Abstract GUI value for string editing.
 */
public abstract class EditGUIValueString extends EditGUIValue {
	/**
	 * Constructor for EditGUIValueString.
	 *
	 * @param key   the key
	 * @param value the initial value
	 */
	public EditGUIValueString(String key, Object value) {
		setKey(key);
		setCurrentValue(value);
	}

	@Override
	public String getType() {
		return "string";
	}

	@Override
	public void onClick(ClickEvent clickEvent) {
		if (getCurrentValue() == null) {
			setCurrentValue("");
		}
		new ValueRequest(AdvancedCorePlugin.getInstance(), AdvancedCorePlugin.getInstance().getDialogService(),
				getInputMethod()).requestString(clickEvent.getPlayer(), String.valueOf(getCurrentValue()), getOptions(),
						true, "Type cancel to cancel", (Player player, String value) -> {
							setValue(player, value);
							setCurrentValue(value);
							player.sendMessage(MessageAPI.colorize("&cSetting " + getKey() + " to " + value));
						});
	}

	/**
	 * Sets the string value.
	 *
	 * @param player the player
	 * @param value  the value to set
	 */
	public abstract void setValue(Player player, String value);
}