package com.bencodez.advancedcore.api.inventory.editgui.valuetypes;

import org.bukkit.entity.Player;

import com.bencodez.advancedcore.api.inventory.BInventory.ClickEvent;
import com.bencodez.advancedcore.api.valuerequest.ValueRequestBuilder;
import com.bencodez.advancedcore.api.valuerequest.listeners.BooleanListener;
import com.bencodez.simpleapi.messages.MessageAPI;

public abstract class EditGUIValueBoolean extends EditGUIValue {
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
			setCurrentValue("false");
		}
		new ValueRequestBuilder(new BooleanListener() {

			@Override
			public void onInput(Player player, boolean value) {
				setValue(player, value);
				player.sendMessage(MessageAPI.colorize("&cSetting " + getKey() + " to " + value));
			}
		}).currentValue(getCurrentValue().toString()).usingMethod(getInputMethod()).request(clickEvent.getPlayer());
	}

	public abstract void setValue(Player player, boolean value);

}
