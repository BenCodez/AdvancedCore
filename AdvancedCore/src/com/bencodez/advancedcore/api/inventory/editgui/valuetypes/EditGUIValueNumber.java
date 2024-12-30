package com.bencodez.advancedcore.api.inventory.editgui.valuetypes;

import org.bukkit.entity.Player;

import com.bencodez.advancedcore.api.inventory.BInventory.ClickEvent;
import com.bencodez.advancedcore.api.valuerequest.ValueRequestBuilder;
import com.bencodez.advancedcore.api.valuerequest.listeners.Listener;
import com.bencodez.simpleapi.messages.MessageAPI;

public abstract class EditGUIValueNumber extends EditGUIValue {

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
			setCurrentValue(0);
		}
		new ValueRequestBuilder(new Listener<Number>() {

			@Override
			public void onInput(Player player, Number number) {
				setValue(player, number);
				player.sendMessage(MessageAPI.colorize("&cSetting " + getKey() + " to " + number.doubleValue()));
			}
		}, new Number[] { 0, 10, 25, 50, 100, 500, 1000, (Number) getCurrentValue() })
				.currentValue(getCurrentValue().toString()).allowCustomOption(true).usingMethod(getInputMethod())
				.request(clickEvent.getPlayer());
	}

	public abstract void setValue(Player player, Number num);
}
