package com.bencodez.advancedcore.api.inventory.editgui.valuetypes;

import org.bukkit.entity.Player;

import com.bencodez.advancedcore.api.inventory.BInventory.ClickEvent;
import com.bencodez.advancedcore.api.valuerequest.ValueRequestBuilder;
import com.bencodez.advancedcore.api.valuerequest.listeners.Listener;
import com.bencodez.simpleapi.array.ArrayUtils;
import com.bencodez.simpleapi.messages.MessageAPI;

public abstract class EditGUIValueString extends EditGUIValue {

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
		new ValueRequestBuilder(new Listener<String>() {
			@Override
			public void onInput(Player player, String value) {
				setValue(player, value);
				player.sendMessage(MessageAPI.colorize("&cSetting " + getKey() + " to " + value));
			}
		}, ArrayUtils.convert(getOptions())).currentValue(getCurrentValue().toString()).allowCustomOption(true)
				.usingMethod(getInputMethod()).request(clickEvent.getPlayer());
	}

	public abstract void setValue(Player player, String value);
}
