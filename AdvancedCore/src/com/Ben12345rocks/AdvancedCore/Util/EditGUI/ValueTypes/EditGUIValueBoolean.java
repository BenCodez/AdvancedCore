package com.Ben12345rocks.AdvancedCore.Util.EditGUI.ValueTypes;

import org.bukkit.entity.Player;

import com.Ben12345rocks.AdvancedCore.Util.Inventory.BInventory.ClickEvent;
import com.Ben12345rocks.AdvancedCore.Util.Messages.StringParser;
import com.Ben12345rocks.AdvancedCore.Util.ValueRequest.ValueRequestBuilder;
import com.Ben12345rocks.AdvancedCore.Util.ValueRequest.Listeners.BooleanListener;

public abstract class EditGUIValueBoolean extends EditGUIValue {
	public EditGUIValueBoolean(String key, Object value) {
		setKey(key);
		setCurrentValue(value);
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
				player.sendMessage(StringParser.getInstance().colorize("&cSetting " + getKey() + " to " + value));
			}
		}).currentValue(getCurrentValue().toString()).request(clickEvent.getPlayer());
	}

	public abstract void setValue(Player player, boolean value);

}
