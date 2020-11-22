package com.bencodez.advancedcore.api.inventory.editgui.valuetypes;

import org.bukkit.entity.Player;

import com.bencodez.advancedcore.api.inventory.BInventory.ClickEvent;
import com.bencodez.advancedcore.api.messages.StringParser;
import com.bencodez.advancedcore.api.misc.ArrayUtils;
import com.bencodez.advancedcore.api.valuerequest.ValueRequestBuilder;
import com.bencodez.advancedcore.api.valuerequest.listeners.Listener;

public abstract class EditGUIValueString extends EditGUIValue {

	public EditGUIValueString(String key, Object value) {
		setKey(key);
		setCurrentValue(value);
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
				player.sendMessage(StringParser.getInstance().colorize("&cSetting " + getKey() + " to " + value));
			}
		}, ArrayUtils.getInstance().convert(getOptions())).currentValue(getCurrentValue().toString())
				.allowCustomOption(true).request(clickEvent.getPlayer());
	}

	public abstract void setValue(Player player, String value);
}
