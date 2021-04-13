package com.bencodez.advancedcore.api.inventory.editgui.valuetypes;

import java.util.ArrayList;

import com.bencodez.advancedcore.api.inventory.BInventory.ClickEvent;
import com.bencodez.advancedcore.api.rewards.RewardEditData;

public abstract class EditGUIValueInventory extends EditGUIValue {
	private ArrayList<String> keys = new ArrayList<String>();

	public EditGUIValueInventory(String key) {
		setKey(key);
		setCanGetValue(false);
		keys.add(key);
	}

	public EditGUIValueInventory addCheckKey(String key) {
		keys.add(key);
		return this;
	}

	public boolean containsKey(RewardEditData rewardEditData) {
		for (String key : keys) {
			if (rewardEditData.hasPath(key)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void onClick(ClickEvent clickEvent) {
		openInventory(clickEvent);
	}

	public abstract void openInventory(ClickEvent clickEvent);

}
