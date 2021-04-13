package com.bencodez.advancedcore.api.rewards.editbuttons;

import org.bukkit.entity.Player;

import com.bencodez.advancedcore.api.inventory.editgui.EditGUIButton;
import com.bencodez.advancedcore.api.inventory.editgui.valuetypes.EditGUIValueNumber;
import com.bencodez.advancedcore.api.inventory.editgui.valuetypes.EditGUIValueString;
import com.bencodez.advancedcore.api.rewards.RewardEditData;

public abstract class RewardEdit {

	public EditGUIButton getIntButton(String key, RewardEditData reward) {
		return new EditGUIButton(new EditGUIValueNumber(key, reward.getValue(key)) {

			@Override
			public void setValue(Player player, Number num) {
				setVal(key, num.intValue());
			}
		});
	}

	public EditGUIButton getStringButton(String key, RewardEditData reward) {
		return new EditGUIButton(new EditGUIValueString(key, reward.getValue(key)) {

			@Override
			public void setValue(Player player, String str) {
				setVal(key, str);
			}
		});
	}

	public abstract void setVal(String key, Object value);

}
