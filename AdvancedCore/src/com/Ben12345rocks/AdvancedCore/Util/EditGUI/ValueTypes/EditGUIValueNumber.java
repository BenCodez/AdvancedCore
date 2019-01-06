package com.Ben12345rocks.AdvancedCore.Util.EditGUI.ValueTypes;

import org.bukkit.entity.Player;

import com.Ben12345rocks.AdvancedCore.Util.Inventory.BInventory.ClickEvent;
import com.Ben12345rocks.AdvancedCore.Util.Misc.StringUtils;
import com.Ben12345rocks.AdvancedCore.Util.ValueRequest.ValueRequestBuilder;
import com.Ben12345rocks.AdvancedCore.Util.ValueRequest.Listeners.Listener;

public abstract class EditGUIValueNumber extends EditGUIValue {

	public EditGUIValueNumber(String key, Object value) {
		setKey(key);
		setCurrentValue(value);
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
				player.sendMessage(
						StringUtils.getInstance().colorize("&cSetting " + getKey() + " to " + number.doubleValue()));
			}
		}, new Number[] { 0, 10, 25, 50, 100, 500, 1000, (Number) getCurrentValue() })
				.currentValue(getCurrentValue().toString()).request(clickEvent.getPlayer());
	}

	public abstract void setValue(Player player, Number num);
}
